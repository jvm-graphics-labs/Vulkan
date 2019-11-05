/*
* Vulkan texture loader
*
* Copyright(C) 2016-2017 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license(MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.base

import gli_.gli
import glm_.L
import glm_.f
import glm_.vec2.Vec2i
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkQueue
import vkk.*
import vkk.entities.*
import vkk.extensionFunctions.*
import java.io.File

/** @brief Vulkan texture base class */
open class Texture {

    lateinit var device: VulkanDevice
    var image = VkImage.NULL
    var imageLayout = VkImageLayout.UNDEFINED
    var deviceMemory = VkDeviceMemory.NULL
    var view = VkImageView.NULL
    val size = Vec2i()
    var mipLevels = 0
    var layerCount = 0
    val descriptor = VkDescriptorImageInfo.calloc()

    /** @brief Optional sampler to use with this texture */
    var sampler= VkSampler.NULL

    /** @brief Update image descriptor from current sampler, view and image layout */
    fun updateDescriptor() {
        descriptor.also {
            it.sampler = sampler
            it.imageView = view
            it.imageLayout = imageLayout
        }
    }

    /** @brief Release all Vulkan resources held by this texture */
    fun destroy() {
        device.logicalDevice!!.apply {
            destroyImageView(view)
            destroyImage(image)
            if (sampler.L != NULL)
                destroySampler(sampler)
            freeMemory(deviceMemory)
        }
    }
}

/** @brief 2D texture */
class Texture2D : Texture() {

    /**
     * Load a 2D texture including all mip levels
     *
     * @param filename File to load (supports .ktx and .dds)
     * @param format Vulkan format of the image data stored in the file
     * @param device Vulkan device to create the texture on
     * @param copyQueue Queue used for the texture staging copy commands (must support transfer)
     * @param (Optional) imageUsageFlags Usage flags for the texture's image (defaults to VK_IMAGE_USAGE_SAMPLED_BIT)
     * @param (Optional) imageLayout Usage layout for the texture (defaults VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
     * @param (Optional) forceLinear Force linear tiling (not advised, defaults to false)
     *
     */
    fun loadFromFile(
            filename: String,
            format: VkFormat,
            device: VulkanDevice,
            copyQueue: VkQueue,
            imageUsageFlags: VkImageUsageFlags = VkImageUsage.SAMPLED_BIT.i,
            imageLayout: VkImageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL,
            forceLinear: Boolean = false) {

        val file = File(filename)

        if (!file.exists())
            tools.exitFatal("Could not load texture from $filename\n\nThe file may be part of the additional asset pack.\n\n" +
                    "Run \"download_assets.py\" in the repository root to download the latest version.", -1)

        val tex2D = gli_.Texture2d(gli.load(file.toPath()))

        assert(tex2D.notEmpty())

        this.device = device
        size(tex2D[0].extent())
        mipLevels = tex2D.levels()

        // Get device properites for the requested texture format
        val formatProperties = device.physicalDevice.getFormatProperties(format)

        /*  Only use linear tiling if requested (and supported by the device)
            Support for linear tiling is mostly limited, so prefer to use optimal tiling instead
            On most implementations linear tiling will only support a very limited amount of formats and features
            (mip maps, cubemaps, arrays, etc.) */
        val useStaging = !forceLinear

//        VkMemoryAllocateInfo memAllocInfo = vks ::initializers::memoryAllocateInfo()
//        VkMemoryRequirements memReqs

        // Use a separate command buffer for texture loading
        val copyCmd = device.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)

        val dev = device.logicalDevice!!

        if (useStaging) {
            // Create a host-visible staging buffer that contains the raw image data

            val bufferCreateInfo = vk.BufferCreateInfo {
                size = VkDeviceSize(tex2D.size)
                // This buffer is used as a transfer source for the buffer copy
                usage = VkBufferUsage.TRANSFER_SRC_BIT.i
                sharingMode = VkSharingMode.EXCLUSIVE
            }
            val stagingBuffer = dev createBuffer bufferCreateInfo

            // Get memory requirements for the staging buffer (alignment, memory type bits)
            val memReqs = dev.getBufferMemoryRequirements(stagingBuffer)
            val memAllocInfo = vk.MemoryAllocateInfo {
                allocationSize = memReqs.size
                // Get memory type index for a host visible buffer
                memoryTypeIndex = device.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT)
            }
            val stagingMemory = dev allocateMemory memAllocInfo
            dev.bindBufferMemory(stagingBuffer, stagingMemory)

            // Copy texture data into staging buffer
            dev.mappedMemory(stagingMemory, VkDeviceSize(0), memReqs.size, 0) { data ->
                memCopy(memAddress(tex2D.data()), data, tex2D.size.L)
            }

            // Setup buffer copy regions for each mip level
            val bufferCopyRegions = vk.BufferImageCopy(mipLevels)
            var offset = VkDeviceSize(0)

            for (i in 0 until mipLevels) {

                bufferCopyRegions[i].apply {
                    imageSubresource.aspectMask = VkImageAspect.COLOR_BIT.i
                    imageSubresource.mipLevel = i
                    imageSubresource.baseArrayLayer = 0
                    imageSubresource.layerCount = 1
                    imageExtent.set(tex2D[i].extent().x, tex2D[i].extent().y, 1)
                    bufferOffset = offset
                }

                offset += tex2D[i].size
            }

            // Create optimal tiled target image
            val imageCreateInfo = vk.ImageCreateInfo {
                imageType = VkImageType._2D
                this.format = format
                mipLevels = this@Texture2D.mipLevels
                arrayLayers = 1
                samples = VkSampleCount._1_BIT
                tiling = VkImageTiling.OPTIMAL
                sharingMode = VkSharingMode.EXCLUSIVE
                initialLayout = VkImageLayout.UNDEFINED
                extent.set(size.x, size.y, 1)
                usage = imageUsageFlags
            }
            // Ensure that the TRANSFER_DST bit is set for staging
            if (imageCreateInfo.usage hasnt VkImageUsage.TRANSFER_DST_BIT)
                imageCreateInfo.usage = imageCreateInfo.usage or VkImageUsage.TRANSFER_DST_BIT

            image = dev createImage imageCreateInfo

            dev.getImageMemoryRequirements(image, memReqs)

            memAllocInfo.allocationSize = memReqs.size
            memAllocInfo.memoryTypeIndex = device.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)

            deviceMemory = dev allocateMemory memAllocInfo
            dev.bindImageMemory(image, deviceMemory)

            val subresourceRange = vk.ImageSubresourceRange {
                aspectMask = VkImageAspect.COLOR_BIT.i
                baseMipLevel = 0
                levelCount = mipLevels
                layerCount = 1
            }
            // Image barrier for optimal image (target)
            // Optimal image will be used as destination for the copy
            tools.setImageLayout(
                    copyCmd,
                    image,
                    VkImageLayout.UNDEFINED,
                    VkImageLayout.TRANSFER_DST_OPTIMAL,
                    subresourceRange)

            // Copy mip levels from staging buffer
            copyCmd.copyBufferToImage(
                    stagingBuffer,
                    image,
                    VkImageLayout.TRANSFER_DST_OPTIMAL,
                    bufferCopyRegions)

            // Change texture image layout to shader read after all mip levels have been copied
            this.imageLayout = imageLayout
            tools.setImageLayout(
                    copyCmd,
                    image,
                    VkImageLayout.TRANSFER_DST_OPTIMAL,
                    imageLayout,
                    subresourceRange)

            device.flushCommandBuffer(copyCmd, copyQueue)

            // Clean up staging resources
            dev freeMemory stagingMemory
            dev destroyBuffer stagingBuffer

        } else {

            /*  Prefer using optimal tiling, as linear tiling may support only a small set of features
                depending on implementation (e.g. no mip maps, only one layer, etc.)             */

            // Check if this support is supported for linear tiling
            assert(formatProperties.linearTilingFeatures has VkFormatFeature.SAMPLED_IMAGE_BIT)

            val imageCreateInfo = vk.ImageCreateInfo {
                imageType = VkImageType._2D
                this.format = format
                extent.set(size.x, size.y, 1)
                mipLevels = 1
                arrayLayers = 1
                samples = VkSampleCount._1_BIT
                tiling = VkImageTiling.LINEAR
                usage = imageUsageFlags
                sharingMode = VkSharingMode.EXCLUSIVE
                initialLayout = VkImageLayout.UNDEFINED
            }
            // Load mip map level 0 to linear tiling image
            val mappableImage = dev createImage imageCreateInfo

            // Get memory requirements for this image like size and alignment
            val memReqs = dev.getImageMemoryRequirements(mappableImage)
            val memAllocInfo = vk.MemoryAllocateInfo {
                // Set memory allocation size to required memory size
                allocationSize = memReqs.size

                // Get memory type that can be mapped to host memory
                memoryTypeIndex = device.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT)
            }
            // Allocate host memory
            val mappableMemory = dev allocateMemory memAllocInfo

            // Bind allocated image for use
            dev.bindImageMemory(mappableImage, mappableMemory)

            // Get sub resource layout: mip map count, array layer, etc.
            val subRes = vk.ImageSubresource {
                aspectMask = VkImageAspect.COLOR_BIT.i
                mipLevel = 0
            }

            // Get sub resources layout, includes row pitch, size offsets, etc.
            val subResLayout = dev.getImageSubresourceLayout(mappableImage, subRes)

            // Map image memory
            dev.mappedMemory(mappableMemory, VkDeviceSize(0), memReqs.size, 0) { data ->
                // Copy image data into memory
                memCopy(memAddress(tex2D[subRes.mipLevel].data()!!), data, tex2D[subRes.mipLevel].size.L)
            }

            // Linear tiled images don't need to be staged
            // and can be directly used as textures
            image = mappableImage
            deviceMemory = mappableMemory
            this.imageLayout = imageLayout

            // Setup image memory barrier
            tools.setImageLayout(copyCmd, image, VkImageAspect.COLOR_BIT.i, VkImageLayout.UNDEFINED, imageLayout)

            device.flushCommandBuffer(copyCmd, copyQueue)
        }

        // Create a defaultsampler
        val samplerCreateInfo = vk.SamplerCreateInfo {
            minMagFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            addressModeUVW = VkSamplerAddressMode.REPEAT
            mipLodBias = 0f
            compareOp = VkCompareOp.NEVER
            minLod = 0f
            // Max level-of-detail should match mip level count
            maxLod = if (useStaging) mipLevels.f else 0f
            // Only enable anisotropic filtering if enabled on the devicec
            maxAnisotropy = if (device.enabledFeatures.samplerAnisotropy) device.properties.limits.maxSamplerAnisotropy else 1f
            anisotropyEnable = device.enabledFeatures.samplerAnisotropy
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
        }
        sampler = dev createSampler samplerCreateInfo

        /*  Create image view
            Textures are not directly accessed by the shaders and are abstracted by image views containing additional
            information and sub resource ranges */
        val viewCreateInfo = vk.ImageViewCreateInfo {
            viewType = VkImageViewType._2D
            this.format = format
            components(VkComponentSwizzle.R, VkComponentSwizzle.G, VkComponentSwizzle.B, VkComponentSwizzle.A )
            subresourceRange.set( VkImageAspect.COLOR_BIT.i, 0, 1, 0, 1 )
            // Linear tiling usually won't support mip maps
            // Only set mip map count if optimal tiling is used
            subresourceRange.levelCount = if(useStaging) mipLevels else 1
            image = this@Texture2D.image
        }
        view = dev createImageView viewCreateInfo

        // Update descriptor image info member that can be used for setting up descriptor sets
        updateDescriptor()
    }

    /**
     * Creates a 2D texture from a buffer
     *
     * @param buffer Buffer containing texture data to upload
     * @param bufferSize Size of the buffer in machine units
     * @param width Width of the texture to create
     * @param height Height of the texture to create
     * @param format Vulkan format of the image data stored in the file
     * @param device Vulkan device to create the texture on
     * @param copyQueue Queue used for the texture staging copy commands (must support transfer)
     * @param (Optional) filter Texture filtering for the sampler (defaults to VK_FILTER_LINEAR)
     * @param (Optional) imageUsageFlags Usage flags for the texture's image (defaults to VK_IMAGE_USAGE_SAMPLED_BIT)
     * @param (Optional) imageLayout Usage layout for the texture (defaults VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
     */
//    fun fromBuffer (
//    void * buffer,
//    VkDeviceSize bufferSize,
//    VkFormat format,
//    uint32_t width,
//    uint32_t height,
//    vks::VulkanDevice * device,
//    VkQueue copyQueue,
//    VkFilter filter = VK_FILTER_LINEAR,
//    VkImageUsageFlags imageUsageFlags = VK_IMAGE_USAGE_SAMPLED_BIT,
//    VkImageLayout imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
//    {
//        assert(buffer)
//
//        this->device = device
//        width = width
//        height = height
//        mipLevels = 1
//
//        VkMemoryAllocateInfo memAllocInfo = vks ::initializers::memoryAllocateInfo()
//        VkMemoryRequirements memReqs
//
//                // Use a separate command buffer for texture loading
//                VkCommandBuffer copyCmd = device->createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, true)
//
//        // Create a host-visible staging buffer that contains the raw image data
//        VkBuffer stagingBuffer
//                VkDeviceMemory stagingMemory
//
//                VkBufferCreateInfo bufferCreateInfo = vks ::initializers::bufferCreateInfo()
//        bufferCreateInfo.size = bufferSize
//        // This buffer is used as a transfer source for the buffer copy
//        bufferCreateInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT
//        bufferCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE
//
//        VK_CHECK_RESULT(vkCreateBuffer(device->logicalDevice, &bufferCreateInfo, nullptr, &stagingBuffer))
//
//        // Get memory requirements for the staging buffer (alignment, memory type bits)
//        vkGetBufferMemoryRequirements(device->logicalDevice, stagingBuffer, &memReqs)
//
//        memAllocInfo.allocationSize = memReqs.size
//        // Get memory type index for a host visible buffer
//        memAllocInfo.memoryTypeIndex = device->getMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
//
//        VK_CHECK_RESULT(vkAllocateMemory(device->logicalDevice, &memAllocInfo, nullptr, &stagingMemory))
//        VK_CHECK_RESULT(vkBindBufferMemory(device->logicalDevice, stagingBuffer, stagingMemory, 0))
//
//        // Copy texture data into staging buffer
//        uint8_t * data
//        VK_CHECK_RESULT(vkMapMemory(device->logicalDevice, stagingMemory, 0, memReqs.size, 0, (void **)&data))
//        memcpy(data, buffer, bufferSize)
//        vkUnmapMemory(device->logicalDevice, stagingMemory)
//
//        VkBufferImageCopy bufferCopyRegion = {}
//        bufferCopyRegion.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//        bufferCopyRegion.imageSubresource.mipLevel = 0
//        bufferCopyRegion.imageSubresource.baseArrayLayer = 0
//        bufferCopyRegion.imageSubresource.layerCount = 1
//        bufferCopyRegion.imageExtent.width = width
//        bufferCopyRegion.imageExtent.height = height
//        bufferCopyRegion.imageExtent.depth = 1
//        bufferCopyRegion.bufferOffset = 0
//
//        // Create optimal tiled target image
//        VkImageCreateInfo imageCreateInfo = vks ::initializers::imageCreateInfo()
//        imageCreateInfo.imageType = VK_IMAGE_TYPE_2D
//        imageCreateInfo.format = format
//        imageCreateInfo.mipLevels = mipLevels
//        imageCreateInfo.arrayLayers = 1
//        imageCreateInfo.samples = VK_SAMPLE_COUNT_1_BIT
//        imageCreateInfo.tiling = VK_IMAGE_TILING_OPTIMAL
//        imageCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE
//        imageCreateInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED
//        imageCreateInfo.extent = { width, height, 1 }
//        imageCreateInfo.usage = imageUsageFlags
//        // Ensure that the TRANSFER_DST bit is set for staging
//        if (!(imageCreateInfo.usage & VK_IMAGE_USAGE_TRANSFER_DST_BIT))
//        {
//            imageCreateInfo.usage | = VK_IMAGE_USAGE_TRANSFER_DST_BIT
//        }
//        VK_CHECK_RESULT(vkCreateImage(device->logicalDevice, &imageCreateInfo, nullptr, &image))
//
//        vkGetImageMemoryRequirements(device->logicalDevice, image, &memReqs)
//
//        memAllocInfo.allocationSize = memReqs.size
//
//        memAllocInfo.memoryTypeIndex = device->getMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
//        VK_CHECK_RESULT(vkAllocateMemory(device->logicalDevice, &memAllocInfo, nullptr, &deviceMemory))
//        VK_CHECK_RESULT(vkBindImageMemory(device->logicalDevice, image, deviceMemory, 0))
//
//        VkImageSubresourceRange subresourceRange = {}
//        subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//        subresourceRange.baseMipLevel = 0
//        subresourceRange.levelCount = mipLevels
//        subresourceRange.layerCount = 1
//
//        // Image barrier for optimal image (target)
//        // Optimal image will be used as destination for the copy
//        vks::tools::setImageLayout(
//                copyCmd,
//                image,
//                VK_IMAGE_LAYOUT_UNDEFINED,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                subresourceRange)
//
//        // Copy mip levels from staging buffer
//        vkCmdCopyBufferToImage(
//                copyCmd,
//                stagingBuffer,
//                image,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                1,
//                & bufferCopyRegion
//        )
//
//        // Change texture image layout to shader read after all mip levels have been copied
//        this->imageLayout = imageLayout
//        vks::tools::setImageLayout(
//                copyCmd,
//                image,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                imageLayout,
//                subresourceRange)
//
//        device->flushCommandBuffer(copyCmd, copyQueue)
//
//        // Clean up staging resources
//        vkFreeMemory(device->logicalDevice, stagingMemory, nullptr)
//        vkDestroyBuffer(device->logicalDevice, stagingBuffer, nullptr)
//
//        // Create sampler
//        VkSamplerCreateInfo samplerCreateInfo = {}
//        samplerCreateInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO
//        samplerCreateInfo.magFilter = filter
//        samplerCreateInfo.minFilter = filter
//        samplerCreateInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR
//        samplerCreateInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_REPEAT
//        samplerCreateInfo.addressModeV = VK_SAMPLER_ADDRESS_MODE_REPEAT
//        samplerCreateInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_REPEAT
//        samplerCreateInfo.mipLodBias = 0.0f
//        samplerCreateInfo.compareOp = VK_COMPARE_OP_NEVER
//        samplerCreateInfo.minLod = 0.0f
//        samplerCreateInfo.maxLod = 0.0f
//        samplerCreateInfo.maxAnisotropy = 1.0f
//        VK_CHECK_RESULT(vkCreateSampler(device->logicalDevice, &samplerCreateInfo, nullptr, &sampler))
//
//        // Create image view
//        VkImageViewCreateInfo viewCreateInfo = {}
//        viewCreateInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO
//        viewCreateInfo.pNext = NULL
//        viewCreateInfo.viewType = VK_IMAGE_VIEW_TYPE_2D
//        viewCreateInfo.format = format
//        viewCreateInfo.components = { VK_COMPONENT_SWIZZLE_R, VK_COMPONENT_SWIZZLE_G, VK_COMPONENT_SWIZZLE_B, VK_COMPONENT_SWIZZLE_A }
//        viewCreateInfo.subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 }
//        viewCreateInfo.subresourceRange.levelCount = 1
//        viewCreateInfo.image = image
//        VK_CHECK_RESULT(vkCreateImageView(device->logicalDevice, &viewCreateInfo, nullptr, &view))
//
//        // Update descriptor image info member that can be used for setting up descriptor sets
//        updateDescriptor()
//    }

}

///** @brief 2D array texture */
//class Texture2DArray : public Texture
//{
//    public:
//    /**
//     * Load a 2D texture array including all mip levels
//     *
//     * @param filename File to load (supports .ktx and .dds)
//     * @param format Vulkan format of the image data stored in the file
//     * @param device Vulkan device to create the texture on
//     * @param copyQueue Queue used for the texture staging copy commands (must support transfer)
//     * @param (Optional) imageUsageFlags Usage flags for the texture's image (defaults to VK_IMAGE_USAGE_SAMPLED_BIT)
//     * @param (Optional) imageLayout Usage layout for the texture (defaults VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
//     *
//     */
//    void loadFromFile (
//            std::string filename,
//    VkFormat format,
//    vks::VulkanDevice * device,
//    VkQueue copyQueue,
//    VkImageUsageFlags imageUsageFlags = VK_IMAGE_USAGE_SAMPLED_BIT,
//    VkImageLayout imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
//    {
//        #if defined(__ANDROID__)
//        // Textures are stored inside the apk on Android (compressed)
//        // So they need to be loaded via the asset manager
//        AAsset * asset = AAssetManager_open(androidApp->activity->assetManager, filename.c_str(), AASSET_MODE_STREAMING)
//        if (!asset) {
//            vks::tools::exitFatal("Could not load texture from " + filename + "\n\nThe file may be part of the additional asset pack.\n\nRun \"download_assets.py\" in the repository root to download the latest version.", -1)
//        }
//        size_t size = AAsset_getLength (asset)
//        assert(size > 0)
//
//        void * textureData = malloc(size)
//        AAsset_read(asset, textureData, size)
//        AAsset_close(asset)
//
//        gli::texture2d_array tex2DArray (gli::load((const char *) textureData, size))
//
//        free(textureData)
//        #else
//        if (!vks::tools::fileExists(filename)) {
//            vks::tools::exitFatal("Could not load texture from " + filename + "\n\nThe file may be part of the additional asset pack.\n\nRun \"download_assets.py\" in the repository root to download the latest version.", -1)
//        }
//        gli::texture2d_array tex2DArray (gli::load(filename))
//        #endif
//        assert(!tex2DArray.empty())
//
//        this->device = device
//        width = static_cast<uint32_t>(tex2DArray.extent().x)
//        height = static_cast<uint32_t>(tex2DArray.extent().y)
//        layerCount = static_cast<uint32_t>(tex2DArray.layers())
//        mipLevels = static_cast<uint32_t>(tex2DArray.levels())
//
//        VkMemoryAllocateInfo memAllocInfo = vks ::initializers::memoryAllocateInfo()
//        VkMemoryRequirements memReqs
//
//                // Create a host-visible staging buffer that contains the raw image data
//                VkBuffer stagingBuffer
//                VkDeviceMemory stagingMemory
//
//                VkBufferCreateInfo bufferCreateInfo = vks ::initializers::bufferCreateInfo()
//        bufferCreateInfo.size = tex2DArray.size()
//        // This buffer is used as a transfer source for the buffer copy
//        bufferCreateInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT
//        bufferCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE
//
//        VK_CHECK_RESULT(vkCreateBuffer(device->logicalDevice, &bufferCreateInfo, nullptr, &stagingBuffer))
//
//        // Get memory requirements for the staging buffer (alignment, memory type bits)
//        vkGetBufferMemoryRequirements(device->logicalDevice, stagingBuffer, &memReqs)
//
//        memAllocInfo.allocationSize = memReqs.size
//        // Get memory type index for a host visible buffer
//        memAllocInfo.memoryTypeIndex = device->getMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
//
//        VK_CHECK_RESULT(vkAllocateMemory(device->logicalDevice, &memAllocInfo, nullptr, &stagingMemory))
//        VK_CHECK_RESULT(vkBindBufferMemory(device->logicalDevice, stagingBuffer, stagingMemory, 0))
//
//        // Copy texture data into staging buffer
//        uint8_t * data
//        VK_CHECK_RESULT(vkMapMemory(device->logicalDevice, stagingMemory, 0, memReqs.size, 0, (void **)&data))
//        memcpy(data, tex2DArray.data(), static_cast<size_t>(tex2DArray.size()))
//        vkUnmapMemory(device->logicalDevice, stagingMemory)
//
//        // Setup buffer copy regions for each layer including all of it's miplevels
//        std::vector<VkBufferImageCopy> bufferCopyRegions
//                size_t offset = 0
//
//        for (uint32_t layer = 0; layer < layerCount; layer++)
//        {
//            for (uint32_t level = 0; level < mipLevels; level++)
//            {
//                VkBufferImageCopy bufferCopyRegion = {}
//                bufferCopyRegion.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//                bufferCopyRegion.imageSubresource.mipLevel = level
//                bufferCopyRegion.imageSubresource.baseArrayLayer = layer
//                bufferCopyRegion.imageSubresource.layerCount = 1
//                bufferCopyRegion.imageExtent.width = static_cast<uint32_t>(tex2DArray[layer][level].extent().x)
//                bufferCopyRegion.imageExtent.height = static_cast<uint32_t>(tex2DArray[layer][level].extent().y)
//                bufferCopyRegion.imageExtent.depth = 1
//                bufferCopyRegion.bufferOffset = offset
//
//                bufferCopyRegions.push_back(bufferCopyRegion)
//
//                // Increase offset into staging buffer for next level / face
//                offset += tex2DArray[layer][level].size()
//            }
//        }
//
//        // Create optimal tiled target image
//        VkImageCreateInfo imageCreateInfo = vks ::initializers::imageCreateInfo()
//        imageCreateInfo.imageType = VK_IMAGE_TYPE_2D
//        imageCreateInfo.format = format
//        imageCreateInfo.samples = VK_SAMPLE_COUNT_1_BIT
//        imageCreateInfo.tiling = VK_IMAGE_TILING_OPTIMAL
//        imageCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE
//        imageCreateInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED
//        imageCreateInfo.extent = { width, height, 1 }
//        imageCreateInfo.usage = imageUsageFlags
//        // Ensure that the TRANSFER_DST bit is set for staging
//        if (!(imageCreateInfo.usage & VK_IMAGE_USAGE_TRANSFER_DST_BIT))
//        {
//            imageCreateInfo.usage | = VK_IMAGE_USAGE_TRANSFER_DST_BIT
//        }
//        imageCreateInfo.arrayLayers = layerCount
//        imageCreateInfo.mipLevels = mipLevels
//
//        VK_CHECK_RESULT(vkCreateImage(device->logicalDevice, &imageCreateInfo, nullptr, &image))
//
//        vkGetImageMemoryRequirements(device->logicalDevice, image, &memReqs)
//
//        memAllocInfo.allocationSize = memReqs.size
//        memAllocInfo.memoryTypeIndex = device->getMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
//
//        VK_CHECK_RESULT(vkAllocateMemory(device->logicalDevice, &memAllocInfo, nullptr, &deviceMemory))
//        VK_CHECK_RESULT(vkBindImageMemory(device->logicalDevice, image, deviceMemory, 0))
//
//        // Use a separate command buffer for texture loading
//        VkCommandBuffer copyCmd = device->createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, true)
//
//        // Image barrier for optimal image (target)
//        // Set initial layout for all array layers (faces) of the optimal (target) tiled texture
//        VkImageSubresourceRange subresourceRange = {}
//        subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//        subresourceRange.baseMipLevel = 0
//        subresourceRange.levelCount = mipLevels
//        subresourceRange.layerCount = layerCount
//
//        vks::tools::setImageLayout(
//                copyCmd,
//                image,
//                VK_IMAGE_LAYOUT_UNDEFINED,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                subresourceRange)
//
//        // Copy the layers and mip levels from the staging buffer to the optimal tiled image
//        vkCmdCopyBufferToImage(
//                copyCmd,
//                stagingBuffer,
//                image,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                static_cast<uint32_t>(bufferCopyRegions.size()),
//                bufferCopyRegions.data())
//
//        // Change texture image layout to shader read after all faces have been copied
//        this->imageLayout = imageLayout
//        vks::tools::setImageLayout(
//                copyCmd,
//                image,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                imageLayout,
//                subresourceRange)
//
//        device->flushCommandBuffer(copyCmd, copyQueue)
//
//        // Create sampler
//        VkSamplerCreateInfo samplerCreateInfo = vks ::initializers::samplerCreateInfo()
//        samplerCreateInfo.magFilter = VK_FILTER_LINEAR
//        samplerCreateInfo.minFilter = VK_FILTER_LINEAR
//        samplerCreateInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR
//        samplerCreateInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
//        samplerCreateInfo.addressModeV = samplerCreateInfo.addressModeU
//        samplerCreateInfo.addressModeW = samplerCreateInfo.addressModeU
//        samplerCreateInfo.mipLodBias = 0.0f
//        samplerCreateInfo.maxAnisotropy = device->enabledFeatures.samplerAnisotropy ? device->properties.limits.maxSamplerAnisotropy : 1.0f
//        samplerCreateInfo.anisotropyEnable = device->enabledFeatures.samplerAnisotropy
//        samplerCreateInfo.compareOp = VK_COMPARE_OP_NEVER
//        samplerCreateInfo.minLod = 0.0f
//        samplerCreateInfo.maxLod = (float) mipLevels
//                samplerCreateInfo.borderColor = VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE
//        VK_CHECK_RESULT(vkCreateSampler(device->logicalDevice, &samplerCreateInfo, nullptr, &sampler))
//
//        // Create image view
//        VkImageViewCreateInfo viewCreateInfo = vks ::initializers::imageViewCreateInfo()
//        viewCreateInfo.viewType = VK_IMAGE_VIEW_TYPE_2D_ARRAY
//        viewCreateInfo.format = format
//        viewCreateInfo.components = { VK_COMPONENT_SWIZZLE_R, VK_COMPONENT_SWIZZLE_G, VK_COMPONENT_SWIZZLE_B, VK_COMPONENT_SWIZZLE_A }
//        viewCreateInfo.subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 }
//        viewCreateInfo.subresourceRange.layerCount = layerCount
//        viewCreateInfo.subresourceRange.levelCount = mipLevels
//        viewCreateInfo.image = image
//        VK_CHECK_RESULT(vkCreateImageView(device->logicalDevice, &viewCreateInfo, nullptr, &view))
//
//        // Clean up staging resources
//        vkFreeMemory(device->logicalDevice, stagingMemory, nullptr)
//        vkDestroyBuffer(device->logicalDevice, stagingBuffer, nullptr)
//
//        // Update descriptor image info member that can be used for setting up descriptor sets
//        updateDescriptor()
//    }
//}
//
///** @brief Cube map texture */
//class TextureCubeMap : public Texture
//{
//    public:
//    /**
//     * Load a cubemap texture including all mip levels from a single file
//     *
//     * @param filename File to load (supports .ktx and .dds)
//     * @param format Vulkan format of the image data stored in the file
//     * @param device Vulkan device to create the texture on
//     * @param copyQueue Queue used for the texture staging copy commands (must support transfer)
//     * @param (Optional) imageUsageFlags Usage flags for the texture's image (defaults to VK_IMAGE_USAGE_SAMPLED_BIT)
//     * @param (Optional) imageLayout Usage layout for the texture (defaults VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
//     *
//     */
//    void loadFromFile (
//            std::string filename,
//    VkFormat format,
//    vks::VulkanDevice * device,
//    VkQueue copyQueue,
//    VkImageUsageFlags imageUsageFlags = VK_IMAGE_USAGE_SAMPLED_BIT,
//    VkImageLayout imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
//    {
//        #if defined(__ANDROID__)
//        // Textures are stored inside the apk on Android (compressed)
//        // So they need to be loaded via the asset manager
//        AAsset * asset = AAssetManager_open(androidApp->activity->assetManager, filename.c_str(), AASSET_MODE_STREAMING)
//        if (!asset) {
//            vks::tools::exitFatal("Could not load texture from " + filename + "\n\nThe file may be part of the additional asset pack.\n\nRun \"download_assets.py\" in the repository root to download the latest version.", -1)
//        }
//        size_t size = AAsset_getLength (asset)
//        assert(size > 0)
//
//        void * textureData = malloc(size)
//        AAsset_read(asset, textureData, size)
//        AAsset_close(asset)
//
//        gli::texture_cube texCube (gli::load((const char *) textureData, size))
//
//        free(textureData)
//        #else
//        if (!vks::tools::fileExists(filename)) {
//            vks::tools::exitFatal("Could not load texture from " + filename + "\n\nThe file may be part of the additional asset pack.\n\nRun \"download_assets.py\" in the repository root to download the latest version.", -1)
//        }
//        gli::texture_cube texCube (gli::load(filename))
//        #endif
//        assert(!texCube.empty())
//
//        this->device = device
//        width = static_cast<uint32_t>(texCube.extent().x)
//        height = static_cast<uint32_t>(texCube.extent().y)
//        mipLevels = static_cast<uint32_t>(texCube.levels())
//
//        VkMemoryAllocateInfo memAllocInfo = vks ::initializers::memoryAllocateInfo()
//        VkMemoryRequirements memReqs
//
//                // Create a host-visible staging buffer that contains the raw image data
//                VkBuffer stagingBuffer
//                VkDeviceMemory stagingMemory
//
//                VkBufferCreateInfo bufferCreateInfo = vks ::initializers::bufferCreateInfo()
//        bufferCreateInfo.size = texCube.size()
//        // This buffer is used as a transfer source for the buffer copy
//        bufferCreateInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT
//        bufferCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE
//
//        VK_CHECK_RESULT(vkCreateBuffer(device->logicalDevice, &bufferCreateInfo, nullptr, &stagingBuffer))
//
//        // Get memory requirements for the staging buffer (alignment, memory type bits)
//        vkGetBufferMemoryRequirements(device->logicalDevice, stagingBuffer, &memReqs)
//
//        memAllocInfo.allocationSize = memReqs.size
//        // Get memory type index for a host visible buffer
//        memAllocInfo.memoryTypeIndex = device->getMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
//
//        VK_CHECK_RESULT(vkAllocateMemory(device->logicalDevice, &memAllocInfo, nullptr, &stagingMemory))
//        VK_CHECK_RESULT(vkBindBufferMemory(device->logicalDevice, stagingBuffer, stagingMemory, 0))
//
//        // Copy texture data into staging buffer
//        uint8_t * data
//        VK_CHECK_RESULT(vkMapMemory(device->logicalDevice, stagingMemory, 0, memReqs.size, 0, (void **)&data))
//        memcpy(data, texCube.data(), texCube.size())
//        vkUnmapMemory(device->logicalDevice, stagingMemory)
//
//        // Setup buffer copy regions for each face including all of it's miplevels
//        std::vector<VkBufferImageCopy> bufferCopyRegions
//                size_t offset = 0
//
//        for (uint32_t face = 0; face < 6; face++)
//        {
//            for (uint32_t level = 0; level < mipLevels; level++)
//            {
//                VkBufferImageCopy bufferCopyRegion = {}
//                bufferCopyRegion.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//                bufferCopyRegion.imageSubresource.mipLevel = level
//                bufferCopyRegion.imageSubresource.baseArrayLayer = face
//                bufferCopyRegion.imageSubresource.layerCount = 1
//                bufferCopyRegion.imageExtent.width = static_cast<uint32_t>(texCube[face][level].extent().x)
//                bufferCopyRegion.imageExtent.height = static_cast<uint32_t>(texCube[face][level].extent().y)
//                bufferCopyRegion.imageExtent.depth = 1
//                bufferCopyRegion.bufferOffset = offset
//
//                bufferCopyRegions.push_back(bufferCopyRegion)
//
//                // Increase offset into staging buffer for next level / face
//                offset += texCube[face][level].size()
//            }
//        }
//
//        // Create optimal tiled target image
//        VkImageCreateInfo imageCreateInfo = vks ::initializers::imageCreateInfo()
//        imageCreateInfo.imageType = VK_IMAGE_TYPE_2D
//        imageCreateInfo.format = format
//        imageCreateInfo.mipLevels = mipLevels
//        imageCreateInfo.samples = VK_SAMPLE_COUNT_1_BIT
//        imageCreateInfo.tiling = VK_IMAGE_TILING_OPTIMAL
//        imageCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE
//        imageCreateInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED
//        imageCreateInfo.extent = { width, height, 1 }
//        imageCreateInfo.usage = imageUsageFlags
//        // Ensure that the TRANSFER_DST bit is set for staging
//        if (!(imageCreateInfo.usage & VK_IMAGE_USAGE_TRANSFER_DST_BIT))
//        {
//            imageCreateInfo.usage | = VK_IMAGE_USAGE_TRANSFER_DST_BIT
//        }
//        // Cube faces count as array layers in Vulkan
//        imageCreateInfo.arrayLayers = 6
//        // This flag is required for cube map images
//        imageCreateInfo.flags = VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT
//
//
//        VK_CHECK_RESULT(vkCreateImage(device->logicalDevice, &imageCreateInfo, nullptr, &image))
//
//        vkGetImageMemoryRequirements(device->logicalDevice, image, &memReqs)
//
//        memAllocInfo.allocationSize = memReqs.size
//        memAllocInfo.memoryTypeIndex = device->getMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
//
//        VK_CHECK_RESULT(vkAllocateMemory(device->logicalDevice, &memAllocInfo, nullptr, &deviceMemory))
//        VK_CHECK_RESULT(vkBindImageMemory(device->logicalDevice, image, deviceMemory, 0))
//
//        // Use a separate command buffer for texture loading
//        VkCommandBuffer copyCmd = device->createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, true)
//
//        // Image barrier for optimal image (target)
//        // Set initial layout for all array layers (faces) of the optimal (target) tiled texture
//        VkImageSubresourceRange subresourceRange = {}
//        subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//        subresourceRange.baseMipLevel = 0
//        subresourceRange.levelCount = mipLevels
//        subresourceRange.layerCount = 6
//
//        vks::tools::setImageLayout(
//                copyCmd,
//                image,
//                VK_IMAGE_LAYOUT_UNDEFINED,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                subresourceRange)
//
//        // Copy the cube map faces from the staging buffer to the optimal tiled image
//        vkCmdCopyBufferToImage(
//                copyCmd,
//                stagingBuffer,
//                image,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                static_cast<uint32_t>(bufferCopyRegions.size()),
//                bufferCopyRegions.data())
//
//        // Change texture image layout to shader read after all faces have been copied
//        this->imageLayout = imageLayout
//        vks::tools::setImageLayout(
//                copyCmd,
//                image,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                imageLayout,
//                subresourceRange)
//
//        device->flushCommandBuffer(copyCmd, copyQueue)
//
//        // Create sampler
//        VkSamplerCreateInfo samplerCreateInfo = vks ::initializers::samplerCreateInfo()
//        samplerCreateInfo.magFilter = VK_FILTER_LINEAR
//        samplerCreateInfo.minFilter = VK_FILTER_LINEAR
//        samplerCreateInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR
//        samplerCreateInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
//        samplerCreateInfo.addressModeV = samplerCreateInfo.addressModeU
//        samplerCreateInfo.addressModeW = samplerCreateInfo.addressModeU
//        samplerCreateInfo.mipLodBias = 0.0f
//        samplerCreateInfo.maxAnisotropy = device->enabledFeatures.samplerAnisotropy ? device->properties.limits.maxSamplerAnisotropy : 1.0f
//        samplerCreateInfo.anisotropyEnable = device->enabledFeatures.samplerAnisotropy
//        samplerCreateInfo.compareOp = VK_COMPARE_OP_NEVER
//        samplerCreateInfo.minLod = 0.0f
//        samplerCreateInfo.maxLod = (float) mipLevels
//                samplerCreateInfo.borderColor = VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE
//        VK_CHECK_RESULT(vkCreateSampler(device->logicalDevice, &samplerCreateInfo, nullptr, &sampler))
//
//        // Create image view
//        VkImageViewCreateInfo viewCreateInfo = vks ::initializers::imageViewCreateInfo()
//        viewCreateInfo.viewType = VK_IMAGE_VIEW_TYPE_CUBE
//        viewCreateInfo.format = format
//        viewCreateInfo.components = { VK_COMPONENT_SWIZZLE_R, VK_COMPONENT_SWIZZLE_G, VK_COMPONENT_SWIZZLE_B, VK_COMPONENT_SWIZZLE_A }
//        viewCreateInfo.subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 }
//        viewCreateInfo.subresourceRange.layerCount = 6
//        viewCreateInfo.subresourceRange.levelCount = mipLevels
//        viewCreateInfo.image = image
//        VK_CHECK_RESULT(vkCreateImageView(device->logicalDevice, &viewCreateInfo, nullptr, &view))
//
//        // Clean up staging resources
//        vkFreeMemory(device->logicalDevice, stagingMemory, nullptr)
//        vkDestroyBuffer(device->logicalDevice, stagingBuffer, nullptr)
//
//        // Update descriptor image info member that can be used for setting up descriptor sets
//        updateDescriptor()
//    }
//}