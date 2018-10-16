package vulkan.base

import glm_.mat4x4.Mat4
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import kotlin.math.sqrt

class Frustum {

    val LEFT = 0
    val RIGHT = 1
    val TOP = 2
    val BOTTOM = 3
    val BACK = 4
    val FRONT = 5

    val planes = Array(6) { Vec4() }

    fun update(matrix: Mat4) {

        planes[LEFT].x = matrix[0].w + matrix[0].x
        planes[LEFT].y = matrix[1].w + matrix[1].x
        planes[LEFT].z = matrix[2].w + matrix[2].x
        planes[LEFT].w = matrix[3].w + matrix[3].x

        planes[RIGHT].x = matrix[0].w - matrix[0].x
        planes[RIGHT].y = matrix[1].w - matrix[1].x
        planes[RIGHT].z = matrix[2].w - matrix[2].x
        planes[RIGHT].w = matrix[3].w - matrix[3].x

        planes[TOP].x = matrix[0].w - matrix[0].y
        planes[TOP].y = matrix[1].w - matrix[1].y
        planes[TOP].z = matrix[2].w - matrix[2].y
        planes[TOP].w = matrix[3].w - matrix[3].y

        planes[BOTTOM].x = matrix[0].w + matrix[0].y
        planes[BOTTOM].y = matrix[1].w + matrix[1].y
        planes[BOTTOM].z = matrix[2].w + matrix[2].y
        planes[BOTTOM].w = matrix[3].w + matrix[3].y

        planes[BACK].x = matrix[0].w + matrix[0].z
        planes[BACK].y = matrix[1].w + matrix[1].z
        planes[BACK].z = matrix[2].w + matrix[2].z
        planes[BACK].w = matrix[3].w + matrix[3].z

        planes[FRONT].x = matrix[0].w - matrix[0].z
        planes[FRONT].y = matrix[1].w - matrix[1].z
        planes[FRONT].z = matrix[2].w - matrix[2].z
        planes[FRONT].w = matrix[3].w - matrix[3].z

        for (i in planes.indices) {
            val length = sqrt(planes[i].x * planes[i].x + planes[i].y * planes[i].y + planes[i].z * planes[i].z)
            planes[i] divAssign length
        }
    }

    fun checkSphere(pos: Vec3, radius: Float): Boolean {

        for (i in planes.indices)

            if (planes[i].x * pos.x + planes[i].y * pos.y + planes[i].z * pos.z + planes[i].w <= -radius)
                return false

        return true
    }
}