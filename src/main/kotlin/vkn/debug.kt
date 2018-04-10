package vkn

import glfw_.appBuffer
import glfw_.appBuffer.ptr
import glfw_.advance
import glm_.L
import glm_.bool
import glm_.i
import glm_.set
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import uno.kotlin.buffers.toCollection
import unsigned.Uint
import unsigned.Ulong
import vkn.ArrayListLong.set
import java.nio.ByteBuffer
import java.nio.LongBuffer
import kotlin.reflect.KMutableProperty0

val String.utf8: ByteBuffer
    get() {
        val size = memLengthUTF8(this, true)
        val target = memByteBuffer(ptr.advance(size), size)
        memUTF8(this, true, target)
        return target
    }
val Long.utf8: String get() = MemoryUtil.memUTF8(this)


inline operator fun PointerBuffer.set(index: Int, string: String) {
    put(index, string.utf8)
}


fun PointerBuffer?.toArrayList(): ArrayList<String> {
    val count = this?.remaining() ?: 0
    if (this == null || count == 0) return arrayListOf()
    val res = ArrayList<String>(count)
    for (i in 0 until count)
        res += get(i).utf8
    return res
}

fun Collection<String>.toPointerBuffer(): PointerBuffer {
    val pointers = PointerBuffer.create(ptr.advance(Pointer.POINTER_SIZE * size), size)
    for (i in indices)
        pointers.put(i, elementAt(i).utf8)
    return pointers
}

inline fun <R> getLong(block: (LongBuffer) -> R): Long {
    val pLong = appBuffer.longBuffer
    block(pLong)
    return pLong[0]
}

inline fun <R> withLong(block: (LongBuffer) -> R): R = block(appBuffer.longBuffer)

inline fun <R> getPointer(block: (PointerBuffer) -> R): Long {
    val pointer = appBuffer.pointerBuffer
    block(pointer)
    return pointer[0]
}
inline fun <R> withPointer(block: (PointerBuffer) -> R): R = block(appBuffer.pointerBuffer)

fun vkCreateInstance(createInfo: VkInstanceCreateInfo, allocator: VkAllocationCallbacks?, instance: KMutableProperty0<VkInstance>)
        : VkResult {
    val pInstance = MemoryUtil.memAllocPointer(1)
    return VK10.vkCreateInstance(createInfo, allocator, pInstance).also {
        instance.set(VkInstance(pInstance[0], createInfo))
    }
}

fun ArrayList<VkDeviceQueueCreateInfo>.toBuffer(): VkDeviceQueueCreateInfo.Buffer {
    val buffer = VkDeviceQueueCreateInfo.calloc(size)
    for (i in indices)
        buffer += get(i)
    return buffer.flip()
}

operator fun VkDeviceQueueCreateInfo.Buffer.plusAssign(info: VkDeviceQueueCreateInfo) {
    put(info)
}

fun vkCreateDevice(physicalDevice: VkPhysicalDevice, createInfo: VkDeviceCreateInfo, allocator: VkAllocationCallbacks?,
                   device: KMutableProperty0<VkDevice?>): VkResult {
    val pDevice = MemoryUtil.memAllocPointer(1)
    return VK10.vkCreateDevice(physicalDevice, createInfo, allocator, pDevice).also {
        device.set(VkDevice(pDevice[0], physicalDevice, createInfo))
    }
}

fun vkCreateSemaphore(device: VkDevice, createInfo: VkSemaphoreCreateInfo, allocator: VkAllocationCallbacks?,
                      semaphore: KMutableProperty0<Long>): VkResult {
    val pSemaphore = MemoryUtil.memAllocLong(1)
    return VK10.vkCreateSemaphore(device, createInfo, allocator, pSemaphore).also {
        semaphore.set(pSemaphore[0])
    }
}

fun Long.toLongBuffer(): LongBuffer = MemoryUtil.memAllocLong(1).also { it[0] = this }

fun vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice: VkPhysicalDevice, count: Int, surface: VkSurfaceKHR): BooleanArray {
    val supported = MemoryUtil.memAllocInt(1)
    return BooleanArray(count) {
        KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, it, surface, supported)
        supported[0].bool
    }
}

fun vrGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice: VkPhysicalDevice, surface: VkSurfaceKHR,
                                         surfaceFormats: ArrayList<VkSurfaceFormatKHR>): VkResult {
    val formatCount = MemoryUtil.memAllocInt(1)
    KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null).check()
    assert(formatCount[0] > 0)
    val pSurfaceFormats = VkSurfaceFormatKHR.calloc(formatCount[0])
    return KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, pSurfaceFormats).also {
        pSurfaceFormats.toCollection(surfaceFormats)
    }
}

fun vkCreateCommandPool(device: VkDevice, createInfo: VkCommandPoolCreateInfo, allocator: VkAllocationCallbacks?,
                        commandPool: KMutableProperty0<VkCommandPool>): VkResult {
    val pCommandPool = MemoryUtil.memAllocLong(1)
    return VK10.vkCreateCommandPool(device, createInfo, allocator, pCommandPool).also {
        commandPool.set(pCommandPool[0])
    }
}

fun vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice: VkPhysicalDevice, surface: VkSurfaceKHR,
                                              presentModes: ArrayList<VkPresentModeKHR>): VkResult {
    val presentModeCount = MemoryUtil.memAllocInt(1)
    KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, null)
    assert(presentModeCount[0] > 0)
    val pPresentModes = MemoryUtil.memAllocInt(presentModeCount[0])
    return KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, pPresentModes).also {
        pPresentModes.toCollection(presentModes)
    }
}

fun vkCreateSwapchainKHR(device: VkDevice, createInfo: VkSwapchainCreateInfoKHR, allocator: VkAllocationCallbacks?,
                         swapchain: KMutableProperty0<VkSwapchainKHR>): VkResult {
    val pSwapchain = MemoryUtil.memAllocLong(1)
    return KHRSwapchain.vkCreateSwapchainKHR(device, createInfo, allocator, pSwapchain).also {
        swapchain.set(pSwapchain[0])
    }
}

fun vkGetSwapchainImagesKHR(device: VkDevice, swapchain: VkSwapchainKHR, images: ArrayList<VkImageView>): VkResult {
    val count = MemoryUtil.memAllocInt(1)
    val ret = KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, count, null)
    if (ret()) return ret
    val pImages = MemoryUtil.memAllocLong(count[0])
    return KHRSwapchain.vkGetSwapchainImagesKHR(device, swapchain, count, pImages).also {
        pImages.toCollection(images)
    }
}

fun vkCreateImageView(device: VkDevice, createInfo: VkImageViewCreateInfo, allocator: VkAllocationCallbacks?,
                      view: KMutableProperty0<VkImageView>): VkResult {
    val pView = MemoryUtil.memAllocLong(1)
    return VK10.vkCreateImageView(device, createInfo, allocator, pView).also {
        view.set(pView[0])
    }
}

fun vkAllocateCommandBuffers(device: VkDevice, allocateInfo: VkCommandBufferAllocateInfo, count: Int,
                             commandBuffers: ArrayList<VkCommandBuffer>): VkResult {
    val pCommandBuffer = MemoryUtil.memAllocPointer(count)
    return VK10.vkAllocateCommandBuffers(device, allocateInfo, pCommandBuffer).also {
        for (i in 0 until count)
            commandBuffers += VkCommandBuffer(pCommandBuffer[i], device)
    }
}

fun vkCreateImage(device: VkDevice, createInfo: VkImageCreateInfo, allocator: VkAllocationCallbacks?,
                  image: KMutableProperty0<VkImage>): VkResult {
    val pImage = MemoryUtil.memAllocLong(1)
    return VK10.vkCreateImage(device, createInfo, allocator, pImage).also {
        image.set(pImage[0])
    }
}

fun vkAllocateMemory(device: VkDevice, allocateInfo: VkMemoryAllocateInfo, allocator: VkAllocationCallbacks?,
                     memory: KMutableProperty0<VkDeviceMemory>): VkResult {
    val pMemory = MemoryUtil.memAllocLong(1)
    return VK10.vkAllocateMemory(device, allocateInfo, allocator, pMemory).also {
        memory.set(pMemory[0])
    }
}

fun vkCreateRenderPass(device: VkDevice, createInfo: VkRenderPassCreateInfo, allocator: VkAllocationCallbacks?,
                       renderPass: KMutableProperty0<VkRenderPass>): VkResult {
    val pRenderPass = MemoryUtil.memAllocLong(1)
    return VK10.vkCreateRenderPass(device, createInfo, allocator, pRenderPass).also {
        renderPass.set(pRenderPass[0])
    }
}

fun vkCreatePipelineCache(device: VkDevice, createInfo: VkPipelineCacheCreateInfo, allocator: VkAllocationCallbacks?,
                          pipelineCache: KMutableProperty0<VkPipelineCache>): VkResult {
    val pPipelineCache = MemoryUtil.memAllocLong(1)
    return VK10.vkCreatePipelineCache(device, createInfo, allocator, pPipelineCache).also {
        pipelineCache.set(pPipelineCache[0])
    }
}

fun vkCreateFramebuffer(device: VkDevice, createInfo: VkFramebufferCreateInfo, allocator: VkAllocationCallbacks?,
                        framebuffer: ArrayList<VkFramebuffer>, index: Int): VkResult {
    val pFramebuffer = MemoryUtil.memAllocLong(1)
    return VK10.vkCreateFramebuffer(device, createInfo, allocator, pFramebuffer).also {
        framebuffer[index] = pFramebuffer
    }
}

fun vkCreateFences(device: VkDevice, createInfo: VkFenceCreateInfo, allocator: VkAllocationCallbacks?, fences: ArrayList<VkFence>) {
    val pFence = MemoryUtil.memAllocLong(1)
    for (i in fences.indices) {
        VK10.vkCreateFence(device, createInfo, allocator, pFence)
        fences[i] = pFence
    }
}

fun vkCreateBuffer(device: VkDevice, createInfo: VkBufferCreateInfo, allocator: VkAllocationCallbacks?,
                   buffer: KMutableProperty0<VkBuffer>): VkResult {
    val pBuffer = MemoryUtil.memAllocLong(1)
    return VK10.vkCreateBuffer(device, createInfo, allocator, pBuffer).also {
        buffer.set(pBuffer[0])
    }
}

fun vkCreateDescriptorSetLayout(device: VkDevice, createInfo: VkDescriptorSetLayoutCreateInfo, allocator: VkAllocationCallbacks?,
                                setLayout: KMutableProperty0<VkDescriptorSetLayout>): VkResult {
    val pSetLayout = MemoryUtil.memAllocLong(1)
    return VK10.vkCreateDescriptorSetLayout(device, createInfo, allocator, pSetLayout).also {
        setLayout.set(pSetLayout[0])
    }
}

fun vkCreatePipelineLayout(device: VkDevice, createInfo: VkPipelineLayoutCreateInfo, allocator: VkAllocationCallbacks?,
                           pipelineLayout: KMutableProperty0<VkPipelineLayout>): VkResult {
    val pPipelineLayout = MemoryUtil.memAllocLong(1)
    return VK10.vkCreatePipelineLayout(device, createInfo, allocator, pPipelineLayout).also {
        pipelineLayout.set(pPipelineLayout[0])
    }
}



fun VkCommandBuffer.toPointerBuffer(): PointerBuffer {
    val p = MemoryUtil.memAllocPointer(1)
    p[0] = address()
    return p
}

val UINT32_MAX = Uint.MAX_VALUE.i
val UINT64_MAX = Ulong.MAX_VALUE.L