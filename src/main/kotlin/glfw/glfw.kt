package glfw

import glm_.vec2.Vec2i
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWVulkan
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.Platform
import org.lwjgl.vulkan.VkAllocationCallbacks
import org.lwjgl.vulkan.VkInstance
import uno.glfw.windowHint
import vkn.VkSurfaceKHR
import vkn.address
import vkn.check
import vkn.withLong

/**
 * Created by elect on 22/04/17.
 */

object glfw {

    fun init() {

        GLFWErrorCallback.createPrint(System.err).set()
        if (!glfwInit())
            throw IllegalStateException("Unable to initialize GLFW")

        /* This window hint is required to use OpenGL 3.1+ on macOS */
        if (Platform.get() == Platform.MACOSX)
            windowHint.forwardComp = true
    }

    fun <T> windowHint(block: windowHint.() -> T) = windowHint.block()

    val primaryMonitor get() = glfwGetPrimaryMonitor()

    val videoMode get() = glfwGetVideoMode(glfw.primaryMonitor)!!

    var start = System.nanoTime()
    val time get() = (System.nanoTime() - glfw.start) / 1e9f

    fun videoMode(monitor: Long) = glfwGetVideoMode(monitor)

    val resolution
        get() = Vec2i(glfw.videoMode.width(), glfw.videoMode.height())

    var swapInterval = 0
        set(value) = glfwSwapInterval(value)

    fun terminate() {
        glfwTerminate()
        glfwSetErrorCallback(null)?.free()
    }

    fun pollEvents() = glfwPollEvents()

    val requiredInstanceExtensions: ArrayList<String>
        get() {
            val pCount = appBuffer.int
            val ppNames = GLFWVulkan.nglfwGetRequiredInstanceExtensions(pCount.address)
            val count = pCount[0]
            val pNames = MemoryUtil.memPointerBufferSafe(ppNames, count) ?: return arrayListOf()
            val res = ArrayList<String>(count)
            for (i in 0 until count)
                res += MemoryUtil.memASCII(pNames[i])
            return res
        }

    fun createWindowSurface(window: GlfwWindow, instance: VkInstance, allocator: VkAllocationCallbacks? = null): VkSurfaceKHR =
            withLong { GLFWVulkan.glfwCreateWindowSurface(instance, window.handle, allocator, it).check() }
}

