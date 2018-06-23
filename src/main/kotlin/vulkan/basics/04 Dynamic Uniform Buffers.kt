/*
* Vulkan Example - Dynamic uniform buffers
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*
* Summary:
* Demonstrates the use of dynamic uniform buffers.
*
* Instead of using one uniform buffer per-object, this example allocates one big uniform buffer
* with respect to the alignment reported by the device via minUniformBufferOffsetAlignment that
* contains all matrices for the objects in the scene.
*
* The used descriptor type VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC then allows to set a dynamic
* offset used to pass data from the single uniform buffer to the connected shader binding point.
*/

package vulkan.basics

import ab.appBuffer
import glm_.L
import glm_.buffer.adr
import glm_.buffer.bufferBig
import glm_.buffer.free
import glm_.glm
import glm_.i
import glm_.mat4x4.Mat4
import glm_.pow
import glm_.vec3.Vec3
import glm_.vec3.operators.times
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memCopy
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import uno.kotlin.buffers.capacity
import vkk.*
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.assetPath
import vulkan.base.Buffer
import vulkan.base.Camera
import vulkan.base.VulkanExampleBase
import vulkan.base.initializers
import java.nio.ByteBuffer

private const val OBJECT_INSTANCES = 125


fun main(args: Array<String>) {
    DynamicUniformBuffers().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class DynamicUniformBuffers : VulkanExampleBase() {

    /** Vertex layout for this example */
    object Vertex {
        //        float pos[3];
//        float color[3];
        val size = Vec3.size * 2
        val offsetPos = 0
        val offsetColor = Vec3.size
    }

    object vertices {
        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
        lateinit var bindingDescriptions: VkVertexInputBindingDescription
        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
    }

    val vertexBuffer = Buffer()
    val indexBuffer = Buffer()
    var indexCount = 0

    object uniformBuffers {
        val view = Buffer()
        val dynamic = Buffer()
    }

    object uboVS : Bufferizable() {
        val projection = Mat4()
        val view = Mat4()
    }

    // Store random per-object rotations
    val rotations = Array(OBJECT_INSTANCES) { Vec3() }
    val rotationSpeeds = Array(OBJECT_INSTANCES) { Vec3() }

    /** One big uniform buffer that contains all matrices
     *  Note that we need to manually allocate the data to cope for GPU-specific uniform buffer offset alignments */
    object uboDataDynamic {
        lateinit var model: ByteBuffer
        var address = NULL
    }

    var pipeline: VkPipeline = NULL
    var pipelineLayout: VkPipelineLayout = NULL
    var descriptorSet: VkDescriptorSet = NULL
    var descriptorSetLayout: VkDescriptorSetLayout = NULL

    var animationTimer = 0f

    var dynamicAlignment = 0L

    init {
        title = "Vulkan Example - Dynamic uniform buffers"
        camera.type = Camera.CameraType.lookAt
        camera.setPosition(Vec3(0f, 0f, -30f))
        camera.setRotation(Vec3(.0f))
        camera.setPerspective(60f, size.aspect, 0.1f, 256f)
//        settings.overlay = true TODO
    }

    override fun destroy() {

        uboDataDynamic.model.free()

        /*  Clean up used Vulkan resources
            Note : Inherited destructor cleans up resources stored in base class         */
        device.apply {
            destroyPipeline(pipeline)

            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }

        vertexBuffer.destroy()
        indexBuffer.destroy()

        uniformBuffers.view.destroy()
        uniformBuffers.dynamic.destroy()

        super.destroy()
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2).also {
            it[0].color(defaultClearColor)
            it[1].depthStencil(1f, 0)
        }
        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@DynamicUniformBuffers.renderPass
            renderArea.apply {
                offset(0)
                extent(size)
            }
            this.clearValues = clearValues
        }

        for (i in drawCmdBuffers.indices) {

            renderPassBeginInfo.framebuffer(frameBuffers[i])

            drawCmdBuffers[i].apply {

                begin(cmdBufInfo)

                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                setViewport(size)
                setScissor(size)

                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)

                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, vertexBuffer.buffer)
                bindIndexBuffer(indexBuffer.buffer, 0, VkIndexType.UINT32)

                // Render multiple objects using different model matrices by dynamically offsetting into one uniform buffer
                repeat(OBJECT_INSTANCES) {
                    // One dynamic offset per dynamic descriptor to offset into the ubo containing all model matrices
                    val dynamicOffset = it * dynamicAlignment
                    // Bind the descriptor set for rendering a mesh using the dynamic offset
                    bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet, dynamicOffset.i)

                    drawIndexed(indexCount, 1, 0, 0, 0)
                }

                endRenderPass()

                end()
            }
        }
    }

    fun draw() {

        super.prepareFrame()

        // Command buffer to be submitted to the queue
        submitInfo.commandBuffers = appBuffer.pointerBufferOf(drawCmdBuffers[currentBuffer])

        // Submit to queue
        queue submit submitInfo

        super.submitFrame()
    }

    fun generateCube() {
        // Setup vertices indices for a colored cube
        val vertices = appBuffer.floatBufferOf(
                -1f, -1f, +1f, 1f, 0f, 0f,
                +1f, -1f, +1f, 0f, 1f, 0f,
                +1f, +1f, +1f, 0f, 0f, 1f,
                -1f, +1f, +1f, 0f, 0f, 0f,
                -1f, -1f, -1f, 1f, 0f, 0f,
                +1f, -1f, -1f, 0f, 1f, 0f,
                +1f, +1f, -1f, 0f, 0f, 1f,
                -1f, +1f, -1f, 0f, 0f, 0f)

        val indices = appBuffer.intBufferOf(0, 1, 2, 2, 3, 0, 1, 5, 6, 6, 2, 1, 7, 6, 5, 5, 4, 7, 4, 0, 3, 3, 7, 4, 4, 5, 1, 1, 0, 4, 3, 2, 6, 6, 7, 3)

        indexCount = indices.capacity

        // Create buffers
        // For the sake of simplicity we won't stage the vertex data to the gpu memory
        val flags = VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT
        // Vertex buffer
        vulkanDevice.createBuffer(VkBufferUsage.VERTEX_BUFFER_BIT.i, flags, vertexBuffer, vertices)
        // Index buffer
        vulkanDevice.createBuffer(VkBufferUsage.INDEX_BUFFER_BIT.i, flags, indexBuffer, indices)
    }

    fun setupVertexDescriptions() {
        // Binding description
        vertices.bindingDescriptions = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, Vertex.size, VkVertexInputRate.VERTEX)

        // Attribute descriptions
        vertices.attributeDescriptions = vk.VertexInputAttributeDescription(
                // Location 0 : Position
                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, Vertex.offsetPos,
                // Location 1 : Color
                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32B32_SFLOAT, Vertex.offsetColor)

        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertices.bindingDescriptions
            vertexAttributeDescriptions = vertices.attributeDescriptions
        }
    }

    fun setupDescriptorPool() {
        // Example uses one ubo and one image sampler
        val poolSizes = vk.DescriptorPoolSize(
                VkDescriptorType.UNIFORM_BUFFER, 1,
                VkDescriptorType.UNIFORM_BUFFER_DYNAMIC, 1,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1)

        descriptorPool = device createDescriptorPool initializers.descriptorPoolCreateInfo(poolSizes, 2)
    }

    fun setupDescriptorSetLayout() {

        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0,
                VkDescriptorType.UNIFORM_BUFFER_DYNAMIC, VkShaderStage.VERTEX_BIT.i, 1,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 2)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo { bindings = setLayoutBindings }

        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo { setLayout = descriptorSetLayout }

        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)

        descriptorSet = device allocateDescriptorSets allocInfo

        val writeDescriptorSets = vk.WriteDescriptorSet(
                // Binding 0 : Projection/View matrix uniform buffer
                descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.view.descriptor,
                // Binding 1 : Instance matrix as dynamic uniform buffer
                descriptorSet, VkDescriptorType.UNIFORM_BUFFER_DYNAMIC, 1, uniformBuffers.dynamic.descriptor)

        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.NONE.i, VkFrontFace.COUNTER_CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(rasterizationSamples = VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)

        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Load shaders
        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
            it[0].loadShader("$assetPath/shaders/dynamicuniformbuffer/base.vert.spv", VkShaderStage.VERTEX_BIT)
            it[1].loadShader("$assetPath/shaders/dynamicuniformbuffer/base.frag.spv", VkShaderStage.FRAGMENT_BIT)
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

        pipeline = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    /** Prepare and initialize uniform buffer containing shader uniforms    */
    fun prepareUniformBuffers() {
        /*  Allocate data for the dynamic uniform buffer object
            We allocate this manually as the alignment of the offset differs between GPUs         */

        // Calculate required alignment based on minimum device offset alignment
        val minUboAlignment = vulkanDevice.properties.limits.minUniformBufferOffsetAlignment
        dynamicAlignment = Mat4.size.L
        if (minUboAlignment > 0)
            dynamicAlignment = (dynamicAlignment + minUboAlignment - 1) and (minUboAlignment - 1).inv()

        val bufferSize = OBJECT_INSTANCES * dynamicAlignment

        uboDataDynamic.model = bufferBig(bufferSize.i).apply { uboDataDynamic.address = adr }

        println("minUniformBufferOffsetAlignment = $minUboAlignment")
        println("dynamicAlignment = $dynamicAlignment")

        // Vertex shader uniform buffer block

        // Static shared uniform buffer object with projection and view matrix
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBuffers.view,
                uboVS.size.L)

        // Uniform buffer object with per-object matrices
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT.i,
                uniformBuffers.dynamic,
                bufferSize)

        // Map persistent
        uniformBuffers.view.map()
        uniformBuffers.dynamic.map()

        // Prepare per-object matrices with offsets and random rotations
        repeat(OBJECT_INSTANCES) {
            rotations[it] = Vec3 { glm.gaussRand(-1f, 1f) } * 2f * glm.PIf
            rotationSpeeds[it] = Vec3 { glm.gaussRand(-1f, 1f) }
        }

        updateUniformBuffers()
        updateDynamicUniformBuffer(true)
    }

    fun updateUniformBuffers() {
        // Fixed ubo with projection and view matrices
        uboVS.projection put camera.matrices.perspective
        uboVS.view put camera.matrices.view

        uboVS to uniformBuffers.view.mapped
    }

    fun updateDynamicUniformBuffer(force: Boolean = false) {
        // Update at max. 60 fps
        animationTimer += frameTimer
        if (animationTimer <= 1f / 60f && !force) return

        // Dynamic ubo with per-object model matrices indexed by offsets in the command buffer
        val dim = OBJECT_INSTANCES pow (1f / 3f)
        val offset = Vec3(5f)

        for (x in 0 until dim)
            for (y in 0 until dim)
                for (z in 0 until dim) {
                    val index = x * dim * dim + y * dim + z

                    // Update rotations
                    rotations[index] plusAssign animationTimer * rotationSpeeds[index]

                    // Update matrices
                    val pos = -((dim * offset) / 2f) + offset / 2f
                    pos.x += x * offset.x
                    pos.y += y * offset.y
                    pos.z += z * offset.z
                    val modelMat = glm.translate(Mat4(1f), pos)
                            .rotateAssign(rotations[index].x, 1f, 1f, 0f)
                            .rotateAssign(rotations[index].y, 0f, 1f, 0f)
                            .rotateAssign(rotations[index].z, 0f, 0f, 1f)
                    // Aligned offset
                    modelMat.to(uboDataDynamic.model, index * dynamicAlignment.i)
                }

        animationTimer = 0f

        memCopy(uboDataDynamic.address, uniformBuffers.dynamic.mapped, uniformBuffers.dynamic.size)
        // Flush to make changes visible to the host
        val memoryRange = vk.MappedMemoryRange {
            memory = uniformBuffers.dynamic.memory
            size = uniformBuffers.dynamic.size
        }
        device flushMappedMemoryRanges memoryRange
    }

    override fun prepare() {
        super.prepare()
        generateCube()
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
        if (!paused)
            updateDynamicUniformBuffer()
    }

    override fun viewChanged() = updateUniformBuffers()
}