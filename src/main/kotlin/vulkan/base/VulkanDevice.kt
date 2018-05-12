package vulkan.base

import glm_.L
import glm_.i
import glm_.size
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugMarker.VK_EXT_DEBUG_MARKER_EXTENSION_NAME
import org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.*
import vkk.*
import vulkan.base.tools.DEFAULT_FENCE_TIMEOUT
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
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
    var properties: VkPhysicalDeviceProperties = VkPhysicalDeviceProperties.calloc()
    /** @brief Features of the physical device that an application can use to check if a feature is supported */
    var features: VkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc()
    /** @brief Features that have been enabled for use on the physical device */
    var enabledFeatures: VkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc()
    /** @brief Memory types and heaps of the physical device */
    val memoryProperties: VkPhysicalDeviceMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
    /** @brief Queue family properties of the physical device */
    val queueFamilyProperties: ArrayList<VkQueueFamilyProperties>
    /** @brief List of extensions supported by the device */
    val supportedExtensions: ArrayList<String>

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
        queueFamilyProperties = vk.getPhysicalDeviceQueueFamilyProperties(physicalDevice)
        // Get list of supported extensions
        supportedExtensions = vk.enumerateDeviceExtensionProperties(physicalDevice)
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
        if (commandPool != NULL) vkDestroyCommandPool(logicalDevice!!, commandPool, null)
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

    fun getMemoryType(typeBits: Int, property: VkMemoryProperty) = getMemoryType(typeBits, property.i)

    /**
     * Get the index of a queue family that supports the requested queue flags
     *
     * @param queueFlags VkQueueFlagBits, Queue flags to find a queue family index for
     *
     * @return Index of the queue family index that matches the flags
     *
     * @throw Throws an exception if no queue family index could be found that supports the requested flags
     */
    fun getQueueFamilyIndex(queueFlags: VkQueueFlag): Int {

        /*  Dedicated queue for compute
            Try to find a queue family index that supports compute but not graphics         */
        if (queueFlags == VkQueueFlag.COMPUTE_BIT)
            for (i in queueFamilyProperties.indices)
                if (queueFamilyProperties[i].queueFlags has queueFlags && queueFamilyProperties[i].queueFlags hasnt VkQueueFlag.GRAPHICS_BIT)
                    return i
        /*  Dedicated queue for transfer
            Try to find a queue family index that supports transfer but not graphics and compute         */
        if (queueFlags == VkQueueFlag.TRANSFER_BIT)
            for (i in queueFamilyProperties.indices)
                if (queueFamilyProperties[i].queueFlags has queueFlags && queueFamilyProperties[i].queueFlags hasnt VkQueueFlag.GRAPHICS_BIT
                        && queueFamilyProperties[i].queueFlags has VkQueueFlag.COMPUTE_BIT)
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
                            useSwapChain: Boolean = true,
                            requestedQueueTypes: VkQueueFlags = VkQueueFlag.GRAPHICS_BIT or VkQueueFlag.COMPUTE_BIT): VkResult {
        /*  Desired queues need to be requested upon logical device creation
            Due to differing queue family configurations of Vulkan implementations this can be a bit tricky,
            especially if the application requests different queue types    */

        val queueCreateInfos = ArrayList<VkDeviceQueueCreateInfo>()

        /*  Get queue family indices for the requested queue family types
            Note that the indices may overlap depending on the implementation         */

        val defaultQueuePriority = 0f

        queueFamilyIndices.graphics =
                if (requestedQueueTypes has VkQueueFlag.GRAPHICS_BIT) { // Graphics queue
                    queueCreateInfos += vk.DeviceQueueCreateInfo {
                        queueFamilyIndex = queueFamilyIndices.graphics
                        queuePriority = defaultQueuePriority
                    }
                    getQueueFamilyIndex(VkQueueFlag.GRAPHICS_BIT)
                } else NULL.i


        queueFamilyIndices.compute =
                if (requestedQueueTypes has VkQueueFlag.COMPUTE_BIT) {   // Dedicated compute queue
                    val compute = getQueueFamilyIndex(VkQueueFlag.COMPUTE_BIT)
                    if (compute != queueFamilyIndices.graphics) {
                        // If compute family index differs, we need an additional queue create info for the compute queue
                        queueCreateInfos += vk.DeviceQueueCreateInfo {
                            queueFamilyIndex = compute
                            queuePriority = defaultQueuePriority
                        }
                    }
                    compute
                } else queueFamilyIndices.graphics  // Else we use the same queue


        queueFamilyIndices.transfer =
                if (requestedQueueTypes has VkQueueFlag.TRANSFER_BIT) { // Dedicated transfer queue
                    val transfer = getQueueFamilyIndex(VkQueueFlag.TRANSFER_BIT)
                    if (transfer != queueFamilyIndices.graphics && transfer != queueFamilyIndices.compute) {
                        // If compute family index differs, we need an additional queue create info for the compute queue
                        queueCreateInfos += vk.DeviceQueueCreateInfo {
                            queueFamilyIndex = transfer
                            queuePriority = defaultQueuePriority
                        }
                    }
                    transfer
                } else queueFamilyIndices.graphics  // Else we use the same queue

        // Create the logical device representation
        val deviceExtensions = ArrayList<String>(enabledExtensions)
        if (useSwapChain)
        // If the device will be used for presenting to a display via a swapchain we need to request the swapchain extension
            deviceExtensions += VK_KHR_SWAPCHAIN_EXTENSION_NAME

        val deviceCreateInfo = vk.DeviceCreateInfo {
            this.queueCreateInfos = queueCreateInfos.toBuffer()
            this.enabledFeatures = enabledFeatures
        }

        // Enable the debug marker extension if it is present (likely meaning a debugging tool is present)
        if (extensionSupported(VK_EXT_DEBUG_MARKER_EXTENSION_NAME)) {
            deviceExtensions += VK_EXT_DEBUG_MARKER_EXTENSION_NAME
            enableDebugMarkers = true
        }

        if (deviceExtensions.isNotEmpty())
            deviceCreateInfo.enabledExtensionNames = deviceExtensions

        val result = vk.createDevice(physicalDevice, deviceCreateInfo, ::logicalDevice)

        if (result == VkResult.SUCCESS)
        // Create a default command pool for graphics command buffers
            commandPool = createCommandPool(queueFamilyIndices.graphics)

        this.enabledFeatures = enabledFeatures // TODO check allocations

        return result
    }


    /**
     * Create a buffer on the device
     *
     * @param usageFlags Usage flag bitmask for the buffer (i.e. index, vertex, uniform buffer)
     * @param memoryPropertyFlags Memory properties for this buffer (i.e. device local, host visible, coherent)
     * @param size Size of the buffer in byes
     * @param buffer Pointer to the buffer handle acquired by the function
     * @param memory Pointer to the memory handle acquired by the function
     * @param data Pointer to the data that should be copied to the buffer after creation (optional, if not set, no data is copied over)
     *
     * @return VK_SUCCESS if buffer handle and memory have been created and (optionally passed) data has been copied
     */
    fun createBuffer(usageFlags: VkBufferUsageFlags, memoryPropertyFlags: VkMemoryPropertyFlags, size: VkDeviceSize,
                     buffer: KMutableProperty0<VkBuffer>, memory: KMutableProperty0<VkDeviceMemory>, data: Long = NULL) {

        val dev = logicalDevice!!
        // Create the buffer handle
        val bufferCreateInfo = vk.BufferCreateInfo {
            usage = usageFlags
            this.size = size
            sharingMode = VkSharingMode.EXCLUSIVE
        }
        dev.createBuffer(bufferCreateInfo, buffer)

        // Create the memory backing up the buffer handle
        val memReqs = dev getBufferMemoryRequirements buffer()
        val memAlloc = vk.MemoryAllocateInfo {
            allocationSize = memReqs.size
            // Find a memory type index that fits the properties of the buffer
            memoryTypeIndex = getMemoryType(memReqs.memoryTypeBits, memoryPropertyFlags)
        }
        dev.allocateMemory(memAlloc, memory)

        // If a pointer to the buffer data has been passed, map the buffer and copy over the data
        if (data != NULL)
            dev.mappingMemory(memory(), 0, size) { mapped ->
                memCopy(data, mapped, size)
                // If host coherency hasn't been requested, do a manual flush to make writes visible
                if (memoryPropertyFlags hasnt VkMemoryProperty.HOST_COHERENT_BIT) {
//                    val mappedRange = vk.MappedMemoryRange{
//                        this.memory = memory()
//                        offset = 0
//                        this.size = size
//                    }
//                    dev.flushMappedMemoryRanges(mappedRange)
                }
            }

        // Attach the memory to the buffer object
        dev.bindBufferMemory(buffer(), memory())
    }

    fun createBuffer(usageFlags: VkBufferUsageFlags, memoryPropertyFlags: VkMemoryPropertyFlags, buffer: Buffer, bytes: ByteBuffer) {
        createBuffer(usageFlags, memoryPropertyFlags, buffer, bytes.size.L, memAddress(bytes))
    }

    fun createBuffer(usageFlags: VkBufferUsageFlags, memoryPropertyFlags: VkMemoryPropertyFlags, buffer: Buffer, floats: FloatBuffer) {
        createBuffer(usageFlags, memoryPropertyFlags, buffer, floats.size.L, memAddress(floats))
    }

    fun createBuffer(usageFlags: VkBufferUsageFlags, memoryPropertyFlags: VkMemoryPropertyFlags, buffer: Buffer, ints: IntBuffer) {
        createBuffer(usageFlags, memoryPropertyFlags, buffer, ints.size.L, memAddress(ints))
    }


    /**
     * Create a buffer on the device
     *
     * @param usageFlags Usage flag bitmask for the buffer (i.e. index, vertex, uniform buffer)
     * @param memoryPropertyFlags Memory properties for this buffer (i.e. device local, host visible, coherent)
     * @param buffer Pointer to a vk::Vulkan buffer object
     * @param size Size of the buffer in byes
     * @param data Pointer to the data that should be copied to the buffer after creation (optional, if not set, no data is copied over)
     *
     * @return VK_SUCCESS if buffer handle and memory have been created and (optionally passed) data has been copied
     */
    fun createBuffer(usageFlags: VkBufferUsageFlags, memoryPropertyFlags: VkMemoryPropertyFlags, buffer: Buffer, size: VkDeviceSize,
                     data: Long = NULL) {

        buffer.device = logicalDevice!!

        // Create the buffer handle
        buffer.buffer = logicalDevice!!.createBuffer(usageFlags, size)

        // Create the memory backing up the buffer handle
        val memReqs = logicalDevice!! getBufferMemoryRequirements buffer.buffer
        val memAlloc = vk.MemoryAllocateInfo {
            allocationSize = memReqs.size
            // Find a memory type index that fits the properties of the buffer
            memoryTypeIndex = getMemoryType(memReqs.memoryTypeBits, memoryPropertyFlags)
        }
        buffer.memory = logicalDevice!! allocateMemory memAlloc

        buffer.apply {
            alignment = memReqs.alignment
            this.size = memAlloc.allocationSize
            this.usageFlags = usageFlags
            this.memoryPropertyFlags = memoryPropertyFlags
        }
        // If a pointer to the buffer data has been passed, map the buffer and copy over the data
        if (data != NULL)
            buffer.mapping { dst -> memCopy(data, dst, size) }

        // Initialize a default descriptor that covers the whole buffer size
        buffer.setupDescriptor()

        // Attach the memory to the buffer object
        buffer.bind()
    }

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
//        assert(src->buffer)
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
    fun createCommandPool(queueFamilyIndex: Int, createFlags: VkCommandPoolCreateFlags = VkCommandPoolCreate.RESET_COMMAND_BUFFER_BIT.i)
            : VkCommandPool {

        val cmdPoolInfo = vk.CommandPoolCreateInfo {
            this.queueFamilyIndex = queueFamilyIndex
            flags = createFlags
        }
        return getLong { vk.createCommandPool(logicalDevice!!, cmdPoolInfo, it).check() }
    }


    /**
     * Allocate a command buffer from the command pool
     *
     * @param level Level of the new command buffer (primary or secondary)
     * @param (Optional) begin If true, recording on the new command buffer will be started (vkBeginCommandBuffer) (Defaults to false)
     *
     * @return A handle to the allocated command buffer
     */
    fun createCommandBuffer(level: VkCommandBufferLevel, begin: Boolean = false): VkCommandBuffer {

        val cmdBufAllocateInfo = vk.CommandBufferAllocateInfo(commandPool, level, 1)

        val cmdBuffer = logicalDevice!! allocateCommandBuffer cmdBufAllocateInfo

        // If requested, also start recording for the new command buffer
        if (begin)
            cmdBuffer begin vk.CommandBufferBeginInfo { }

        return cmdBuffer
    }

    /**
     * Finish command buffer recording and submit it to a queue
     *
     * @param commandBuffer Command buffer to flush
     * @param queue Queue to submit the command buffer to
     * @param free (Optional) Free the command buffer once it has been submitted (Defaults to true)
     *
     * @note The queue that the command buffer is submitted to must be from the same family index as the pool it was allocated from
     * @note Uses a fence to ensure command buffer has finished executing
     */
    fun flushCommandBuffer(commandBuffer: VkCommandBuffer, queue: VkQueue, free: Boolean = true) {

        if (commandBuffer.adr == NULL)
            return

        commandBuffer.end()

        val submitInfo = vk.SubmitInfo {
            commandBuffers = appBuffer pointerBufferOf commandBuffer
        }
        // Create fence to ensure that the command buffer has finished executing
        logicalDevice!!.withFence { fence ->
            // Submit to the queue
            queue.submit(submitInfo, fence)
            // Wait for the fence to signal that command buffer has finished executing
            logicalDevice!!.waitForFence(fence, true, DEFAULT_FENCE_TIMEOUT)
        }

        if (free)
            logicalDevice!!.freeCommandBuffer(commandPool, commandBuffer)
    }

    /**
     * Check if an extension is supported by the (physical device)
     *
     * @param extension Name of the extension to check
     *
     * @return True if the extension is supported (present in the list read at device creation time)
     */
    fun extensionSupported(extension: String) = supportedExtensions.contains(extension)
}