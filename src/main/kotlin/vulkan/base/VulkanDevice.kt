package vulkan.base

import gli_.has
import gli_.hasnt
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugMarker.VK_EXT_DEBUG_MARKER_EXTENSION_NAME
import org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.*
import vkn.*
import vkn.VkMemoryStack.Companion.withStack
import java.nio.LongBuffer
import kotlin.reflect.KMutableProperty0

class VulkanDevice
/**
 * Default constructor
 *
 * @param physicalDevice Physical device that is to be used
 */
constructor(
        /** @brief Physical device representation */
        val physicalDevice: VkPhysicalDevice) {

    /** @brief Logical device representation (application's view of the device) */
    var logicalDevice: VkDevice? = null
    /** @brief Properties of the physical device including limits that the application can check against */
    var properties = VkPhysicalDeviceProperties.calloc()
    /** @brief Features of the physical device that an application can use to check if a feature is supported */
    var features = VkPhysicalDeviceFeatures.calloc()
    /** @brief Features that have been enabled for use on the physical device */
    var enabledFeatures: VkPhysicalDeviceFeatures? = null
    /** @brief Memory types and heaps of the physical device */
    val memoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
    /** @brief Queue family properties of the physical device */
    val queueFamilyProperties = ArrayList<VkQueueFamilyProperties>()
    /** @brief List of extensions supported by the device */
    val supportedExtensions = ArrayList<String>()

    /** @brief Default command pool for the graphics queue family index */
    var commandPool: VkCommandPool = NULL

    /** @brief Set to true when the debug marker extension is detected */
    var enableDebugMarkers = false

    init {
        // Store Properties features, limits and properties of the physical device for later use
        // Device properties also contain limits and sparse properties
        vkGetPhysicalDeviceProperties(physicalDevice, properties)
        // Features should be checked by the examples before using them
        vkGetPhysicalDeviceFeatures(physicalDevice, features)
        // Memory properties are used regularly for creating all kinds of buffers
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties)
        // Queue family properties, used for setting up requested queues upon device creation
        vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, queueFamilyProperties)
        // Get list of supported extensions
        withStack { vkEnumerateDeviceExtensionProperties(physicalDevice, null, supportedExtensions) }
    }

    /** @brief Contains queue family indices */
    val queueFamilyIndices = QueueFamilyIndices()

    class QueueFamilyIndices {
        var graphics = 0
        var compute = 0
        var transfer = 0
    }

//    /**  @brief Typecast to VkDevice */
//    operator VkDevice() { return logicalDevice; }


    /**
     * Default destructor
     *
     * @note Frees the logical device
     */
    fun destroy() {
//        if (commandPool) vkDestroyCommandPool(logicalDevice, commandPool, nullptr)
        logicalDevice?.let { vkDestroyDevice(it, null) }
    }

    /**
     * Get the index of a memory type that has all the requested property bits set
     *
     * @param typeBits Bitmask with bits set for each memory type supported by the resource to request for (from VkMemoryRequirements)
     * @param properties Bitmask of properties for the memory type to request
     * @param (Optional) memTypeFound Pointer to a bool that is set to true if a matching memory type has been found
     *
     * @return Index of the requested memory type
     *
     * @throw Throws an exception if memTypeFound is null and no memory type could be found that supports the requested properties
     */
    fun getMemoryType(typeBits: Int, properties: VkMemoryPropertyFlags, memTypeFound: KMutableProperty0<Boolean>? = null): Int {
        var typeBits = typeBits
        for (i in 0 until memoryProperties.memoryTypeCount) {
            if ((typeBits and 1) == 1 && (memoryProperties.memoryTypes[i].propertyFlags and properties) == properties) {
                if (memTypeFound?.get() == true)
                    memTypeFound.set(true)
                return i
            }
            typeBits = typeBits ushr 1
        }

        if (memTypeFound?.get() == true) {
            memTypeFound.set(false)
            return 0
        } else
            throw RuntimeException("Could not find a matching memory type")
    }

    /**
     * Get the index of a queue family that supports the requested queue flags
     *
     * @param queueFlags VkQueueFlagBits, Queue flags to find a queue family index for
     *
     * @return Index of the queue family index that matches the flags
     *
     * @throw Throws an exception if no queue family index could be found that supports the requested flags
     */
    fun getQueueFamilyIndex(queueFlags: VkQueueFlagBits): Int {

        /*  Dedicated queue for compute
            Try to find a queue family index that supports compute but not graphics         */
        if (queueFlags has VkQueue_COMPUTE_BIT)
            for (i in queueFamilyProperties.indices)
                if (queueFamilyProperties[i].queueFlags has queueFlags && queueFamilyProperties[i].queueFlags hasnt VkQueue_GRAPHICS_BIT)
                    return i
        /*  Dedicated queue for transfer
            Try to find a queue family index that supports transfer but not graphics and compute         */
        if (queueFlags has VkQueue_TRANSFER_BIT)
            for (i in queueFamilyProperties.indices)
                if (queueFamilyProperties[i].queueFlags has queueFlags && queueFamilyProperties[i].queueFlags hasnt VkQueue_GRAPHICS_BIT
                        && queueFamilyProperties[i].queueFlags has VkQueue_COMPUTE_BIT)
                    return i
        // For other queue types or if no separate compute queue is present, return the first one to support the requested flags
        for (i in queueFamilyProperties.indices)
            if (queueFamilyProperties[i].queueFlags has queueFlags)
                return i

        throw Error("Could not find a matching queue family index")
    }

    /**
     * Create the logical device based on the assigned physical device, also gets default queue family indices
     *
     * @param enabledFeatures Can be used to enable certain features upon device creation
     * @param useSwapChain Set to false for headless rendering to omit the swapchain device extensions
     * @param requestedQueueTypes VkQueueFlags Bit flags specifying the queue types to be requested from the device
     *
     * @return VkResult of the device creation call
     */
    fun createLogicalDevice(enabledFeatures: VkPhysicalDeviceFeatures, enabledExtensions: ArrayList<String>,
                            useSwapChain: Boolean = true, requestedQueueTypes: Int = VkQueue_GRAPHICS_BIT or VkQueue_COMPUTE_BIT)
            : VkResult = withStack {
        /*  Desired queues need to be requested upon logical device creation
            Due to differing queue family configurations of Vulkan implementations this can be a bit tricky,
            especially if the application requests different queue types    */

        val queueCreateInfos = ArrayList<VkDeviceQueueCreateInfo>()

        /*  Get queue family indices for the requested queue family types
            Note that the indices may overlap depending on the implementation         */

        val defaultQueuePriority = floats(0f)

        queueFamilyIndices.graphics =
                if (requestedQueueTypes has VkQueue_GRAPHICS_BIT) { // Graphics queue
                    queueCreateInfos += cVkDeviceQueueCreateInfo {
                        type = VkStructureType_DEVICE_QUEUE_CREATE_INFO
                        queueFamilyIndex = queueFamilyIndices.graphics
                        queuePriorities = defaultQueuePriority
                    }
                    getQueueFamilyIndex(VkQueue_GRAPHICS_BIT)
                } else 0


        queueFamilyIndices.compute =
                if (requestedQueueTypes has VkQueue_COMPUTE_BIT) {   // Dedicated compute queue
                    if (queueFamilyIndices.compute != queueFamilyIndices.graphics) {
                        // If compute family index differs, we need an additional queue create info for the compute queue
                        queueCreateInfos += cVkDeviceQueueCreateInfo {
                            type = VkStructureType_DEVICE_QUEUE_CREATE_INFO
                            queueFamilyIndex = queueFamilyIndices.compute
                            queuePriorities = defaultQueuePriority
                        }
                    }
                    getQueueFamilyIndex(VkQueue_COMPUTE_BIT)
                } else queueFamilyIndices.graphics  // Else we use the same queue


        queueFamilyIndices.transfer =
                if (requestedQueueTypes has VkQueue_TRANSFER_BIT) { // Dedicated transfer queue
                    if (queueFamilyIndices.transfer != queueFamilyIndices.graphics && queueFamilyIndices.transfer != queueFamilyIndices.compute) {
                        // If compute family index differs, we need an additional queue create info for the compute queue
                        queueCreateInfos += cVkDeviceQueueCreateInfo {
                            type = VkStructureType_DEVICE_QUEUE_CREATE_INFO
                            queueFamilyIndex = queueFamilyIndices.transfer
                            queuePriorities = defaultQueuePriority
                        }
                    }
                    getQueueFamilyIndex(VK_QUEUE_TRANSFER_BIT)
                } else queueFamilyIndices.graphics  // Else we use the same queue

        // Create the logical device representation
        val deviceExtensions = ArrayList<String>(enabledExtensions)
        if (useSwapChain)
        // If the device will be used for presenting to a display via a swapchain we need to request the swapchain extension
            deviceExtensions += VK_KHR_SWAPCHAIN_EXTENSION_NAME

        val deviceCreateInfo = cVkDeviceCreateInfo {
            type = VkStructureType_DEVICE_CREATE_INFO
            this.queueCreateInfos = queueCreateInfos.toBuffer()
            this.enabledFeatures = enabledFeatures
        }

        // Enable the debug marker extension if it is present (likely meaning a debugging tool is present)
        if (extensionSupported(VK_EXT_DEBUG_MARKER_EXTENSION_NAME)) {
            deviceExtensions += VK_EXT_DEBUG_MARKER_EXTENSION_NAME
            enableDebugMarkers = true
        }

        if (deviceExtensions.isNotEmpty())
            deviceCreateInfo.enabledExtensionNames = deviceExtensions.toPointerBuffer()

        val result = vkCreateDevice(physicalDevice, deviceCreateInfo, null, ::logicalDevice)

        if (result == VK_SUCCESS)
        // Create a default command pool for graphics command buffers
            commandPool = createCommandPool(queueFamilyIndices.graphics)

        this@VulkanDevice.enabledFeatures = enabledFeatures

        result
    }

//    /**
//     * Create a buffer on the device
//     *
//     * @param usageFlags Usage flag bitmask for the buffer (i.e. index, vertex, uniform buffer)
//     * @param memoryPropertyFlags Memory properties for this buffer (i.e. device local, host visible, coherent)
//     * @param size Size of the buffer in byes
//     * @param buffer Pointer to the buffer handle acquired by the function
//     * @param memory Pointer to the memory handle acquired by the function
//     * @param data Pointer to the data that should be copied to the buffer after creation (optional, if not set, no data is copied over)
//     *
//     * @return VK_SUCCESS if buffer handle and memory have been created and (optionally passed) data has been copied
//     */
//    VkResult createBuffer(VkBufferUsageFlags usageFlags, VkMemoryPropertyFlags memoryPropertyFlags, VkDeviceSize size, VkBuffer *buffer, VkDeviceMemory *memory, void *data = nullptr)
//    {
//        // Create the buffer handle
//        VkBufferCreateInfo bufferCreateInfo = vks ::initializers::bufferCreateInfo(usageFlags, size)
//        bufferCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE
//        VK_CHECK_RESULT(vkCreateBuffer(logicalDevice, & bufferCreateInfo, nullptr, buffer))
//
//        // Create the memory backing up the buffer handle
//        VkMemoryRequirements memReqs
//        VkMemoryAllocateInfo memAlloc = vks ::initializers::memoryAllocateInfo()
//        vkGetBufferMemoryRequirements(logicalDevice, *buffer, & memReqs)
//        memAlloc.allocationSize = memReqs.size
//        // Find a memory type index that fits the properties of the buffer
//        memAlloc.memoryTypeIndex = getMemoryType(memReqs.memoryTypeBits, memoryPropertyFlags)
//        VK_CHECK_RESULT(vkAllocateMemory(logicalDevice, & memAlloc, nullptr, memory))
//
//        // If a pointer to the buffer data has been passed, map the buffer and copy over the data
//        if (data != nullptr) {
//            void * mapped
//            VK_CHECK_RESULT(vkMapMemory(logicalDevice, *memory, 0, size, 0, & mapped))
//            memcpy(mapped, data, size)
//            // If host coherency hasn't been requested, do a manual flush to make writes visible
//            if ((memoryPropertyFlags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) == 0)
//            {
//                VkMappedMemoryRange mappedRange = vks ::initializers::mappedMemoryRange()
//                mappedRange.memory = * memory
//                mappedRange.offset = 0
//                mappedRange.size = size
//                vkFlushMappedMemoryRanges(logicalDevice, 1, & mappedRange)
//            }
//            vkUnmapMemory(logicalDevice, *memory)
//        }
//
//        // Attach the memory to the buffer object
//        VK_CHECK_RESULT(vkBindBufferMemory(logicalDevice, *buffer, *memory, 0))
//
//        return VK_SUCCESS
//    }
//
//    /**
//     * Create a buffer on the device
//     *
//     * @param usageFlags Usage flag bitmask for the buffer (i.e. index, vertex, uniform buffer)
//     * @param memoryPropertyFlags Memory properties for this buffer (i.e. device local, host visible, coherent)
//     * @param buffer Pointer to a vk::Vulkan buffer object
//     * @param size Size of the buffer in byes
//     * @param data Pointer to the data that should be copied to the buffer after creation (optional, if not set, no data is copied over)
//     *
//     * @return VK_SUCCESS if buffer handle and memory have been created and (optionally passed) data has been copied
//     */
//    VkResult createBuffer(VkBufferUsageFlags usageFlags, VkMemoryPropertyFlags memoryPropertyFlags, vks::Buffer *buffer, VkDeviceSize size, void *data = nullptr)
//    {
//        buffer->device = logicalDevice
//
//        // Create the buffer handle
//        VkBufferCreateInfo bufferCreateInfo = vks ::initializers::bufferCreateInfo(usageFlags, size)
//        VK_CHECK_RESULT(vkCreateBuffer(logicalDevice, & bufferCreateInfo, nullptr, & buffer->buffer))
//
//        // Create the memory backing up the buffer handle
//        VkMemoryRequirements memReqs
//        VkMemoryAllocateInfo memAlloc = vks ::initializers::memoryAllocateInfo()
//        vkGetBufferMemoryRequirements(logicalDevice, buffer->buffer, &memReqs)
//        memAlloc.allocationSize = memReqs.size
//        // Find a memory type index that fits the properties of the buffer
//        memAlloc.memoryTypeIndex = getMemoryType(memReqs.memoryTypeBits, memoryPropertyFlags)
//        VK_CHECK_RESULT(vkAllocateMemory(logicalDevice, & memAlloc, nullptr, & buffer->memory))
//
//        buffer->alignment = memReqs.alignment
//        buffer->size = memAlloc.allocationSize
//        buffer->usageFlags = usageFlags
//        buffer->memoryPropertyFlags = memoryPropertyFlags
//
//        // If a pointer to the buffer data has been passed, map the buffer and copy over the data
//        if (data != nullptr) {
//            VK_CHECK_RESULT(buffer->map())
//            memcpy(buffer->mapped, data, size)
//            buffer->unmap()
//        }
//
//        // Initialize a default descriptor that covers the whole buffer size
//        buffer->setupDescriptor()
//
//        // Attach the memory to the buffer object
//        return buffer->bind()
//    }
//
//    /**
//     * Copy buffer data from src to dst using VkCmdCopyBuffer
//     *
//     * @param src Pointer to the source buffer to copy from
//     * @param dst Pointer to the destination buffer to copy tp
//     * @param queue Pointer
//     * @param copyRegion (Optional) Pointer to a copy region, if NULL, the whole buffer is copied
//     *
//     * @note Source and destionation pointers must have the approriate transfer usage flags set (TRANSFER_SRC / TRANSFER_DST)
//     */
//    void copyBuffer(vks::Buffer *src, vks::Buffer *dst, VkQueue queue, VkBufferCopy *copyRegion = nullptr)
//    {
//        assert(dst->size <= src->size)
//        assert(src->buffer && src->buffer)
//        VkCommandBuffer copyCmd = createCommandBuffer (VK_COMMAND_BUFFER_LEVEL_PRIMARY, true)
//        VkBufferCopy bufferCopy {}
//        if (copyRegion == nullptr) {
//            bufferCopy.size = src->size
//        } else {
//            bufferCopy = * copyRegion
//        }
//
//        vkCmdCopyBuffer(copyCmd, src->buffer, dst->buffer, 1, &bufferCopy)
//
//        flushCommandBuffer(copyCmd, queue)
//    }
//
    /**
     * Create a command pool for allocation command buffers from
     *
     * @param queueFamilyIndex Family index of the queue to create the command pool for
     * @param createFlags (Optional) Command pool creation flags (Defaults to VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
     *
     * @note Command buffers allocated from the created pool can only be submitted to a queue with the same family index
     *
     * @return A handle to the created command buffer
     */
    fun VkMemoryStack.createCommandPool(queueFamilyIndex: Int, createFlags: VkCommandPoolCreateFlags =
            VkCommandPoolCreate_RESET_COMMAND_BUFFER_BIT): VkCommandPool {

        val cmdPoolInfo = cVkCommandPoolCreateInfo {
            type = VkStructureType_COMMAND_POOL_CREATE_INFO
            this.queueFamilyIndex = queueFamilyIndex
            flags = createFlags
        }
        return withLong { vkCreateCommandPool(logicalDevice!!, cmdPoolInfo, null, it).check() }
    }

//
//    /**
//     * Allocate a command buffer from the command pool
//     *
//     * @param level Level of the new command buffer (primary or secondary)
//     * @param (Optional) begin If true, recording on the new command buffer will be started (vkBeginCommandBuffer) (Defaults to false)
//     *
//     * @return A handle to the allocated command buffer
//     */
//    VkCommandBuffer createCommandBuffer(VkCommandBufferLevel level, bool begin = false)
//    {
//        VkCommandBufferAllocateInfo cmdBufAllocateInfo = vks ::initializers::commandBufferAllocateInfo(commandPool, level, 1)
//
//        VkCommandBuffer cmdBuffer
//        VK_CHECK_RESULT(vkAllocateCommandBuffers(logicalDevice, & cmdBufAllocateInfo, & cmdBuffer))
//
//        // If requested, also start recording for the new command buffer
//        if (begin) {
//            VkCommandBufferBeginInfo cmdBufInfo = vks ::initializers::commandBufferBeginInfo()
//            VK_CHECK_RESULT(vkBeginCommandBuffer(cmdBuffer, & cmdBufInfo))
//        }
//
//        return cmdBuffer
//    }
//
//    /**
//     * Finish command buffer recording and submit it to a queue
//     *
//     * @param commandBuffer Command buffer to flush
//     * @param queue Queue to submit the command buffer to
//     * @param free (Optional) Free the command buffer once it has been submitted (Defaults to true)
//     *
//     * @note The queue that the command buffer is submitted to must be from the same family index as the pool it was allocated from
//     * @note Uses a fence to ensure command buffer has finished executing
//     */
//    void flushCommandBuffer(VkCommandBuffer commandBuffer, VkQueue queue, bool free = true)
//    {
//        if (commandBuffer == VK_NULL_HANDLE) {
//            return
//        }
//
//        VK_CHECK_RESULT(vkEndCommandBuffer(commandBuffer))
//
//        VkSubmitInfo submitInfo = vks ::initializers::submitInfo()
//        submitInfo.commandBufferCount = 1
//        submitInfo.pCommandBuffers = & commandBuffer
//
//        // Create fence to ensure that the command buffer has finished executing
//        VkFenceCreateInfo fenceInfo = vks ::initializers::fenceCreateInfo(VK_FLAGS_NONE)
//        VkFence fence
//        VK_CHECK_RESULT(vkCreateFence(logicalDevice, & fenceInfo, nullptr, & fence))
//
//        // Submit to the queue
//        VK_CHECK_RESULT(vkQueueSubmit(queue, 1, & submitInfo, fence))
//        // Wait for the fence to signal that command buffer has finished executing
//        VK_CHECK_RESULT(vkWaitForFences(logicalDevice, 1, & fence, VK_TRUE, DEFAULT_FENCE_TIMEOUT))
//
//        vkDestroyFence(logicalDevice, fence, nullptr)
//
//        if (free) {
//            vkFreeCommandBuffers(logicalDevice, commandPool, 1, & commandBuffer)
//        }
//    }

    /**
     * Check if an extension is supported by the (physical device)
     *
     * @param extension Name of the extension to check
     *
     * @return True if the extension is supported (present in the list read at device creation time)
     */
    fun extensionSupported(extension: String) = supportedExtensions.contains(extension)
}