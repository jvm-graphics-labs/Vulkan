/*
* Vulkan Example - Shader specialization constants
*
* For details see https://www.khronos.org/registry/vulkan/specs/misc/GL_KHR_vulkan_glsl.txt
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.basics

import ab.appBuffer
import glm_.BYTES
import glm_.L
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import vkk.*
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.assetPath
import vulkan.base.*


fun main(args: Array<String>) {
    SpecializationConstants().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}


class SpecializationConstants : VulkanExampleBase() {

    object vertices {
        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
        lateinit var bindingDescriptions: VkVertexInputBindingDescription
        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
    }

    // Vertex layout for the models
    val vertexLayout = VertexLayout(
            VertexComponent.POSITION,
            VertexComponent.NORMAL,
            VertexComponent.UV,
            VertexComponent.COLOR)

    object models {
        val cube = Model()
    }

    object textures {
        val colormap = Texture2D()
    }

    val uniformBuffer = Buffer()

    // Same uniform buffer layout as shader
    object uboVS : Bufferizable() {

        lateinit var projection: Mat4
        @Order(1)
        lateinit var modelView: Mat4
        @Order(2)
        val lightPos = Vec4(0f, -2f, 1f, 0f)
    }

    var pipelineLayout: VkPipelineLayout = NULL
    var descriptorSet: VkDescriptorSet = NULL
    var descriptorSetLayout: VkDescriptorSetLayout = NULL

    object pipelines {
        var phong: VkPipeline = NULL
        var toon: VkPipeline = NULL
        var textured: VkPipeline = NULL
    }

    init {
        title = "Specialization constants"
        camera.type = Camera.CameraType.lookAt
        camera.setPerspective(60f, (size.x / 3f) / size.y, 0.1f, 512f)
        camera.setRotation(Vec3(-40f, -90f, 0f))
        camera.setTranslation(Vec3(0f, 0f, -2f))
        settings.overlay = false // TODO
    }

    override fun destroy() {
        device.apply {
            destroyPipeline(pipelines.phong)
            destroyPipeline(pipelines.textured)
            destroyPipeline(pipelines.toon)

            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }
        models.cube.destroy()
        textures.colormap.destroy()
        uniformBuffer.destroy()

        super.destroy()
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2).also {
            it[0].color(defaultClearColor)
            it[1].depthStencil(1f, 0)
        }
        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@SpecializationConstants.renderPass
            renderArea.apply {
                offset(0)
                extent(size)
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

                // Left
                viewport.width = size.x / 3f
                setViewport(viewport)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.phong)

                drawIndexed(models.cube.indexCount, 1, 0, 0, 0)

                // Center
                viewport.x = size.x / 3f
                setViewport(viewport)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.toon)
                drawIndexed(models.cube.indexCount, 1, 0, 0, 0)

                // Right
                viewport.x = size.x / 3f + size.x / 3f
                setViewport(viewport)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.textured)
                drawIndexed(models.cube.indexCount, 1, 0, 0, 0)

                endRenderPass()

                end()
            }
        }
    }

    fun loadAssets() {
        models.cube.loadFromFile("$assetPath/models/color_teapot_spheres.dae", vertexLayout, 0.1f, vulkanDevice, queue)
        textures.colormap.loadFromFile("$assetPath/textures/metalplate_nomips_rgba.ktx", VkFormat.R8G8B8A8_UNORM, vulkanDevice, queue)
    }

    fun setupVertexDescriptions() {
        // Binding description
        vertices.bindingDescriptions = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, vertexLayout.stride, VkVertexInputRate.VERTEX)

        // Attribute descriptions
        vertices.attributeDescriptions = vk.VertexInputAttributeDescription(
                // Location 0 : Position
                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, 0,
                // Location 1 : Color
                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32B32_SFLOAT, Vec3.size,
                // Location 3 : Texture coordinates
                VERTEX_BUFFER_BIND_ID, 2, VkFormat.R32G32_SFLOAT, Vec3.size * 2,
                // Location 2 : Normal
                VERTEX_BUFFER_BIND_ID, 3, VkFormat.R32G32B32_SFLOAT, Vec3.size * 2 + Vec2.size)

        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertices.bindingDescriptions
            vertexAttributeDescriptions = vertices.attributeDescriptions
        }
    }

    fun setupDescriptorPool() {

        val poolSizes = vk.DescriptorPoolSize(
                VkDescriptorType.UNIFORM_BUFFER, 1,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1)

        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 1)

        descriptorPool = device createDescriptorPool descriptorPoolInfo
    }

    fun setupDescriptorSetLayout() {

        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 1)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)

        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)

        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)

        descriptorSet = device allocateDescriptorSets allocInfo

        val writeDescriptorSets = vk.WriteDescriptorSet(
                descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffer.descriptor,
                descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, textures.colormap.descriptor)

        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.NONE.i, VkFrontFace.CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR, VkDynamicState.LINE_WIDTH)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        val shaderStages = vk.PipelineShaderStageCreateInfo(2)

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
        // Prepare specialization data

        // Host data to take specialization constants from
        val specializationData = appBuffer.buffer(Int.BYTES + Float.BYTES).apply {
            putFloat(Int.BYTES, 0.5f)
        }
//        {
//            // Sets the lighting model used in the fragment "uber" shader
//            uint32_t lightingModel
//                    // Parameter for the toon shading part of the fragment shader
//                    float toonDesaturationFactor = 0.5f
//        }

        // Each shader constant of a shader stage corresponds to one map entry
        val specializationMapEntries = vk.SpecializationMapEntry(2).also {
            // Shader bindings based on specialization constants are marked by the new "constant_id" layout qualifier:
            //	layout (constant_id = 0) const int LIGHTING_MODEL = 0;
            //	layout (constant_id = 1) const float PARAM_TOON_DESATURATION = 0.0f;

            // Map entry for the lighting model to be used by the fragment shader
            it[0].apply {
                constantId = 0
                size = Int.BYTES.L
                offset = 0
            }
            // Map entry for the toon shader parameter
            it[1].apply {
                constantId = 1
                size = Float.BYTES.L
                offset = Int.BYTES
            }
        }
        // Prepare specialization info block for the shader stage
        val specializationInfo = vk.SpecializationInfo {
            mapEntries = specializationMapEntries
            data = specializationData
        }
        // Create pipelines
        // All pipelines will use the same "uber" shader and specialization constants to change branching and parameters of that shader
        shaderStages[0].loadShader("$assetPath/shaders/specializationconstants/uber.vert.spv", VkShaderStage.VERTEX_BIT)
        shaderStages[1].loadShader("$assetPath/shaders/specializationconstants/uber.frag.spv", VkShaderStage.FRAGMENT_BIT)
        // Specialization info is assigned is part of the shader stage (modul) and must be set after creating the module and before creating the pipeline
        shaderStages[1].specializationInfo = specializationInfo

        // Solid phong shading
        specializationData.putInt(0, 0)
        pipelines.phong = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)

        // Phong and textured
        specializationData.putInt(0, 1)
        pipelines.toon = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)

        // Textured discard
        specializationData.putInt(0, 2)
        pipelines.textured = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    // Prepare and initialize uniform buffer containing shader uniforms
    fun prepareUniformBuffers() {
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

    fun updateUniformBuffers() {
        uboVS.projection = camera.matrices.perspective
        uboVS.modelView = camera.matrices.view

        uboVS to uniformBuffer.mapped
    }

    fun draw() {

        super.prepareFrame()

        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]
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
}