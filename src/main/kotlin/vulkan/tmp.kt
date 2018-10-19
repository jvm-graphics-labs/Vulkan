package vulkan

import glm_.BYTES
import imgui.DrawVert
import kool.Ptr
import org.lwjgl.system.Configuration
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.memPutFloat
import org.lwjgl.system.MemoryUtil.memPutInt
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService




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


class Task(val name: String) : Runnable {

    override fun run() =
            try {
                val duration = (Math.random() * 10).toLong()
                println("Executing : $name")
                TimeUnit.SECONDS.sleep(duration)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
}

fun main(args: Array<String>) {
//    val executor = Executors.newFixedThreadPool(5)
//
//    for (i in 1..5) {
//        val task = Task("Task $i")
//        println("Created : " + task.name)
//
//        executor.execute {
//            try {
//                val duration = (Math.random() * 10).toLong()
//                println("Executing : $duration")
//                TimeUnit.SECONDS.sleep(duration)
//                println("Done : $duration")
//            } catch (e: InterruptedException) {
//                e.printStackTrace()
//            }
//        }
//    }
//    executor.shutdown()

    val executorService = Executors.newFixedThreadPool(10)

    executorService.execute { println("Asynchronous task") }
    executorService.execute { println("Asynchronous task") }
    executorService.execute { println("Asynchronous task") }
    executorService.execute { println("Asynchronous task") }

    executorService.shutdown()

    println("terminated properly: ${executorService.awaitTermination(1, TimeUnit.MINUTES)}")
}

fun MemoryStack.reset() {
    val size = Configuration.STACK_SIZE.get(64) * 1024
    pointer = size
}