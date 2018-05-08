///*
//* Vulkan Example - Model loading and rendering
//*
//* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
//*
//* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
//*/
//
//package vulkan.basics
//
//import assimp.AiPostProcessStepsFlags
//import assimp.Importer
//import assimp.or
//import glm_.L
//import glm_.mat4x4.Mat4
//import glm_.size
//import glm_.vec2.Vec2
//import glm_.vec3.Vec3
//import glm_.vec4.Vec4
//import org.lwjgl.system.MemoryUtil.NULL
//import org.lwjgl.vulkan.VkDevice
//import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
//import org.lwjgl.vulkan.VkVertexInputAttributeDescription
//import org.lwjgl.vulkan.VkVertexInputBindingDescription
//import vkn.*
//import vulkan.USE_STAGING
//import vulkan.VERTEX_BUFFER_BIND_ID
//import vulkan.base.Buffer
//import vulkan.base.Texture2D
//import vulkan.base.VulkanExampleBase
//import assimp.AiPostProcessStep as Pp
//
//
//fun main(args: Array<String>) {
//    ModelRendering().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//
//class ModelRendering : VulkanExampleBase() {
//
//    var wireframe = false
//
//    object textures {
//        val colorMap = Texture2D()
//    }
//
//    object vertices {
//        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
//        lateinit var bindingDescriptions: VkVertexInputBindingDescription
//        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
//    }
//
//    // Vertex layout used in this example
//    // This must fit input locations of the vertex shader used to render the model
//    class Vertex : Bufferizable() {
//        lateinit var pos: Vec3
//        @Order(1)
//        lateinit var normal: Vec3
//        lateinit var uv: Vec2
//        @Order(3)
//        lateinit var color: Vec3
//
//        companion object {
//            val size = Vec3.size * 3 + Vec2.size
//        }
//    }
//
//    // Contains all Vulkan resources required to represent vertex and index buffers for a model
//    // This is for demonstration and learning purposes, the other examples use a model loader class for easy access
//    object model {
//        object vertices {
//            var buffer: VkBuffer = NULL
//            var memory: VkDeviceMemory = NULL
//        }
//
//        object indices {
//            var count = 0
//            var buffer: VkBuffer = NULL
//            var memory: VkDeviceMemory = NULL
//        }
//
//        // Destroys all Vulkan resources created for this model
//        fun destroy(device: VkDevice) = device.apply {
//            destroyBuffer(vertices.buffer)
//            freeMemory(vertices.memory)
//            destroyBuffer(indices.buffer)
//            freeMemory(indices.memory)
//        }
//    }
//
//    object uniformBuffers {
//        val scene = Buffer()
//    }
//
//    object uboVS : Bufferizable() {
//        lateinit var projection: Mat4
//        @Order(1)
//        lateinit var model: Mat4
//        @Order(2)
//        val lightPos = Vec4(25f, 5f, 5f, 1f)
//    }
//
//    object pipelines {
//        var solid: VkPipeline = NULL
//        var wireframe: VkPipeline = NULL
//    }
//
//    var pipelineLayout: VkPipelineLayout = NULL
//    var descriptorSet: VkDescriptorSet = NULL
//    var descriptorSetLayout: VkDescriptorSetLayout = NULL
//
//    init {
//        zoom = -5.5f
//        zoomSpeed = 2.5f
//        rotationSpeed = 0.5f
//        rotation(-0.5f, -112.75f, 0f)
//        cameraPos(0.1f, 1.1f, 0f)
//        title = "Model rendering"
//        settings.overlay = true
//    }
//
//    override fun destroy() {
//        // Clean up used Vulkan resources
//        // Note : Inherited destructor cleans up resources stored in base class
//        device.apply {
//            destroyPipeline(pipelines.solid)
//            if (pipelines.wireframe != NULL)
//                destroyPipeline(pipelines.wireframe)
//
//            destroyPipelineLayout(pipelineLayout)
//            destroyDescriptorSetLayout(descriptorSetLayout)
//        }
//        model.destroy(device)
//
//        textures.colorMap.destroy()
//        uniformBuffers.scene.destroy()
//
//        super.destroy()
//    }
//
//    override fun getEnabledFeatures() {
//        // Fill mode non solid is required for wireframe display
//        if (deviceFeatures.fillModeNonSolid)
//            enabledFeatures.fillModeNonSolid = true
//    }
//
//    override fun buildCommandBuffers() {
//
//        val cmdBufInfo = vk.CommandBufferBeginInfo()
//
//        val clearValues = vk.ClearValue(2).also {
//            it[0].color(defaultClearColor)
//            it[1].depthStencil(1f, 0)
//        }
//        val renderPassBeginInfo = vk.RenderPassBeginInfo {
//            renderPass = this@ModelRendering.renderPass
//            renderArea.apply {
//                offset(0)
//                extent(size)
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
//                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)
//
//                setViewport(size)
//
//                setScissor(size)
//
//                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
//                bindPipeline(VkPipelineBindPoint.GRAPHICS, if (wireframe) pipelines.wireframe else pipelines.solid)
//
//                // Bind mesh vertex buffer
//                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, model.vertices.buffer)
//                // Bind mesh index buffer
//                bindIndexBuffer(model.indices.buffer, 0, VkIndexType.UINT32)
//                // Render mesh vertex buffer using it's indices
//                drawIndexed(model.indices.count, 1, 0, 0, 0)
//
//                endRenderPass()
//
//                end()
//            }
//        }
//    }
//
//    /** Load a model from file using the ASSIMP model loader and generate all resources required to render the model */
//    fun loadModel(filename: String) {
//        // Load the model from file using ASSIMP
//
//        // Flags for loading the mesh
//        val assimpFlags: AiPostProcessStepsFlags = Pp.FlipWindingOrder or Pp.Triangulate or Pp.PreTransformVertices
//
//        val scene = Importer().readFile(filename, assimpFlags)!!
//
//        // Generate vertex buffer from ASSIMP scene data
//        val scale = 1f
//        val vertices = ArrayList<Vertex>()
//
//        // Iterate through all meshes in the file and extract the vertex components
//        for (m in 0 until scene.numMeshes)
//            for (v in 0 until scene.meshes[m].numVertices) {
//
//                val vertex = Vertex().apply {
//                    // Use glm make_* functions to convert ASSIMP vectors to glm vectors
//                    pos = scene.meshes[m].vertices[v] * scale
//                    normal = scene.meshes[m].normals[v]
//                    // Texture coordinates and colors may have multiple channels, we only use the first [0] one
//                    uv = Vec2(scene.meshes[m].textureCoords[0][v])
//                    // Mesh may not have vertex colors
//                    color = scene.meshes[m].colors.getOrNull(0)?.let { Vec3(it[v]) } ?: Vec3(1f)
//                    // Vulkan uses a right-handed NDC (contrary to OpenGL), so simply flip Y-Axis
//                    pos.y *= -1f
//                }
//                vertices += vertex
//            }
//        val vertexBuffer = bufferOf(vertices)
//
//        // Generate index buffer from ASSIMP scene data
//        val indices = ArrayList<Int>()
//        for (m in 0 until scene.numMeshes) {
//            val indexBase = indices.size
//            for (f in 0 until scene.meshes[m].numFaces)
//            // We assume that all faces are triangulated
//                for (i in 0..2)
//                    indices += scene.meshes[m].faces[f][i] + indexBase
//        }
//        val indexBuffer = intArrayOf(indices)
//        model.indices.count = indices.size
//
//        // Static mesh should always be device local
//
//        if (USE_STAGING) {
//
//            val vertexStaging = object {
//                var buffer: VkBuffer = NULL
//                var memory: VkDeviceMemory = NULL
//            }
//            val indexStaging = object {
//                var buffer: VkBuffer = NULL
//                var memory: VkDeviceMemory = NULL
//            }
//
//            // Create staging buffers
//            // Vertex data
//            vulkanDevice.createBuffer(
//                    VkBufferUsage.TRANSFER_SRC_BIT.i,
//                    VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                    vertexBuffer.size.L,
//                    vertexStaging::buffer,
//                    vertexStaging::memory,
//                    vertexBuffer.adr)
//            // Index data
//            vulkanDevice.createBuffer(
//                    VkBufferUsage.TRANSFER_SRC_BIT.i,
//                    VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                    indexBuffer.size.L,
//                    indexStaging::buffer,
//                    indexStaging::memory,
//                    indices.adr)
//
//            // Create device local buffers
//            // Vertex buffer
//            vulkanDevice.createBuffer(
//                    VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT,
//                    VkMemoryProperty.DEVICE_LOCAL_BIT.i,
//                    vertexBuffer.size.L,
//                    model.vertices::buffer,
//                    model.vertices::memory)
//            // Index buffer
//            vulkanDevice.createBuffer(
//                    VkBufferUsage.INDEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT,
//                    VkMemoryProperty.DEVICE_LOCAL_BIT.i,
//                    indexBuffer.size.L,
//                    model.indices::buffer,
//                    model.indices::memory)
//
//            // Copy from staging buffers
//            val copyCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)
//
//            val copyRegion = vk.BufferCopy { size = vertexBuffer.size.L }
//            copyCmd.copyBuffer(
//                    vertexStaging.buffer,
//                    model.vertices.buffer,
//                    copyRegion)
//
//            copyRegion.size = indexBuffer.size.L
//            copyCmd.copyBuffer(
//                    indexStaging.buffer,
//                    model.indices.buffer,
//                    copyRegion)
//
//            super.flushCommandBuffer(copyCmd, queue, true)
//
//            vkDestroyBuffer(device, vertexStaging.buffer, nullptr)
//            vkFreeMemory(device, vertexStaging.memory, nullptr)
//            vkDestroyBuffer(device, indexStaging.buffer, nullptr)
//            vkFreeMemory(device, indexStaging.memory, nullptr)
//        } else {
//            // Vertex buffer
//            VK_CHECK_RESULT(vulkanDevice->createBuffer(
//            VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
//            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
//            vertexBufferSize,
//            &model.vertices.buffer,
//            &model.vertices.memory,
//            vertices.data()))
//            // Index buffer
//            VK_CHECK_RESULT(vulkanDevice->createBuffer(
//            VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
//            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT,
//            indexBufferSize,
//            &model.indices.buffer,
//            &model.indices.memory,
//            indices.data()))
//        }
//    }
//
//    void loadAssets()
//    {
//        loadModel(getAssetPath() + "models/voyager/voyager.dae")
//        if (deviceFeatures.textureCompressionBC) {
//            textures.colorMap.loadFromFile(getAssetPath() + "models/voyager/voyager_bc3_unorm.ktx", VK_FORMAT_BC3_UNORM_BLOCK, vulkanDevice, queue)
//        } else if (deviceFeatures.textureCompressionASTC_LDR) {
//            textures.colorMap.loadFromFile(getAssetPath() + "models/voyager/voyager_astc_8x8_unorm.ktx", VK_FORMAT_ASTC_8x8_UNORM_BLOCK, vulkanDevice, queue)
//        } else if (deviceFeatures.textureCompressionETC2) {
//            textures.colorMap.loadFromFile(getAssetPath() + "models/voyager/voyager_etc2_unorm.ktx", VK_FORMAT_ETC2_R8G8B8A8_UNORM_BLOCK, vulkanDevice, queue)
//        } else {
//            vks::tools::exitFatal("Device does not support any compressed texture format!", VK_ERROR_FEATURE_NOT_PRESENT)
//        }
//    }
//
//    void setupVertexDescriptions()
//    {
//        // Binding description
//        vertices.bindingDescriptions.resize(1)
//        vertices.bindingDescriptions[0] =
//                vks::initializers::vertexInputBindingDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        sizeof(Vertex),
//                        VK_VERTEX_INPUT_RATE_VERTEX)
//
//        // Attribute descriptions
//        // Describes memory layout and shader positions
//        vertices.attributeDescriptions.resize(4)
//        // Location 0 : Position
//        vertices.attributeDescriptions[0] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        0,
//                        VK_FORMAT_R32G32B32_SFLOAT,
//                        offsetof(Vertex, pos))
//        // Location 1 : Normal
//        vertices.attributeDescriptions[1] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        1,
//                        VK_FORMAT_R32G32B32_SFLOAT,
//                        offsetof(Vertex, normal))
//        // Location 2 : Texture coordinates
//        vertices.attributeDescriptions[2] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        2,
//                        VK_FORMAT_R32G32_SFLOAT,
//                        offsetof(Vertex, uv))
//        // Location 3 : Color
//        vertices.attributeDescriptions[3] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        3,
//                        VK_FORMAT_R32G32B32_SFLOAT,
//                        offsetof(Vertex, color))
//
//        vertices.inputState = vks::initializers::pipelineVertexInputStateCreateInfo()
//        vertices.inputState.vertexBindingDescriptionCount = static_cast<uint32_t>(vertices.bindingDescriptions.size())
//        vertices.inputState.pVertexBindingDescriptions = vertices.bindingDescriptions.data()
//        vertices.inputState.vertexAttributeDescriptionCount = static_cast<uint32_t>(vertices.attributeDescriptions.size())
//        vertices.inputState.pVertexAttributeDescriptions = vertices.attributeDescriptions.data()
//    }
//
//    void setupDescriptorPool()
//    {
//        // Example uses one ubo and one combined image sampler
//        std::vector<VkDescriptorPoolSize> poolSizes =
//        {
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1),
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1),
//        }
//
//        VkDescriptorPoolCreateInfo descriptorPoolInfo =
//        vks::initializers::descriptorPoolCreateInfo(
//                static_cast<uint32_t>(poolSizes.size()),
//                poolSizes.data(),
//                1)
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
//            // Binding 1 : Fragment shader combined sampler
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    VK_SHADER_STAGE_FRAGMENT_BIT,
//                    1),
//        }
//
//        VkDescriptorSetLayoutCreateInfo descriptorLayout =
//        vks::initializers::descriptorSetLayoutCreateInfo(
//                setLayoutBindings.data(),
//                static_cast<uint32_t>(setLayoutBindings.size()))
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
//    void setupDescriptorSet()
//    {
//        VkDescriptorSetAllocateInfo allocInfo =
//        vks::initializers::descriptorSetAllocateInfo(
//                descriptorPool,
//                & descriptorSetLayout,
//        1)
//
//        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & descriptorSet))
//
//        VkDescriptorImageInfo texDescriptor =
//        vks::initializers::descriptorImageInfo(
//                textures.colorMap.sampler,
//                textures.colorMap.view,
//                VK_IMAGE_LAYOUT_GENERAL)
//
//        std::vector<VkWriteDescriptorSet> writeDescriptorSets =
//        {
//            // Binding 0 : Vertex shader uniform buffer
//            vks::initializers::writeDescriptorSet(
//                    descriptorSet,
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    0,
//                    & uniformBuffers . scene . descriptor),
//            // Binding 1 : Color map
//            vks::initializers::writeDescriptorSet(
//                    descriptorSet,
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    1,
//                    & texDescriptor)
//        }
//
//        vkUpdateDescriptorSets(device, static_cast<uint32_t>(writeDescriptorSets.size()), writeDescriptorSets.data(), 0, NULL)
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
//                VK_FRONT_FACE_CLOCKWISE,
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
//                VK_TRUE,
//                VK_TRUE,
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
//                static_cast<uint32_t>(dynamicStateEnables.size()),
//                0)
//
//        // Solid rendering pipeline
//        // Load shaders
//        std::array < VkPipelineShaderStageCreateInfo, 2> shaderStages
//
//        shaderStages[0] = loadShader(getAssetPath() + "shaders/mesh/mesh.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
//        shaderStages[1] = loadShader(getAssetPath() + "shaders/mesh/mesh.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)
//
//        VkGraphicsPipelineCreateInfo pipelineCreateInfo =
//        vks::initializers::pipelineCreateInfo(
//                pipelineLayout,
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
//
//        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . solid))
//
//        // Wire frame rendering pipeline
//        if (deviceFeatures.fillModeNonSolid) {
//            rasterizationState.polygonMode = VK_POLYGON_MODE_LINE
//            rasterizationState.lineWidth = 1.0f
//            VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . wireframe))
//        }
//    }
//
//    // Prepare and initialize uniform buffer containing shader uniforms
//    void prepareUniformBuffers()
//    {
//        // Vertex shader uniform buffer block
//        VK_CHECK_RESULT(vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &uniformBuffers.scene,
//        sizeof(uboVS)))
//
//        // Map persistent
//        VK_CHECK_RESULT(uniformBuffers.scene.map())
//
//        updateUniformBuffers()
//    }
//
//    void updateUniformBuffers()
//    {
//        uboVS.projection = glm::perspective(glm::radians(60.0f), (float) width /(float) height, 0.1f, 256.0f)
//        glm::mat4 viewMatrix = glm ::translate(glm::mat4(1.0f), glm::vec3(0.0f, 0.0f, zoom))
//
//        uboVS.model = viewMatrix * glm::translate(glm::mat4(1.0f), cameraPos)
//        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.x), glm::vec3(1.0f, 0.0f, 0.0f))
//        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.y), glm::vec3(0.0f, 1.0f, 0.0f))
//        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.z), glm::vec3(0.0f, 0.0f, 1.0f))
//
//        memcpy(uniformBuffers.scene.mapped, & uboVS, sizeof(uboVS))
//    }
//
//    void draw()
//    {
//        VulkanExampleBase::prepareFrame()
//
//        // Command buffer to be sumitted to the queue
//        submitInfo.commandBufferCount = 1
//        submitInfo.pCommandBuffers = & drawCmdBuffers [currentBuffer]
//
//        // Submit to queue
//        VK_CHECK_RESULT(vkQueueSubmit(queue, 1, & submitInfo, VK_NULL_HANDLE))
//
//        VulkanExampleBase::submitFrame()
//    }
//
//    void prepare()
//    {
//        VulkanExampleBase::prepare()
//        loadAssets()
//        setupVertexDescriptions()
//        prepareUniformBuffers()
//        setupDescriptorSetLayout()
//        preparePipelines()
//        setupDescriptorPool()
//        setupDescriptorSet()
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
//        if (overlay->checkBox("Wireframe", &wireframe)) {
//        buildCommandBuffers()
//    }
//    }
//    }
//}
//
//VULKAN_EXAMPLE_MAIN()