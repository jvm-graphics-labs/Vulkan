package vkn

import glfw_.advance
import glfw_.appBuffer.ptr
import glm_.vec2.Vec2i
import glm_.vec3.Vec3i
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer


//fun pointerBufferOf(vararg strings: String): PointerBuffer {
//    val buf = pointerBufferBig(strings.size)
//    for (i in strings.indices)
//        buf[i] = strings[i]
//    return buf
//}
//
//operator fun PointerBuffer.set(index: Int, string: String) {
//    put(index, string.memUTF16)
//}
inline operator fun PointerBuffer.set(index: Int, long: Long) {
    put(index, long)
}

inline operator fun PointerBuffer.set(index: Int, pointer: Pointer) {
    put(index, pointer)
}

//operator fun PointerBuffer.plusAssign(string: String) {
//    put(string.stackUTF16)
//}

//operator fun <T> PointerBuffer.plusAssign(elements: Iterable<T>) {
//    for (item in elements)
//        if (item is String)
//            put(item.memUTF16)
//        else
//            throw Error()
//}
//
//fun PointerBuffer.isNotEmpty() = position() > 0


typealias VkCommandPool = Long
typealias VkSurfaceKHR = Long
typealias VkSwapchainKHR = Long
typealias VkImage = Long
typealias VkImageView = Long
typealias VkDeviceMemory = Long
typealias VkDeviceSize = Long
typealias VkRenderPass = Long
typealias VkPipelineCache = Long
typealias VkFramebuffer = Long
typealias VkSemaphore = Long
typealias VkFence = Long
typealias VkDescriptorPool = Long
typealias VkShaderModule = Long
typealias VkPipelineLayout = Long
typealias VkPipeline = Long
typealias VkDescriptorSetLayout = Long
typealias VkDescriptorSet = Long
typealias VkBuffer = Long

typealias VkSemaphorePtr = LongBuffer
typealias VkSwapchainKHRptr = LongBuffer
typealias VkResultPtr = IntBuffer
typealias VkSamplerPtr = LongBuffer
typealias VkImageViewPtr = LongBuffer


object ArrayListLong {
    operator fun ArrayList<Long>.set(index: Int, long: LongBuffer) {
        set(index, long[0])
    }

    infix fun ArrayList<Long>.resize(newSize: Int) {
        if (size < newSize)
            for (i in size until newSize)
                add(NULL)
        else if (size > newSize)
            for (i in size downTo newSize + 1)
                removeAt(lastIndex)
    }
}

// @formatter:off
inline fun vkDestroyDescriptorPool(device: VkDevice, descriptorPool: VkDescriptorPool, allocator: VkAllocationCallbacks? = null) = VK10.nvkDestroyDescriptorPool(device, descriptorPool, allocator?.address() ?: NULL)
inline fun vkDestroyRenderPass(device: VkDevice, renderPass: VkRenderPass, allocator: VkAllocationCallbacks? = null) = VK10.nvkDestroyRenderPass(device, renderPass, allocator?.address() ?: NULL)
inline fun vkDestroyFramebuffer(device: VkDevice, framebuffers: Iterable<VkFramebuffer>, allocator: VkAllocationCallbacks? = null) {
    for (i in framebuffers)
        VK10.nvkDestroyFramebuffer(device, i, allocator?.adr ?: NULL)
}

inline fun vkDestroyFence(device: VkDevice, fences: Iterable<VkFence>, allocator: VkAllocationCallbacks? = null) {
    for (i in fences)
        VK10.nvkDestroyFence(device, i, allocator?.address() ?: NULL)
}
// @formatter:on
inline fun vkDestroyImageView(device: VkDevice, imageView: VkImageView) = VK10.nvkDestroyImageView(device, imageView, NULL)

inline fun vkDestroyImage(device: VkDevice, image: VkImage) = VK10.nvkDestroyImage(device, image, NULL)
inline fun vkFreeMemory(device: VkDevice, memory: VkDeviceMemory) = VK10.nvkFreeMemory(device, memory, NULL)
inline fun vkDestroyPipelineCache(device: VkDevice, pipelineCache: VkPipelineCache) = VK10.nvkDestroyPipelineCache(device, pipelineCache, NULL)
inline fun vkDestroyCommandPool(device: VkDevice, commandPool: VkCommandPool) = VK10.nvkDestroyCommandPool(device, commandPool, NULL)
inline fun vkDestroySemaphores(device: VkDevice, semaphores: VkSemaphorePtr) {
    for (i in 0 until semaphores.remaining())
        VK10.nvkDestroySemaphore(device, semaphores[i], NULL)
}

inline fun vkDestroySemaphore(device: VkDevice, semaphore: VkSemaphore) = VK10.nvkDestroySemaphore(device, semaphore, NULL)

inline fun vkDestroyDebugReportCallback(instance: VkInstance, callbackAddress: Long) = EXTDebugReport.nvkDestroyDebugReportCallbackEXT(instance, callbackAddress, NULL)
inline fun vkDestroyInstance(instance: VkInstance) = VK10.nvkDestroyInstance(instance, NULL)
inline fun vkDestroyPipeline(device: VkDevice, pipeline: VkPipeline) = VK10.nvkDestroyPipeline(device, pipeline, NULL)
inline fun vkDestroyPipelineLayout(device: VkDevice, pipelineLayout: VkPipelineLayout) = VK10.nvkDestroyPipelineLayout(device, pipelineLayout, NULL)
inline fun vkDestroyDescriptorSetLayout(device: VkDevice, descriptorSetLayout: VkDescriptorSetLayout) = VK10.nvkDestroyDescriptorSetLayout(device, descriptorSetLayout, NULL)
inline fun vkDestroyBuffer(device: VkDevice, buffer: VkBuffer) = VK10.nvkDestroyBuffer(device, buffer, NULL)
inline fun vkDestroyShaderModule(device: VkDevice, shaderModule: VkShaderModule) = VK10.vkDestroyShaderModule(device, shaderModule, null)


val FloatBuffer.adr get() = MemoryUtil.memAddress(this)
val IntBuffer.adr get() = MemoryUtil.memAddress(this)

inline val Pointer.adr get() = address()


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