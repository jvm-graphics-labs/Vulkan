/*
* Vulkan Example - CPU based fire particle system
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.basics

import glm_.*
import glm_.buffer.adr
import glm_.buffer.bufferBig
import glm_.func.rad
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memCopy
import vkk.*
import vulkan.VERTEX_BUFFER_BIND_ID
import vulkan.assetPath
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

    object uboVS : Bufferizable() {
        lateinit var projection: Mat4
        @Order(1)
        lateinit var model: Mat4
        lateinit var viewportDim: Vec2
        @Order(3)
        val pointSize = particle.SIZE
    }

    object uboEnv : Bufferizable() {
        lateinit var projection: Mat4
        lateinit var model: Mat4
        lateinit var normal: Mat4
        val lightPos = Vec4(0f)
        lateinit var cameraPos: Vec4
        override var fieldOrder = arrayOf("projection", "model", "normal", "lightPos", "cameraPos")
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
        settings.overlay = false // TODO
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

    fun updateParticles() {

        val particleTimer = frameTimer * 0.45f
        for (p in particles) {
            when (p.type) {
                particle.Type.Flame -> {
                    p.pos.y -= p.vel.y * particleTimer * 3.5f
                    p.alpha += particleTimer * 2.5f
                    p.size_ -= particleTimer * 0.5f
                }
                particle.Type.Smoke -> {
                    p.pos -= p.vel * frameTimer * 1.0f
                    p.alpha += particleTimer * 1.25f
                    p.size_ += particleTimer * 0.125f
                    p.color minusAssign particleTimer * 0.05f
                }
            }
            p.rotation += particleTimer * p.rotationSpeed
            // Transition particle state
            if (p.alpha > 2f)
                p.transition()
        }

        memCopy(particleBuffer.adr, particlesData.mappedMemory, particleBuffer.size.L)
    }

    fun loadAssets() {
        // Textures
        val (texFormatSuffix, texFormat) = vulkanDevice.features.run {
            when {
            // Get supported compressed texture format
                textureCompressionBC -> "_bc3_unorm" to VkFormat.BC3_UNORM_BLOCK
                textureCompressionASTC_LDR -> "_astc_8x8_unorm" to VkFormat.ASTC_8x8_UNORM_BLOCK
                textureCompressionETC2 -> "_etc2_unorm" to VkFormat.ETC2_R8G8B8_UNORM_BLOCK
                else -> tools.exitFatal("Device does not support any compressed texture format!", VkResult.ERROR_FEATURE_NOT_PRESENT)
            }
        }

        // Particles
        textures.particles.smoke.loadFromFile("$assetPath/textures/particle_smoke.ktx", VkFormat.R8G8B8A8_UNORM, vulkanDevice, queue)
        textures.particles.fire.loadFromFile("$assetPath/textures/particle_fire.ktx", VkFormat.R8G8B8A8_UNORM, vulkanDevice, queue)

        // Floor
        textures.floor.colorMap.loadFromFile("$assetPath/textures/fireplace_colormap$texFormatSuffix.ktx", texFormat, vulkanDevice, queue)
        textures.floor.normalMap.loadFromFile("$assetPath/textures/fireplace_normalmap$texFormatSuffix.ktx", texFormat, vulkanDevice, queue)

        // Create a custom sampler to be used with the particle textures
        // Create sampler
        val samplerCreateInfo = vk.SamplerCreateInfo {
            magFilter = VkFilter.LINEAR
            minFilter = VkFilter.LINEAR
            mipmapMode = VkSamplerMipmapMode.LINEAR
            // Different address mode
            addressMode = VkSamplerAddressMode.CLAMP_TO_BORDER
            mipLodBias = 0f
            compareOp = VkCompareOp.NEVER
            minLod = 0f
            // Both particle textures have the same number of mip maps
            maxLod = textures.particles.fire.mipLevels.f
            // Enable anisotropic filtering
            maxAnisotropy = 8f
            anisotropyEnable = true
            // Use a different border color (than the normal texture loader) for additive blending
            borderColor = VkBorderColor.FLOAT_TRANSPARENT_BLACK
        }
        textures.particles.sampler = device createSampler samplerCreateInfo

        models.environment.loadFromFile("$assetPath/models/fireplace.obj", vertexLayout, 10f, vulkanDevice, queue)
    }

    fun setupDescriptorPool() {
        // Example uses one ubo and one image sampler
        val poolSizes = vk.DescriptorPoolSize(
                VkDescriptorType.UNIFORM_BUFFER, 2,
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, 4)

        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(poolSizes, 2)

        descriptorPool = device createDescriptorPool descriptorPoolInfo
    }

    fun setupDescriptorSetLayout() {

        val setLayoutBindings = vk.DescriptorSetLayoutBinding(
                // Binding 0 : Vertex shader uniform buffer
                VkDescriptorType.UNIFORM_BUFFER, VkShaderStage.VERTEX_BIT.i, 0,
                // Binding 1 : Fragment shader image sampler
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 1,
                // Binding 1 : Fragment shader image sampler
                VkDescriptorType.COMBINED_IMAGE_SAMPLER, VkShaderStage.FRAGMENT_BIT.i, 2)

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(setLayoutBindings)

        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)

        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSets() {

        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)

        descriptorSets.particles = device allocateDescriptorSets allocInfo

        // Image descriptor for the color map texture
        val texDescriptorSmoke = vk.DescriptorImageInfo(
                textures.particles.sampler,
                textures.particles.smoke.view,
                VkImageLayout.GENERAL)
        val texDescriptorFire = vk.DescriptorImageInfo(
                textures.particles.sampler,
                textures.particles.fire.view,
                VkImageLayout.GENERAL)

        var writeDescriptorSets = vk.WriteDescriptorSet(
                // Binding 0: Vertex shader uniform buffer
                descriptorSets.particles, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.fire.descriptor,
                // Binding 1: Smoke texture
                descriptorSets.particles, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, texDescriptorSmoke,
                // Binding 1: Fire texture array
                descriptorSets.particles, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 2, texDescriptorFire)

        device updateDescriptorSets writeDescriptorSets

        // Environment
        descriptorSets.environment = device allocateDescriptorSets allocInfo

        writeDescriptorSets = vk.WriteDescriptorSet(
                // Binding 0: Vertex shader uniform buffer
                descriptorSets.environment, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBuffers.environment.descriptor,
                // Binding 1: Color map
                descriptorSets.environment, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 1, textures.floor.colorMap.descriptor,
                // Binding 2: Normal map
                descriptorSets.environment, VkDescriptorType.COMBINED_IMAGE_SAMPLER, 2, textures.floor.normalMap.descriptor)

        device updateDescriptorSets writeDescriptorSets
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.POINT_LIST, 0, false)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.BACK_BIT.i, VkFrontFace.CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        // Load shaders
        val shaderStages = vk.PipelineShaderStageCreateInfo(2)

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
        }
        // Particle rendering pipeline
        run {
            // Shaders
            shaderStages[0].loadShader("$assetPath/shaders/particlefire/particle.vert.spv", VkShaderStage.VERTEX_BIT)
            shaderStages[1].loadShader("$assetPath/shaders/particlefire/particle.frag.spv", VkShaderStage.FRAGMENT_BIT)

            // Vertex input state
            val vertexInputBinding = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, particleSize, VkVertexInputRate.VERTEX)

            val vertexInputAttributes = vk.VertexInputAttributeDescription(
                    VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32A32_SFLOAT, 0,                      // Location 0: Position
                    VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32B32A32_SFLOAT, Vec4.size,              // Location 1: Color
                    VERTEX_BUFFER_BIND_ID, 2, VkFormat.R32_SFLOAT, Vec4.size * 2,                   // Location 2: Alpha
                    VERTEX_BUFFER_BIND_ID, 3, VkFormat.R32_SFLOAT, Vec4.size * 2 + Float.BYTES,     // Location 3: Size
                    VERTEX_BUFFER_BIND_ID, 4, VkFormat.R32_SFLOAT, Vec4.size * 2 + Float.BYTES * 2, // Location 4: Rotation
                    VERTEX_BUFFER_BIND_ID, 5, VkFormat.R32_SINT, Vec4.size * 2 + Float.BYTES * 3)   // Location 5: Particle type

            val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
                vertexBindingDescription = vertexInputBinding
                vertexAttributeDescriptions = vertexInputAttributes
            }
            pipelineCreateInfo.vertexInputState = vertexInputState

            // Dont' write to depth buffer
            depthStencilState.depthWriteEnable = false

            // Premulitplied alpha
            blendAttachmentState.apply {
                blendEnable = true
                srcColorBlendFactor = VkBlendFactor.ONE
                dstColorBlendFactor = VkBlendFactor.ONE_MINUS_SRC_ALPHA
                colorBlendOp = VkBlendOp.ADD
                srcAlphaBlendFactor = VkBlendFactor.ONE
                dstAlphaBlendFactor = VkBlendFactor.ZERO
                alphaBlendOp = VkBlendOp.ADD
                colorWriteMask = VkColorComponent.R_BIT or VkColorComponent.G_BIT or VkColorComponent.B_BIT or VkColorComponent.A_BIT
            }
            pipelines.particles = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
        }

        // Environment rendering pipeline (normal mapped)
        run {
            // Shaders
            shaderStages[0].loadShader("$assetPath/shaders/particlefire/normalmap.vert.spv", VkShaderStage.VERTEX_BIT)
            shaderStages[1].loadShader("$assetPath/shaders/particlefire/normalmap.frag.spv", VkShaderStage.FRAGMENT_BIT)

            // Vertex input state
            val vertexInputBinding = vk.VertexInputBindingDescription(VERTEX_BUFFER_BIND_ID, vertexLayout.stride, VkVertexInputRate.VERTEX)

            val vertexInputAttributes = vk.VertexInputAttributeDescription(
                    VERTEX_BUFFER_BIND_ID, 0, VkFormat.R32G32B32_SFLOAT, 0,                         // Location 0: Position
                    VERTEX_BUFFER_BIND_ID, 1, VkFormat.R32G32_SFLOAT, Vec3.size,                            // Location 1: UV
                    VERTEX_BUFFER_BIND_ID, 2, VkFormat.R32G32B32_SFLOAT, Vec3.size + Vec2.size,     // Location 2: Normal
                    VERTEX_BUFFER_BIND_ID, 3, VkFormat.R32G32B32_SFLOAT, Vec3.size * 2 + Vec2.size, // Location 3: Tangent
                    VERTEX_BUFFER_BIND_ID, 4, VkFormat.R32G32B32_SFLOAT, Vec3.size * 3 + Vec2.size) // Location 4: Bitangen

            val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
                vertexBindingDescription = vertexInputBinding
                vertexAttributeDescriptions = vertexInputAttributes
            }
            pipelineCreateInfo.vertexInputState = vertexInputState

            blendAttachmentState.blendEnable = false
            depthStencilState.depthWriteEnable = true
            inputAssemblyState.topology = VkPrimitiveTopology.TRIANGLE_LIST

            pipelines.environment = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)
        }
    }

    /** Prepare and initialize uniform buffer containing shader uniforms */
    fun prepareUniformBuffers() {
        // Vertex shader uniform buffer block
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBuffers.fire,
                uboVS.size.L)

        // Vertex shader uniform buffer block
        vulkanDevice.createBuffer(
                VkBufferUsage.UNIFORM_BUFFER_BIT.i,
                VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT,
                uniformBuffers.environment,
                uboEnv.size.L)

        // Map persistent
        uniformBuffers.fire.map()
        uniformBuffers.environment.map()

        updateUniformBuffers()
    }

    fun updateUniformBufferLight()    {
        // Environment
        uboEnv.lightPos.x = sin(timer * 2f * glm.PIf) * 1.5f
        uboEnv.lightPos.y = 0f
        uboEnv.lightPos.z = cos(timer * 2f * glm.PIf) * 1.5f
        uboEnv to uniformBuffers.environment.mapped
    }

    fun updateUniformBuffers()    {
        // Vertex shader
        uboVS.projection = glm.perspective(60f.rad, size.aspect, 0.001f, 256f)
        val viewMatrix = glm.translate(Mat4(), 0f, 0f, zoom)

        uboVS.model = viewMatrix * glm.translate(uboVS.model, 0f, 15f, 0f)
                .rotateAssign(rotation.x.rad, 1f, 0f, 0f)
                .rotateAssign(rotation.y.rad, 0f, 1f, 0f)
                .rotateAssign(rotation.z.rad, 0f, 0f, 1f)

        uboVS.viewportDim put size
        uboVS to uniformBuffers.fire.mapped

        // Environment
        uboEnv.projection = uboVS.projection
        uboEnv.model = uboVS.model
//        uboEnv.normal = glm.inverseTranspose(uboEnv.model)
        uboEnv.normal = uboEnv.model.inverse().transposeAssign()
        uboEnv.cameraPos = Vec4(0f, 0f, zoom, 0f)
        uboEnv to uniformBuffers.environment.mapped
    }

    fun draw()    {

        super.prepareFrame()

        // Command buffer to be sumitted to the queue
        submitInfo.commandBuffer = drawCmdBuffers [currentBuffer]

        // Submit to queue
        queue submit submitInfo

        super.submitFrame()
    }

    override fun prepare()    {
        super.prepare()
        loadAssets()
        prepareParticles()
        prepareUniformBuffers()
        setupDescriptorSetLayout()
        preparePipelines()
        setupDescriptorPool()
        setupDescriptorSets()
        buildCommandBuffers()
        prepared = true
        window.show()
    }

    override fun render()    {
        if (!prepared)
            return
        draw()
        if (!paused) {
            updateUniformBufferLight()
            updateParticles()
        }
    }

    override fun viewChanged() = updateUniformBuffers()
}