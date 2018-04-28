package vulkan.base

import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkImageSubresourceRange
import org.lwjgl.vulkan.VkPhysicalDevice
import uno.buffer.toBuffer
import uno.buffer.use
import vkn.*
import java.io.File
import java.nio.ByteBuffer
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

    /** Create an image memory barrier for changing the layout of an image and put it into an active command buffer
     *  See chapter 11.4 "Image Layout" for details */
    fun setImageLayout(
            cmdbuffer: VkCommandBuffer,
            image: VkImage,
            oldImageLayout: VkImageLayout,
            newImageLayout: VkImageLayout,
            subresourceRange: VkImageSubresourceRange,
            srcStageMask: VkPipelineStageFlags = VkPipelineStage.ALL_COMMANDS_BIT.i,
            dstStageMask: VkPipelineStageFlags = VkPipelineStage.ALL_COMMANDS_BIT.i) {

        // Create an image barrier object
        val imageMemoryBarrier = vk.ImageMemoryBarrier {
            oldLayout = oldImageLayout
            newLayout = newImageLayout
            this.image = image
            this.subresourceRange = subresourceRange
        }
        /*  Source layouts (old)
            Source access mask controls actions that have to be finished on the old layout before
            it will be transitioned to the new layout */
        imageMemoryBarrier.srcAccessMask = when (oldImageLayout) {

        /*  Image layout is undefined (or does not matter)
            Only valid as initial layout
            No flags required, listed only for completeness */
            VkImageLayout.UNDEFINED -> 0

        /*  Image is preinitialized
            Only valid as initial layout for linear images, preserves memory contents
            Make sure host writes have been finished */
            VkImageLayout.PREINITIALIZED -> VkAccess.HOST_WRITE_BIT.i

        /*  Image is a color attachment
            Make sure any writes to the color buffer have been finished         */
            VkImageLayout.COLOR_ATTACHMENT_OPTIMAL -> VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i

        /*  Image is a depth/stencil attachment
            Make sure any writes to the depth/stencil buffer have been finished         */
            VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> VkAccess.DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.i

        /*  Image is a transfer source
            Make sure any reads from the image have been finished         */
            VkImageLayout.TRANSFER_SRC_OPTIMAL -> VkAccess.TRANSFER_READ_BIT.i

        /*  Image is a transfer destination
            Make sure any writes to the image have been finished         */
            VkImageLayout.TRANSFER_DST_OPTIMAL -> VkAccess.TRANSFER_WRITE_BIT.i

        /*  Image is read by a shader
            Make sure any shader reads from the image have been finished         */
            VkImageLayout.SHADER_READ_ONLY_OPTIMAL -> VkAccess.SHADER_READ_BIT.i

        // Other source layouts aren't handled (yet)
            else -> imageMemoryBarrier.srcAccessMask
        }

        /*  Target layouts (new)
            Destination access mask controls the dependency for the new image layout         */
        imageMemoryBarrier.dstAccessMask = when (newImageLayout) {

        /*  Image will be used as a transfer destination
            Make sure any writes to the image have been finished         */
            VkImageLayout.TRANSFER_DST_OPTIMAL -> VkAccess.TRANSFER_WRITE_BIT.i

        /*  Image will be used as a transfer source
            Make sure any reads from the image have been finished         */
            VkImageLayout.TRANSFER_SRC_OPTIMAL -> VkAccess.TRANSFER_READ_BIT.i

        /*  Image will be used as a color attachment
            Make sure any writes to the color buffer have been finished         */
            VkImageLayout.COLOR_ATTACHMENT_OPTIMAL -> VkAccess.COLOR_ATTACHMENT_WRITE_BIT.i

        /*  Image layout will be used as a depth/stencil attachment
            Make sure any writes to depth/stencil buffer have been finished         */
            VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL -> imageMemoryBarrier.dstAccessMask or VkAccess.DEPTH_STENCIL_ATTACHMENT_WRITE_BIT

        /*  Image will be read in a shader (sampler, input attachment)
            Make sure any writes to the image have been finished         */
            VkImageLayout.SHADER_READ_ONLY_OPTIMAL -> {
                if (imageMemoryBarrier.srcAccessMask == 0)
                    imageMemoryBarrier.srcAccessMask = VkAccess.HOST_WRITE_BIT or VkAccess.TRANSFER_WRITE_BIT
                VkAccess.SHADER_READ_BIT.i
            }

        // Other source layouts aren't handled (yet)
            else -> imageMemoryBarrier.dstAccessMask
        }

        // Put barrier inside setup command buffer
        cmdbuffer.pipelineBarrier(
                srcStageMask,
                dstStageMask,
                0,
                null,
                null,
                imageMemoryBarrier)
    }

    // Fixed sub resource on first mip level and layer
    fun setImageLayout(
            cmdbuffer: VkCommandBuffer,
            image: VkImage,
            aspectMask: VkImageAspectFlags,
            oldImageLayout: VkImageLayout,
            newImageLayout: VkImageLayout,
            srcStageMask: VkPipelineStageFlags = VkPipelineStage.ALL_COMMANDS_BIT.i,
            dstStageMask: VkPipelineStageFlags = VkPipelineStage.ALL_COMMANDS_BIT.i) {
        val subresourceRange = vk.ImageSubresourceRange {
            this.aspectMask = aspectMask
            baseMipLevel = 0
            levelCount = 1
            layerCount = 1
        }
        setImageLayout(cmdbuffer, image, oldImageLayout, newImageLayout, subresourceRange, srcStageMask, dstStageMask)
    }
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

    fun exitFatal(message: String, exitCode: VkResult): Nothing = exitFatal(message, exitCode.i)
    fun exitFatal(message: String, exitCode: Int): Nothing = throw Error("$message, exitCode $exitCode")

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

    /** Vulkan loads its shaders from an immediate binary representation called SPIR-V
     *  Shaders are compiled offline from e.g. GLSL using the reference glslang compiler
     *  This function loads such a shader from a binary file and returns a shader module structure  */
    infix fun VkDevice.loadShader(filename: String): VkShaderModule {

        val file = File(ClassLoader.getSystemResource(filename).toURI())

        var shaderModule = MemoryUtil.NULL

        if (file.exists() && file.canRead()) {

            shaderModule = file.readBytes().toBuffer().use { loadShader(it) }
        } else
            System.err.println("Error: Could not open shader file \"$filename\"")

        return shaderModule
    }

    infix fun VkDevice.loadShader(buffer: ByteBuffer): VkShaderModule {
        // Create a new shader module that will be used for pipeline creation
        val moduleCreateInfo = vk.ShaderModuleCreateInfo { code = buffer }

        return createShaderModule(moduleCreateInfo)
    }

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