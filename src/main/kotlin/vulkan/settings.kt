package vulkan

import glm_.vec2.Vec2i
import org.lwjgl.vulkan.VK10
import vkk.entities.VkDeviceSize
import java.nio.file.Paths


/** Set to "true" to enable Vulkan's validation layers (see vulkandebug.cpp for details)    */
const val ENABLE_VALIDATION = true
/** Set to "true" to use staging buffers for uploading vertex and index data to device local memory
 *  See "prepareVertices" for details on what's staging and on why to use it    */
var useStaging = true


const val VERTEX_BUFFER_BIND_ID = 0


val assetPath = Paths.get("").toAbsolutePath().toString() + "/src/main/resources"


const val PARTICLE_COUNT = 256 * 1024

const val PARTICLES_PER_ATTRACTOR = 4 * 1024

val TEX_DIM = Vec2i(2048)

val NUM_LIGHTS = 64


fun FloatArray.last(last: Float) = set(lastIndex, last)


val VK_WHOLE_SIZE = VkDeviceSize(VK10.VK_WHOLE_SIZE)

const val UINT32_MAX = 0.inv()
const val UINT64_MAX = 0L.inv()