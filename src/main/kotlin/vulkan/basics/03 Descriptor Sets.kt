/*
* Vulkan Example - Using descriptor sets for passing data to shader stages
*
* Relevant code parts are marked with [POI]
*
* Copyright (C) 2018 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.basics

import glm_.L
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import org.lwjgl.system.MemoryUtil.NULL
import vkk.*
import vulkan.assetPath
import vulkan.base.*

fun main(args: Array<String>) {
    DescriptorSets().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class DescriptorSets : VulkanExampleBase() {

    var animate = true

    val vertexLayout = VertexLayout(
            VertexComponent.POSITION,
            VertexComponent.NORMAL,
            VertexComponent.UV,
            VertexComponent.COLOR)

    class Cube {

        class Matrices : Bufferizable() {
            var projection = Mat4()
            var view = Mat4()
            var model = Mat4()

            override var fieldOrder = arrayOf("projection", "view", "model")
        }

        val matrices = Matrices()
        var descriptorSet: VkDescriptorSet = NULL
        val texture = Texture2D()
        val uniformBuffer = Buffer()
        val rotation = Vec3()
    }

    val cubes = Array(2) { Cube() }

    object models {
        val cube = Model()
    }

    var pipeline: VkPipeline = NULL
    var pipelineLayout: VkPipelineLayout = NULL

    var descriptorSetLayout: VkDescriptorSetLayout = NULL

    init {
        title = "Using descriptor Sets"
//        settings.overlay = true TODO
        camera.type = Camera.CameraType.lookAt
        camera.setPerspective(60f, size.aspect, 0.1f, 512f)
        camera.setRotation(Vec3())
        camera.setTranslation(Vec3(0f, 0f, -5f))
    }

    override fun destroy() {
        device.apply {
            destroyPipeline(pipeline)
            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }
        models.cube.destroy()
        for (cube in cubes) {
            cube.uniformBuffer.destroy()
            cube.texture.destroy()
        }
        super.destroy()
    }

    override fun getEnabledFeatures() {
        if (deviceFeatures.samplerAnisotropy)
            enabledFeatures.samplerAnisotropy = true
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2).also {
            it[0].color(defaultClearColor)
            it[1].depthStencil.set(1f, 0)
        }
        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@DescriptorSets.renderPass
            renderArea.offset.set(0, 0)
            renderArea.extent.set(size.x, size.y)
            this.clearValues = clearValues
        }
        for (i in drawCmdBuffers.indices) {

            renderPassBeginInfo.framebuffer(frameBuffers[i]) // TODO BUG

            drawCmdBuffers[i].apply {

                begin(cmdBufInfo)

                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)

                setViewport(size)
                setScissor(size)

                bindVertexBuffers(0, models.cube.vertices.buffer)
                bindIndexBuffer(models.cube.indices.buffer, 0, VkIndexType.UINT32)

                // [POI] Render cubes with separate descriptor sets
                for (cube in cubes) {
                    // Bind the cube's descriptor set. This tells the command buffer to use the uniform buffer and image set for this cube
                    bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, cube.descriptorSet)
                    drawIndexed(models.cube.indexCount, 1, 0, 0, 0)
                }

                endRenderPass()

                end()
            }
        }
    }

    fun loadAssets() {
        models.cube.loadFromFile("$assetPath/models/cube.dae", vertexLayout, 1f, vulkanDevice, queue)
        cubes[0].texture.loadFromFile("$assetPath/textures/crate01_color_height_rgba.ktx", VkFormat.R8G8B8A8_UNORM, vulkanDevice, queue)
        cubes[1].texture.loadFromFile("$assetPath/textures/crate02_color_height_rgba.ktx", VkFormat.R8G8B8A8_UNORM, vulkanDevice, queue)
    }

    /** [POI] Set up descriptor sets and set layout */
    fun setupDescriptors() {

        /*
            Descriptor set layout

            The layout describes the shader bindings and types used for a certain descriptor layout and as such
            must match the shader bindings

            Shader bindings used in this example:

            VS:
                layout (set = 0, binding = 0) uniform UBOMatrices ...

            FS :
                layout (set = 0, binding = 1) uniform sampler2D ...;
        */

        val setLayoutBindings = vk.DescriptorSetLayoutBinding(2).also {
            /*
                Binding 0: Uniform buffers (used to pass matrices matrices)
            */
            it[0].apply {
                descriptorType = VkDescriptorType.UNIFORM_BUFFER
                // Shader binding point
                binding = 0
                // Accessible from the vertex shader only (flags can be combined to make it accessible to multiple shader stages)
                stageFlags = VkShaderStage.VERTEX_BIT.i
                // Binding contains one element (can be used for array bindings)
                descriptorCount = 1
            }
            /*
                Binding 1: Combined image sampler (used to pass per object texture information)
            */
            it[1].apply {
                descriptorType = VkDescriptorType.COMBINED_IMAGE_SAMPLER
                binding = 1
                // Accessible from the fragment shader only
                stageFlags = VkShaderStage.FRAGMENT_BIT.i
                descriptorCount = 1
            }
        }
        // Create the descriptor set layout
        val descriptorLayoutCI = vk.DescriptorSetLayoutCreateInfo { bindings = setLayoutBindings }
        descriptorSetLayout = device createDescriptorSetLayout descriptorLayoutCI

        /*
            Descriptor pool

            Actual descriptors are allocated from a descriptor pool telling the driver what types and how many
            descriptors this application will use

            An application can have multiple pools (e.g. for multiple threads) with any number of descriptor types
            as long as device limits are not surpassed

            It's good practice to allocate pools with actually required descriptor types and counts
        */

        val descriptorPoolSizes = vk.DescriptorPoolSize(2).also {
            // Uniform buffers : 1 for scene and 1 per object (scene and local matrices)
            it[0].type = VkDescriptorType.UNIFORM_BUFFER
            it[0].descriptorCount = 1 + cubes.size

            // Combined image samples : 1 per mesh texture
            it[1].type = VkDescriptorType.COMBINED_IMAGE_SAMPLER
            it[1].descriptorCount = cubes.size
        }
        // Create the global descriptor pool
        val descriptorPoolCI = vk.DescriptorPoolCreateInfo {
            poolSizes = descriptorPoolSizes
            // Max. number of descriptor sets that can be allocted from this pool (one per object)
            maxSets = descriptorPoolSizes.capacity()
        }
        descriptorPool = device createDescriptorPool descriptorPoolCI

        /*
            Descriptor sets

            Using the shared descriptor set layout and the descriptor pool we will now allocate the descriptor sets.

            Descriptor sets contain the actual descriptor fo the objects (buffers, images) used at render time.
        */

        for (cube in cubes) {

            // Allocates an empty descriptor set without actual descriptors from the pool using the set layout
            val allocateInfo = vk.DescriptorSetAllocateInfo {
                descriptorPool = this@DescriptorSets.descriptorPool
                descriptorSetCount = 1
                setLayout = descriptorSetLayout
            }
            cube.descriptorSet = device allocateDescriptorSets allocateInfo

            // Update the descriptor set with the actual descriptors matching shader bindings set in the layout

            val writeDescriptorSets = vk.WriteDescriptorSet(2).also {
                /*
                    Binding 0: Object matrices Uniform buffer
                */
                it[0].apply {
                    dstSet = cube.descriptorSet
                    dstBinding = 0
                    descriptorType = VkDescriptorType.UNIFORM_BUFFER
                    bufferInfo_ = cube.uniformBuffer.descriptor
                }
                /*
                    Binding 1: Object texture
                */
                it[1].apply {
                    dstSet = cube.descriptorSet
                    dstBinding = 1
                    descriptorType = VkDescriptorType.COMBINED_IMAGE_SAMPLER
                    // Images use a different descriptor strucutre, so we use pImageInfo instead of pBufferInfo
                    imageInfo_ = cube.texture.descriptor
                }
            }
            /*  Execute the writes to update descriptors for this set
                Note that it's also possible to gather all writes and only run updates once, even for multiple sets
                This is possible because each VkWriteDescriptorSet also contains the destination set to be updated
                For simplicity we will update once per set instead */
            device updateDescriptorSets writeDescriptorSets
        }
    }

    fun preparePipelines() {
        /*
            [POI] Create a pipeline layout used for our graphics pipeline
        */
        val pipelineLayoutCI = vk.PipelineLayoutCreateInfo {
            // The pipeline layout is based on the descriptor set layout we created above
            setLayout = descriptorSetLayout
        }
        pipelineLayout = device createPipelineLayout pipelineLayoutCI

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)

        val inputAssemblyStateCI = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)
        val rasterizationStateCI = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.BACK_BIT.i, VkFrontFace.CLOCKWISE)
        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)
        val colorBlendStateCI = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)
        val depthStencilStateCI = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)
        val viewportStateCI = vk.PipelineViewportStateCreateInfo(1, 1, 0)
        val multisampleStateCI = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)
        val dynamicStateCI = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Vertex bindings and attributes
        val vertexInputBinding = vk.VertexInputBindingDescription(0, vertexLayout.stride, VkVertexInputRate.VERTEX)

        val vertexInputAttributes = vk.VertexInputAttributeDescription(
                0, 0, VkFormat.R32G32B32_SFLOAT, 0,                         // Location 0: Position
                0, 1, VkFormat.R32G32B32_SFLOAT, Vec3.size,                         // Location 1: Normal
                0, 2, VkFormat.R32G32_SFLOAT, Vec3.size * 2,                // Location 2: UV
                0, 3, VkFormat.R32G32B32_SFLOAT, Vec3.size * 2 + Vec2.size) // Location 3: Color

        val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertexInputBinding
            vertexAttributeDescriptions = vertexInputAttributes
        }
        val pipelineCreateInfoCI = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {

            it.vertexInputState = vertexInputState
            it.inputAssemblyState = inputAssemblyStateCI
            it.rasterizationState = rasterizationStateCI
            it.colorBlendState = colorBlendStateCI
            it.multisampleState = multisampleStateCI
            it.viewportState = viewportStateCI
            it.depthStencilState = depthStencilStateCI
            it.dynamicState = dynamicStateCI

            it.stages = vk.PipelineShaderStageCreateInfo(2).also {
                it[0].loadShader("$assetPath/shaders/descriptorsets/cube.vert.spv", VkShaderStage.VERTEX_BIT)
                it[1].loadShader("$assetPath/shaders/descriptorsets/cube.frag.spv", VkShaderStage.FRAGMENT_BIT)
            }
        }

        pipeline = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfoCI)
    }

    fun prepareUniformBuffers() {
        // Vertex shader matrix uniform buffer block
        for (cube in cubes) {
            vulkanDevice.createBuffer(
                    VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                    VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                    cube.uniformBuffer,
                    Mat4.size.L)
            cube.uniformBuffer.map()
        }

        updateUniformBuffers()
    }

    fun updateUniformBuffers() {

        cubes[0].matrices.model = glm.translate(Mat4(1f), -2f, 0f, 0f)
        cubes[1].matrices.model = glm.translate(Mat4(1f), 1.5f, 0.5f, 0f)

        for (cube in cubes) {
            cube.matrices.apply {
                projection put camera.matrices.perspective
                view put camera.matrices.view
                model
                        .rotateAssign(cube.rotation.x.rad, 1f, 0f, 0f)
                        .rotateAssign(cube.rotation.y.rad, 0f, 1f, 0f)
                        .rotateAssign(cube.rotation.z.rad, 0f, 0f, 1f)
            }
            cube.matrices to cube.uniformBuffer.mapped[0]
        }
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
        prepareUniformBuffers()
        setupDescriptors()
        preparePipelines()
        buildCommandBuffers()
        prepared = true
        window.show()
    }

    override fun render()    {
        if (!prepared)
            return
        draw()
        if (animate) {
            cubes[0].rotation.x += 2.5f * frameTimer
            if (cubes[0].rotation.x > 360f)
                cubes[0].rotation.x -= 360f
            cubes[1].rotation.y += 2f * frameTimer
            if (cubes[1].rotation.x > 360f)
                cubes[1].rotation.x -= 360f
        }
        if (camera.updated || animate)
            updateUniformBuffers()
    }

//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
//    {
//        if (overlay->header("Settings")) { overlay ->
//        checkBox("Animate", & animate)
//    }
//    }
}