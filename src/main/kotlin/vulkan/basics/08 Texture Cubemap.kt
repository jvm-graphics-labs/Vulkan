/*
* Vulkan Example - Cube map texture loading and displaying
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.basics

import gli_.gli
import glm_.L
import glm_.f
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec3.Vec3
import org.lwjgl.system.MemoryUtil.*
import vkk.*
import vulkan.assetPath
import vulkan.base.*

fun main(args: Array<String>) {
    TextureCubemap().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class TextureCubemap : VulkanExampleBase() {

    var displaySkybox = true

    val cubeMap = Texture()

    // Vertex layout for the models
    val vertexLayout = VertexLayout(
            VertexComponent.POSITION,
            VertexComponent.NORMAL,
            VertexComponent.UV)

    object models {
        val skybox = Model()
        val objects = ArrayList<Model>()
        var objectIndex = 0
    }

    object uniformBuffers {
        val `object` = Buffer()
        val skybox = Buffer()
    }

    object uboVS : Bufferizable() {
        var projection = Mat4()
        @Order(1)
        var model = Mat4()
        @Order(2)
        var lodBias = 0f
    }

    object pipelines {
        var skybox  = VkPipeline (NULL)
        var reflect = VkPipeline(NULL)
    }

    object descriptorSets {
        var `object` = VkDescriptorSet (NULL)
        var skybox = VkDescriptorSet (NULL)
    }

    var pipelineLayout = VkPipelineLayout(NULL)
    var descriptorSetLayout = VkDescriptorSetLayout(NULL)

    val objectNames = ArrayList<String>()

    init {
        zoom = -4f
        rotationSpeed = 0.25f
        rotation(-7.25f, -120f, 0f)
        title = "Cube map textures"
//        settings.overlay = true
    }

    override fun destroy() {

        // Clean up used Vulkan resources
        // Note : Inherited destructor cleans up resources stored in base class

        device.apply {
            // Clean up texture resources
            destroyImageView(cubeMap.view)
            destroyImage(cubeMap.image)
            destroySampler(cubeMap.sampler)
            freeMemory(cubeMap.deviceMemory)

            destroyPipeline(pipelines.skybox)
            destroyPipeline(pipelines.reflect)

            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)
        }
        for (model in models.objects)
            model.destroy()

        models.skybox.destroy()

        uniformBuffers.`object`.destroy()
        uniformBuffers.skybox.destroy()

        super.destroy()
    }

    // Enable physical device features required for this example
    override fun getEnabledFeatures() {
        if (deviceFeatures.samplerAnisotropy)
            enabledFeatures.samplerAnisotropy = true

        when {
            deviceFeatures.textureCompressionBC -> enabledFeatures.textureCompressionBC = true
            deviceFeatures.textureCompressionASTC_LDR -> enabledFeatures.textureCompressionASTC_LDR = true
            deviceFeatures.textureCompressionETC2 -> enabledFeatures.textureCompressionETC2 = true
        }
    }

    fun loadCubemap(filename: String, format: VkFormat, forceLinearTiling: Boolean) {

        val texCube = gli_.TextureCube(gli.load(filename))

        assert(texCube.notEmpty())

        cubeMap.size(texCube.extent())
        cubeMap.mipLevels = texCube.levels()


        // Create a host-visible staging buffer that contains the raw image data

        val bufferCreateInfo = vk.BufferCreateInfo {
            size = VkDeviceSize(texCube.size.L)
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
        device.mappingMemory(stagingMemory, VkDeviceSize(0), memReqs.size) { data ->
            memCopy(memAddress(texCube.data()), data, texCube.size.L)
        }

        // Create optimal tiled target image
        val imageCreateInfo = vk.ImageCreateInfo {
            imageType = VkImageType.`2D`
            this.format = format
            mipLevels = cubeMap.mipLevels
            samples = VkSampleCount.`1_BIT`
            tiling = VkImageTiling.OPTIMAL
            sharingMode = VkSharingMode.EXCLUSIVE
            initialLayout = VkImageLayout.UNDEFINED
            extent.set(cubeMap.size.x, cubeMap.size.y, 1)
            usage = VkImageUsage.TRANSFER_DST_BIT or VkImageUsage.SAMPLED_BIT
            // Cube faces count as array layers in Vulkan
            arrayLayers = 6
            // This flag is required for cube map images
            flags = VkImageCreate.CUBE_COMPATIBLE_BIT.i
        }
        cubeMap.image = device createImage imageCreateInfo

        device.getImageMemoryRequirements(cubeMap.image, memReqs)

        memAllocInfo.allocationSize = memReqs.size
        memAllocInfo.memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)

        cubeMap.deviceMemory = device allocateMemory memAllocInfo
        device.bindImageMemory(cubeMap.image, cubeMap.deviceMemory)

        val copyCmd = createCommandBuffer(VkCommandBufferLevel.PRIMARY, true)

        // Setup buffer copy regions for each face including all of it's miplevels
        val bufferCopyRegions = vk.BufferImageCopy(6 * cubeMap.mipLevels)
        var offset = VkDeviceSize(0)

        for (face in 0..5)
            for (level in 0 until cubeMap.mipLevels) {

                bufferCopyRegions[face * cubeMap.mipLevels + level].apply {
                    imageSubresource.apply {
                        aspectMask = VkImageAspect.COLOR_BIT.i
                        mipLevel = level
                        baseArrayLayer = face
                        layerCount = 1
                    }
                    val extent = texCube[face][level].extent()
                    imageExtent.set(extent.x, extent.y, 1)
                    bufferOffset = offset
                }
                // Increase offset into staging buffer for next level / face
                offset += texCube[face][level].size
            }

        // Image barrier for optimal image (target)
        // Set initial layout for all array layers (faces) of the optimal (target) tiled texture
        val subresourceRange = vk.ImageSubresourceRange {
            aspectMask = VkImageAspect.COLOR_BIT.i
            baseMipLevel = 0
            levelCount = cubeMap.mipLevels
            layerCount = 6
        }
        tools.setImageLayout(
                copyCmd,
                cubeMap.image,
                VkImageLayout.UNDEFINED,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                subresourceRange)

        // Copy the cube map faces from the staging buffer to the optimal tiled image
        copyCmd.copyBufferToImage(
                stagingBuffer,
                cubeMap.image,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                bufferCopyRegions)

        // Change texture image layout to shader read after all faces have been copied
        cubeMap.imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
        tools.setImageLayout(
                copyCmd,
                cubeMap.image,
                VkImageLayout.TRANSFER_DST_OPTIMAL,
                cubeMap.imageLayout,
                subresourceRange)

        super.flushCommandBuffer(copyCmd, queue, true)

        // Create sampler
        val sampler = vk.SamplerCreateInfo {
            minMagFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            addressModeUVW = VkSamplerAddressMode.CLAMP_TO_EDGE
            mipLodBias = 0f
            compareOp = VkCompareOp.NEVER
            minLod = 0f
            maxLod = cubeMap.mipLevels.f
            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
            maxAnisotropy = 1f
        }
        if (vulkanDevice.features.samplerAnisotropy) {
            sampler.maxAnisotropy = vulkanDevice.properties.limits.maxSamplerAnisotropy
            sampler.anisotropyEnable = true
        }
        cubeMap.sampler = device createSampler sampler

        // Create image view
        val view = vk.ImageViewCreateInfo {
            // Cube map view type
            viewType = VkImageViewType.CUBE
            this.format = format
            components(VkComponentSwizzle.R, VkComponentSwizzle.G, VkComponentSwizzle.B, VkComponentSwizzle.A)
            this.subresourceRange.apply {
                set(VkImageAspect.COLOR_BIT.i, 0, 1, 0, 1)
                // 6 array layers (faces)
                layerCount = 6
                // Set number of mip levels
                levelCount = cubeMap.mipLevels
            }
            image = cubeMap.image
        }
        cubeMap.view = device createImageView view

        // Clean up staging resources
        device freeMemory stagingMemory
        device destroyBuffer stagingBuffer
    }

    fun loadTextures() {
        // Vulkan core supports three different compressed texture formats
        // As the support differs between implemementations we need to check device features and select a proper format and file
        val (filename, format) = when {

            deviceFeatures.textureCompressionBC -> "cubemap_yokohama_bc3_unorm.ktx" to VkFormat.BC2_UNORM_BLOCK

            deviceFeatures.textureCompressionASTC_LDR -> "cubemap_yokohama_astc_8x8_unorm.ktx" to VkFormat.ASTC_8x8_UNORM_BLOCK

            deviceFeatures.textureCompressionETC2 -> "cubemap_yokohama_etc2_unorm.ktx" to VkFormat.ETC2_R8G8B8_UNORM_BLOCK

            else -> tools.exitFatal("Device does not support any compressed texture format!", ERROR_FEATURE_NOT_PRESENT)
        }

        loadCubemap("$assetPath/textures/$filename", format, false)
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2).also {
            it[0].color(defaultClearColor)
            it[1].depthStencil.set(1f, 0)
        }
        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@TextureCubemap.renderPass
            renderArea.offset.set(0, 0)
            renderArea.extent.set(size.x, size.y)
            this.clearValues = clearValues
        }
        for (i in drawCmdBuffers.indices) {

            // Set target frame buffer
            renderPassBeginInfo.framebuffer(frameBuffers[i].L) // TODO BUG

            drawCmdBuffers[i].apply {

                begin(cmdBufInfo)

                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                setViewport(size)
                setScissor(size)

                // Skybox
                if (displaySkybox) {
                    bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSets.skybox)
                    bindVertexBuffers(0, models.skybox.vertices.buffer)
                    bindIndexBuffer(models.skybox.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
                    bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.skybox)
                    drawIndexed(models.skybox.indexCount, 1, 0, 0, 0)
                }

                // 3D object
                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSets.`object`)
                bindVertexBuffers(models.objects[models.objectIndex].vertices.buffer)
                bindIndexBuffer(models.objects[models.objectIndex].indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.reflect)
                drawIndexed(models.objects[models.objectIndex].indexCount, 1, 0, 0, 0)

                drawUI()

                endRenderPass()

                end()
            }
        }
    }

    fun loadAssets() {
        // Skybox
        models.skybox.loadFromFile("$assetPath/models/cube.obj", vertexLayout, 0.05f, vulkanDevice, queue)
        // Objects
        val filenames = listOf("sphere.obj", "teapot.dae", "torusknot.obj")
        objectNames += listOf("Sphere", "Teapot", "Torusknot", "Venus")
        for (file in filenames) {
            val model = Model()
            val scale = 0.05f * if (file == "venus.fbx") 3f else 1f
            model.loadFromFile("$assetPath/models/$file", vertexLayout, scale, vulkanDevice, queue)
            models.objects += model
        }
    }

    fun setupDescriptorPool() {

        val poolSizes = vk.DescriptorPoolSize(
                VkDescriptorType.UNIFORM_BUFFER, 2,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 2)

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

    fun setupDescriptorSets() {

        // Image descriptor for the cube map texture
        val textureDescriptor = vk.DescriptorImageInfo(cubeMap.sampler, cubeMap.view, cubeMap.imageLayout)

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)

        // 3D object descriptor set
        descriptorSets.`object` = device allocateDescriptorSets allocInfo

        val writeDescriptorSets = vk.WriteDescriptorSet(2).also {
            // Binding 0 : Vertex shader uniform buffer
            it[0](descriptorSets.`object`, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.`object`.descriptor)
            // Binding 1 : Fragment shader cubemap sampler
            it[1](descriptorSets.`object`, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, textureDescriptor)
        }
        device.updateDescriptorSets(writeDescriptorSets)

        // Sky box descriptor set
        descriptorSets.skybox = device allocateDescriptorSets allocInfo

        writeDescriptorSets.also {
            // Binding 0 : Vertex shader uniform buffer
            it[0](descriptorSets.skybox, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.skybox.descriptor)
            // Binding 1 : Fragment shader cubemap sampler
            it[1](descriptorSets.skybox, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, textureDescriptor)
        }
        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.BACK_BIT.i, VkFrontFace.COUNTER_CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(false, false, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Vertex bindings and attributes
        val vertexInputBinding = vk.VertexInputBindingDescription(0, vertexLayout.stride, VkVertexInputRate.VERTEX)

        val vertexInputAttributes = vk.VertexInputAttributeDescription(
                0, 0, VkFormat.R32G32B32_SFLOAT, 0,     // Location 0: Position
                0, 1, VkFormat.R32G32B32_SFLOAT, Vec3.size)    // Location 1: Normal

        val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertexInputBinding
            vertexAttributeDescriptions = vertexInputAttributes
        }

        val shaderStages = vk.PipelineShaderStageCreateInfo(2)

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass, 0).also {
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
            it.vertexInputState = vertexInputState
        }
        // Skybox pipeline (background cube)
        shaderStages[0].loadShader("$assetPath/shaders/texturecubemap/skybox.vert.spv", VkShaderStage.VERTEX_BIT)
        shaderStages[1].loadShader("$assetPath/shaders/texturecubemap/skybox.frag.spv", VkShaderStage.FRAGMENT_BIT)
        pipelines.skybox = device.createPipeline(pipelineCache, pipelineCreateInfo)

        // Cube map reflect pipeline
        shaderStages[0].loadShader("$assetPath/shaders/texturecubemap/reflect.vert.spv", VkShaderStage.VERTEX_BIT)
        shaderStages[1].loadShader("$assetPath/shaders/texturecubemap/reflect.frag.spv", VkShaderStage.FRAGMENT_BIT)
        // Enable depth test and write
        depthStencilState.depthWriteEnable = true
        depthStencilState.depthTestEnable = true
        // Flip cull mode
        rasterizationState.cullMode = VkCullMode.FRONT_BIT.i
        pipelines.reflect = device.createPipeline(pipelineCache, pipelineCreateInfo)
    }

    /** Prepare and initialize uniform buffer containing shader uniforms */
    fun prepareUniformBuffers() {

        // Objact vertex shader uniform buffer
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBuffers.`object`,
                VkDeviceSize(uboVS.size.L))

        // Skybox vertex shader uniform buffer
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBuffers.skybox,
                VkDeviceSize(uboVS.size.L))

        // Map persistent
        uniformBuffers.`object`.map()
        uniformBuffers.skybox.map()

        updateUniformBuffers()
    }

    fun updateUniformBuffers() {

        // 3D object
        var viewMatrix = Mat4(1f)
        uboVS.projection = glm.perspective(60.0f.rad, size.aspect, 0.001f, 256f)
        viewMatrix = glm.translate(viewMatrix, 0f, 0f, zoom)

        uboVS.model put 1f
        uboVS.model = viewMatrix * glm.translate(uboVS.model, cameraPos)
                .rotateAssign(rotation.x.rad, 1f, 0f, 0f)
                .rotateAssign(rotation.y.rad, 0f, 1f, 0f)
                .rotateAssign(rotation.z.rad, 0f, 0f, 1f)

        uboVS to uniformBuffers.`object`.mapped

        // Skybox
        viewMatrix put 1f
        uboVS.projection = glm.perspective(60f.rad, size.aspect, 0.001f, 256f)

        uboVS.model put viewMatrix
        uboVS.model
                .rotateAssign(rotation.x.rad, 1f, 0f, 0f)
                .rotateAssign(rotation.y.rad, 0f, 1f, 0f)
                .rotateAssign(rotation.z.rad, 0f, 0f, 1f)

        uboVS to uniformBuffers.skybox.mapped
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
        loadAssets()
        prepareUniformBuffers()
        setupDescriptorSetLayout()
        preparePipelines()
        setupDescriptorPool()
        setupDescriptorSets()
        buildCommandBuffers()
        prepared = true
        window.show()
    }

    override fun render() {
        if (!prepared)
            return
        draw()
    }

    override fun viewChanged() = updateUniformBuffers()

//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay)
//    {
//        if (overlay->header("Settings")) {
//        if (overlay->sliderFloat("LOD bias", &uboVS.lodBias, 0.0f, (float)cubeMap.mipLevels)) {
//        updateUniformBuffers()
//    }
//        if (overlay->comboBox("Object type", &models.objectIndex, objectNames)) {
//        buildCommandBuffers()
//    }
//        if (overlay->checkBox("Skybox", &displaySkybox)) {
//        buildCommandBuffers()
//    }
//    }
//    }
}