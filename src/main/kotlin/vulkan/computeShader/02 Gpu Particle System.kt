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
//import glm_.L
//import glm_.glm
//import glm_.random
//import glm_.vec2.Vec2
//import glm_.vec4.Vec4
//import org.lwjgl.system.MemoryUtil.NULL
//import org.lwjgl.vulkan.*
//import vkn.*
//import vulkan.PARTICLE_COUNT
//import vulkan.VERTEX_BUFFER_BIND_ID
//import vulkan.assetPath
//import vulkan.base.Buffer
//import vulkan.base.Texture2D
//import vulkan.base.VulkanExampleBase
//import vulkan.base.tools.VK_FLAGS_NONE
//
//
//fun main(args: Array<String>) {
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
//    var _timer = 0f
//    var animStart = 20f
//    var animate = true
//
//    private val textures = object {
//        val particle = Texture2D()
//        val gradient = Texture2D()
//    }
//
//    private val vertices = object {
//        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
//        lateinit var bindingDescriptions: VkVertexInputBindingDescription
//        lateinit var attributeDescriptions: VkVertexInputAttributeDescription
//    }
//
//    /** Resources for the graphics part of the example */
//    private val graphics = object {
//        var descriptorSetLayout: VkDescriptorSetLayout = NULL     // Particle system rendering shader binding layout
//        var descriptorSet: VkDescriptorSet = NULL                // Particle system rendering shader bindings
//        var pipelineLayout: VkPipelineLayout = NULL             // Layout of the graphics pipeline
//        var pipeline: VkPipeline = NULL                        // Particle rendering pipeline
//    }
//
//    /** Resources for the compute part of the example */
//    private val compute = object {
//        val storageBuffer = Buffer()                    // (Shader) storage buffer object containing the particles
//        val uniformBuffer = Buffer()                    // Uniform buffer object containing particle system parameters
//        lateinit var queue: VkQueue                                // Separate queue for compute commands (queue family may differ from the one used for graphics)
//        var commandPool: VkCommandPool = NULL                     // Use a separate command pool (queue family may differ from the one used for graphics)
//        lateinit var commandBuffer: VkCommandBuffer                 // Command buffer storing the dispatch commands and barriers
//        var fence: VkFence = NULL                                // Synchronization fence to avoid rewriting compute CB if still in use
//        var descriptorSetLayout: VkDescriptorSetLayout = NULL     // Compute shader binding layout
//        var descriptorSet: VkDescriptorSet = NULL                 // Compute shader bindings
//        var pipelineLayout: VkPipelineLayout = NULL            // Layout of the compute pipeline
//        var pipeline: VkPipeline = NULL                         // Compute pipeline for updating particle positions
//        val computeUBO = object {                            // Compute shader uniform block object
//            var deltaT = 0f                            //		Frame delta time
//            var dest = Vec2()                            //		x position of the attractor
//            var particleCount = PARTICLE_COUNT
//        }
//    }
//
//    // SSBO particle declaration
//    class Particle {
//        val pos = Vec2()            // Particle position
//        val vel = Vec2()            // Particle velocity
//        val gradientPos = Vec4()    // Texture coordiantes for the gradient ramp map
//
//        companion object {
//            val size = Vec2.size * 2 + Vec4.size
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
//        val clearValues = vk.ClearValue(2)
//        clearValues[0].color(defaultClearColor)
//        clearValues[1].depthStencil.set(1f, 0)
//
//        val renderPassBeginInfo = vk.RenderPassBeginInfo {
//            renderPass = this@GpuParticleSystem.renderPass
//            renderArea.apply {
//                offset.set(0, 0)
//                renderArea.extent.set(size.x, size.y)
//            }
//            this.clearValues = clearValues
//        }
//        for (i in drawCmdBuffers.indices) {
//            // Set target frame buffer
//            renderPassBeginInfo.framebuffer(frameBuffers[i])
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
//                    bufferMemoryBarriers = bufferBarrier)
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
//                    bufferMemoryBarriers = bufferBarrier)
//
//            end()
//        }
//    }
//
//    /** Setup and fill the compute shader storage buffers containing the particles */
//    fun prepareStorageBuffers() {
//
//        // Initial particle positions
//        val particleBuffer = ArrayList<Particle>(PARTICLE_COUNT)
//        for (particle in particleBuffer) {
//            particle.pos(glm.linearRand(-1f, 1f), glm.linearRand(-1f, 1f))
//            particle.vel(0f)
//            particle.gradientPos.x = particle.pos.x / 2f
//        }
//
//        val storageBufferSize: VkDeviceSize = particleBuffer.size * Particle.size.L
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
//                storageBufferSize,
//                particleBuffer)
//
//        vulkanDevice->createBuffer(
//        // The SSBO will be used as a storage buffer for the compute pipeline and as a vertex buffer in the graphics pipeline
//        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
//        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
//        &compute.storageBuffer,
//        storageBufferSize)
//
//        // Copy to staging buffer
//        VkCommandBuffer copyCmd = VulkanExampleBase ::createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, true)
//        VkBufferCopy copyRegion = {}
//        copyRegion.size = storageBufferSize
//        vkCmdCopyBuffer(copyCmd, stagingBuffer.buffer, compute.storageBuffer.buffer, 1, & copyRegion)
//        VulkanExampleBase::flushCommandBuffer(copyCmd, queue, true)
//
//        stagingBuffer.destroy()
//
//        // Binding description
//        vertices.bindingDescriptions.resize(1)
//        vertices.bindingDescriptions[0] =
//                vks::initializers::vertexInputBindingDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        sizeof(Particle),
//                        VK_VERTEX_INPUT_RATE_VERTEX)
//
//        // Attribute descriptions
//        // Describes memory layout and shader positions
//        vertices.attributeDescriptions.resize(2)
//        // Location 0 : Position
//        vertices.attributeDescriptions[0] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        0,
//                        VK_FORMAT_R32G32_SFLOAT,
//                        offsetof(Particle, pos))
//        // Location 1 : Gradient position
//        vertices.attributeDescriptions[1] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        1,
//                        VK_FORMAT_R32G32B32A32_SFLOAT,
//                        offsetof(Particle, gradientPos))
//
//        // Assign to vertex buffer
//        vertices.inputState = vks::initializers::pipelineVertexInputStateCreateInfo()
//        vertices.inputState.vertexBindingDescriptionCount = static_cast<uint32_t>(vertices.bindingDescriptions.size())
//        vertices.inputState.pVertexBindingDescriptions = vertices.bindingDescriptions.data()
//        vertices.inputState.vertexAttributeDescriptionCount = static_cast<uint32_t>(vertices.attributeDescriptions.size())
//        vertices.inputState.pVertexAttributeDescriptions = vertices.attributeDescriptions.data()
//    }
//
//    void setupDescriptorPool()
//    {
//        std::vector<VkDescriptorPoolSize> poolSizes =
//        {
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1),
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1),
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 2)
//        }
//
//        VkDescriptorPoolCreateInfo descriptorPoolInfo =
//        vks::initializers::descriptorPoolCreateInfo(
//                static_cast<uint32_t>(poolSizes.size()),
//                poolSizes.data(),
//                2)
//
//        VK_CHECK_RESULT(vkCreateDescriptorPool(device, & descriptorPoolInfo, nullptr, & descriptorPool))
//    }
//
//    void setupDescriptorSetLayout()
//    {
//        std::vector<VkDescriptorSetLayoutBinding> setLayoutBindings
//                // Binding 0 : Particle color map
//                setLayoutBindings.push_back(vks::initializers::descriptorSetLayoutBinding(
//                        VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                        VK_SHADER_STAGE_FRAGMENT_BIT,
//                        0))
//        // Binding 1 : Particle gradient ramp
//        setLayoutBindings.push_back(vks::initializers::descriptorSetLayoutBinding(
//                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                VK_SHADER_STAGE_FRAGMENT_BIT,
//                1))
//
//        VkDescriptorSetLayoutCreateInfo descriptorLayout =
//        vks::initializers::descriptorSetLayoutCreateInfo(
//                setLayoutBindings.data(),
//                static_cast<uint32_t>(setLayoutBindings.size()))
//
//        VK_CHECK_RESULT(vkCreateDescriptorSetLayout(device, & descriptorLayout, nullptr, & graphics . descriptorSetLayout))
//
//        VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo =
//        vks::initializers::pipelineLayoutCreateInfo(
//                & graphics . descriptorSetLayout,
//        1)
//
//        VK_CHECK_RESULT(vkCreatePipelineLayout(device, & pipelineLayoutCreateInfo, nullptr, & graphics . pipelineLayout))
//    }
//
//    void setupDescriptorSet()
//    {
//        VkDescriptorSetAllocateInfo allocInfo =
//        vks::initializers::descriptorSetAllocateInfo(
//                descriptorPool,
//                & graphics . descriptorSetLayout,
//        1)
//
//        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & graphics . descriptorSet))
//
//        std::vector<VkWriteDescriptorSet> writeDescriptorSets
//                // Binding 0 : Particle color map
//                writeDescriptorSets.push_back(vks::initializers::writeDescriptorSet(
//                        graphics.descriptorSet,
//                        VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                        0,
//                        & textures . particle . descriptor))
//        // Binding 1 : Particle gradient ramp
//        writeDescriptorSets.push_back(vks::initializers::writeDescriptorSet(
//                graphics.descriptorSet,
//                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                1,
//                & textures . gradient . descriptor))
//
//        vkUpdateDescriptorSets(device, static_cast<uint32_t>(writeDescriptorSets.size()), writeDescriptorSets.data(), 0, NULL)
//    }
//
//    void preparePipelines()
//    {
//        VkPipelineInputAssemblyStateCreateInfo inputAssemblyState =
//        vks::initializers::pipelineInputAssemblyStateCreateInfo(
//                VK_PRIMITIVE_TOPOLOGY_POINT_LIST,
//                0,
//                VK_FALSE)
//
//        VkPipelineRasterizationStateCreateInfo rasterizationState =
//        vks::initializers::pipelineRasterizationStateCreateInfo(
//                VK_POLYGON_MODE_FILL,
//                VK_CULL_MODE_NONE,
//                VK_FRONT_FACE_COUNTER_CLOCKWISE,
//                0)
//
//        VkPipelineColorBlendAttachmentState blendAttachmentState =
//        vks::initializers::pipelineColorBlendAttachmentState(
//                0xf,
//                VK_FALSE)
//
//        VkPipelineColorBlendStateCreateInfo colorBlendState =
//        vks::initializers::pipelineColorBlendStateCreateInfo(
//                1,
//                & blendAttachmentState)
//
//        VkPipelineDepthStencilStateCreateInfo depthStencilState =
//        vks::initializers::pipelineDepthStencilStateCreateInfo(
//                VK_FALSE,
//                VK_FALSE,
//                VK_COMPARE_OP_ALWAYS)
//
//        VkPipelineViewportStateCreateInfo viewportState =
//        vks::initializers::pipelineViewportStateCreateInfo(1, 1, 0)
//
//        VkPipelineMultisampleStateCreateInfo multisampleState =
//        vks::initializers::pipelineMultisampleStateCreateInfo(
//                VK_SAMPLE_COUNT_1_BIT,
//                0)
//
//        std::vector<VkDynamicState> dynamicStateEnables = { VK_DYNAMIC_STATE_VIEWPORT,
//                                                            VK_DYNAMIC_STATE_SCISSOR
//        }
//        VkPipelineDynamicStateCreateInfo dynamicState =
//        vks::initializers::pipelineDynamicStateCreateInfo(
//                dynamicStateEnables.data(),
//                static_cast<uint32_t>(dynamicStateEnables.size()),
//                0)
//
//        // Rendering pipeline
//        // Load shaders
//        std::array < VkPipelineShaderStageCreateInfo, 2> shaderStages
//
//        shaderStages[0] = loadShader(getAssetPath() + "shaders/computeparticles/particle.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
//        shaderStages[1] = loadShader(getAssetPath() + "shaders/computeparticles/particle.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)
//
//        VkGraphicsPipelineCreateInfo pipelineCreateInfo =
//        vks::initializers::pipelineCreateInfo(
//                graphics.pipelineLayout,
//                renderPass,
//                0)
//
//        pipelineCreateInfo.pVertexInputState = & vertices . inputState
//                pipelineCreateInfo.pInputAssemblyState = & inputAssemblyState
//                pipelineCreateInfo.pRasterizationState = & rasterizationState
//                pipelineCreateInfo.pColorBlendState = & colorBlendState
//                pipelineCreateInfo.pMultisampleState = & multisampleState
//                pipelineCreateInfo.pViewportState = & viewportState
//                pipelineCreateInfo.pDepthStencilState = & depthStencilState
//                pipelineCreateInfo.pDynamicState = & dynamicState
//                pipelineCreateInfo.stageCount = static_cast<uint32_t>(shaderStages.size())
//        pipelineCreateInfo.pStages = shaderStages.data()
//        pipelineCreateInfo.renderPass = renderPass
//
//        // Additive blending
//        blendAttachmentState.colorWriteMask = 0xF
//        blendAttachmentState.blendEnable = VK_TRUE
//        blendAttachmentState.colorBlendOp = VK_BLEND_OP_ADD
//        blendAttachmentState.srcColorBlendFactor = VK_BLEND_FACTOR_ONE
//        blendAttachmentState.dstColorBlendFactor = VK_BLEND_FACTOR_ONE
//        blendAttachmentState.alphaBlendOp = VK_BLEND_OP_ADD
//        blendAttachmentState.srcAlphaBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA
//        blendAttachmentState.dstAlphaBlendFactor = VK_BLEND_FACTOR_DST_ALPHA
//
//        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & graphics . pipeline))
//    }
//
//    void prepareCompute()
//    {
//        // Create a compute capable device queue
//        // The VulkanDevice::createLogicalDevice functions finds a compute capable queue and prefers queue families that only support compute
//        // Depending on the implementation this may result in different queue family indices for graphics and computes,
//        // requiring proper synchronization (see the memory barriers in buildComputeCommandBuffer)
//        vkGetDeviceQueue(device, vulkanDevice->queueFamilyIndices.compute, 0, &compute.queue)
//
//        // Create compute pipeline
//        // Compute pipelines are created separate from graphics pipelines even if they use the same queue (family index)
//
//        std::vector<VkDescriptorSetLayoutBinding> setLayoutBindings = {
//            // Binding 0 : Particle position storage buffer
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
//                    VK_SHADER_STAGE_COMPUTE_BIT,
//                    0),
//            // Binding 1 : Uniform buffer
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    VK_SHADER_STAGE_COMPUTE_BIT,
//                    1),
//        }
//
//        VkDescriptorSetLayoutCreateInfo descriptorLayout =
//        vks::initializers::descriptorSetLayoutCreateInfo(
//                setLayoutBindings.data(),
//                static_cast<uint32_t>(setLayoutBindings.size()))
//
//        VK_CHECK_RESULT(vkCreateDescriptorSetLayout(device, & descriptorLayout, nullptr, & compute . descriptorSetLayout))
//
//        VkPipelineLayoutCreateInfo pPipelineLayoutCreateInfo =
//        vks::initializers::pipelineLayoutCreateInfo(
//                & compute . descriptorSetLayout,
//        1)
//
//        VK_CHECK_RESULT(vkCreatePipelineLayout(device, & pPipelineLayoutCreateInfo, nullptr, & compute . pipelineLayout))
//
//        VkDescriptorSetAllocateInfo allocInfo =
//        vks::initializers::descriptorSetAllocateInfo(
//                descriptorPool,
//                & compute . descriptorSetLayout,
//        1)
//
//        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & compute . descriptorSet))
//
//        std::vector<VkWriteDescriptorSet> computeWriteDescriptorSets =
//        {
//            // Binding 0 : Particle position storage buffer
//            vks::initializers::writeDescriptorSet(
//                    compute.descriptorSet,
//                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
//                    0,
//                    & compute . storageBuffer . descriptor),
//            // Binding 1 : Uniform buffer
//            vks::initializers::writeDescriptorSet(
//                    compute.descriptorSet,
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    1,
//                    & compute . uniformBuffer . descriptor)
//        }
//
//        vkUpdateDescriptorSets(device, static_cast<uint32_t>(computeWriteDescriptorSets.size()), computeWriteDescriptorSets.data(), 0, NULL)
//
//        // Create pipeline
//        VkComputePipelineCreateInfo computePipelineCreateInfo = vks ::initializers::computePipelineCreateInfo(compute.pipelineLayout, 0)
//        computePipelineCreateInfo.stage = loadShader(getAssetPath() + "shaders/computeparticles/particle.comp.spv", VK_SHADER_STAGE_COMPUTE_BIT)
//        VK_CHECK_RESULT(vkCreateComputePipelines(device, pipelineCache, 1, & computePipelineCreateInfo, nullptr, & compute . pipeline))
//
//        // Separate command pool as queue family for compute may be different than graphics
//        VkCommandPoolCreateInfo cmdPoolInfo = {}
//        cmdPoolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO
//        cmdPoolInfo.queueFamilyIndex = vulkanDevice->queueFamilyIndices.compute
//        cmdPoolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT
//        VK_CHECK_RESULT(vkCreateCommandPool(device, & cmdPoolInfo, nullptr, & compute . commandPool))
//
//        // Create a command buffer for compute operations
//        VkCommandBufferAllocateInfo cmdBufAllocateInfo =
//        vks::initializers::commandBufferAllocateInfo(
//                compute.commandPool,
//                VK_COMMAND_BUFFER_LEVEL_PRIMARY,
//                1)
//
//        VK_CHECK_RESULT(vkAllocateCommandBuffers(device, & cmdBufAllocateInfo, & compute . commandBuffer))
//
//        // Fence for compute CB sync
//        VkFenceCreateInfo fenceCreateInfo = vks ::initializers::fenceCreateInfo(VK_FENCE_CREATE_SIGNALED_BIT)
//        VK_CHECK_RESULT(vkCreateFence(device, & fenceCreateInfo, nullptr, & compute . fence))
//
//        // Build a single command buffer containing the compute dispatch commands
//        buildComputeCommandBuffer()
//    }
//
//    // Prepare and initialize uniform buffer containing shader uniforms
//    void prepareUniformBuffers()
//    {
//        // Compute shader uniform buffer block
//        vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &compute.uniformBuffer,
//        sizeof(compute.ubo))
//
//        // Map for host access
//        VK_CHECK_RESULT(compute.uniformBuffer.map())
//
//        updateUniformBuffers()
//    }
//
//    void updateUniformBuffers()
//    {
//        compute.ubo.deltaT = frameTimer * 2.5f
//        if (animate) {
//            compute.ubo.destX = sin(glm::radians(timer * 360.0f)) * 0.75f
//            compute.ubo.destY = 0.0f
//        } else {
//            float normalizedMx =(mousePos.x - static_cast<float>(width / 2)) / static_cast<float>(width / 2)
//            float normalizedMy =(mousePos.y - static_cast<float>(height / 2)) / static_cast<float>(height / 2)
//            compute.ubo.destX = normalizedMx
//            compute.ubo.destY = normalizedMy
//        }
//
//        memcpy(compute.uniformBuffer.mapped, & compute . ubo, sizeof(compute.ubo))
//    }
//
//    void draw()
//    {
//        VkSubmitInfo computeSubmitInfo = vks ::initializers::submitInfo()
//        computeSubmitInfo.commandBufferCount = 1
//        computeSubmitInfo.pCommandBuffers = & compute . commandBuffer
//
//                VK_CHECK_RESULT(vkQueueSubmit(compute.queue, 1, & computeSubmitInfo, compute.fence) )
//
//        // Submit graphics commands
//        VulkanExampleBase::prepareFrame()
//
//        submitInfo.commandBufferCount = 1
//        submitInfo.pCommandBuffers = & drawCmdBuffers [currentBuffer]
//        VK_CHECK_RESULT(vkQueueSubmit(queue, 1, & submitInfo, VK_NULL_HANDLE))
//
//        VulkanExampleBase::submitFrame()
//
//        // Submit compute commands
//        vkWaitForFences(device, 1, & compute . fence, VK_TRUE, UINT64_MAX)
//        vkResetFences(device, 1, & compute . fence)
//    }
//
//    void prepare()
//    {
//        VulkanExampleBase::prepare()
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
//    }
//
//    virtual void render()
//    {
//        if (!prepared)
//            return
//        draw()
//
//        if (animate) {
//            if (animStart > 0.0f) {
//                animStart -= frameTimer * 5.0f
//            } else if (animStart <= 0.0f) {
//                timer += frameTimer * 0.04f
//                if (timer > 1.f)
//                    timer = 0.f
//            }
//        }
//
//        updateUniformBuffers()
//    }
//
//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
//    {
//        if (overlay->header("Settings")) { overlay ->
//        checkBox("Moving attractor", & animate)
//    }
//    }
//}