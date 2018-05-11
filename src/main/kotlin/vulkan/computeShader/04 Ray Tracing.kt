/*
* Vulkan Example - Compute shader ray tracing
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.computeShader

import glm_.BYTES
import glm_.L
import glm_.buffer.bufferBig
import glm_.func.rad
import glm_.size
import glm_.vec2.Vec2i
import glm_.vec3.Vec3
import glm_.vec3.Vec3i
import glm_.vec4.Vec4
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkQueue
import vkn.*
import vulkan.TEX_DIM
import vulkan.assetPath
import vulkan.base.*
import vulkan.base.tools.VK_FLAGS_NONE
import kotlin.math.cos
import kotlin.math.sin


fun main(args: Array<String>) {
    RayTracing().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

class RayTracing : VulkanExampleBase() {

    val textureComputeTarget = Texture()

    /** Resources for the graphics part of the example */
    object graphics {
        var descriptorSetLayout: VkDescriptorSetLayout = NULL   // Raytraced image display shader binding layout
        var descriptorSetPreCompute: VkDescriptorSet = NULL     // Raytraced image display shader bindings before compute shader image manipulation
        var descriptorSet: VkDescriptorSet = NULL               // Raytraced image display shader bindings after compute shader image manipulation
        var pipeline: VkPipeline = NULL                         // Raytraced image display pipeline
        var pipelineLayout: VkPipelineLayout = NULL             // Layout of the graphics pipeline
    }

    /** Resources for the compute part of the example */
    object compute {
        object storageBuffers {
            val spheres = Buffer()  // (Shader) storage buffer object with scene spheres
            val planes = Buffer()   // (Shader) storage buffer object with scene planes
        }

        val uniformBuffer = Buffer()                            // Uniform buffer object containing scene data
        lateinit var queue: VkQueue                             // Separate queue for compute commands (queue family may differ from the one used for graphics)
        var commandPool: VkCommandPool = NULL                   // Use a separate command pool (queue family may differ from the one used for graphics)
        lateinit var commandBuffer: VkCommandBuffer             // Command buffer storing the dispatch commands and barriers
        var fence: VkFence = NULL                               // Synchronization fence to avoid rewriting compute CB if still in use
        var descriptorSetLayout: VkDescriptorSetLayout = NULL   // Compute shader binding layout
        var descriptorSet: VkDescriptorSet = NULL               // Compute shader bindings
        var pipelineLayout: VkPipelineLayout = NULL             // Layout of the compute pipeline
        var pipeline: VkPipeline = NULL                         // Compute raytracing pipeline

        object ubo {
            // Compute shader uniform block object
            var lightPos = Vec3()
            var aspectRatio = 0f                        // Aspect ratio of the viewport
            var fogColor = Vec4(0f)

            object camera {
                var pos = Vec3(0f, 0f, 4f)
                var lookat = Vec3(0f, 0.5f, 0f)
                var fov = 10f
            }

            fun pack() {
                lightPos to buffer
                buffer.putFloat(Vec3.size, aspectRatio)
                fogColor.to(buffer, Vec4.size)
                camera.pos.to(buffer, Vec4.size * 2)
                camera.lookat.to(buffer, Vec4.size * 2 + Vec3.size)
                buffer.putFloat(Vec4.size + Vec3.size * 2, camera.fov)
            }

            val size = Vec4.size * 2 + Vec3.size * 2 + 2 * Float.BYTES
            val buffer = bufferBig(size)
            val address = memAddress(buffer)
        }
    }

    // SSBO sphere declaration
    class Sphere( // Shader uses std140 layout (so we only use vec4 instead of vec3)
            val pos: Vec3,
            val radius: Float,
            val diffuse: Vec3,
            val specular: Float,
            /** Id used to identify sphere for raytracing */
            val id: Int) : Bufferizable() {

        lateinit var _pad: Vec3i

        override var fieldOrder = arrayOf("pos", "radius", "diffuse", "specular", "id")

        companion object {
            val size = Vec4.size * 3
        }
    }

    // SSBO plane declaration
    class Plane(
            val normal: Vec3,
            var distance: Float,
            val diffuse: Vec3,
            val specular: Float,
            val id: Int) : Bufferizable() {

        lateinit var _pad: Vec3i

        override var fieldOrder = arrayOf("normal", "distance", "diffuse", "specular", "id")
    }

    init {
        title = "Compute shader ray tracing"
//        settings.overlay = true
        compute.ubo.aspectRatio = size.aspect
        timerSpeed *= 0.25f

        camera.type = Camera.CameraType.lookAt
        camera.setPerspective(60f, size.aspect, 0.1f, 512f)
        camera.setRotation(Vec3())
        camera.setTranslation(Vec3(0f, 0f, -4f))
        camera.rotationSpeed = 0f
        camera.movementSpeed = 2.5f
    }

    override fun destroy() {

        device.apply {
            // Graphics
            destroyPipeline(graphics.pipeline)
            destroyPipelineLayout(graphics.pipelineLayout)
            destroyDescriptorSetLayout(graphics.descriptorSetLayout)

            // Compute
            destroyPipeline(compute.pipeline)
            destroyPipelineLayout(compute.pipelineLayout)
            destroyDescriptorSetLayout(compute.descriptorSetLayout)
            destroyFence(compute.fence)
            destroyCommandPool(compute.commandPool)
        }
        compute.uniformBuffer.destroy()
        compute.storageBuffers.spheres.destroy()
        compute.storageBuffers.planes.destroy()
        textureComputeTarget.destroy()

        super.destroy()
    }

    /** Prepare a texture target that is used to store compute shader calculations */
    fun prepareTextureTarget(tex: Texture, size: Vec2i, format: VkFormat) {
        // Get device properties for the requested texture format
        val formatProperties = physicalDevice getFormatProperties format
        // Check if requested image format supports image storage operations
        assert(formatProperties.optimalTilingFeatures has VkFormatFeature.STORAGE_IMAGE_BIT)

        // Prepare blit target texture
        tex.size(size)

        val imageCreateInfo = vk.ImageCreateInfo {
            imageType = VkImageType.`2D`
            this.format = format
            extent.set(size.x, size.y, 1)
            mipLevels = 1
            arrayLayers = 1
            samples = VkSampleCount.`1_BIT`
            tiling = VkImageTiling.OPTIMAL
            initialLayout = VkImageLayout.UNDEFINED
            // Image will be sampled in the fragment shader and used as storage target in the compute shader
            usage = VkImageUsage.SAMPLED_BIT or VkImageUsage.STORAGE_BIT
            flags = 0
        }

        tex.image = device createImage imageCreateInfo
        val memReqs = device.getImageMemoryRequirements(tex.image)
        val memAllocInfo = vk.MemoryAllocateInfo {
            allocationSize = memReqs.size
            memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
        }
        tex.deviceMemory = device allocateMemory memAllocInfo
        device.bindImageMemory(tex.image, tex.deviceMemory)

        val layoutCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)

        tex.imageLayout = VkImageLayout.GENERAL
        tools.setImageLayout(
                layoutCmd,
                tex.image,
                VkImageAspect.COLOR_BIT.i,
                VkImageLayout.UNDEFINED,
                tex.imageLayout)

        super.flushCommandBuffer(layoutCmd, queue, true)

        // Create sampler
        val sampler = vk.SamplerCreateInfo {
            magFilter = VkFilter.LINEAR
            minFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            addressModeU = VkSamplerAddressMode.CLAMP_TO_BORDER
            addressModeV = addressModeU
            addressModeW = addressModeU
            mipLodBias = 0f
            maxAnisotropy = 1f
            compareOp = VkCompareOp.NEVER
            minLod = 0f
            maxLod = 0f
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
        }
        tex.sampler = device createSampler sampler

        // Create image view
        val view = vk.ImageViewCreateInfo {
            viewType = VkImageViewType.`2D`
            this.format = format
            components(VkComponentSwizzle.R, VkComponentSwizzle.G, VkComponentSwizzle.B, VkComponentSwizzle.A)
            subresourceRange.set(VkImageAspect.COLOR_BIT.i, 0, 1, 0, 1)
            image = tex.image
        }
        tex.view = device createImageView view

        // Initialize a descriptor for later use
        tex.descriptor.imageLayout = tex.imageLayout
        tex.descriptor.imageView = tex.view
        tex.descriptor.sampler = tex.sampler
        tex.device = vulkanDevice
    }

    override fun buildCommandBuffers() {
        // Destroy command buffers if already present
        if (!checkCommandBuffers()) {
            destroyCommandBuffers()
            createCommandBuffers()
        }

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2)
        clearValues[0].color(defaultClearColor)
        clearValues[1].depthStencil.set(1f, 0)

        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@RayTracing.renderPass
            renderArea.apply {
                offset.set(0, 0)
                extent.set(size.x, size.y)
            }
            this.clearValues = clearValues
        }
        for (i in drawCmdBuffers.indices) {
            // Set target frame buffer
            renderPassBeginInfo.framebuffer(frameBuffers[i])

            drawCmdBuffers[i].apply {

                begin(cmdBufInfo)

                // Image memory barrier to make sure that compute shader writes are finished before sampling from the texture
                val imageMemoryBarrier = vk.ImageMemoryBarrier {
                    oldLayout = VkImageLayout.GENERAL
                    newLayout = VkImageLayout.GENERAL
                    image = textureComputeTarget.image
                    subresourceRange.set(VkImageAspect.COLOR_BIT.i, 0, 1, 0, 1)
                    srcAccessMask = VkAccess.SHADER_WRITE_BIT.i
                    dstAccessMask = VkAccess.SHADER_READ_BIT.i
                }
                pipelineBarrier(
                        VkPipelineStage.COMPUTE_SHADER_BIT.i,
                        VkPipelineStage.FRAGMENT_SHADER_BIT.i,
                        VK_FLAGS_NONE,
                        imageMemoryBarrier = imageMemoryBarrier)

                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                setViewport(size)
                setScissor(size)

                // Display ray traced image generated by compute shader as a full screen quad
                // Quad vertices are generated in the vertex shader
                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, graphics.pipelineLayout, graphics.descriptorSet)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, graphics.pipeline)
                draw(3, 1, 0, 0)

                endRenderPass()

                end()
            }
        }
    }

    fun buildComputeCommandBuffer() {

        compute.commandBuffer.apply {

            val cmdBufInfo = vk.CommandBufferBeginInfo()

            begin(cmdBufInfo)

            bindPipeline(VkPipelineBindPoint.COMPUTE, compute.pipeline)
            bindDescriptorSets(VkPipelineBindPoint.COMPUTE, compute.pipelineLayout, compute.descriptorSet)

            dispatch(textureComputeTarget.size / 16, 1)

            end()
        }
    }

    /** Id used to identify objects by the ray tracing shader */
    var currentId = 0

    /** Setup and fill the compute shader storage buffers containing primitives for the raytraced scene */
    fun prepareStorageBuffers() {

        // Spheres
        val spheres = bufferOf(
                Sphere(Vec3(1.75f, -0.5f, 0.0f), 1f, Vec3(0f, 1f, 0f), 32f, currentId++),
                Sphere(Vec3(0f, 1f, -0.5f), 1f, Vec3(0.65f, 0.77f, 0.97f), 32f, currentId++),
                Sphere(Vec3(-1.75f, -0.75f, -0.5f), 1.25f, Vec3(0.9f, 0.76f, 0.46f), 32f, currentId++))
        var storageBufferSize: VkDeviceSize = spheres.size.L

        // Stage
        val stagingBuffer = Buffer()

        vulkanDevice.createBuffer(
                VkBufferUsage.TRANSFER_SRC_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                stagingBuffer,
                spheres)

        vulkanDevice.createBuffer(
                // The SSBO will be used as a storage buffer for the compute pipeline and as a vertex buffer in the graphics pipeline
                VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.STORAGE_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT,
                VkMemoryProperty.DEVICE_LOCAL_BIT.i,
                compute.storageBuffers.spheres,
                storageBufferSize)

        // Copy to staging buffer
        var copyCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)
        val copyRegion = vk.BufferCopy { size = storageBufferSize }
        copyCmd.copyBuffer(stagingBuffer.buffer, compute.storageBuffers.spheres.buffer, copyRegion)
        super.flushCommandBuffer(copyCmd, queue, true)

        stagingBuffer.destroy()

        // Planes
        val roomDim = 4f
        val planes = bufferOf(
                Plane(Vec3(0f, 1f, 0f), roomDim, Vec3(1f), 32f, currentId++),
                Plane(Vec3(0f, -1f, 0f), roomDim, Vec3(1f), 32f, currentId++),
                Plane(Vec3(0f, 0f, 1f), roomDim, Vec3(1f), 32f, currentId++),
                Plane(Vec3(0f, 0f, -1f), roomDim, Vec3(0f), 32f, currentId++),
                Plane(Vec3(-1f, 0f, 0f), roomDim, Vec3(1f, 0f, 0f), 32f, currentId++),
                Plane(Vec3(1f, 0f, 0f), roomDim, Vec3(0f, 1f, 0f), 32f, currentId++))
        storageBufferSize = planes.size.L

        // Stage
        vulkanDevice.createBuffer(
                VkBufferUsage.TRANSFER_SRC_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                stagingBuffer,
                planes)

        vulkanDevice.createBuffer(
                // The SSBO will be used as a storage buffer for the compute pipeline and as a vertex buffer in the graphics pipeline
                VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.STORAGE_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT,
                VkMemoryProperty.DEVICE_LOCAL_BIT.i,
                compute.storageBuffers.planes,
                storageBufferSize)

        // Copy to staging buffer
        copyCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)
        copyRegion.size = storageBufferSize
        copyCmd.copyBuffer(stagingBuffer.buffer, compute.storageBuffers.planes.buffer, copyRegion)
        super.flushCommandBuffer(copyCmd, queue, true)

        stagingBuffer.destroy()
    }

    fun setupDescriptorPool() {

        val poolSizes = vk.DescriptorPoolSize(
                VkDescriptorType.UNIFORM_BUFFER, 2,             // Compute UBO
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 4,     // Graphics image samplers
                VkDescriptorType.STORAGE_IMAGE, 1,              // Storage image for ray traced image output
                VkDescriptorType.STORAGE_BUFFER, 2)             // Storage buffer for the scene primitives

        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 3)

        descriptorPool = device createDescriptorPool descriptorPoolInfo
    }

    fun setupDescriptorSetLayout() {

        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
                // Binding 0 : Fragment shader image sampler
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 0)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)

        graphics.descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(graphics.descriptorSetLayout)

        graphics.pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, graphics.descriptorSetLayout)

        graphics.descriptorSet = device allocateDescriptorSets allocInfo

        val writeDescriptorSets = vk.WriteDescriptorSet(
                // Binding 0 : Fragment shader texture sampler
                graphics.descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 0, textureComputeTarget.descriptor)

        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.FRONT_BIT.i, VkFrontFace.COUNTER_CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(false, false, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Display pipeline
        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
            it[0].loadShader("$assetPath/shaders/raytracing/texture.vert.spv", VkShaderStage.VERTEX_BIT)
            it[1].loadShader("$assetPath/shaders/raytracing/texture.frag.spv", VkShaderStage.FRAGMENT_BIT)
        }
        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(graphics.pipelineLayout, renderPass)

        val emptyInputState = vk.PipelineVertexInputStateCreateInfo {
            vertexAttributeDescriptions = null
            vertexBindingDescriptions = null
        }
        pipelineCreateInfo.also {
            it.vertexInputState = emptyInputState
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
            it.renderPass = renderPass
        }
        graphics.pipeline = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    /** Prepare the compute pipeline that generates the ray traced image */
    fun prepareCompute() {
        /*  Create a compute capable device queue
            The VulkanDevice::createLogicalDevice functions finds a compute capable queue and prefers queue families
            that only support compute
            Depending on the implementation this may result in different queue family indices for graphics and computes,
            requiring proper synchronization (see the memory barriers in buildComputeCommandBuffer) */
        val queueCreateInfo = vk.DeviceQueueCreateInfo { queueFamilyIndex = vulkanDevice.queueFamilyIndices.compute }
        compute.queue = device getQueue vulkanDevice.queueFamilyIndices.compute

        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
                // Binding 0: Storage image (raytraced output)
                VkDescriptorType.STORAGE_IMAGE, VkShaderStage.COMPUTE_BIT.i, 0,
                // Binding 1: Uniform buffer block
                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.COMPUTE_BIT.i, 1,
                // Binding 1: Shader storage buffer for the spheres
                VkDescriptorType.STORAGE_BUFFER, VkShaderStage.COMPUTE_BIT.i, 2,
                // Binding 1: Shader storage buffer for the planes
                VkDescriptorType.STORAGE_BUFFER, VkShaderStage.COMPUTE_BIT.i, 3)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)

        compute.descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(compute.descriptorSetLayout)

        compute.pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, compute.descriptorSetLayout)

        compute.descriptorSet = device allocateDescriptorSets allocInfo

        val computeWriteDescriptorSets = vk.WriteDescriptorSet(
                // Binding 0: Output storage image
                compute.descriptorSet, VkDescriptorType.STORAGE_IMAGE, 0, textureComputeTarget.descriptor,
                // Binding 1: Uniform buffer block
                compute.descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 1, compute.uniformBuffer.descriptor,
                // Binding 2: Shader storage buffer for the spheres
                compute.descriptorSet, VkDescriptorType.STORAGE_BUFFER, 2, compute.storageBuffers.spheres.descriptor,
                // Binding 2: Shader storage buffer for the planes
                compute.descriptorSet, VkDescriptorType.STORAGE_BUFFER, 3, compute.storageBuffers.planes.descriptor)

        device updateDescriptorSets computeWriteDescriptorSets

        // Create compute shader pipelines
        val computePipelineCreateInfo = vk.ComputePipelineCreateInfo(compute.pipelineLayout).apply {
            stage.loadShader("$assetPath/shaders/raytracing/raytracing.comp.spv", VkShaderStage.COMPUTE_BIT)
        }
        compute.pipeline = device.createComputePipelines(pipelineCache, computePipelineCreateInfo)

        // Separate command pool as queue family for compute may be different than graphics
        val cmdPoolInfo = vk.CommandPoolCreateInfo {
            queueFamilyIndex = vulkanDevice.queueFamilyIndices.compute
            flags = VkCommandPoolCreate.RESET_COMMAND_BUFFER_BIT.i
        }
        compute.commandPool = device createCommandPool cmdPoolInfo

        // Create a command buffer for compute operations
        val cmdBufAllocateInfo = vk.CommandBufferAllocateInfo(compute.commandPool, VkCommandBufferLevel.PRIMARY, 1)

        compute.commandBuffer = device allocateCommandBuffer cmdBufAllocateInfo

        // Fence for compute CB sync
        val fenceCreateInfo = vk.FenceCreateInfo(VkFenceCreate.SIGNALED_BIT.i)
        compute.fence = device createFence fenceCreateInfo

        // Build a single command buffer containing the compute dispatch commands
        buildComputeCommandBuffer()
    }

    /** Prepare and initialize uniform buffer containing shader uniforms */
    fun prepareUniformBuffers() {
        // Compute shader parameter uniform buffer block
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                compute.uniformBuffer,
                compute.ubo.size.L)

        updateUniformBuffers()
    }

    fun updateUniformBuffers() {
        compute.ubo.lightPos.put(
                sin((timer * 360f).rad) * cos((timer * 360f).rad) * 2f,
                sin((timer * 360f).rad) * 2f,
                cos((timer * 360f).rad) * 2f)
        compute.ubo.camera.pos = -camera.position
        compute.uniformBuffer.mapping { data ->
            memCopy(compute.ubo.address, data, compute.ubo.size.L)
        }
    }

    fun draw() {

        super.prepareFrame()

        // Command buffer to be sumitted to the queue
        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]
        queue submit submitInfo

        super.submitFrame()

        // Submit compute commands
        // Use a fence to ensure that compute command buffer has finished executing before using it again
        device.waitForFence(compute.fence, true, UINT64_MAX)
        device resetFence compute.fence

        val computeSubmitInfo = vk.SubmitInfo { commandBuffer = compute.commandBuffer }
        compute.queue.submit(computeSubmitInfo, compute.fence)
    }

    override fun prepare() {
        super.prepare()
        prepareStorageBuffers()
        prepareUniformBuffers()
        prepareTextureTarget(textureComputeTarget, TEX_DIM, VkFormat.R8G8B8A8_UNORM)
        setupDescriptorSetLayout()
        preparePipelines()
        setupDescriptorPool()
        setupDescriptorSet()
        prepareCompute()
        buildCommandBuffers()
        prepared = true
        window.show()
    }

    override fun render() {
        if (!prepared)
            return
        draw()
        if (!paused)
            updateUniformBuffers()
    }

    override fun viewChanged() {
        compute.ubo.aspectRatio = size.aspect
        updateUniformBuffers()
    }
}