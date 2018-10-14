package vulkan

import glm_.BYTES
import imgui.DrawVert
import kool.Ptr
import org.lwjgl.system.MemoryUtil.memPutFloat
import org.lwjgl.system.MemoryUtil.memPutInt

fun FloatArray.rotateLeft(amount: Int) {
    val backup = clone()
    for (i in indices)
        set(i, backup[(i + amount) % size])
}

fun DrawVert.to(ptr: Ptr, offset: Int) {
    var ofs = offset
    memPutFloat(ptr, pos.x)
    ofs += Float.BYTES
    memPutFloat(ptr + ofs, pos.y)
    ofs += Float.BYTES
    memPutFloat(ptr, uv.x)
    ofs += Float.BYTES
    memPutFloat(ptr + ofs, uv.y)
    ofs += Float.BYTES
    memPutInt(ptr + ofs, col)
}