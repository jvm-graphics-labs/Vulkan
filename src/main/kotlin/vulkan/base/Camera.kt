package vulkan.base

import glm_.func.common.abs
import glm_.func.cos
import glm_.func.rad
import glm_.func.sin
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec3.Vec3

class Camera {

    var fov = 0f
    var zNear = 0f
    var zFar = 0f

    fun updateViewMatrix() {
        val rotM = Mat4(1f)
                .rotate(rotation.x.rad, 1f, 0f, 0f)
                .rotate(rotation.y.rad, 0f, 1f, 0f)
                .rotate(rotation.z.rad, 0f, 0f, 1f)

        val transM = Mat4(1f) translate position

        matrices.view = when (type) {
            CameraType.firstPerson -> rotM * transM
            else -> transM * rotM
        }
    }

    enum class CameraType { lookAt, firstPerson }

    val type = CameraType.lookAt

    val rotation = Vec3()
    val position = Vec3()

    var rotationSpeed = 1f
    var movementSpeed = 1f

    private val matrices = object {
        var perspective = Mat4()
        var view = Mat4()
    }

    val keys = Keys()

    class Keys {
        var left = false
        var right = false
        var up = false
        var down = false
    }

    val moving get() = keys.left || keys.right || keys.up || keys.down

    fun setPerspective(fov: Float, aspect: Float, zNear: Float, zFar: Float) {
        this.fov = fov
        this.zNear = zNear
        this.zFar = zFar
        matrices.perspective = glm.perspective(fov.rad, aspect, zNear, zFar)
    }

    infix fun updateAspectRatio(aspect: Float) {
        matrices.perspective = glm.perspective(fov.rad, aspect, zNear, zFar)
    }

    infix fun setPosition(position: Vec3) {
        this.position(position)
        updateViewMatrix()
    }

    infix fun setRotation(rotation: Vec3) {
        this.rotation(rotation)
        updateViewMatrix()
    }

    infix fun rotate(delta: Vec3) {
        rotation += delta
        updateViewMatrix()
    }

    infix fun setTranslation(translation: Vec3) {
        position(translation)
        updateViewMatrix()
    }

    infix fun translate(delta: Vec3) {
        position += delta
        updateViewMatrix()
    }

    infix fun update(deltaTime: Float) {
        if (type == CameraType.firstPerson) {
            if (moving) {
                val camFront = Vec3(
                        x = -rotation.x.rad.cos * rotation.y.rad.sin,
                        y = rotation.x.rad.sin,
                        z = rotation.x.rad.cos * rotation.y.rad.cos).normalizeAssign()

                val moveSpeed = deltaTime * movementSpeed

                if (keys.up)
                    position += camFront * moveSpeed
                if (keys.down)
                    position -= camFront * moveSpeed
                if (keys.left)
                    position -= (camFront cross Vec3(0f, 1f, 0f)).normalizeAssign() * moveSpeed
                if (keys.right)
                    position += (camFront cross Vec3(0f, 1f, 0f)).normalizeAssign() * moveSpeed

                updateViewMatrix()
            }
        }
    }

    /** Update camera passing separate axis data (gamepad)
     *  Returns true if view or position has been changed   */
    fun updatePad(axisLeft: Vec2, axisRight: Vec2, deltaTime: Float): Boolean {

        var retVal = false

        if (type == CameraType.firstPerson) {
            // Use the common console thumbstick layout
            // Left = view, right = move

            val deadZone = 0.0015f
            val range = 1f - deadZone

            val camFront = Vec3(
                    x = -rotation.x.rad.cos * rotation.y.rad.sin,
                    y = rotation.x.rad.sin,
                    z = rotation.x.rad.cos * rotation.y.rad.cos).normalizeAssign()

            val moveSpeed = deltaTime * movementSpeed * 2f
            val rotSpeed = deltaTime * rotationSpeed * 50f

            // Move
            if (axisLeft.y.abs > deadZone) {
                val pos = (axisLeft.y.abs - deadZone) / range
                position -= camFront * pos * (if (axisLeft.y < 0f) -1f else 1f) * moveSpeed
                retVal = true
            }
            if (axisLeft.x.abs > deadZone) {
                val pos = (axisLeft.x.abs - deadZone) / range
                position += (camFront cross Vec3(0f, 1f, 0f)).normalizeAssign() * pos * (if (axisLeft.x < 0f) -1f else 1f) * moveSpeed
                retVal = true
            }

            // Rotate
            if (axisRight.x.abs > deadZone) {
                val pos = (axisRight.x.abs - deadZone) / range
                rotation.y += pos * (if (axisRight.x < 0f) -1f else 1f) * rotSpeed
                retVal = true
            }
            if (axisRight.y.abs > deadZone) {
                val pos = (axisRight.y.abs - deadZone) / range
                rotation.x -= pos * (if (axisRight.y < 0f) -1f else 1f) * rotSpeed
                retVal = true
            }
        } else {
        }  // todo: move code from example base class for look-at

        if (retVal) updateViewMatrix()

        return retVal
    }
}