/*
* Vulkan Example - Using subpasses for G-Buffer compositing
*
* Copyright (C) 2016-2017 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*
* Summary:
* Implements a deferred rendering setup with a forward transparency pass using sub passes
*
* Sub passes allow reading from the previous framebuffer (in the same render pass) at
* the same pixel position.
*
* This is a feature that was especially designed for tile-based-renderers
* (mostly mobile GPUs) and is a new optomization feature in Vulkan for those GPU types.
*
*/

package vulkan.basics

import glm_.BYTES
import glm_.L
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import kool.stak
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import vkk.*
import vulkan.NUM_LIGHTS
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.assetPath
import vulkan.base.*
import java.lang.Math.abs


fun main(args: Array<String>) {
    Subpasses().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class Subpasses : VulkanExampleBase() {

    object textures {
        val glass = Texture2D()
    }

    // Vertex layout for the models
    val vertexLayout = VertexLayout(
            VertexComponent.POSITION,
            VertexComponent.COLOR,
            VertexComponent.NORMAL,
            VertexComponent.UV)

    object models {
        val scene = Model()
        val transparent = Model()
    }

    object vertices {
        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
        lateinit var bindingDescriptions: VkVertexInputBindingDescription
        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
    }

    object uboGBuffer : Bufferizable() {
        lateinit var projection: Mat4
        @Order(1)
        lateinit var model: Mat4
        lateinit var view: Mat4
    }

    class Light {
        var position = Vec4()
        var color = Vec3()
        var radius = 0f
    }

    object uboLights : Bufferizable() {
        lateinit var viewPos: Vec4
        val lights = Array(NUM_LIGHTS) { Light() }
    }

    object uniformBuffers {
        val GBuffer = Buffer()
        val lights = Buffer()
    }

    object pipelines {
        var offscreen = VkPipeline(NULL)
        var composition = VkPipeline(NULL)
        var transparent = VkPipeline(NULL)
    }

    object pipelineLayouts {
        var offscreen = VkPipelineLayout(NULL)
        var composition = VkPipelineLayout(NULL)
        var transparent = VkPipelineLayout(NULL)
    }

    object descriptorSets {
        var scene = VkDescriptorSet(NULL)
        var composition = VkDescriptorSet(NULL)
        var transparent = VkDescriptorSet(NULL)
    }

    object descriptorSetLayouts {
        var scene = VkDescriptorSetLayout(NULL)
        var composition = VkDescriptorSetLayout(NULL)
        var transparent = VkDescriptorSetLayout(NULL)
    }

    // G-Buffer framebuffer attachments
    class FrameBufferAttachment {
        var image = VkImage(NULL)
        var mem = VkDeviceMemory(0)
        var view = VkImageView(NULL)
        var format = VkFormat.UNDEFINED
    }

    object attachments {
        val position = FrameBufferAttachment()
        val normal = FrameBufferAttachment()
        val albedo = FrameBufferAttachment()
    }

    init {
        title = "Subpasses"
        camera.apply {
            type = Camera.CameraType.firstPerson
            movementSpeed = 5f
            setPosition(Vec3(-3.2f, 1f, 5.9f))
            setRotation(Vec3(0.5f, 210.05f, 0f))
            setPerspective(60f, size.aspect, 0.1f, 256f)
        }
        settings.overlay = false // TODO
    }

    override fun destroy() {

        device.apply {
            // Clean up used Vulkan resources
            // Note : Inherited destructor cleans up resources stored in base class

            destroyImageView(attachments.position.view)
            destroyImage(attachments.position.image)
            freeMemory(attachments.position.mem)

            destroyImageView(attachments.normal.view)
            destroyImage(attachments.normal.image)
            freeMemory(attachments.normal.mem)

            destroyImageView(attachments.albedo.view)
            destroyImage(attachments.albedo.image)
            freeMemory(attachments.albedo.mem)

            destroyPipeline(pipelines.offscreen)
            destroyPipeline(pipelines.composition)
            destroyPipeline(pipelines.transparent)

            destroyPipelineLayout(pipelineLayouts.offscreen)
            destroyPipelineLayout(pipelineLayouts.composition)
            destroyPipelineLayout(pipelineLayouts.transparent)

            destroyDescriptorSetLayout(descriptorSetLayouts.scene)
            destroyDescriptorSetLayout(descriptorSetLayouts.composition)
            destroyDescriptorSetLayout(descriptorSetLayouts.transparent)
        }
        textures.glass.destroy()
        models.scene.destroy()
        models.transparent.destroy()
        uniformBuffers.GBuffer.destroy()
        uniformBuffers.lights.destroy()

        super.destroy()
    }

    /** Enable physical device features required for this example */
    override fun getEnabledFeatures() {
        // Enable anisotropic filtering if supported
        if (deviceFeatures.samplerAnisotropy)
            enabledFeatures.samplerAnisotropy = true
        // Enable texture compression
        when {
            deviceFeatures.textureCompressionBC -> enabledFeatures.textureCompressionBC = true
            deviceFeatures.textureCompressionASTC_LDR -> enabledFeatures.textureCompressionASTC_LDR = true
            deviceFeatures.textureCompressionETC2 -> enabledFeatures.textureCompressionETC2 = true
        }
    }

    /** Create a frame buffer attachment */
    fun createAttachment(format: VkFormat, usage: VkImageUsageFlags, attachment: FrameBufferAttachment) {

        var aspectMask: VkImageAspectFlags = 0
        var imageLayout = VkImageLayout.UNDEFINED

        attachment.format = format

        if (usage has VkImageUsage.COLOR_ATTACHMENT_BIT) {
            aspectMask = VkImageAspect.COLOR_BIT.i
            imageLayout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
        }
        if (usage has VkImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT) {
            aspectMask = VkImageAspect.DEPTH_BIT or VkImageAspect.STENCIL_BIT
            imageLayout = VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL
        }

        assert(aspectMask > 0)

        val image = vk.ImageCreateInfo {
            imageType = VkImageType.`2D`
            this.format = format
            extent(size, 1)
            mipLevels = 1
            arrayLayers = 1
            samples = VkSampleCount.`1_BIT`
            tiling = VkImageTiling.OPTIMAL
            this.usage = usage or VkImageUsage.INPUT_ATTACHMENT_BIT    // VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT flag is required for input attachments;
            initialLayout = VkImageLayout.UNDEFINED
        }

        attachment.image = device createImage image
        val memReqs = device getImageMemoryRequirements attachment.image
        val memAlloc = vk.MemoryAllocateInfo {
            allocationSize = memReqs.size
            memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
        }
        attachment.mem = device allocateMemory memAlloc
        device.bindImageMemory(attachment.image, attachment.mem)

        val imageView = vk.ImageViewCreateInfo {
            viewType = VkImageViewType.`2D`
            this.format = format
            subresourceRange.apply {
                this.aspectMask = aspectMask
                baseMipLevel = 0
                levelCount = 1
                baseArrayLayer = 0
                layerCount = 1
            }
            this.image = attachment.image
        }
        attachment.view = device createImageView imageView
    }

    /** Create color attachments for the G-Buffer components */
    fun createGBufferAttachments() {
        createAttachment(VkFormat.R16G16B16A16_SFLOAT, VkImageUsage.COLOR_ATTACHMENT_BIT.i, attachments.position)   // (World space) Positions
        createAttachment(VkFormat.R16G16B16A16_SFLOAT, VkImageUsage.COLOR_ATTACHMENT_BIT.i, attachments.normal)     // (World space) Normals
        createAttachment(VkFormat.R8G8B8A8_UNORM, VkImageUsage.COLOR_ATTACHMENT_BIT.i, attachments.albedo)          // Albedo (color)
    }

    /** Override framebuffer setup from base class
     *  Deferred components will be used as frame buffer attachments */
    override fun setupFrameBuffer() = stak {

        val attachments = it.vkImageViewBufferBig(5)

        val frameBufferCreateInfo = vk.FramebufferCreateInfo {
            renderPass = this@Subpasses.renderPass
            this.attachments = attachments
            width = size.x
            height = size.y
            layers = 1
        }
        // Create frame buffers for every swap chain image
        frameBuffers = initVkFramebufferArray(swapChain.imageCount) { i ->
            attachments[0] = swapChain.buffers[i].view // TODO put(vararg)?
            attachments[1] = Subpasses.attachments.position.view
            attachments[2] = Subpasses.attachments.normal.view
            attachments[3] = Subpasses.attachments.albedo.view
            attachments[4] = depthStencil.view
            device createFramebuffer frameBufferCreateInfo
        }
    }

    /** Override render pass setup from base class */
    override fun setupRenderPass() {

        createGBufferAttachments()

        val attachments = vk.AttachmentDescription(5).also {
            // Color attachment
            it[0].apply {
                format = swapChain.colorFormat
                samples = VkSampleCount.`1_BIT`
                loadOp = VkAttachmentLoadOp.CLEAR
                storeOp = VkAttachmentStoreOp.STORE
                stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
                stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
                initialLayout = VkImageLayout.UNDEFINED
                finalLayout = VkImageLayout.PRESENT_SRC_KHR
            }
            // Deferred attachments
            // Position
            it[1].apply {
                format = Subpasses.attachments.position.format
                samples = VkSampleCount.`1_BIT`
                loadOp = VkAttachmentLoadOp.CLEAR
                storeOp = VkAttachmentStoreOp.DONT_CARE
                stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
                stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
                initialLayout = VkImageLayout.UNDEFINED
                finalLayout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            }
            // Normals
            it[2].apply {
                format = Subpasses.attachments.normal.format
                samples = VkSampleCount.`1_BIT`
                loadOp = VkAttachmentLoadOp.CLEAR
                storeOp = VkAttachmentStoreOp.DONT_CARE
                stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
                stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
                initialLayout = VkImageLayout.UNDEFINED
                finalLayout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            }
            // Albedo
            it[3].apply {
                format = Subpasses.attachments.albedo.format
                samples = VkSampleCount.`1_BIT`
                loadOp = VkAttachmentLoadOp.CLEAR
                storeOp = VkAttachmentStoreOp.DONT_CARE
                stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
                stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
                initialLayout = VkImageLayout.UNDEFINED
                finalLayout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
            }
            // Depth attachment
            it[4].apply {
                format = depthFormat
                samples = VkSampleCount.`1_BIT`
                loadOp = VkAttachmentLoadOp.CLEAR
                storeOp = VkAttachmentStoreOp.DONT_CARE
                stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
                stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
                initialLayout = VkImageLayout.UNDEFINED
                finalLayout = VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL
            }
        }
        // Three subpasses
        val subpassDescriptions = vk.SubpassDescription(3)

        // First subpass: Fill G-Buffer components
        // ----------------------------------------------------------------------------------------

        val colorReferences = vk.AttachmentReference(
                0, VkImageLayout.COLOR_ATTACHMENT_OPTIMAL,
                1, VkImageLayout.COLOR_ATTACHMENT_OPTIMAL,
                2, VkImageLayout.COLOR_ATTACHMENT_OPTIMAL,
                3, VkImageLayout.COLOR_ATTACHMENT_OPTIMAL)
        val depthReference = vk.AttachmentReference(4, VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL)

        subpassDescriptions[0].apply {
            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
            colorAttachmentCount = 4
            colorAttachments = colorReferences
            depthStencilAttachment = depthReference
        }
        // Second subpass: Final composition (using G-Buffer components)
        // ----------------------------------------------------------------------------------------

        val colorReference = vk.AttachmentReference(0, VkImageLayout.COLOR_ATTACHMENT_OPTIMAL)

        val inputReferences = vk.AttachmentReference(
                1, VkImageLayout.SHADER_READ_ONLY_OPTIMAL,
                2, VkImageLayout.SHADER_READ_ONLY_OPTIMAL,
                3, VkImageLayout.SHADER_READ_ONLY_OPTIMAL)

        subpassDescriptions[1].apply {
            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
            colorAttachmentCount = 1
            colorAttachment = colorReference
            depthStencilAttachment = depthReference
            // Use the color attachments filled in the first pass as input attachments
            inputAttachments = inputReferences
        }
        // Third subpass: Forward transparency
        // ----------------------------------------------------------------------------------------
        colorReference(0, VkImageLayout.COLOR_ATTACHMENT_OPTIMAL)

        inputReferences[0](1, VkImageLayout.SHADER_READ_ONLY_OPTIMAL)

        subpassDescriptions[2].apply {
            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
            colorAttachmentCount = 1
            colorAttachment = colorReference
            depthStencilAttachment = depthReference
            // Use the color/depth attachments filled in the first pass as input attachments
            inputAttachments = inputReferences
        }
        // Subpass dependencies for layout transitions
        val dependencies = vk.SubpassDependency(4).also {
            it[0].apply {
                srcSubpass = VK_SUBPASS_EXTERNAL
                dstSubpass = 0
                srcStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
                dstStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
                srcAccessMask = VkAccess.MEMORY_READ_BIT.i
                dstAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
                dependencyFlags = VkDependency.BY_REGION_BIT.i
            }
            // This dependency transitions the input attachment from color attachment to shader read
            it[1].apply {
                srcSubpass = 0
                dstSubpass = 1
                srcStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
                dstStageMask = VkPipelineStage.FRAGMENT_SHADER_BIT.i
                srcAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                dstAccessMask = VkAccess.SHADER_READ_BIT.i
                dependencyFlags = VkDependency.BY_REGION_BIT.i
            }
            it[2].apply {
                srcSubpass = 1
                dstSubpass = 2
                srcStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
                dstStageMask = VkPipelineStage.FRAGMENT_SHADER_BIT.i
                srcAccessMask = VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i
                dstAccessMask = VkAccess.SHADER_READ_BIT.i
                dependencyFlags = VkDependency.BY_REGION_BIT.i
            }
            it[3].apply {
                srcSubpass = 0
                dstSubpass = VK_SUBPASS_EXTERNAL
                srcStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
                dstStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
                srcAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
                dstAccessMask = VkAccess.MEMORY_READ_BIT.i
                dependencyFlags = VkDependency.BY_REGION_BIT.i
            }
        }
        val renderPassInfo = vk.RenderPassCreateInfo {
            this.attachments = attachments
            subpasses = subpassDescriptions
            this.dependencies = dependencies
        }
        renderPass = device createRenderPass renderPassInfo
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(5).also {
            it[0].color(0f, 0f, 0f, 0f)
            it[1].color(0f, 0f, 0f, 0f)
            it[2].color(0f, 0f, 0f, 0f)
            it[3].color(0f, 0f, 0f, 0f)
            it[4].depthStencil(1f, 0)
        }
        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@Subpasses.renderPass
            renderArea.offset(0)
            renderArea.extent(size)
            this.clearValues = clearValues
        }
        for (i in drawCmdBuffers.indices) {
            // Set target frame buffer
            renderPassBeginInfo.framebuffer(frameBuffers[i].L) // TODO BUG

            drawCmdBuffers[i].apply {

                begin(cmdBufInfo)

                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                setViewport(size)
                setScissor(size)

                // First sub pass
                // Renders the components of the scene to the G-Buffer atttachments
                debugMarker.withRegion(this, "Subpass 0: Deferred G-Buffer creation", Vec4(1f)) {

                    bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.offscreen)
                    bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayouts.offscreen, descriptorSets.scene)
                    bindVertexBuffers(VERTEX_BUFFER_BIND_ID, models.scene.vertices.buffer)
                    bindIndexBuffer(models.scene.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
                    drawIndexed(models.scene.indexCount, 1, 0, 0, 0)
                }

                // Second sub pass
                // This subpass will use the G-Buffer components that have been filled in the first subpass as input attachment for the final compositing
                debugMarker.withRegion(this, "Subpass 1: Deferred composition", Vec4(1f)) {

                    nextSubpass(VkSubpassContents.INLINE)

                    bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.composition)
                    bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayouts.composition, descriptorSets.composition)
                    draw(3, 1, 0, 0)
                }

                // Third subpass
                // Render transparent geometry using a forward pass that compares against depth generted during G-Buffer fill
                debugMarker.withRegion(this, "Subpass 2: Forward transparency", Vec4(1f)) {

                    nextSubpass(VkSubpassContents.INLINE)

                    bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.transparent)
                    bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayouts.transparent, descriptorSets.transparent)
                    bindVertexBuffers(VERTEX_BUFFER_BIND_ID, models.transparent.vertices.buffer)
                    bindIndexBuffer(models.transparent.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
                    drawIndexed(models.transparent.indexCount, 1, 0, 0, 0)
                }

                endRenderPass()

                end()
            }
        }
    }

    fun loadAssets() {

        models.scene.loadFromFile("$assetPath/models/samplebuilding.dae", vertexLayout, 1f, vulkanDevice, queue)
        models.transparent.loadFromFile("$assetPath/models/samplebuilding_glass.dae", vertexLayout, 1f, vulkanDevice, queue)
        // Textures
        val (text, format) = when {
            vulkanDevice.features.textureCompressionBC -> "bc3_unorm.ktx" to VkFormat.BC3_UNORM_BLOCK
            vulkanDevice.features.textureCompressionASTC_LDR -> "astc_8x8_unorm.ktx" to VkFormat.ASTC_8x8_UNORM_BLOCK
            vulkanDevice.features.textureCompressionETC2 -> "etc2_unorm.ktx" to VkFormat.ETC2_R8G8B8A8_UNORM_BLOCK
            else -> tools.exitFatal("Device does not support any compressed texture format!", ERROR_FEATURE_NOT_PRESENT)
        }
        textures.glass.loadFromFile("$assetPath/textures/colored_glass_$text", format, vulkanDevice, queue)
    }

    fun setupVertexDescriptions() {
        // Binding description
        vertices.bindingDescriptions = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, vertexLayout.stride, VkVertexInputRate.VERTEX)

        // Attribute descriptions
        vertices.attributeDescriptions = vk.VertexInputAttributeDescription(
                // Location 0: Position
                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, 0,
                // Location 1: Color
                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32B32_SFLOAT, Vec3.size,
                // Location 2: Normal
                VERTEX_BUFFER_BIND_ID, 2, VkFormat.R32G32B32_SFLOAT, Vec3.size * 2,
                // Location 3: UV
                VERTEX_BUFFER_BIND_ID, 3, VkFormat.R32G32_SFLOAT, Vec3.size * 3)

        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertices.bindingDescriptions
            vertexAttributeDescriptions = vertices.attributeDescriptions
        }
    }

    fun setupDescriptorPool() {

        val poolSizes = vk.DescriptorPoolSize(
                VkDescriptorType.UNIFORM_BUFFER, 4,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 4,
                VkDescriptorType.INPUT_ATTACHMENT, 4)

        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 4)

        descriptorPool = device createDescriptorPool descriptorPoolInfo
    }

    fun setupDescriptorSetLayout() {
        // Deferred shading layout
        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
                // Binding 0 : Vertex shader uniform buffer
                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)

        descriptorSetLayouts.scene = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayouts.scene)

        // Offscreen (scene) rendering pipeline layout
        pipelineLayouts.offscreen = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayouts.scene)

        descriptorSets.scene = device allocateDescriptorSets allocInfo

        val writeDescriptorSets = vk.WriteDescriptorSet(
                // Binding 0: Vertex shader uniform buffer
                descriptorSets.scene, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.GBuffer.descriptor)

        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.BACK_BIT.i, VkFrontFace.CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Final fullscreen pass pipeline
        val shaderStages = vk.PipelineShaderStageCreateInfo(2)

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayouts.offscreen, renderPass).also {
            it.vertexInputState = vertices.inputState
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
            it.subpass = 0
        }
        val blendAttachmentStates = vk.PipelineColorBlendAttachmentState(
                0xf, false,
                0xf, false,
                0xf, false,
                0xf, false)

        colorBlendState.attachments = blendAttachmentStates

        // Offscreen scene rendering pipeline
        shaderStages[0].loadShader("$assetPath/shaders/subpasses/gbuffer.vert.spv", VkShaderStage.VERTEX_BIT)
        shaderStages[1].loadShader("$assetPath/shaders/subpasses/gbuffer.frag.spv", VkShaderStage.FRAGMENT_BIT)

        pipelines.offscreen = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    /** Create the Vulkan objects used in the composition pass (descriptor sets, pipelines, etc.) */
    fun prepareCompositionPass() = stak {
        // Descriptor set layout
        var setLayoutBindings = vk.DescriptorSetLayoutBinding(
                // Binding 0: Position input attachment
                VkDescriptorType.INPUT_ATTACHMENT, VkShaderStage.FRAGMENT_BIT.i, 0,
                // Binding 1: Normal input attachment
                VkDescriptorType.INPUT_ATTACHMENT, VkShaderStage.FRAGMENT_BIT.i, 1,
                // Binding 2: Albedo input attachment
                VkDescriptorType.INPUT_ATTACHMENT, VkShaderStage.FRAGMENT_BIT.i, 2,
                // Binding 3: Light positions
                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.FRAGMENT_BIT.i, 3)

        var descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)

        descriptorSetLayouts.composition = device createDescriptorSetLayout descriptorLayout

        // Pipeline layout
        var pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayouts.composition)

        pipelineLayouts.composition = device createPipelineLayout pipelineLayoutCreateInfo

        // Descriptor sets
        var allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayouts.composition)

        descriptorSets.composition = device allocateDescriptorSets allocInfo

        // Image descriptors for the offscreen color attachments
        val nullSampler = VkSampler(NULL)

        val texDescriptorPosition = vk.DescriptorImageInfo(nullSampler, attachments.position.view, VkImageLayout.SHADER_READ_ONLY_OPTIMAL)

        val texDescriptorNormal = vk.DescriptorImageInfo(nullSampler, attachments.normal.view, VkImageLayout.SHADER_READ_ONLY_OPTIMAL)

        val texDescriptorAlbedo = vk.DescriptorImageInfo(nullSampler, attachments.albedo.view, VkImageLayout.SHADER_READ_ONLY_OPTIMAL)

        var writeDescriptorSets = vk.WriteDescriptorSet(
                // Binding 0: Position texture target
                descriptorSets.composition, VkDescriptorType.INPUT_ATTACHMENT, 0, texDescriptorPosition,
                // Binding 1: Normals texture target
                descriptorSets.composition, VkDescriptorType.INPUT_ATTACHMENT, 1, texDescriptorNormal,
                // Binding 2: Albedo texture target
                descriptorSets.composition, VkDescriptorType.INPUT_ATTACHMENT, 2, texDescriptorAlbedo,
                // Binding 4: Fragment shader lights
                descriptorSets.composition, VkDescriptorType.UNIFORM_BUFFER, 3, uniformBuffers.lights.descriptor)

        device updateDescriptorSets writeDescriptorSets

        // Pipeline
        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.NONE.i, VkFrontFace.CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
            it[0].loadShader("$assetPath/shaders/subpasses/composition.vert.spv", VkShaderStage.VERTEX_BIT)
            it[1].loadShader("$assetPath/shaders/subpasses/composition.frag.spv", VkShaderStage.FRAGMENT_BIT)
        }

        // Use specialization constants to pass number of lights to the shader
        val specializationEntry = vk.SpecializationMapEntry {
            constantId = 0
            offset = 0
            size = Int.BYTES.L
        }
        val specializationData = it.malloc(Int.BYTES).apply { putInt(0, NUM_LIGHTS) }

        val specializationInfo = vk.SpecializationInfo {
            mapEntry = specializationEntry
            data = specializationData
        }
        shaderStages[1].specializationInfo = specializationInfo

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayouts.composition, renderPass).also {

            val emptyInputState = vk.PipelineVertexInputStateCreateInfo()

            it.vertexInputState = emptyInputState
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
            // Index of the subpass that this pipeline will be used in
            it.subpass = 1
        }
        depthStencilState.depthWriteEnable = false

        pipelines.composition = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)

        // Transparent (forward) pipeline

        // Descriptor set layout
        setLayoutBindings = vk.DescriptorSetLayoutBinding(
                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0,
                VkDescriptorType.INPUT_ATTACHMENT, VkShaderStage.FRAGMENT_BIT.i, 1,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 2)

        descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)
        descriptorSetLayouts.transparent = device createDescriptorSetLayout descriptorLayout

        // Pipeline layout
        pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayouts.transparent)
        pipelineLayouts.transparent = device createPipelineLayout pipelineLayoutCreateInfo

        // Descriptor sets
        allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayouts.transparent)
        descriptorSets.transparent = device allocateDescriptorSets allocInfo

        writeDescriptorSets = vk.WriteDescriptorSet(
                descriptorSets.transparent, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.GBuffer.descriptor,
                descriptorSets.transparent, VkDescriptorType.INPUT_ATTACHMENT, 1, texDescriptorPosition,
                descriptorSets.transparent, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 2, textures.glass.descriptor)
        device updateDescriptorSets writeDescriptorSets

        // Enable blending
        blendAttachmentState.apply {
            blendEnable = true
            srcColorBlendFactor = VkBlendFactor.SRC_ALPHA
            dstColorBlendFactor = VkBlendFactor.ONE_MINUS_SRC_ALPHA
            colorBlendOp = VkBlendOp.ADD
            srcAlphaBlendFactor = VkBlendFactor.ONE
            dstAlphaBlendFactor = VkBlendFactor.ZERO
            alphaBlendOp = VkBlendOp.ADD
            colorWriteMask = VkColorComponent.R_BIT or VkColorComponent.G_BIT or VkColorComponent.B_BIT or VkColorComponent.A_BIT
        }
        pipelineCreateInfo.apply {
            vertexInputState = vertices.inputState
            layout = pipelineLayouts.transparent
            subpass = 2
        }
        shaderStages[0].loadShader("$assetPath/shaders/subpasses/transparent.vert.spv", VkShaderStage.VERTEX_BIT)
        shaderStages[1].loadShader("$assetPath/shaders/subpasses/transparent.frag.spv", VkShaderStage.FRAGMENT_BIT)

        pipelines.transparent = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    /** Prepare and initialize uniform buffer containing shader uniforms */
    fun prepareUniformBuffers() {
        // Deferred vertex shader
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBuffers.GBuffer,
                VkDeviceSize(uboGBuffer.size.L))

        // Deferred fragment shader
        TODO()
//        vulkanDevice.createBuffer(
//                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
//                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
//                uniformBuffers.lights,
//                uboLights.size)

        // Update
        updateUniformBufferDeferredMatrices()
        updateUniformBufferDeferredLights()
    }

    fun updateUniformBufferDeferredMatrices() {

        uboGBuffer.projection = camera.matrices.perspective
        uboGBuffer.view = camera.matrices.view
        uboGBuffer.model put 1f

        uniformBuffers.GBuffer.mapping { mapped -> uboGBuffer to mapped }
    }

    fun initLights() {

        val colors = arrayOf(
                Vec3(1f, 1f, 1f),
                Vec3(1f, 0f, 0f),
                Vec3(0f, 1f, 0f),
                Vec3(0f, 0f, 1f),
                Vec3(1f, 1f, 0f))

        fun rndDist() = glm.linearRand(-1f, 1f)
        fun rndCol() = glm.linearRand(0, colors.lastIndex)

        for (light in uboLights.lights) {
            light.position.put(rndDist() * 6f, 0.25f + abs(rndDist()) * 4f, rndDist() * 6f, 1f)
            light.color put colors[rndCol()]
            light.radius = 1f + abs(rndDist())
        }
    }

    /** Update fragment shader light position uniform block */
    fun updateUniformBufferDeferredLights() {
        // Current view position
        uboLights.viewPos = Vec4(camera.position, 0f) * Vec4(-1f, 1f, -1f, 1f)

        uniformBuffers.lights.mapping { mapped -> uboLights to mapped }
    }

    fun draw() {

        super.prepareFrame()

        // Command buffer to be sumitted to the queue
        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]

        // Submit to queue
        queue submit submitInfo

        super.submitFrame()
    }

    override fun prepare() {
        super.prepare()
        loadAssets()
        setupVertexDescriptions()
        initLights()
        prepareUniformBuffers()
        setupDescriptorSetLayout()
        preparePipelines()
        setupDescriptorPool()
        setupDescriptorSet()
        prepareCompositionPass()
        buildCommandBuffers()
        prepared = true
        window.show()
    }

    override fun render() {
        if (!prepared)
            return
        draw()
    }

    override fun viewChanged() {
        updateUniformBufferDeferredMatrices()
        updateUniformBufferDeferredLights()
    }

    // UI overlay configuration needs to be adjusted for this example (renderpass setup, attachment count, etc.)
//    virtual void OnSetupUIOverlay(vks::UIOverlayCreateInfo &createInfo)
//    {
//        createInfo.targetSubpass = 2
//    }
//
//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
//    {
//        if (overlay->header("Subpasses")) { overlay ->
//        text("0: Deferred G-Buffer creation")
//        overlay->text("1: Deferred composition")
//        overlay->text("2: Forward transparency")
//    }
//        if (overlay->header("Settings")) {
//        if (overlay->button("Randomize lights")) {
//        initLights()
//        updateUniformBufferDeferredLights()
//    }
//    }
//    }
}