///*
//* Vulkan Example - Attraction based compute shader particle system
//*
//* Updated compute shader by Lukas Bergdoll (https://github.com/Voultapher)
//*
//* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
//*
//* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
//*/
//
//package vulkan.computeShader
//
//import glm_.BYTES
//import glm_.L
//import glm_.func.rad
//import glm_.glm
//import glm_.vec2.Vec2
//import glm_.vec4.Vec4
//import kool.bufferBig
//import kool.free
//import org.lwjgl.system.MemoryUtil.*
//import org.lwjgl.vulkan.*
//import vkk.*
//import vulkan.PARTICLE_COUNT
//import vulkan.UINT64_MAX
//import vulkan.VERTEX_BUFFER_BIND_ID
//import vulkan.assetPath
//import vulkan.base.Buffer
//import vulkan.base.Texture2D
//import vulkan.base.VulkanExampleBase
//import vulkan.base.tools.VK_FLAGS_NONE
//import java.nio.ByteBuffer
//import kotlin.math.sin
//
//
//fun main() {
//    GpuParticleSystem().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//class GpuParticleSystem : VulkanExampleBase() {
//
//    var animStart = 20f
//    var animate = true // TODO fix mouse animation
//
//    object textures {
//        val particle = Texture2D()
//        val gradient = Texture2D()
//    }
//
//    object vertices {
//        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
//        lateinit var bindingDescriptions: VkVertexInputBindingDescription
//        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
//    }
//
//    /** Resources for the graphics part of the example */
//    object graphics {
//        var descriptorSetLayout = VkDescriptorSetLayout.NULL     // Particle system rendering shader binding layout
//        var descriptorSet = VkDescriptorSet.NULL                // Particle system rendering shader bindings
//        var pipelineLayout = VkPipelineLayout.NULL             // Layout of the graphics pipeline
//        var pipeline = VkPipeline.NULL                        // Particle rendering pipeline
//    }
//
//    /** Resources for the compute part of the example */
//    object compute {
//
//        val storageBuffer = Buffer()                    // (Shader) storage buffer object containing the particles
//        val uniformBuffer = Buffer()                    // Uniform buffer object containing particle system parameters
//        lateinit var queue: VkQueue                                // Separate queue for compute commands (queue family may differ from the one used for graphics)
//        var commandPool = VkCommandPool.NULL                     // Use a separate command pool (queue family may differ from the one used for graphics)
//        lateinit var commandBuffer: VkCommandBuffer                 // Command buffer storing the dispatch commands and barriers
//        var fence = VkFence.NULL                                // Synchronization fence to avoid rewriting compute CB if still in use
//        var descriptorSetLayout = VkDescriptorSetLayout.NULL     // Compute shader binding layout
//        var descriptorSet = VkDescriptorSet.NULL                 // Compute shader bindings
//        var pipelineLayout = VkPipelineLayout.NULL            // Layout of the compute pipeline
//        var pipeline = VkPipeline.NULL                         // Compute pipeline for updating particle positions
//
//        object ubo {                            // Compute shader uniform block object
//            var deltaT = 0f                            //		Frame delta time
//            var dest = Vec2()                            //		x position of the attractor
//            var particleCount = PARTICLE_COUNT
//
//            fun pack() {
//                buffer.putFloat(0, deltaT)
//                dest.to(buffer, Float.BYTES)
//                buffer.putInt(Float.BYTES + Vec2.size, particleCount)
//            }
//
//            val size = Float.BYTES + Vec2.size + Int.BYTES
//            val buffer = bufferBig(size)
//            val address = memAddress(buffer)
//        }
//    }
//
//    // SSBO particle declaration
//    class Particle {
//        val pos = Vec2()            // Particle position
//        val vel = Vec2()            // Particle velocity
//        val gradientPos = Vec4()    // Texture coordiantes for the gradient ramp map
//
//        fun to(buffer: ByteBuffer, offset: Int) {
//            pos.to(buffer, offset)
//            vel.to(buffer, offset + Vec2.size)
//            gradientPos.to(buffer, offset + Vec2.size * 2)
//        }
//
//        companion object {
//            val size = Vec2.size * 2 + Vec4.size
//            val ofsPos = 0
//            val ofsVel = Vec2.size
//            val ofsGrd = Vec2.size * 2
//        }
//    }
//
//    init {
//        title = "Compute shader particle system"
////        settings.overlay = true
//    }
//
//    override fun destroy() {
//
//        device.apply {
//            // Graphics
//            destroyPipeline(graphics.pipeline)
//            destroyPipelineLayout(graphics.pipelineLayout)
//            destroyDescriptorSetLayout(graphics.descriptorSetLayout)
//
//            // Compute
//            compute.storageBuffer.destroy()
//            compute.uniformBuffer.destroy()
//            destroyPipelineLayout(compute.pipelineLayout)
//            destroyDescriptorSetLayout(compute.descriptorSetLayout)
//            destroyPipeline(compute.pipeline)
//            destroyFence(compute.fence)
//            destroyCommandPool(compute.commandPool)
//        }
//        textures.particle.destroy()
//        textures.gradient.destroy()
//
//        super.destroy()
//    }
//
//    fun loadAssets() {
//        textures.particle.loadFromFile("$assetPath/textures/particle01_rgba.ktx", VkFormat.R8G8B8A8_UNORM, vulkanDevice, queue)
//        textures.gradient.loadFromFile("$assetPath/textures/particle_gradient_rgba.ktx", VkFormat.R8G8B8A8_UNORM, vulkanDevice, queue)
//    }
//
//    override fun buildCommandBuffers() {
//        // Destroy command buffers if already present
//        if (!checkCommandBuffers()) {
//            destroyCommandBuffers()
//            createCommandBuffers()
//        }
//
//        val cmdBufInfo = vk.CommandBufferBeginInfo()
//
//        val clearValues = vk.ClearValue(2).also {
//            it[0].color(defaultClearColor)
//            it[1].depthStencil(1f, 0)
//        }
//        val renderPassBeginInfo = vk.RenderPassBeginInfo {
//            renderPass = this@GpuParticleSystem.renderPass
//            renderArea.offset(0)
//            renderArea.extent(size)
//            this.clearValues = clearValues
//        }
//        for (i in drawCmdBuffers.indices) {
//            // Set target frame buffer
//            renderPassBeginInfo.framebuffer(frameBuffers[i].L)
//
//            drawCmdBuffers[i].apply {
//
//                begin(cmdBufInfo)
//
//                // Draw the particle system using the update vertex buffer
//
//                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)
//
//                setViewport(size)
//                setScissor(size)
//
//                bindPipeline(VkPipelineBindPoint.GRAPHICS, graphics.pipeline)
//                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, graphics.pipelineLayout, graphics.descriptorSet)
//
//                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, compute.storageBuffer.buffer)
//                draw(PARTICLE_COUNT, 1, 0, 0)
//
//                drawUI()
//
//                endRenderPass()
//
//                end()
//            }
//        }
//    }
//
//    fun buildComputeCommandBuffer() {
//
//        val cmdBufInfo = vk.CommandBufferBeginInfo()
//
//        compute.commandBuffer.apply {
//
//            begin(cmdBufInfo)
//
//            // Compute particle movement
//
//            // Add memory barrier to ensure that the (graphics) vertex shader has fetched attributes before compute starts to write to the buffer
//            val bufferBarrier = vk.BufferMemoryBarrier {
//                buffer = compute.storageBuffer.buffer
//                size = compute.storageBuffer.descriptor.range
//                srcAccessMask = VkAccess.VERTEX_ATTRIBUTE_READ_BIT.i    // Vertex shader invocations have finished reading from the buffer
//                dstAccessMask = VkAccess.SHADER_WRITE_BIT.i             // Compute shader wants to write to the buffer
//                // Compute and graphics queue may have different queue families (see VulkanDevice::createLogicalDevice)
//                // For the barrier to work across different queues, we need to set their family indices
//                srcQueueFamilyIndex = vulkanDevice.queueFamilyIndices.graphics  // Required as compute and graphics queue may have different families
//                dstQueueFamilyIndex = vulkanDevice.queueFamilyIndices.compute   // Required as compute and graphics queue may have different families
//            }
//            compute.commandBuffer.pipelineBarrier(
//                    VkPipelineStage.VERTEX_SHADER_BIT.i,
//                    VkPipelineStage.COMPUTE_SHADER_BIT.i,
//                    VK_FLAGS_NONE,
//                    bufferMemoryBarrier = bufferBarrier)
//
//            bindPipeline(VkPipelineBindPoint.COMPUTE, compute.pipeline)
//            bindDescriptorSets(VkPipelineBindPoint.COMPUTE, compute.pipelineLayout, compute.descriptorSet)
//
//            // Dispatch the compute job
//            dispatch(PARTICLE_COUNT / 256, 1, 1)
//
//            // Add memory barrier to ensure that compute shader has finished writing to the buffer
//            // Without this the (rendering) vertex shader may display incomplete results (partial data from last frame)
//            bufferBarrier.apply {
//                srcAccessMask = VkAccess.SHADER_WRITE_BIT.i             // Compute shader has finished writes to the buffer
//                dstAccessMask = VkAccess.VERTEX_ATTRIBUTE_READ_BIT.i    // Vertex shader invocations want to read from the buffer
//                buffer = compute.storageBuffer.buffer
//                size = compute.storageBuffer.descriptor.range
//                // Compute and graphics queue may have different queue families (see VulkanDevice::createLogicalDevice)
//                // For the barrier to work across different queues, we need to set their family indices
//                srcQueueFamilyIndex = vulkanDevice.queueFamilyIndices.compute   // Required as compute and graphics queue may have different families
//                dstQueueFamilyIndex = vulkanDevice.queueFamilyIndices.graphics  // Required as compute and graphics queue may have different families
//            }
//            compute.commandBuffer.pipelineBarrier(
//                    VkPipelineStage.COMPUTE_SHADER_BIT.i,
//                    VkPipelineStage.VERTEX_SHADER_BIT.i,
//                    VK_FLAGS_NONE,
//                    bufferMemoryBarrier = bufferBarrier)
//
//            end()
//        }
//    }
//
//    /** Setup and fill the compute shader storage buffers containing the particles */
//    fun prepareStorageBuffers() {
//
//        // Initial particle positions
//        val particlesBuffer = bufferBig(PARTICLE_COUNT * Particle.size)
//        for (i in 0 until PARTICLE_COUNT) {
//            val particle = Particle().apply {
//                pos(glm.linearRand(-1f, 1f), glm.linearRand(-1f, 1f))
//                vel(0f)
//                gradientPos.x = pos.x / 2f
//            }
//            particle.to(particlesBuffer, i * Particle.size)
//        }
//
//
//        val storageBufferSize = VkDeviceSize(PARTICLE_COUNT * Particle.size)
//
//        // Staging
//        // SSBO won't be changed on the host after upload so copy to device local memory
//
//        val stagingBuffer = Buffer()
//
//        vulkanDevice.createBuffer(
//                VkBufferUsage.TRANSFER_SRC_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                stagingBuffer,
//                particlesBuffer)
//
//        vulkanDevice.createBuffer(
//                // The SSBO will be used as a storage buffer for the compute pipeline and as a vertex buffer in the graphics pipeline
//                VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.STORAGE_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT,
//                VkMemoryProperty.DEVICE_LOCAL_BIT.i,
//                compute.storageBuffer,
//                storageBufferSize)
//
//        // Copy to staging buffer
//        val copyCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)
//        val copyRegion = vk.BufferCopy { size = storageBufferSize }
//        copyCmd.copyBuffer(stagingBuffer.buffer, compute.storageBuffer.buffer, copyRegion)
//        super.flushCommandBuffer(copyCmd, queue, true)
//
//        stagingBuffer.destroy()
//
//        // Binding description
//        vertices.bindingDescriptions = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, Particle.size, VkVertexInputRate.VERTEX)
//
//        // Attribute descriptions
//        // Describes memory layout and shader positions
//        vertices.attributeDescriptions = vk.VertexInputAttributeDescription(
//                // Location 0 : Position
//                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32_SFLOAT, Particle.ofsPos,
//                // Location 1 : Gradient position
//                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32B32A32_SFLOAT, Particle.ofsGrd)
//
//        // Assign to vertex buffer
//        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
//            vertexBindingDescription = vertices.bindingDescriptions
//            vertexAttributeDescriptions = vertices.attributeDescriptions
//        }
//
//        particlesBuffer.free()
//    }
//
//    fun setupDescriptorPool() {
//
//        val poolSizes = vk.DescriptorPoolSize(
//                VkDescriptorType.UNIFORM_BUFFER, 1,
//                VkDescriptorType.STORAGE_BUFFER, 1,
//                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 2)
//
//        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 2)
//
//        descriptorPool = device createDescriptorPool descriptorPoolInfo
//    }
//
//    fun setupDescriptorSetLayout() {
//
//        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
//                // Binding 0 : Particle color map
//                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 0,
//                // Binding 1 : Particle gradient ramp
//                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 1)
//
//        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
//
//        graphics.descriptorSetLayout = device createDescriptorSetLayout descriptorLayout
//
//        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(graphics.descriptorSetLayout)
//
//        graphics.pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
//    }
//
//    fun setupDescriptorSet() {
//
//        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, graphics.descriptorSetLayout)
//
//        graphics.descriptorSet = device allocateDescriptorSets allocInfo
//
//        val writeDescriptorSets = vk.WriteDescriptorSet(
//                // Binding 0 : Particle color map
//                graphics.descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 0, textures.particle.descriptor,
//                // Binding 1 : Particle gradient ramp
//                graphics.descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, textures.gradient.descriptor)
//
//        device updateDescriptorSets writeDescriptorSets
//    }
//
//    fun preparePipelines() {
//
//        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.POINT_LIST, 0, false)
//
//        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.NONE.i, VkFrontFace.COUNTER_CLOCKWISE)
//
//        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)
//
//        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)
//
//        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(false, false, VkCompareOp.ALWAYS)
//
//        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)
//
//        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount._1_BIT)
//
//        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
//        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)
//
//        // Rendering pipeline
//        // Load shaders
//        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
//            it[0].loadShader("$assetPath/shaders/computeparticles/particle.vert.spv", VkShaderStage.VERTEX_BIT)
//            it[1].loadShader("$assetPath/shaders/computeparticles/particle.frag.spv", VkShaderStage.FRAGMENT_BIT)
//        }
//        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(graphics.pipelineLayout, renderPass).also {
//            it.vertexInputState = vertices.inputState
//            it.inputAssemblyState = inputAssemblyState
//            it.rasterizationState = rasterizationState
//            it.colorBlendState = colorBlendState
//            it.multisampleState = multisampleState
//            it.viewportState = viewportState
//            it.depthStencilState = depthStencilState
//            it.dynamicState = dynamicState
//            it.stages = shaderStages
//            it.renderPass = renderPass
//        }
//        // Additive blending
//        blendAttachmentState.apply {
//            colorWriteMask = 0xF
//            blendEnable = true
//            colorBlendOp = VkBlendOp.ADD
//            srcColorBlendFactor = VkBlendFactor.ONE
//            dstColorBlendFactor = VkBlendFactor.ONE
//            alphaBlendOp = VkBlendOp.ADD
//            srcAlphaBlendFactor = VkBlendFactor.SRC_ALPHA
//            dstAlphaBlendFactor = VkBlendFactor.DST_ALPHA
//        }
//        graphics.pipeline = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
//    }
//
//    fun prepareCompute() {
//        /*  Create a compute capable device queue
//            The VulkanDevice::createLogicalDevice functions finds a compute capable queue and prefers queue families
//            that only support compute
//            Depending on the implementation this may result in different queue family indices for graphics and computes,
//            requiring proper synchronization (see the memory barriers in buildComputeCommandBuffer) */
//        compute.queue = device getQueue vulkanDevice.queueFamilyIndices.compute
//
//        // Create compute pipeline
//        // Compute pipelines are created separate from graphics pipelines even if they use the same queue (family index)
//
//        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
//                // Binding 0 : Particle position storage buffer
//                VkDescriptorType.STORAGE_BUFFER, VkShaderStage.COMPUTE_BIT.i, 0,
//                // Binding 1 : Uniform buffer
//                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.COMPUTE_BIT.i, 1)
//
//        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
//
//        compute.descriptorSetLayout = device createDescriptorSetLayout descriptorLayout
//
//        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(compute.descriptorSetLayout)
//
//        compute.pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
//
//        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, compute.descriptorSetLayout)
//
//        compute.descriptorSet = device allocateDescriptorSets allocInfo
//
//        val computeWriteDescriptorSets = vk.WriteDescriptorSet(
//                // Binding 0 : Particle position storage buffer
//                compute.descriptorSet, VkDescriptorType.STORAGE_BUFFER, 0, compute.storageBuffer.descriptor,
//                // Binding 1 : Uniform buffer
//                compute.descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 1, compute.uniformBuffer.descriptor)
//
//        device updateDescriptorSets computeWriteDescriptorSets
//
//        // Create pipeline
//        val computePipelineCreateInfo = vk.ComputePipelineCreateInfo(compute.pipelineLayout).apply {
//            stage.loadShader("$assetPath/shaders/computeparticles/particle.comp.spv", VkShaderStage.COMPUTE_BIT)
//        }
//        compute.pipeline = device.createComputePipelines(pipelineCache, computePipelineCreateInfo)
//
//        // Separate command pool as queue family for compute may be different than graphics
//        val cmdPoolInfo = vk.CommandPoolCreateInfo {
//            queueFamilyIndex = vulkanDevice.queueFamilyIndices.compute
//            flags = VkCommandPoolCreate.RESET_COMMAND_BUFFER_BIT.i
//        }
//        compute.commandPool = device createCommandPool cmdPoolInfo
//
//        // Create a command buffer for compute operations
//        val cmdBufAllocateInfo = vk.CommandBufferAllocateInfo(compute.commandPool, VkCommandBufferLevel.PRIMARY, 1)
//
//        compute.commandBuffer = device allocateCommandBuffer cmdBufAllocateInfo
//
//        // Fence for compute CB sync
//        val fenceCreateInfo = vk.FenceCreateInfo(VkFenceCreate.SIGNALED_BIT.i)
//        compute.fence = device createFence fenceCreateInfo
//
//        // Build a single command buffer containing the compute dispatch commands
//        buildComputeCommandBuffer()
//    }
//
//    /** Prepare and initialize uniform buffer containing shader uniforms */
//    fun prepareUniformBuffers() {
//        // Compute shader uniform buffer block
//        vulkanDevice.createBuffer(
//                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                compute.uniformBuffer,
//                VkDeviceSize(compute.ubo.size))
//
//        // Map for host access
//                        compute.uniformBuffer.map()
//
//        updateUniformBuffers()
//    }
//
//    fun updateUniformBuffers() {
//
//        compute.ubo.deltaT = frameTimer * 2.5f
//        if (animate)
//            compute.ubo.dest(sin((timer * 360f).rad) * 0.75f, 0f)
//        else {
//            val normalizedM = (mousePos - (size / 2)) / (size / 2)
//            compute.ubo.dest(normalizedM)
//        }
//        }
//
//        compute.ubo.pack()
//        memCopy(compute.ubo.address, compute.uniformBuffer.mapped, compute.ubo.size.L)
//    }
//
//    fun draw() {
//
//        val computeSubmitInfo = vk.SubmitInfo { commandBuffer = compute.commandBuffer }
//        compute.queue.submit(computeSubmitInfo, compute.fence)
//
//        // Submit graphics commands
//        super.prepareFrame()
//
//        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]
//        queue submit submitInfo
//
//        super.submitFrame()
//
//        // Submit compute commands
//        device.waitForFence(compute.fence, true, UINT64_MAX)
//        device resetFence compute.fence
//    }
//
//    override fun prepare() {
//        super.prepare()
//        loadAssets()
//        prepareStorageBuffers()
//        prepareUniformBuffers()
//        setupDescriptorSetLayout()
//        preparePipelines()
//        setupDescriptorPool()
//        setupDescriptorSet()
//        prepareCompute()
//        buildCommandBuffers()
//        prepared = true
//        window.show()
//    }
//
//    override fun render() {
//        if (!prepared)
//            return
//        draw()
//
//        if (animate)
//            if (animStart > 0f)
//                animStart -= frameTimer * 5f
//            else if (animStart <= 0f) {
//                timer += frameTimer * 0.04f
//                if (timer > 1f)
//                    timer = 0f
//            }
//
//        updateUniformBuffers()
//    }
//
////    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
////    {
////        if (overlay->header("Settings")) { overlay ->
////        checkBox("Moving attractor", & animate)
////    }
////    }
//}