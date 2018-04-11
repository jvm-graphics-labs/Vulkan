package glfw_

import glm_.BYTES
import glm_.L
import glm_.i
import glm_.set
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.Pointer
import uno.buffer.bufferBig
import vkn.adr
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.util.concurrent.atomic.AtomicLong

object appBuffer {

    val SIZE = Math.pow(2.0, 16.0).i  // 65536 TODO infix glm

    var buffer = bufferBig(SIZE)
    var address = MemoryUtil.memAddress(buffer)

    val ptr = AtomicLong(address)

    inline val intBuffer: IntBuffer
        get() {
            val size = Int.BYTES.L
            return MemoryUtil.memIntBuffer(ptr.getAndAdd(size), 1)
        }

    inline val longBuffer: LongBuffer
        get() {
            val size = Long.BYTES.L
            return MemoryUtil.memLongBuffer(ptr.getAndAdd(size), 1)
        }
    inline val doubleBuffer: DoubleBuffer
        get() {
            val size = Double.BYTES.L
            return MemoryUtil.memDoubleBuffer(ptr.getAndAdd(size), 1)
        }
    inline val pointerBuffer: PointerBuffer
        get() {
            val size = Pointer.POINTER_SIZE
            return MemoryUtil.memPointerBuffer(ptr.advance(size), 1)
        }

    inline fun pointerBufferOf(pointer: Pointer) = pointerBuffer(1).apply { put(0, pointer.adr) }
    inline fun pointerBuffer(capacity: Int): PointerBuffer {
        val size = Pointer.POINTER_SIZE * capacity
        return MemoryUtil.memPointerBuffer(ptr.advance(size), capacity)
    }

    inline val int get() = ptr.advance(Int.BYTES)
    inline val long get() = ptr.advance(Long.BYTES)
    inline val pointer get() = ptr.advance(Pointer.POINTER_SIZE)
//    inline val int get() = pointer.getAndAdd(Int.BYTES)

    inline fun intArray(size: Int) = ptr.advance(Int.BYTES * size)
    inline fun longArray(size: Int) = ptr.advance(Long.BYTES * size)
    inline fun pointerArray(size: Int) = ptr.advance(Pointer.POINTER_SIZE * size)
    inline fun floats(float: Float): Long {
        val res = ptr.advance(Float.BYTES)
        MemoryUtil.memPutFloat(res, float)
        return res
    }
    inline fun floatBufferOf(float: Float): FloatBuffer {
        val res = floatBuffer(1)
        res[0] = float
        return res
    }
    inline fun floatBufferOf(vararg floats: Float): FloatBuffer {
        val res = floatBuffer(floats.size)
        for(i in floats.indices)
            res[i] = floats[i]
        return res
    }
    inline fun longBufferOf(long0: Long, long1: Long): LongBuffer {
        val res = longBuffer(2)
        res[0] = long0
        res[1] = long1
        return res
    }
    inline fun intBuffer(size: Int): IntBuffer = MemoryUtil.memIntBuffer(ptr.advance(Int.BYTES * size), size)
    inline fun floatBuffer(size: Int): FloatBuffer = MemoryUtil.memFloatBuffer(ptr.advance(Float.BYTES * size), size)
    inline fun longBuffer(size: Int): LongBuffer = MemoryUtil.memLongBuffer(ptr.advance(Long.BYTES * size), size)

    fun reset() {
        ptr.set(address)
        MemoryUtil.memSet(address, 0, SIZE.L)
    }

    fun next() = MemoryUtil.memGetByte(ptr.get())
    fun printNext() = println("@${ptr.get() - address}: ${next()}")
}

inline fun AtomicLong.advance(int: Int) = getAndAdd(int.L)