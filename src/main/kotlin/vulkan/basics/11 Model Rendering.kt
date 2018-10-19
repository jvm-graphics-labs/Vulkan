/*
* Vulkan Example - Model loading and rendering
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.basics

import assimp.AiPostProcessStepsFlags
import assimp.Importer
import assimp.or
import glm_.L
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.size
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import kool.adr
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import vkk.*
import vulkan.useStaging
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.assetPath
import vulkan.base.Buffer
import vulkan.base.Texture2D
import vulkan.base.VulkanExampleBase
import vulkan.base.tools
import assimp.AiPostProcessStep as Pp


fun main(args: Array<String>) {
    ModelRendering().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}


private class ModelRendering : VulkanExampleBase() {

    var wireframe = false

    object textures {
        val colorMap = Texture2D()
    }

    object vertices {
        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
        lateinit var bindingDescriptions: VkVertexInputBindingDescription
        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
    }

    // Vertex layout used in this example
    // This must fit input locations of the vertex shader used to render the model
    class Vertex : Bufferizable() {
        lateinit var pos: Vec3
        @Order(1)
        lateinit var normal: Vec3
        lateinit var uv: Vec2
        @Order(3)
        lateinit var color: Vec3
    }

    // Contains all Vulkan resources required to represent vertex and index buffers for a model
    // This is for demonstration and learning purposes, the other examples use a model loader class for easy access
    object model {
        object vertices {
            var buffer = VkBuffer(NULL)
            var memory = VkDeviceMemory(NULL)
        }

        object indices {
            var count = 0
            var buffer = VkBuffer(NULL)
            var memory = VkDeviceMemory(NULL)
        }

        // Destroys all Vulkan resources created for this model
        fun destroy(device: VkDevice) = device.apply {
            destroyBuffer(vertices.buffer)
            freeMemory(vertices.memory)
            destroyBuffer(indices.buffer)
            freeMemory(indices.memory)
        }
    }

    object uniformBuffers {
        val scene = Buffer()
    }

    object uboVS : Bufferizable() {
        lateinit var projection: Mat4
        @Order(1)
        lateinit var model: Mat4
        @Order(2)
        val lightPos = Vec4(25f, 5f, 5f, 1f)
    }

    object pipelines {
        var solid = VkPipeline(NULL)
        var wireframe = VkPipeline(NULL)
    }

    var pipelineLayout = VkPipelineLayout(NULL)
    var descriptorSet = VkDescriptorSet(NULL)
    var descriptorSetLayout = VkDescriptorSetLayout(NULL)

    init {
        zoom = -5.5f
        zoomSpeed = 2.5f
        rotationSpeed = 0.5f
        rotation(-0.5f, -112.75f, 0f)
        cameraPos(0.1f, 1.1f, 0f)
        title = "Model rendering"
        settings.overlay = false // TODO
    }

    override fun destroy() {
        // Clean up used Vulkan resources
        // Note : Inherited destructor cleans up resources stored in base class
        device.apply {
            destroyPipeline(pipelines.solid)
            if (pipelines.wireframe.L != NULL)
                destroyPipeline(pipelines.wireframe)

            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }
        model.destroy(device)

        textures.colorMap.destroy()
        uniformBuffers.scene.destroy()

        super.destroy()
    }

    override fun getEnabledFeatures() {
        // Fill mode non solid is required for wireframe display
        if (deviceFeatures.fillModeNonSolid)
            enabledFeatures.fillModeNonSolid = true
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2).also {
            it[0].color(defaultClearColor)
            it[1].depthStencil(1f, 0)
        }
        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@ModelRendering.renderPass
            renderArea.apply {
                offset(0)
                extent(size)
            }
            this.clearValues = clearValues
        }
        for (i in drawCmdBuffers.indices) {
            // Set target frame buffer
            renderPassBeginInfo.framebuffer(frameBuffers[i].L)

            drawCmdBuffers[i].apply {

                begin(cmdBufInfo)

                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                setViewport(size)

                setScissor(size)

                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, if (wireframe) pipelines.wireframe else pipelines.solid)

                // Bind mesh vertex buffer
                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, model.vertices.buffer)
                // Bind mesh index buffer
                bindIndexBuffer(model.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
                // Render mesh vertex buffer using it's indices
                drawIndexed(model.indices.count, 1, 0, 0, 0)

                drawUI()

                endRenderPass()

                end()
            }
        }
    }

    /** Load a model from file using the ASSIMP model loader and generate all resources required to render the model */
    fun loadModel(filename: String) {
        // Load the model from file using ASSIMP

        // Flags for loading the mesh
        val assimpFlags: AiPostProcessStepsFlags = Pp.FlipWindingOrder or Pp.Triangulate or Pp.PreTransformVertices

        val scene = Importer().readFile(filename, assimpFlags)!!

        // Generate vertex buffer from ASSIMP scene data
        val scale = 1f
        val vertices = ArrayList<Vertex>()

        // Iterate through all meshes in the file and extract the vertex components
        for (m in 0 until scene.numMeshes)
            for (v in 0 until scene.meshes[m].numVertices) {

                val vertex = Vertex().apply {
                    // Use glm make_* functions to convert ASSIMP vectors to glm vectors
                    pos = scene.meshes[m].vertices[v] * scale
                    normal = scene.meshes[m].normals[v]
                    // Texture coordinates and colors may have multiple channels, we only use the first [0] one
                    uv = Vec2(scene.meshes[m].textureCoords[0][v])
                    // Mesh may not have vertex colors
                    color = scene.meshes[m].colors.getOrNull(0)?.let { Vec3(it[v]) } ?: Vec3(1f)
                    // Vulkan uses a right-handed NDC (contrary to OpenGL), so simply flip Y-Axis
                    pos.y *= -1f
                }
                vertices += vertex
            }
        val vertexBuffer = bufferOf(vertices)
        val vertexBufferSize = VkDeviceSize(vertexBuffer.size.L)

        // Generate index buffer from ASSIMP scene data
        val indices = ArrayList<Int>()
        for (m in 0 until scene.numMeshes) {
            val indexBase = indices.size
            for (f in 0 until scene.meshes[m].numFaces)
            // We assume that all faces are triangulated
                for (i in 0..2)
                    indices += scene.meshes[m].faces[f][i] + indexBase
        }
        val indexBuffer = intArrayOf(indices)
        val indexBufferSize = VkDeviceSize(indexBuffer.size.L)
        model.indices.count = indices.size

        // Static mesh should always be device local

        if (useStaging) {

            val vertexStaging = object {
                var buffer = VkBuffer(NULL)
                var memory = VkDeviceMemory(NULL)
            }
            val indexStaging = object {
                var buffer = VkBuffer(NULL)
                var memory= VkDeviceMemory (NULL)
            }

            // Create staging buffers
            // Vertex data
            vulkanDevice.createBuffer(
                    VkBufferUsage.TRANSFER_SRC_BIT.i,
                    VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                    vertexBufferSize,
                    vertexStaging::buffer,
                    vertexStaging::memory,
                    vertexBuffer.adr)
            // Index data
            vulkanDevice.createBuffer(
                    VkBufferUsage.TRANSFER_SRC_BIT.i,
                    VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                    indexBufferSize,
                    indexStaging::buffer,
                    indexStaging::memory,
                    indexBuffer.adr)

            // Create device local buffers
            // Vertex buffer
            vulkanDevice.createBuffer(
                    VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT,
                    VkMemoryProperty.DEVICE_LOCAL_BIT.i,
                    vertexBufferSize,
                    model.vertices::buffer,
                    model.vertices::memory)
            // Index buffer
            vulkanDevice.createBuffer(
                    VkBufferUsage.INDEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT,
                    VkMemoryProperty.DEVICE_LOCAL_BIT.i,
                    indexBufferSize,
                    model.indices::buffer,
                    model.indices::memory)

            // Copy from staging buffers
            val copyCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)

            val copyRegion = vk.BufferCopy { size = vertexBufferSize }
            copyCmd.copyBuffer(
                    vertexStaging.buffer,
                    model.vertices.buffer,
                    copyRegion)

            copyRegion.size = indexBufferSize
            copyCmd.copyBuffer(
                    indexStaging.buffer,
                    model.indices.buffer,
                    copyRegion)

            super.flushCommandBuffer(copyCmd, queue, true)

            device.apply {
                destroyBuffer(vertexStaging.buffer)
                freeMemory(vertexStaging.memory)
                destroyBuffer(indexStaging.buffer)
                freeMemory(indexStaging.memory)
            }
        } else {
            // Vertex buffer
            vulkanDevice.createBuffer(
                    VkBufferUsage.VERTEX_BUFFER_BIT.i,
                    VkMemoryProperty.HOST_VISIBLE_BIT.i,
                    vertexBufferSize,
                    model.vertices::buffer,
                    model.vertices::memory,
                    vertexBuffer.adr)
            // Index buffer
            vulkanDevice.createBuffer(
                    VkBufferUsage.INDEX_BUFFER_BIT.i,
                    VkMemoryProperty.HOST_VISIBLE_BIT.i,
                    indexBufferSize,
                    model.indices::buffer,
                    model.indices::memory,
                    indexBuffer.adr)
        }
    }

    fun loadAssets() {
        loadModel("$assetPath/models/voyager/voyager.dae")
        val (texture, format) = when {
            deviceFeatures.textureCompressionBC -> "voyager_bc3_unorm.ktx" to VkFormat.BC3_UNORM_BLOCK
            deviceFeatures.textureCompressionASTC_LDR -> "voyager_astc_8x8_unorm.ktx" to VkFormat.ASTC_8x8_UNORM_BLOCK
            deviceFeatures.textureCompressionETC2 -> "voyager_etc2_unorm.ktx" to VkFormat.ETC2_R8G8B8A8_UNORM_BLOCK
            else -> tools.exitFatal("Device does not support any compressed texture format!", ERROR_FEATURE_NOT_PRESENT)
        }
        textures.colorMap.loadFromFile("$assetPath/models/voyager/$texture", format, vulkanDevice, queue)
    }

    fun setupVertexDescriptions() {
        val vertex = Vertex()
        // Binding description
        vertices.bindingDescriptions = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, vertex.size, VkVertexInputRate.VERTEX)

        // Attribute descriptions
        // Describes memory layout and shader positions
        vertices.attributeDescriptions = vk.VertexInputAttributeDescription(
                // Location 0 : Position
                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, vertex.offsetOf("pos"),
                // Location 1 : Normal
                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32B32_SFLOAT, vertex.offsetOf("normal"),
                // Location 2 : Texture coordinates
                VERTEX_BUFFER_BIND_ID, 2, VkFormat.R32G32_SFLOAT, vertex.offsetOf("uv"),
                // Location 3 : Color
                VERTEX_BUFFER_BIND_ID, 3, VkFormat.R32G32B32_SFLOAT, vertex.offsetOf("color"))

        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertices.bindingDescriptions
            vertexAttributeDescriptions = vertices.attributeDescriptions
        }
    }

    fun setupDescriptorPool() {
        // Example uses one ubo and one combined image sampler
        val poolSizes = vk.DescriptorPoolSize(
                VkDescriptorType.UNIFORM_BUFFER, 1,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1)

        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 1)

        descriptorPool = device createDescriptorPool descriptorPoolInfo
    }

    fun setupDescriptorSetLayout() {

        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
                // Binding 0 : Vertex shader uniform buffer
                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0,
                // Binding 1 : Fragment shader combined sampler
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 1)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)

        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)

        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)

        descriptorSet = device allocateDescriptorSets allocInfo

        val texDescriptor = vk.DescriptorImageInfo(textures.colorMap.sampler, textures.colorMap.view, VkImageLayout.GENERAL)

        val writeDescriptorSets = vk.WriteDescriptorSet(
                // Binding 0 : Vertex shader uniform buffer
                descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.scene.descriptor,
                // Binding 1 : Color map
                descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, texDescriptor)

        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.BACK_BIT.i, VkFrontFace.CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Solid rendering pipeline
        // Load shaders
        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
            it[0].loadShader("$assetPath/shaders/mesh/mesh.vert.spv", VkShaderStage.VERTEX_BIT)
            it[1].loadShader("$assetPath/shaders/mesh/mesh.frag.spv", VkShaderStage.FRAGMENT_BIT)
        }
        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {
            it.vertexInputState = vertices.inputState
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
        }
        pipelines.solid = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)

        // Wire frame rendering pipeline
        if (deviceFeatures.fillModeNonSolid) {
            rasterizationState.polygonMode = VkPolygonMode.LINE
            rasterizationState.lineWidth = 1f
            pipelines.wireframe = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
        }
    }

    /** Prepare and initialize uniform buffer containing shader uniforms */
    fun prepareUniformBuffers() {
        // Vertex shader uniform buffer block
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBuffers.scene,
                VkDeviceSize(uboVS.size.L))

        // Map persistent
        uniformBuffers.scene.map()

        updateUniformBuffers()
    }

    fun updateUniformBuffers() {

        uboVS.projection = glm.perspective(60f.rad, size.aspect, 0.1f, 256f)
        val viewMatrix = glm.translate(Mat4(1f), Vec3(0f, 0f, zoom))

        uboVS.model = viewMatrix * glm.translate(Mat4(1f), cameraPos)
                .rotateAssign(rotation.x.rad, 1f, 0f, 0f)
                .rotateAssign(rotation.y.rad, 0f, 1f, 0f)
                .rotateAssign(rotation.z.rad, 0f, 0f, 1f)

        uboVS to uniformBuffers.scene.mapped
    }

    fun draw() {

        super.prepareFrame()

        // Command buffer to be sumitted to the queue
        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]

        // Submit to queue
        queue submit submitInfo

        super.submitFrame()
    }

    override fun prepare() {
        super.prepare()
        loadAssets()
        setupVertexDescriptions()
        prepareUniformBuffers()
        setupDescriptorSetLayout()
        preparePipelines()
        setupDescriptorPool()
        setupDescriptorSet()
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
//        if (overlay->checkBox("Wireframe", &wireframe)) {
//        buildCommandBuffers()
//    }
//    }
//    }
}