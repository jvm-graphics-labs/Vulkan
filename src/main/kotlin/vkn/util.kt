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

const val VK_WHOLE_SIZE = 0L.inv()

typealias VkBuffer = Long
typealias VkCommandPool = Long
typealias VkDebugReportCallbackEXT = Long
typealias VkDescriptorPool = Long
typealias VkDescriptorSet = Long
typealias VkDescriptorSetLayout = Long
typealias VkDeviceMemory = Long
typealias VkDeviceSize = Long
typealias VkFence = Long
typealias VkFramebuffer = Long
typealias VkImage = Long
typealias VkImageView = Long
typealias VkPipeline = Long
typealias VkPipelineCache = Long
typealias VkPipelineLayout = Long
typealias VkRenderPass = Long
typealias VkSampler = Long
typealias VkSemaphore = Long
typealias VkShaderModule = Long
typealias VkSurfaceKHR = Long
typealias VkSwapchainKHR = Long

typealias VkSemaphorePtr = LongBuffer
typealias VkSwapchainKHRptr = LongBuffer
typealias VkResultPtr = IntBuffer
typealias VkSamplerPtr = LongBuffer
typealias VkImageViewPtr = LongBuffer


object LongArrayList {
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
object VkPhysicalDeviceArrayList {
//    operator fun ArrayList<VkPhysicalDevice>.set(index: Int, long: LongBuffer) {
//        set(index, long[0])
//    }

    infix fun ArrayList<VkPhysicalDevice>.resize(newSize: Int) {
        if (size < newSize) TODO()
//            for (i in size until newSize)
//                add(VkPhysicalDevice())
        else if (size > newSize)
            for (i in size downTo newSize + 1)
                removeAt(lastIndex)
    }
}






inline fun vkDestroySemaphores(device: VkDevice, semaphores: VkSemaphorePtr) {
    for (i in 0 until semaphores.remaining())
        VK10.nvkDestroySemaphore(device, semaphores[i], NULL)
}



inline fun vkDestroyInstance(instance: VkInstance) = VK10.nvkDestroyInstance(instance, NULL)

inline fun vkDestroyBuffer(device: VkDevice, buffer: VkBuffer) = VK10.nvkDestroyBuffer(device, buffer, NULL)



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