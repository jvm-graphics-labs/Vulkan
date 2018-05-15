/*
* Vulkan Example - CPU based fire particle system
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.basics

import glm_.BYTES
import glm_.L
import glm_.buffer.adr
import glm_.buffer.bufferBig
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.size
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import org.lwjgl.system.MemoryUtil.NULL
import vkk.*
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.base.*
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin


private const val FLAME_RADIUS = 8f

private object particle {
    const val COUNT = 512
    const val SIZE = 10f

    enum class Type { Flame, Smoke }
}


fun main(args: Array<String>) {
    CpuParticleSystem().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class CpuParticleSystem : VulkanExampleBase() {

    object textures {
        object particles {
            val smoke = Texture2D()
            val fire = Texture2D()
            // Use a custom sampler to change sampler attributes required for rotating the uvs in the shader for alpha blended textures
            var sampler: VkSampler = NULL
        }

        object floor {
            val colorMap = Texture2D()
            val normalMap = Texture2D()
        }
    }

    // Vertex layout for the models
    val vertexLayout = VertexLayout(
            VertexComponent.POSITION,
            VertexComponent.UV,
            VertexComponent.NORMAL,
            VertexComponent.TANGENT,
            VertexComponent.BITANGENT)

    object models {
        val environment = Model()
    }

    val emitterPos = Vec3(0f, -FLAME_RADIUS + 2f, 0f)
    val minVel = Vec3(-3f, 0.5f, -3f)
    val maxVel = Vec3(3f, 7f, 3f)

    fun rnd(range: Float) = glm.linearRand(0f, range)

    private inner class Particle : Bufferizable() {
        val pos = Vec4()
        lateinit var color: Vec4
        var alpha = 0f
        var size_ = 0f
        var rotation = 0f
        var type = particle.Type.Flame
        // Attributes not used in shader
        lateinit var vel: Vec4
        var rotationSpeed = 0f

        fun init(emitterPos: Vec3) {

            vel = Vec4(0f, minVel.y + rnd(maxVel.y - minVel.y), 0f, 0f)
            alpha = rnd(0.75f)
            size_ = 1f + rnd(0.5f)
            color = Vec4(1f)
            type = particle.Type.Flame
            rotation = rnd(2f * glm.PIf)
            rotationSpeed = rnd(2f) - rnd(2f)

            // Get random sphere point
            val theta = rnd(2f * glm.PIf)
            val phi = rnd(glm.PIf) - glm.HPIf
            val r = rnd(FLAME_RADIUS)

            pos.x = r * cos(theta) * cos(phi)
            pos.y = r * sin(phi)
            pos.z = r * sin(theta) * cos(phi)

            pos plusAssign Vec4(emitterPos, 0f)
        }
    }

    val particleSize = Vec4.size * 3 + Float.BYTES * 4 + Int.BYTES

    object particlesData {
        var buffer: VkBuffer = NULL
        var memory: VkDeviceMemory = 0
        // Store the mapped address of the particle data for reuse
        var mappedMemory = NULL
        // Size of the particle buffer in bytes
        var size = 0
    }

    object uniformBuffers {
        val fire = Buffer()
        val environment = Buffer()
    }

    object uboVS {
        lateinit var projection: Mat4
        lateinit var model: Mat4
        lateinit var viewportDim: Vec2
        val pointSize = particle.SIZE
    }

    object uboEnv {
        lateinit var projection: Mat4
        lateinit var model: Mat4
        lateinit var normal: Mat4
        val lightPos = Vec4(0f)
        lateinit var cameraPos: Vec4
    }

    object pipelines {
        var particles: VkPipeline = NULL
        var environment: VkPipeline = NULL
    }

    var pipelineLayout: VkPipelineLayout = NULL
    var descriptorSetLayout: VkDescriptorSetLayout = NULL

    object descriptorSets {
        var particles: VkDescriptorSet = NULL
        var environment: VkDescriptorSet = NULL
    }

    lateinit var particleBuffer: ByteBuffer
    private val particles = ArrayList<Particle>()

    init {
        zoom = -75.0f
        rotation(-15f, 45f, 0f)
        title = "CPU based particle system"
        settings.overlay = true
        zoomSpeed *= 1.5f
        timerSpeed *= 8f
    }

    override fun destroy() {

        // Clean up used Vulkan resources
        // Note : Inherited destructor cleans up resources stored in base class

        textures.particles.smoke.destroy()
        textures.particles.fire.destroy()
        textures.floor.colorMap.destroy()
        textures.floor.normalMap.destroy()

        device.apply {

            destroyPipeline(pipelines.particles)
            destroyPipeline(pipelines.environment)

            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)

            unmapMemory(particlesData.memory)
            destroyBuffer(particlesData.buffer)
            freeMemory(particlesData.memory)

            uniformBuffers.environment.destroy()
            uniformBuffers.fire.destroy()

            models.environment.destroy()

            destroySampler(textures.particles.sampler)
        }
        super.destroy()
    }

    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2).also {
            it[0].color(defaultClearColor)
            it[1].depthStencil(1f, 0)
        }
        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@CpuParticleSystem.renderPass
            renderArea.offset(0)
            renderArea.extent(size)
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

                // Environment
                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSets.environment)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.environment)
                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, models.environment.vertices.buffer)
                bindIndexBuffer(models.environment.indices.buffer, 0, VkIndexType.UINT32)
                drawIndexed(models.environment.indexCount, 1, 0, 0, 0)

                // Particle system (no index buffer)
                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSets.particles)
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.particles)
                bindVertexBuffers(VERTEX_BUFFER_BIND_ID, particlesData.buffer)
                draw(particle.COUNT, 1, 0, 0)

                endRenderPass()

                end()
            }
        }
    }


    private fun Particle.transition() = when (type) {
        particle.Type.Flame -> {
            // Flame particles have a chance of turning into smoke
            if (rnd(1f) < 0.05f) {
                alpha = 0f
                color = Vec4(0.25f + rnd(0.25f))
                pos.x *= 0.5f
                pos.z *= 0.5f
                vel = Vec4(rnd(1f) - rnd(1f), (minVel.y * 2) + rnd(maxVel.y - minVel.y), rnd(1f) - rnd(1f), 0f)
                size_ = 1f + rnd(0.5f)
                rotationSpeed = rnd(1f) - rnd(1f)
                type = particle.Type.Smoke
            } else init(emitterPos)
        }
    // Respawn at end of life
        particle.Type.Smoke -> init(emitterPos)
    }

    fun prepareParticles() {

        particles += List(particle.COUNT) {
            Particle().apply {
                init(emitterPos)
                alpha = 1f - abs(pos.y) / (FLAME_RADIUS * 2f)
            }
        }

        particleBuffer = bufferBig(particleSize * particles.size)

        for (i in particles.indices)
            particles[i] to (particleBuffer.adr + particleSize * i)

        particlesData.size = particleBuffer.size

        vulkanDevice.createBuffer(
                VkBufferUsage.VERTEX_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                particlesData.size.L,
                particlesData::buffer,
                particlesData::memory,
                particleBuffer.adr)

        // Map the memory and store the pointer for reuse
        particlesData.mappedMemory = device.mapMemory(particlesData.memory, 0, particlesData.size.L)
    }

    void updateParticles()
    {
        float particleTimer = frameTimer * 0.45f
        for (auto& particle : particleBuffer)
        {
            switch(particle.type)
            {
                case PARTICLE_TYPE_FLAME :
                particle.pos.y -= particle.vel.y * particleTimer * 3.5f
                particle.alpha += particleTimer * 2.5f
                particle.size -= particleTimer * 0.5f
                break
                case PARTICLE_TYPE_SMOKE :
                particle.pos -= particle.vel * frameTimer * 1.0f
                particle.alpha += particleTimer * 1.25f
                particle.size += particleTimer * 0.125f
                particle.color -= particleTimer * 0.05f
                break
            }
            particle.rotation += particleTimer * particle.rotationSpeed
            // Transition particle state
            if (particle.alpha > 2.0f) {
                transitionParticle(& particle)
            }
        }
        size_t size = particleBuffer . size () * sizeof(Particle)
        memcpy(particles.mappedMemory, particleBuffer.data(), size)
    }

    void loadAssets()
    {
        // Textures
        std::string texFormatSuffix
                VkFormat texFormat
                // Get supported compressed texture format
                if (vulkanDevice->features.textureCompressionBC) {
        texFormatSuffix = "_bc3_unorm"
        texFormat = VK_FORMAT_BC3_UNORM_BLOCK
    }
        else if (vulkanDevice->features.textureCompressionASTC_LDR) {
        texFormatSuffix = "_astc_8x8_unorm"
        texFormat = VK_FORMAT_ASTC_8x8_UNORM_BLOCK
    }
        else if (vulkanDevice->features.textureCompressionETC2) {
        texFormatSuffix = "_etc2_unorm"
        texFormat = VK_FORMAT_ETC2_R8G8B8_UNORM_BLOCK
    }
        else {
        vks::tools::exitFatal("Device does not support any compressed texture format!", VK_ERROR_FEATURE_NOT_PRESENT)
    }

        // Particles
        textures.particles.smoke.loadFromFile(getAssetPath() + "textures/particle_smoke.ktx", VK_FORMAT_R8G8B8A8_UNORM, vulkanDevice, queue)
        textures.particles.fire.loadFromFile(getAssetPath() + "textures/particle_fire.ktx", VK_FORMAT_R8G8B8A8_UNORM, vulkanDevice, queue)

        // Floor
        textures.floor.colorMap.loadFromFile(getAssetPath() + "textures/fireplace_colormap" + texFormatSuffix + ".ktx", texFormat, vulkanDevice, queue)
        textures.floor.normalMap.loadFromFile(getAssetPath() + "textures/fireplace_normalmap" + texFormatSuffix + ".ktx", texFormat, vulkanDevice, queue)

        // Create a custom sampler to be used with the particle textures
        // Create sampler
        VkSamplerCreateInfo samplerCreateInfo = vks ::initializers::samplerCreateInfo()
        samplerCreateInfo.magFilter = VK_FILTER_LINEAR
        samplerCreateInfo.minFilter = VK_FILTER_LINEAR
        samplerCreateInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR
        // Different address mode
        samplerCreateInfo.addressModeU = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_BORDER
        samplerCreateInfo.addressModeV = samplerCreateInfo.addressModeU
        samplerCreateInfo.addressModeW = samplerCreateInfo.addressModeU
        samplerCreateInfo.mipLodBias = 0.0f
        samplerCreateInfo.compareOp = VK_COMPARE_OP_NEVER
        samplerCreateInfo.minLod = 0.0f
        // Both particle textures have the same number of mip maps
        samplerCreateInfo.maxLod = float(textures.particles.fire.mipLevels)
        // Enable anisotropic filtering
        samplerCreateInfo.maxAnisotropy = 8.0f
        samplerCreateInfo.anisotropyEnable = VK_TRUE
        // Use a different border color (than the normal texture loader) for additive blending
        samplerCreateInfo.borderColor = VK_BORDER_COLOR_FLOAT_TRANSPARENT_BLACK
        VK_CHECK_RESULT(vkCreateSampler(device, & samplerCreateInfo, nullptr, & textures . particles . sampler))

        models.environment.loadFromFile(getAssetPath() + "models/fireplace.obj", vertexLayout, 10.0f, vulkanDevice, queue)
    }

    void setupDescriptorPool()
    {
        // Example uses one ubo and one image sampler
        std::vector<VkDescriptorPoolSize> poolSizes =
        {
            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 2),
            vks::initializers::descriptorPoolSize(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 4)
        }

        VkDescriptorPoolCreateInfo descriptorPoolInfo =
        vks::initializers::descriptorPoolCreateInfo(
                poolSizes.size(),
                poolSizes.data(),
                2)

        VK_CHECK_RESULT(vkCreateDescriptorPool(device, & descriptorPoolInfo, nullptr, & descriptorPool))
    }

    void setupDescriptorSetLayout()
    {
        std::vector<VkDescriptorSetLayoutBinding> setLayoutBindings =
        {
            // Binding 0 : Vertex shader uniform buffer
            vks::initializers::descriptorSetLayoutBinding(
                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    VK_SHADER_STAGE_VERTEX_BIT,
                    0),
            // Binding 1 : Fragment shader image sampler
            vks::initializers::descriptorSetLayoutBinding(
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                    VK_SHADER_STAGE_FRAGMENT_BIT,
                    1),
            // Binding 1 : Fragment shader image sampler
            vks::initializers::descriptorSetLayoutBinding(
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                    VK_SHADER_STAGE_FRAGMENT_BIT,
                    2)
        }

        VkDescriptorSetLayoutCreateInfo descriptorLayout =
        vks::initializers::descriptorSetLayoutCreateInfo(
                setLayoutBindings.data(),
                setLayoutBindings.size())

        VK_CHECK_RESULT(vkCreateDescriptorSetLayout(device, & descriptorLayout, nullptr, & descriptorSetLayout))

        VkPipelineLayoutCreateInfo pPipelineLayoutCreateInfo =
        vks::initializers::pipelineLayoutCreateInfo(
                & descriptorSetLayout,
        1)

        VK_CHECK_RESULT(vkCreatePipelineLayout(device, & pPipelineLayoutCreateInfo, nullptr, & pipelineLayout))
    }

    void setupDescriptorSets()
    {
        std::vector<VkWriteDescriptorSet> writeDescriptorSets

                VkDescriptorSetAllocateInfo allocInfo =
        vks::initializers::descriptorSetAllocateInfo(
                descriptorPool,
                & descriptorSetLayout,
        1)

        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & descriptorSets . particles))

        // Image descriptor for the color map texture
        VkDescriptorImageInfo texDescriptorSmoke =
        vks::initializers::descriptorImageInfo(
                textures.particles.sampler,
                textures.particles.smoke.view,
                VK_IMAGE_LAYOUT_GENERAL)
        VkDescriptorImageInfo texDescriptorFire =
        vks::initializers::descriptorImageInfo(
                textures.particles.sampler,
                textures.particles.fire.view,
                VK_IMAGE_LAYOUT_GENERAL)

        writeDescriptorSets = {
            // Binding 0: Vertex shader uniform buffer
            vks::initializers::writeDescriptorSet(
                    descriptorSets.particles,
                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    0,
                    & uniformBuffers . fire . descriptor),
            // Binding 1: Smoke texture
            vks::initializers::writeDescriptorSet(
                    descriptorSets.particles,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                    1,
                    & texDescriptorSmoke),
            // Binding 1: Fire texture array
            vks::initializers::writeDescriptorSet(
                    descriptorSets.particles,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                    2,
                    & texDescriptorFire)
        }

        vkUpdateDescriptorSets(device, writeDescriptorSets.size(), writeDescriptorSets.data(), 0, NULL)

        // Environment
        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, & allocInfo, & descriptorSets . environment))

        writeDescriptorSets = {
            // Binding 0: Vertex shader uniform buffer
            vks::initializers::writeDescriptorSet(
                    descriptorSets.environment,
                    VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                    0,
                    & uniformBuffers . environment . descriptor),
            // Binding 1: Color map
            vks::initializers::writeDescriptorSet(
                    descriptorSets.environment,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                    1,
                    & textures . floor . colorMap . descriptor),
            // Binding 2: Normal map
            vks::initializers::writeDescriptorSet(
                    descriptorSets.environment,
                    VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                    2,
                    & textures . floor . normalMap . descriptor),
        }

        vkUpdateDescriptorSets(device, writeDescriptorSets.size(), writeDescriptorSets.data(), 0, NULL)
    }

    void preparePipelines()
    {
        VkPipelineInputAssemblyStateCreateInfo inputAssemblyState =
        vks::initializers::pipelineInputAssemblyStateCreateInfo(
                VK_PRIMITIVE_TOPOLOGY_POINT_LIST,
                0,
                VK_FALSE)

        VkPipelineRasterizationStateCreateInfo rasterizationState =
        vks::initializers::pipelineRasterizationStateCreateInfo(
                VK_POLYGON_MODE_FILL,
                VK_CULL_MODE_BACK_BIT,
                VK_FRONT_FACE_CLOCKWISE,
                0)

        VkPipelineColorBlendAttachmentState blendAttachmentState =
        vks::initializers::pipelineColorBlendAttachmentState(
                0xf,
                VK_FALSE)

        VkPipelineColorBlendStateCreateInfo colorBlendState =
        vks::initializers::pipelineColorBlendStateCreateInfo(
                1,
                & blendAttachmentState)

        VkPipelineDepthStencilStateCreateInfo depthStencilState =
        vks::initializers::pipelineDepthStencilStateCreateInfo(
                VK_TRUE,
                VK_TRUE,
                VK_COMPARE_OP_LESS_OR_EQUAL)

        VkPipelineViewportStateCreateInfo viewportState =
        vks::initializers::pipelineViewportStateCreateInfo(1, 1, 0)

        VkPipelineMultisampleStateCreateInfo multisampleState =
        vks::initializers::pipelineMultisampleStateCreateInfo(
                VK_SAMPLE_COUNT_1_BIT,
                0)

        std::vector<VkDynamicState> dynamicStateEnables = { VK_DYNAMIC_STATE_VIEWPORT,
                                                            VK_DYNAMIC_STATE_SCISSOR
        }
        VkPipelineDynamicStateCreateInfo dynamicState =
        vks::initializers::pipelineDynamicStateCreateInfo(
                dynamicStateEnables.data(),
                dynamicStateEnables.size(),
                0)

        // Load shaders
        std::array < VkPipelineShaderStageCreateInfo, 2> shaderStages

        VkGraphicsPipelineCreateInfo pipelineCreateInfo =
        vks::initializers::pipelineCreateInfo(
                pipelineLayout,
                renderPass,
                0)

        pipelineCreateInfo.pInputAssemblyState = & inputAssemblyState
                pipelineCreateInfo.pRasterizationState = & rasterizationState
                pipelineCreateInfo.pColorBlendState = & colorBlendState
                pipelineCreateInfo.pMultisampleState = & multisampleState
                pipelineCreateInfo.pViewportState = & viewportState
                pipelineCreateInfo.pDepthStencilState = & depthStencilState
                pipelineCreateInfo.pDynamicState = & dynamicState
                pipelineCreateInfo.stageCount = shaderStages.size()
        pipelineCreateInfo.pStages = shaderStages.data();

        // Particle rendering pipeline
        {
            // Shaders
            shaderStages[0] = loadShader(getAssetPath() + "shaders/particlefire/particle.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
            shaderStages[1] = loadShader(getAssetPath() + "shaders/particlefire/particle.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)

            // Vertex input state
            VkVertexInputBindingDescription vertexInputBinding =
            vks::initializers::vertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, sizeof(Particle), VK_VERTEX_INPUT_RATE_VERTEX)

            std::vector<VkVertexInputAttributeDescription> vertexInputAttributes = {
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 0, VK_FORMAT_R32G32B32A32_SFLOAT, offsetof(Particle, pos)),    // Location 0: Position
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 1, VK_FORMAT_R32G32B32A32_SFLOAT, offsetof(Particle, color)),    // Location 1: Color
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 2, VK_FORMAT_R32_SFLOAT, offsetof(Particle, alpha)),            // Location 2: Alpha
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 3, VK_FORMAT_R32_SFLOAT, offsetof(Particle, size)),            // Location 3: Size
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 4, VK_FORMAT_R32_SFLOAT, offsetof(Particle, rotation)),        // Location 4: Rotation
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 5, VK_FORMAT_R32_SINT, offsetof(Particle, type)),                // Location 5: Particle type
            }

            VkPipelineVertexInputStateCreateInfo vertexInputState = vks ::initializers::pipelineVertexInputStateCreateInfo()
            vertexInputState.vertexBindingDescriptionCount = 1
            vertexInputState.pVertexBindingDescriptions = & vertexInputBinding
                    vertexInputState.vertexAttributeDescriptionCount = static_cast<uint32_t>(vertexInputAttributes.size())
            vertexInputState.pVertexAttributeDescriptions = vertexInputAttributes.data()

            pipelineCreateInfo.pVertexInputState = & vertexInputState

                    // Dont' write to depth buffer
                    depthStencilState.depthWriteEnable = VK_FALSE

            // Premulitplied alpha
            blendAttachmentState.blendEnable = VK_TRUE
            blendAttachmentState.srcColorBlendFactor = VK_BLEND_FACTOR_ONE
            blendAttachmentState.dstColorBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA
            blendAttachmentState.colorBlendOp = VK_BLEND_OP_ADD
            blendAttachmentState.srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE
            blendAttachmentState.dstAlphaBlendFactor = VK_BLEND_FACTOR_ZERO
            blendAttachmentState.alphaBlendOp = VK_BLEND_OP_ADD
            blendAttachmentState.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT

            VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . particles))
        }

        // Environment rendering pipeline (normal mapped)
        {
            // Shaders
            shaderStages[0] = loadShader(getAssetPath() + "shaders/particlefire/normalmap.vert.spv", VK_SHADER_STAGE_VERTEX_BIT)
            shaderStages[1] = loadShader(getAssetPath() + "shaders/particlefire/normalmap.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT)

            // Vertex input state
            VkVertexInputBindingDescription vertexInputBinding =
            vks::initializers::vertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, vertexLayout.stride(), VK_VERTEX_INPUT_RATE_VERTEX)

            std::vector<VkVertexInputAttributeDescription> vertexInputAttributes = {
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 0, VK_FORMAT_R32G32B32_SFLOAT, 0),                            // Location 0: Position
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 1, VK_FORMAT_R32G32_SFLOAT, sizeof(float) * 3),                // Location 1: UV
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 2, VK_FORMAT_R32G32B32_SFLOAT, sizeof(float) * 5),            // Location 2: Normal
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 3, VK_FORMAT_R32G32B32_SFLOAT, sizeof(float) * 8),            // Location 3: Tangent
                vks::initializers::vertexInputAttributeDescription(VERTEX_BUFFER_BIND_ID, 4, VK_FORMAT_R32G32B32_SFLOAT, sizeof(float) * 11),            // Location 4: Bitangen
            }

            VkPipelineVertexInputStateCreateInfo vertexInputState = vks ::initializers::pipelineVertexInputStateCreateInfo()
            vertexInputState.vertexBindingDescriptionCount = 1
            vertexInputState.pVertexBindingDescriptions = & vertexInputBinding
                    vertexInputState.vertexAttributeDescriptionCount = static_cast<uint32_t>(vertexInputAttributes.size())
            vertexInputState.pVertexAttributeDescriptions = vertexInputAttributes.data()

            pipelineCreateInfo.pVertexInputState = & vertexInputState

                    blendAttachmentState.blendEnable = VK_FALSE
            depthStencilState.depthWriteEnable = VK_TRUE
            inputAssemblyState.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST

            VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, 1, & pipelineCreateInfo, nullptr, & pipelines . environment))
        }
    }

    // Prepare and initialize uniform buffer containing shader uniforms
    void prepareUniformBuffers()
    {
        // Vertex shader uniform buffer block
        VK_CHECK_RESULT(vulkanDevice->createBuffer(
        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        &uniformBuffers.fire,
        sizeof(uboVS)))

        // Vertex shader uniform buffer block
        VK_CHECK_RESULT(vulkanDevice->createBuffer(
        VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        &uniformBuffers.environment,
        sizeof(uboEnv)))

        // Map persistent
        VK_CHECK_RESULT(uniformBuffers.fire.map())
        VK_CHECK_RESULT(uniformBuffers.environment.map())

        updateUniformBuffers()
    }

    void updateUniformBufferLight()
    {
        // Environment
        uboEnv.lightPos.x = sin(timer * 2.0f * float(M_PI)) * 1.5f
        uboEnv.lightPos.y = 0.0f
        uboEnv.lightPos.z = cos(timer * 2.0f * float(M_PI)) * 1.5f
        memcpy(uniformBuffers.environment.mapped, & uboEnv, sizeof(uboEnv))
    }

    void updateUniformBuffers()
    {
        // Vertex shader
        glm::mat4 viewMatrix = glm ::mat4(1.0f)
        uboVS.projection = glm::perspective(glm::radians(60.0f), (float) width /(float) height, 0.001f, 256.0f)
        viewMatrix = glm::translate(viewMatrix, glm::vec3(0.0f, 0.0f, zoom))

        uboVS.model = glm::mat4(1.0f)
        uboVS.model = viewMatrix * glm::translate(uboVS.model, glm::vec3(0.0f, 15.0f, 0.0f))
        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.x), glm::vec3(1.0f, 0.0f, 0.0f))
        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.y), glm::vec3(0.0f, 1.0f, 0.0f))
        uboVS.model = glm::rotate(uboVS.model, glm::radians(rotation.z), glm::vec3(0.0f, 0.0f, 1.0f))

        uboVS.viewportDim = glm::vec2((float) width, (float) height)
        memcpy(uniformBuffers.fire.mapped, & uboVS, sizeof(uboVS))

        // Environment
        uboEnv.projection = uboVS.projection
        uboEnv.model = uboVS.model
        uboEnv.normal = glm::inverseTranspose(uboEnv.model)
        uboEnv.cameraPos = glm::vec4(0.0, 0.0, zoom, 0.0)
        memcpy(uniformBuffers.environment.mapped, & uboEnv, sizeof(uboEnv))
    }

    void draw()
    {
        VulkanExampleBase::prepareFrame()

        // Command buffer to be sumitted to the queue
        submitInfo.commandBufferCount = 1
        submitInfo.pCommandBuffers = & drawCmdBuffers [currentBuffer]

        // Submit to queue
        VK_CHECK_RESULT(vkQueueSubmit(queue, 1, & submitInfo, VK_NULL_HANDLE))

        VulkanExampleBase::submitFrame()
    }

    void prepare()
    {
        VulkanExampleBase::prepare()
        loadAssets()
        prepareParticles()
        prepareUniformBuffers()
        setupDescriptorSetLayout()
        preparePipelines()
        setupDescriptorPool()
        setupDescriptorSets()
        buildCommandBuffers()
        prepared = true
    }

    virtual void render()
    {
        if (!prepared)
            return
        draw()
        if (!paused) {
            updateUniformBufferLight()
            updateParticles()
        }
    }

    virtual void viewChanged()
    {
        updateUniformBuffers()
    }
}

VULKAN_EXAMPLE_MAIN()