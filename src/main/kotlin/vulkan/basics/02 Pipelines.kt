package vulkan.basics

import glm_.L
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import org.lwjgl.system.MemoryUtil.NULL
import vkk.*
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.assetPath
import vulkan.base.*

/*
* Vulkan Example - Using different pipelines in one single renderpass
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/


fun main(args: Array<String>) {
    Pipelines().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class Pipelines : VulkanExampleBase() {

    /** Vertex layout for the models */
    val vertexLayout = VertexLayout(
            VertexComponent.POSITION,
            VertexComponent.NORMAL,
            VertexComponent.UV,
            VertexComponent.COLOR)

    object models {
        val cube = Model()
    }

    val uniformBuffer = Buffer()

    /** Same uniform buffer layout as shader */
    object uboVS : Bufferizable() {
        var projection = Mat4()
        @Order(1)
        var modelView = Mat4()
        @Order(2)
        val lightPos = Vec4(0f, 2f, 1f, 0f)
    }

    var pipelineLayout: VkPipelineLayout = NULL
    var descriptorSet: VkDescriptorSet = NULL
    var descriptorSetLayout: VkDescriptorSetLayout = NULL

    object pipelines {
        var phong: VkPipeline = NULL
        var wireframe: VkPipeline = NULL
        var toon: VkPipeline = NULL
    }

    init {
        zoom = -10.5f
        rotation(-25f, 15f, 0f)
        title = "Pipeline state objects"
//        settings.overlay = true
    }

    override fun destroy() {
        // Clean up used Vulkan resources
        // Note : Inherited destructor cleans up resources stored in base class
        device.apply {
            destroyPipeline(pipelines.phong)
            if (deviceFeatures.fillModeNonSolid)
                destroyPipeline(pipelines.wireframe)
            destroyPipeline(pipelines.toon)

            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }
        models.cube.destroy()
        uniformBuffer.destroy()

        super.destroy()
    }

    // Enable physical device features required for this example
    override fun getEnabledFeatures() {
        // Fill mode non solid is required for wireframe display
        if (deviceFeatures.fillModeNonSolid) {
            enabledFeatures.fillModeNonSolid = true
            // Wide lines must be present for line width > 1.0f
            if (deviceFeatures.wideLines)
                enabledFeatures.wideLines = true
        }
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo {}

        val clearValues = vk.ClearValue(2)
        clearValues[0].color(defaultClearColor)
        clearValues[1].depthStencil(1f, 0)

        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@Pipelines.renderPass
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

                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                val viewport = vk.Viewport(size)
                setViewport(viewport)

                setScissor(size)

                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)

                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, models.cube.vertices.buffer)
                bindIndexBuffer(models.cube.indices.buffer, 0, VkIndexType.UINT32)

                // Left : Solid colored
                viewport.width = size.x / 3f
                setViewport(viewport)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.phong)

                drawIndexed(models.cube.indexCount, 1, 0, 0, 0)

                // Center : Toon
                viewport.x = size.x / 3f
                setViewport(viewport)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.toon)
                // Line width > 1.0f only if wide lines feature is supported
                if (deviceFeatures.wideLines)
                    setLineWidth(2f)

                drawIndexed(models.cube.indexCount, 1, 0, 0, 0)

                if (deviceFeatures.fillModeNonSolid) {
                    // Right : Wireframe
                    viewport.x = size.x / 3f + size.x / 3f
                    setViewport(viewport)
                    bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.wireframe)
                    drawIndexed(models.cube.indexCount, 1, 0, 0, 0)
                }

                endRenderPass()

                end()
            }
        }
    }

    fun loadAssets() {
        models.cube.loadFromFile("$assetPath/models/treasure_smooth.dae", vertexLayout, 1f, vulkanDevice, queue)
    }

    fun setupDescriptorPool() {

        val poolSize = vk.DescriptorPoolSize(VkDescriptorType.UNIFORM_BUFFER, 1)

        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSize, 2)

        descriptorPool = device createDescriptorPool descriptorPoolInfo
    }

    fun setupDescriptorSetLayout() {

        val setLayoutBinding = vk.DescriptorSetLayoutBinding(VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBinding)

        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)

        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)

        descriptorSet = device allocateDescriptorSets allocInfo

        val writeDescriptorSet = vk.WriteDescriptorSet(
                descriptorSet,
                VkDescriptorType.UNIFORM_BUFFER,
                0,  // Binding 0 : Vertex shader uniform buffer
                uniformBuffer.descriptor)

        device updateDescriptorSets writeDescriptorSet
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(
                VkPrimitiveTopology.TRIANGLE_LIST,
                0,
                false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(
                VkPolygonMode.FILL,
                VkCullMode.BACK_BIT.i,
                VkFrontFace.CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR, VkDynamicState.LINE_WIDTH)

        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass)

        val shaderStages = vk.PipelineShaderStageCreateInfo(2)

        pipelineCreateInfo.let {
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
        }
        // Shared vertex bindings and attributes used by all pipelines

        // Binding description
        val vertexInputBindings = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, vertexLayout.stride, VkVertexInputRate.VERTEX)

        // Attribute descriptions
        val vertexInputAttributes = vk.VertexInputAttributeDescription(
                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, 0,                         // Location 0: Position
                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32B32_SFLOAT, Vec3.size,                         // Location 1: Color
                VERTEX_BUFFER_BIND_ID, 2, VkFormat.R32G32_SFLOAT, Vec3.size * 2,                // Location 2 : Texture coordinates
                VERTEX_BUFFER_BIND_ID, 3, VkFormat.R32G32B32_SFLOAT, Vec3.size * 2 + Vec2.size) // Location 3 : Normal

        val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertexInputBindings
            vertexAttributeDescriptions = vertexInputAttributes
        }
        pipelineCreateInfo.vertexInputState = vertexInputState

        /*  Create the graphics pipeline state objects

            We are using this pipeline as the base for the other pipelines (derivatives)
            Pipeline derivatives can be used for pipelines that share most of their state
            Depending on the implementation this may result in better performance for pipeline switchting and faster creation time */
        pipelineCreateInfo.flags = VkPipelineCreate.ALLOW_DERIVATIVES_BIT.i

        // Textured pipeline
        // Phong shading pipeline
        shaderStages[0].loadShader("$assetPath/shaders/pipelines/phong.vert.spv", VkShaderStage.VERTEX_BIT)
        shaderStages[1].loadShader("$assetPath/shaders/pipelines/phong.frag.spv", VkShaderStage.FRAGMENT_BIT)
        pipelines.phong = device.createPipeline(pipelineCache, pipelineCreateInfo)

        // All pipelines created after the base pipeline will be derivatives
        pipelineCreateInfo.flags = VkPipelineCreate.DERIVATIVE_BIT.i
        // Base pipeline will be our first created pipeline
        pipelineCreateInfo.basePipelineHandle = pipelines.phong
        // It's only allowed to either use a handle or index for the base pipeline
        // As we use the handle, we must set the index to -1 (see section 9.5 of the specification)
        pipelineCreateInfo.basePipelineIndex = -1

        // Toon shading pipeline
        shaderStages[0].loadShader("$assetPath/shaders/pipelines/toon.vert.spv", VkShaderStage.VERTEX_BIT)
        shaderStages[1].loadShader("$assetPath/shaders/pipelines/toon.frag.spv", VkShaderStage.FRAGMENT_BIT)
        pipelines.toon = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)

        // Pipeline for wire frame rendering
        // Non solid rendering is not a mandatory Vulkan feature
        if (deviceFeatures.fillModeNonSolid) {
            rasterizationState.polygonMode = VkPolygonMode.LINE
            shaderStages[0].loadShader("$assetPath/shaders/pipelines/wireframe.vert.spv", VkShaderStage.VERTEX_BIT)
            shaderStages[1].loadShader("$assetPath/shaders/pipelines/wireframe.frag.spv", VkShaderStage.FRAGMENT_BIT)
            pipelines.wireframe = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
        }
    }

    // Prepare and initialize uniform buffer containing shader uniforms
    fun prepareUniformBuffers()    {
        // Create the vertex shader uniform buffer block
        vulkanDevice.createBuffer(
        VkBufferUsage.UNIFORM_BUFFER_BIT.i,
        VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
        uniformBuffer,
        uboVS.size.L)

        // Map persistent
        uniformBuffer.map()

        updateUniformBuffers()
    }

    fun updateUniformBuffers()    {

        uboVS.projection = glm.perspective(60f.rad, (size.x / 3f) / size.y, 0.1f, 256f)

        val viewMatrix = glm.translate(Mat4(1f), Vec3(0f, 0f, zoom))

        uboVS.modelView = viewMatrix translate cameraPos
        uboVS.modelView
                .rotateAssign(rotation.x.rad, 1f, 0f, 0f)
                .rotateAssign(rotation.y.rad, 0f, 1f, 0f)
                .rotateAssign(rotation.z.rad, 0f, 0f, 1f)

        uboVS to uniformBuffer.mapped[0]
    }

    fun draw()    {

        super.prepareFrame()

        submitInfo.commandBuffer = drawCmdBuffers [currentBuffer]
        queue submit submitInfo

        super.submitFrame()
    }

    override fun prepare()    {
        super.prepare()
        loadAssets()
        prepareUniformBuffers()
        setupDescriptorSetLayout()
        preparePipelines()
        setupDescriptorPool()
        setupDescriptorSet()
        buildCommandBuffers()
        prepared = true
        window.show()
    }

    override fun render()    {
        if (!prepared)
            return
        draw()
    }

    override fun viewChanged() = updateUniformBuffers()

//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
//    {
//        if (!deviceFeatures.fillModeNonSolid) {
//            if (overlay->header("Info")) { overlay ->
//                text("Non solid fill modes not supported!")
//            }
//        }
//    }
}