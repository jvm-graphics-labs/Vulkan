///*
//* Vulkan Example - 3D texture loading (and generation using perlin noise) example
//*
//* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
//*
//* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
//*/
//
//package vulkan.basics
//
//import glm_.mat4x4.Mat4
//import glm_.vec2.Vec2i
//import glm_.vec3.Vec3
//import glm_.vec3.Vec3i
//import glm_.vec4.Vec4
//import org.lwjgl.system.MemoryUtil.NULL
//import org.lwjgl.vulkan.VkDescriptorImageInfo
//import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
//import org.lwjgl.vulkan.VkVertexInputAttributeDescription
//import org.lwjgl.vulkan.VkVertexInputBindingDescription
//import uno.buffer.bufferBig
//import vkn.*
//import vulkan.base.Buffer
//import vulkan.base.Model
//import vulkan.base.VulkanExampleBase
//
//
//fun main(args: Array<String>) {
//    Texture3d().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//private class Texture3d : VulkanExampleBase() {
//
//    /** Vertex layout for this example */
//    object Vertex {
////    float pos[3];
////    float uv[2];
////    float normal[3];
//    }
//
//    /** Fractal noise generator based on perlin noise above */
//    class FractalNoise {
//
//        private :
//        PerlinNoise<float> perlinNoise
//        uint32_t octaves
//        T frequency
//        T amplitude
//        T persistence
//        public :
//
//        FractalNoise(const PerlinNoise<T> &perlinNoise)
//        {
//            this->perlinNoise = perlinNoise
//            octaves = 6
//            persistence = (T)0.5
//        }
//
//        T noise(T x, T y, T z)
//        {
//            T sum = 0
//            T frequency =(T)1
//            T amplitude =(T)1
//            T max =(T)0
//            for (int32_t i = 0; i < octaves; i++)
//            {
//                sum += perlinNoise.noise(x * frequency, y * frequency, z * frequency) * amplitude
//                max += amplitude
//                amplitude *= persistence
//                frequency *= (T)2
//            }
//
//            sum = sum / max
//            return (sum + (T)1.0) / (T)2.0
//        }
//    }
//
//    /** Contains all Vulkan objects that are required to store and use a 3D texture */
//    object texture {
//        var sampler: VkSampler = NULL
//        var image: VkImage = NULL
//        var imageLayout = VkImageLayout.UNDEFINED
//        var deviceMemory: VkDeviceMemory = NULL
//        var view: VkImageView = NULL
//        lateinit var descriptor: VkDescriptorImageInfo
//        var format = VkFormat.UNDEFINED
//        val extent = Vec3i()
//        var mipLevels = 0
//    }
//
//    var regenerateNoise = true
//
//    object models {
//        val cube = Model()
//    }
//
//    object vertices {
//        lateinit var inputState: VkPipelineVertexInputStateCreateInfo
//        lateinit var inputBinding: VkVertexInputBindingDescription
//        lateinit var inputAttributes: VkVertexInputAttributeDescription.Buffer
//    }
//
//    val vertexBuffer = Buffer()
//    val indexBuffer = Buffer()
//    var indexCount = 0
//
//    val uniformBufferVS = Buffer()
//
//    object uboVS : Bufferizable() {
//        lateinit var projection: Mat4
//        lateinit var model: Mat4
//        lateinit var viewPos: Vec4
//        val depth = 0f
//        override val fieldOrder = arrayOf("projection", "model", "viewPos", "depth")
//    }
//
//    object pipelines {
//        var solid: VkPipeline = NULL
//    }
//
//    var pipelineLayout: VkPipelineLayout = NULL
//    var descriptorSet: VkDescriptorSet = NULL
//    var descriptorSetLayout: VkDescriptorSetLayout = NULL
//
//    init {
//        zoom = -2.5f
//        rotation(0f, 15f, 0f)
//        title = "3D textures"
////        settings.overlay = true
////        srand((unsigned int) time (NULL))
//    }
//
//    override fun destroy() {
//        // Clean up used Vulkan resources
//        // Note : Inherited destructor cleans up resources stored in base class
//
//        destroyTextureImage()
//
//        device.apply {
//            destroyPipeline(pipelines.solid)
//
//            destroyPipelineLayout(pipelineLayout)
//            destroyDescriptorSetLayout(descriptorSetLayout)
//        }
//        vertexBuffer.destroy()
//        indexBuffer.destroy()
//        uniformBufferVS.destroy()
//    }
//
//    /** Prepare all Vulkan resources for the 3D texture (including descriptors)
//     *  Does not fill the texture with data */
//    fun prepareNoiseTexture(size: Vec2i, depth: Int) {
//        // A 3D texture is described as width x height x depth
//        texture.extent.put(size.x, size.y, depth) // TODO glm
//        texture.mipLevels = 1
//        texture.format = VkFormat.R8_UNORM
//
//        // Format support check
//        // 3D texture support in Vulkan is mandatory (in contrast to OpenGL) so no need to check if it's supported
//        val formatProperties = physicalDevice getFormatProperties texture.format
//        // Check if format supports transfer
//        if (formatProperties.optimalTilingFeatures hasnt VkImageUsage.TRANSFER_DST_BIT) {
//            System.err.println("Error: Device does not support flag TRANSFER_DST for selected texture format!")
//            return
//        }
//        // Check if GPU supports requested 3D texture dimensions
//        val maxImageDimension3D = vulkanDevice.properties.limits.maxImageDimension3D
//        if (size.x > maxImageDimension3D || size.y > maxImageDimension3D || depth > maxImageDimension3D) {
//            System.out.println("Error: Requested texture dimensions is greater than supported 3D texture dimension!")
//            return
//        }
//
//        // Create optimal tiled target image
//        val imageCreateInfo = vk.ImageCreateInfo {
//            imageType = VkImageType.`3D`
//            format = texture.format
//            mipLevels = texture.mipLevels
//            arrayLayers = 1
//            samples = VkSampleCount.`1_BIT`
//            tiling = VkImageTiling.OPTIMAL
//            sharingMode = VkSharingMode.EXCLUSIVE
//            extent(texture.extent)
//            // Set initial layout of the image to undefined
//            initialLayout = VkImageLayout.UNDEFINED
//            usage = VkImageUsage.TRANSFER_DST_BIT or VkImageUsage.SAMPLED_BIT
//        }
//        texture.image = device createImage imageCreateInfo
//
//        // Device local memory to back up image
//        val memReqs = device getImageMemoryRequirements texture.image
//        val memAllocInfo = vk.MemoryAllocateInfo {
//            allocationSize = memReqs.size
//            memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
//        }
//        texture.deviceMemory = device allocateMemory memAllocInfo
//        device.bindImageMemory(texture.image, texture.deviceMemory, 0)
//
//        // Create sampler
//        val sampler = vk.SamplerCreateInfo {
//            magFilter = VkFilter.LINEAR
//            minFilter = VkFilter.LINEAR
//            mipmapMode = VkSamplerMipmapMode.LINEAR
//            addressModeU = VkSamplerAddressMode.CLAMP_TO_EDGE
//            addressModeV = VkSamplerAddressMode.CLAMP_TO_EDGE
//            addressModeW = VkSamplerAddressMode.CLAMP_TO_EDGE
//            mipLodBias = 0f
//            compareOp = VkCompareOp.NEVER
//            minLod = 0f
//            maxLod = 0f
//            maxAnisotropy = 1f
//            anisotropyEnable = false
//            borderColor = VkBorderColor.FLOAT_OPAQUE_WHITE
//        }
//        texture.sampler = device createSampler sampler
//
//        // Create image view
//        val view = vk.ImageViewCreateInfo {
//            image = texture.image
//            viewType = VkImageViewType.`3D`
//            format = texture.format
//            components(VkComponentSwizzle.R, VkComponentSwizzle.G, VkComponentSwizzle.B, VkComponentSwizzle.A)
//            subresourceRange.apply {
//                aspectMask = VkImageAspect.COLOR_BIT.i
//                baseMipLevel = 0
//                baseArrayLayer = 0
//                layerCount = 1
//                levelCount = 1
//            }
//        }
//        texture.view = device createImageView view
//
//        // Fill image descriptor image info to be used descriptor set setup
//        texture.descriptor.apply {
//            imageLayout = VkImageLayout.SHADER_READ_ONLY_OPTIMAL
//            imageView = texture.view
//            this.sampler = texture.sampler
//        }
//    }
//
//    /** Generate randomized noise and upload it to the 3D texture using staging */
//    fun updateNoiseTexture() {
//
//        val ext = texture.extent
//        val texMemSize = ext.x * ext.y * ext.z
//
//        val data = bufferBig(texMemSize)
//
//        // Generate perlin based noise
//        println("Generating ${ext.x} x ${ext.y} x ${ext.z} noise texture...")
//
//        auto tStart = std ::chrono::high_resolution_clock::now()
//
//        PerlinNoise<float> perlinNoise
//                FractalNoise<float> fractalNoise (perlinNoise)
//
//        const int32_t noiseType = rand() % 2
//        const float noiseScale = static_cast<float>(rand() % 10) + 4.0f
//
//        #pragma omp parallel for
//            for (int32_t z = 0; z < texture.depth; z++)
//        {
//            for (uint32_t y = 0; y < texture.height; y++)
//            {
//                for (int32_t x = 0; x < texture.width; x++)
//                {
//                    float nx =(float) x /(float) texture . width
//                            float ny =(float) y /(float) texture . height
//                            float nz =(float) z /(float) texture . depth
//                    #define FRACTAL
//                    #ifdef FRACTAL
//                        float n = fractalNoise . noise (nx * noiseScale, ny * noiseScale, nz * noiseScale)
//                    #else
//                    float n = 20.0 * perlinNoise.noise(nx, ny, nz)
//                    #endif
//                    n = n - floor(n)
//
//                    data[x + y * texture.width + z * texture.width * texture.height] = static_cast<uint8_t>(floor(n * 255))
//                }
//            }
//        }
//
//        auto tEnd = std ::chrono::high_resolution_clock::now()
//        auto tDiff = std ::chrono::duration < double, std::milli>(tEnd-tStart).count()
//
//        std::cout < < "Done in " << tDiff << "ms" << std::endl
//
//        // Create a host-visible staging buffer that contains the raw image data
//        VkBuffer stagingBuffer
//                VkDeviceMemory stagingMemory
//
//                // Buffer object
//                VkBufferCreateInfo bufferCreateInfo = vks ::initializers::bufferCreateInfo()
//        bufferCreateInfo.size = texMemSize
//        bufferCreateInfo.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT
//        bufferCreateInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE
//        VK_CHECK_RESULT(vkCreateBuffer(device, & bufferCreateInfo, nullptr, & stagingBuffer))
//
//        // Allocate host visible memory for data upload
//        VkMemoryAllocateInfo memAllocInfo = vks ::initializers::memoryAllocateInfo()
//        VkMemoryRequirements memReqs = {}
//        vkGetBufferMemoryRequirements(device, stagingBuffer, & memReqs)
//        memAllocInfo.allocationSize = memReqs.size
//        memAllocInfo.memoryTypeIndex = vulkanDevice->getMemoryType(memReqs.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
//        VK_CHECK_RESULT(vkAllocateMemory(device, & memAllocInfo, nullptr, & stagingMemory))
//        VK_CHECK_RESULT(vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0))
//
//        // Copy texture data into staging buffer
//        uint8_t * mapped
//        VK_CHECK_RESULT(vkMapMemory(device, stagingMemory, 0, memReqs.size, 0, (void * *)& mapped))
//        memcpy(mapped, data, texMemSize)
//        vkUnmapMemory(device, stagingMemory)
//
//        VkCommandBuffer copyCmd = VulkanExampleBase ::createCommandBuffer(VK_COMMAND_BUFFER_LEVEL_PRIMARY, true)
//
//        // Image barrier for optimal image
//
//        // The sub resource range describes the regions of the image we will be transition
//        VkImageSubresourceRange subresourceRange = {}
//        subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//        subresourceRange.baseMipLevel = 0
//        subresourceRange.levelCount = 1
//        subresourceRange.layerCount = 1
//
//        // Optimal image will be used as destination for the copy, so we must transfer from our
//        // initial undefined image layout to the transfer destination layout
//        vks::tools::setImageLayout(
//                copyCmd,
//                texture.image,
//                VK_IMAGE_LAYOUT_UNDEFINED,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                subresourceRange)
//
//        // Copy 3D noise data to texture
//
//        // Setup buffer copy regions
//        VkBufferImageCopy bufferCopyRegion {}
//        bufferCopyRegion.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT
//        bufferCopyRegion.imageSubresource.mipLevel = 0
//        bufferCopyRegion.imageSubresource.baseArrayLayer = 0
//        bufferCopyRegion.imageSubresource.layerCount = 1
//        bufferCopyRegion.imageExtent.width = texture.width
//        bufferCopyRegion.imageExtent.height = texture.height
//        bufferCopyRegion.imageExtent.depth = texture.depth
//
//        vkCmdCopyBufferToImage(
//                copyCmd,
//                stagingBuffer,
//                texture.image,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                1,
//                & bufferCopyRegion)
//
//        // Change texture image layout to shader read after all mip levels have been copied
//        texture.imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
//        vks::tools::setImageLayout(
//                copyCmd,
//                texture.image,
//                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
//                texture.imageLayout,
//                subresourceRange)
//
//        VulkanExampleBase::flushCommandBuffer(copyCmd, queue, true)
//
//        // Clean up staging resources
//        delete[] data
//                vkFreeMemory(device, stagingMemory, nullptr)
//        vkDestroyBuffer(device, stagingBuffer, nullptr)
//        regenerateNoise = false
//    }
//
//    // Free all Vulkan resources used a texture object
//    void destroyTextureImage(Texture texture)
//    {
//        if (texture.view != VK_NULL_HANDLE)
//            vkDestroyImageView(device, texture.view, nullptr)
//        if (texture.image != VK_NULL_HANDLE)
//            vkDestroyImage(device, texture.image, nullptr)
//        if (texture.sampler != VK_NULL_HANDLE)
//            vkDestroySampler(device, texture.sampler, nullptr)
//        if (texture.deviceMemory != VK_NULL_HANDLE)
//            vkFreeMemory(device, texture.deviceMemory, nullptr)
//    }
//
//    void buildCommandBuffers()
//    {
//        VkCommandBufferBeginInfo cmdBufInfo = vks ::initializers::commandBufferBeginInfo()
//
//        VkClearValue clearValues [2]
//        clearValues[0].color = defaultClearColor
//        clearValues[1].depthStencil = { 1.0f, 0 }
//
//        VkRenderPassBeginInfo renderPassBeginInfo = vks ::initializers::renderPassBeginInfo()
//        renderPassBeginInfo.renderPass = renderPass
//        renderPassBeginInfo.renderArea.offset.x = 0
//        renderPassBeginInfo.renderArea.offset.y = 0
//        renderPassBeginInfo.renderArea.extent.width = width
//        renderPassBeginInfo.renderArea.extent.height = height
//        renderPassBeginInfo.clearValueCount = 2
//        renderPassBeginInfo.pClearValues = clearValues
//
//        for (int32_t i = 0; i < drawCmdBuffers.size(); ++i)
//        {
//            // Set target frame buffer
//            renderPassBeginInfo.framebuffer = frameBuffers[i]
//
//            VK_CHECK_RESULT(vkBeginCommandBuffer(drawCmdBuffers[i], & cmdBufInfo))
//
//            vkCmdBeginRenderPass(drawCmdBuffers[i], & renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)
//
//            VkViewport viewport = vks ::initializers::viewport((float) width, (float) height, 0.0f, 1.0f)
//            vkCmdSetViewport(drawCmdBuffers[i], 0, 1, & viewport)
//
//            VkRect2D scissor = vks ::initializers::rect2D(width, height, 0, 0)
//            vkCmdSetScissor(drawCmdBuffers[i], 0, 1, & scissor)
//
//            vkCmdBindDescriptorSets(drawCmdBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0, 1, & descriptorSet, 0, NULL)
//            vkCmdBindPipeline(drawCmdBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipelines.solid)
//
//            VkDeviceSize offsets [1] = { 0 }
//            vkCmdBindVertexBuffers(drawCmdBuffers[i], VERTEX_BUFFER_BIND_ID, 1, & vertexBuffer . buffer, offsets)
//            vkCmdBindIndexBuffer(drawCmdBuffers[i], indexBuffer.buffer, 0, VK_INDEX_TYPE_UINT32)
//            vkCmdDrawIndexed(drawCmdBuffers[i], indexCount, 1, 0, 0, 0)
//
//            vkCmdEndRenderPass(drawCmdBuffers[i])
//
//            VK_CHECK_RESULT(vkEndCommandBuffer(drawCmdBuffers[i]))
//        }
//    }
//
//    void draw()
//    {
//        VulkanExampleBase::prepareFrame()
//
//        // Command buffer to be sumitted to the queue
//        submitInfo.commandBufferCount = 1
//        submitInfo.pCommandBuffers = & drawCmdBuffers [currentBuffer]
//
//        // Submit to queue
//        VK_CHECK_RESULT(vkQueueSubmit(queue, 1, & submitInfo, VK_NULL_HANDLE))
//
//        VulkanExampleBase::submitFrame()
//    }
//
//    void generateQuad()
//    {
//        // Setup vertices for a single uv-mapped quad made from two triangles
//        std::vector<Vertex> vertices =
//        {
//            { { 1.0f, 1.0f, 0.0f }, { 1.0f, 1.0f },{ 0.0f, 0.0f, 1.0f } },
//            { { -1.0f, 1.0f, 0.0f }, { 0.0f, 1.0f },{ 0.0f, 0.0f, 1.0f } },
//            { { -1.0f, -1.0f, 0.0f }, { 0.0f, 0.0f },{ 0.0f, 0.0f, 1.0f } },
//            { { 1.0f, -1.0f, 0.0f }, { 1.0f, 0.0f },{ 0.0f, 0.0f, 1.0f } }
//        }
//
//        // Setup indices
//        std::vector<uint32_t> indices = { 0, 1, 2, 2, 3, 0 }
//        indexCount = static_cast<uint32_t>(indices.size())
//
//        // Create buffers
//        // For the sake of simplicity we won't stage the vertex data to the gpu memory
//        // Vertex buffer
//        VK_CHECK_RESULT(vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_VERTEX_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &vertexBuffer,
//        vertices.size() * sizeof(Vertex),
//        vertices.data()))
//        // Index buffer
//        VK_CHECK_RESULT(vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_INDEX_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &indexBuffer,
//        indices.size() * sizeof(uint32_t),
//        indices.data()))
//    }
//
//    void setupVertexDescriptions()
//    {
//        // Binding description
//        vertices.inputBinding.resize(1)
//        vertices.inputBinding[0] =
//                vks::initializers::vertexInputBindingDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        sizeof(Vertex),
//                        VK_VERTEX_INPUT_RATE_VERTEX)
//
//        // Attribute descriptions
//        // Describes memory layout and shader positions
//        vertices.inputAttributes.resize(3)
//        // Location 0 : Position
//        vertices.inputAttributes[0] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        0,
//                        VK_FORMAT_R32G32B32_SFLOAT,
//                        offsetof(Vertex, pos))
//        // Location 1 : Texture coordinates
//        vertices.inputAttributes[1] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        1,
//                        VK_FORMAT_R32G32_SFLOAT,
//                        offsetof(Vertex, uv))
//        // Location 1 : Vertex normal
//        vertices.inputAttributes[2] =
//                vks::initializers::vertexInputAttributeDescription(
//                        VERTEX_BUFFER_BIND_ID,
//                        2,
//                        VK_FORMAT_R32G32B32_SFLOAT,
//                        offsetof(Vertex, normal))
//
//        vertices.inputState = vks::initializers::pipelineVertexInputStateCreateInfo()
//        vertices.inputState.vertexBindingDescriptionCount = static_cast<uint32_t>(vertices.inputBinding.size())
//        vertices.inputState.pVertexBindingDescriptions = vertices.inputBinding.data()
//        vertices.inputState.vertexAttributeDescriptionCount = static_cast<uint32_t>(vertices.inputAttributes.size())
//        vertices.inputState.pVertexAttributeDescriptions = vertices.inputAttributes.data()
//    }
//
//    void setupDescriptorPool()
//    {
//        // Example uses one ubo and one image sampler
//        std::vector<VkDescriptorPoolSize> poolSizes =
//        {
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1),
//            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1)
//        }
//
//        VkDescriptorPoolCreateInfo descriptorPoolInfo =
//        vks::initializers::descriptorPoolCreateInfo(
//                static_cast<uint32_t>(poolSizes.size()),
//                poolSizes.data(),
//                2)
//
//        VK_CHECK_RESULT(vkCreateDescriptorPool(device, & descriptorPoolInfo, nullptr, & descriptorPool))
//    }
//
//    void setupDescriptorSetLayout()
//    {
//        std::vector<VkDescriptorSetLayoutBinding> setLayoutBindings =
//        {
//            // Binding 0 : Vertex shader uniform buffer
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    VK_SHADER_STAGE_VERTEX_BIT,
//                    0),
//            // Binding 1 : Fragment shader image sampler
//            vks::initializers::descriptorSetLayoutBinding(
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    VK_SHADER_STAGE_FRAGMENT_BIT,
//                    1)
//        }
//
//        VkDescriptorSetLayoutCreateInfo descriptorLayout =
//        vks::initializers::descriptorSetLayoutCreateInfo(
//                setLayoutBindings.data(),
//                static_cast<uint32_t>(setLayoutBindings.size()))
//
//        VK_CHECK_RESULT(vkCreateDescriptorSetLayout(device, & descriptorLayout, nullptr, & descriptorSetLayout))
//
//        VkPipelineLayoutCreateInfo pPipelineLayoutCreateInfo =
//        vks::initializers::pipelineLayoutCreateInfo(
//                & descriptorSetLayout,
//        1)
//
//        VK_CHECK_RESULT(vkCreatePipelineLayout(device, & pPipelineLayoutCreateInfo, nullptr, & pipelineLayout))
//    }
//
//    void setupDescriptorSet()
//    {
//        VkDescriptorSetAllocateInfo allocInfo =
//        vks::initializers::descriptorSetAllocateInfo(
//                descriptorPool,
//                & descriptorSetLayout,
//        1)
//
//        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & descriptorSet))
//
//        std::vector<VkWriteDescriptorSet> writeDescriptorSets =
//        {
//            // Binding 0 : Vertex shader uniform buffer
//            vks::initializers::writeDescriptorSet(
//                    descriptorSet,
//                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
//                    0,
//                    & uniformBufferVS . descriptor),
//            // Binding 1 : Fragment shader texture sampler
//            vks::initializers::writeDescriptorSet(
//                    descriptorSet,
//                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
//                    1,
//                    & texture . descriptor)
//        }
//
//        vkUpdateDescriptorSets(device, static_cast<uint32_t>(writeDescriptorSets.size()), writeDescriptorSets.data(), 0, NULL)
//    }
//
//    void preparePipelines()
//    {
//        VkPipelineInputAssemblyStateCreateInfo inputAssemblyState =
//        vks::initializers::pipelineInputAssemblyStateCreateInfo(
//                VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
//                0,
//                VK_FALSE)
//
//        VkPipelineRasterizationStateCreateInfo rasterizationState =
//        vks::initializers::pipelineRasterizationStateCreateInfo(
//                VK_POLYGON_MODE_FILL,
//                VK_CULL_MODE_NONE,
//                VK_FRONT_FACE_COUNTER_CLOCKWISE,
//                0)
//
//        VkPipelineColorBlendAttachmentState blendAttachmentState =
//        vks::initializers::pipelineColorBlendAttachmentState(
//                0xf,
//                VK_FALSE)
//
//        VkPipelineColorBlendStateCreateInfo colorBlendState =
//        vks::initializers::pipelineColorBlendStateCreateInfo(
//                1,
//                & blendAttachmentState)
//
//        VkPipelineDepthStencilStateCreateInfo depthStencilState =
//        vks::initializers::pipelineDepthStencilStateCreateInfo(
//                VK_TRUE,
//                VK_TRUE,
//                VK_COMPARE_OP_LESS_OR_EQUAL)
//
//        VkPipelineViewportStateCreateInfo viewportState =
//        vks::initializers::pipelineViewportStateCreateInfo(1, 1, 0)
//
//        VkPipelineMultisampleStateCreateInfo multisampleState =
//        vks::initializers::pipelineMultisampleStateCreateInfo(
//                VK_SAMPLE_COUNT_1_BIT,
//                0)
//
//        std::vector<VkDynamicState> dynamicStateEnables = { VK_DYNAMIC_STATE_VIEWPORT,
//                                                            VK_DYNAMIC_STATE_SCISSOR
//        }
//        VkPipelineDynamicStateCreateInfo dynamicState =
//        vks::initializers::pipelineDynamicStateCreateInfo(
//                dynamicStateEnables.data(),
//                static_cast<uint32_t>(dynamicStateEnables.size()),
//                0)
//
//        // Load shaders
//        std::array < VkPipelineShaderStageCreateInfo, 2> shaderStages
//
//        shaderStages[0] = loadShader(getAssetPath() + "shaders/texture3d/texture3d.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
//        shaderStages[1] = loadShader(getAssetPath() + "shaders/texture3d/texture3d.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)
//
//        VkGraphicsPipelineCreateInfo pipelineCreateInfo =
//        vks::initializers::pipelineCreateInfo(
//                pipelineLayout,
//                renderPass,
//                0)
//
//        pipelineCreateInfo.pVertexInputState = & vertices . inputState
//                pipelineCreateInfo.pInputAssemblyState = & inputAssemblyState
//                pipelineCreateInfo.pRasterizationState = & rasterizationState
//                pipelineCreateInfo.pColorBlendState = & colorBlendState
//                pipelineCreateInfo.pMultisampleState = & multisampleState
//                pipelineCreateInfo.pViewportState = & viewportState
//                pipelineCreateInfo.pDepthStencilState = & depthStencilState
//                pipelineCreateInfo.pDynamicState = & dynamicState
//                pipelineCreateInfo.stageCount = static_cast<uint32_t>(shaderStages.size())
//        pipelineCreateInfo.pStages = shaderStages.data()
//
//        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . solid))
//    }
//
//    // Prepare and initialize uniform buffer containing shader uniforms
//    void prepareUniformBuffers()
//    {
//        // Vertex shader uniform buffer block
//        VK_CHECK_RESULT(vulkanDevice->createBuffer(
//        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
//        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
//        &uniformBufferVS,
//        sizeof(uboVS),
//        &uboVS))
//
//        updateUniformBuffers()
//    }
//
//    void updateUniformBuffers(bool viewchanged = true)
//    {
//        if (viewchanged) {
//            uboVS.projection = glm::perspective(glm::radians(60.0f), (float) width /(float) height, 0.001f, 256.0f)
//            glm::mat4 viewMatrix = glm ::translate(glm::mat4(1.0f), glm::vec3(0.0f, 0.0f, zoom))
//
//            uboVS.model = viewMatrix * glm::translate(glm::mat4(1.0f), cameraPos)
//            uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.x), glm::vec3(1.0f, 0.0f, 0.0f))
//            uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.y), glm::vec3(0.0f, 1.0f, 0.0f))
//            uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.z), glm::vec3(0.0f, 0.0f, 1.0f))
//
//            uboVS.viewPos = glm::vec4(0.0f, 0.0f, -zoom, 0.0f)
//        } else {
//            uboVS.depth += frameTimer * 0.15f
//            if (uboVS.depth > 1.0f)
//                uboVS.depth = uboVS.depth - 1.0f
//        }
//
//        VK_CHECK_RESULT(uniformBufferVS.map())
//        memcpy(uniformBufferVS.mapped, & uboVS, sizeof(uboVS))
//        uniformBufferVS.unmap()
//    }
//
//    void prepare()
//    {
//        VulkanExampleBase::prepare()
//        generateQuad()
//        setupVertexDescriptions()
//        prepareUniformBuffers()
//        prepareNoiseTexture(256, 256, 256)
//        setupDescriptorSetLayout()
//        preparePipelines()
//        setupDescriptorPool()
//        setupDescriptorSet()
//        buildCommandBuffers()
//        prepared = true
//    }
//
//    virtual void render()
//    {
//        if (!prepared)
//            return
//        draw()
//        if (regenerateNoise) {
//            updateNoiseTexture()
//        }
//        if (!paused)
//            updateUniformBuffers(false)
//    }
//
//    virtual void viewChanged()
//    {
//        updateUniformBuffers()
//    }
//
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
//}
//
//VULKAN_EXAMPLE_MAIN()
