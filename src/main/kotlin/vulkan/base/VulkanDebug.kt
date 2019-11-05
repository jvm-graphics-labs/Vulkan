package vulkan.base

import glm_.vec4.Vec4
import imgui.plusAssign
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_FALSE
import vkk.*
import vkk.entities.VkDebugReportCallback
import vkk.entities.VkSemaphore
import vkk.extensionFunctions.createDebugReportCallbackEXT
import vkk.extensionFunctions.destroyDebugReportCallbackEXT
import java.nio.ByteBuffer

object debug {

    /** Default validation layers   */
    val validationLayerNames = arrayListOf("VK_LAYER_LUNARG_standard_validation")

    var msgCallback = VkDebugReportCallback.NULL

    /** Default debug callback  */
    val messageCallback = VkDebugReportCallbackEXT.create { flags, _, _, _, msgCode, layerPrefix, msg, _ ->
        /*  Select prefix depending on flags passed to the callback
            Note that multiple flags may be set for a single validation message         */
        val prefix = StringBuilder()

        // Error that may result in undefined behaviour
        if (flags has VkDebugReport.ERROR_BIT_EXT)
            prefix += "ERROR"
        // Warnings may hint at unexpected / non-spec API usage
        if (flags has VkDebugReport.WARNING_BIT_EXT)
            prefix += "WARNING"
        // May indicate sub-optimal usage of the API
        if (flags has VkDebugReport.PERFORMANCE_WARNING_BIT_EXT)
            prefix += "PERFORMANCE"
        // Informal messages that may become handy during debugging
        if (flags has VkDebugReport.INFORMATION_BIT_EXT)
            prefix += "INFO"
        /*  Diagnostic info from the Vulkan loader and layers
            Usually not helpful in terms of API usage, but may help to debug layer and loader problems         */
        if (flags has VkDebugReport.DEBUG_BIT_EXT)
            prefix += "DEBUG"

        // Display message to default output (console/logcat)
        val debugMessage = "$prefix: [$layerPrefix] Code $msgCode : $msg"

        when {
            flags has VkDebugReport.ERROR_BIT_EXT -> System.err
            else -> System.out
        }.println(debugMessage)

        /*  The return value of this callback controls wether the Vulkan call that caused
            the validation message will be aborted or not
            We return VK_FALSE as we DON'T want Vulkan calls that cause a validation message
            (and return a VkResult) to abort
            If you instead want to have calls abort, pass in VK_TRUE and the function will
            return VK_ERROR_VALIDATION_FAILED_EXT   */
        VK_FALSE
    }

    /** Load debug function pointers and set debug callback
     *  @param flags = VkDebugReportFlagBitsEXT */
    fun setupDebugging(instance: VkInstance, flags: VkDebugReportFlagsEXT, callBack: VkDebugReportCallbackEXT?) {

        val dbgCreateInfo = vk.DebugReportCallbackCreateInfoEXT {
            this.callback = callBack ?: messageCallback
            this.flags = flags
        }

        msgCallback = instance createDebugReportCallbackEXT dbgCreateInfo
    }

    /** Clear debug callback    */
    fun freeDebugCallback(instance: VkInstance) {
        if (msgCallback.L != NULL)
            instance destroyDebugReportCallbackEXT msgCallback
    }
}

/** Setup and functions for the VK_EXT_debug_marker_extension
 *  Extension spec can be found at https://github.com/KhronosGroup/Vulkan-Docs/blob/1.0-VK_EXT_debug_marker/doc/specs/vulkan/appendices/VK_EXT_debug_marker.txt
 *  Note that the extension will only be present if run from an offline debugging application
 *  The actual check for extension presence and enabling it on the device is done in the example base class
 *  See VulkanExampleBase::createInstance and VulkanExampleBase::createDevice (base/vulkanexamplebase.cpp)  */
object debugMarker {

    /** Set to true if function pointer for the debug marker are available */
    var active = false

    lateinit var device: VkDevice

    /** Sets the debug name of an object
     *  All Objects in Vulkan are represented by their 64-bit handles which are passed into this function
     *  along with the object type */
//    fun setObjectName(device: VkDevice, `object`: VkObject, objectType: VkDebugReportObjectType, name: String) {
//        setObjectName(device, `object`.L, objectType, name)
//    }
//
//    /** Sets the debug name of an object
//     *  All Objects in Vulkan are represented by their 64-bit handles which are passed into this function
//     *  along with the object type */
//    fun setObjectName(device: VkDevice, pointer: Pointer, objectType: VkDebugReportObjectType, name: String) {
//        setObjectName(device, pointer.adr, objectType, name)
//    }
//
//    /** Sets the debug name of an object
//     *  All Objects in Vulkan are represented by their 64-bit handles which are passed into this function
//     *  along with the object type */
//    fun setObjectName(device: VkDevice, `object`: Long, objectType: VkDebugReportObjectType, name: String) {
//        // Check for valid function pointer (may not be present if not running in a debugging application)
//        if (active) {
//            val nameInfo = vk.DebugMarkerObjectNameInfoEXT {
//                this.objectType = objectType
//                this.`object` = `object`
//                objectName = name
//            }
//            device debugMarkerSetObjectName nameInfo
//        }
//    }
//
//    /** Set the tag for an object */
//    fun setObjectTag(device: VkDevice, `object`: Long, objectType: VkDebugReportObjectType, name: String, tag: ByteBuffer) {
//
//        // Check for valid function pointer (may not be present if not running in a debugging application)
//        if (active) {
//            val tagInfo = vk.DebugMarkerObjectTagInfoEXT {
//                this.objectType = objectType
//                this.`object` = `object`
//                tagName = name
//                this.tag = tag
//            }
//            device debugMarkerSetObjectTag tagInfo
//        }
//    }
//
//    fun withRegion(cmdBuffer: VkCommandBuffer, markerName: String, color: Vec4, block: () -> Unit) {
//        beginRegion(cmdBuffer, markerName, color)
//        block()
//        endRegion(cmdBuffer)
//    }
//
//    /** Start a new debug marker region */
//    fun beginRegion(cmdBuffer: VkCommandBuffer, markerName: String, color: Vec4) {
//        // Check for valid function pointer (may not be present if not running in a debugging application)
//        if (active) {
//            val markerInfo = vk.DebugMarkerMarkerInfoEXT {
//                this.color(color)
//                this.markerName = markerName
//            }
//            cmdBuffer debugMarkerBegin markerInfo
//        }
//    }
//
//    /** Insert a new debug marker into the command buffer */
//    fun insert(cmdBuffer: VkCommandBuffer, markerName: String, color: Vec4) {
//        // Check for valid function pointer (may not be present if not running in a debugging application)
//        if (active) {
//            val markerInfo = vk.DebugMarkerMarkerInfoEXT {
//                this.color(color)
//                this.markerName = markerName
//            }
//            cmdBuffer debugMarkerInsert markerInfo
//        }
//    }
//
//    /** End the current debug marker region */
//    fun endRegion(cmdBuffer: VkCommandBuffer) {
//        // Check for valid function (may not be present if not runnin in a debugging application)
//        if (active)
//            cmdBuffer.debugMarkerEnd()
//    }
//
//    // TODO remove device?
//    // Object specific naming functions
//    fun setCommandBufferName(device: VkDevice, cmdBuffer: VkCommandBuffer, name: String) {
//        setObjectName(device, cmdBuffer, VkDebugReportObjectType.COMMAND_BUFFER_EXT, name)
//    }
//
//    fun setQueueName(device: VkDevice, queue: VkQueue, name: String) {
//        setObjectName(device, queue, VkDebugReportObjectType.QUEUE_EXT, name)
//    }
//
//    fun setImageName(device: VkDevice, image: VkImage, name: String) {
//        setObjectName(device, image, VkDebugReportObjectType.IMAGE_EXT, name)
//    }
//
//    fun setSamplerName(device: VkDevice, sampler: VkSampler, name: String) {
//        setObjectName(device, sampler, VkDebugReportObjectType.SAMPLER_EXT, name)
//    }
//
//    fun setBufferName(device: VkDevice, buffer: VkBuffer, name: String) {
//        setObjectName(device, buffer, VkDebugReportObjectType.BUFFER_EXT, name)
//    }
//
//    fun setDeviceMemoryName(device: VkDevice, memory: VkDeviceMemory, name: String) {
//        setObjectName(device, memory, VkDebugReportObjectType.DEVICE_MEMORY_EXT, name)
//    }
//
//    fun setShaderModuleName(device: VkDevice, shaderModule: VkShaderModule, name: String) {
//        setObjectName(device, shaderModule, VkDebugReportObjectType.SHADER_MODULE_EXT, name)
//    }
//
//    fun setPipelineName(device: VkDevice, pipeline: VkPipeline, name: String) {
//        setObjectName(device, pipeline, VkDebugReportObjectType.PIPELINE_EXT, name)
//    }
//
//    fun setPipelineLayoutName(device: VkDevice, pipelineLayout: VkPipelineLayout, name: String) {
//        setObjectName(device, pipelineLayout, VkDebugReportObjectType.PIPELINE_LAYOUT_EXT, name)
//    }
//
//    fun setRenderPassName(device: VkDevice, renderPass: VkRenderPass, name: String) {
//        setObjectName(device, renderPass, VkDebugReportObjectType.RENDER_PASS_EXT, name)
//    }
//
//    fun setFramebufferName(device: VkDevice, framebuffer: VkFramebuffer, name: String) {
//        setObjectName(device, framebuffer, VkDebugReportObjectType.FRAMEBUFFER_EXT, name)
//    }
//
//    fun setDescriptorSetLayoutName(device: VkDevice, descriptorSetLayout: VkDescriptorSetLayout, name: String) {
//        setObjectName(device, descriptorSetLayout, VkDebugReportObjectType.DESCRIPTOR_SET_LAYOUT_EXT, name)
//    }
//
//    fun setDescriptorSetName(device: VkDevice, descriptorSet: VkDescriptorSet, name: String) {
//        setObjectName(device, descriptorSet, VkDebugReportObjectType.DESCRIPTOR_SET_EXT, name)
//    }
//
//    fun setSemaphoreName(device: VkDevice, semaphore: VkSemaphore, name: String) {
//        setObjectName(device, semaphore, VkDebugReportObjectType.SEMAPHORE_EXT, name)
//    }
//
//    fun setFenceName(device: VkDevice, fence: VkFence, name: String) {
//        setObjectName(device, fence, VkDebugReportObjectType.FENCE_EXT, name)
//    }
//
//    fun setEventName(device: VkDevice, event: VkEvent, name: String) {
//        setObjectName(device, event, VkDebugReportObjectType.EVENT_EXT, name)
//    }
}