/*
* Vulkan Example - imGui (https://github.com/ocornut/imgui)
*
* Copyright (C) 2017 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.userInterface

//import glm_.BYTES
//import glm_.L
//import glm_.func.cos
//import glm_.func.rad
//import glm_.func.sin
//import glm_.i
//import glm_.mat4x4.Mat4
//import glm_.max
//import glm_.vec2.Vec2
//import glm_.vec2.Vec2i
//import glm_.vec2.operators.div
//import glm_.vec3.Vec3
//import glm_.vec4.Vec4
//import imgui.*
//import imgui.dsl.window
//import imgui.imgui.Context
//import kool.adr
//import org.lwjgl.glfw.GLFW.*
//import org.lwjgl.system.MemoryStack.stackGet
//import org.lwjgl.system.MemoryUtil.NULL
//import org.lwjgl.system.MemoryUtil.memPutInt
//import org.lwjgl.vulkan.VkCommandBuffer
//import org.lwjgl.vulkan.VkQueue
//import vkk.*
//import vkk.entities.*
//import vkk.extensionFunctions.*
//import vulkan.assetPath
//import vulkan.base.*
//import vulkan.rotateLeft
//import java.nio.ByteBuffer
//
//
//fun main() {
//    VulkanExample().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//// Options and values to display/toggle from the UI
//object uiSettings {
//    var displayModels = true
//    var displayLogos = true
//    var displayBackground = true
//    var animateLight = false
//    var lightSpeed = 0.25f
//    val frameTimes = FloatArray(50)
//    var frameTimeMin = 9999f
//    var frameTimeMax = 0f
//    var lightTimer = 0f
//}
//
//// ----------------------------------------------------------------------------
//// ImGUI class
//// ----------------------------------------------------------------------------
//class ImGUI(val example: VulkanExampleBase) {
//
//    // Vulkan resources for rendering the UI
//    var sampler = VkSampler.NULL
//    val vertexBuffer = Buffer()
//    val indexBuffer = Buffer()
//    var vertexCount = 0
//    var indexCount = 0
//    var fontMemory = VkDeviceMemory.NULL
//    var fontImage = VkImage.NULL
//    var fontView = VkImageView.NULL
//    var pipelineCache = VkPipelineCache.NULL
//    var pipelineLayout = VkPipelineLayout.NULL
//    var pipeline = VkPipeline.NULL
//    var descriptorPool = VkDescriptorPool.NULL
//    var descriptorSetLayout = VkDescriptorSetLayout.NULL
//    var descriptorSet = VkDescriptorSet.NULL
//    lateinit var device: VulkanDevice
//
//    val dev get() = device.logicalDevice!!
//
//    // UI params are set via push constants
//    private val pushConstBlock = object {
//        var scale = Vec2()
//        var translate = Vec2()
//        fun toBufferStack(): ByteBuffer {
//            val buf = stackGet().malloc(Vec2.size * 2)
//            scale to buf
//            translate.to(buf, Vec2.size)
//            return buf
//        }
//
//        val size = Vec2i.size * 2
//    }
//
//    val context = Context()
//
//    val imgui = ImGui
//    val style = ImGui.style
//    val io = ImGui.io
//
//    fun destroy() {
//
//        context.destroy()
//        // Release all Vulkan resources required for rendering imGui
//        vertexBuffer.destroy()
//        indexBuffer.destroy()
//        dev.apply {
//            destroyImage(fontImage)
//            destroyImageView(fontView)
//            freeMemory(fontMemory)
//            destroySampler(sampler)
//            destroyPipelineCache(pipelineCache)
//            destroyPipeline(pipeline)
//            destroyPipelineLayout(pipelineLayout)
//            destroyDescriptorPool(descriptorPool)
//            destroyDescriptorSetLayout(descriptorSetLayout)
//        }
//    }
//
//    // Initialize styles, keys, etc.
//    fun init(size: Vec2i) {
//        // Color scheme
//        style.colors.also {
//            it[Col.TitleBg] = Vec4(1f, 0f, 0f, 0.6f)
//            it[Col.TitleBgActive] = Vec4(1f, 0f, 0f, 0.8f)
//            it[Col.MenuBarBg] = Vec4(1f, 0f, 0f, 0.4f)
//            it[Col.Header] = Vec4(1f, 0f, 0f, 0.4f)
//            it[Col.CheckMark] = Vec4(0f, 1f, 0f, 1f)
//        }
//        // Dimensions
//        io.apply {
//            displaySize put size
//            displayFramebufferScale put 1f
//        }
//    }
//
//    // Initialize all Vulkan resources used by the ui
//    fun initResources(renderPass: VkRenderPass, copyQueue: VkQueue) {
//
//        // Create font texture
//        val (fontData, size, bytePerPixel) = io.fonts.getTexDataAsRGBA32()
//        val uploadSize = VkDeviceSize(size.x * size.y * bytePerPixel.L)
//
//        // Create target image for copy
//        val imageInfo = vk.ImageCreateInfo {
//            imageType = VkImageType._2D
//            format = VkFormat.R8G8B8A8_UNORM
//            extent(size, 1)
//            mipLevels = 1
//            arrayLayers = 1
//            samples = VkSampleCount._1_BIT
//            tiling = VkImageTiling.OPTIMAL
//            usage = VkImageUsage.SAMPLED_BIT or VkImageUsage.TRANSFER_DST_BIT
//            sharingMode = VkSharingMode.EXCLUSIVE
//            initialLayout = VkImageLayout.UNDEFINED
//        }
//        fontImage = dev createImage imageInfo
//        val memReqs = dev.getImageMemoryRequirements(fontImage)
//        val memAllocInfo = vk.MemoryAllocateInfo {
//            allocationSize = memReqs.size
//            memoryTypeIndex = device.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
//        }
//        fontMemory = dev allocateMemory memAllocInfo
//        dev.bindImageMemory(fontImage, fontMemory)
//
//        // Image view
//        val viewInfo = vk.ImageViewCreateInfo {
//            image = fontImage
//            viewType = VkImageViewType._2D
//            format = VkFormat.R8G8B8A8_UNORM
//            subresourceRange.apply {
//                aspectMask = VkImageAspect.COLOR_BIT.i
//                levelCount = 1
//                layerCount = 1
//            }
//        }
//        fontView = dev createImageView viewInfo
//
//        // Staging buffers for font data upload
//        val stagingBuffer = Buffer()
//
//        device.createBuffer(
//                VkBufferUsage.TRANSFER_SRC_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                stagingBuffer,
//                uploadSize)
//
//        stagingBuffer.mapping { data ->
//            memCopy(fontData.adr, data, uploadSize)
//        }
//
//        // Copy buffer data to font image
//        val copyCmd = device.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)
//
//        // Prepare for transfer
//        tools.setImageLayout(
//                copyCmd,
//                fontImage,
//                VkImageAspect.COLOR_BIT.i,
//                VkImageLayout.UNDEFINED,
//                VkImageLayout.TRANSFER_DST_OPTIMAL,
//                VkPipelineStage.HOST_BIT.i,
//                VkPipelineStage.TRANSFER_BIT.i)
//
//        // Copy
//        val bufferCopyRegion = vk.BufferImageCopy {
//            imageSubresource.apply {
//                aspectMask = VkImageAspect.COLOR_BIT.i
//                layerCount = 1
//            }
//            imageExtent(size, 1)
//        }
//        copyCmd.copyBufferToImage(
//                stagingBuffer.buffer,
//                fontImage,
//                VkImageLayout.TRANSFER_DST_OPTIMAL,
//                bufferCopyRegion)
//
//        // Prepare for shader read
//        tools.setImageLayout(
//                copyCmd,
//                fontImage,
//                VkImageAspect.COLOR_BIT.i,
//                VkImageLayout.TRANSFER_DST_OPTIMAL,
//                VkImageLayout.SHADER_READ_ONLY_OPTIMAL,
//                VkPipelineStage.TRANSFER_BIT.i,
//                VkPipelineStage.FRAGMENT_SHADER_BIT.i)
//
//        device.flushCommandBuffer(copyCmd, copyQueue, true)
//
//        stagingBuffer.destroy()
//
//        // Font texture Sampler
//        val samplerInfo = vk.SamplerCreateInfo {
//            minMagFilter = VkFilter.LINEAR
//            mipmapMode = VkSamplerMipmapMode.LINEAR
//            addressModeUVW = VkSamplerAddressMode.CLAMP_TO_EDGE
//            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
//        }
//        sampler = dev createSampler samplerInfo
//
//        // Descriptor pool
//        val poolSizes = vk.DescriptorPoolSize(VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1)
//        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 2)
//        descriptorPool = dev createDescriptorPool descriptorPoolInfo
//
//        // Descriptor set layout
//        val setLayoutBindings = vk.DescriptorSetLayoutBinding(VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 0)
//        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
//        descriptorSetLayout = dev createDescriptorSetLayout descriptorLayout
//
//        // Descriptor set
//        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)
//        descriptorSet = dev allocateDescriptorSets allocInfo
//        val fontDescriptor = vk.DescriptorImageInfo(sampler, fontView, VkImageLayout.SHADER_READ_ONLY_OPTIMAL)
//        val writeDescriptorSets = vk.WriteDescriptorSet(descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 0, fontDescriptor)
//        dev.updateDescriptorSets(writeDescriptorSets)
//
//        // Pipeline cache
//        pipelineCache = dev createPipelineCache vk.PipelineCacheCreateInfo()
//
//        // Pipeline layout
//        // Push constants for UI rendering parameters
//        val pushConstantRange = vk.PushConstantRange(VkShaderStage.VERTEX_BIT, pushConstBlock.size)
//        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout).also {
//            it.pushConstantRange = pushConstantRange
//        }
//        pipelineLayout = dev createPipelineLayout pipelineLayoutCreateInfo
//
//        // Setup graphics pipeline for UI rendering
//        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST)
//
//        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.NONE.i, VkFrontFace.COUNTER_CLOCKWISE)
//
//        // Enable blending
//        val blendAttachmentState = vk.PipelineColorBlendAttachmentState {
//            blendEnable = true
//            colorWriteMask = VkColorComponent.R_BIT or VkColorComponent.G_BIT or VkColorComponent.B_BIT or VkColorComponent.A_BIT
//            srcColorBlendFactor = VkBlendFactor.SRC_ALPHA
//            dstColorBlendFactor = VkBlendFactor.ONE_MINUS_SRC_ALPHA
//            colorBlendOp = VkBlendOp.ADD
//            srcAlphaBlendFactor = VkBlendFactor.ONE_MINUS_SRC_ALPHA
//            dstAlphaBlendFactor = VkBlendFactor.ZERO
//            alphaBlendOp = VkBlendOp.ADD
//        }
//
//        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)
//
//        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(false, false, VkCompareOp.LESS_OR_EQUAL)
//
//        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1, 0)
//
//        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount._1_BIT)
//
//        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
//        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)
//
//        val shaderStages = vk.PipelineShaderStageCreateInfo(2)
//
//        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {
//            it.inputAssemblyState = inputAssemblyState
//            it.rasterizationState = rasterizationState
//            it.colorBlendState = colorBlendState
//            it.multisampleState = multisampleState
//            it.viewportState = viewportState
//            it.depthStencilState = depthStencilState
//            it.dynamicState = dynamicState
//            it.stages = shaderStages
//        }
//        // Vertex bindings an attributes based on ImGui vertex definition
//        val vertexInputBinding = vk.VertexInputBindingDescription(0, DrawVert.size, VkVertexInputRate.VERTEX)
//        val vertexInputAttributes = vk.VertexInputAttributeDescription(
//                0, 0, VkFormat.R32G32_SFLOAT, DrawVert.ofsPos.i,  // Location 0: Position // TODO remove .i
//                0, 1, VkFormat.R32G32_SFLOAT, DrawVert.ofsUv,   // Location 1: UV
//                0, 2, VkFormat.R8G8B8A8_UNORM, DrawVert.ofsCol) // Location 0: Color
//        val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
//            vertexBindingDescription = vertexInputBinding
//            vertexAttributeDescriptions = vertexInputAttributes
//        }
//
//        pipelineCreateInfo.vertexInputState = vertexInputState
//
//        example.apply {
//            shaderStages[0].loadShader("$assetPath/shaders/imgui/ui.vert.spv", VkShaderStage.VERTEX_BIT)
//            shaderStages[1].loadShader("$assetPath/shaders/imgui/ui.frag.spv", VkShaderStage.FRAGMENT_BIT)
//        }
//
//        pipeline = dev.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
//    }
//
//    var f = 0f
//    var showDemo = true // TODO remove
//
//    // Starts a new imGui frame and sets up windows and ui elements
//    fun newFrame(example: VulkanExampleBase, updateFrameGraph: Boolean) {
//
//        imgui.newFrame()
//
//        // Init imGui windows and elements
//
//        val clearColor = Color(114, 144, 154)
//        imgui.textUnformatted(example.title)
//        imgui.textUnformatted(device.properties.deviceName)
//
//        // Update frame time display
//        if (updateFrameGraph) {
//            uiSettings.frameTimes.rotateLeft(1)
//            val frameTime = 1000f / (example.frameTimer * 1000f)
//            uiSettings.frameTimes[uiSettings.frameTimes.lastIndex] = frameTime
//            if (frameTime < uiSettings.frameTimeMin)
//                uiSettings.frameTimeMin = frameTime
//            if (frameTime > uiSettings.frameTimeMax)
//                uiSettings.frameTimeMax = frameTime
//        }
//
//        imgui.apply {
//
//            plotLines("Frame Times", uiSettings.frameTimes, 0, "", uiSettings.frameTimeMin, uiSettings.frameTimeMax, Vec2(0, 80))
//
//            text("Camera")
//            inputVec3("position", example.camera.position/*, 2*/)
//            inputVec3("rotation", example.camera.rotation/*, 2*/)
//
//            setNextWindowSize(Vec2(200), Cond.FirstUseEver)
//            window("Example settings") {
//                checkbox("Render models", uiSettings::displayModels)
//                checkbox("Display logos", uiSettings::displayLogos)
//                checkbox("Display background", uiSettings::displayBackground)
//                checkbox("Animate light", uiSettings::animateLight)
//                sliderFloat("Light speed", uiSettings::lightSpeed, 0.1f, 1f)
//            }
//
//            setNextWindowPos(Vec2(650, 20), Cond.FirstUseEver)
//            showDemoWindow(::showDemo)
//
//            // Render to generate draw buffers
//            render()
//        }
//    }
//
//    // Update vertex and index buffer containing the imGui elements when required
//    fun updateBuffers() {
//
//        val drawData = imgui.drawData!!
//
//        // Note: Alignment is done inside buffer creation
//        val vertexBufferSize = VkDeviceSize(drawData.totalVtxCount * DrawVert.size.L)
//        val indexBufferSize = VkDeviceSize(drawData.totalIdxCount * DrawIdx.BYTES.L)
//
//        if (vertexBufferSize.isEmpty || indexBufferSize.isEmpty)
//            return
//
//        // Update buffers only if vertex or index count has been changed compared to current buffer size
//
//        // Vertex buffer
//        if (vertexBuffer.buffer.L == NULL || vertexCount != drawData.totalVtxCount) {
//            vertexBuffer.unmap()
//            vertexBuffer.destroy()
//            device.createBuffer(VkBufferUsage.VERTEX_BUFFER_BIT.i, VkMemoryProperty.HOST_VISIBLE_BIT.i, vertexBuffer, vertexBufferSize)
//            vertexCount = drawData.totalVtxCount
//            vertexBuffer.unmap()
//            vertexBuffer.map()
//        }
//
//        // Index buffer
//        val indexSize = VkDeviceSize(drawData.totalIdxCount * DrawIdx.BYTES.L)
//        if (indexBuffer.buffer.L == NULL || indexCount < drawData.totalIdxCount) {
//            indexBuffer.unmap()
//            indexBuffer.destroy()
//            device.createBuffer(VkBufferUsage.INDEX_BUFFER_BIT.i, VkMemoryProperty.HOST_VISIBLE_BIT.i, indexBuffer, indexBufferSize)
//            indexCount = drawData.totalIdxCount
//            indexBuffer.map()
//        }
//
//        // Upload data
//        var vtxDst = vertexBuffer.mapped
//        var idxDst = indexBuffer.mapped
//
//        for (n in 0 until drawData.cmdLists.size) {
//            val cmdList = drawData.cmdLists[n]
//            var ofs = 0
//            TODO()
////            for (v in cmdList.vtxBuffer) {
////                v.to(vtxDst, ofs)
////                ofs += DrawVert.size
////            }
////            ofs = 0
////            for (i in cmdList.idxBuffer) {
////                memPutInt(idxDst + ofs, i)
////                ofs += Int.BYTES
////            }
////            vtxDst += cmdList.vtxBuffer.size
////            idxDst += cmdList.idxBuffer.size
//        }
//
//        // Flush to make writes visible to GPU
//        vertexBuffer.flush()
//        indexBuffer.flush()
//    }
//
//    // Draw current imGui frame into a command buffer
//    fun VkCommandBuffer.drawFrame() {
//
//        bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
//        bindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)
//
//        setViewport(io.displaySize)
//
//        // UI scale and translate via push constants
//        pushConstBlock.scale.put(2f / io.displaySize)
//        pushConstBlock.translate put -1f
//        pushConstants(pipelineLayout, VkShaderStage.VERTEX_BIT.i, 0, pushConstBlock.toBufferStack())
//
//        // Render commands
//        val drawData = ImGui.drawData!!
//        var vertexOffset = 0
//        var indexOffset = 0
//
//        if (drawData.cmdLists.size > 0) {
//
//            bindVertexBuffers(vertexBuffer.buffer)
//            bindIndexBuffer(indexBuffer.buffer, VkDeviceSize(0), VkIndexType.UINT32) // jvm imgui uses int, not shorts
//
//            for (cmdList in drawData.cmdLists) {
//                for (cmd in cmdList.cmdBuffer) {
//                    val scissorRect = vk.Rect2D {
//                        offset.x = cmd.clipRect.x.i max 0
//                        offset.y = cmd.clipRect.y.i max 0
//                        extent.width = (cmd.clipRect.z - cmd.clipRect.x).i
//                        extent.height = (cmd.clipRect.w - cmd.clipRect.y).i
//                    }
//                    setScissor(scissorRect)
//                    drawIndexed(cmd.elemCount, 1, indexOffset, vertexOffset, 0)
//                    indexOffset += cmd.elemCount
//                }
//                vertexOffset += cmdList.vtxBuffer.size
//            }
//        }
//    }
//}
//
//class VulkanExample : VulkanExampleBase() {
//
//    lateinit var imGui: ImGUI
//
//    // Vertex layout for the models
//    val vertexLayout = VertexLayout(
//            VertexComponent.POSITION,
//            VertexComponent.NORMAL,
//            VertexComponent.COLOR)
//
//    private object models {
//        val models = Model()
//        val logos = Model()
//        val background = Model()
//    }
//
//    val uniformBufferVS = Buffer()
//
//    private object uboVS : Bufferizable() {
//        var projection = Mat4()
//        var modelview = Mat4()
//        var lightPos = Vec4()
//        override var fieldOrder = arrayOf("projection", "modelview", "lightPos")
//    }
//
//    var pipelineLayout = VkPipelineLayout.NULL
//    var pipeline = VkPipeline.NULL
//    var descriptorSetLayout = VkDescriptorSetLayout.NULL
//    var descriptorSet = VkDescriptorSet.NULL
//
//    init {
//        title = "Vulkan Example - ImGui"
//        camera.apply {
//            type = Camera.CameraType.lookAt
//            setPosition(Vec3(0f, 1.4f, -4.8f))
//            setRotation(Vec3(4.5f, -380f, 0f))
//            setPerspective(45f, size.aspect, 0.1f, 256f)
//        }
////
////        window.mouseButtonCallback = {button, action, mods ->
////
////        }
////        virtual void mouseMoved(double x, double y, bool &handled)
////        {
////            ImGuiIO& io = ImGui::GetIO()
////            handled = io.WantCaptureMouse
////        }
//    }
//
//    override fun destroy() {
//
//        device.apply {
//            destroyPipeline(pipeline)
//            destroyPipelineLayout(pipelineLayout)
//            destroyDescriptorSetLayout(descriptorSetLayout)
//        }
//        models.apply {
//            models.destroy()
//            background.destroy()
//            logos.destroy()
//        }
//        uniformBufferVS.destroy()
//
//        super.destroy()
//    }
//
//    override fun buildCommandBuffers() {
//
//        val cmdBufInfo = vk.CommandBufferBeginInfo()
//
//        val clearValues = vk.ClearValue(2).also {
//            it[0].color(0.2f, 0.2f, 0.2f, 1f)
//            it[1].depthStencil(1f, 0)
//        }
//
//        val renderPassBeginInfo = vk.RenderPassBeginInfo {
//            this.renderPass = renderPass
//            renderArea.apply {
//                offset.set(0, 0)
//                extent(size)
//            }
//            this.clearValues = clearValues
//        }
//        imGui.newFrame(this, frameCounter == 0)
//
//        imGui.updateBuffers()
//
//        for (i in drawCmdBuffers.indices) {
//
//            // Set target frame buffer
//            renderPassBeginInfo.framebuffer = frameBuffers[i]
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
//                // Render scene
//                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
//                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)
//
//                if (uiSettings.displayBackground) {
//                    bindVertexBuffers(models.background.vertices.buffer)
//                    bindIndexBuffer(models.background.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
//                    drawIndexed(models.background.indexCount, 1, 0, 0, 0)
//                }
//
//                if (uiSettings.displayModels) {
//                    bindVertexBuffers(models.models.vertices.buffer)
//                    bindIndexBuffer(models.models.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
//                    drawIndexed(models.models.indexCount, 1, 0, 0, 0)
//                }
//
//                if (uiSettings.displayLogos) {
//                    bindVertexBuffers(models.logos.vertices.buffer)
//                    bindIndexBuffer(models.logos.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
//                    drawIndexed(models.logos.indexCount, 1, 0, 0, 0)
//                }
//
//                // Render imGui
//                imGui.apply { drawFrame() }
//
//                endRenderPass()
//
//                end()
//            }
//        }
//    }
//
//    fun setupLayoutsAndDescriptors() {
//
//        // descriptor pool
//        val poolSizes = vk.DescriptorPoolSize(
//                VkDescriptorType.UNIFORM_BUFFER, 2,
//                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1)
//
//        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 2)
//        descriptorPool = device createDescriptorPool descriptorPoolInfo
//
//        // Set layout
//        val setLayoutBindings = vk.DescriptorSetLayoutBinding(VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0)
//
//        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
//        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout
//
//        // Pipeline layout
//        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)
//        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
//
//        // Descriptor set
//        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)
//        descriptorSet = device allocateDescriptorSets allocInfo
//        val writeDescriptorSets = vk.WriteDescriptorSet(descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBufferVS.descriptor)
//        device.updateDescriptorSets(writeDescriptorSets)
//    }
//
//    fun preparePipelines() {
//
//        // Rendering
//        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST)
//
//        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.FRONT_BIT.i, VkFrontFace.COUNTER_CLOCKWISE)
//
//        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)
//
//        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)
//
//        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)
//
//        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1, 0)
//
//        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount._1_BIT)
//
//        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
//        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)
//
//        // Load shaders
//        val shaderStages = vk.PipelineShaderStageCreateInfo(2)
//
//        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {
//            it.inputAssemblyState = inputAssemblyState
//            it.rasterizationState = rasterizationState
//            it.colorBlendState = colorBlendState
//            it.multisampleState = multisampleState
//            it.viewportState = viewportState
//            it.depthStencilState = depthStencilState
//            it.dynamicState = dynamicState
//            it.stages = shaderStages
//        }
//
//        val vertexInputBinding = vk.VertexInputBindingDescription(0, vertexLayout.stride, VkVertexInputRate.VERTEX)
//
//        val vertexInputAttributes = vk.VertexInputAttributeDescription(
//                0, 0, VkFormat.R32G32B32_SFLOAT, 0,             // Location 0: Position
//                0, 1, VkFormat.R32G32B32_SFLOAT, Vec3.size,             // Location 1: Normal
//                0, 2, VkFormat.R32G32B32_SFLOAT, Vec2.size * 2) // Location 2: Color
//
//        val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
//            vertexBindingDescription = vertexInputBinding
//            vertexAttributeDescriptions = vertexInputAttributes
//        }
//
//        pipelineCreateInfo.vertexInputState = vertexInputState
//
//        shaderStages[0].loadShader("$assetPath/shaders/imgui/scene.vert.spv", VkShaderStage.VERTEX_BIT)
//        shaderStages[1].loadShader("$assetPath/shaders/imgui/scene.frag.spv", VkShaderStage.FRAGMENT_BIT)
//        pipeline = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
//    }
//
//    // Prepare and initialize uniform buffer containing shader uniforms
//    fun prepareUniformBuffers() {
//        // Vertex shader uniform buffer block
//        vulkanDevice.createBuffer(
//                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                uniformBufferVS,
//                VkDeviceSize(uboVS.size.L))
//
//        updateUniformBuffers()
//    }
//
//    fun updateUniformBuffers() {
//
//        // Vertex shader
//        uboVS.projection put camera.matrices.perspective
//        uboVS.modelview put camera.matrices.view
//
//        // Light source
//        if (uiSettings.animateLight) {
//            uiSettings.lightTimer += frameTimer * uiSettings.lightSpeed
//            uboVS.lightPos.x = (uiSettings.lightTimer * 360f).rad.sin * 15f
//            uboVS.lightPos.z = (uiSettings.lightTimer * 360f).rad.cos * 15f
//        }
//
//        uniformBufferVS.mapping { pData -> uboVS to pData }
//    }
//
//    fun draw() {
//
//        super.prepareFrame()
//        buildCommandBuffers()
//        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]
//        queue submit submitInfo
//        super.submitFrame()
//    }
//
//    fun loadAssets() {
//        models.models.loadFromFile("$assetPath/models/vulkanscenemodels.dae", vertexLayout, 1f, vulkanDevice, queue)
//        models.background.loadFromFile("$assetPath/models/vulkanscenebackground.dae", vertexLayout, 1f, vulkanDevice, queue)
//        models.logos.loadFromFile("$assetPath/models/vulkanscenelogos.dae", vertexLayout, 1f, vulkanDevice, queue)
//    }
//
//    fun prepareImGui() {
//        imGui = ImGUI(this).apply {
//            init(size)
//            initResources(renderPass, queue)
//        }
//    }
//
//    override fun prepare() {
//        super.prepare()
//        loadAssets()
//        prepareUniformBuffers()
//        setupLayoutsAndDescriptors()
//        preparePipelines()
//        prepareImGui()
//        buildCommandBuffers()
//        prepared = true
//    }
//
//    override fun render() {
//
//        if (!prepared)
//            return
//
//        // Update imGui
//        ImGui.io.apply {
//
//            displaySize put size
//            deltaTime = frameTimer
//
//            mousePos = this@VulkanExample.mousePos
//            TODO()
////            mouseDown[0] = window.mouseButton(GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS
////            mouseDown[1] = window.mouseButton(GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS
//        }
//
//        draw()
//
//        if (uiSettings.animateLight)
//            updateUniformBuffers()
//    }
//
//    override fun viewChanged() = updateUniformBuffers()
//}