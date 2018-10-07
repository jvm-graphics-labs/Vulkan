///*
//* Vulkan Example - Push constants example (small shader block accessed outside of uniforms for fast updates)
//*
//* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
//*
//* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
//*/
//
//package vulkan.basics
//
//import glm_.L
//import glm_.func.rad
//import glm_.glm
//import glm_.mat4x4.Mat4
//import glm_.size
//import glm_.vec2.Vec2
//import glm_.vec3.Vec3
//import glm_.vec4.Vec4
//import kool.bufferBig
//import org.lwjgl.system.MemoryUtil.NULL
//import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
//import org.lwjgl.vulkan.VkVertexInputAttributeDescription
//import org.lwjgl.vulkan.VkVertexInputBindingDescription
//import vkk.*
//import vulkan.VERTEX_BUFFER_BIND_ID
//import vulkan.assetPath
//import vulkan.base.*
//import kotlin.math.cos
//import kotlin.math.sin
//
//
//fun main(args: Array<String>) {
//    PushConstants().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//
//private class PushConstants : VulkanExampleBase() {
//
//    object vertices {
//        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
//        lateinit var bindingDescriptions: VkVertexInputBindingDescription
//        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
//    }
//
//    // Vertex layout for the models
//    val vertexLayout = VertexLayout(
//            VertexComponent.POSITION,
//            VertexComponent.NORMAL,
//            VertexComponent.UV,
//            VertexComponent.COLOR)
//
//    object models {
//        val scene = Model()
//    }
//
//    val uniformBuffer = Buffer()
//
//    object uboVS: Bufferizable() {
//
//        lateinit var projection: Mat4
//        @Order(1)
//        lateinit var model: Mat4
//        @Order(2)
//        val lightPos = Vec4(0f, 0, -2f, 1)
//    }
//
//    object pipelines {
//        var solid = VkPipeline (NULL)
//    }
//
//    var pipelineLayout = VkPipelineLayout(NULL)
//    var descriptorSet= VkDescriptorSet (NULL)
//    var descriptorSetLayout= VkDescriptorSetLayout (NULL)
//
//    // This array holds the light positions and will be updated via a push constant
//    val pushConstants = bufferBig(Vec4.size * 6)
//
//    init {
//        zoom = -30f
//        zoomSpeed = 2.5f
//        rotationSpeed = 0.5f
//        timerSpeed *= 0.5f
//        rotation(-32.5f, 45f, 0f)
//        title = "Push constants"
////        settings.overlay = true
//    }
//
//    override fun destroy() {
//
//        device.apply {
//            // Clean up used Vulkan resources
//            // Note : Inherited destructor cleans up resources stored in base class
//            destroyPipeline(pipelines.solid)
//
//            destroyPipelineLayout(pipelineLayout)
//            destroyDescriptorSetLayout(descriptorSetLayout)
//        }
//        models.scene.destroy()
//
//        uniformBuffer.destroy()
//
//        super.destroy()
//    }
//
//    fun reBuildCommandBuffers() {
//        if (!checkCommandBuffers()) {
//            destroyCommandBuffers()
//            createCommandBuffers()
//        }
//        buildCommandBuffers()
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
//            renderPass = this@PushConstants.renderPass
//            renderArea.apply {
//                offset(0)
//                extent(size)
//            }
//            this.clearValues = clearValues
//        }
//        for (i in drawCmdBuffers.indices) {
//
//            // Set target frame buffer
//            renderPassBeginInfo.framebuffer(frameBuffers[i].L)
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
//                // Update light positions
//                // w component = light radius scale
//                val r = 7.5f
//                val sinT = sin((timer * 360).rad)
//                val cosT = cos((timer * 360).rad)
//                val y = -4f
//                Vec4(r * 1.1 * sinT, y, r * 1.1 * cosT, 1f) to pushConstants
//                Vec4(-r * sinT, y, -r * cosT, 1f).to(pushConstants, Vec4.size)
//                Vec4(r * 0.85f * sinT, y, -sinT * 2.5f, 1.5f).to(pushConstants, Vec4.size * 2)
//                Vec4(0f, y, r * 1.25f * cosT, 1.5f).to(pushConstants, Vec4.size * 3)
//                Vec4(r * 2.25f * cosT, y, 0f, 1.25f).to(pushConstants, Vec4.size * 4)
//                Vec4(r * 2.5f * cosT, y, r * 2.5f * sinT, 1.25f).to(pushConstants, Vec4.size * 5)
//
//                // Submit via push constant (rather than a UBO)
//                pushConstants(
//                        pipelineLayout,
//                        VkShaderStage.VERTEX_BIT.i,
//                        0,
//                        pushConstants)
//
//                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.solid)
//                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
//
//                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, models.scene.vertices.buffer)
//                bindIndexBuffer(models.scene.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
//
//                drawIndexed(models.scene.indexCount, 1, 0, 0, 0)
//
//                endRenderPass()
//
//                end()
//            }
//        }
//    }
//
//    fun loadAssets() {
//        models.scene.loadFromFile("$assetPath/models/samplescene.dae", vertexLayout, 0.35f, vulkanDevice, queue)
//    }
//
//    fun setupVertexDescriptions() {
//        // Binding description
//        vertices.bindingDescriptions = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, vertexLayout.stride, VkVertexInputRate.VERTEX)
//
//        // Attribute descriptions
//        // Describes memory layout and shader positions
//        vertices.attributeDescriptions = vk.VertexInputAttributeDescription(
//                // Location 0 : Position
//                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, 0,
//                // Location 1 : Normal
//                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32B32_SFLOAT, Vec3.size,
//                // Location 2 : Texture coordinates
//                VERTEX_BUFFER_BIND_ID, 2, VkFormat.R32G32_SFLOAT, Vec3.size * 2,
//                // Location 3 : Color
//                VERTEX_BUFFER_BIND_ID, 3, VkFormat.R32G32B32_SFLOAT, Vec3.size * 2 + Vec2.size)
//
//        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
//            vertexBindingDescription = vertices.bindingDescriptions
//            vertexAttributeDescriptions = vertices.attributeDescriptions
//        }
//    }
//
//    fun setupDescriptorPool() {
//        // Example uses one ubo
//        val poolSizes = vk.DescriptorPoolSize(VkDescriptorType.UNIFORM_BUFFER, 1)
//
//        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 2)
//
//        descriptorPool = device createDescriptorPool descriptorPoolInfo
//    }
//
//    fun setupDescriptorSetLayout() {
//
//        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
//                // Binding 0 : Vertex shader uniform buffer
//                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0)
//
//        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
//
//        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout
//
//        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)
//
//        /*  Define push constant
//            Example uses six light positions as push constants
//            6 * 4 * 4 = 96 bytes
//            Spec requires a minimum of 128 bytes, bigger values need to be checked against maxPushConstantsSize
//            But even at only 128 bytes, lots of stuff can fit inside push constants */
//        val pushConstantRange = vk.PushConstantRange(VkShaderStage.VERTEX_BIT.i, pushConstants.size, 0)
//
//        // Push constant ranges are part of the pipeline layout
//        pipelineLayoutCreateInfo.pushConstantRange = pushConstantRange
//
//        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
//    }
//
//    fun setupDescriptorSet() {
//
//        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)
//
//        descriptorSet = device allocateDescriptorSets allocInfo
//
//        // Binding 0 : Vertex shader uniform buffer
//        val writeDescriptorSet = vk.WriteDescriptorSet(descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffer.descriptor)
//
//        device updateDescriptorSets writeDescriptorSet
//    }
//
//    fun preparePipelines() {
//
//        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)
//
//        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.BACK_BIT.i, VkFrontFace.CLOCKWISE)
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
//        // Solid rendering pipeline
//        // Load shaders
//        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
//            it[0].loadShader("$assetPath/shaders/pushconstants/lights.vert.spv", VkShaderStage.VERTEX_BIT)
//            it[1].loadShader("$assetPath/shaders/pushconstants/lights.frag.spv", VkShaderStage.FRAGMENT_BIT)
//        }
//        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {
//            it.vertexInputState = vertices.inputState
//            it.inputAssemblyState = inputAssemblyState
//            it.rasterizationState = rasterizationState
//            it.colorBlendState = colorBlendState
//            it.multisampleState = multisampleState
//            it.viewportState = viewportState
//            it.depthStencilState = depthStencilState
//            it.dynamicState = dynamicState
//            it.stages = shaderStages
//        }
//        pipelines.solid = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
//    }
//
//    fun prepareUniformBuffers() {
//        // Vertex shader uniform buffer block
//        vulkanDevice.createBuffer(
//                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                uniformBuffer,
//                VkDeviceSize(uboVS.size.L))
//
//        // Map persistent
//        uniformBuffer.map()
//
//        updateUniformBuffers()
//    }
//
//    fun updateUniformBuffers()    {
//        // Vertex shader
//        var viewMatrix = Mat4(1f)
//        uboVS.projection = glm.perspective(60f.rad, size.aspect, 0.001f, 256f)
//        viewMatrix = glm.translate(viewMatrix, 0f, 2f, zoom)
//
//        uboVS.model = viewMatrix * glm.translate(Mat4(1f), Vec3())
//                .rotateAssign(rotation.x.rad, 1f, 0f, 0f)
//                .rotateAssign(rotation.y.rad, 0f, 1f, 0f)
//                .rotateAssign(rotation.z.rad, 0f, 0f, 1f)
//
//        uboVS to uniformBuffer.mapped
//    }
//
//    fun draw()    {
//
//        super.prepareFrame()
//
//        // Command buffer to be sumitted to the queue
//        submitInfo.commandBuffer = drawCmdBuffers [currentBuffer]
//
//        // Submit to queue
//        queue submit submitInfo
//
//        super.submitFrame()
//    }
//
//    override fun prepare()    {
//
//        super.prepare()
//
//        // Check requested push constant size against hardware limit
//        // Specs require 128 bytes, so if the device complies our push constant buffer should always fit into memory
//        assert(pushConstants.size <= vulkanDevice.properties.limits.maxPushConstantsSize)
//
//        loadAssets()
//        setupVertexDescriptions()
//        prepareUniformBuffers()
//        setupDescriptorSetLayout()
//        preparePipelines()
//        setupDescriptorPool()
//        setupDescriptorSet()
//        buildCommandBuffers()
//        prepared = true
//        window.show()
//    }
//
//    override fun render()
//    {
//        if (!prepared)
//            return
//        draw()
//        if (!paused)
//            reBuildCommandBuffers()
//    }
//
//    override fun viewChanged() = updateUniformBuffers()
//}