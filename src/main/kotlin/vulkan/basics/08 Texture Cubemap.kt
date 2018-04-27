///*
//* Vulkan Example - Cube map texture loading and displaying
//*
//* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
//*
//* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
//*/
//
//package vulkan.basics
//
//import gli_.gli
//import glm_.L
//import glm_.mat4x4.Mat4
//import org.lwjgl.system.MemoryUtil.NULL
//import org.lwjgl.system.MemoryUtil.memCopy
//import vkn.*
//import vulkan.base.*
//import java.net.URI
//
//fun main(args: Array<String>) {
//    TextureCubemap().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//private class TextureCubemap : VulkanExampleBase() {
//
//    var displaySkybox = true
//
//    val cubeMap = Texture()
//
//    // Vertex layout for the models
//    val vertexLayout = VertexLayout(
//            VertexComponent.POSITION,
//            VertexComponent.NORMAL,
//            VertexComponent.UV)
//
//    private val models = object {
//        val skybox = Model()
//        val objects = ArrayList<Model>()
//        var objectIndex = 0
//    }
//
//    private val uniformBuffers = object {
//        val `object` = Buffer()
//        val skybox = Buffer()
//    }
//
//    private val uboVS = object {
//        var projection = Mat4()
//        var model = Mat4()
//        var lodBias = 0f
//    }
//
//    private val pipelines = object {
//        var skybox: VkPipeline = NULL
//        var reflect: VkPipeline = NULL
//    }
//
//    private val descriptorSets = object {
//        var `object`: VkDescriptorSet = NULL
//        var skybox: VkDescriptorSet = NULL
//    }
//
//    var pipelineLayout: VkPipelineLayout = NULL
//    var descriptorSetLayout: VkDescriptorSetLayout = NULL
//
//    val objectNames = ArrayList<String>()
//
//    init {
//        zoom = -4f
//        rotationSpeed = 0.25f
//        rotation(-7.25f, -120f, 0f)
//        title = "Cube map textures"
////        settings.overlay = true
//    }
//
//    override fun destroy() {
//
//        // Clean up used Vulkan resources
//        // Note : Inherited destructor cleans up resources stored in base class
//
//        device.apply {
//            // Clean up texture resources
//            destroyImageView(cubeMap.view)
//            destroyImage(cubeMap.image)
//            destroySampler(cubeMap.sampler)
//            freeMemory(cubeMap.deviceMemory)
//
//            destroyPipeline(pipelines.skybox)
//            destroyPipeline(pipelines.reflect)
//
//            destroyPipelineLayout(pipelineLayout)
//            destroyDescriptorSetLayout(descriptorSetLayout)
//        }
//        for (model in models.objects)
//            model.destroy()
//
//        models.skybox.destroy()
//
//        uniformBuffers.`object`.destroy()
//        uniformBuffers.skybox.destroy()
//
//        super.destroy()
//    }
//
//    // Enable physical device features required for this example
//    override fun getEnabledFeatures() {
//        if (deviceFeatures.samplerAnisotropy)
//            enabledFeatures.samplerAnisotropy = true
//
//        when {
//            deviceFeatures.textureCompressionBC -> enabledFeatures.textureCompressionBC = true
//            deviceFeatures.textureCompressionASTC_LDR -> enabledFeatures.textureCompressionASTC_LDR = true
//            deviceFeatures.textureCompressionETC2 -> enabledFeatures.textureCompressionETC2 = true
//        }
//    }
//
//    fun loadCubemap(filename: URI, format: VkFormat, forceLinearTiling: Boolean) {
//
//        val texCube = gli_.TextureCube(gli.load(filename))
//
//        assert(texCube.notEmpty())
//
//        cubeMap.size(texCube.extent())
//        cubeMap.mipLevels = texCube.levels()
//
//
//        // Create a host-visible staging buffer that contains the raw image data
//
//        val bufferCreateInfo = vk.BufferCreateInfo {
//            size = texCube.size.L
//            // This buffer is used as a transfer source for the buffer copy
//            usage = VkBufferUsage.TRANSFER_SRC_BIT.i
//            sharingMode = VkSharingMode.EXCLUSIVE
//        }
//        val stagingBuffer = device createBuffer bufferCreateInfo
//
//        // Get memory requirements for the staging buffer (alignment, memory type bits)
//        val memReqs = device getBufferMemoryRequirements stagingBuffer
//        val memAllocInfo = vk.MemoryAllocateInfo {
//            allocationSize = memReqs.size
//            // Get memory type index for a host visible buffer
//            memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT)
//        }
//        val stagingMemory = device allocateMemory memAllocInfo
//        device.bindBufferMemory(stagingBuffer, stagingMemory)
//
//        // Copy texture data into staging buffer
//        device.mappingMemory(stagingMemory, 0, memReqs.size, 0) { data ->
//            memCopy(texCube.data(), data, texCube.size.L)
//        }
//
//        // Create optimal tiled target image
//        val imageCreateInfo = vk.ImageCreateInfo {
//            imageType = VkImageType.`2D`
//            this.format = format
//            mipLevels = cubeMap.mipLevels
//            samples = VkSampleCount.`1_BIT`
//            tiling = VkImageTiling.OPTIMAL
//            sharingMode = VkSharingMode.EXCLUSIVE
//            initialLayout = VkImageLayout.UNDEFINED
//            extent.set(cubeMap.size.x, cubeMap.size.y, 1)
//            usage = VkImageUsage.TRANSFER_DST_BIT or VkImageUsage.SAMPLED_BIT
//            // Cube faces count as array layers in Vulkan
//            arrayLayers = 6
//            // This flag is required for cube map images
//            flags = VkImageCreate.CUBE_COMPATIBLE_BIT.i
//        }
//        cubeMap.image = device createImage imageCreateInfo
//
//        device.getImageMemoryRequirements(cubeMap.image, memReqs)
//
//        memAllocInfo.allocationSize = memReqs.size
//        memAllocInfo.memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
//
//        cubeMap.deviceMemory = device allocateMemory memAllocInfo
//        device.bindImageMemory(cubeMap.image, cubeMap.deviceMemory)
//
//        val copyCmd = createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)
//
//        // Setup buffer copy regions for each face including all of it's miplevels
//        val bufferCopyRegions = vk.BufferImageCopy(6 * cubeMap.mipLevels)
//        var offset = 0L
//
//        for (face in 0..5)
//            for (level in 0 until cubeMap.mipLevels)            {
//
//                bufferCopyRegions[] = {}
//                bufferCopyRegion.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//                bufferCopyRegion.imageSubresource.mipLevel = level
//                bufferCopyRegion.imageSubresource.baseArrayLayer = face
//                bufferCopyRegion.imageSubresource.layerCount = 1
//                bufferCopyRegion.imageExtent.width = texCube[face][level].extent().x
//                bufferCopyRegion.imageExtent.height = texCube[face][level].extent().y
//                bufferCopyRegion.imageExtent.depth = 1
//                bufferCopyRegion.bufferOffset = offset
//
//                bufferCopyRegions.push_back(bufferCopyRegion)
//
//                // Increase offset into staging buffer for next level / face
//                offset += texCube[face][level].size()
//            }
//
//        // Image barrier for optimal image (target)
//        // Set initial layout for all array layers (faces) of the optimal (target) tiled texture
//        VkImageSubresourceRange subresourceRange = {}
//        subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//        subresourceRange.baseMipLevel = 0
//        subresourceRange.levelCount = cubeMap.mipLevels
//        subresourceRange.layerCount = 6
//
//        vks::tools::setImageLayout(
//                copyCmd,
//                cubeMap.image,
//                VK_IMAGE_LAYOUT_UNDEFINED,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                subresourceRange)
//
//        // Copy the cube map faces from the staging buffer to the optimal tiled image
//        vkCmdCopyBufferToImage(
//                copyCmd,
//                stagingBuffer,
//                cubeMap.image,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                static_cast<uint32_t>(bufferCopyRegions.size()),
//                bufferCopyRegions.data()
//        )
//
//        // Change texture image layout to shader read after all faces have been copied
//        cubeMap.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
//        vks::tools::setImageLayout(
//                copyCmd,
//                cubeMap.image,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                cubeMap.imageLayout,
//                subresourceRange)
//
//        VulkanExampleBase::flushCommandBuffer(copyCmd, queue, true)
//
//        // Create sampler
//        VkSamplerCreateInfo sampler = vks ::initializers::samplerCreateInfo()
//        sampler.magFilter = VK_FILTER_LINEAR
//        sampler.minFilter = VK_FILTER_LINEAR
//        sampler.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR
//        sampler.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
//        sampler.addressModeV = sampler.addressModeU
//        sampler.addressModeW = sampler.addressModeU
//        sampler.mipLodBias = 0.0f
//        sampler.compareOp = VK_COMPARE_OP_NEVER
//        sampler.minLod = 0.0f
//        sampler.maxLod = cubeMap.mipLevels
//        sampler.borderColor = VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE
//        sampler.maxAnisotropy = 1.0f
//        if (vulkanDevice->features.samplerAnisotropy)
//        {
//            sampler.maxAnisotropy = vulkanDevice->properties.limits.maxSamplerAnisotropy
//            sampler.anisotropyEnable = VK_TRUE
//        }
//        VK_CHECK_RESULT(vkCreateSampler(device, & sampler, nullptr, & cubeMap . sampler))
//
//        // Create image view
//        VkImageViewCreateInfo view = vks ::initializers::imageViewCreateInfo()
//        // Cube map view type
//        view.viewType = VK_IMAGE_VIEW_TYPE_CUBE
//        view.format = format
//        view.components = { VK_COMPONENT_SWIZZLE_R, VK_COMPONENT_SWIZZLE_G, VK_COMPONENT_SWIZZLE_B, VK_COMPONENT_SWIZZLE_A }
//        view.subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 }
//        // 6 array layers (faces)
//        view.subresourceRange.layerCount = 6
//        // Set number of mip levels
//        view.subresourceRange.levelCount = cubeMap.mipLevels
//        view.image = cubeMap.image
//        VK_CHECK_RESULT(vkCreateImageView(device, & view, nullptr, & cubeMap . view))
//
//        // Clean up staging resources
//        vkFreeMemory(device, stagingMemory, nullptr)
//        vkDestroyBuffer(device, stagingBuffer, nullptr)
//    }
//
//    void loadTextures()
//    {
//        // Vulkan core supports three different compressed texture formats
//        // As the support differs between implemementations we need to check device features and select a proper format and file
//        std::string filename
//                VkFormat format
//                if (deviceFeatures.textureCompressionBC) {
//                    filename = "cubemap_yokohama_bc3_unorm.ktx"
//                    format = VK_FORMAT_BC2_UNORM_BLOCK
//                } else if (deviceFeatures.textureCompressionASTC_LDR) {
//                    filename = "cubemap_yokohama_astc_8x8_unorm.ktx"
//                    format = VK_FORMAT_ASTC_8x8_UNORM_BLOCK
//                } else if (deviceFeatures.textureCompressionETC2) {
//                    filename = "cubemap_yokohama_etc2_unorm.ktx"
//                    format = VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK
//                } else {
//                    vks::tools::exitFatal("Device does not support any compressed texture format!", VK_ERROR_FEATURE_NOT_PRESENT)
//                }
//
//        loadCubemap(getAssetPath() + "textures/" + filename, format, false)
//    }
//
//    void buildCommandBuffers()
//    {
//        VkCommandBufferBeginInfo cmdBufInfo = vks ::initializers::commandBufferBeginInfo()
//
//        VkClearValue clearValues [2]
//        clearValues[0].color = defaultClearColor
//        clearValues[1].depthStencil = { 1.0f, 0 }
//
//        VkRenderPassBeginInfo renderPassBeginInfo = vks ::initializers::renderPassBeginInfo()
//        renderPassBeginInfo.renderPass = renderPass
//        renderPassBeginInfo.renderArea.offset.x = 0
//        renderPassBeginInfo.renderArea.offset.y = 0
//        renderPassBeginInfo.renderArea.extent.width = width
//        renderPassBeginInfo.renderArea.extent.height = height
//        renderPassBeginInfo.clearValueCount = 2
//        renderPassBeginInfo.pClearValues = clearValues
//
//        for (int32_t i = 0; i < drawCmdBuffers.size(); ++i)
//        {
//            // Set target frame buffer
//            renderPassBeginInfo.framebuffer = frameBuffers[i]
//
//            VK_CHECK_RESULT(vkBeginCommandBuffer(drawCmdBuffers[i], & cmdBufInfo))
//
//            vkCmdBeginRenderPass(drawCmdBuffers[i], & renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)
//
//            VkViewport viewport = vks ::initializers::viewport((float) width, (float) height, 0.0f, 1.0f)
//            vkCmdSetViewport(drawCmdBuffers[i], 0, 1, & viewport)
//
//            VkRect2D scissor = vks ::initializers::rect2D(width, height, 0, 0)
//            vkCmdSetScissor(drawCmdBuffers[i], 0, 1, & scissor)
//
//            VkDeviceSize offsets [1] = { 0 }
//
//            // Skybox
//            if (displaySkybox) {
//                vkCmdBindDescriptorSets(drawCmdBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0, 1, & descriptorSets . skybox, 0, NULL)
//                vkCmdBindVertexBuffers(drawCmdBuffers[i], 0, 1, & models . skybox . vertices . buffer, offsets)
//                vkCmdBindIndexBuffer(drawCmdBuffers[i], models.skybox.indices.buffer, 0, VK_INDEX_TYPE_UINT32)
//                vkCmdBindPipeline(drawCmdBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipelines.skybox)
//                vkCmdDrawIndexed(drawCmdBuffers[i], models.skybox.indexCount, 1, 0, 0, 0)
//            }
//
//            // 3D object
//            vkCmdBindDescriptorSets(drawCmdBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0, 1, & descriptorSets .object, 0, NULL)
//            vkCmdBindVertexBuffers(drawCmdBuffers[i], 0, 1, & models . objects [models.objectIndex].vertices.buffer, offsets)
//            vkCmdBindIndexBuffer(drawCmdBuffers[i], models.objects[models.objectIndex].indices.buffer, 0, VK_INDEX_TYPE_UINT32)
//            vkCmdBindPipeline(drawCmdBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipelines.reflect)
//            vkCmdDrawIndexed(drawCmdBuffers[i], models.objects[models.objectIndex].indexCount, 1, 0, 0, 0)
//
//            vkCmdEndRenderPass(drawCmdBuffers[i])
//
//            VK_CHECK_RESULT(vkEndCommandBuffer(drawCmdBuffers[i]))
//        }
//    }
//
//    void loadAssets()
//    {
//        // Skybox
//        models.skybox.loadFromFile(getAssetPath() + "models/cube.obj", vertexLayout, 0.05f, vulkanDevice, queue)
//        // Objects
//        std::vector < std::string > filenames = { "sphere.obj", "teapot.dae", "torusknot.obj", "venus.fbx" }
//        objectNames = { "Sphere", "Teapot", "Torusknot", "Venus" }
//        for (auto file : filenames) {
//        vks::Model model
//                model.loadFromFile(getAssetPath() + "models/" + file, vertexLayout, 0.05f * (file == "venus.fbx" ? 3.0f : 1.0f), vulkanDevice, queue)
//        models.objects.push_back(model)
//    }
//    }
//
//    void setupDescriptorPool()
//    {
//        std::vector<VkDescriptorPoolSize> poolSizes =
//        {
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 2),
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 2)
//        }
//
//        VkDescriptorPoolCreateInfo descriptorPoolInfo =
//        vks::initializers::descriptorPoolCreateInfo(
//                poolSizes.size(),
//                poolSizes.data(),
//                2)
//
//        VK_CHECK_RESULT(vkCreateDescriptorPool(device, & descriptorPoolInfo, nullptr, & descriptorPool))
//    }
//
//    void setupDescriptorSetLayout()
//    {
//        std::vector<VkDescriptorSetLayoutBinding> setLayoutBindings =
//        {
//            // Binding 0 : Vertex shader uniform buffer
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    VK_SHADER_STAGE_VERTEX_BIT,
//                    0),
//            // Binding 1 : Fragment shader image sampler
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    VK_SHADER_STAGE_FRAGMENT_BIT,
//                    1)
//        }
//
//        VkDescriptorSetLayoutCreateInfo descriptorLayout =
//        vks::initializers::descriptorSetLayoutCreateInfo(
//                setLayoutBindings.data(),
//                setLayoutBindings.size())
//
//        VK_CHECK_RESULT(vkCreateDescriptorSetLayout(device, & descriptorLayout, nullptr, & descriptorSetLayout))
//
//        VkPipelineLayoutCreateInfo pPipelineLayoutCreateInfo =
//        vks::initializers::pipelineLayoutCreateInfo(
//                & descriptorSetLayout,
//        1)
//
//        VK_CHECK_RESULT(vkCreatePipelineLayout(device, & pPipelineLayoutCreateInfo, nullptr, & pipelineLayout))
//    }
//
//    void setupDescriptorSets()
//    {
//        // Image descriptor for the cube map texture
//        VkDescriptorImageInfo textureDescriptor =
//        vks::initializers::descriptorImageInfo(
//                cubeMap.sampler,
//                cubeMap.view,
//                cubeMap.imageLayout)
//
//        VkDescriptorSetAllocateInfo allocInfo =
//        vks::initializers::descriptorSetAllocateInfo(
//                descriptorPool,
//                & descriptorSetLayout,
//        1)
//
//        // 3D object descriptor set
//        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & descriptorSets .object))
//
//        std::vector<VkWriteDescriptorSet> writeDescriptorSets =
//        {
//            // Binding 0 : Vertex shader uniform buffer
//            vks::initializers::writeDescriptorSet(
//                    descriptorSets.object,
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    0,
//                    & uniformBuffers .object.descriptor),
//            // Binding 1 : Fragment shader cubemap sampler
//            vks::initializers::writeDescriptorSet(
//                    descriptorSets.object,
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    1,
//                    & textureDescriptor)
//        }
//        vkUpdateDescriptorSets(device, writeDescriptorSets.size(), writeDescriptorSets.data(), 0, NULL)
//
//        // Sky box descriptor set
//        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & descriptorSets . skybox))
//
//        writeDescriptorSets =
//                {
//                    // Binding 0 : Vertex shader uniform buffer
//                    vks::initializers::writeDescriptorSet(
//                            descriptorSets.skybox,
//                            VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                            0,
//                            & uniformBuffers . skybox . descriptor),
//                    // Binding 1 : Fragment shader cubemap sampler
//                    vks::initializers::writeDescriptorSet(
//                            descriptorSets.skybox,
//                            VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                            1,
//                            & textureDescriptor)
//                }
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
//                VK_CULL_MODE_BACK_BIT,
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
//        // Vertex bindings and attributes
//        VkVertexInputBindingDescription vertexInputBinding =
//        vks::initializers::vertexInputBindingDescription(0, vertexLayout.stride(), VK_VERTEX_INPUT_RATE_VERTEX)
//
//        std::vector<VkVertexInputAttributeDescription> vertexInputAttributes = {
//            vks::initializers::vertexInputAttributeDescription(0, 0, VK_FORMAT_R32G32B32_SFLOAT, 0),                    // Location 0: Position
//            vks::initializers::vertexInputAttributeDescription(0, 1, VK_FORMAT_R32G32B32_SFLOAT, sizeof(float) * 3),    // Location 1: Normal
//        }
//
//        VkPipelineVertexInputStateCreateInfo vertexInputState = vks ::initializers::pipelineVertexInputStateCreateInfo()
//        vertexInputState.vertexBindingDescriptionCount = 1
//        vertexInputState.pVertexBindingDescriptions = & vertexInputBinding
//                vertexInputState.vertexAttributeDescriptionCount = static_cast<uint32_t>(vertexInputAttributes.size())
//        vertexInputState.pVertexAttributeDescriptions = vertexInputAttributes.data()
//
//        std::array < VkPipelineShaderStageCreateInfo, 2> shaderStages
//
//        VkGraphicsPipelineCreateInfo pipelineCreateInfo = vks ::initializers::pipelineCreateInfo(pipelineLayout, renderPass, 0)
//        pipelineCreateInfo.pInputAssemblyState = & inputAssemblyState
//                pipelineCreateInfo.pRasterizationState = & rasterizationState
//                pipelineCreateInfo.pColorBlendState = & colorBlendState
//                pipelineCreateInfo.pMultisampleState = & multisampleState
//                pipelineCreateInfo.pViewportState = & viewportState
//                pipelineCreateInfo.pDepthStencilState = & depthStencilState
//                pipelineCreateInfo.pDynamicState = & dynamicState
//                pipelineCreateInfo.stageCount = shaderStages.size()
//        pipelineCreateInfo.pStages = shaderStages.data()
//        pipelineCreateInfo.pVertexInputState = & vertexInputState
//
//                // Skybox pipeline (background cube)
//                shaderStages[0] = loadShader(getAssetPath() + "shaders/texturecubemap/skybox.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
//        shaderStages[1] = loadShader(getAssetPath() + "shaders/texturecubemap/skybox.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)
//        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . skybox))
//
//        // Cube map reflect pipeline
//        shaderStages[0] = loadShader(getAssetPath() + "shaders/texturecubemap/reflect.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
//        shaderStages[1] = loadShader(getAssetPath() + "shaders/texturecubemap/reflect.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)
//        // Enable depth test and write
//        depthStencilState.depthWriteEnable = VK_TRUE
//        depthStencilState.depthTestEnable = VK_TRUE
//        // Flip cull mode
//        rasterizationState.cullMode = VK_CULL_MODE_FRONT_BIT
//        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . reflect))
//    }
//
//    // Prepare and initialize uniform buffer containing shader uniforms
//    void prepareUniformBuffers()
//    {
//        // Objact vertex shader uniform buffer
//        VK_CHECK_RESULT(vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &uniformBuffers.object,
//        sizeof(uboVS)))
//
//        // Skybox vertex shader uniform buffer
//        VK_CHECK_RESULT(vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &uniformBuffers.skybox,
//        sizeof(uboVS)))
//
//        // Map persistent
//        VK_CHECK_RESULT(uniformBuffers.object.map())
//        VK_CHECK_RESULT(uniformBuffers.skybox.map())
//
//        updateUniformBuffers()
//    }
//
//    void updateUniformBuffers()
//    {
//        // 3D object
//        glm::mat4 viewMatrix = glm ::mat4(1.0f)
//        uboVS.projection = glm::perspective(glm::radians(60.0f), (float) width /(float) height, 0.001f, 256.0f)
//        viewMatrix = glm::translate(viewMatrix, glm::vec3(0.0f, 0.0f, zoom))
//
//        uboVS.model = glm::mat4(1.0f)
//        uboVS.model = viewMatrix * glm::translate(uboVS.model, cameraPos)
//        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.x), glm::vec3(1.0f, 0.0f, 0.0f))
//        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.y), glm::vec3(0.0f, 1.0f, 0.0f))
//        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.z), glm::vec3(0.0f, 0.0f, 1.0f))
//
//        memcpy(uniformBuffers.object.mapped, & uboVS, sizeof(uboVS))
//
//        // Skybox
//        viewMatrix = glm::mat4(1.0f)
//        uboVS.projection = glm::perspective(glm::radians(60.0f), (float) width /(float) height, 0.001f, 256.0f)
//
//        uboVS.model = glm::mat4(1.0f)
//        uboVS.model = viewMatrix * glm::translate(uboVS.model, glm::vec3(0, 0, 0))
//        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.x), glm::vec3(1.0f, 0.0f, 0.0f))
//        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.y), glm::vec3(0.0f, 1.0f, 0.0f))
//        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.z), glm::vec3(0.0f, 0.0f, 1.0f))
//
//        memcpy(uniformBuffers.skybox.mapped, & uboVS, sizeof(uboVS))
//    }
//
//    void draw()
//    {
//        VulkanExampleBase::prepareFrame()
//
//        submitInfo.commandBufferCount = 1
//        submitInfo.pCommandBuffers = & drawCmdBuffers [currentBuffer]
//        VK_CHECK_RESULT(vkQueueSubmit(queue, 1, & submitInfo, VK_NULL_HANDLE))
//
//        VulkanExampleBase::submitFrame()
//    }
//
//    void prepare()
//    {
//        VulkanExampleBase::prepare()
//        loadTextures()
//        loadAssets()
//        prepareUniformBuffers()
//        setupDescriptorSetLayout()
//        preparePipelines()
//        setupDescriptorPool()
//        setupDescriptorSets()
//        buildCommandBuffers()
//        prepared = true
//    }
//
//    virtual void render()
//    {
//        if (!prepared)
//            return
//        draw()
//    }
//
//    virtual void viewChanged()
//    {
//        updateUniformBuffers()
//    }
//
//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
//    {
//        if (overlay->header("Settings")) {
//        if (overlay->sliderFloat("LOD bias", &uboVS.lodBias, 0.0f, (float)cubeMap.mipLevels)) {
//        updateUniformBuffers()
//    }
//        if (overlay->comboBox("Object type", &models.objectIndex, objectNames)) {
//        buildCommandBuffers()
//    }
//        if (overlay->checkBox("Skybox", &displaySkybox)) {
//        buildCommandBuffers()
//    }
//    }
//    }
//}
//
//VULKAN_EXAMPLE_MAIN()