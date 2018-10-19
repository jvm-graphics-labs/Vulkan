/*
* Vulkan Example - Texture loading (and display) example (including mip maps)
*
* Copyright (C) 2016-2017 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.basics

import gli_.gli
import glm_.L
import glm_.f
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec2.Vec2i
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import kool.adr
import kool.cap
import kool.stak
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memCopy
import org.lwjgl.vulkan.*
import vkk.*
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.assetPath
import vulkan.base.Buffer
import vulkan.base.VulkanExampleBase
import vulkan.base.initializers


fun main(args: Array<String>) {
    Texture().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class Texture : VulkanExampleBase() {

    /** Vertex layout for this example */
    object Vertex {
        //    float pos[3];
//    float uv[2];
//    float normal[3];
        val size = Vec3.size * 2 + Vec2.size
        val posOffset = 0
        val uvOffset = Vec3.size
        val normalOffset = Vec3.size + Vec2.size
    }

    /** Contains all Vulkan objects that are required to store and use a texture
     *  Note that this repository contains a texture class (VulkanTexture.hpp) that encapsulates texture loading functionality
     *  in a class that is used in subsequent demos */
    object texture {
        var sampler = VkSampler(NULL)
        var image = VkImage(NULL)
        var imageLayout = VkImageLayout.UNDEFINED
        var deviceMemory = VkDeviceMemory(NULL)
        var view = VkImageView(NULL)
        val size = Vec2i()
        var mipLevels = 0
    }

    object vertices {
        val inputState = cVkPipelineVertexInputStateCreateInfo { }
        lateinit var bindingDescriptions: VkVertexInputBindingDescription.Buffer
        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
    }

    val vertexBuffer = Buffer()
    val indexBuffer = Buffer()
    var indexCount = 0

    val uniformBufferVS = Buffer()

    object uboVS : Bufferizable() {

        val projection = Mat4()
        @Order(1)
        val model = Mat4()
        val viewPos = Vec4()
        @Order(3)
        var lodBias = 0f
    }

    object pipelines {
        var solid = VkPipeline(NULL)
    }

    var pipelineLayout = VkPipelineLayout(NULL)
    var descriptorSet = VkDescriptorSet(NULL)
    var descriptorSetLayout = VkDescriptorSetLayout(NULL)

    init {
        zoom = -2.5f
        rotation(0f, 15f, 0f)
        title = "Texture loading"
//        settings.overlay = true
    }

    override fun destroy() {
        // Clean up used Vulkan resources
        // Note : Inherited destructor cleans up resources stored in base class

        destroyTextureImage()

        device.apply {
            destroyPipeline(pipelines.solid)

            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }

        vertexBuffer.destroy()
        indexBuffer.destroy()
        uniformBufferVS.destroy()

        super.destroy()
    }

    /** Enable physical device features required for this example */
    override fun getEnabledFeatures() {
        // Enable anisotropic filtering if supported
        if (deviceFeatures.samplerAnisotropy)
            enabledFeatures.samplerAnisotropy = true
    }

    /*  Upload texture image data to the GPU

        Vulkan offers two types of image tiling (memory layout):

        Linear tiled images:
            These are stored as is and can be copied directly to. But due to the linear nature they're not a good match
            for GPUs and format and feature support is very limited.
            It's not advised to use linear tiled images for anything else than copying from host to GPU if buffer copies
            are not an option.
            Linear tiling is thus only implemented for learning purposes, one should always prefer optimal tiled image.

        Optimal tiled images:
            These are stored in an implementation specific layout matching the capability of the hardware.
            They usually support more formats and features and are much faster.
            Optimal tiled images are stored on the device and not accessible by the host. So they can't be written
            directly to (like liner tiled images) and always require some sort of data copy, either from a buffer or
            a linear tiled image.

        In Short: Always use optimal tiled images for rendering.    */

    fun loadTexture() {
        // We use the Khronos texture format (https://www.khronos.org/opengles/sdk/tools/KTX/file_format_spec/)
        val filename = "$assetPath/textures/metalplate01_rgba.ktx"
        // Texture data contains 4 channels (RGBA) with unnormalized 8-bit values, this is the most commonly supported format
        val format = VkFormat.R8G8B8A8_UNORM

        val tex2D = gli_.Texture2d(gli.load(filename))

        assert(tex2D.notEmpty())

        texture.size(tex2D[0].extent())
        texture.mipLevels = tex2D.levels()

        // We prefer using staging to copy the texture data to a device local optimal image
        var useStaging = true

        // Only use linear tiling if forced
        val forceLinearTiling = false
        if (forceLinearTiling) {
            // Don't use linear if format is not supported for (linear) shader sampling
            // Get device properites for the requested texture format
            val formatProperties = physicalDevice getFormatProperties format
            useStaging = formatProperties.linearTilingFeatures hasnt VkFormatFeature.SAMPLED_IMAGE_BIT
        }
        val memAllocInfo = vk.MemoryAllocateInfo { }
        val memReqs = vk.MemoryRequirements { }

        if (useStaging) {

            // Copy data to an optimal tiled image
            // This loads the texture data into a host local buffer that is copied to the optimal tiled image on the device

            // Create a host-visible staging buffer that contains the raw image data
            // This buffer will be the data source for copying texture data to the optimal tiled image on the device

            val bufferCreateInfo = vk.BufferCreateInfo {
                size = VkDeviceSize(tex2D.size.L)
                // This buffer is used as a transfer source for the buffer copy
                usage = VkBufferUsage.TRANSFER_SRC_BIT.i
                sharingMode = VkSharingMode.EXCLUSIVE
            }

            val stagingBuffer: VkBuffer = device createBuffer bufferCreateInfo

            // Get memory requirements for the staging buffer (alignment, memory type bits)
            device.getBufferMemoryRequirements(stagingBuffer, memReqs)
            memAllocInfo.apply {
                allocationSize = memReqs.size
                // Get memory type index for a host visible buffer
                memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT)
            }
            val stagingMemory = device allocateMemory memAllocInfo
            device.bindBufferMemory(stagingBuffer, stagingMemory)

            // Copy texture data into staging buffer
            val data = device.mapMemory(stagingMemory, VkDeviceSize(0), memReqs.size)
            memCopy(tex2D.data().adr, data, tex2D.size.L)
            device unmapMemory stagingMemory

            // Setup buffer copy regions for each mip level
            val bufferCopyRegions = VkBufferImageCopy.calloc(texture.mipLevels)
            var offset = VkDeviceSize(0)

            bufferCopyRegions.forEachIndexed { i, it ->
                it.imageSubresource.apply {
                    aspectMask = VkImageAspect.COLOR_BIT.i
                    mipLevel = i
                    baseArrayLayer = 0
                    layerCount = 1
                }
                val (w, h) = tex2D[i].extent()
                it.imageExtent.width = w // TODO BUG
                it.imageExtent.height = h
                it.imageExtent.depth = 1
                it.bufferOffset = offset

                offset += tex2D[i].size
            }

            // Create optimal tiled target image on the device
            val imageCreateInfo = vk.ImageCreateInfo {
                imageType = VkImageType.`2D`
                this.format = format
                mipLevels = texture.mipLevels
                arrayLayers = 1
                samples = VkSampleCount.`1_BIT`
                tiling = VkImageTiling.OPTIMAL
                sharingMode = VkSharingMode.EXCLUSIVE
                // Set initial layout of the image to undefined
                initialLayout = VkImageLayout.UNDEFINED
                extent.set(texture.size.x, texture.size.y, 1)
                usage = VkImageUsage.TRANSFER_DST_BIT or VkImageUsage.SAMPLED_BIT
            }
            texture.image = device createImage imageCreateInfo
            device.getImageMemoryRequirements(texture.image, memReqs)
            memAllocInfo.apply {
                allocationSize = memReqs.size
                memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
            }
            texture.deviceMemory = device allocateMemory memAllocInfo
            device.bindImageMemory(texture.image, texture.deviceMemory)

            val copyCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)

            // Image memory barriers for the texture image

            // The sub resource range describes the regions of the image that will be transitioned using the memory barriers below
            val subresourceRange = VkImageSubresourceRange.calloc().apply {
                // Image only contains color data
                aspectMask = VkImageAspect.COLOR_BIT.i
                // Start at first mip level
                baseMipLevel = 0
                // We will transition on all mip levels
                levelCount = texture.mipLevels
                // The 2D texture only has one layer
                layerCount = 1
            }

            // Transition the texture image layout to transfer target, so we can safely copy our buffer data to it.
            val imageMemoryBarrier = vk.ImageMemoryBarrier {
                image = texture.image
                this.subresourceRange = subresourceRange
                srcAccessMask = 0
                dstAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                oldLayout = VkImageLayout.UNDEFINED
                newLayout = VkImageLayout.TRANSFER_DST_OPTIMAL
            }
            // Insert a memory dependency at the proper pipeline stages that will execute the image layout transition
            // Source pipeline stage is host write/read exection (VK_PIPELINE_STAGE_HOST_BIT)
            // Destination pipeline stage is copy command exection (VK_PIPELINE_STAGE_TRANSFER_BIT)
            copyCmd.pipelineBarrier(
                    VkPipelineStage.HOST_BIT,
                    VkPipelineStage.TRANSFER_BIT,
                    imageMemoryBarrier = imageMemoryBarrier)

            // Copy mip levels from staging buffer
            copyCmd.copyBufferToImage(
                    stagingBuffer,
                    texture.image,
                    VkImageLayout.TRANSFER_DST_OPTIMAL,
                    bufferCopyRegions)

            // Once the data has been uploaded we transfer to the texture image to the shader read layout, so it can be sampled from
            imageMemoryBarrier.apply {
                srcAccessMask = VkAccess.TRANSFER_WRITE_BIT.i
                dstAccessMask = VkAccess.SHADER_READ_BIT.i
                oldLayout = VkImageLayout.TRANSFER_DST_OPTIMAL
                newLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
            }
            // Insert a memory dependency at the proper pipeline stages that will execute the image layout transition
            // Source pipeline stage stage is copy command exection (VK_PIPELINE_STAGE_TRANSFER_BIT)
            // Destination pipeline stage fragment shader access (VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
            copyCmd.pipelineBarrier(
                    VkPipelineStage.TRANSFER_BIT,
                    VkPipelineStage.FRAGMENT_SHADER_BIT,
                    imageMemoryBarrier = imageMemoryBarrier)
            // Store current layout for later reuse
            texture.imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL

            super.flushCommandBuffer(copyCmd, queue, true)

            // Clean up staging resources
            device freeMemory stagingMemory
            device destroyBuffer stagingBuffer
        } else {

            // Copy data to a linear tiled image

//            VkImage mappableImage
//                    VkDeviceMemory mappableMemory
//
//                    // Load mip map level 0 to linear tiling image
//                    VkImageCreateInfo imageCreateInfo = vks ::initializers::imageCreateInfo()
//            imageCreateInfo.imageType = VK_IMAGE_TYPE_2D
//            imageCreateInfo.format = format
//            imageCreateInfo.mipLevels = 1
//            imageCreateInfo.arrayLayers = 1
//            imageCreateInfo.samples = VK_SAMPLE_COUNT_1_BIT
//            imageCreateInfo.tiling = VK_IMAGE_TILING_LINEAR
//            imageCreateInfo.usage = VK_IMAGE_USAGE_SAMPLED_BIT
//            imageCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE
//            imageCreateInfo.initialLayout = VK_IMAGE_LAYOUT_PREINITIALIZED
//            imageCreateInfo.extent = { texture.width, texture.height, 1 }
//            VK_CHECK_RESULT(vkCreateImage(device, & imageCreateInfo, nullptr, & mappableImage))
//
//            // Get memory requirements for this image
//            // like size and alignment
//            vkGetImageMemoryRequirements(device, mappableImage, & memReqs)
//            // Set memory allocation size to required memory size
//            memAllocInfo.allocationSize = memReqs.size
//
//            // Get memory type that can be mapped to host memory
//            memAllocInfo.memoryTypeIndex = vulkanDevice->getMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
//
//            // Allocate host memory
//            VK_CHECK_RESULT(vkAllocateMemory(device, & memAllocInfo, nullptr, & mappableMemory))
//
//            // Bind allocated image for use
//            VK_CHECK_RESULT(vkBindImageMemory(device, mappableImage, mappableMemory, 0))
//
//            // Get sub resource layout
//            // Mip map count, array layer, etc.
//            VkImageSubresource subRes = {}
//            subRes.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//
//            VkSubresourceLayout subResLayout
//                    void * data
//
//            // Get sub resources layout
//            // Includes row pitch, size offsets, etc.
//            vkGetImageSubresourceLayout(device, mappableImage, & subRes, &subResLayout)
//
//            // Map image memory
//            VK_CHECK_RESULT(vkMapMemory(device, mappableMemory, 0, memReqs.size, 0, & data))
//
//            // Copy image data into memory
//            memcpy(data, tex2D[subRes.mipLevel].data(), tex2D[subRes.mipLevel].size())
//
//            vkUnmapMemory(device, mappableMemory)
//
//            // Linear tiled images don't need to be staged
//            // and can be directly used as textures
//            texture.image = mappableImage
//            texture.deviceMemory = mappableMemory
//            texture.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
//
//            VkCommandBuffer copyCmd = VulkanExampleBase ::createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, true)
//
//            // Setup image memory barrier transfer image to shader read layout
//
//            // The sub resource range describes the regions of the image we will be transition
//            VkImageSubresourceRange subresourceRange = {}
//            // Image only contains color data
//            subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//            // Start at first mip level
//            subresourceRange.baseMipLevel = 0
//            // Only one mip level, most implementations won't support more for linear tiled images
//            subresourceRange.levelCount = 1
//            // The 2D texture only has one layer
//            subresourceRange.layerCount = 1
//
//            setImageLayout(
//                    copyCmd,
//                    texture.image,
//                    VK_IMAGE_ASPECT_COLOR_BIT,
//                    VK_IMAGE_LAYOUT_PREINITIALIZED,
//                    texture.imageLayout,
//                    subresourceRange)
//
//            VulkanExampleBase::flushCommandBuffer(copyCmd, queue, true)
        }

        // Create a texture sampler
        // In Vulkan textures are accessed by samplers
        // This separates all the sampling information from the texture data. This means you could have multiple sampler objects for the same texture with different settings
        // Note: Similar to the samplers available with OpenGL 3.3
        val sampler = vk.SamplerCreateInfo {
            minMagFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            addressModeUVW = VkSamplerAddressMode.REPEAT
            mipLodBias = 0f
            compareOp = VkCompareOp.NEVER
            minLod = 0f
            // Set max level-of-detail to mip level count of the texture
            maxLod = if (useStaging) texture.mipLevels.f else 0f
            // Enable anisotropic filtering
            // This feature is optional, so we must check if it's supported on the device
            if (vulkanDevice.features.samplerAnisotropy) {
                // Use max. level of anisotropy for this example
                maxAnisotropy = vulkanDevice.properties.limits.maxSamplerAnisotropy
                anisotropyEnable = true
            } else {
                // The device does not support anisotropic filtering
                maxAnisotropy = 1f
                anisotropyEnable = false
            }
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
        }
        texture.sampler = device createSampler sampler

        /*  Create image view
            Textures are not directly accessed by the shaders and are abstracted by image views containing additional
            information and sub resource ranges */
        val view = vk.ImageViewCreateInfo {
            viewType = VkImageViewType.`2D`
            this.format = format
            components(VkComponentSwizzle.R, VkComponentSwizzle.G, VkComponentSwizzle.B, VkComponentSwizzle.A)
            // The subresource range describes the set of mip levels (and array layers) that can be accessed through this image view
            // It's possible to create multiple image views for a single image referring to different (and/or overlapping) ranges of the image
            subresourceRange.apply {
                aspectMask = VkImageAspect.COLOR_BIT.i
                baseMipLevel = 0
                baseArrayLayer = 0
                layerCount = 1
                // Linear tiling usually won't support mip maps
                // Only set mip map count if optimal tiling is used
                levelCount = if (useStaging) texture.mipLevels else 1
            }
            // The view will be based on the texture's image
            image = texture.image
        }
        texture.view = device createImageView view
    }

    /** Free all Vulkan resources used by a texture object */
    fun destroyTextureImage() {
        device destroyImageView texture.view
        device destroyImage texture.image
        device destroySampler texture.sampler
        device freeMemory texture.deviceMemory
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo { }

        val clearValues = vk.ClearValue(2).also {
            it[0].color(defaultClearColor)
            it[1].depthStencil(1f, 0)
        }
        val (w, h) = size

        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@Texture.renderPass
            renderArea.apply {
                offset.set(0, 0)
                extent.set(w, h) // TODO BUG
            }
            this.clearValues = clearValues
        }

        drawCmdBuffers.forEachIndexed { i, it ->
            // Set target frame buffer
            renderPassBeginInfo.framebuffer(frameBuffers[i].L) // TODO BUG

            it begin cmdBufInfo

            it.beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

            it setViewport initializers.viewport(size, 0f, 1f)

            it setScissor vk.Rect2D(size)

            it.bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
            it.bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.solid)

            it.bindVertexBuffers(VERTEX_BUFFER_BIND_ID, vertexBuffer.buffer)
            it.bindIndexBuffer(indexBuffer.buffer, VkDeviceSize(0), VkIndexType.UINT32)

            it.drawIndexed(indexCount, 1, 0, 0, 0)

            it.drawUI()

            it.endRenderPass()

            it.end()
        }
    }

    fun draw() {

        super.prepareFrame()

        // Command buffer to be sumitted to the queue
        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]

        // Submit to queue
        queue submit submitInfo

        super.submitFrame()
    }

    fun generateQuad() = stak {
        // Setup vertices for a single uv-mapped quad made from two triangles
        val vertices = it.floats(
                +1f, +1f, 0f, 1f, 1f, 0f, 0f, 1f,
                -1f, +1f, 0f, 0f, 1f, 0f, 0f, 1f,
                -1f, -1f, 0f, 0f, 0f, 0f, 0f, 1f,
                +1f, -1f, 0f, 1f, 0f, 0f, 0f, 1f)

        // Setup indices
        val indices = it.ints(0, 1, 2, 2, 3, 0)
        indexCount = indices.cap

        // Create buffers
        // For the sake of simplicity we won't stage the vertex data to the gpu memory
        // Vertex buffer
        vulkanDevice.createBuffer(
                VkBufferUsage.VERTEX_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                vertexBuffer,
                vertices)
        // Index buffer
        vulkanDevice.createBuffer(
                VkBufferUsage.INDEX_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                indexBuffer,
                indices)
    }

    fun setupVertexDescriptions() {
        // Binding description
        vertices.bindingDescriptions = VkVertexInputBindingDescription.calloc(1)
        vertices.bindingDescriptions[0].apply {
            binding = VERTEX_BUFFER_BIND_ID
            stride = Vertex.size
            inputRate = VkVertexInputRate.VERTEX
        }

        // Attribute descriptions
        // Describes memory layout and shader positions
        vertices.attributeDescriptions = VkVertexInputAttributeDescription.calloc(3)
        // Location 0 : Position
        vertices.attributeDescriptions[0].apply {
            binding = VERTEX_BUFFER_BIND_ID
            location = 0
            format = VkFormat.R32G32B32_SFLOAT
            offset = Vertex.posOffset
        }
        // Location 1 : Texture coordinates
        vertices.attributeDescriptions[1].apply {
            binding = VERTEX_BUFFER_BIND_ID
            location = 1
            format = VkFormat.R32G32_SFLOAT
            offset = Vertex.uvOffset
        }
        // Location 1 : Vertex normal
        vertices.attributeDescriptions[2].apply {
            binding = VERTEX_BUFFER_BIND_ID
            location = 2
            format = VkFormat.R32G32B32_SFLOAT
            offset = Vertex.normalOffset
        }

        vertices.inputState.apply {
            vertexBindingDescriptions = vertices.bindingDescriptions
            vertexAttributeDescriptions = vertices.attributeDescriptions
        }
    }

    fun setupDescriptorPool() {
        // Example uses one ubo and one image sampler
        val poolSizes = VkDescriptorPoolSize.calloc(2).also {
            it[0](VkDescriptorType.UNIFORM_BUFFER, 1)
            it[1](VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1)
        }

        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo {
            this.poolSizes = poolSizes
            maxSets = 2
        }

        descriptorPool = device createDescriptorPool descriptorPoolInfo
    }

    fun setupDescriptorSetLayout() {

        val setLayoutBindings = VkDescriptorSetLayoutBinding.calloc(2).also {
            // Binding 0 : Vertex shader uniform buffer
            it[0](0, VkDescriptorType.UNIFORM_BUFFER, 1, VkShaderStage.VERTEX_BIT.i)
            // Binding 1 : Fragment shader image sampler
            it[1](1, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, VkShaderStage.FRAGMENT_BIT.i)
        }

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo {
            bindings = setLayoutBindings
        }

        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo {
            setLayout = descriptorSetLayout
        }

        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {

        val allocInfo = vk.DescriptorSetAllocateInfo {
            descriptorPool = this@Texture.descriptorPool
            setLayout = descriptorSetLayout
            descriptorSetCount = 1
        }

        descriptorSet = device allocateDescriptorSets allocInfo

        // Setup a descriptor image info for the current texture to be used as a combined image sampler
        val textureDescriptor = vk.DescriptorImageInfo(1) {
            // The image's view (images are never directly accessed by the shader, but rather through views defining subresources)
            imageView = texture.view
            // The sampler (Telling the pipeline how to sample the texture, including repeat, border, etc.)
            sampler = texture.sampler
            // The current layout of the image (Note: Should always fit the actual use, e.g. shader read)
            imageLayout = texture.imageLayout
        }

        val writeDescriptorSets = VkWriteDescriptorSet.calloc(2)
        // Binding 0 : Vertex shader uniform buffer
        writeDescriptorSets[0].apply {
            type = VkStructureType.WRITE_DESCRIPTOR_SET
            dstSet = descriptorSet
            descriptorType = VkDescriptorType.UNIFORM_BUFFER
            dstBinding = 0
            bufferInfo_ = uniformBufferVS.descriptor
        }
        // Binding 1 : Fragment shader texture sampler
        //	Fragment shader: layout (binding = 1) uniform sampler2D samplerColor;
        writeDescriptorSets[1].apply {
            type = VkStructureType.WRITE_DESCRIPTOR_SET
            dstSet = descriptorSet
            // The descriptor set will use a combined image sampler (sampler and image could be split)
            descriptorType = VkDescriptorType.COMBINED_IMAGE_SAMPLER
            dstBinding = 1                  // Shader binding point 1
            imageInfo = textureDescriptor   // Pointer to the descriptor image for our texture
        }

        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() = stak {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo {
            topology = VkPrimitiveTopology.TRIANGLE_LIST
        }

        val rasterizationState = initializers.pipelineRasterizationStateCreateInfo(
                VkPolygonMode.FILL,
                VkCullMode.NONE.i,
                VkFrontFace.COUNTER_CLOCKWISE,
                0)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState { colorWriteMask = 0xf }

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo { attachment = blendAttachmentState }

        val depthStencilState = initializers.pipelineDepthStencilStateCreateInfo(
                true,
                true,
                VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo {
            viewportCount = 1
            scissorCount = 1
        }

        val multisampleState = vk.PipelineMultisampleStateCreateInfo {
            rasterizationSamples = VkSampleCount.`1_BIT`
        }

        val dynamicStateEnables = it.vkDynamicStateBufferOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo {
            dynamicStates = dynamicStateEnables
        }

        // Load shaders
        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
            it[0].loadShader("$assetPath/shaders/texture/texture.vert", VkShaderStage.VERTEX_BIT)
            it[1].loadShader("$assetPath/shaders/texture/texture.frag", VkShaderStage.FRAGMENT_BIT)
        }

        val pipelineCreateInfo = initializers.pipelineCreateInfo(
                pipelineLayout,
                renderPass).apply {
            vertexInputState = vertices.inputState
            this.inputAssemblyState = inputAssemblyState
            this.rasterizationState = rasterizationState
            this.colorBlendState = colorBlendState
            this.multisampleState = multisampleState
            this.viewportState = viewportState
            this.depthStencilState = depthStencilState
            this.dynamicState = dynamicState
            stages = shaderStages
        }

        pipelines.solid = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    // Prepare and initialize uniform buffer containing shader uniforms
    fun prepareUniformBuffers() {
        // Vertex shader uniform buffer block
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBufferVS,
                VkDeviceSize(uboVS.size.L))

        updateUniformBuffers()
    }

    val viewMatrix = Mat4(1f).translateAssign(0f, 0f, zoom)
    fun updateUniformBuffers() {
        // Vertex shader
        glm.perspective(uboVS.projection, 60f.rad, size.aspect, 0.001f, 256f)

        uboVS.model.apply {
            put(viewMatrix * Mat4(1f).translateAssign(cameraPos))
            rotateAssign(rotation.x.rad, Vec3(1f, 0f, 0f))
            rotateAssign(rotation.y.rad, Vec3(0f, 1f, 0f))
            rotateAssign(rotation.z.rad, Vec3(0f, 0f, 1f))
        }

        uboVS.viewPos put Vec4(0f, 0f, -zoom, 0f)

        uniformBufferVS.mapping { dst -> uboVS to dst }
    }

    override fun prepare() {
        super.prepare()
        loadTexture()
        generateQuad()
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

//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
//    {
//        if (overlay->header("Settings")) {
//        if (overlay->sliderFloat("LOD bias", &uboVS.lodBias, 0.0f, (float)texture.mipLevels)) {
//        updateUniformBuffers()
//    }
//    }
//    }
}