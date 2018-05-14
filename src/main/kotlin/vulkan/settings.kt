package vulkan

import glm_.vec2.Vec2i
import java.nio.file.Paths


/** Set to "true" to enable Vulkan's validation layers (see vulkandebug.cpp for details)    */
const val ENABLE_VALIDATION = true
/** Set to "true" to use staging buffers for uploading vertex and index data to device local memory
 *  See "prepareVertices" for details on what's staging and on why to use it    */
var USE_STAGING = true


const val VERTEX_BUFFER_BIND_ID = 0


val assetPath = Paths.get("").toAbsolutePath().toString() + "/src/main/resources"


const val PARTICLE_COUNT = 256 * 1024

const val PARTICLES_PER_ATTRACTOR = 4 * 1024

val TEX_DIM = Vec2i(2048)

val NUM_LIGHTS = 64
