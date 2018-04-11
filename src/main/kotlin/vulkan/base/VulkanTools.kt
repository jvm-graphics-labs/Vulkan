package vulkan.base

import gli_.has
import org.lwjgl.system.Platform
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VkFormatProperties
import org.lwjgl.vulkan.VkPhysicalDevice
import vkn.*
import vkn.VkMemoryStack.Companion.withStack
import kotlin.reflect.KMutableProperty0

object tools {

    /** Custom define for better code readability   */
    val VK_FLAGS_NONE = 0
    /** Default fence timeout in nanoseconds    */
    var DEFAULT_FENCE_TIMEOUT = 100_000_000_000

    //    bool errorModeSilent = false;

    //    std::string physicalDeviceTypeString(VkPhysicalDeviceType type)
//    {
//        switch (type)
//        {
//            #define STR(r) case VK_PHYSICAL_DEVICE_TYPE_ ##r: return #r
//            STR(OTHER);
//            STR(INTEGRATED_GPU);
//            STR(DISCRETE_GPU);
//            STR(VIRTUAL_GPU);
//            #undef STR
//                default: return "UNKNOWN_DEVICE_TYPE";
//        }
//    }
//
    fun getSupportedDepthFormat(physicalDevice: VkPhysicalDevice, depthFormat: KMutableProperty0<VkFormat>): Boolean {
        /*  Since all depth formats may be optional, we need to find a suitable depth format to use
            Start with the highest precision packed format         */
        arrayOf(VkFormat.D32_SFLOAT_S8_UINT,
                VkFormat.D32_SFLOAT,
                VkFormat.D24_UNORM_S8_UINT,
                VkFormat.D16_UNORM_S8_UINT,
                VkFormat.D16_UNORM).forEach {

            val formatProps = vk.getPhysicalDeviceFormatProperties(physicalDevice, it)
            // Format must support depth stencil attachment for optimal tiling
            if (formatProps.optimalTilingFeatures has VkFormatFeature.DEPTH_STENCIL_ATTACHMENT_BIT) {
                depthFormat.set(it)
                return true
            }
        }
        return false
    }
//
//    // Create an image memory barrier for changing the layout of
//    // an image and put it into an active command buffer
//    // See chapter 11.4 "Image Layout" for details
//
//    void setImageLayout(
//            VkCommandBuffer cmdbuffer,
//    VkImage image,
//    VkImageLayout oldImageLayout,
//    VkImageLayout newImageLayout,
//    VkImageSubresourceRange subresourceRange,
//    VkPipelineStageFlags srcStageMask,
//    VkPipelineStageFlags dstStageMask)
//    {
//        // Create an image barrier object
//        VkImageMemoryBarrier imageMemoryBarrier = vks::initializers::imageMemoryBarrier();
//        imageMemoryBarrier.oldLayout = oldImageLayout;
//        imageMemoryBarrier.newLayout = newImageLayout;
//        imageMemoryBarrier.image = image;
//        imageMemoryBarrier.subresourceRange = subresourceRange;
//
//        // Source layouts (old)
//        // Source access mask controls actions that have to be finished on the old layout
//        // before it will be transitioned to the new layout
//        switch (oldImageLayout)
//        {
//            case VK_IMAGE_LAYOUT_UNDEFINED:
//            // Image layout is undefined (or does not matter)
//            // Only valid as initial layout
//            // No flags required, listed only for completeness
//            imageMemoryBarrier.srcAccessMask = 0;
//            break;
//
//            case VK_IMAGE_LAYOUT_PREINITIALIZED:
//            // Image is preinitialized
//            // Only valid as initial layout for linear images, preserves memory contents
//            // Make sure host writes have been finished
//            imageMemoryBarrier.srcAccessMask = VK_ACCESS_HOST_WRITE_BIT;
//            break;
//
//            case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL:
//            // Image is a color attachment
//            // Make sure any writes to the color buffer have been finished
//            imageMemoryBarrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
//            break;
//
//            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL:
//            // Image is a depth/stencil attachment
//            // Make sure any writes to the depth/stencil buffer have been finished
//            imageMemoryBarrier.srcAccessMask = VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
//            break;
//
//            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL:
//            // Image is a transfer source
//            // Make sure any reads from the image have been finished
//            imageMemoryBarrier.srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
//            break;
//
//            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
//            // Image is a transfer destination
//            // Make sure any writes to the image have been finished
//            imageMemoryBarrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
//            break;
//
//            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:
//            // Image is read by a shader
//            // Make sure any shader reads from the image have been finished
//            imageMemoryBarrier.srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
//            break;
//            default:
//            // Other source layouts aren't handled (yet)
//            break;
//        }
//
//        // Target layouts (new)
//        // Destination access mask controls the dependency for the new image layout
//        switch (newImageLayout)
//        {
//            case VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
//            // Image will be used as a transfer destination
//            // Make sure any writes to the image have been finished
//            imageMemoryBarrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
//            break;
//
//            case VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL:
//            // Image will be used as a transfer source
//            // Make sure any reads from the image have been finished
//            imageMemoryBarrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
//            break;
//
//            case VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL:
//            // Image will be used as a color attachment
//            // Make sure any writes to the color buffer have been finished
//            imageMemoryBarrier.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
//            break;
//
//            case VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL:
//            // Image layout will be used as a depth/stencil attachment
//            // Make sure any writes to depth/stencil buffer have been finished
//            imageMemoryBarrier.dstAccessMask = imageMemoryBarrier.dstAccessMask | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
//            break;
//
//            case VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:
//            // Image will be read in a shader (sampler, input attachment)
//            // Make sure any writes to the image have been finished
//            if (imageMemoryBarrier.srcAccessMask == 0)
//            {
//                imageMemoryBarrier.srcAccessMask = VK_ACCESS_HOST_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT;
//            }
//            imageMemoryBarrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
//            break;
//            default:
//            // Other source layouts aren't handled (yet)
//            break;
//        }
//
//        // Put barrier inside setup command buffer
//        vkCmdPipelineBarrier(
//                cmdbuffer,
//                srcStageMask,
//                dstStageMask,
//                0,
//                0, nullptr,
//                0, nullptr,
//                1, &imageMemoryBarrier);
//    }
//
//    // Fixed sub resource on first mip level and layer
//    void setImageLayout(
//            VkCommandBuffer cmdbuffer,
//    VkImage image,
//    VkImageAspectFlags aspectMask,
//    VkImageLayout oldImageLayout,
//    VkImageLayout newImageLayout,
//    VkPipelineStageFlags srcStageMask,
//    VkPipelineStageFlags dstStageMask)
//    {
//        VkImageSubresourceRange subresourceRange = {};
//        subresourceRange.aspectMask = aspectMask;
//        subresourceRange.baseMipLevel = 0;
//        subresourceRange.levelCount = 1;
//        subresourceRange.layerCount = 1;
//        setImageLayout(cmdbuffer, image, oldImageLayout, newImageLayout, subresourceRange, srcStageMask, dstStageMask);
//    }
//
//    void insertImageMemoryBarrier(
//            VkCommandBuffer cmdbuffer,
//    VkImage image,
//    VkAccessFlags srcAccessMask,
//    VkAccessFlags dstAccessMask,
//    VkImageLayout oldImageLayout,
//    VkImageLayout newImageLayout,
//    VkPipelineStageFlags srcStageMask,
//    VkPipelineStageFlags dstStageMask,
//    VkImageSubresourceRange subresourceRange)
//    {
//        VkImageMemoryBarrier imageMemoryBarrier = vks::initializers::imageMemoryBarrier();
//        imageMemoryBarrier.srcAccessMask = srcAccessMask;
//        imageMemoryBarrier.dstAccessMask = dstAccessMask;
//        imageMemoryBarrier.oldLayout = oldImageLayout;
//        imageMemoryBarrier.newLayout = newImageLayout;
//        imageMemoryBarrier.image = image;
//        imageMemoryBarrier.subresourceRange = subresourceRange;
//
//        vkCmdPipelineBarrier(
//                cmdbuffer,
//                srcStageMask,
//                dstStageMask,
//                0,
//                0, nullptr,
//                0, nullptr,
//                1, &imageMemoryBarrier);
//    }

    fun exitFatal(message: String, exitCode: Int) {
        System.err.println(message)
        System.exit(exitCode)
    }

//    std::string readTextFile(const char *fileName)
//    {
//        std::string fileContent;
//        std::ifstream fileStream(fileName, std::ios::in);
//        if (!fileStream.is_open()) {
//            printf("File %s not found\n", fileName);
//            return "";
//        }
//        std::string line = "";
//        while (!fileStream.eof()) {
//            getline(fileStream, line);
//            fileContent.append(line + "\n");
//        }
//        fileStream.close();
//        return fileContent;
//    }
//
//    #if defined(__ANDROID__)
//    // Android shaders are stored as assets in the apk
//    // So they need to be loaded via the asset manager
//    VkShaderModule loadShader(AAssetManager* assetManager, const char *fileName, VkDevice device)
//    {
//        // Load shader from compressed asset
//        AAsset* asset = AAssetManager_open(assetManager, fileName, AASSET_MODE_STREAMING);
//        assert(asset);
//        size_t size = AAsset_getLength(asset);
//        assert(size > 0);
//
//        char *shaderCode = new char[size];
//        AAsset_read(asset, shaderCode, size);
//        AAsset_close(asset);
//
//        VkShaderModule shaderModule;
//        VkShaderModuleCreateInfo moduleCreateInfo;
//        moduleCreateInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
//        moduleCreateInfo.pNext = NULL;
//        moduleCreateInfo.codeSize = size;
//        moduleCreateInfo.pCode = (uint32_t*)shaderCode;
//        moduleCreateInfo.flags = 0;
//
//        VK_CHECK_RESULT(vkCreateShaderModule(device, &moduleCreateInfo, NULL, &shaderModule));
//
//        delete[] shaderCode;
//
//        return shaderModule;
//    }
//    #else
//    VkShaderModule loadShader(const char *fileName, VkDevice device)
//    {
//        std::ifstream is(fileName, std::ios::binary | std::ios::in | std::ios::ate);
//
//        if (is.is_open())
//        {
//            size_t size = is.tellg();
//            is.seekg(0, std::ios::beg);
//            char* shaderCode = new char[size];
//            is.read(shaderCode, size);
//            is.close();
//
//            assert(size > 0);
//
//            VkShaderModule shaderModule;
//            VkShaderModuleCreateInfo moduleCreateInfo{};
//            moduleCreateInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
//            moduleCreateInfo.codeSize = size;
//            moduleCreateInfo.pCode = (uint32_t*)shaderCode;
//
//            VK_CHECK_RESULT(vkCreateShaderModule(device, &moduleCreateInfo, NULL, &shaderModule));
//
//            delete[] shaderCode;
//
//            return shaderModule;
//        }
//        else
//        {
//            std::cerr << "Error: Could not open shader file \"" << fileName << "\"" << std::endl;
//            return VK_NULL_HANDLE;
//        }
//    }
//    #endif
//
//    VkShaderModule loadShaderGLSL(const char *fileName, VkDevice device, VkShaderStageFlagBits stage)
//    {
//        std::string shaderSrc = readTextFile(fileName);
//        const char *shaderCode = shaderSrc.c_str();
//        size_t size = strlen(shaderCode);
//        assert(size > 0);
//
//        VkShaderModule shaderModule;
//        VkShaderModuleCreateInfo moduleCreateInfo;
//        moduleCreateInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
//        moduleCreateInfo.pNext = NULL;
//        moduleCreateInfo.codeSize = 3 * sizeof(uint32_t) + size + 1;
//        moduleCreateInfo.pCode = (uint32_t*)malloc(moduleCreateInfo.codeSize);
//        moduleCreateInfo.flags = 0;
//
//        // Magic SPV number
//        ((uint32_t *)moduleCreateInfo.pCode)[0] = 0x07230203;
//        ((uint32_t *)moduleCreateInfo.pCode)[1] = 0;
//        ((uint32_t *)moduleCreateInfo.pCode)[2] = stage;
//        memcpy(((uint32_t *)moduleCreateInfo.pCode + 3), shaderCode, size + 1);
//
//        VK_CHECK_RESULT(vkCreateShaderModule(device, &moduleCreateInfo, NULL, &shaderModule));
//
//        return shaderModule;
//    }
//
//    bool fileExists(const std::string &filename)
//    {
//        std::ifstream f(filename.c_str());
//        return !f.fail();
//    }
}