///*
//* Vulkan Example - Compute shader N-body simulation using two passes and shared compute shader memory
//*
//* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
//*
//* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
//*/
//
//package vulkan.computeShader
//
//import glm_.*
//import glm_.func.rad
//import glm_.mat4x4.Mat4
//import glm_.vec2.Vec2
//import glm_.vec3.Vec3
//import glm_.vec4.Vec4
//import kool.bufferBig
//import org.lwjgl.system.MemoryUtil.*
//import org.lwjgl.vulkan.*
//import org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED
//import vkk.*
//import vulkan.PARTICLES_PER_ATTRACTOR
//import vulkan.UINT64_MAX
//import vulkan.VERTEX_BUFFER_BIND_ID
//import vulkan.assetPath
//import vulkan.base.*
//import vulkan.base.tools.VK_FLAGS_NONE
//import java.nio.ByteBuffer
//import kotlin.math.sin
//
//
//fun main(args: Array<String>) {
//    NBodySimulation().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//
//private class NBodySimulation : VulkanExampleBase() {
//
//    var numParticles = 0
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
//        val uniformBuffer = Buffer()                            // Contains scene matrices
//        var descriptorSetLayout= VkDescriptorSetLayout (NULL)   // Particle system rendering shader binding layout
//        var descriptorSet= VkDescriptorSet (NULL)               // Particle system rendering shader bindings
//        var pipelineLayout= VkPipelineLayout (NULL)             // Layout of the graphics pipeline
//        var pipeline= VkPipeline(NULL)                         // Particle rendering pipeline
//
//        object ubo {
//            var projection = Mat4()
//            var view = Mat4()
//            var screenDim = Vec2()
//
//            fun pack() {
//                projection.to(buffer, 0)
//                view.to(buffer, Mat4.size)
//                screenDim.to(buffer, Mat4.size * 2)
//            }
//
//            val size = Mat4.size * 2 + Vec2.size
//            val buffer = bufferBig(size)
//            val address = memAddress(buffer)
//        }
//    }
//
//    /** Resources for the compute part of the example */
//    object compute {
//        val storageBuffer = Buffer()                            // (Shader) storage buffer object containing the particles
//        val uniformBuffer = Buffer()                            // Uniform buffer object containing particle system parameters
//        lateinit var queue: VkQueue                             // Separate queue for compute commands (queue family may differ from the one used for graphics)
//        var commandPool= VkCommandPool (NULL)                   // Use a separate command pool (queue family may differ from the one used for graphics)
//        lateinit var commandBuffer: VkCommandBuffer             // Command buffer storing the dispatch commands and barriers
//        var fence= VkFence (NULL)                               // Synchronization fence to avoid rewriting compute CB if still in use
//        var descriptorSetLayout= VkDescriptorSetLayout (NULL)   // Compute shader binding layout
//        var descriptorSet= VkDescriptorSet (NULL)               // Compute shader bindings
//        var pipelineLayout= VkPipelineLayout (NULL)             // Layout of the compute pipeline
//        var pipelineCalculate= VkPipeline (NULL)                // Compute pipeline for N-Body velocity calculation (1st pass)
//        var pipelineIntegrate= VkPipeline (NULL)                // Compute pipeline for euler integration (2nd pass)
//        var blur= VkPipeline (NULL)
//        var pipelineLayoutBlur= VkPipelineLayout (NULL)
//        var descriptorSetLayoutBlur= VkDescriptorSetLayout (NULL)
//        var descriptorSetBlur= VkDescriptorSet (NULL)
//
//        object ubo {
//            // Compute shader uniform block object
//            var deltaT = 0f         //		Frame delta time
//            var dest = Vec2()       //		x position of the attractor
//            var particleCount = 0
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
//        var pos = Vec4()    // xyz = position, w = mass
//        var vel = Vec4()    // xyz = velocity, w = gradient texture position
//
//        fun to(buffer: ByteBuffer, offset: Int) {
//            pos.to(buffer, offset)
//            vel.to(buffer, offset + Vec4.size)
//        }
//
//        companion object {
//            val size = Vec4.size * 2
//            val ofsPos = 0
//            val ofsVel = Vec4.size
//        }
//    }
//
//    init {
//        title = "Compute shader N-body system"
////        settings.overlay = true
//        camera.type = Camera.CameraType.lookAt
//        camera.setPerspective(60f, size.aspect, 0.1f, 512f)
//        camera.setRotation(Vec3(-26f, 75f, 0f))
//        camera.setTranslation(Vec3(0f, 0f, -14f))
//        camera.movementSpeed = 2.5f
//    }
//
//    override fun destroy() {
//
//        device.apply {
//            // Graphics
//            graphics.uniformBuffer.destroy()
//            destroyPipeline(graphics.pipeline)
//            destroyPipelineLayout(graphics.pipelineLayout)
//            destroyDescriptorSetLayout(graphics.descriptorSetLayout)
//
//            // Compute
//            compute.storageBuffer.destroy()
//            compute.uniformBuffer.destroy()
//            destroyPipelineLayout(compute.pipelineLayout)
//            destroyDescriptorSetLayout(compute.descriptorSetLayout)
//            destroyPipeline(compute.pipelineCalculate)
//            destroyPipeline(compute.pipelineIntegrate)
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
//            it[0].color(0f, 0f, 0f, 1f)
//            it[1].depthStencil.set(1f, 0)
//        }
//        val renderPassBeginInfo = vk.RenderPassBeginInfo {
//            renderPass = this@NBodySimulation.renderPass
//            renderArea.offset.set(0, 0)
//            renderArea.extent.set(size.x, size.y)
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
//                draw(numParticles, 1, 0, 0)
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
//                // Transfer ownership if compute and graphics queue familiy indices differ
//                srcQueueFamilyIndex = vulkanDevice.queueFamilyIndices.graphics
//                dstQueueFamilyIndex = vulkanDevice.queueFamilyIndices.compute
//            }
//            pipelineBarrier(
//                    VkPipelineStage.VERTEX_SHADER_BIT.i,
//                    VkPipelineStage.COMPUTE_SHADER_BIT.i,
//                    tools.VK_FLAGS_NONE,
//                    bufferMemoryBarrier = bufferBarrier)
//
//            bindPipeline(VkPipelineBindPoint.COMPUTE, compute.pipelineCalculate)
//            bindDescriptorSets(VkPipelineBindPoint.COMPUTE, compute.pipelineLayout, compute.descriptorSet)
//
//            // First pass: Calculate particle movement
//            // -------------------------------------------------------------------------------------------------------
//            dispatch(numParticles / 256, 1, 1)
//
//            // Add memory barrier to ensure that compute shader has finished writing to the buffer
//            bufferBarrier.apply {
//                srcAccessMask = VkAccess.SHADER_WRITE_BIT.i     // Compute shader has finished writes to the buffer
//                dstAccessMask = VkAccess.SHADER_READ_BIT.i
//                buffer = compute.storageBuffer.buffer
//                size = compute.storageBuffer.descriptor.range
//                // No ownership transfer necessary
//                srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED
//                dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED
//            }
//            pipelineBarrier(
//                    VkPipelineStage.COMPUTE_SHADER_BIT.i,
//                    VkPipelineStage.COMPUTE_SHADER_BIT.i,
//                    VK_FLAGS_NONE,
//                    bufferMemoryBarrier = bufferBarrier)
//
//            // Second pass: Integrate particles
//            // -------------------------------------------------------------------------------------------------------
//            bindPipeline(VkPipelineBindPoint.COMPUTE, compute.pipelineIntegrate)
//            dispatch(numParticles / 256, 1, 1)
//
//            // Add memory barrier to ensure that compute shader has finished writing to the buffer
//            // Without this the (rendering) vertex shader may display incomplete results (partial data from last frame)
//            bufferBarrier.apply {
//                srcAccessMask = VkAccess.SHADER_WRITE_BIT.i                                // Compute shader has finished writes to the buffer
//                dstAccessMask = VkAccess.VERTEX_ATTRIBUTE_READ_BIT.i                        // Vertex shader invocations want to read from the buffer
//                buffer = compute.storageBuffer.buffer
//                size = compute.storageBuffer.descriptor.range
//                // Transfer ownership if compute and graphics queue familiy indices differ
//                srcQueueFamilyIndex = vulkanDevice.queueFamilyIndices.compute
//                dstQueueFamilyIndex = vulkanDevice.queueFamilyIndices.graphics
//            }
//            pipelineBarrier(
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
////        val attractors = listOf(
////            Vec3(2.5f, 1.5f, 0f),
////            Vec3(-2.5f, -1.5f, 0f))
//        val attractors = listOf(
//                Vec3(5f, 0f, 0f),
//                Vec3(-5f, 0f, 0f),
//                Vec3(0f, 0f, 5f),
//                Vec3(0f, 0f, -5f),
//                Vec3(0f, 4f, 0f),
//                Vec3(0f, -8f, 0f))
//
//        numParticles = attractors.size * PARTICLES_PER_ATTRACTOR
//
//        // Initial particle positions
//        val particleBuffer = bufferBig(numParticles * Particle.size)
//
//        fun next() = glm.linearRand(0f, 1f)
//
//        for (i in attractors.indices)
//            for (j in 0 until PARTICLES_PER_ATTRACTOR) {
//
//                val particle = Particle()
//
//                // First particle in group as heavy center of gravity
//                if (j == 0) {
//                    particle.pos(attractors[i] * 1.5f, 90000f)
//                    particle.vel(0f)
//                } else {
//                    // Position
//                    val position = attractors[i] + Vec3 { next() } * 0.75f
//                    val len = (position - attractors[i]).normalizeAssign().length()
//                    position.y *= 2f - len * len
//
//                    // Velocity
//                    val angular = Vec3(0.5f, 1.5f, 0.5f) * if ((i % 2) == 0) 1f else -1f
//                    val velocity = ((position - attractors[i]) cross angular) + Vec3(next(), next(), next() * 0.025f)
//
//                    val mass = (next() * 0.5f + 0.5f) * 75f
//                    particle.pos(position, mass)
//                    particle.vel(velocity, 0f)
//                }
//
//                // Color gradient offset
//                particle.vel.w = i * 1f / attractors.size
//
//                particle.to(particleBuffer, (i * PARTICLES_PER_ATTRACTOR + j) * Particle.size)
//            }
//
//        compute.ubo.particleCount = numParticles
//
//        val storageBufferSize = VkDeviceSize(particleBuffer.size.L)
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
//                particleBuffer)
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
//                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32A32_SFLOAT, Particle.ofsPos,
//                // Location 1 : Velocity (used for gradient lookup)
//                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32B32A32_SFLOAT, Particle.ofsVel)
//
//        // Assign to vertex buffer
//        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
//            vertexBindingDescription = vertices.bindingDescriptions
//            vertexAttributeDescriptions = vertices.attributeDescriptions
//        }
//    }
//
//    fun setupDescriptorPool() {
//
//        val poolSizes = vk.DescriptorPoolSize(
//                VkDescriptorType.UNIFORM_BUFFER, 2,
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
//                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 0,
//                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 1,
//                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 2)
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
//                graphics.descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 0, textures.particle.descriptor,
//                graphics.descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, textures.gradient.descriptor,
//                graphics.descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 2, graphics.uniformBuffer.descriptor)
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
//        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)
//
//        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
//
//        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)
//
//        // Rendering pipeline
//        // Load shaders
//        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
//            it[0].loadShader("$assetPath/shaders/computenbody/particle.vert.spv", VkShaderStage.VERTEX_BIT)
//            it[1].loadShader("$assetPath/shaders/computenbody/particle.frag.spv", VkShaderStage.FRAGMENT_BIT)
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
//    // Set shader parameters via specialization constants
//    object specializationData {
//        var sharedDataSize = 0
//        var gravity = 0f
//        var power = 0f
//        var soften = 0f
//
//        fun toBuffer() = bufferBig(size).apply {
//            putInt(offset.sharedDataSize, sharedDataSize)
//            putFloat(offset.gravity, gravity)
//            putFloat(offset.power, power)
//            putFloat(offset.soften, soften)
//        }
//
//        val size = Int.BYTES + Float.BYTES * 3
//
//        object offset {
//            val sharedDataSize = 0
//            val gravity = Int.BYTES
//            val power = gravity + Float.BYTES
//            val soften = power + Float.BYTES
//        }
//    }
//
//    fun prepareCompute() {
//        /*  Create a compute capable device queue
//            The VulkanDevice::createLogicalDevice functions finds a compute capable queue and prefers queue families
//            that only support compute
//            Depending on the implementation this may result in different queue family indices for graphics and computes,
//            requiring proper synchronization (see the memory barriers in buildComputeCommandBuffer) */
//        val queueCreateInfo = vk.DeviceQueueCreateInfo {
//            queueFamilyIndex = vulkanDevice.queueFamilyIndices.compute
//        }
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
//        // Create pipelines
//        val computePipelineCreateInfo = vk.ComputePipelineCreateInfo(compute.pipelineLayout).apply {
//            // 1st pass
//            stage.loadShader("$assetPath/shaders/computenbody/particle_calculate.comp.spv", VkShaderStage.COMPUTE_BIT)
//        }
//
//        val specializationMapEntries = vk.SpecializationMapEntry(
//                0, specializationData.offset.sharedDataSize, Int.BYTES,
//                1, specializationData.offset.gravity, Float.BYTES,
//                2, specializationData.offset.power, Float.BYTES,
//                3, specializationData.offset.soften, Float.BYTES)
//
//        specializationData.apply {
//            sharedDataSize = 1024 min (vulkanDevice.properties.limits.maxComputeSharedMemorySize / Vec4.size)
//            gravity = 0.002f
//            power = 0.75f
//            soften = 0.05f
//        }
//        val specializationInfo = vk.SpecializationInfo(specializationMapEntries, specializationData.toBuffer())
//        computePipelineCreateInfo.stage.specializationInfo = specializationInfo
//
//        compute.pipelineCalculate = device.createComputePipelines(pipelineCache, computePipelineCreateInfo)
//
//        // 2nd pass
//        computePipelineCreateInfo.stage.loadShader("$assetPath/shaders/computenbody/particle_integrate.comp.spv", VkShaderStage.COMPUTE_BIT)
//        compute.pipelineIntegrate = device.createComputePipelines(pipelineCache, computePipelineCreateInfo)
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
//                VkDeviceSize( compute.ubo.size.L))
//
//        // Map for host access
//        compute.uniformBuffer.map()
//
//        // Vertex shader uniform buffer block
//        vulkanDevice.createBuffer(
//                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                graphics.uniformBuffer,
//                VkDeviceSize(graphics.ubo.size.L))
//
//        // Map for host access
//        graphics.uniformBuffer.map()
//
//        updateGraphicsUniformBuffers()
//    }
//
//    fun updateUniformBuffers() {
//        compute.ubo.deltaT = if (paused) 0f else frameTimer * 0.05f
//        compute.ubo.dest(sin((timer * 360f).rad) * 0.75f, 0f)
//        compute.ubo.pack()
//        memCopy(compute.ubo.address, compute.uniformBuffer.mapped, compute.ubo.size.L)
//    }
//
//    fun updateGraphicsUniformBuffers() {
//        graphics.ubo.projection put camera.matrices.perspective
//        graphics.ubo.view put camera.matrices.view
//        graphics.ubo.screenDim(size)
//        compute.ubo.pack()
//        memCopy(graphics.ubo.address, graphics.uniformBuffer.mapped, graphics.ubo.size.L)
//    }
//
//    fun draw() {
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
//
//        val computeSubmitInfo = vk.SubmitInfo { commandBuffer = compute.commandBuffer }
//
//        compute.queue.submit(computeSubmitInfo, compute.fence)
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
//        updateUniformBuffers()
//    }
//
//    override fun viewChanged() = updateGraphicsUniformBuffers()
//}