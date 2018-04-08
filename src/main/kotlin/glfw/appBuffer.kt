package glfw

import glm_.BYTES
import glm_.L
import glm_.i
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.jni.JNINativeInterface
import uno.buffer.bufferBig
import java.nio.DoubleBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicInteger

object appBuffer {

    val SIZE = Math.pow(2.0, 16.0).i  // 65536 TODO infix glm

    var buffer = bufferBig(SIZE)
    var address = MemoryUtil.memAddress(buffer)

    val pointer = AtomicInteger()

    inline val int: IntBuffer
        get() {
            val size = Int.BYTES
            return JNINativeInterface.nNewDirectByteBuffer(address + pointer.getAndAdd(size), size.L)!!.asIntBuffer()
        }
    inline val long: LongBuffer
        get() {
            val size = Long.BYTES
            return JNINativeInterface.nNewDirectByteBuffer(address + pointer.getAndAdd(size), size.L)!!.asLongBuffer()
        }
    inline val double: DoubleBuffer
        get() {
            val size = Double.BYTES
            return JNINativeInterface.nNewDirectByteBuffer(address + pointer.getAndAdd(size), size.L)!!.asDoubleBuffer()
        }

    fun reset() = pointer.set(0)
}