package vulkan.base

import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkInstance
import uno.kotlin.plusAssign
import vkk.*

object debug {

    /** Default validation layers   */
    val validationLayerNames = arrayListOf("VK_LAYER_LUNARG_standard_validation")

    var msgCallback: VkDebugReportCallback = NULL

    /** Default debug callback  */
    val messageCallback: VkDebugReportCallbackFunc = { flags, objType, srcObject, location, msgCode, layerPrefix, msg, userData ->
        /*  Select prefix depending on flags passed to the callback
            Note that multiple flags may be set for a single validation message         */
        val prefix = StringBuilder()

        // Error that may result in undefined behaviour
        if (flags has VkDebugReport.ERROR_BIT_EXT)
            prefix += "ERROR:"
        // Warnings may hint at unexpected / non-spec API usage
        if (flags has VkDebugReport.WARNING_BIT_EXT)
            prefix += "WARNING:"
        // May indicate sub-optimal usage of the API
        if (flags has VkDebugReport.PERFORMANCE_WARNING_BIT_EXT)
            prefix += "PERFORMANCE:"
        // Informal messages that may become handy during debugging
        if (flags has VkDebugReport.INFORMATION_BIT_EXT)
            prefix += "INFO:"
        /*  Diagnostic info from the Vulkan loader and layers
            Usually not helpful in terms of API usage, but may help to debug layer and loader problems         */
        if (flags has VkDebugReport.DEBUG_BIT_EXT)
            prefix += "DEBUG:"

        // Display message to default output (console/logcat)
        val debugMessage = "$prefix [$layerPrefix] Code $msgCode : $msg"

        if (flags has VkDebugReport.ERROR_BIT_EXT)
            System.err.println(debugMessage)
        else
            println(debugMessage)

        /*  The return value of this callback controls wether the Vulkan call that caused
            the validation message will be aborted or not
            We return VK_FALSE as we DON'T want Vulkan calls that cause a validation message
            (and return a VkResult) to abort
            If you instead want to have calls abort, pass in VK_TRUE and the function will
            return VK_ERROR_VALIDATION_FAILED_EXT   */
        false
    }

    /** Load debug function pointers and set debug callback
     *  @param flags = VkDebugReportFlagBitsEXT */
    fun setupDebugging(instance: VkInstance, flags: Int, callBack: VkDebugReportCallbackFunc?) {

        val dbgCreateInfo = vk.DebugReportCallbackCreateInfoEXT {
            this.callback = callBack ?: messageCallback
            this.flags = flags
        }

        msgCallback = instance createDebugReportCallbackEXT dbgCreateInfo
    }

    /** Clear debug callback    */
    fun freeDebugCallback(instance: VkInstance) {
        if (msgCallback != NULL)
            instance destroyDebugReportCallbackEXT msgCallback
    }
}

/** Setup and functions for the VK_EXT_debug_marker_extension
 *  Extension spec can be found at https://github.com/KhronosGroup/Vulkan-Docs/blob/1.0-VK_EXT_debug_marker/doc/specs/vulkan/appendices/VK_EXT_debug_marker.txt
 *  Note that the extension will only be present if run from an offline debugging application
 *  The actual check for extension presence and enabling it on the device is done in the example base class
 *  See VulkanExampleBase::createInstance and VulkanExampleBase::createDevice (base/vulkanexamplebase.cpp)  */
object debugMarker {

//    // Set to true if function pointer for the debug marker are available
//    extern bool active;
//
    /** Get function pointers for the debug report extensions from the device   */
    fun setup(device: VkDevice) {

    }
//
//    // Sets the debug name of an object
//    // All Objects in Vulkan are represented by their 64-bit handles which are passed into this function
//    // along with the object type
//    void setObjectName (VkDevice device, uint64_t object, VkDebugReportObjectTypeEXT objectType, const char *name);
//
//    // Set the tag for an object
//    void setObjectTag (VkDevice device, uint64_t object, VkDebugReportObjectTypeEXT objectType, uint64_t name, size_t tagSize, const void* tag);
//
//    // Start a new debug marker region
//    void beginRegion (VkCommandBuffer cmdbuffer, const char* pMarkerName, glm::vec4 color);
//
//    // Insert a new debug marker into the command buffer
//    void insert (VkCommandBuffer cmdbuffer, std::string markerName, glm::vec4 color);
//
//    // End the current debug marker region
//    void endRegion (VkCommandBuffer cmdBuffer);
//
//    // Object specific naming functions
//    void setCommandBufferName (VkDevice device, VkCommandBuffer cmdBuffer, const char * name);
//    void setQueueName (VkDevice device, VkQueue queue, const char * name);
//    void setImageName (VkDevice device, VkImage image, const char * name);
//    void setSamplerName (VkDevice device, VkSampler sampler, const char * name);
//    void setBufferName (VkDevice device, VkBuffer buffer, const char * name);
//    void setDeviceMemoryName (VkDevice device, VkDeviceMemory memory, const char * name);
//    void setShaderModuleName (VkDevice device, VkShaderModule shaderModule, const char * name);
//    void setPipelineName (VkDevice device, VkPipeline pipeline, const char * name);
//    void setPipelineLayoutName (VkDevice device, VkPipelineLayout pipelineLayout, const char * name);
//    void setRenderPassName (VkDevice device, VkRenderPass renderPass, const char * name);
//    void setFramebufferName (VkDevice device, VkFramebuffer framebuffer, const char * name);
//    void setDescriptorSetLayoutName (VkDevice device, VkDescriptorSetLayout descriptorSetLayout, const char * name);
//    void setDescriptorSetName (VkDevice device, VkDescriptorSet descriptorSet, const char * name);
//    void setSemaphoreName (VkDevice device, VkSemaphore semaphore, const char * name);
//    void setFenceName (VkDevice device, VkFence fence, const char * name);
//    void setEventName (VkDevice device, VkEvent _event, const char * name);
}