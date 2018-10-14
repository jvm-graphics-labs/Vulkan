package vulkan.base

import glm_.*
import glm_.vec2.Vec2
import glm_.vec2.Vec2i
import glm_.vec2.operators.div
import glm_.vec4.Vec4
import imgui.*
import kool.adr
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memPutInt
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkQueue
import vkk.*
import vulkan.to
import java.nio.ByteBuffer
import kotlin.reflect.KMutableProperty0

// TODO redo completely
class UIOverlayCreateInfo {

    var device: VulkanDevice? = null
    var copyQueue: VkQueue? = null
//    var renderPass: VkRenderPass
//    std::vector<VkFramebuffer> framebuffers;
//    VkFormat colorformat;
//    VkFormat depthformat;
//    uint32_t width;
//    uint32_t height;
//    std::vector<VkPipelineShaderStageCreateInfo> shaders;
//    VkSampleCountFlagBits rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
//    uint32_t subpassCount = 1;
//    std::vector<VkClearValue> clearValues = {};
//    uint32_t attachmentCount = 1;
}

class UIOverlay {

    lateinit var device: VulkanDevice
    lateinit var queue: VkQueue

    // TODO flag
    var rasterizationSamples: VkSampleCount = VkSampleCount.`1_BIT`
    var subpass = 0

    val vertexBuffer = Buffer()
    val indexBuffer = Buffer()
    var vertexCount = 0
    var indexCount = 0

    val shaders = VkPipelineShaderStageCreateInfo.calloc(2)

    var descriptorPool = VkDescriptorPool(NULL)
    var descriptorSetLayout = VkDescriptorSetLayout(NULL)
    var descriptorSet = VkDescriptorSet(NULL)
    var pipelineLayout = VkPipelineLayout(NULL)
    var pipeline = VkPipeline(NULL)

    var fontMemory = VkDeviceMemory(NULL)
    var fontImage = VkImage(NULL)
    var fontView = VkImageView(NULL)
    var sampler = VkSampler(NULL)

    private object pushConstBlock : Bufferizable() {
        var scale = Vec2()
        var translate = Vec2()

        fun toBuf(): ByteBuffer {
            val buf = stackGet().malloc(size)
            scale to buf
            translate.to(buf, Vec2.size)
            return buf
        }
    }

    var visible = true
    var updated = false
    var scale = 1f

    // Init ImGui
    val context = Context()
    // Color scheme
    val style = ImGui.style.colors.also {
        it[Col.TitleBg] = Vec4(1f, 0f, 0f, 1f)
        it[Col.TitleBgActive] = Vec4(1f, 0f, 0f, 1f)
        it[Col.TitleBgCollapsed] = Vec4(1f, 0f, 0f, 0.1f)
        it[Col.MenuBarBg] = Vec4(1f, 0f, 0f, 0.4f)
        it[Col.Header] = Vec4(0.8f, 0f, 0f, 0.4f)
        it[Col.HeaderActive] = Vec4(1f, 0f, 0f, 0.4f)
        it[Col.HeaderHovered] = Vec4(1f, 0f, 0f, 0.4f)
        it[Col.FrameBg] = Vec4(0f, 0f, 0f, 0.8f)
        it[Col.CheckMark] = Vec4(1f, 0f, 0f, 0.8f)
        it[Col.SliderGrab] = Vec4(1f, 0f, 0f, 0.4f)
        it[Col.SliderGrabActive] = Vec4(1f, 0f, 0f, 0.8f)
        it[Col.FrameBgHovered] = Vec4(1f, 1f, 1f, 0.1f)
        it[Col.FrameBgActive] = Vec4(1f, 1f, 1f, 0.2f)
        it[Col.Button] = Vec4(1f, 0f, 0f, 0.4f)
        it[Col.ButtonHovered] = Vec4(1f, 0f, 0f, 0.6f)
        it[Col.ButtonActive] = Vec4(1f, 0f, 0f, 0.8f)
    }
    // Dimensions
    val io = ImGui.io.apply {
        fontGlobalScale = scale
    }

    val dev get() = device.logicalDevice!!

    fun destroy() = context.destroy()

    /** Prepare a separate pipeline for the UI overlay rendering decoupled from the main application */
    fun preparePipeline(pipelineCache: VkPipelineCache, renderPass: VkRenderPass) {

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
            colorWriteMask = VkColorComponent.R_BIT or VkColorComponent.G_BIT or VkColorComponent.B_BIT or VkColorComponent.A_BIT
            srcColorBlendFactor = VkBlendFactor.SRC_ALPHA
            dstColorBlendFactor = VkBlendFactor.ONE_MINUS_SRC_ALPHA
            colorBlendOp = VkBlendOp.ADD
            srcAlphaBlendFactor = VkBlendFactor.ONE_MINUS_SRC_ALPHA
            dstAlphaBlendFactor = VkBlendFactor.ZERO
            alphaBlendOp = VkBlendOp.ADD
        }
        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(false, false, VkCompareOp.ALWAYS)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1, 0)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(rasterizationSamples)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaders
            it.subpass = subpass
        }
        // Vertex bindings an attributes based on ImGui vertex definition
        val vertexInputBindings = vk.VertexInputBindingDescription(0, DrawVert.size, VkVertexInputRate.VERTEX)

        val vertexInputAttributes = vk.VertexInputAttributeDescription(
                0, 0, VkFormat.R32G32_SFLOAT, DrawVert.ofsPos,  // Location 0: Position
                0, 1, VkFormat.R32G32_SFLOAT, DrawVert.ofsUv,   // Location 1: UV
                0, 2, VkFormat.R8G8B8A8_UNORM, DrawVert.ofsCol) // Location 0: Color

        val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertexInputBindings
            vertexAttributeDescriptions = vertexInputAttributes
        }
        pipelineCreateInfo.vertexInputState = vertexInputState

        pipeline = dev.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    /** Prepare all vulkan resources required to render the UI overlay */
    fun prepareResources() {

        // Create font texture
        val (fontData, texSize, bytePerPixel) = io.fonts.getTexDataAsRGBA32()
        val uploadSize = VkDeviceSize(texSize.x * texSize.y * bytePerPixel.L)

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
        fontImage = dev createImage imageInfo
        val memReqs = dev getImageMemoryRequirements fontImage
        val memAllocInfo = vk.MemoryAllocateInfo {
            allocationSize = memReqs.size
            memoryTypeIndex = device.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
        }
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
                VkDeviceSize(uploadSize.L))

        stagingBuffer.mapping { pData ->
            memCopy(fontData.adr, pData, uploadSize)
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

        device.flushCommandBuffer(copyCmd, queue, true)

        stagingBuffer.destroy()

        // Font texture Sampler
        val samplerInfo = vk.SamplerCreateInfo {
            minMagFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            addressModeUVW = VkSamplerAddressMode.CLAMP_TO_EDGE
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
        val fontDescriptor = vk.DescriptorImageInfo(sampler, fontView, VkImageLayout.SHADER_READ_ONLY_OPTIMAL)
        val writeDescriptorSets = vk.WriteDescriptorSet(descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 0, fontDescriptor)
        dev.updateDescriptorSets(writeDescriptorSets)
    }

    /** Update vertex and index buffer containing the imGui elements when required */
    fun update(): Boolean {

        val drawData = ImGui.drawData ?: return false
        var updateCmdBuffers = false

        // Note: Alignment is done inside buffer creation
        val vertexBufferSize = VkDeviceSize(drawData.totalVtxCount * DrawVert.size.L)
        val indexBufferSize = VkDeviceSize(drawData.totalIdxCount * DrawIdx.BYTES.L)

        // Update buffers only if vertex or index count has been changed compared to current buffer size
        if (vertexBufferSize.isEmpty || indexBufferSize.isEmpty)
            return false

        // Vertex buffer
        if (vertexBuffer.buffer.L == NULL || vertexCount != drawData.totalVtxCount) {
            vertexBuffer.unmap()
            vertexBuffer.destroy()
            device.createBuffer(VkBufferUsage.VERTEX_BUFFER_BIT.i, VkMemoryProperty.HOST_VISIBLE_BIT.i, vertexBuffer, vertexBufferSize)
            vertexCount = drawData.totalVtxCount
            vertexBuffer.unmap()
            vertexBuffer.map()
            updateCmdBuffers = true
        }

        // Index buffer
        val indexSize = VkDeviceSize(drawData.totalIdxCount * DrawIdx.BYTES.L)
        if (indexBuffer.buffer.L == NULL || indexCount < drawData.totalIdxCount) {
            indexBuffer.unmap()
            indexBuffer.destroy()
            device.createBuffer(VkBufferUsage.INDEX_BUFFER_BIT.i, VkMemoryProperty.HOST_VISIBLE_BIT.i, indexBuffer, indexBufferSize)
            indexCount = drawData.totalIdxCount
            indexBuffer.map()
            updateCmdBuffers = true
        }

        // Upload data
        var vtxDst = vertexBuffer.mapped
        var idxDst = indexBuffer.mapped

        for (cmdList in drawData.cmdLists) {
            var ofs = 0
            for (v in cmdList.vtxBuffer) {
                v.to(vtxDst, ofs)
                ofs += DrawVert.size
            }
            ofs = 0
            for (i in cmdList.idxBuffer) {
                memPutInt(idxDst + ofs, i)
                ofs += Int.BYTES
            }
            vtxDst += cmdList.vtxBuffer.size
            idxDst += cmdList.idxBuffer.size
        }

        // Flush to make writes visible to GPU
        vertexBuffer.flush()
        indexBuffer.flush()

        return updateCmdBuffers
    }

    fun VkCommandBuffer.draw() {

        val drawData = ImGui.drawData
        var vertexOffset = 0
        var indexOffset = 0

        if (drawData == null || drawData.cmdListsCount == 0)
            return

        bindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)
        bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)

        pushConstBlock.scale.put(2f / io.displaySize)
        pushConstBlock.translate put -1f
        pushConstants(pipelineLayout, VkShaderStage.VERTEX_BIT.i, pushConstBlock.size, pushConstBlock.toBuf())

        bindVertexBuffers(vertexBuffer.buffer)
        bindIndexBuffer(indexBuffer.buffer, VkDeviceSize(0), VkIndexType.UINT32)

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

    fun resize(size: Vec2i) = io.displaySize put size

    fun freeResources() {
        context.destroy()
        vertexBuffer.destroy()
        indexBuffer.destroy()
        dev.apply {
            destroyImageView(fontView)
            destroyImage(fontImage)
            freeMemory(fontMemory)
            destroySampler(sampler)
            destroyDescriptorSetLayout(descriptorSetLayout)
            destroyDescriptorPool(descriptorPool)
            destroyPipelineLayout(pipelineLayout)
            destroyPipeline(pipeline)
        }
    }

    fun header(caption: String) = ImGui.collapsingHeader(caption, TreeNodeFlag.DefaultOpen.i)

    fun checkBox(caption: String, value: KMutableProperty0<Boolean>): Boolean =
            ImGui.checkbox(caption, value).also { if (it) updated = true }

    var bool = true

    @JvmName("checkBox_")
    fun checkBox(caption: String, pValue: KMutableProperty0<Int>): Boolean {
        bool = pValue().bool
        return checkBox(caption, ::bool).also { pValue.set(bool.i) }
    }

    fun inputFloat(caption: String, value: KMutableProperty0<Float>, step: Float, precision: Int): Boolean =
            ImGui.inputFloat(caption, value, step, step * 10f/*, precision*/).also { if (it) updated = true }

    fun sliderFloat(caption: String, value: KMutableProperty0<Float>, min: Float, max: Float): Boolean =
            ImGui.sliderFloat(caption, value, min, max).also { if (it) updated = true }

    fun sliderInt(caption: String, value: KMutableProperty0<Int>, min: Int, max: Int): Boolean =
            ImGui.sliderInt(caption, value, min, max).also { if (it) updated = true }

    var currentItem = 0

    fun comboBox(caption: String, itemIndex: KMutableProperty0<Int>, items: Array<String>): Boolean {
        if (items.isEmpty())
            return false
        return ImGui.combo(caption, itemIndex, items, items.size, items.size).also {  if (it)  updated = true }
    }

    fun button(caption: String): Boolean =  ImGui.button(caption).also { if (it) updated = true }

    fun text(string: String) = ImGui.text(string)
}