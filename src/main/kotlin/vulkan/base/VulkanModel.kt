/*
* Vulkan Model loader using ASSIMP
*
* Copyright(C) 2016-2017 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license(MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.base

import assimp.AiPostProcessStep as Pp
import assimp.AiPostProcessStepsFlags
import assimp.Importer
import assimp.or
import glm_.BYTES
import glm_.L
import glm_.buffer.free
import glm_.max
import glm_.min
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkQueue
import uno.buffer.*
import vkn.*
import java.nio.FloatBuffer
import java.nio.IntBuffer


/** @brief Vertex layout components */
enum class VertexComponent { POSITION, NORMAL, COLOR, UV, TANGENT, BITANGENT, DUMMY_FLOAT, DUMMY_VEC4 }

/** @brief Stores vertex layout components for model loading and Vulkan vertex input and atribute bindings  */
class VertexLayout(
        /** @brief Components used to generate vertices from */
        vararg val components: VertexComponent) {

    val stride
        get() = components.sumBy {
            when (it) {
                VertexComponent.UV -> Vec2.size
                VertexComponent.DUMMY_FLOAT -> Float.BYTES
                VertexComponent.DUMMY_VEC4 -> Vec4.size
            // All components except the ones listed above are made up of 3 floats
                else -> Vec3.size
            }
        }
}

/** @brief Used to parametrize model loading */
class ModelCreateInfo(
        val scale: Vec3 = Vec3(),
        val uvScale: Vec3 = Vec3(),
        val center: Vec3 = Vec3()) {
    constructor(scale: Float, uvScale: Float, center: Float) : this(Vec3(scale), Vec3(uvScale), Vec3(center))
}

class Model {

    var device: VkDevice? = null
    val vertices = Buffer()
    val indices = Buffer()
    var indexCount = 0
    var vertexCount = 0

    lateinit var vertexBuffer: FloatBuffer
    lateinit var indexBuffer: IntBuffer

    /** @brief Stores vertex and index base and counts for each part of a model */
    class ModelPart {
        var vertexBase = 0
        var vertexCount = 0
        var indexBase = 0
        var indexCount = 0
    }

    val parts = ArrayList<ModelPart>()

    val defaultFlags = Pp.FlipWindingOrder or Pp.Triangulate or Pp.PreTransformVertices or Pp.CalcTangentSpace or Pp.GenSmoothNormals

    object dim {
        val min = Vec3(Float.MAX_VALUE)
        val max = Vec3(-Float.MAX_VALUE)
        val size = Vec3()
    }

    /** @brief Release all Vulkan resources of this model */
    fun destroy() {
        val dev = device!!
        vk.destroyBuffer(dev, vertices.buffer)
        vk.freeMemory(dev, vertices.memory)
        if (indices.buffer != NULL) {
            vk.destroyBuffer(dev, indices.buffer)
            vk.freeMemory(dev, indices.memory)
        }
        if (::vertexBuffer.isInitialized)
            vertexBuffer.free()
        if (::indexBuffer.isInitialized)
            indexBuffer.free()
    }

    /**
     * Loads a 3D model from a file into Vulkan buffers
     *
     * @param device Pointer to the Vulkan device used to generated the vertex and index buffers on
     * @param filename File to load (must be a model format supported by ASSIMP)
     * @param layout Vertex layout components (position, normals, tangents, etc.)
     * @param createInfo MeshCreateInfo structure for load time settings like scale, center, etc.
     * @param copyQueue Queue used for the memory staging copy commands (must support transfer)
     * @param (Optional) flags ASSIMP model loading flags
     */
    fun loadFromFile(filename: String, layout: VertexLayout, createInfo: ModelCreateInfo?, device: VulkanDevice, copyQueue: VkQueue,
                     flags: AiPostProcessStepsFlags = defaultFlags): Boolean {

        this.device = device.logicalDevice

        val importer = Importer()
        val scene = importer.readFile(filename, flags) ?: tools.exitFatal("${importer.errorString}\n\n" +
                "The file may be part of the additional asset pack.\n\nRun \"download_assets.py\" in the repository root to download the latest version.", -1)


        parts.clear()
        for (i in 0 until scene.numMeshes)
            parts += ModelPart()

        val scale = Vec3(1f)
        val uvScale = Vec3(1f)
        val center = Vec3()
        createInfo?.also {
            scale(it.scale)
            uvScale(it.uvScale)
            center(it.center)
        }

        val vertices = ArrayList<Float>()
        val indices = ArrayList<Int>()

        vertexCount = 0
        indexCount = 0

        // Load meshes
        for (i in 0 until scene.numMeshes) {

            val mesh = scene.meshes[i]

            parts[i].vertexBase = vertexCount
            parts[i].indexBase = indexCount

            vertexCount += scene.meshes[i].numVertices

            val color = scene.materials[mesh.materialIndex].color?.diffuse ?: Vec3()

            for (j in 0 until mesh.numVertices) {

                val pos = mesh.vertices[j]
                val normal = mesh.normals[j]
                val texCoord = mesh.textureCoords.getOrNull(0)?.getOrNull(j)?.let {
                    Vec3(it[0], it[1], it.getOrElse(2) { 0f })
                } ?: Vec3()
                val tangent = mesh.tangents.getOrElse(j) { Vec3() }
                val biTangent = mesh.bitangents.getOrElse(j) { Vec3() }

                for (component in layout.components) with(vertices) {
                    when (component) {
                        VertexComponent.POSITION -> {
                            add(pos.x * scale.x + center.x)
                            add(-pos.y * scale.y + center.y)
                            add(pos.z * scale.z + center.z)
                        }
                        VertexComponent.NORMAL -> {
                            add(normal.x)
                            add(-normal.y)
                            add(normal.z)
                        }
                        VertexComponent.UV -> {
                            add(texCoord.x * uvScale.s)
                            add(texCoord.y * uvScale.t)
                        }
                        VertexComponent.COLOR -> {
                            add(color.r)
                            add(color.g)
                            add(color.b)
                        }
                        VertexComponent.TANGENT -> {
                            add(tangent.x)
                            add(tangent.y)
                            add(tangent.z)
                        }
                        VertexComponent.BITANGENT -> {
                            add(biTangent.x)
                            add(biTangent.y)
                            add(biTangent.z)
                        }
                    // Dummy components for padding
                        VertexComponent.DUMMY_FLOAT -> add(0f)
                        VertexComponent.DUMMY_VEC4 -> {
                            add(0f)
                            add(0f)
                            add(0f)
                            add(0f)
                        }
                    }
                }

                dim.max.x = pos.x max dim.max.x
                dim.max.y = pos.y max dim.max.y
                dim.max.z = pos.z max dim.max.z

                dim.min.x = pos.x min dim.min.x
                dim.min.y = pos.y min dim.min.y
                dim.min.z = pos.z min dim.min.z
            }

            dim.size(dim.max - dim.min)

            parts[i].vertexCount = mesh.numVertices

            val indexBase = indices.size
            mesh.faces.filter { it.size == 3 }.forEach {
                for (k in 0..2)
                    indices += indexBase + it[k]
                parts[i].indexCount += 3
                indexCount += 3
            }
        }

        val vBufferSize = vertices.size * Float.BYTES
        val iBufferSize = indices.size * Int.BYTES

        // Use staging buffer to move vertex and index buffer to device local memory
        // Create staging buffers
        val vertexStaging = Buffer()
        val indexStaging = Buffer()

        val memoryProps = VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT

        vertexBuffer = floatBufferOf(vertices)
        indexBuffer = intBufferOf(indices)
        // Vertex buffer
        device.createBuffer(VkBufferUsage.TRANSFER_SRC_BIT.i, memoryProps, vertexStaging, vertexBuffer)
        // Index buffer
        device.createBuffer(VkBufferUsage.TRANSFER_SRC_BIT.i, memoryProps, indexStaging, indexBuffer)

        // Create device local target buffers
        // Vertex buffer
        device.createBuffer(
                VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT,
                VkMemoryProperty.DEVICE_LOCAL_BIT.i,
                this.vertices,
                vBufferSize.L)

        // Index buffer
        device.createBuffer(
                VkBufferUsage.INDEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT,
                VkMemoryProperty.DEVICE_LOCAL_BIT.i,
                this.indices,
                iBufferSize.L)

        // Copy from staging buffers
        val copyCmd = device.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)

        vk.BufferCopy {

            size = this@Model.vertices.size
            copyCmd.copyBuffer(vertexStaging.buffer, this@Model.vertices.buffer, this)

            size = this@Model.indices.size
            copyCmd.copyBuffer(indexStaging.buffer, this@Model.indices.buffer, this)
        }

        device.flushCommandBuffer(copyCmd, copyQueue)

        // Destroy staging resources
        device.logicalDevice!!.apply {
            destroyBuffer(vertexStaging.buffer)
            freeMemory(vertexStaging.memory)
            destroyBuffer(indexStaging.buffer)
            freeMemory(indexStaging.memory)
        }
        return true
    }

    /**
     * Loads a 3D model from a file into Vulkan buffers
     *
     * @param device Pointer to the Vulkan device used to generated the vertex and index buffers on
     * @param filename File to load (must be a model format supported by ASSIMP)
     * @param layout Vertex layout components (position, normals, tangents, etc.)
     * @param scale Load time scene scale
     * @param copyQueue Queue used for the memory staging copy commands (must support transfer)
     * @param (Optional) flags ASSIMP model loading flags
     */
    fun loadFromFile(filename: String, layout: VertexLayout, scale: Float, device: VulkanDevice, copyQueue: VkQueue,
                     flags: Int = defaultFlags): Boolean {
        val modelCreateInfo = ModelCreateInfo(scale, 1f, 0f)
        return loadFromFile(filename, layout, modelCreateInfo, device, copyQueue, flags)
    }
}