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
//import glm_.vec2.Vec2
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
//    object uboShared : Bufferizable() {
//        lateinit var projection: Mat4
//        @Order(1)
//        lateinit var model: Mat4
//        @Order(2)
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
//    fun setupVertexDescriptions() {
//        // Binding description
//        vertices.bindingDescriptions = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, vertexLayout.stride, VkVertexInputRate.VERTEX)
//
//        // Attribute descriptions
//        vertices.attributeDescriptions = vk.VertexInputAttributeDescription(
//                // Location 0 : Position
//                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, 0,
//                // Location 1 : Texture coordinates
//                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32_SFLOAT, Vec3.size,
//                // Location 2 : Color
//                VERTEX_BUFFER_BIND_ID, 2, VkFormat.R32G32B32_SFLOAT, Vec3.size + Vec2.size,
//                // Location 3 : Normal
//                VERTEX_BUFFER_BIND_ID, 3, VkFormat.R32G32B32_SFLOAT, Vec3.size * 2 + Vec2.size)
//
//        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
//            vertexBindingDescription = vertices.bindingDescriptions
//            vertexAttributeDescriptions = vertices.attributeDescriptions
//        }
//    }
//
//    fun setupDescriptorPool() {
//
//        val poolSizes = vk.DescriptorPoolSize(
//                VkDescriptorType.UNIFORM_BUFFER, 6,
//                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 8)
//
//        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 5)
//
//        descriptorPool = device createDescriptorPool descriptorPoolInfo
//    }
//
//    fun setupDescriptorSetLayout() {
//
//        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
//                // Binding 0 : Vertex shader uniform buffer
//                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0,
//                // Binding 1 : Fragment shader image sampler
//                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 1,
//                // Binding 2 : Fragment shader image sampler
//                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 2)
//
//        // Shaded layouts (only use first layout binding, that is [0])
//        var descriptorLayoutInfo = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings[0])
//        descriptorSetLayouts.shaded = device createDescriptorSetLayout descriptorLayoutInfo
//
//        var pipelineLayoutInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayouts.shaded)
//        pipelineLayouts.shaded = device createPipelineLayout pipelineLayoutInfo
//
//        // Textured layouts (use all layout bindings)
//        descriptorLayoutInfo = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
//        descriptorSetLayouts.textured = device createDescriptorSetLayout descriptorLayoutInfo
//
//        pipelineLayoutInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayouts.textured)
//        pipelineLayouts.textured = device createPipelineLayout pipelineLayoutInfo
//    }
//
//    fun setupDescriptorSet() {
//        // Mirror plane descriptor set
//        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayouts.textured)
//
//        descriptorSets.mirror = device allocateDescriptorSets allocInfo
//
//        val writeDescriptorSets = vk.WriteDescriptorSet(
//                // Binding 0 : Vertex shader uniform buffer
//                descriptorSets.mirror, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.vsMirror.descriptor,
//                // Binding 1 : Fragment shader texture sampler
//                descriptorSets.mirror, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, offscreenPass.descriptor,
//                // Binding 2 : Fragment shader texture sampler
//                descriptorSets.mirror, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 2, textures.colorMap.descriptor)
//
//        device updateDescriptorSets writeDescriptorSets
//
//        // Debug quad
//        descriptorSets.debugQuad = device allocateDescriptorSets allocInfo
//
//        val debugQuadWriteDescriptorSets = vk.WriteDescriptorSet(
//                // Binding 0 : Vertex shader uniform buffer
//                descriptorSets.debugQuad, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.vsDebugQuad.descriptor,
//                // Binding 1 : Fragment shader texture sampler
//                descriptorSets.debugQuad, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, offscreenPass.descriptor)
//
//        device updateDescriptorSets debugQuadWriteDescriptorSets
//
//        // Shaded descriptor sets
//        allocInfo.setLayout = descriptorSetLayouts.shaded
//
//        // Model
//        // No texture
//        descriptorSets.model = device allocateDescriptorSets allocInfo
//
//        val modelWriteDescriptorSets = vk.WriteDescriptorSet(
//                // Binding 0 : Vertex shader uniform buffer
//                descriptorSets.model, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.vsShared.descriptor)
//
//        device updateDescriptorSets modelWriteDescriptorSets
//
//        // Offscreen
//        descriptorSets.offscreen = device allocateDescriptorSets allocInfo
//
//        val offScreenWriteDescriptorSets = vk.WriteDescriptorSet(
//                // Binding 0 : Vertex shader uniform buffer
//                descriptorSets.offscreen, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.vsOffScreen.descriptor)
//
//        device updateDescriptorSets offScreenWriteDescriptorSets
//    }
//
//    fun preparePipelines() {
//
//        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)
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
//        // Solid rendering pipeline
//        // Load shaders
//        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
//            it[0].loadShader("$assetPath/shaders/offscreen/quad.vert.spv", VkShaderStage.VERTEX_BIT)
//            it[1].loadShader("$assetPath/shaders/offscreen/quad.frag.spv", VkShaderStage.FRAGMENT_BIT)
//        }
//        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayouts.textured, renderPass).also {
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
//        pipelines.debug = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
//
//        // Mirror
//        shaderStages[0].loadShader("$assetPath/shaders/offscreen/mirror.vert.spv", VkShaderStage.VERTEX_BIT)
//        shaderStages[1].loadShader("$assetPath/shaders/offscreen/mirror.frag.spv", VkShaderStage.FRAGMENT_BIT)
//        rasterizationState.cullMode = VkCullMode.NONE.i
//        pipelines.mirror = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
//
//        // Flip culling
//        rasterizationState.cullMode = VkCullMode.BACK_BIT.i
//
//        // Phong shading pipelines
//        pipelineCreateInfo.layout = pipelineLayouts.shaded
//        // Scene
//        shaderStages[0].loadShader("$assetPath/shaders/offscreen/phong.vert.spv", VkShaderStage.VERTEX_BIT)
//        shaderStages[1].loadShader("$assetPath/shaders/offscreen/phong.frag.spv", VkShaderStage.FRAGMENT_BIT)
//        pipelines.shaded = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
//        // Offscreen
//        // Flip culling
//        rasterizationState.cullMode = VkCullMode.FRONT_BIT.i
//        pipelineCreateInfo.renderPass = offscreenPass.renderPass
//        pipelines.shadedOffscreen = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
//    }
//
//    /** Prepare and initialize uniform buffer containing shader uniforms */
//    fun prepareUniformBuffers() {
//        // Mesh vertex shader uniform buffer block
//        vulkanDevice.createBuffer(
//                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                uniformBuffers.vsShared,
//                uboShared.size.L)
//
//        // Mirror plane vertex shader uniform buffer block
//        vulkanDevice.createBuffer(
//                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                uniformBuffers.vsMirror,
//                uboShared.size.L)
//
//        // Offscreen vertex shader uniform buffer block
//        vulkanDevice.createBuffer(
//                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                uniformBuffers.vsOffScreen,
//                uboShared.size.L)
//
//        // Debug quad vertex shader uniform buffer block
//        vulkanDevice.createBuffer(
//        VkBufferUsage.UNIFORM_BUFFER_BIT.i,
//        VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//        uniformBuffers.vsDebugQuad,
//        uboShared.size.L)
//
//        // Map persistent
//        uniformBuffers.apply {
//            vsShared.map()
//            vsMirror.map()
//            vsOffScreen.map()
//            vsDebugQuad.map()
//        }
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
