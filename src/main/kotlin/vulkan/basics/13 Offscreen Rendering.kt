///*
//* Vulkan Example - Offscreen rendering using a separate framebuffer
//*
//* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
//*
//* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
//*/
//
//package vulkan.basics
//
//import glm_.L
//import glm_.buffer.adr
//import glm_.mat4x4.Mat4
//import glm_.size
//import glm_.vec2.Vec2i
//import glm_.vec3.Vec3
//import glm_.vec4.Vec4
//import org.lwjgl.system.MemoryUtil.NULL
//import org.lwjgl.vulkan.*
//import org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL
//import vkk.*
//import vulkan.VERTEX_BUFFER_BIND_ID
//import vulkan.assetPath
//import vulkan.base.*
//
//
//fun main(args: Array<String>) {
//    OffscreenRendering().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//// Offscreen frame buffer properties
//private const val FB_DIM = 512
//private val FB_COLOR_FORMAT = VkFormat.R8G8B8A8_UNORM
//
//private class OffscreenRendering : VulkanExampleBase() {
//
//    var debugDisplay = false
//
//    object textures {
//        val colorMap = Texture2D()
//    }
//
//    // Vertex layout for the models
//    val vertexLayout = VertexLayout(
//            VertexComponent.POSITION,
//            VertexComponent.UV,
//            VertexComponent.COLOR,
//            VertexComponent.NORMAL)
//
//    object models {
//        val example = Model()
//        val quad = Model()
//        val plane = Model()
//    }
//
//    object vertices {
//        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
//        lateinit var bindingDescriptions: VkVertexInputBindingDescription
//        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
//    }
//
//    object uniformBuffers {
//        val vsShared = Buffer()
//        val vsMirror = Buffer()
//        val vsOffScreen = Buffer()
//        val vsDebugQuad = Buffer()
//    }
//
//    object uboShared {
//        lateinit var projection: Mat4
//        lateinit var model: Mat4
//        val lightPos = Vec4(0f, 0f, 0f, 1f)
//    }
//
//    object pipelines {
//        var debug: VkPipeline = NULL
//        var shaded: VkPipeline = NULL
//        var shadedOffscreen: VkPipeline = NULL
//        var mirror: VkPipeline = NULL
//    }
//
//    object pipelineLayouts {
//        var textured: VkPipelineLayout = NULL
//        var shaded: VkPipelineLayout = NULL
//    }
//
//    object descriptorSets {
//        var offscreen: VkDescriptorSet = NULL
//        var mirror: VkDescriptorSet = NULL
//        var model: VkDescriptorSet = NULL
//        var debugQuad: VkDescriptorSet = NULL
//    }
//
//    object descriptorSetLayouts {
//        var textured: VkDescriptorSetLayout = NULL
//        var shaded: VkDescriptorSetLayout = NULL
//    }
//
//    // Framebuffer for offscreen rendering
//    class FrameBufferAttachment {
//        var image: VkImage = NULL
//        var mem: VkDeviceMemory = NULL
//        var view: VkImageView = NULL
//    }
//
//    object offscreenPass {
//        val size = Vec2i()
//        var frameBuffer: VkFramebuffer = NULL
//        val color = FrameBufferAttachment()
//        val depth = FrameBufferAttachment()
//        var renderPass: VkRenderPass = NULL
//        var sampler: VkSampler = NULL
//        lateinit var descriptor: VkDescriptorImageInfo
//        lateinit var commandBuffer: VkCommandBuffer
//        // Semaphore used to synchronize between offscreen and final scene render pass
//        var semaphore: VkSemaphore = NULL
//    }
//
//    val meshPos = Vec3(0f, -1.5f, 0f)
//    val meshRot = Vec3()
//
//    init {
//        zoom = -6f
//        rotation(-2.5f, 0f, 0f)
//        cameraPos(0f, 1f, 0f)
//        timerSpeed *= 0.25f
//        title = "Offscreen rendering"
////        settings.overlay = true
//        enabledFeatures.shaderClipDistance = true
//    }
//
//    override fun destroy() {
//
//        // Clean up used Vulkan resources
//        // Note : Inherited destructor cleans up resources stored in base class
//
//        // Textures
//        textures.colorMap.destroy()
//
//        device.apply {
//            // Frame buffer
//
//            // Color attachment
//            destroyImageView(offscreenPass.color.view)
//            destroyImage(offscreenPass.color.image)
//            freeMemory(offscreenPass.color.mem)
//
//            // Depth attachment
//            destroyImageView(offscreenPass.depth.view)
//            destroyImage(offscreenPass.depth.image)
//            freeMemory(offscreenPass.depth.mem)
//
//            destroyRenderPass(offscreenPass.renderPass)
//            destroySampler(offscreenPass.sampler)
//            destroyFramebuffer(offscreenPass.frameBuffer)
//
//            destroyPipeline(pipelines.debug)
//            destroyPipeline(pipelines.shaded)
//            destroyPipeline(pipelines.shadedOffscreen)
//            destroyPipeline(pipelines.mirror)
//
//            destroyPipelineLayout(pipelineLayouts.textured)
//            destroyPipelineLayout(pipelineLayouts.shaded)
//
//            destroyDescriptorSetLayout(descriptorSetLayouts.shaded)
//            destroyDescriptorSetLayout(descriptorSetLayouts.textured)
//
//            // Models
//            models.apply {
//                example.destroy()
//                quad.destroy()
//                plane.destroy()
//            }
//            // Uniform buffers
//            uniformBuffers.apply {
//                vsShared.destroy()
//                vsMirror.destroy()
//                vsOffScreen.destroy()
//                vsDebugQuad.destroy()
//            }
//            freeCommandBuffer(cmdPool, offscreenPass.commandBuffer)
//            destroySemaphore(offscreenPass.semaphore)
//        }
//        super.destroy()
//    }
//
//    /** Setup the offscreen framebuffer for rendering the mirrored scene
//     *  The color attachment of this framebuffer will then be used to sample from in the fragment shader of the final pass */
//    fun prepareOffscreen() {
//
//        offscreenPass.size put FB_DIM
//
//        // Find a suitable depth format
//        val fbDepthFormat = tools getSupportedDepthFormat physicalDevice
//        assert(fbDepthFormat != VkFormat.UNDEFINED)
//
//        // Color attachment
//        val image = vk.ImageCreateInfo {
//            imageType = VkImageType.`2D`
//            format = FB_COLOR_FORMAT
//            extent(offscreenPass.size, 1)
//            mipLevels = 1
//            arrayLayers = 1
//            samples = VkSampleCount.`1_BIT`
//            tiling = VkImageTiling.OPTIMAL
//            // We will sample directly from the color attachment
//            usage = VkImageUsage.COLOR_ATTACHMENT_BIT or VkImageUsage.SAMPLED_BIT
//        }
//
//        offscreenPass.color.image = device createImage image
//        val memReqs = device getImageMemoryRequirements offscreenPass.color.image
//        val memAlloc = vk.MemoryAllocateInfo {
//            allocationSize = memReqs.size
//            memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
//        }
//        offscreenPass.color.mem = device allocateMemory memAlloc
//        device.bindImageMemory(offscreenPass.color.image, offscreenPass.color.mem)
//
//        val colorImageView = vk.ImageViewCreateInfo {
//            viewType = VkImageViewType.`2D`
//            format = FB_COLOR_FORMAT
//            subresourceRange.apply {
//                aspectMask = VkImageAspect.COLOR_BIT.i
//                baseMipLevel = 0
//                levelCount = 1
//                baseArrayLayer = 0
//                layerCount = 1
//            }
//            this.image = offscreenPass.color.image
//        }
//        offscreenPass.color.view = device createImageView colorImageView
//
//        // Create sampler to sample from the attachment in the fragment shader
//        val samplerInfo = vk.SamplerCreateInfo {
//            magFilter = VkFilter.LINEAR
//            minFilter = VkFilter.LINEAR
//            mipmapMode = VkSamplerMipmapMode.LINEAR
//            addressModeU = VkSamplerAddressMode.CLAMP_TO_EDGE
//            addressModeV = addressModeU // TODO custom func?
//            addressModeW = addressModeU
//            mipLodBias = 0f
//            maxAnisotropy = 1f
//            minLod = 0f
//            maxLod = 1f
//            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
//        }
//        offscreenPass.sampler = device createSampler samplerInfo
//
//        // Depth stencil attachment
//        image.format = fbDepthFormat
//        image.usage = VkImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT.i
//
//        offscreenPass.depth.image = device createImage image
//        device.getImageMemoryRequirements(offscreenPass.depth.image, memReqs)
//        memAlloc.allocationSize = memReqs.size
//        memAlloc.memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
//        offscreenPass.depth.mem = device allocateMemory memAlloc
//        device.bindImageMemory(offscreenPass.depth.image, offscreenPass.depth.mem)
//
//        val depthStencilView = vk.ImageViewCreateInfo {
//            viewType = VkImageViewType.`2D`
//            format = fbDepthFormat
//            flags = 0
//            subresourceRange.apply {
//                aspectMask = VkImageAspect.DEPTH_BIT or VkImageAspect.STENCIL_BIT
//                baseMipLevel = 0
//                levelCount = 1
//                baseArrayLayer = 0
//                layerCount = 1
//            }
//            this.image = offscreenPass.depth.image
//        }
//        offscreenPass.depth.view = device createImageView depthStencilView
//
//        // Create a separate render pass for the offscreen rendering as it may differ from the one used for scene rendering
//
//        val attchmentDescriptions = vk.AttachmentDescription(2).also {
//            // Color attachment
//            it[0].apply {
//                format = FB_COLOR_FORMAT
//                samples = VkSampleCount.`1_BIT`
//                loadOp = VkAttachmentLoadOp.CLEAR
//                storeOp = VkAttachmentStoreOp.STORE
//                stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
//                stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
//                initialLayout = VkImageLayout.UNDEFINED
//                finalLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
//            }
//            // Depth attachment
//            it[1].apply {
//                format = fbDepthFormat
//                samples = VkSampleCount.`1_BIT`
//                loadOp = VkAttachmentLoadOp.CLEAR
//                storeOp = VkAttachmentStoreOp.DONT_CARE
//                stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
//                stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
//                initialLayout = VkImageLayout.UNDEFINED
//                finalLayout = VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL
//            }
//        }
//        val colorReference = vk.AttachmentReference(0, VkImageLayout.COLOR_ATTACHMENT_OPTIMAL)
//        val depthReference = vk.AttachmentReference(1, VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
//
//        val subpassDescription = vk.SubpassDescription {
//            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
//            colorAttachmentCount = 1
//            colorAttachment = colorReference
//            depthStencilAttachment = depthReference
//        }
//        // Use subpass dependencies for layout transitions
//        val dependencies = vk.SubpassDependency(2).also {
//            it[0].apply {
//                srcSubpass = VK_SUBPASS_EXTERNAL
//                dstSubpass = 0
//                srcStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
//                dstStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
//                srcAccessMask = VkAccess.MEMORY_READ_BIT.i
//                dstAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
//                dependencyFlags = VkDependency.BY_REGION_BIT.i
//            }
//            it[1].apply {
//                srcSubpass = 0
//                dstSubpass = VK_SUBPASS_EXTERNAL
//                srcStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
//                dstStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
//                srcAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
//                dstAccessMask = VkAccess.MEMORY_READ_BIT.i
//                dependencyFlags = VkDependency.BY_REGION_BIT.i
//            }
//        }
//        // Create the actual renderpass
//        val renderPassInfo = vk.RenderPassCreateInfo {
//            attachments = attchmentDescriptions
//            subpass = subpassDescription
//            this.dependencies = dependencies
//        }
//        offscreenPass.renderPass = device createRenderPass renderPassInfo
//
//        val attachments = appBuffer.longBufferOf(offscreenPass.color.view, offscreenPass.depth.view)
//
//        val fbufCreateInfo = vk.FramebufferCreateInfo {
//            renderPass = offscreenPass.renderPass
//            this.attachments = attachments
//            extent(offscreenPass.size, 1)
//        }
//        offscreenPass.frameBuffer = device createFramebuffer fbufCreateInfo
//
//        // Fill a descriptor for later use in a descriptor set
//        offscreenPass.descriptor.apply {
//            imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
//            imageView = offscreenPass.color.view
//            sampler = offscreenPass.sampler
//        }
//    }
//
//    /** Sets up the command buffer that renders the scene to the offscreen frame buffer */
//    fun buildOffscreenCommandBuffer() {
//
//        if (offscreenPass::commandBuffer.isInitialized)
//            offscreenPass.commandBuffer = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, false)
//
//        if (offscreenPass.semaphore == NULL) {
//            // Create a semaphore used to synchronize offscreen rendering and usage
//            val semaphoreCreateInfo = vk.SemaphoreCreateInfo()
//            offscreenPass.semaphore = device createSemaphore semaphoreCreateInfo
//        }
//
//        val cmdBufInfo = vk.CommandBufferBeginInfo()
//
//        val clearValues = vk.ClearValue(2).also {
//            it[0].color(0f)
//            it[1].depthStencil(1f, 0)
//        }
//        val renderPassBeginInfo = vk.RenderPassBeginInfo {
//            renderPass = offscreenPass.renderPass
//            framebuffer = offscreenPass.frameBuffer
//            renderArea.extent(offscreenPass.size)
//            this.clearValues = clearValues
//        }
//
//        offscreenPass.commandBuffer.apply {
//
//            begin(cmdBufInfo)
//
//            beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)
//
//            setViewport(offscreenPass.size)
//
//            setScissor(offscreenPass.size)
//
//            // Mirrored scene
//            bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayouts.shaded, descriptorSets.offscreen)
//            bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.shadedOffscreen)
//            bindVertexBuffers(VERTEX_BUFFER_BIND_ID, models.example.vertices.buffer)
//            bindIndexBuffer(models.example.indices.buffer, 0, VkIndexType.UINT32)
//            drawIndexed(models.example.indexCount, 1, 0, 0, 0)
//
//            endRenderPass()
//
//            end()
//        }
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
//            this.renderPass = renderPass
//            renderArea.offset(0)
//            renderArea.extent(size)
//            this.clearValues = clearValues
//        }
//        for (i in drawCmdBuffers.indices) {
//            // Set target frame buffer
//            renderPassBeginInfo.framebuffer(frameBuffers[i])
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
//                if (debugDisplay) {
//                    bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayouts.textured, descriptorSets.debugQuad)
//                    bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.debug)
//                    bindVertexBuffers(VERTEX_BUFFER_BIND_ID, models.quad.vertices.buffer)
//                    bindIndexBuffer(models.quad.indices.buffer, 0, VkIndexType.UINT32)
//                    drawIndexed(models.quad.indexCount, 1, 0, 0, 0)
//                }
//
//                // Scene
//                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.debug)
//
//                // Reflection plane
//                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayouts.textured, descriptorSets.mirror)
//                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.mirror)
//
//                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, models.plane.vertices.buffer)
//                bindIndexBuffer(models.plane.indices.buffer, 0, VkIndexType.UINT32)
//                drawIndexed(models.plane.indexCount, 1, 0, 0, 0)
//
//                // Model
//                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayouts.shaded, descriptorSets.model)
//                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.shaded)
//
//                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, models.example.vertices.buffer)
//                bindIndexBuffer(models.example.indices.buffer, 0, VkIndexType.UINT32)
//                drawIndexed(models.example.indexCount, 1, 0, 0, 0)
//
//                endRenderPass()
//
//                end()
//            }
//        }
//    }
//
//    fun loadAssets() {
//
//        models.plane.loadFromFile("$assetPath/models/plane.obj", vertexLayout, 0.5f, vulkanDevice, queue)
//        models.example.loadFromFile("$assetPath/models/chinesedragon.dae", vertexLayout, 0.3f, vulkanDevice, queue)
//
//        // Textures
//        val (texFormat, format) = vulkanDevice.features.run {
//            when {
//                textureCompressionBC -> "bc3" to VkFormat.BC3_UNORM_BLOCK
//                textureCompressionASTC_LDR -> "astc_8x8" to VkFormat.ASTC_8x8_UNORM_BLOCK
//                textureCompressionETC2 -> "etc2" to VkFormat.ETC2_R8G8B8_UNORM_BLOCK
//                else -> tools.exitFatal("Device does not support any compressed texture format!", VkResult.ERROR_FEATURE_NOT_PRESENT)
//            }
//        }
//        textures.colorMap.loadFromFile("$assetPath/textures/darkmetal_${texFormat}_unorm.ktx", format, vulkanDevice, queue)
//    }
//
//    fun generateQuad() {
//        // Setup vertices for a single uv-mapped quad
////        struct Vertex {
////            float pos [3]
////            float uv [2]
////            float col [3]
////            float normal [3]
////        }
//
//        val QUAD_COLOR_NORMAL = floatArrayOf(
//                1f, 1f, 1f,
//                0f, 0f, 1f)
//        val vertexBuffer = appBuffer.floatBufferOf(
//                1f, 1f, 0f, 1f, 1f, *QUAD_COLOR_NORMAL,
//                0f, 1f, 0f, 0f, 1f, *QUAD_COLOR_NORMAL,
//                0f, 0f, 0f, 0f, 0f, *QUAD_COLOR_NORMAL,
//                1f, 0f, 0f, 1f, 0f, *QUAD_COLOR_NORMAL)
//
//        vulkanDevice.createBuffer(
//                VkBufferUsage.VERTEX_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                vertexBuffer.size.L,
//                models.quad.vertices::buffer,
//                models.quad.vertices::memory,
//                vertexBuffer.adr)
//
//        // Setup indices
//        val indexBuffer = appBuffer.intBufferOf(0, 1, 2, 2, 3, 0)
//        models.quad.indexCount = indexBuffer.size
//
//        vulkanDevice.createBuffer(
//                VkBufferUsage.INDEX_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                indexBuffer.size.L,
//                models.quad.indices::buffer,
//                models.quad.indices::memory,
//                indexBuffer.adr)
//
//        models.quad.device = device
//    }
//
//    void setupVertexDescriptions()
//    {
//        // Binding description
//        vertices.bindingDescriptions.resize(1)
//        vertices.bindingDescriptions[0] =
//                vks::initializers::vertexInputBindingDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        vertexLayout.stride(),
//                        VK_VERTEX_INPUT_RATE_VERTEX)
//
//        // Attribute descriptions
//        vertices.attributeDescriptions.resize(4)
//        // Location 0 : Position
//        vertices.attributeDescriptions[0] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        0,
//                        VK_FORMAT_R32G32B32_SFLOAT,
//                        0)
//        // Location 1 : Texture coordinates
//        vertices.attributeDescriptions[1] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        1,
//                        VK_FORMAT_R32G32_SFLOAT,
//                        sizeof(float) * 3)
//        // Location 2 : Color
//        vertices.attributeDescriptions[2] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        2,
//                        VK_FORMAT_R32G32B32_SFLOAT,
//                        sizeof(float) * 5)
//        // Location 3 : Normal
//        vertices.attributeDescriptions[3] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        3,
//                        VK_FORMAT_R32G32B32_SFLOAT,
//                        sizeof(float) * 8)
//
//        vertices.inputState = vks::initializers::pipelineVertexInputStateCreateInfo()
//        vertices.inputState.vertexBindingDescriptionCount = vertices.bindingDescriptions.size()
//        vertices.inputState.pVertexBindingDescriptions = vertices.bindingDescriptions.data()
//        vertices.inputState.vertexAttributeDescriptionCount = vertices.attributeDescriptions.size()
//        vertices.inputState.pVertexAttributeDescriptions = vertices.attributeDescriptions.data()
//    }
//
//    void setupDescriptorPool()
//    {
//        std::vector<VkDescriptorPoolSize> poolSizes =
//        {
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 6),
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 8)
//        }
//
//        VkDescriptorPoolCreateInfo descriptorPoolInfo =
//        vks::initializers::descriptorPoolCreateInfo(
//                poolSizes.size(),
//                poolSizes.data(),
//                5)
//
//        VK_CHECK_RESULT(vkCreateDescriptorPool(device, & descriptorPoolInfo, nullptr, & descriptorPool))
//    }
//
//    void setupDescriptorSetLayout()
//    {
//        std::vector<VkDescriptorSetLayoutBinding> setLayoutBindings
//                VkDescriptorSetLayoutCreateInfo descriptorLayoutInfo
//                VkPipelineLayoutCreateInfo pipelineLayoutInfo
//
//                // Binding 0 : Vertex shader uniform buffer
//                setLayoutBindings.push_back(vks::initializers::descriptorSetLayoutBinding(
//                        VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                        VK_SHADER_STAGE_VERTEX_BIT,
//                        0))
//        // Binding 1 : Fragment shader image sampler
//        setLayoutBindings.push_back(vks::initializers::descriptorSetLayoutBinding(
//                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                VK_SHADER_STAGE_FRAGMENT_BIT,
//                1))
//        // Binding 2 : Fragment shader image sampler
//        setLayoutBindings.push_back(vks::initializers::descriptorSetLayoutBinding(
//                VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                VK_SHADER_STAGE_FRAGMENT_BIT,
//                2))
//
//        // Shaded layouts (only use first layout binding)
//        descriptorLayoutInfo = vks::initializers::descriptorSetLayoutCreateInfo(setLayoutBindings.data(), 1)
//        VK_CHECK_RESULT(vkCreateDescriptorSetLayout(device, & descriptorLayoutInfo, nullptr, & descriptorSetLayouts . shaded))
//
//        pipelineLayoutInfo = vks::initializers::pipelineLayoutCreateInfo(& descriptorSetLayouts . shaded, 1)
//        VK_CHECK_RESULT(vkCreatePipelineLayout(device, & pipelineLayoutInfo, nullptr, & pipelineLayouts . shaded))
//
//        // Textured layouts (use all layout bindings)
//        descriptorLayoutInfo = vks::initializers::descriptorSetLayoutCreateInfo(setLayoutBindings.data(), static_cast<uint32_t>(setLayoutBindings.size()))
//        VK_CHECK_RESULT(vkCreateDescriptorSetLayout(device, & descriptorLayoutInfo, nullptr, & descriptorSetLayouts . textured))
//
//        pipelineLayoutInfo = vks::initializers::pipelineLayoutCreateInfo(& descriptorSetLayouts . textured, 1)
//        VK_CHECK_RESULT(vkCreatePipelineLayout(device, & pipelineLayoutInfo, nullptr, & pipelineLayouts . textured))
//    }
//
//    void setupDescriptorSet()
//    {
//        // Mirror plane descriptor set
//        VkDescriptorSetAllocateInfo allocInfo =
//        vks::initializers::descriptorSetAllocateInfo(
//                descriptorPool,
//                & descriptorSetLayouts . textured,
//        1)
//
//        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & descriptorSets . mirror))
//
//        std::vector<VkWriteDescriptorSet> writeDescriptorSets =
//        {
//            // Binding 0 : Vertex shader uniform buffer
//            vks::initializers::writeDescriptorSet(
//                    descriptorSets.mirror,
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    0,
//                    & uniformBuffers . vsMirror . descriptor),
//            // Binding 1 : Fragment shader texture sampler
//            vks::initializers::writeDescriptorSet(
//                    descriptorSets.mirror,
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    1,
//                    & offscreenPass . descriptor),
//            // Binding 2 : Fragment shader texture sampler
//            vks::initializers::writeDescriptorSet(
//                    descriptorSets.mirror,
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    2,
//                    & textures . colorMap . descriptor)
//        }
//
//        vkUpdateDescriptorSets(device, writeDescriptorSets.size(), writeDescriptorSets.data(), 0, NULL)
//
//        // Debug quad
//        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & descriptorSets . debugQuad))
//
//        std::vector<VkWriteDescriptorSet> debugQuadWriteDescriptorSets =
//        {
//            // Binding 0 : Vertex shader uniform buffer
//            vks::initializers::writeDescriptorSet(
//                    descriptorSets.debugQuad,
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    0,
//                    & uniformBuffers . vsDebugQuad . descriptor),
//            // Binding 1 : Fragment shader texture sampler
//            vks::initializers::writeDescriptorSet(
//                    descriptorSets.debugQuad,
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    1,
//                    & offscreenPass . descriptor)
//        }
//        vkUpdateDescriptorSets(device, debugQuadWriteDescriptorSets.size(), debugQuadWriteDescriptorSets.data(), 0, NULL)
//
//        // Shaded descriptor sets
//        allocInfo.pSetLayouts = & descriptorSetLayouts . shaded
//
//                // Model
//                // No texture
//                VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & descriptorSets . model))
//
//        std::vector<VkWriteDescriptorSet> modelWriteDescriptorSets =
//        {
//            // Binding 0 : Vertex shader uniform buffer
//            vks::initializers::writeDescriptorSet(
//                    descriptorSets.model,
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    0,
//                    & uniformBuffers . vsShared . descriptor)
//        }
//        vkUpdateDescriptorSets(device, modelWriteDescriptorSets.size(), modelWriteDescriptorSets.data(), 0, NULL)
//
//        // Offscreen
//        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & descriptorSets . offscreen))
//
//        std::vector<VkWriteDescriptorSet> offScreenWriteDescriptorSets =
//        {
//            // Binding 0 : Vertex shader uniform buffer
//            vks::initializers::writeDescriptorSet(
//                    descriptorSets.offscreen,
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    0,
//                    & uniformBuffers . vsOffScreen . descriptor)
//        }
//        vkUpdateDescriptorSets(device, offScreenWriteDescriptorSets.size(), offScreenWriteDescriptorSets.data(), 0, NULL)
//    }
//
//    void preparePipelines()
//    {
//        VkPipelineInputAssemblyStateCreateInfo inputAssemblyState =
//        vks::initializers::pipelineInputAssemblyStateCreateInfo(
//                VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
//                0,
//                VK_FALSE)
//
//        VkPipelineRasterizationStateCreateInfo rasterizationState =
//        vks::initializers::pipelineRasterizationStateCreateInfo(
//                VK_POLYGON_MODE_FILL,
//                VK_CULL_MODE_FRONT_BIT,
//                VK_FRONT_FACE_CLOCKWISE,
//                0)
//
//        VkPipelineColorBlendAttachmentState blendAttachmentState =
//        vks::initializers::pipelineColorBlendAttachmentState(
//                0xf,
//                VK_FALSE)
//
//        VkPipelineColorBlendStateCreateInfo colorBlendState =
//        vks::initializers::pipelineColorBlendStateCreateInfo(
//                1,
//                & blendAttachmentState)
//
//        VkPipelineDepthStencilStateCreateInfo depthStencilState =
//        vks::initializers::pipelineDepthStencilStateCreateInfo(
//                VK_TRUE,
//                VK_TRUE,
//                VK_COMPARE_OP_LESS_OR_EQUAL)
//
//        VkPipelineViewportStateCreateInfo viewportState =
//        vks::initializers::pipelineViewportStateCreateInfo(1, 1, 0)
//
//        VkPipelineMultisampleStateCreateInfo multisampleState =
//        vks::initializers::pipelineMultisampleStateCreateInfo(
//                VK_SAMPLE_COUNT_1_BIT,
//                0)
//
//        std::vector<VkDynamicState> dynamicStateEnables = { VK_DYNAMIC_STATE_VIEWPORT,
//                                                            VK_DYNAMIC_STATE_SCISSOR
//        }
//        VkPipelineDynamicStateCreateInfo dynamicState =
//        vks::initializers::pipelineDynamicStateCreateInfo(
//                dynamicStateEnables.data(),
//                dynamicStateEnables.size(),
//                0)
//
//        // Solid rendering pipeline
//        // Load shaders
//        std::array < VkPipelineShaderStageCreateInfo, 2> shaderStages
//
//        shaderStages[0] = loadShader(getAssetPath() + "shaders/offscreen/quad.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
//        shaderStages[1] = loadShader(getAssetPath() + "shaders/offscreen/quad.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)
//
//        VkGraphicsPipelineCreateInfo pipelineCreateInfo =
//        vks::initializers::pipelineCreateInfo(
//                pipelineLayouts.textured,
//                renderPass,
//                0)
//
//        pipelineCreateInfo.pVertexInputState = & vertices . inputState
//                pipelineCreateInfo.pInputAssemblyState = & inputAssemblyState
//                pipelineCreateInfo.pRasterizationState = & rasterizationState
//                pipelineCreateInfo.pColorBlendState = & colorBlendState
//                pipelineCreateInfo.pMultisampleState = & multisampleState
//                pipelineCreateInfo.pViewportState = & viewportState
//                pipelineCreateInfo.pDepthStencilState = & depthStencilState
//                pipelineCreateInfo.pDynamicState = & dynamicState
//                pipelineCreateInfo.stageCount = shaderStages.size()
//        pipelineCreateInfo.pStages = shaderStages.data()
//
//        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . debug))
//
//        // Mirror
//        shaderStages[0] = loadShader(getAssetPath() + "shaders/offscreen/mirror.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
//        shaderStages[1] = loadShader(getAssetPath() + "shaders/offscreen/mirror.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)
//        rasterizationState.cullMode = VK_CULL_MODE_NONE
//        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . mirror))
//
//        // Flip culling
//        rasterizationState.cullMode = VK_CULL_MODE_BACK_BIT
//
//        // Phong shading pipelines
//        pipelineCreateInfo.layout = pipelineLayouts.shaded
//        // Scene
//        shaderStages[0] = loadShader(getAssetPath() + "shaders/offscreen/phong.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
//        shaderStages[1] = loadShader(getAssetPath() + "shaders/offscreen/phong.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)
//        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . shaded))
//        // Offscreen
//        // Flip culling
//        rasterizationState.cullMode = VK_CULL_MODE_FRONT_BIT
//        pipelineCreateInfo.renderPass = offscreenPass.renderPass
//        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . shadedOffscreen))
//
//    }
//
//    // Prepare and initialize uniform buffer containing shader uniforms
//    void prepareUniformBuffers()
//    {
//        // Mesh vertex shader uniform buffer block
//        VK_CHECK_RESULT(vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &uniformBuffers.vsShared,
//        sizeof(uboShared)))
//
//        // Mirror plane vertex shader uniform buffer block
//        VK_CHECK_RESULT(vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &uniformBuffers.vsMirror,
//        sizeof(uboShared)))
//
//        // Offscreen vertex shader uniform buffer block
//        VK_CHECK_RESULT(vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &uniformBuffers.vsOffScreen,
//        sizeof(uboShared)))
//
//        // Debug quad vertex shader uniform buffer block
//        VK_CHECK_RESULT(vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &uniformBuffers.vsDebugQuad,
//        sizeof(uboShared)))
//
//        // Map persistent
//        VK_CHECK_RESULT(uniformBuffers.vsShared.map())
//        VK_CHECK_RESULT(uniformBuffers.vsMirror.map())
//        VK_CHECK_RESULT(uniformBuffers.vsOffScreen.map())
//        VK_CHECK_RESULT(uniformBuffers.vsDebugQuad.map())
//
//        updateUniformBuffers()
//        updateUniformBufferOffscreen()
//    }
//
//    void updateUniformBuffers()
//    {
//        // Mesh
//        uboShared.projection = glm::perspective(glm::radians(60.0f), (float) width /(float) height, 0.1f, 256.0f)
//        glm::mat4 viewMatrix = glm ::translate(glm::mat4(1.0f), glm::vec3(0.0f, 0.0f, zoom))
//
//        uboShared.model = viewMatrix * glm::translate(glm::mat4(1.0f), cameraPos)
//        uboShared.model = glm::rotate(uboShared.model, glm::radians(rotation.x), glm::vec3(1.0f, 0.0f, 0.0f))
//        uboShared.model = glm::rotate(uboShared.model, glm::radians(rotation.y + meshRot.y), glm::vec3(0.0f, 1.0f, 0.0f))
//        uboShared.model = glm::rotate(uboShared.model, glm::radians(rotation.z), glm::vec3(0.0f, 0.0f, 1.0f))
//
//        uboShared.model = glm::translate(uboShared.model, meshPos)
//
//        memcpy(uniformBuffers.vsShared.mapped, & uboShared, sizeof(uboShared))
//
//        // Mirror
//        uboShared.model = viewMatrix * glm::translate(glm::mat4(1.0f), cameraPos)
//        uboShared.model = glm::rotate(uboShared.model, glm::radians(rotation.x), glm::vec3(1.0f, 0.0f, 0.0f))
//        uboShared.model = glm::rotate(uboShared.model, glm::radians(rotation.y), glm::vec3(0.0f, 1.0f, 0.0f))
//        uboShared.model = glm::rotate(uboShared.model, glm::radians(rotation.z), glm::vec3(0.0f, 0.0f, 1.0f))
//
//        memcpy(uniformBuffers.vsMirror.mapped, & uboShared, sizeof(uboShared))
//
//        // Debug quad
//        uboShared.projection = glm::ortho(4.0f, 0.0f, 0.0f, 4.0f * (float) height /(float) width, -1.0f, 1.0f)
//        uboShared.model = glm::translate(glm::mat4(1.0f), glm::vec3(0.0f, 0.0f, 0.0f))
//
//        memcpy(uniformBuffers.vsDebugQuad.mapped, & uboShared, sizeof(uboShared))
//    }
//
//    void updateUniformBufferOffscreen()
//    {
//        uboShared.projection = glm::perspective(glm::radians(60.0f), (float) width /(float) height, 0.1f, 256.0f)
//        glm::mat4 viewMatrix = glm ::translate(glm::mat4(1.0f), glm::vec3(0.0f, 0.0f, zoom))
//
//        uboShared.model = viewMatrix * glm::translate(glm::mat4(1.0f), cameraPos)
//        uboShared.model = glm::rotate(uboShared.model, glm::radians(rotation.x), glm::vec3(1.0f, 0.0f, 0.0f))
//        uboShared.model = glm::rotate(uboShared.model, glm::radians(rotation.y + meshRot.y), glm::vec3(0.0f, 1.0f, 0.0f))
//        uboShared.model = glm::rotate(uboShared.model, glm::radians(rotation.z), glm::vec3(0.0f, 0.0f, 1.0f))
//
//        uboShared.model = glm::scale(uboShared.model, glm::vec3(1.0f, -1.0f, 1.0f))
//        uboShared.model = glm::translate(uboShared.model, meshPos)
//
//        memcpy(uniformBuffers.vsOffScreen.mapped, & uboShared, sizeof(uboShared))
//    }
//
//    void draw()
//    {
//        VulkanExampleBase::prepareFrame()
//
//        // The scene render command buffer has to wait for the offscreen
//        // rendering to be finished before we can use the framebuffer
//        // color image for sampling during final rendering
//        // To ensure this we use a dedicated offscreen synchronization
//        // semaphore that will be signaled when offscreen renderin
//        // has been finished
//        // This is necessary as an implementation may start both
//        // command buffers at the same time, there is no guarantee
//        // that command buffers will be executed in the order they
//        // have been submitted by the application
//
//        // Offscreen rendering
//
//        // Wait for swap chain presentation to finish
//        submitInfo.pWaitSemaphores = & semaphores . presentComplete
//                // Signal ready with offscreen semaphore
//                submitInfo.pSignalSemaphores = & offscreenPass . semaphore
//
//                // Submit work
//                submitInfo.commandBufferCount = 1
//        submitInfo.pCommandBuffers = & offscreenPass . commandBuffer
//                VK_CHECK_RESULT(vkQueueSubmit(queue, 1, & submitInfo, VK_NULL_HANDLE))
//
//        // Scene rendering
//
//        // Wait for offscreen semaphore
//        submitInfo.pWaitSemaphores = & offscreenPass . semaphore
//                // Signal ready with render complete semaphpre
//                submitInfo.pSignalSemaphores = & semaphores . renderComplete
//
//                // Submit work
//                submitInfo.pCommandBuffers = & drawCmdBuffers [currentBuffer]
//        VK_CHECK_RESULT(vkQueueSubmit(queue, 1, & submitInfo, VK_NULL_HANDLE))
//
//        VulkanExampleBase::submitFrame()
//    }
//
//    void prepare()
//    {
//        VulkanExampleBase::prepare()
//        loadAssets()
//        generateQuad()
//        prepareOffscreen()
//        setupVertexDescriptions()
//        prepareUniformBuffers()
//        setupDescriptorSetLayout()
//        preparePipelines()
//        setupDescriptorPool()
//        setupDescriptorSet()
//        buildCommandBuffers()
//        buildOffscreenCommandBuffer()
//        prepared = true
//    }
//
//    virtual void render()
//    {
//        if (!prepared)
//            return
//        draw()
//        if (!paused) {
//            meshRot.y += frameTimer * 10.0f
//            updateUniformBuffers()
//            updateUniformBufferOffscreen()
//        }
//    }
//
//    virtual void viewChanged()
//    {
//        updateUniformBuffers()
//        updateUniformBufferOffscreen()
//    }
//
//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
//    {
//        if (overlay->header("Settings")) {
//        if (overlay->checkBox("Display render target", &debugDisplay)) {
//        buildCommandBuffers()
//    }
//    }
//    }
//}
//
//VULKAN_EXAMPLE_MAIN()
