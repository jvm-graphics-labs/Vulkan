///*
//* Vulkan Example - Compute shader ray tracing
//*
//* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
//*
//* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
//*/
//
//package vulkan.computeShader
//
//import glm_.L
//import glm_.vec2.Vec2i
//import glm_.vec3.Vec3
//import glm_.vec3.Vec3i
//import glm_.vec4.Vec4
//import org.lwjgl.system.MemoryUtil.NULL
//import org.lwjgl.vulkan.VkCommandBuffer
//import org.lwjgl.vulkan.VkQueue
//import vkn.*
//import vulkan.base.*
//import vulkan.base.tools.VK_FLAGS_NONE
//
//
//fun main(args: Array<String>) {
//    RayTracing().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//class RayTracing : VulkanExampleBase() {
//
//    val textureComputeTarget = Texture()
//
//    /** Resources for the graphics part of the example */
//    object graphics {
//        var descriptorSetLayout: VkDescriptorSetLayout = NULL   // Raytraced image display shader binding layout
//        var descriptorSetPreCompute: VkDescriptorSet = NULL     // Raytraced image display shader bindings before compute shader image manipulation
//        var descriptorSet: VkDescriptorSet = NULL               // Raytraced image display shader bindings after compute shader image manipulation
//        var pipeline: VkPipeline = NULL                         // Raytraced image display pipeline
//        var pipelineLayout: VkPipelineLayout = NULL             // Layout of the graphics pipeline
//    }
//
//    /** Resources for the compute part of the example */
//    object compute {
//        object storageBuffers {
//            val spheres = Buffer()  // (Shader) storage buffer object with scene spheres
//            val planes = Buffer()   // (Shader) storage buffer object with scene planes
//        }
//
//        val uniformBuffer = Buffer()                            // Uniform buffer object containing scene data
//        lateinit var queue: VkQueue                             // Separate queue for compute commands (queue family may differ from the one used for graphics)
//        var commandPool: VkCommandPool = NULL                   // Use a separate command pool (queue family may differ from the one used for graphics)
//        lateinit var commandBuffer: VkCommandBuffer             // Command buffer storing the dispatch commands and barriers
//        var fence: VkFence = NULL                               // Synchronization fence to avoid rewriting compute CB if still in use
//        var descriptorSetLayout: VkDescriptorSetLayout = NULL   // Compute shader binding layout
//        var descriptorSet: VkDescriptorSet = NULL               // Compute shader bindings
//        var pipelineLayout: VkPipelineLayout = NULL             // Layout of the compute pipeline
//        var pipeline: VkPipeline = NULL                         // Compute raytracing pipeline
//
//        object ubo {
//            // Compute shader uniform block object
//            var lightPos = Vec3()
//            var aspectRatio = 0f                        // Aspect ratio of the viewport
//            var fogColor = Vec4(0f)
//
//            object camera {
//                var pos = Vec3(0f, 0f, 4f)
//                var lookat = Vec3(0f, 0.5f, 0f)
//                var fov = 10f
//            }
//        }
//    }
//
//    // SSBO sphere declaration
//    class Sphere( // Shader uses std140 layout (so we only use vec4 instead of vec3)
//            val pos: Vec3,
//            val radius: Float,
//            val diffuse: Vec3,
//            val specular: Float,
//            /** Id used to identify sphere for raytracing */
//            val id: Int) : Bufferizable() {
//
//        lateinit var _pad: Vec3i
//
//        override val fieldOrder = arrayOf("pos", "radius", "diffuse", "specular", "id")
//
//        companion object {
//            val size = Vec4.size * 3
//        }
//    }
//
//    // SSBO plane declaration
//    class Plane (
//        val normal: Vec3,
//        var distance: Float,
//        val diffuse: Vec3,
//        val specular: Float,
//        val id: Int) : Bufferizable() {
//
//        lateinit var _pad: Vec3i
//    }
//
//    init {
//        title = "Compute shader ray tracing"
////        settings.overlay = true
//        compute.ubo.aspectRatio = size.aspect
//        timerSpeed *= 0.25f
//
//        camera.type = Camera.CameraType.lookAt
//        camera.setPerspective(60f, size.aspect, 0.1f, 512f)
//        camera.setRotation(Vec3())
//        camera.setTranslation(Vec3(0f, 0f, -4f))
//        camera.rotationSpeed = 0f
//        camera.movementSpeed = 2.5f
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
//            destroyPipeline(compute.pipeline)
//            destroyPipelineLayout(compute.pipelineLayout)
//            destroyDescriptorSetLayout(compute.descriptorSetLayout)
//            destroyFence(compute.fence)
//            destroyCommandPool(compute.commandPool)
//            compute.uniformBuffer.destroy()
//            compute.storageBuffers.spheres.destroy()
//            compute.storageBuffers.planes.destroy()
//
//            textureComputeTarget.destroy()
//        }
//    }
//
//    /** Prepare a texture target that is used to store compute shader calculations */
//    fun prepareTextureTarget(tex: Texture, size: Vec2i, format: VkFormat) {
//        // Get device properties for the requested texture format
//        val formatProperties = physicalDevice getFormatProperties format
//        // Check if requested image format supports image storage operations
//        assert(formatProperties.optimalTilingFeatures has VkFormatFeature.STORAGE_IMAGE_BIT)
//
//        // Prepare blit target texture
//        tex.size(size)
//
//        val imageCreateInfo = vk.ImageCreateInfo {
//            imageType = VkImageType.`2D`
//            this.format = format
//            extent.set(size.x, size.y, 1)
//            mipLevels = 1
//            arrayLayers = 1
//            samples = VkSampleCount.`1_BIT`
//            tiling = VkImageTiling.OPTIMAL
//            initialLayout = VkImageLayout.UNDEFINED
//            // Image will be sampled in the fragment shader and used as storage target in the compute shader
//            usage = VkImageUsage.SAMPLED_BIT or VkImageUsage.STORAGE_BIT
//            flags = 0
//        }
//
//        tex.image = device createImage imageCreateInfo
//        val memReqs = device.getImageMemoryRequirements(tex.image)
//        val memAllocInfo = vk.MemoryAllocateInfo {
//            allocationSize = memReqs.size
//            memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
//        }
//        tex.deviceMemory = device allocateMemory memAllocInfo
//        device.bindImageMemory(tex.image, tex.deviceMemory)
//
//        val layoutCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)
//
//        tex.imageLayout = VkImageLayout.GENERAL
//        tools.setImageLayout(
//                layoutCmd,
//                tex.image,
//                VkImageAspect.COLOR_BIT.i,
//                VkImageLayout.UNDEFINED,
//                tex.imageLayout)
//
//        super.flushCommandBuffer(layoutCmd, queue, true)
//
//        // Create sampler
//        val sampler = vk.SamplerCreateInfo {
//            magFilter = VkFilter.LINEAR
//            minFilter = VkFilter.LINEAR
//            mipmapMode = VkSamplerMipmapMode.LINEAR
//            addressModeU = VkSamplerAddressMode.CLAMP_TO_BORDER
//            addressModeV = addressModeU
//            addressModeW = addressModeU
//            mipLodBias = 0f
//            maxAnisotropy = 1f
//            compareOp = VkCompareOp.NEVER
//            minLod = 0f
//            maxLod = 0f
//            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
//        }
//        tex.sampler = device createSampler sampler
//
//        // Create image view
//        val view = vk.ImageViewCreateInfo {
//            viewType = VkImageViewType.`2D`
//            this.format = format
//            components(VkComponentSwizzle.R, VkComponentSwizzle.G, VkComponentSwizzle.B, VkComponentSwizzle.A)
//            subresourceRange.set(VkImageAspect.COLOR_BIT.i, 0, 1, 0, 1)
//            image = tex.image
//        }
//        tex.view = device createImageView view
//
//        // Initialize a descriptor for later use
//        tex.descriptor.imageLayout = tex.imageLayout
//        tex.descriptor.imageView = tex.view
//        tex.descriptor.sampler = tex.sampler
//        tex.device = vulkanDevice
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
//            renderPass = this@RayTracing.renderPass
//            renderArea.apply {
//                offset.set(0, 0)
//                extent.set(size.x, size.y)
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
//                // Image memory barrier to make sure that compute shader writes are finished before sampling from the texture
//                val imageMemoryBarrier = vk.ImageMemoryBarrier {
//                    oldLayout = VkImageLayout.GENERAL
//                    newLayout = VkImageLayout.GENERAL
//                    image = textureComputeTarget.image
//                    subresourceRange.set(VkImageAspect.COLOR_BIT.i, 0, 1, 0, 1)
//                    srcAccessMask = VkAccess.SHADER_WRITE_BIT.i
//                    dstAccessMask = VkAccess.SHADER_READ_BIT.i
//                }
//                pipelineBarrier(
//                        VkPipelineStage.COMPUTE_SHADER_BIT.i,
//                        VkPipelineStage.FRAGMENT_SHADER_BIT.i,
//                        VK_FLAGS_NONE,
//                        imageMemoryBarrier = imageMemoryBarrier)
//
//                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)
//
//                setViewport(size)
//                setScissor(size)
//
//                // Display ray traced image generated by compute shader as a full screen quad
//                // Quad vertices are generated in the vertex shader
//                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, graphics.pipelineLayout, graphics.descriptorSet)
//                bindPipeline(VkPipelineBindPoint.GRAPHICS, graphics.pipeline)
//                draw(3, 1, 0, 0)
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
//        compute.commandBuffer.apply {
//
//            val cmdBufInfo = vk.CommandBufferBeginInfo()
//
//            begin(cmdBufInfo)
//
//            bindPipeline(VkPipelineBindPoint.COMPUTE, compute.pipeline)
//            bindDescriptorSets(VkPipelineBindPoint.COMPUTE, compute.pipelineLayout, compute.descriptorSet)
//
//            dispatch(textureComputeTarget.size / 16, 1)
//
//            end()
//        }
//    }
//
//    /** Id used to identify objects by the ray tracing shader */
//    var currentId = 0
//
//    /** Setup and fill the compute shader storage buffers containing primitives for the raytraced scene */
//    fun prepareStorageBuffers() {
//
//        // Spheres
//        val spheres = bufferOf(
//                Sphere(Vec3(1.75f, -0.5f, 0.0f), 1f, Vec3(0f, 1f, 0f), 32f, currentId++),
//                Sphere(Vec3(0f, 1f, -0.5f), 1f, Vec3(0.65f, 0.77f, 0.97f), 32f, currentId++),
//                Sphere(Vec3(-1.75f, -0.75f, -0.5f), 1.25f, Vec3(0.9f, 0.76f, 0.46f), 32f, currentId++))
//        val storageBufferSize: VkDeviceSize = spheres.size * Sphere.size.L
//
//        // Stage
//        val stagingBuffer = Buffer()
//
//        vulkanDevice.createBuffer(
//                VkBufferUsage.TRANSFER_SRC_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                stagingBuffer,
//                storageBufferSize,
//                spheres)
//
//        vulkanDevice->createBuffer(
//        // The SSBO will be used as a storage buffer for the compute pipeline and as a vertex buffer in the graphics pipeline
//        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
//        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
//        &compute.storageBuffers.spheres,
//        storageBufferSize)
//
//        // Copy to staging buffer
//        VkCommandBuffer copyCmd = VulkanExampleBase ::createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, true)
//        VkBufferCopy copyRegion = {}
//        copyRegion.size = storageBufferSize
//        vkCmdCopyBuffer(copyCmd, stagingBuffer.buffer, compute.storageBuffers.spheres.buffer, 1, & copyRegion)
//        VulkanExampleBase::flushCommandBuffer(copyCmd, queue, true)
//
//        stagingBuffer.destroy()
//
//        // Planes
//        std::vector<Plane> planes
//                const float roomDim = 4.0f
//        planes.push_back(newPlane(glm::vec3(0.0f, 1.0f, 0.0f), roomDim, glm::vec3(1.0f), 32.0f))
//        planes.push_back(newPlane(glm::vec3(0.0f, -1.0f, 0.0f), roomDim, glm::vec3(1.0f), 32.0f))
//        planes.push_back(newPlane(glm::vec3(0.0f, 0.0f, 1.0f), roomDim, glm::vec3(1.0f), 32.0f))
//        planes.push_back(newPlane(glm::vec3(0.0f, 0.0f, -1.0f), roomDim, glm::vec3(0.0f), 32.0f))
//        planes.push_back(newPlane(glm::vec3(-1.0f, 0.0f, 0.0f), roomDim, glm::vec3(1.0f, 0.0f, 0.0f), 32.0f))
//        planes.push_back(newPlane(glm::vec3(1.0f, 0.0f, 0.0f), roomDim, glm::vec3(0.0f, 1.0f, 0.0f), 32.0f))
//        storageBufferSize = planes.size() * sizeof(Plane)
//
//        // Stage
//        vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &stagingBuffer,
//        storageBufferSize,
//        planes.data())
//
//        vulkanDevice->createBuffer(
//        // The SSBO will be used as a storage buffer for the compute pipeline and as a vertex buffer in the graphics pipeline
//        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_TRANSFER_DST_BIT,
//        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
//        &compute.storageBuffers.planes,
//        storageBufferSize)
//
//        // Copy to staging buffer
//        copyCmd = VulkanExampleBase::createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, true)
//        copyRegion.size = storageBufferSize
//        vkCmdCopyBuffer(copyCmd, stagingBuffer.buffer, compute.storageBuffers.planes.buffer, 1, & copyRegion)
//        VulkanExampleBase::flushCommandBuffer(copyCmd, queue, true)
//
//        stagingBuffer.destroy()
//    }
//
//    void setupDescriptorPool()
//    {
//        std::vector<VkDescriptorPoolSize> poolSizes =
//        {
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 2),            // Compute UBO
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 4),    // Graphics image samplers
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1),                // Storage image for ray traced image output
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 2),            // Storage buffer for the scene primitives
//        }
//
//        VkDescriptorPoolCreateInfo descriptorPoolInfo =
//        vks::initializers::descriptorPoolCreateInfo(
//                poolSizes.size(),
//                poolSizes.data(),
//                3)
//
//        VK_CHECK_RESULT(vkCreateDescriptorPool(device, & descriptorPoolInfo, nullptr, & descriptorPool))
//    }
//
//    void setupDescriptorSetLayout()
//    {
//        std::vector<VkDescriptorSetLayoutBinding> setLayoutBindings =
//        {
//            // Binding 0 : Fragment shader image sampler
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    VK_SHADER_STAGE_FRAGMENT_BIT,
//                    0)
//        }
//
//        VkDescriptorSetLayoutCreateInfo descriptorLayout =
//        vks::initializers::descriptorSetLayoutCreateInfo(
//                setLayoutBindings.data(),
//                setLayoutBindings.size())
//
//        VK_CHECK_RESULT(vkCreateDescriptorSetLayout(device, & descriptorLayout, nullptr, & graphics . descriptorSetLayout))
//
//        VkPipelineLayoutCreateInfo pPipelineLayoutCreateInfo =
//        vks::initializers::pipelineLayoutCreateInfo(
//                & graphics . descriptorSetLayout,
//        1)
//
//        VK_CHECK_RESULT(vkCreatePipelineLayout(device, & pPipelineLayoutCreateInfo, nullptr, & graphics . pipelineLayout))
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
//        std::vector<VkWriteDescriptorSet> writeDescriptorSets =
//        {
//            // Binding 0 : Fragment shader texture sampler
//            vks::initializers::writeDescriptorSet(
//                    graphics.descriptorSet,
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    0,
//                    & textureComputeTarget . descriptor)
//        }
//
//        vkUpdateDescriptorSets(device, writeDescriptorSets.size(), writeDescriptorSets.data(), 0, NULL)
//    }
//
//    void preparePipelines()
//    {
//        VkPipelineInputAssemblyStateCreateInfo inputAssemblyState =
//        vks::initializers::pipelineInputAssemblyStateCreateInfo(
//                VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
//                0,
//                VK_FALSE)
//
//        VkPipelineRasterizationStateCreateInfo rasterizationState =
//        vks::initializers::pipelineRasterizationStateCreateInfo(
//                VK_POLYGON_MODE_FILL,
//                VK_CULL_MODE_FRONT_BIT,
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
//                VK_COMPARE_OP_LESS_OR_EQUAL)
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
//                dynamicStateEnables.size(),
//                0)
//
//        // Display pipeline
//        std::array < VkPipelineShaderStageCreateInfo, 2> shaderStages
//
//        shaderStages[0] = loadShader(getAssetPath() + "shaders/raytracing/texture.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
//        shaderStages[1] = loadShader(getAssetPath() + "shaders/raytracing/texture.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)
//
//        VkGraphicsPipelineCreateInfo pipelineCreateInfo =
//        vks::initializers::pipelineCreateInfo(
//                graphics.pipelineLayout,
//                renderPass,
//                0)
//
//        VkPipelineVertexInputStateCreateInfo emptyInputState {}
//        emptyInputState.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO
//        emptyInputState.vertexAttributeDescriptionCount = 0
//        emptyInputState.pVertexAttributeDescriptions = nullptr
//        emptyInputState.vertexBindingDescriptionCount = 0
//        emptyInputState.pVertexBindingDescriptions = nullptr
//        pipelineCreateInfo.pVertexInputState = & emptyInputState
//
//                pipelineCreateInfo.pInputAssemblyState = & inputAssemblyState
//                pipelineCreateInfo.pRasterizationState = & rasterizationState
//                pipelineCreateInfo.pColorBlendState = & colorBlendState
//                pipelineCreateInfo.pMultisampleState = & multisampleState
//                pipelineCreateInfo.pViewportState = & viewportState
//                pipelineCreateInfo.pDepthStencilState = & depthStencilState
//                pipelineCreateInfo.pDynamicState = & dynamicState
//                pipelineCreateInfo.stageCount = shaderStages.size()
//        pipelineCreateInfo.pStages = shaderStages.data()
//        pipelineCreateInfo.renderPass = renderPass
//
//        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & graphics . pipeline))
//    }
//
//    // Prepare the compute pipeline that generates the ray traced image
//    void prepareCompute()
//    {
//        // Create a compute capable device queue
//        // The VulkanDevice::createLogicalDevice functions finds a compute capable queue and prefers queue families that only support compute
//        // Depending on the implementation this may result in different queue family indices for graphics and computes,
//        // requiring proper synchronization (see the memory barriers in buildComputeCommandBuffer)
//        VkDeviceQueueCreateInfo queueCreateInfo = {}
//        queueCreateInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO
//        queueCreateInfo.pNext = NULL
//        queueCreateInfo.queueFamilyIndex = vulkanDevice->queueFamilyIndices.compute
//        queueCreateInfo.queueCount = 1
//        vkGetDeviceQueue(device, vulkanDevice->queueFamilyIndices.compute, 0, &compute.queue)
//
//        std::vector<VkDescriptorSetLayoutBinding> setLayoutBindings = {
//            // Binding 0: Storage image (raytraced output)
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
//                    VK_SHADER_STAGE_COMPUTE_BIT,
//                    0),
//            // Binding 1: Uniform buffer block
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    VK_SHADER_STAGE_COMPUTE_BIT,
//                    1),
//            // Binding 1: Shader storage buffer for the spheres
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
//                    VK_SHADER_STAGE_COMPUTE_BIT,
//                    2),
//            // Binding 1: Shader storage buffer for the planes
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
//                    VK_SHADER_STAGE_COMPUTE_BIT,
//                    3)
//        }
//
//        VkDescriptorSetLayoutCreateInfo descriptorLayout =
//        vks::initializers::descriptorSetLayoutCreateInfo(
//                setLayoutBindings.data(),
//                setLayoutBindings.size())
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
//            // Binding 0: Output storage image
//            vks::initializers::writeDescriptorSet(
//                    compute.descriptorSet,
//                    VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
//                    0,
//                    & textureComputeTarget . descriptor),
//            // Binding 1: Uniform buffer block
//            vks::initializers::writeDescriptorSet(
//                    compute.descriptorSet,
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    1,
//                    & compute . uniformBuffer . descriptor),
//            // Binding 2: Shader storage buffer for the spheres
//            vks::initializers::writeDescriptorSet(
//                    compute.descriptorSet,
//                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
//                    2,
//                    & compute . storageBuffers . spheres . descriptor),
//            // Binding 2: Shader storage buffer for the planes
//            vks::initializers::writeDescriptorSet(
//                    compute.descriptorSet,
//                    VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
//                    3,
//                    & compute . storageBuffers . planes . descriptor)
//        }
//
//        vkUpdateDescriptorSets(device, computeWriteDescriptorSets.size(), computeWriteDescriptorSets.data(), 0, NULL)
//
//        // Create compute shader pipelines
//        VkComputePipelineCreateInfo computePipelineCreateInfo =
//        vks::initializers::computePipelineCreateInfo(
//                compute.pipelineLayout,
//                0)
//
//        computePipelineCreateInfo.stage = loadShader(getAssetPath() + "shaders/raytracing/raytracing.comp.spv", VK_SHADER_STAGE_COMPUTE_BIT)
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
//        // Compute shader parameter uniform buffer block
//        vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &compute.uniformBuffer,
//        sizeof(compute.ubo))
//
//        updateUniformBuffers()
//    }
//
//    void updateUniformBuffers()
//    {
//        compute.ubo.lightPos.x = 0.0f + sin(glm::radians(timer * 360.0f)) * cos(glm::radians(timer * 360.0f)) * 2.0f
//        compute.ubo.lightPos.y = 0.0f + sin(glm::radians(timer * 360.0f)) * 2.0f
//        compute.ubo.lightPos.z = 0.0f + cos(glm::radians(timer * 360.0f)) * 2.0f
//        compute.ubo.camera.pos = camera.position * -1.0f
//        VK_CHECK_RESULT(compute.uniformBuffer.map())
//        memcpy(compute.uniformBuffer.mapped, & compute . ubo, sizeof(compute.ubo))
//        compute.uniformBuffer.unmap()
//    }
//
//    void draw()
//    {
//        VulkanExampleBase::prepareFrame()
//
//        // Command buffer to be sumitted to the queue
//        submitInfo.commandBufferCount = 1
//        submitInfo.pCommandBuffers = & drawCmdBuffers [currentBuffer]
//        VK_CHECK_RESULT(vkQueueSubmit(queue, 1, & submitInfo, VK_NULL_HANDLE))
//
//        VulkanExampleBase::submitFrame()
//
//        // Submit compute commands
//        // Use a fence to ensure that compute command buffer has finished executing before using it again
//        vkWaitForFences(device, 1, & compute . fence, VK_TRUE, UINT64_MAX)
//        vkResetFences(device, 1, & compute . fence)
//
//        VkSubmitInfo computeSubmitInfo = vks ::initializers::submitInfo()
//        computeSubmitInfo.commandBufferCount = 1
//        computeSubmitInfo.pCommandBuffers = & compute . commandBuffer
//
//                VK_CHECK_RESULT(vkQueueSubmit(compute.queue, 1, & computeSubmitInfo, compute.fence))
//    }
//
//    void prepare()
//    {
//        VulkanExampleBase::prepare()
//        prepareStorageBuffers()
//        prepareUniformBuffers()
//        prepareTextureTarget(& textureComputeTarget, TEX_DIM, TEX_DIM, VK_FORMAT_R8G8B8A8_UNORM)
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
//        if (!paused) {
//            updateUniformBuffers()
//        }
//    }
//
//    virtual void viewChanged()
//    {
//        compute.ubo.aspectRatio = (float) width /(float) height
//                updateUniformBuffers()
//    }
//}
//
//VULKAN_EXAMPLE_MAIN()