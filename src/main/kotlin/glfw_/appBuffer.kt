package glfw_

import glm_.BYTES
import glm_.L
import glm_.i
import org.lwjgl.system.MemoryUtil
import uno.buffer.bufferBig
import java.nio.DoubleBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicLong

object appBuffer {

    val SIZE = Math.pow(2.0, 16.0).i  // 65536 TODO infix glm

    var buffer = bufferBig(SIZE)
    var address = MemoryUtil.memAddress(buffer)

    val pointer = AtomicLong(address)

    inline val int: IntBuffer
        get() {
            val size = Int.BYTES.L
//            return JNINativeInterface.nNewDirectByteBuffer(pointer.getAndAdd(size), size)!!.asIntBuffer()
            return MemoryUtil.memIntBuffer(pointer.getAndAdd(size), 1)
        }
    inline val long: LongBuffer
        get() {
            val size = Long.BYTES.L
//            return JNINativeInterface.nNewDirectByteBuffer(pointer.getAndAdd(size), size)!!.asLongBuffer()
            return MemoryUtil.memLongBuffer(pointer.getAndAdd(size), 1)
        }
    inline val double: DoubleBuffer
        get() {
            val size = Double.BYTES.L
//            return JNINativeInterface.nNewDirectByteBuffer(pointer.getAndAdd(size), size)!!.asDoubleBuffer()
            return MemoryUtil.memDoubleBuffer(pointer.getAndAdd(size), 1)
        }

    fun reset() = pointer.set(address)
}

fun AtomicLong.getAndAdd(int: Int) = getAndAdd(int.L)