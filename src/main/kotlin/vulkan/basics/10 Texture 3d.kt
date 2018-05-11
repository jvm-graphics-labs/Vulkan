/*
* Vulkan Example - 3D texture loading (and generation using perlin noise) example
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.basics

import glfw_.appBuffer
import glm_.L
import glm_.b
import glm_.buffer.bufferBig
import glm_.buffer.free
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.set
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import glm_.vec3.Vec3i
import glm_.vec4.Vec4
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import uno.kotlin.buffers.capacity
import uno.kotlin.buffers.indices
import vkn.*
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.assetPath
import vulkan.base.Buffer
import vulkan.base.Model
import vulkan.base.VulkanExampleBase
import vulkan.base.tools
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.IntStream
import kotlin.system.measureTimeMillis


fun main(args: Array<String>) {
    Texture3d().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class Texture3d : VulkanExampleBase() {

    /** Vertex layout for this example */
    object Vertex : Bufferizable() {
        lateinit var pos: Vec3
        lateinit var uv: Vec2
        @Order(2)
        lateinit var normal: Vec3
    }

    /** Fractal noise generator based on perlin noise above */
    class FractalNoise {

        val octaves = 6
        var frequency = 0f
        var amplitude = 0f
        var persistence = 0.5f

        fun noise(v: Vec3): Float {
            var sum = 0f
            frequency = 1f
            amplitude = 1f
            var max = 0f
            for (i in 0 until octaves) {
                sum += glm.perlin(v * frequency) * amplitude
                max += amplitude
                amplitude *= persistence
                frequency *= 2f
            }

            sum /= max
            return (sum + 1f) / 2f
        }
    }

    /** Contains all Vulkan objects that are required to store and use a 3D texture */
    object texture {
        var sampler: VkSampler = NULL
        var image: VkImage = NULL
        var imageLayout = VkImageLayout.UNDEFINED
        var deviceMemory: VkDeviceMemory = NULL
        var view: VkImageView = NULL
        lateinit var descriptor: VkDescriptorImageInfo
        var format = VkFormat.UNDEFINED
        val extent = Vec3i()
        var mipLevels = 0
    }

    var regenerateNoise = true

    object models {
        val cube = Model()
    }

    object vertices {
        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
        lateinit var inputBinding: VkVertexInputBindingDescription
        lateinit var inputAttributes: VkVertexInputAttributeDescription.Buffer
    }

    val vertexBuffer = Buffer()
    val indexBuffer = Buffer()
    var indexCount = 0

    val uniformBufferVS = Buffer()

    object uboVS : Bufferizable() {
        lateinit var projection: Mat4
        @Order(1)
        lateinit var model: Mat4
        lateinit var viewPos: Vec4
        @Order(3)
        var depth = 0f
    }

    object pipelines {
        var solid: VkPipeline = NULL
    }

    var pipelineLayout: VkPipelineLayout = NULL
    var descriptorSet: VkDescriptorSet = NULL
    var descriptorSetLayout: VkDescriptorSetLayout = NULL

    init {
        zoom = -2.5f
        rotation(0f, 15f, 0f)
        title = "3D textures"
//        settings.overlay = true
//        srand((unsigned int) time (NULL))
    }

    override fun destroy() {
        // Clean up used Vulkan resources
        // Note : Inherited destructor cleans up resources stored in base class

        destroyTextureImage()

        device.apply {
            destroyPipeline(pipelines.solid)

            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }
        vertexBuffer.destroy()
        indexBuffer.destroy()
        uniformBufferVS.destroy()

        super.destroy()
    }

    /** Prepare all Vulkan resources for the 3D texture (including descriptors)
     *  Does not fill the texture with data */
    fun prepareNoiseTexture(width: Int, height: Int, depth: Int) {
        // A 3D texture is described as width x height x depth
        texture.extent.put(width, height, depth) // TODO glm
        texture.mipLevels = 1
        texture.format = VkFormat.R8_UNORM

        // Format support check
        // 3D texture support in Vulkan is mandatory (in contrast to OpenGL) so no need to check if it's supported
        val formatProperties = physicalDevice getFormatProperties texture.format
        // Check if format supports transfer
        if (formatProperties.optimalTilingFeatures hasnt VkImageUsage.TRANSFER_DST_BIT) {
            System.err.println("Error: Device does not support flag TRANSFER_DST for selected texture format!")
            return
        }
        // Check if GPU supports requested 3D texture dimensions
        val maxImageDimension3D = vulkanDevice.properties.limits.maxImageDimension3D
        if (width > maxImageDimension3D || height > maxImageDimension3D || depth > maxImageDimension3D) {
            System.out.println("Error: Requested texture dimensions is greater than supported 3D texture dimension!")
            return
        }

        // Create optimal tiled target image
        val imageCreateInfo = vk.ImageCreateInfo {
            imageType = VkImageType.`3D`
            format = texture.format
            mipLevels = texture.mipLevels
            arrayLayers = 1
            samples = VkSampleCount.`1_BIT`
            tiling = VkImageTiling.OPTIMAL
            sharingMode = VkSharingMode.EXCLUSIVE
            extent(texture.extent)
            // Set initial layout of the image to undefined
            initialLayout = VkImageLayout.UNDEFINED
            usage = VkImageUsage.TRANSFER_DST_BIT or VkImageUsage.SAMPLED_BIT
        }
        texture.image = device createImage imageCreateInfo

        // Device local memory to back up image
        val memReqs = device getImageMemoryRequirements texture.image
        val memAllocInfo = vk.MemoryAllocateInfo {
            allocationSize = memReqs.size
            memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
        }
        texture.deviceMemory = device allocateMemory memAllocInfo
        device.bindImageMemory(texture.image, texture.deviceMemory, 0)

        // Create sampler
        val sampler = vk.SamplerCreateInfo {
            magFilter = VkFilter.LINEAR
            minFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            addressModeU = VkSamplerAddressMode.CLAMP_TO_EDGE
            addressModeV = VkSamplerAddressMode.CLAMP_TO_EDGE
            addressModeW = VkSamplerAddressMode.CLAMP_TO_EDGE
            mipLodBias = 0f
            compareOp = VkCompareOp.NEVER
            minLod = 0f
            maxLod = 0f
            maxAnisotropy = 1f
            anisotropyEnable = false
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
        }
        texture.sampler = device createSampler sampler

        // Create image view
        val view = vk.ImageViewCreateInfo {
            image = texture.image
            viewType = VkImageViewType.`3D`
            format = texture.format
            components(VkComponentSwizzle.R, VkComponentSwizzle.G, VkComponentSwizzle.B, VkComponentSwizzle.A)
            subresourceRange.apply {
                aspectMask = VkImageAspect.COLOR_BIT.i
                baseMipLevel = 0
                baseArrayLayer = 0
                layerCount = 1
                levelCount = 1
            }
        }
        texture.view = device createImageView view

        // Fill image descriptor image info to be used descriptor set setup
        texture.descriptor = vk.DescriptorImageInfo {
            imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
            imageView = texture.view
            this.sampler = texture.sampler
        }
    }

    /** Generate randomized noise and upload it to the 3D texture using staging */
    fun updateNoiseTexture() {

        val ext = texture.extent
        val texMemSize = ext.x * ext.y * ext.z

        val data = bufferBig(texMemSize)
        val adr = memAddress(data)
        for (i in data.indices)
            memPutByte(adr + i, i.b)

        // Generate perlin based noise
        println("Generating ${ext.x} x ${ext.y} x ${ext.z} noise texture...")

//        auto tStart = std ::chrono::high_resolution_clock::now()

        /*  Maximum value that can be returned by the rand function:
            define RAND_MAX 0x7fff
            0111 1111 1111 1111
         */
        fun rand() = ThreadLocalRandom.current().nextInt() ushr 1

        val time = measureTimeMillis {

            val FRACTAL = true
            val noiseScale = rand() % 10 + 4f

            val parallel = true

            if (!parallel)
                for (z in 0 until ext.z) {
                    println(z)
                    for (y in 0 until ext.y)
                        for (x in 0 until ext.x) {
                            println("x $x, y $y, z $z")
                            val v = Vec3(x, y, z) / ext
                            var n = when {
                                FRACTAL -> FractalNoise().noise(v * noiseScale)
                                else -> 20f * glm.perlin(v)
                            }
                            n -= glm.floor(n)

                            data[x + y * ext.x + z * ext.x * ext.y] = glm.floor(n * 255).b
                        }
                }
            else {
//                runBlocking {
//                    for (z in 0 until 1) {
//                        println(z)
////                    for (z in 0 until 1) {
//                        for (y in 0 until 1)
//                            for (x in 0 until 100) {
//                                launch {
//                                    val v = Vec3(x, y, z) / texture.extent
//                                    var n = when {
//                                        FRACTAL -> FractalNoise().noise(v * noiseScale)
//                                        else -> 20f * glm.perlin(v)
//                                    }
//                                    n -= glm.floor(n)
//
//                                    val offset = x + y * texture.extent.x + z * texture.extent.x * texture.extent.y
//                                    println("$adr, "+offset)
//                                    memPutByte(adr + offset, glm.floor(n * 255).b)
////                                    data[x + y * texture.extent.x + z * texture.extent.x * texture.extent.y] = glm.floor(n * 255).b
//                                }
//                            }
//                    }
//                }
                IntStream
                        .range(0, ext.x * ext.y * ext.z)
                        .parallel()
                        .forEach {
                            val z = it / (ext.x * ext.y)
                            val remainder = it - z * ext.x * ext.y
                            val y = remainder / ext.x
                            val x = remainder % ext.x
                            val v = Vec3(x, y, z) / ext
                            var n = when {
                                FRACTAL -> FractalNoise().noise(v * noiseScale)
                                else -> 20f * glm.perlin(v)
                            }
                            n -= glm.floor(n)

                            data[x + y * ext.x + z * ext.x * ext.y] = glm.floor(n * 255).b
                        }
//
//                val channel = Channel<ProcessingRequest>()
//
//                val processingPool = newThreadPool
//
//                launch(processingPool) {  for (request in channel) doProcessing(it) }
//
//
//
//                for ...
//
//                    for ...
//
//                        channel.sendBlocking(ProcessingRequest(...))
            }
        }
        println("Done in ${time}ms")

        // Create a host-visible staging buffer that contains the raw image data

        // Buffer object
        val bufferCreateInfo = vk.BufferCreateInfo {
            size = texMemSize.L
            usage = VkBufferUsage.TRANSFER_SRC_BIT.i
            sharingMode = VkSharingMode.EXCLUSIVE
        }
        val stagingBuffer: VkBuffer = device createBuffer bufferCreateInfo

        // Allocate host visible memory for data upload
        val memReqs = device getBufferMemoryRequirements stagingBuffer
        val memAllocInfo = vk.MemoryAllocateInfo {
            allocationSize = memReqs.size
            memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT)
        }
        val stagingMemory = device allocateMemory memAllocInfo
        device.bindBufferMemory(stagingBuffer, stagingMemory)

        // Copy texture data into staging buffer
        device.mappingMemory(stagingMemory, 0, memReqs.size) { mapped ->
            memCopy(memAddress(data), mapped, texMemSize.L)
        }

        val copyCmd = super.createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)

        // Image barrier for optimal image

        // The sub resource range describes the regions of the image we will be transition
        val subresourceRange = vk.ImageSubresourceRange {
            aspectMask = VkImageAspect.COLOR_BIT.i
            baseMipLevel = 0
            levelCount = 1
            layerCount = 1
        }
        // Optimal image will be used as destination for the copy, so we must transfer from our
        // initial undefined image layout to the transfer destination layout
        tools.setImageLayout(
                copyCmd,
                texture.image,
                VkImageLayout.UNDEFINED,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                subresourceRange)

        // Copy 3D noise data to texture

        // Setup buffer copy regions
        val bufferCopyRegion = vk.BufferImageCopy {
            imageSubresource.apply {
                aspectMask = VkImageAspect.COLOR_BIT.i
                mipLevel = 0
                baseArrayLayer = 0
                layerCount = 1
            }
            imageExtent(texture.extent)
        }
        copyCmd.copyBufferToImage(
                stagingBuffer,
                texture.image,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                bufferCopyRegion)

        // Change texture image layout to shader read after all mip levels have been copied
        texture.imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
        tools.setImageLayout(
                copyCmd,
                texture.image,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                texture.imageLayout,
                subresourceRange)

        super.flushCommandBuffer(copyCmd, queue, true)

        // Clean up staging resources
        data.free()
        device freeMemory stagingMemory
        device destroyBuffer stagingBuffer
        regenerateNoise = false
    }

    /** Free all Vulkan resources used a texture object */
    fun destroyTextureImage() {
        device.apply {
            if (texture.view != NULL)
                destroyImageView(texture.view)
            if (texture.image != NULL)
                destroyImage(texture.image)
            if (texture.sampler != NULL)
                destroySampler(texture.sampler)
            if (texture.deviceMemory != NULL)
                freeMemory(texture.deviceMemory)
        }
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2)
        clearValues[0].color(defaultClearColor)
        clearValues[1].depthStencil(1f, 0)

        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@Texture3d.renderPass
            renderArea.apply {
                offset(0)
                extent(size)
            }
            this.clearValues = clearValues
        }
        for (i in drawCmdBuffers.indices) {
            // Set target frame buffer
            renderPassBeginInfo.framebuffer(frameBuffers[i])

            drawCmdBuffers[i].apply {

                begin(cmdBufInfo)

                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                setViewport(size)

                setScissor(size)

                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.solid)

                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, vertexBuffer.buffer)
                bindIndexBuffer(indexBuffer.buffer, 0, VkIndexType.UINT32)
                drawIndexed(indexCount, 1, 0, 0, 0)

                endRenderPass()

                end()
            }
        }
    }

    fun draw() {

        super.prepareFrame()

        // Command buffer to be sumitted to the queue
        submitInfo.commandBuffer = drawCmdBuffers[currentBuffer]

        // Submit to queue
        queue submit submitInfo

        super.submitFrame()
    }

    fun generateQuad() {
        // Setup vertices for a single uv-mapped quad made from two triangles
        val vertices = appBuffer.floatBufferOf(
                +1f, +1f, 0f, 1f, 1f, 0f, 0f, 1f,
                -1f, +1f, 0f, 0f, 1f, 0f, 0f, 1f,
                -1f, -1f, 0f, 0f, 0f, 0f, 0f, 1f,
                +1f, -1f, 0f, 1f, 0f, 0f, 0f, 1f)

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
        vertices.inputBinding = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, Vertex.size, VkVertexInputRate.VERTEX)

        // Attribute descriptions
        // Describes memory layout and shader positions
        vertices.inputAttributes = vk.VertexInputAttributeDescription(
                // Location 0 : Position
                VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, Vertex.offsetOf("pos"),
                // Location 1 : Texture coordinates
                VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32_SFLOAT, Vertex.offsetOf("uv"),
                // Location 1 : Vertex normal
                VERTEX_BUFFER_BIND_ID, 2, VkFormat.R32G32B32_SFLOAT, Vertex.offsetOf("normal"))

        vertices.inputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertices.inputBinding
            vertexAttributeDescriptions = vertices.inputAttributes
        }
    }

    fun setupDescriptorPool() {
        // Example uses one ubo and one image sampler
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
                // Binding 1 : Fragment shader image sampler
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 1)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)

        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)

        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)

        descriptorSet = device allocateDescriptorSets allocInfo

        val writeDescriptorSets = vk.WriteDescriptorSet(
                // Binding 0 : Vertex shader uniform buffer
                descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBufferVS.descriptor,
                // Binding 1 : Fragment shader texture sampler
                descriptorSet, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, texture.descriptor)

        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.NONE.i, VkFrontFace.CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Load shaders
        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
            it[0].loadShader("$assetPath/shaders/texture3d/texture3d.vert.spv", VkShaderStage.VERTEX_BIT)
            it[1].loadShader("$assetPath/shaders/texture3d/texture3d.frag.spv", VkShaderStage.FRAGMENT_BIT)
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
        pipelines.solid = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
    }

    /** Prepare and initialize uniform buffer containing shader uniforms */
    fun prepareUniformBuffers() {
        // Vertex shader uniform buffer block
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBufferVS,
                uboVS.size.L)

        updateUniformBuffers()
    }

    fun updateUniformBuffers(viewchanged: Boolean = true) {
        if (viewchanged) {
            uboVS.projection = glm.perspective(60f.rad, size.aspect, 0.001f, 256f)
            val viewMatrix = glm.translate(Mat4(1f), 0f, 0f, zoom)

            uboVS.model = viewMatrix * glm.translate(Mat4(1f), cameraPos)
                    .rotateAssign(rotation.x.rad, 1f, 0f, 0f)
                    .rotateAssign(rotation.y.rad, 0f, 1f, 0f)
                    .rotateAssign(rotation.z.rad, 0f, 0f, 1f)

            uboVS.viewPos = Vec4(0f, 0f, -zoom, 0f)
        } else {
            uboVS.depth += frameTimer * 0.15f
            if (uboVS.depth > 1f)
                uboVS.depth = uboVS.depth - 1f
        }

        uniformBufferVS.mapping { mapped -> uboVS to mapped }
    }

    override fun prepare() {
        super.prepare()
        generateQuad()
        setupVertexDescriptions()
        prepareUniformBuffers()
        prepareNoiseTexture(256, 256, 256)
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
        if (regenerateNoise)
            updateNoiseTexture()
        if (!paused)
            updateUniformBuffers(false)
    }

    override fun viewChanged() = updateUniformBuffers()

//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
//    {
//        if (overlay->header("Settings")) {
//        if (regenerateNoise) { overlay ->
//            text("Generating new noise texture...")
//        } else {
//            if (overlay->button("Generate new texture")) {
//                regenerateNoise = true
//            }
//        }
//    }
//    }
}