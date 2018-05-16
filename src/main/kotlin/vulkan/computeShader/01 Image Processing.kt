/*
* Vulkan Example - Compute shader image processing
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.computeShader

import glm_.L
import glm_.buffer.bufferBig
import glm_.f
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec2.Vec2i
import glm_.vec3.Vec3
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import uno.kotlin.buffers.capacity
import vkk.*
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.assetPath
import vulkan.base.*
import vulkan.base.tools.VK_FLAGS_NONE


fun main(args: Array<String>) {
    ImageProcessing().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

// Vertex layout for this example
private val Vertex = object {
    //    float pos[3];
//    float uv[2];
    val size = Vec3.size + Vec2.size
    val offPos = 0
    val offUv = Vec3.size
}

private class ImageProcessing : VulkanExampleBase() {

    val textureColorMap = Texture2D()
    val textureComputeTarget = Texture2D()

    private val vertices = object {
        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
        lateinit var bindingDescriptions: VkVertexInputBindingDescription
        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
    }

    /** Resources for the graphics part of the example */
    private val graphics = object {
        var descriptorSetLayout: VkDescriptorSetLayout = NULL   // Image display shader binding layout
        var descriptorSetPreCompute: VkDescriptorSet = NULL     // Image display shader bindings before compute shader image manipulation
        var descriptorSetPostCompute: VkDescriptorSet = NULL    // Image display shader bindings after compute shader image manipulation
        var pipeline: VkPipeline = NULL                         // Image display pipeline
        var pipelineLayout: VkPipelineLayout = NULL             // Layout of the graphics pipeline
    }

    /** Resources for the compute part of the example */
    private val compute = object {
        lateinit var queue: VkQueue                             // Separate queue for compute commands (queue family may differ from the one used for graphics)
        var commandPool: VkCommandPool = NULL                   // Use a separate command pool (queue family may differ from the one used for graphics)
        lateinit var commandBuffer: VkCommandBuffer             // Command buffer storing the dispatch commands and barriers
        var fence: VkFence = NULL                               // Synchronization fence to avoid rewriting compute CB if still in use
        var descriptorSetLayout: VkDescriptorSetLayout = NULL   // Compute shader binding layout
        var descriptorSet: VkDescriptorSet = NULL               // Compute shader bindings
        var pipelineLayout: VkPipelineLayout = NULL             // Layout of the compute pipeline
        val pipelines = ArrayList<VkPipeline>()                 // Compute pipelines for image filters
        var pipelineIndex = 0                                   // Current image filtering compute pipeline index
        var queueFamilyIndex = 0                                // Family index of the graphics queue, used for barriers
    }

    val vertexBuffer = Buffer()
    var indexBuffer = Buffer()
    var indexCount = 0

    var uniformBufferVS = Buffer()

    private val uboVS = object {
        var projection = Mat4()
        var model = Mat4()

        fun pack() {
            projection to buffer
            model.to(buffer, Mat4.size)
        }

        val size = Mat4.size * 2
        val buffer = bufferBig(size)
        val address = memAddress(buffer)
    }

    var vertexBufferSize = 0

    val shaderNames = ArrayList<String>()

    init {
        zoom = -2.0f
        title = "Compute shader image load/store"
//        settings.overlay = true
    }

    override fun destroy() {
        device.apply {
            // Graphics
            destroyPipeline(graphics.pipeline)
            destroyPipelineLayout(graphics.pipelineLayout)
            destroyDescriptorSetLayout(graphics.descriptorSetLayout)

            // Compute
            for (pipeline in compute.pipelines)
                destroyPipeline(pipeline)
            destroyPipelineLayout(compute.pipelineLayout)
            destroyDescriptorSetLayout(compute.descriptorSetLayout)
            destroyFence(compute.fence)
            destroyCommandPool(compute.commandPool)
        }
        vertexBuffer.destroy()
        indexBuffer.destroy()
        uniformBufferVS.destroy()

        textureColorMap.destroy()
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
            // Image will be sampled in the fragment shader and used as storage target in the compute shader
            usage = VkImageUsage.SAMPLED_BIT or VkImageUsage.STORAGE_BIT
            flags = 0
            // Sharing mode exclusive means that ownership of the image does not need to be explicitly transferred between the compute and graphics queue
            sharingMode = VkSharingMode.EXCLUSIVE
        }

        tex.image = device createImage imageCreateInfo

        val memReqs = device getImageMemoryRequirements tex.image
        val memAllocInfo = vk.MemoryAllocateInfo {
            allocationSize = memReqs.size
            memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
        }
        tex.deviceMemory = device allocateMemory memAllocInfo
        device.bindImageMemory(tex.image, tex.deviceMemory)

        val layoutCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)

        tex.imageLayout = VkImageLayout.GENERAL
        tools.setImageLayout(
                layoutCmd, tex.image,
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
            maxLod = tex.mipLevels.f
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
        }
        tex.sampler = device createSampler sampler

        // Create image view
        val view = vk.ImageViewCreateInfo {
            image = NULL
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

    fun loadAssets() {
        textureColorMap.loadFromFile(
                "$assetPath/textures/vulkan_11_rgba.ktx",
                VkFormat.R8G8B8A8_UNORM,
                vulkanDevice, queue,
                VkImageUsage.SAMPLED_BIT or VkImageUsage.STORAGE_BIT,
                VkImageLayout.GENERAL)
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
            renderPass = this@ImageProcessing.renderPass
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
                    // We won't be changing the layout of the image
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

                val viewport = vk.Viewport(size.x * 0.5f, size.y.f)
                setViewport(viewport)

                setScissor(size)

                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, vertexBuffer.buffer)
                bindIndexBuffer(indexBuffer.buffer, 0, VkIndexType.UINT32)

                // Left (pre compute)
                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, graphics.pipelineLayout, graphics.descriptorSetPreCompute)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, graphics.pipeline)

                drawIndexed(indexCount, 1, 0, 0, 0)

                // Right (post compute)
                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, graphics.pipelineLayout, graphics.descriptorSetPostCompute)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, graphics.pipeline)

                viewport.x = size.x / 2f
                setViewport(viewport)
                drawIndexed(indexCount, 1, 0, 0, 0)

                endRenderPass()

                end()
            }
        }
    }

    fun buildComputeCommandBuffer() {
        // Flush the queue if we're rebuilding the command buffer after a pipeline change to ensure it's not currently in use
        compute.queue.waitIdle()

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        compute.commandBuffer.apply {

            begin(cmdBufInfo)

            bindPipeline(VkPipelineBindPoint.COMPUTE, compute.pipelines[compute.pipelineIndex])
            bindDescriptorSets(VkPipelineBindPoint.COMPUTE, compute.pipelineLayout, compute.descriptorSet)

            compute.commandBuffer.dispatch(textureComputeTarget.size / 16, 1)

            end()
        }
    }

    /** Setup vertices for a single uv-mapped quad */
    fun generateQuad() {
        // Setup vertices for a single uv-mapped quad made from two triangles
        val vertices = appBuffer.floatBufferOf(
                +1f, +1f, 0f, 1f, 1f,
                -1f, +1f, 0f, 0f, 1f,
                -1f, -1f, 0f, 0f, 0f,
                +1f, -1f, 0f, 1f, 0f)

        // Setup indices
        val indices = appBuffer.intBufferOf(0, 1, 2, 2, 3, 0)
        indexCount = indices.capacity

        // Create buffers
        // For the sake of simplicity we won't stage the vertex data to the gpu memory
        // Vertex buffer
        vulkanDevice.createBuffer(
                VkBufferUsage.VERTEX_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                vertexBuffer,
                vertices)
        // Index buffer
        vulkanDevice.createBuffer(
                VkBufferUsage.INDEX_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                indexBuffer,
                indices)
    }

    fun setupVertexDescriptions() {
        // Binding description
        vertices.bindingDescriptions = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, Vertex.size, VkVertexInputRate.VERTEX)

        // Attribute descriptions
        // Describes memory layout and shader positions
        vertices.attributeDescriptions = vk.VertexInputAttributeDescription(
                // Location 0: Position
                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, Vertex.offPos,
                // Location 1: Texture coordinates
                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32_SFLOAT, Vertex.offUv)

        // Assign to vertex buffer
        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertices.bindingDescriptions
            vertexAttributeDescriptions = vertices.attributeDescriptions
        }
    }

    fun setupDescriptorPool() {

        val poolSizes = vk.DescriptorPoolSize(
                // Graphics pipelines uniform buffers
                VkDescriptorType.UNIFORM_BUFFER, 2,
                // Graphics pipelines image samplers for displaying compute output image
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 2,
                // Compute pipelines uses a storage image for image reads and writes
                VkDescriptorType.STORAGE_IMAGE, 2)
        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 3)
        descriptorPool = device createDescriptorPool descriptorPoolInfo
    }

    fun setupDescriptorSetLayout() {

        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
                // Binding 0: Vertex shader uniform buffer
                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0,
                // Binding 1: Fragment shader input image
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 1)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
        graphics.descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(graphics.descriptorSetLayout)
        graphics.pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, graphics.descriptorSetLayout)

        // Input image (before compute post processing)
        graphics.descriptorSetPreCompute = device allocateDescriptorSets allocInfo
        val baseImageWriteDescriptorSets = vk.WriteDescriptorSet(
                graphics.descriptorSetPreCompute, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBufferVS.descriptor,
                graphics.descriptorSetPreCompute, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, textureColorMap.descriptor)

        device updateDescriptorSets baseImageWriteDescriptorSets

        // Final image (after compute shader processing)
        graphics.descriptorSetPostCompute = device allocateDescriptorSets allocInfo
        val writeDescriptorSets = vk.WriteDescriptorSet(
                graphics.descriptorSetPostCompute, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBufferVS.descriptor,
                graphics.descriptorSetPostCompute, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, textureComputeTarget.descriptor)

        device updateDescriptorSets writeDescriptorSets

    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.NONE.i, VkFrontFace.CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Rendering pipeline
        // Load shaders
        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
            it[0].loadShader("$assetPath/shaders/computeshader/texture.vert.spv", VkShaderStage.VERTEX_BIT)
            it[1].loadShader("$assetPath/shaders/computeshader/texture.frag.spv", VkShaderStage.FRAGMENT_BIT)
        }
        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(graphics.pipelineLayout, renderPass).also {
            it.vertexInputState = vertices.inputState
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

    /** Find and create a compute capable device queue */
    fun getComputeQueue() {

        val queueFamilyProperties = physicalDevice.queueFamilyProperties

        // Some devices have dedicated compute queues, so we first try to find a queue that supports compute and not graphics
        var computeQueueFound = false
        for (i in queueFamilyProperties.indices)
            if (queueFamilyProperties[i].queueFlags has VkQueueFlag.COMPUTE_BIT && queueFamilyProperties[i].queueFlags hasnt VkQueueFlag.GRAPHICS_BIT) {
                compute.queueFamilyIndex = i
                computeQueueFound = true
                break
            }
        // If there is no dedicated compute queue, just find the first queue family that supports compute
        if (!computeQueueFound)
            for (i in queueFamilyProperties.indices) {
                if (queueFamilyProperties[i].queueFlags has VkQueueFlag.COMPUTE_BIT) {
                    compute.queueFamilyIndex = i
                    computeQueueFound = true
                    break
                }
            }

        // Compute is mandatory in Vulkan, so there must be at least one queue family that supports compute
        assert(computeQueueFound)
        // Get a compute queue from the device
        compute.queue = device getQueue compute.queueFamilyIndex
    }

    fun prepareCompute() {

        getComputeQueue()

        // Create compute pipeline
        // Compute pipelines are created separate from graphics pipelines even if they use the same queue

        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
                // Binding 0: Input image (read-only)
                VkDescriptorType.STORAGE_IMAGE, VkShaderStage.COMPUTE_BIT.i, 0,
                // Binding 1: Output image (write)
                VkDescriptorType.STORAGE_IMAGE, VkShaderStage.COMPUTE_BIT.i, 1)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
        compute.descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(compute.descriptorSetLayout)

        compute.pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, compute.descriptorSetLayout)

        compute.descriptorSet = device allocateDescriptorSets allocInfo
        val computeWriteDescriptorSets = vk.WriteDescriptorSet(
                compute.descriptorSet, VkDescriptorType.STORAGE_IMAGE, 0, textureColorMap.descriptor,
                compute.descriptorSet, VkDescriptorType.STORAGE_IMAGE, 1, textureComputeTarget.descriptor)

        device updateDescriptorSets computeWriteDescriptorSets

        // Create compute shader pipelines
        val computePipelineCreateInfo = vk.ComputePipelineCreateInfo(compute.pipelineLayout)

        // One pipeline for each effect
        shaderNames += listOf("emboss", "edgedetect", "sharpen")
        for (shaderName in shaderNames) {
            val fileName = "$assetPath/shaders/computeshader/$shaderName.comp.spv"
            computePipelineCreateInfo.stage.loadShader(fileName, VkShaderStage.COMPUTE_BIT)
            val pipeline = device.createComputePipelines(pipelineCache, computePipelineCreateInfo)
            compute.pipelines += pipeline
        }
        // Separate command pool as queue family for compute may be different than graphics
        val cmdPoolInfo = vk.CommandPoolCreateInfo {
            queueFamilyIndex = compute.queueFamilyIndex
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
        // Vertex shader uniform buffer block
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBufferVS,
                uboVS.size.L)

        // Map persistent
        uniformBufferVS.map()

        updateUniformBuffers()
    }

    fun updateUniformBuffers() {
        // Vertex shader uniform buffer block
        uboVS.projection = glm.perspective(60f.rad, size.x * 0.5f / size.y, 0.1f, 256f)
        val viewMatrix = glm.translate(Mat4(1f), 0f, 0f, zoom)

        uboVS.model = viewMatrix * glm.translate(Mat4(1f), cameraPos)
                .rotateAssign(rotation.x.rad, 1f, 0f, 0f)
                .rotateAssign(rotation.y.rad, 0f, 1f, 0f)
                .rotateAssign(rotation.z.rad, 0f, 0f, 1f)

        uboVS.pack()
        memCopy(uboVS.address, uniformBufferVS.mapped, uboVS.size.L)
    }

    fun draw() {

        super.prepareFrame()

        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]
        queue submit submitInfo

        super.submitFrame()

        // Submit compute commands
        // Use a fence to ensure that compute command buffer has finished executin before using it again
        device.waitForFence(compute.fence, true, UINT64_MAX)
        device.resetFence(compute.fence)

        val computeSubmitInfo = vk.SubmitInfo { commandBuffer = compute.commandBuffer }
        compute.queue.submit(computeSubmitInfo, compute.fence)
    }

    override fun prepare() {
        super.prepare()
        loadAssets()
        generateQuad()
        setupVertexDescriptions()
        prepareUniformBuffers()
        prepareTextureTarget(textureComputeTarget, textureColorMap.size, VkFormat.R8G8B8A8_UNORM)
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
    }

    override fun viewChanged() = updateUniformBuffers()

//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
//    {
//        if (overlay->header("Settings")) {
//        if (overlay->comboBox("Shader", &compute.pipelineIndex, shaderNames)) {
//        buildComputeCommandBuffer()
//    }
//    }
//    }
}