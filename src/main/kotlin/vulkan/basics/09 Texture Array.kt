/*
* Vulkan Example - Texture arrays and instanced rendering
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.basics

import glfw_.appBuffer
import gli_.Texture2dArray
import gli_.gli
import glm_.L
import glm_.f
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import uno.buffer.bufferBig
import uno.buffer.floatBufferOf
import uno.buffer.intBufferOf
import uno.kotlin.buffers.capacity
import vkn.*
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.assetPath
import vulkan.base.Buffer
import vulkan.base.Texture
import vulkan.base.VulkanExampleBase
import vulkan.base.tools
import java.nio.ByteBuffer

fun main(args: Array<String>) {
    TextureArray().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

class TextureArray : VulkanExampleBase() {

    /** Vertex layout for this example */
    object Vertex {
        //    float pos[3];
//    float uv[2];
        val size = Vec3.size + Vec2.size
    }

    // Number of array layers in texture array
    // Also used as instance count
    var layerCount = 0
    val textureArray = Texture()

    object vertices {
        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
        lateinit var bindingDescription: VkVertexInputBindingDescription
        lateinit var attributeDescriptions: VkVertexInputAttributeDescription.Buffer
    }

    val vertexBuffer = Buffer()
    val indexBuffer = Buffer()
    var indexCount = 0

    val uniformBufferVS = Buffer()

    class UboInstanceData {
        // Model matrix
        var model = Mat4()
        // Texture array index
        // Vec4 due to padding
        var arrayIndex = Vec4()

        fun to(bytes: ByteBuffer, offset: Int) {
            model.to(bytes, offset)
            bytes.putFloat(offset + Mat4.size, arrayIndex.x)
        }

        companion object {
            val size = Mat4.size + Vec4.size
        }
    }

    object uboVS {
        // Global matrices
        object matrices : Bufferizable() {
            var projection = Mat4()
            var view = Mat4()
            override val fieldOrder = arrayOf("projection", "view")
        }
        // Seperate data for each instance
        val instance = ArrayList<UboInstanceData>()

        fun prepare() {
            buffer = bufferBig(matrices.size + instance.size * UboInstanceData.size)
            address = memAddress(buffer)
            for (i in instance.indices)
                instance[i].to(buffer, matrices.size + UboInstanceData.size * i)
        }

        lateinit var buffer: ByteBuffer
        var address = NULL
    }


    var pipeline: VkPipeline = NULL
    var pipelineLayout: VkPipelineLayout = NULL
    var descriptorSet: VkDescriptorSet = NULL
    var descriptorSetLayout: VkDescriptorSetLayout = NULL

    init {
        zoom = -15f
        rotationSpeed = 0.25f
        rotation(-15f, 35f, 0f)
        title = "Texture arrays"
//        settings.overlay = true
    }

    override fun destroy() {

        // Clean up used Vulkan resources
        // Note : Inherited destructor cleans up resources stored in base class

        // Clean up texture resources
        device.apply {
            destroyImageView(textureArray.view)
            destroyImage(textureArray.image)
            destroySampler(textureArray.sampler)
            freeMemory(textureArray.deviceMemory)

            destroyPipeline(pipeline)

            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }
        vertexBuffer.destroy()
        indexBuffer.destroy()

        uniformBufferVS.destroy()

        super.destroy()
    }

    fun loadTextureArray(filename: String, format: VkFormat) {

        val tex2DArray = Texture2dArray(gli.load(filename))

        assert(tex2DArray.notEmpty())

        textureArray.size(tex2DArray.extent())
        layerCount = tex2DArray.layers()

        // Create a host-visible staging buffer that contains the raw image data

        val bufferCreateInfo = vk.BufferCreateInfo {
            size = tex2DArray.size.L
            // This buffer is used as a transfer source for the buffer copy
            usage = VkBufferUsage.TRANSFER_SRC_BIT.i
            sharingMode = VkSharingMode.EXCLUSIVE
        }
        val stagingBuffer = device createBuffer bufferCreateInfo

        // Get memory requirements for the staging buffer (alignment, memory type bits)
        val memReqs = device getBufferMemoryRequirements stagingBuffer
        val memAllocInfo = vk.MemoryAllocateInfo {
            allocationSize = memReqs.size
            // Get memory type index for a host visible buffer
            memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT)
        }
        val stagingMemory = device allocateMemory memAllocInfo
        device.bindBufferMemory(stagingBuffer, stagingMemory)

        // Copy texture data into staging buffer
        device.mappingMemory(stagingMemory, 0, memReqs.size, 0) { data ->
            memCopy(memAddress(tex2DArray.data()), data, tex2DArray.size.L)
        }

        // Setup buffer copy regions for array layers
        val bufferCopyRegions = vk.BufferImageCopy(layerCount)
        var offset = 0L

        for (layer in 0 until layerCount) {

            bufferCopyRegions[layer].apply {
                imageSubresource.apply {
                    aspectMask = VkImageAspect.COLOR_BIT.i
                    mipLevel = 0
                    baseArrayLayer = layer
                    layerCount = 1
                }
                imageExtent.apply {
                    val (w, h) = tex2DArray[layer][0].extent() // TODO BUG
                    width = w
                    height = h
                    depth = 1
                }
                bufferOffset = offset
            }

            // Increase offset into staging buffer for next level / face
            offset += tex2DArray[layer][0].size
        }

        // Create optimal tiled target image
        val imageCreateInfo = vk.ImageCreateInfo {
            imageType = VkImageType.`2D`
            this.format = format
            mipLevels = 1
            samples = VkSampleCount.`1_BIT`
            tiling = VkImageTiling.OPTIMAL
            sharingMode = VkSharingMode.EXCLUSIVE
            initialLayout = VkImageLayout.UNDEFINED
            extent.set(textureArray.size.x, textureArray.size.y, 1)
            usage = VkImageUsage.TRANSFER_DST_BIT or VkImageUsage.SAMPLED_BIT
            arrayLayers = layerCount
        }
        textureArray.image = device createImage imageCreateInfo

        device.getImageMemoryRequirements(textureArray.image, memReqs)

        memAllocInfo.allocationSize = memReqs.size
        memAllocInfo.memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)

        textureArray.deviceMemory = device allocateMemory memAllocInfo
        device.bindImageMemory(textureArray.image, textureArray.deviceMemory)

        val copyCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)

        // Image barrier for optimal image (target)
        // Set initial layout for all array layers (faces) of the optimal (target) tiled texture
        val subresourceRange = vk.ImageSubresourceRange {
            aspectMask = VkImageAspect.COLOR_BIT.i
            baseMipLevel = 0
            levelCount = 1
            layerCount = this@TextureArray.layerCount
        }
        tools.setImageLayout(
                copyCmd,
                textureArray.image,
                VkImageLayout.UNDEFINED,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                subresourceRange)

        // Copy the cube map faces from the staging buffer to the optimal tiled image
        copyCmd.copyBufferToImage(
                stagingBuffer,
                textureArray.image,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                bufferCopyRegions)

        // Change texture image layout to shader read after all faces have been copied
        textureArray.imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
        tools.setImageLayout(
                copyCmd,
                textureArray.image,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                textureArray.imageLayout,
                subresourceRange)

        super.flushCommandBuffer(copyCmd, queue, true)

        // Create sampler
        val sampler = vk.SamplerCreateInfo {
            magFilter = VkFilter.LINEAR
            minFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            addressModeU = VkSamplerAddressMode.CLAMP_TO_EDGE
            addressModeV = addressModeU
            addressModeW = addressModeU
            mipLodBias = 0f
            maxAnisotropy = 8f
            compareOp = VkCompareOp.NEVER
            minLod = 0f
            maxLod = 0f
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
        }
        textureArray.sampler = device createSampler sampler

        // Create image view
        val view = vk.ImageViewCreateInfo {
            viewType = VkImageViewType.`2D_ARRAY`
            this.format = format
            components(VkComponentSwizzle.R, VkComponentSwizzle.G, VkComponentSwizzle.B, VkComponentSwizzle.A)
            this.subresourceRange.apply {
                set(VkImageAspect.COLOR_BIT.i, 0, 1, 0, 1)
                layerCount = this@TextureArray.layerCount // TODO move in ::set
                levelCount = 1
            }
            image = textureArray.image
        }
        textureArray.view = device createImageView view

        // Clean up staging resources
        device freeMemory stagingMemory
        device destroyBuffer stagingBuffer
    }

    fun loadTextures() {
        // Vulkan core supports three different compressed texture formats
        // As the support differs between implemementations we need to check device features and select a proper format and file
        val (filename, format) = when {
            deviceFeatures.textureCompressionBC -> "texturearray_bc3_unorm.ktx" to VkFormat.BC3_UNORM_BLOCK
            deviceFeatures.textureCompressionASTC_LDR -> "texturearray_astc_8x8_unorm.ktx" to VkFormat.ASTC_8x8_UNORM_BLOCK
            deviceFeatures.textureCompressionETC2 -> "texturearray_etc2_unorm.ktx" to VkFormat.ETC2_R8G8B8_UNORM_BLOCK
            else -> tools.exitFatal("Device does not support any compressed texture format!", VkResult.ERROR_FEATURE_NOT_PRESENT)
        }
        loadTextureArray("$assetPath/textures/$filename", format)
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2)
        clearValues[0].color(defaultClearColor)
        clearValues[1].depthStencil.set(1f, 0)

        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@TextureArray.renderPass
            renderArea.apply {
                offset.set(0, 0)
                extent.set(size.x, size.y)
            }
            this.clearValues = clearValues
        }
        for (i in drawCmdBuffers.indices) {
            // Set target frame buffer
            renderPassBeginInfo.framebuffer(frameBuffers[i]) // TODO BUG

            drawCmdBuffers[i].apply {

                begin(cmdBufInfo)

                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                setViewport(size)
                setScissor(size)

                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)

                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, vertexBuffer.buffer)
                bindIndexBuffer(indexBuffer.buffer, 0, VkIndexType.UINT32)

                drawIndexed(indexCount, layerCount, 0, 0, 0)

                endRenderPass()

                end()
            }
        }
    }

    fun generateQuad() {
        // Setup vertices for a single uv-mapped quad made from two triangles
        val vertices = appBuffer.floatBufferOf(
                +2.5f, +2.5f, 0f, 1f, 1f,
                -2.5f, +2.5f, 0f, 0f, 1f,
                -2.5f, -2.5f, 0f, 0f, 0f,
                +2.5f, -2.5f, 0f, 1f, 0f)

        // Setup indices
        val indices = appBuffer.intBufferOf(0, 1, 2, 2, 3, 0)
        indexCount = indices.capacity

        // Create buffers
        // For the sake of simplicity we won't stage the vertex data to the gpu memory
        // Vertex buffer
        vulkanDevice.createBuffer(
                VkBufferUsage.VERTEX_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                vertexBuffer,
                vertices)
        // Index buffer
        vulkanDevice.createBuffer(
                VkBufferUsage.INDEX_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                indexBuffer,
                indices)
    }

    fun setupVertexDescriptions() {
        // Binding description
        vertices.bindingDescription = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, Vertex.size, VkVertexInputRate.VERTEX)

        // Attribute descriptions
        // Describes memory layout and shader positions
        vertices.attributeDescriptions = vk.VertexInputAttributeDescription(
                // Location 0 : Position
                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, 0,
                // Location 1 : Texture coordinates
                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32_SFLOAT, Vec3.size)

        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertices.bindingDescription
            vertexAttributeDescriptions = vertices.attributeDescriptions
        }
    }

    fun setupDescriptorPool() {

        val poolSizes = vk.DescriptorPoolSize(
                VkDescriptorType.UNIFORM_BUFFER, 1,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1)

        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 2)

        descriptorPool = device createDescriptorPool descriptorPoolInfo
    }

    fun setupDescriptorSetLayout() {

        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
                // Binding 0 : Vertex shader uniform buffer
                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0,
                // Binding 1 : Fragment shader image sampler (texture array)
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 1)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)

        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)

        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)

        descriptorSet = device allocateDescriptorSets allocInfo

        // Image descriptor for the texture array
        val textureDescriptor = vk.DescriptorImageInfo(textureArray.sampler, textureArray.view, textureArray.imageLayout)

        val writeDescriptorSets = vk.WriteDescriptorSet(
            // Binding 0 : Vertex shader uniform buffer
            descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBufferVS.descriptor,
            // Binding 1 : Fragment shader cubemap sampler
            descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, textureDescriptor)

        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.NONE.i, VkFrontFace.COUNTER_CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Instacing pipeline
        // Load shaders
        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
            it[0].loadShader("$assetPath/shaders/texturearray/instancing.vert.spv", VkShaderStage.VERTEX_BIT)
            it[1].loadShader("$assetPath/shaders/texturearray/instancing.frag.spv", VkShaderStage.FRAGMENT_BIT)
        }
        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {
            it.vertexInputState = vertices.inputState
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
        }
        pipeline = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    fun prepareUniformBuffers() {

        for (i in 0 until layerCount)
            uboVS.instance += UboInstanceData()

        val uboSize = uboVS.matrices.size + layerCount * UboInstanceData.size

        // Vertex shader uniform buffer block
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBufferVS,
                uboSize.L)

        // Array indices and model matrices are fixed
        val offset = -1.5f
        val center = (layerCount * offset) / 2
        for (i in 0 until layerCount) {
            // Instance model matrix
            uboVS.instance[i].model = glm.translate(Mat4(1f), 0f, i * offset - center, 0f)
                    .rotateAssign(60f.rad, 1f, 0f, 0f)
            // Instance texture array index
            uboVS.instance[i].arrayIndex.x = i.f
        }

        // Update instanced part of the uniform buffer
        val dataOffset = uboVS.matrices.size.L
        val dataSize = layerCount * UboInstanceData.size.L
        uboVS.prepare()
        device.mappingMemory(uniformBufferVS.memory, dataOffset, dataSize, 0) { data ->
            memCopy(uboVS.address + dataOffset, data, dataSize)
        }

        // Map persistent
        uniformBufferVS.map()

        updateUniformBufferMatrices()
    }

    fun updateUniformBufferMatrices() {

        // Only updates the uniform buffer block part containing the global matrices

        // Projection
        uboVS.matrices.projection = glm.perspective(60f.rad, size.aspect, 0.001f, 256f)

        // View
        uboVS.matrices.view = glm.translate(Mat4(1f), 0f, -1f, zoom)
                .rotateAssign(rotation.x.rad, 1f, 0f, 0f)
                .rotateAssign(rotation.y.rad, 0f, 1f, 0f)
                .rotateAssign(rotation.z.rad, 0f, 0f, 1f)

        // Only update the matrices part of the uniform buffer
        uboVS.matrices to uniformBufferVS.mapped[0]
    }

    fun draw() {

        super.prepareFrame()

        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]
        queue submit submitInfo

        super.submitFrame()
    }

    override fun prepare() {

        super.prepare()
        loadTextures()
        setupVertexDescriptions()
        generateQuad()
        prepareUniformBuffers()
        setupDescriptorSetLayout()
        preparePipelines()
        setupDescriptorPool()
        setupDescriptorSet()
        buildCommandBuffers()
        prepared = true
        window.show()
    }

    override fun render() {
        if (!prepared)
            return
        draw()
    }

    override fun viewChanged() = updateUniformBufferMatrices()
}