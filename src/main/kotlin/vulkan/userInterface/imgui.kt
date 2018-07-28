/*
* Vulkan Example - imGui (https://github.com/ocornut/imgui)
*
* Copyright (C) 2017 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.userInterface

import glm_.BYTES
import glm_.L
import glm_.buffer.adr
import glm_.buffer.bufferBig
import glm_.func.cos
import glm_.func.rad
import glm_.func.sin
import glm_.i
import glm_.mat4x4.Mat4
import glm_.max
import glm_.vec2.Vec2
import glm_.vec2.Vec2i
import glm_.vec2.operators.div
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import glm_.vec4.Vec4b
import imgui.*
import imgui.ImGui.checkbox
import imgui.ImGui.inputVec3
import imgui.ImGui.plotLines
import imgui.ImGui.setNextWindowPos
import imgui.ImGui.setNextWindowSize
import imgui.ImGui.sliderFloat
import imgui.ImGui.text
import imgui.ImGui.textUnformatted
import imgui.functionalProgramming.withWindow
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkQueue
import vkk.*
import vulkan.assetPath
import vulkan.base.*
import vulkan.last


fun main(args: Array<String>) {
    Imgui().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

// Options and values to display/toggle from the UI
object uiSettings {
    var displayModels = true
    var displayLogos = true
    var displayBackground = true
    var animateLight = false
    var lightSpeed = 0.25f
    val frameTimes = FloatArray(50)
    var frameTimeMin = 9999f
    var frameTimeMax = 0f
    var lightTimer = 0f
}

// ----------------------------------------------------------------------------
// ImGUI class
// ----------------------------------------------------------------------------
class ImGUI(val example: VulkanExampleBase) {

    // Vulkan resources for rendering the UI
    var sampler: VkSampler = NULL
    val vertexBuffer = Buffer()
    val indexBuffer = Buffer()
    var vertexCount = 0
    var indexCount = 0
    var fontMemory: VkDeviceMemory = NULL
    var fontImage: VkImage = NULL
    var fontView: VkImageView = NULL
    var pipelineCache: VkPipelineCache = NULL
    var pipelineLayout: VkPipelineLayout = NULL
    var pipeline: VkPipeline = NULL
    var descriptorPool: VkDescriptorPool = NULL
    var descriptorSetLayout: VkDescriptorSetLayout = NULL
    var descriptorSet: VkDescriptorSet = NULL
    val device: VulkanDevice = example.vulkanDevice

    // UI params are set via push constants
    object pushConstBlock {
        val scale = Vec2()
        val translate = Vec2()
        fun pack() {
            scale to buffer
            translate.to(buffer, Vec2.size)
        }

        val size = Vec2.size * 2
        val buffer = bufferBig(size)
    }

    fun destroy() {
        // Release all Vulkan resources required for rendering imGui
        vertexBuffer.destroy()
        indexBuffer.destroy()
        device.logicalDevice!!.apply {
            destroyImage(fontImage)
            destroyImageView(fontView)
            freeMemory(fontMemory)
            destroySampler(sampler)
            destroyPipelineCache(pipelineCache)
            destroyPipeline(pipeline)
            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorPool(descriptorPool)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }
    }

    /** Initialize styles, keys, etc. */
    fun init(size: Vec2i) {
        // Color scheme
        ImGui.style.colors.also {
            it[Col.TitleBg] = Vec4(1f, 0f, 0f, 0.6f)
            it[Col.TitleBgActive] = Vec4(1f, 0f, 0f, 0.8f)
            it[Col.MenuBarBg] = Vec4(1f, 0f, 0f, 0.4f)
            it[Col.Header] = Vec4(1f, 0f, 0f, 0.4f)
            it[Col.CheckMark] = Vec4(0f, 1f, 0f, 1f)
        }
        // Dimensions
        ImGui.io.apply {
            displaySize put size
            displayFramebufferScale put 1f
        }
    }

    /** Initialize all Vulkan resources used by the ui */
    fun initResources(renderPass: VkRenderPass, copyQueue: VkQueue) {

        val io = ImGui.io

        // Create font texture
        val (fontData, texSize, _) = io.fonts.getTexDataAsRGBA32()
        val uploadSize: VkDeviceSize = texSize.x * texSize.y * Vec4b.size.L

        // Create target image for copy
        val imageInfo = vk.ImageCreateInfo {
            imageType = VkImageType.`2D`
            format = VkFormat.R8G8B8A8_UNORM
            extent(texSize, 1)
            mipLevels = 1
            arrayLayers = 1
            samples = VkSampleCount.`1_BIT`
            tiling = VkImageTiling.OPTIMAL
            usage = VkImageUsage.SAMPLED_BIT or VkImageUsage.TRANSFER_DST_BIT
            sharingMode = VkSharingMode.EXCLUSIVE
            initialLayout = VkImageLayout.UNDEFINED
        }
        val dev = device.logicalDevice!!
        fontImage = dev createImage imageInfo
        val memReqs = dev getImageMemoryRequirements fontImage
        val memAllocInfo = vk.MemoryAllocateInfo { }
        memAllocInfo.allocationSize = memReqs.size
        memAllocInfo.memoryTypeIndex = device.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
        fontMemory = dev allocateMemory memAllocInfo
        dev.bindImageMemory(fontImage, fontMemory)

        // Image view
        val viewInfo = vk.ImageViewCreateInfo {
            image = fontImage
            viewType = VkImageViewType.`2D`
            format = VkFormat.R8G8B8A8_UNORM
            subresourceRange.apply {
                aspectMask = VkImageAspect.COLOR_BIT.i
                levelCount = 1
                layerCount = 1
            }
        }
        fontView = dev createImageView viewInfo

        // Staging buffers for font data upload
        val stagingBuffer = Buffer()

        device.createBuffer(
                VkBufferUsage.TRANSFER_SRC_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                stagingBuffer,
                uploadSize)

        stagingBuffer.mapping {
            memCopy(fontData.adr, stagingBuffer.mapped, uploadSize)
        }
        // Copy buffer data to font image
        val copyCmd = device.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)

        // Prepare for transfer
        tools.setImageLayout(
                copyCmd,
                fontImage,
                VkImageAspect.COLOR_BIT.i,
                VkImageLayout.UNDEFINED,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                VkPipelineStage.HOST_BIT.i,
                VkPipelineStage.TRANSFER_BIT.i)

        // Copy
        val bufferCopyRegion = vk.BufferImageCopy {
            imageSubresource.apply {
                aspectMask = VkImageAspect.COLOR_BIT.i
                layerCount = 1
            }
            imageExtent(texSize, 1)
        }
        copyCmd.copyBufferToImage(
                stagingBuffer.buffer,
                fontImage,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                bufferCopyRegion)

        // Prepare for shader read
        tools.setImageLayout(
                copyCmd,
                fontImage,
                VkImageAspect.COLOR_BIT.i,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                VkImageLayout.SHADER_READ_ONLY_OPTIMAL,
                VkPipelineStage.TRANSFER_BIT.i,
                VkPipelineStage.FRAGMENT_SHADER_BIT.i)

        device.flushCommandBuffer(copyCmd, copyQueue, true)

        stagingBuffer.destroy()

        // Font texture Sampler
        val samplerInfo = vk.SamplerCreateInfo {
            magFilter = VkFilter.LINEAR
            minFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            addressMode = VkSamplerAddressMode.CLAMP_TO_EDGE
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
        }
        sampler = dev createSampler samplerInfo

        // Descriptor pool
        val poolSizes = vk.DescriptorPoolSize(VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1)

        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 2)
        descriptorPool = dev createDescriptorPool descriptorPoolInfo

        // Descriptor set layout
        val setLayoutBindings = vk.DescriptorSetLayoutBinding(VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 0)
        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
        descriptorSetLayout = dev createDescriptorSetLayout descriptorLayout

        // Descriptor set
        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)
        descriptorSet = dev allocateDescriptorSets allocInfo
        val fontDescriptor = vk.DescriptorImageInfo(
                sampler,
                fontView,
                VkImageLayout.SHADER_READ_ONLY_OPTIMAL)
        val writeDescriptorSets = vk.WriteDescriptorSet(descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 0, fontDescriptor)
        dev.updateDescriptorSets(writeDescriptorSets)

        // Pipeline cache
        pipelineCache = dev createPipelineCache vk.PipelineCacheCreateInfo()

        // Pipeline layout
        // Push constants for UI rendering parameters
        val pushConstantRange = vk.PushConstantRange(VkShaderStage.VERTEX_BIT, pushConstBlock.size)
        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout).also {
            it.pushConstantRange = pushConstantRange
        }
        pipelineLayout = dev createPipelineLayout pipelineLayoutCreateInfo

        // Setup graphics pipeline for UI rendering
        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.NONE.i, VkFrontFace.COUNTER_CLOCKWISE)

        // Enable blending
        val blendAttachmentState = vk.PipelineColorBlendAttachmentState {
            blendEnable = true
            colorWriteMask = VkColorComponent.R_BIT or VkColorComponent.G_BIT or VkColorComponent.B_BIT.i or VkColorComponent.A_BIT.i
            srcColorBlendFactor = VkBlendFactor.SRC_ALPHA
            dstColorBlendFactor = VkBlendFactor.ONE_MINUS_SRC_ALPHA
            colorBlendOp = VkBlendOp.ADD
            srcAlphaBlendFactor = VkBlendFactor.ONE_MINUS_SRC_ALPHA
            dstAlphaBlendFactor = VkBlendFactor.ZERO
            alphaBlendOp = VkBlendOp.ADD
        }
        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(false, false, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        val shaderStages = vk.PipelineShaderStageCreateInfo(2)

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
        }
        // Vertex bindings an attributes based on ImGui vertex definition
        val vertexInputBindings = vk.VertexInputBindingDescription(0, DrawVert.size, VkVertexInputRate.VERTEX)

        val vertexInputAttributes = vk.VertexInputAttributeDescription(
                0, 0, VkFormat.R32G32_SFLOAT, 0,    // Location 0: Position
                0, 1, VkFormat.R32G32_SFLOAT, Vec2.size,    // Location 1: UV
                0, 2, VkFormat.R8G8B8A8_UNORM, Vec2.size * 2)    // Location 0: Color
        val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertexInputBindings
            vertexAttributeDescriptions = vertexInputAttributes
        }
        pipelineCreateInfo.vertexInputState = vertexInputState

        example.apply {
            shaderStages[0].loadShader("$assetPath/shaders/imgui/ui.vert.spv", VkShaderStage.VERTEX_BIT)
            shaderStages[1].loadShader("$assetPath/shaders/imgui/ui.frag.spv", VkShaderStage.FRAGMENT_BIT)
        }
        pipeline = dev.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    var f = 0f

    /** Starts a new imGui frame and sets up windows and ui elements */
    fun newFrame(updateFrameGraph: Boolean) {

        ImGui.newFrame()

        // Init imGui windows and elements

        val clearColor = Color(114, 144, 154)
        textUnformatted(example.title)
        textUnformatted(device.properties.deviceName)

        // Update frame time display
        if (updateFrameGraph) {
            for (i in uiSettings.frameTimes.indices)
                uiSettings.frameTimes[i] = uiSettings.frameTimes.getOrElse(i) { uiSettings.frameTimes.last() }
            val frameTime = 1000f / (example.frameTimer * 1000f)
            uiSettings.frameTimes.last(frameTime)
            if (frameTime < uiSettings.frameTimeMin)
                uiSettings.frameTimeMin = frameTime
            if (frameTime > uiSettings.frameTimeMax)
                uiSettings.frameTimeMax = frameTime
        }

        plotLines("Frame Times", uiSettings.frameTimes, 0, "", uiSettings.frameTimeMin, uiSettings.frameTimeMax, Vec2(0, 80))

        text("Camera")
        inputVec3("position", example.camera.position, "%f")
        inputVec3("rotation", example.camera.rotation, "%f")

        setNextWindowSize(Vec2(200), Cond.FirstUseEver)
        withWindow("Example settings") {
            checkbox("Render models", uiSettings::displayModels)
            checkbox("Display logos", uiSettings::displayLogos)
            checkbox("Display background", uiSettings::displayBackground)
            checkbox("Animate light", uiSettings::animateLight)
            sliderFloat("Light speed", uiSettings::lightSpeed, 0.1f, 1f)
        }

        setNextWindowPos(Vec2(650, 20), Cond.FirstUseEver)
//        showTestWindow()

        // Render to generate draw buffers
        ImGui.render()
    }

    /** Update vertex and index buffer containing the imGui elements when required */
    fun updateBuffers() {

        val drawData = ImGui.drawData!!

        // Note: Alignment is done inside buffer creation
        val vertexBufferSize: VkDeviceSize = drawData.totalVtxCount * DrawVert.size.L
        val indexBufferSize: VkDeviceSize = drawData.totalIdxCount * DrawIdx.BYTES.L

        // Update buffers only if vertex or index count has been changed compared to current buffer size

        // Vertex buffer
        if (vertexBuffer.buffer == NULL || vertexCount != drawData.totalVtxCount) {
            vertexBuffer.unmap()
            vertexBuffer.destroy()
            device.createBuffer(VkBufferUsage.VERTEX_BUFFER_BIT.i, VkMemoryProperty.HOST_VISIBLE_BIT.i, vertexBuffer, vertexBufferSize)
            vertexCount = drawData.totalVtxCount
            vertexBuffer.unmap()
            vertexBuffer.map()
        }

        // Index buffer
        val indexSize: VkDeviceSize = drawData.totalIdxCount * DrawIdx.BYTES.L
        if (indexBuffer.buffer == NULL || indexCount < drawData.totalIdxCount) {
            indexBuffer.unmap()
            indexBuffer.destroy()
            device.createBuffer(VkBufferUsage.INDEX_BUFFER_BIT.i, VkMemoryProperty.HOST_VISIBLE_BIT.i, indexBuffer, indexBufferSize)
            indexCount = drawData.totalIdxCount
            indexBuffer.map()
        }

        // Upload data
        var vtxDst = vertexBuffer.mapped
        var idxDst = indexBuffer.mapped

        for (cmdList in drawData.cmdLists) {
            var k = 0
            for (v in cmdList.vtxBuffer) {
                val ofs = vtxDst + DrawVert.size * k++
                memPutFloat(ofs, v.pos.x)
                memPutFloat(ofs + Float.BYTES, v.pos.y)
                memPutFloat(ofs + Vec2.size, v.uv.x)
                memPutFloat(ofs + Vec2.size + Float.BYTES, v.uv.y)
                memPutInt(ofs + Vec2.size * 2, v.col)
            }
            k = 0
            for (i in cmdList.idxBuffer)
                memPutInt(idxDst + DrawIdx.BYTES * k++, i)
            vtxDst += cmdList.vtxBuffer.size
            idxDst += cmdList.idxBuffer.size
        }

        // Flush to make writes visible to GPU
        vertexBuffer.flush()
        indexBuffer.flush()
    }

    /** Draw current imGui frame into a command buffer */
    fun drawFrame(commandBuffer: VkCommandBuffer) {

        val io = ImGui.io

        commandBuffer.apply {
            bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
            bindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)

            // Bind vertex and index buffer
            bindVertexBuffers(vertexBuffer.buffer)
            bindIndexBuffer(indexBuffer.buffer, 0, VkIndexType.UINT16)

            setViewport(vk.Viewport(io.displaySize))

            // UI scale and translate via push constants
            pushConstBlock.scale put 2f / io.displaySize
            pushConstBlock.translate put -1f
            pushConstBlock.pack()
            pushConstants(pipelineLayout, VkShaderStage.VERTEX_BIT.i, pushConstBlock.size, pushConstBlock.buffer)

            // Render commands
            val drawData = ImGui.drawData!!
            var vertexOffset = 0
            var indexOffset = 0
            for (cmdList in drawData.cmdLists) {
                for (cmd in cmdList.cmdBuffer) {
                    val scissorRect = vk.Rect2D(
                            offsetX = cmd.clipRect.x.i max 0,
                            offsetY = cmd.clipRect.y.i max 0,
                            width = (cmd.clipRect.z - cmd.clipRect.x).i,
                            height = (cmd.clipRect.w - cmd.clipRect.y).i)
                    setScissor(scissorRect)
                    drawIndexed(cmd.elemCount, 1, indexOffset, vertexOffset, 0)
                    indexOffset += cmd.elemCount
                }
                vertexOffset += cmdList.vtxBuffer.size
            }
        }
    }

}

// ----------------------------------------------------------------------------
// VulkanExample
// ----------------------------------------------------------------------------

private class Imgui : VulkanExampleBase() {

    lateinit var imGui: ImGUI

    // Vertex layout for the models
    val vertexLayout = VertexLayout(
            VertexComponent.POSITION,
            VertexComponent.NORMAL,
            VertexComponent.COLOR)

    object models {
        val models = Model()
        val logos = Model()
        val background = Model()
    }

    val uniformBufferVS = Buffer()

    object uboVS {
        var projection = Mat4()
        var modelview = Mat4()
        var lightPos = Vec4()
        fun pack() {
            projection to buffer
            modelview.to(buffer, Mat4.size)
            lightPos.to(buffer, Mat4.size * 2)
        }

        val size = Mat4.size * 2 + Vec4.size
        val buffer = bufferBig(size)
    }

    var pipelineLayout: VkPipelineLayout = NULL
    var pipeline: VkPipeline = NULL
    var descriptorSetLayout: VkDescriptorSetLayout = NULL
    var descriptorSet: VkDescriptorSet = NULL

    init {
        title = "Vulkan Example - ImGui"
        camera.type = Camera.CameraType.lookAt
        camera.setPosition(Vec3(0f, 1.4f, -4.8f))
        camera.setRotation(Vec3(4.5f, -380f, 0f))
        camera.setPerspective(45f, size.aspect, 0.1f, 256f)
    }

    override fun destroy() {
        super.destroy()

        device.apply {
            destroyPipeline(pipeline)
            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }
        models.apply {
            models.destroy()
            background.destroy()
            logos.destroy()
        }
        uniformBufferVS.destroy()

        imGui.destroy()
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2).also {
            it[0].color(0.2f, 0.2f, 0.2f, 1f)
            it[1].depthStencil(1f, 0)
        }
        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@Imgui.renderPass
            renderArea.apply {
                offset(0)
                extent(size)
            }
            this.clearValues = clearValues
        }
        imGui.newFrame(frameCounter == 0)

        imGui.updateBuffers()

        for (i in drawCmdBuffers.indices) {
            // Set target frame buffer
            renderPassBeginInfo.framebuffer = frameBuffers[i]

            drawCmdBuffers[i].apply {

                begin(cmdBufInfo)

                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                setViewport(vk.Viewport(size))

                setScissor(vk.Rect2D(size))

                // Render scene
                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)

                if (uiSettings.displayBackground) {
                    bindVertexBuffers(models.background.vertices.buffer)
                    bindIndexBuffer(models.background.indices.buffer, 0, VkIndexType.UINT32)
                    drawIndexed(models.background.indexCount, 1, 0, 0, 0)
                }

                if (uiSettings.displayModels) {
                    bindVertexBuffers(models.models.vertices.buffer)
                    bindIndexBuffer(models.models.indices.buffer, 0, VkIndexType.UINT32)
                    drawIndexed(models.models.indexCount, 1, 0, 0, 0)
                }

                if (uiSettings.displayLogos) {
                    bindVertexBuffers(models.logos.vertices.buffer)
                    bindIndexBuffer(models.logos.indices.buffer, 0, VkIndexType.UINT32)
                    drawIndexed(models.logos.indexCount, 1, 0, 0, 0)
                }

                // Render imGui
                imGui.drawFrame(this)

                endRenderPass()

                end()
            }
        }
    }

    fun setupLayoutsAndDescriptors() {
        // descriptor pool
        val poolSizes = vk.DescriptorPoolSize(
                VkDescriptorType.UNIFORM_BUFFER, 2,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1)
        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 2)
        descriptorPool = device createDescriptorPool descriptorPoolInfo

        // Set layout
        val setLayoutBindings = vk.DescriptorSetLayoutBinding(VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0)
        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        // Pipeline layout
        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)
        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo

        // Descriptor set
        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)
        descriptorSet = device allocateDescriptorSets allocInfo
        val writeDescriptorSets = vk.WriteDescriptorSet(descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBufferVS.descriptor)

        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() {
        // Rendering
        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.FRONT_BIT.i, VkFrontFace.COUNTER_CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Load shaders
        val shaderStages = vk.PipelineShaderStageCreateInfo(2)

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
        }
        val vertexInputBindings = vk.VertexInputBindingDescription(0, vertexLayout.stride, VkVertexInputRate.VERTEX)

        val vertexInputAttributes = vk.VertexInputAttributeDescription(
                0, 0, VkFormat.R32G32B32_SFLOAT, 0,                    // Location 0: Position
                0, 1, VkFormat.R32G32B32_SFLOAT, Vec3.size,    // Location 1: Normal
                0, 2, VkFormat.R32G32B32_SFLOAT, Vec3.size * 2)    // Location 2: Color

        val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertexInputBindings
            vertexAttributeDescriptions = vertexInputAttributes
        }
        pipelineCreateInfo.vertexInputState = vertexInputState

        shaderStages[0].loadShader("$assetPath/shaders/imgui/scene.vert.spv", VkShaderStage.VERTEX_BIT)
        shaderStages[1].loadShader("$assetPath/shaders/imgui/scene.frag.spv", VkShaderStage.FRAGMENT_BIT)
        pipeline = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    /** Prepare and initialize uniform buffer containing shader uniforms */
    fun prepareUniformBuffers() {
        // Vertex shader uniform buffer block
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBufferVS,
                uboVS.size.L,
                uboVS.buffer.adr)

        updateUniformBuffers()
    }

    fun updateUniformBuffers() {
        // Vertex shader
        uboVS.projection = camera.matrices.perspective
        uboVS.modelview = camera.matrices.view

        // Light source
        if (uiSettings.animateLight) {
            uiSettings.lightTimer += frameTimer * uiSettings.lightSpeed
            uboVS.lightPos.x = (uiSettings.lightTimer * 360f).rad.sin * 15f
            uboVS.lightPos.z = (uiSettings.lightTimer * 360f).rad.cos * 15f
        }

        uniformBufferVS.mapping {
            memCopy(uboVS.buffer.adr, uniformBufferVS.mapped, uboVS.size.L)
        }
    }

    fun draw() {
        super.prepareFrame()
        buildCommandBuffers()
        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]
        queue submit submitInfo
        super.submitFrame()
    }

    fun loadAssets() {
        models.models.loadFromFile("$assetPath/models/vulkanscenemodels.dae", vertexLayout, 1f, vulkanDevice, queue)
        models.background.loadFromFile("$assetPath/models/vulkanscenebackground.dae", vertexLayout, 1f, vulkanDevice, queue)
        models.logos.loadFromFile("$assetPath/models/vulkanscenelogos.dae", vertexLayout, 1f, vulkanDevice, queue)
    }

    fun prepareImGui() {
        imGui = ImGUI(this)
        imGui.init(size)
        imGui.initResources(renderPass, queue)
    }

    override fun prepare() {
        super.prepare()
        loadAssets()
        prepareUniformBuffers()
        setupLayoutsAndDescriptors()
        preparePipelines()
        prepareImGui()
        buildCommandBuffers()
        prepared = true
    }

    override fun render() {

        if (!prepared) return

        // Update imGui
        ImGui.io.apply {
            displaySize(size)
            deltaTime = frameTimer

            mousePos(this@Imgui.mousePos)
            mouseDown[0] = window.mouseButton(GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS
            mouseDown[0] = window.mouseButton(GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS
        }
        draw()

        if (uiSettings.animateLight)
            updateUniformBuffers()
    }

    override fun viewChanged() = updateUniformBuffers()

//    override fun mouseMoved(double x, double y, bool & handled)    {
//        ImGuiIO& io = ImGui::GetIO()
//        handled = io.WantCaptureMouse
//    }
}