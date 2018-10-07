//package vulkan.basics
//
//import glm_.L
//import glm_.mat4x4.Mat4
//import glm_.vec3.Vec3
//import glm_.vec4.Vec4
//import org.lwjgl.system.MemoryUtil.NULL
//import vkk.*
//import vulkan.assetPath
//import vulkan.base.*
//
///*
//* Vulkan Example - Rendering outlines using the stencil buffer
//*
//* Copyright (C) 2016-2017 by Sascha Willems - www.saschawillems.de
//*
//* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
//*/
//
//fun main(args: Array<String>) {
//    StencilBuffer().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//private class StencilBuffer : VulkanExampleBase() {
//
//    // Vertex layout for the models
//    val vertexLayout = VertexLayout(
//            VertexComponent.POSITION,
//            VertexComponent.COLOR,
//            VertexComponent.NORMAL)
//
//    val model = Model()
//
//    object uboVS : Bufferizable() {
//        var projection = Mat4()
//        var model = Mat4()
//        val lightPos = Vec4(0f, -2f, 1f, 0f)
//        // Vertex shader extrudes model by this value along normals for outlining
//        var outlineWidth = 0.05f
//    }
//
//    val uniformBufferVS = Buffer()
//
//    object pipelines {
//        var stencil = VkPipeline(NULL)
//        var outline = VkPipeline(NULL)
//    }
//
//    var pipelineLayout = VkPipelineLayout(NULL)
//    var descriptorSet = VkDescriptorSet(NULL)
//    var descriptorSetLayout = VkDescriptorSetLayout(NULL)
//
//    init {
//        title = "Stencil buffer outlines"
//        timerSpeed *= 0.25f
//        camera.type = Camera.CameraType.lookAt
//        camera.setPerspective(60f, size.aspect, 0.1f, 512f)
//        camera.setRotation(Vec3(2.5f, -35f, 0f))
//        camera.setTranslation(Vec3(0.08f, 3.6f, -8.4f))
////        settings.overlay = true
//    }
//
//    override fun destroy() {
//        super.destroy()
//
//        device.apply {
//            destroyPipeline(pipelines.stencil)
//            destroyPipeline(pipelines.outline)
//
//            destroyPipelineLayout(pipelineLayout)
//
//            destroyDescriptorSetLayout(descriptorSetLayout)
//        }
//        model.destroy()
//        uniformBufferVS.destroy()
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
//            renderPass = this@StencilBuffer.renderPass
//            renderArea.offset(0)
//            renderArea.extent(size)
//            this.clearValues = clearValues
//        }
//        for (i in drawCmdBuffers.indices) {
//
//            renderPassBeginInfo.framebuffer(frameBuffers[i].L) // bug
//
//            drawCmdBuffers[i].apply {
//
//                begin(cmdBufInfo)
//
//                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)
//
//                setViewport(size)
//                setScissor(size)
//
//                bindVertexBuffers(model.vertices.buffer)
//                bindIndexBuffer(model.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
//
//                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
//
//                // First pass renders object (toon shaded) and fills stencil buffer
//                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.stencil)
//                drawIndexed(model.indexCount, 1, 0, 0, 0)
//
//                // Second pass renders scaled object only where stencil was not set by first pass
//                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.outline)
//                drawIndexed(model.indexCount, 1, 0, 0, 0)
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
//    fun loadAssets() {
//        model.loadFromFile("$assetPath/models/venus.fbx", vertexLayout, 0.3f, vulkanDevice, queue)
//    }
//
//    fun setupDescriptorPool() {
//
//        val poolSize = vk.DescriptorPoolSize(VkDescriptorType.UNIFORM_BUFFER, 1)
//
//        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSize, 1)
//        descriptorPool = device createDescriptorPool descriptorPoolInfo
//    }
//
//    fun setupDescriptorSetLayout() {
//
//        val setLayoutBinding = vk.DescriptorSetLayoutBinding(
//                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0)
//
//        val descriptorLayoutInfo = vk.DescriptorSetLayoutCreateInfo(setLayoutBinding)
//        descriptorSetLayout = device createDescriptorSetLayout descriptorLayoutInfo
//
//        val pipelineLayoutInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)
//        pipelineLayout = device createPipelineLayout pipelineLayoutInfo
//    }
//
//    fun setupDescriptorSet() {
//        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)
//        descriptorSet = device allocateDescriptorSets allocInfo
//        val modelWriteDescriptorSets = vk.WriteDescriptorSet(
//                descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBufferVS.descriptor)
//        device updateDescriptorSets modelWriteDescriptorSets
//    }
//
//    fun preparePipelines() {
//
//        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST)
//
//        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.FRONT_BIT.i, VkFrontFace.CLOCKWISE)
//
//        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)
//
//        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)
//
//        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)
//
//        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)
//
//        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)
//
//        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
//        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)
//
//        val shaderStages = vk.PipelineShaderStageCreateInfo(2)
//
//        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass)
//
//        // Vertex bindings an attributes
//        val vertexInputBinding = vk.VertexInputBindingDescription(0, vertexLayout.stride, VkVertexInputRate.VERTEX)
//
//        val vertexInputAttributes = vk.VertexInputAttributeDescription(
//                0, 0, VkFormat.R32G32B32_SFLOAT, 0,              // Location 0: Position
//                0, 1, VkFormat.R32G32B32_SFLOAT, Vec3.size,             // Location 1: Color
//                0, 2, VkFormat.R32G32B32_SFLOAT, Vec3.size * 2) // Location 2: Normal
//
//        val vertexInputState = vk.PipelineVertexInputStateCreateInfo().apply {
//            vertexBindingDescription = vertexInputBinding
//            vertexAttributeDescriptions = vertexInputAttributes
//        }
//        pipelineCreateInfo.also {
//            it.vertexInputState = vertexInputState
//            it.inputAssemblyState = inputAssemblyState
//            it.rasterizationState = rasterizationState
//            it.colorBlendState = colorBlendState
//            it.multisampleState = multisampleState
//            it.viewportState = viewportState
//            it.depthStencilState = depthStencilState
//            it.dynamicState = dynamicState
//            it.stages = shaderStages
//        }
//        // Toon render and stencil fill pass
//        shaderStages[0].loadShader("$assetPath/shaders/stencilbuffer/toon.vert.spv", VkShaderStage.VERTEX_BIT)
//        shaderStages[1].loadShader("$assetPath/shaders/stencilbuffer/toon.frag.spv", VkShaderStage.FRAGMENT_BIT)
//
//        rasterizationState.cullMode = VkCullMode.NONE.i
//
//        depthStencilState.apply {
//            stencilTestEnable = true
//
//            back.apply {
//                compareOp = VkCompareOp.ALWAYS
//                failOp = VkStencilOp.REPLACE
//                depthFailOp = VkStencilOp.REPLACE
//                passOp = VkStencilOp.REPLACE
//                compareMask = 0xff
//                writeMask = 0xff
//                reference = 1
//            }
//            front = back
//        }
//        pipelines.stencil = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
//
//        // Outline pass
//        depthStencilState.apply {
//            back.apply {
//                compareOp = VkCompareOp.NOT_EQUAL
//                failOp = VkStencilOp.KEEP
//                depthFailOp = VkStencilOp.KEEP
//                passOp = VkStencilOp.REPLACE
//            }
//            front = back
//            depthTestEnable = false
//        }
//        shaderStages[0].loadShader("$assetPath/shaders/stencilbuffer/outline.vert.spv", VkShaderStage.VERTEX_BIT)
//        shaderStages[1].loadShader("$assetPath/shaders/stencilbuffer/outline.frag.spv", VkShaderStage.FRAGMENT_BIT)
//        pipelines.outline = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
//    }
//
//    // Prepare and initialize uniform buffer containing shader uniforms
//    fun prepareUniformBuffers() {
//        // Mesh vertex shader uniform buffer block
//        vulkanDevice.createBuffer(
//                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                uniformBufferVS,
//                VkDeviceSize(uboVS.size.L))
//
//        // Map persistent
//        uniformBufferVS.map()
//
//        updateUniformBuffers()
//    }
//
//    fun updateUniformBuffers() {
//        uboVS.projection = camera.matrices.perspective
//        uboVS.model = camera.matrices.view
//        uboVS to uniformBufferVS.mapped
//    }
//
//    fun draw() {
//
//        super.prepareFrame()
//
//        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]
//        queue submit submitInfo
//
//        super.submitFrame()
//    }
//
//    override fun prepare() {
//        super.prepare()
//        loadAssets()
//        prepareUniformBuffers()
//        setupDescriptorSetLayout()
//        preparePipelines()
//        setupDescriptorPool()
//        setupDescriptorSet()
//        buildCommandBuffers()
//        prepared = true
//    }
//
//    override fun render() {
//        if (!prepared)
//            return
//        draw()
//    }
//
//    override fun viewChanged() = updateUniformBuffers()
//
////    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)    {
////       if (overlay->header("Settings"))
////        if (overlay->inputFloat("Outline width", &uboVS.outlineWidth, 0.05f, 2))
////        updateUniformBuffers()
////    }
//}
