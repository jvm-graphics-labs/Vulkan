package vulkan.performance

import glm_.*
import glm_.func.cos
import glm_.func.rad
import glm_.func.sin
import glm_.mat4x4.Mat4
import glm_.vec3.Vec3
import kool.stak
import kotlinx.coroutines.launch
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memGetAddress
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.nvkWaitForFences
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferInheritanceInfo
import vkk.*
import vulkan.assetPath
import vulkan.base.*
import vulkan.reset
import java.nio.ByteBuffer
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.acos

fun main(args: Array<String>) {
    Coroutines().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class Coroutines : VulkanExampleBase() {

    var displaySkybox = true

    // Vertex layout for the models
    val vertexLayout = VertexLayout(
            VertexComponent.POSITION,
            VertexComponent.NORMAL,
            VertexComponent.COLOR)

    private object models {
        val ufo = Model()
        val skysphere = Model()
    }

    // Shared matrices used for thread push constant blocks
    private object matrices {
        var projection = Mat4()
        var view = Mat4()
    }

    private object pipelines {
        var phong = VkPipeline(NULL)
        var starsphere = VkPipeline(NULL)
    }

    var pipelineLayout = VkPipelineLayout(NULL)

    lateinit var primaryCommandBuffer: VkCommandBuffer

    // Secondary scene command buffers used to store backgdrop and user interface
    private object secondaryCommandBuffers {
        lateinit var background: VkCommandBuffer
        lateinit var ui: VkCommandBuffer
    }

    // Number of animated objects to be renderer by using threads and secondary command buffers
    val numObjectsPerThread: Int

    // Multi threaded stuff

    // Max. number of concurrent threads
    val numThreads = Runtime.getRuntime().availableProcessors()

    init {
        // Get number of max. concurrrent threads
        assert(numThreads > 0)
        println("numThreads = $numThreads")
        numObjectsPerThread = 512 / numThreads
    }

    // Use push constants to update shader parameters on a per-thread base
    class ThreadPushConstantBlock {
        var mvp = Mat4()
        var color = Vec3()

        fun toBuf(): ByteBuffer {
            val buf = stackGet().malloc(size)
            mvp to buf
            color to Vec3()
            return buf
        }

        companion object {
            val size = Mat4.size + Vec3.size
        }
    }

    class ObjectData {
        var model = Mat4()
        var pos = Vec3()
        var rotation = Vec3()
        var rotationDir = 0f
        var rotationSpeed = 0f
        var scale = 0f
        var deltaT = 0f
        var stateT = 0f
        var visible = true
    }

    class ThreadData {
        var commandPool = VkCommandPool(NULL)
        // One command buffer per render object
        lateinit var commandBuffer: Array<VkCommandBuffer>
        // One push constant block per render object
        lateinit var pushConstBlock: Array<ThreadPushConstantBlock>
        // Per object information (position, rotation, etc.)
        lateinit var objectData: Array<ObjectData>
    }

    val threadData = Array(numThreads) { ThreadData() }

    // Fence to wait for all command buffers to finish before presenting to the swap chain
    var renderFence = VkFence(NULL)

    // Max. dimension of the ufo mesh for use as the sphere radius for frustum culling
    var objectSphereDim = 0f

    // View frustum for culling invisible objects
    val frustum = Frustum()

    init {
        zoom = -32.5f
        zoomSpeed = 2.5f
        rotationSpeed = 0.5f
        rotation.put(0f, 37.5f, 0f)
        title = "Multi threaded command buffer"
//        settings.overlay = true
    }

    override fun destroy() {

        // Clean up used Vulkan resources
        device.apply {

            destroyPipeline(pipelines.phong)
            destroyPipeline(pipelines.starsphere)

            destroyPipelineLayout(pipelineLayout)

            models.ufo.destroy()
            models.skysphere.destroy()

            for (thread in threadData) {
                freeCommandBuffers(thread.commandPool, thread.commandBuffer)
                destroyCommandPool(thread.commandPool)
            }

            destroyFence(renderFence)
        }

        // Note : Inherited destructor cleans up resources stored in base class
        super.destroy()
    }

    fun rnd(range: Float): Float = glm.linearRand(0f, range)

    /** Create all threads and initialize shader push constants */
    fun prepareMultiThreadedRenderer() {

        /*  Since this demo updates the command buffers on each frame we don't use the per-framebuffer command buffers
            from the base class, and create a single primary command buffer instead */
        val cmdBufAllocateInfo = vk.CommandBufferAllocateInfo(cmdPool, VkCommandBufferLevel.PRIMARY, 1)
        primaryCommandBuffer = device allocateCommandBuffer cmdBufAllocateInfo

        // Create additional secondary CBs for background and ui
        cmdBufAllocateInfo.level = VkCommandBufferLevel.SECONDARY
        secondaryCommandBuffers.background = device allocateCommandBuffer cmdBufAllocateInfo
        secondaryCommandBuffers.ui = device allocateCommandBuffer cmdBufAllocateInfo

//        val maxX = glm.floor(sqrt(numThreads * numObjectsPerThread.d))
//        var posX = 0
//        var posZ = 0

        for (thread in threadData) {

            // Create one command pool for each thread
            val cmdPoolInfo = vk.CommandPoolCreateInfo {
                queueFamilyIndex = swapChain.queueNodeIndex
                flags = VkCommandPoolCreate.RESET_COMMAND_BUFFER_BIT.i
            }
            thread.commandPool = device createCommandPool cmdPoolInfo

            // Generate secondary command buffers for each thread
            val secondaryCmdBufAllocateInfo = vk.CommandBufferAllocateInfo(thread.commandPool, VkCommandBufferLevel.SECONDARY, numObjectsPerThread)
            // One secondary command buffer per object that is updated by this thread
            thread.commandBuffer = stak {
                val count = secondaryCmdBufAllocateInfo.commandBufferCount
                val pCommandBuffer = it.nmalloc(Pointer.POINTER_SIZE, Pointer.POINTER_SIZE * count)
                VK_CHECK_RESULT(VK10.nvkAllocateCommandBuffers(device, secondaryCmdBufAllocateInfo.adr, pCommandBuffer))
                Array(count) { VkCommandBuffer(memGetAddress(pCommandBuffer + Pointer.POINTER_SIZE * it), device) }
            }

            thread.pushConstBlock = Array(numObjectsPerThread) { ThreadPushConstantBlock() }
            thread.objectData = Array(numObjectsPerThread) { ObjectData() }

            for (j in 0 until numObjectsPerThread) {

                val theta = 2f * glm.PIf * rnd(1f)
                val phi = acos(1f - 2f * rnd(1f))

                thread.objectData[j].apply {

                    pos = Vec3(phi.sin * theta.cos, 0f, phi.cos) * 35f

                    rotation = Vec3(0f, rnd(360f), 0f)
                    deltaT = rnd(1f)
                    rotationDir = if (rnd(100f) < 50f) 1f else -1f
                    rotationSpeed = (2f + rnd(4f)) * rotationDir
                    scale = 0.75f + rnd(0.5f)
                }
                thread.pushConstBlock[j].color = Vec3 { rnd(1f) }
            }
        }
    }

    /** Builds the secondary command buffer for each thread */
    fun threadRenderCode(threadIndex: Int, cmdBufferIndex: Int, inheritanceInfo: VkCommandBufferInheritanceInfo) {

        val thread = threadData[threadIndex]
        val objectData = thread.objectData[cmdBufferIndex]

        // Check visibility against view frustum
        objectData.visible = frustum.checkSphere(objectData.pos, objectSphereDim * 0.5f)

        if (!objectData.visible) return

        val commandBufferBeginInfo = vk.CommandBufferBeginInfo {
            flags = VkCommandBufferUsage.RENDER_PASS_CONTINUE_BIT.i
            this.inheritanceInfo = inheritanceInfo
        }

        thread.commandBuffer[cmdBufferIndex].record(commandBufferBeginInfo) {

            setViewport(size)
            setScissor(size)

            bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.phong)

            objectData.apply {
                // Update
                if (!paused) {
                    rotation.y += 2.5f * rotationSpeed * frameTimer
                    if (rotation.y > 360f)
                        rotation.y -= 360f
                    deltaT += 0.15f * frameTimer
                    if (deltaT > 1f)
                        deltaT -= 1f
                    pos.y = (deltaT * 360f).rad.sin * 2.5f
                }

                model = Mat4(1f).translate(pos)
                        .rotate(-(deltaT * 360f).rad.sin * 0.25f, rotationDir, 0f, 0f)
                        .rotate(rotation.y.rad, 0f, rotationDir, 0f)
                        .rotate((deltaT * 360f).rad, 0f, rotationDir, 0f)
                        .scale(scale)
            }

            thread.pushConstBlock[cmdBufferIndex].mvp = matrices.projection * matrices.view * objectData.model

            // Update shader push constant block
            // Contains model view matrix
            pushConstants(
                    pipelineLayout,
                    VkShaderStage.VERTEX_BIT.i,
                    0,
                    thread.pushConstBlock[cmdBufferIndex].toBuf())

            bindVertexBuffers(models.ufo.vertices.buffer)
            bindIndexBuffer(models.ufo.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
            drawIndexed(models.ufo.indexCount, 1, 0, 0, 0)
        }
        stackGet().reset()
    }

    fun updateSecondaryCommandBuffers(inheritanceInfo: VkCommandBufferInheritanceInfo) {

        // Secondary command buffer for the sky sphere
        val commandBufferBeginInfo = vk.CommandBufferBeginInfo {
            flags = VkCommandBufferUsage.RENDER_PASS_CONTINUE_BIT.i
            this.inheritanceInfo = inheritanceInfo
        }
        val viewport = vk.Viewport(size)
        val scissor = vk.Rect2D(size)

        /*
            Background
        */

        secondaryCommandBuffers.background.record(commandBufferBeginInfo) {

            setViewport(viewport)
            setScissor(scissor)

            bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.starsphere)

            val view = Mat4(1f).rotateXYZ(rotation.x.rad, rotation.y.rad, rotation.z.rad)

            val mvp = matrices.projection * view

            pushConstants(
                    pipelineLayout,
                    VkShaderStage.VERTEX_BIT.i,
                    0,
                    mvp.toBuffer(stackGet()))

            bindVertexBuffers(models.skysphere.vertices.buffer)
            bindIndexBuffer(models.skysphere.indices.buffer, VkDeviceSize(0), VkIndexType.UINT32)
            drawIndexed(models.skysphere.indexCount, 1, 0, 0, 0)
        }

        /*
            User interface

            With VK_SUBPASS_CONTENTS_SECONDARY_COMMAND_BUFFERS, the primary command buffer's content has to be defined
            by secondary command buffers, which also applies to the UI overlay command buffer
        */

        secondaryCommandBuffers.ui.record(commandBufferBeginInfo) {

            setViewport(viewport)
            setScissor(scissor)

            bindPipeline(VkPipelineBindPoint.GRAPHICS, pipelines.starsphere)

            if (settings.overlay)
                drawUI()
        }
    }

    /** Updates the secondary command buffers using a thread pool and puts them into the primary command buffer
     *  that's lat submitted to the queue for rendering */
    fun updateCommandBuffers(frameBuffer: VkFramebuffer) {

        // Contains the list of secondary command buffers to be submitted
        val commandBuffers = ArrayList<VkCommandBuffer>()

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        val clearValues = vk.ClearValue(2).also {
            it[0].color(defaultClearColor)
            it[1].depthStencil(1f, 0)
        }
        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@Coroutines.renderPass
            renderArea.apply {
                offset(0)
                extent(size)
            }
            this.clearValues = clearValues
            framebuffer = frameBuffer
        }
        // Set target frame buffer

        primaryCommandBuffer.record(cmdBufInfo) {

            // The primary command buffer does not contain any rendering commands
            // These are stored (and retrieved) from the secondary command buffers
            beginRenderPass(renderPassBeginInfo, VkSubpassContents.SECONDARY_COMMAND_BUFFERS)

            // Inheritance info for the secondary command buffers
            val inheritanceInfo = vk.CommandBufferInheritanceInfo {
                renderPass = this@Coroutines.renderPass
                // Secondary command buffer also use the currently active framebuffer
                this.framebuffer = frameBuffer
            }
            // Update secondary scene command buffers
            updateSecondaryCommandBuffers(inheritanceInfo)

            if (displaySkybox)
                commandBuffers += secondaryCommandBuffers.background

            // Add a job to the thread's queue for each object to be rendered
            for (t in 0 until numThreads)
//                launch {
//                    for (i in 0 until numObjectsPerThread)
//                        threadRenderCode(t, i, inheritanceInfo)
//                }

            // Only submit if object is within the current view frustum
            for (t in 0 until numThreads)
                for (i in 0 until numObjectsPerThread)
                    if (threadData[t].objectData[i].visible)
                        commandBuffers += threadData[t].commandBuffer[i]

            // Render ui last
            if (uiOverlay.visible)
                commandBuffers += secondaryCommandBuffers.ui

            // Execute render commands from the secondary command buffer
            executeCommands(commandBuffers)

            endRenderPass()
        }
    }

    fun loadAssets() {
        models.ufo.loadFromFile("$assetPath/models/retroufo_red_lowpoly.dae", vertexLayout, 0.12f, vulkanDevice, queue)
        models.skysphere.loadFromFile("$assetPath/models/sphere.obj", vertexLayout, 1f, vulkanDevice, queue)
        objectSphereDim = (models.ufo.dim.size.x max models.ufo.dim.size.y) max models.ufo.dim.size.z
    }

    fun setupPipelineLayout() {

        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo()

        // Push constants for model matrices
        val pushConstantRange = vk.PushConstantRange(VkShaderStage.VERTEX_BIT, ThreadPushConstantBlock.size)

        // Push constant ranges are part of the pipeline layout
        pipelineLayoutCreateInfo.pushConstantRange = pushConstantRange

        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun preparePipelines() {

        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST)

        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.BACK_BIT.i, VkFrontFace.CLOCKWISE)

        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)

        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1, 0)

        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        val shaderStages = vk.PipelineShaderStageCreateInfo(2)

        val pipelineCI = vk.GraphicsPipelineCreateInfo(pipelineLayout, renderPass).also {
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.dynamicState = dynamicState
            it.stages = shaderStages
        }
        // Vertex bindings and attributes
        val vertexInputBindings = vk.VertexInputBindingDescription(0, vertexLayout.stride, VkVertexInputRate.VERTEX)

        val vertexInputAttributes = vk.VertexInputAttributeDescription(
                0, 0, VkFormat.R32G32B32_SFLOAT, 0,             // Location 0: Position
                0, 1, VkFormat.R32G32B32_SFLOAT, Vec3.size,             // Location 1: Normal
                0, 2, VkFormat.R32G32B32_SFLOAT, Vec3.size * 2) // Location 2: Color

        val vertexInputStateCI = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertexInputBindings
            vertexAttributeDescriptions = vertexInputAttributes
        }
        pipelineCI.vertexInputState = vertexInputStateCI

        // Object rendering pipeline
        shaderStages[0].loadShader("$assetPath/shaders/multithreading/phong.vert.spv", VkShaderStage.VERTEX_BIT)
        shaderStages[1].loadShader("$assetPath/shaders/multithreading/phong.frag.spv", VkShaderStage.FRAGMENT_BIT)
        pipelines.phong = device.createGraphicsPipelines(pipelineCache, pipelineCI)

        // Star sphere rendering pipeline
        rasterizationState.cullMode = VkCullMode.FRONT_BIT.i
        depthStencilState.depthWriteEnable = false
        shaderStages[0].loadShader("$assetPath/shaders/multithreading/starsphere.vert.spv", VkShaderStage.VERTEX_BIT)
        shaderStages[1].loadShader("$assetPath/shaders/multithreading/starsphere.frag.spv", VkShaderStage.FRAGMENT_BIT)
        pipelines.starsphere = device.createGraphicsPipelines(pipelineCache, pipelineCI)
    }

    fun updateMatrices() {

        matrices.projection = glm.perspective(60f.rad, size.aspect, 0.1f, 256f)
        matrices.view = Mat4(1f)
                .translate(0.0f, 0.0f, zoom)
                .rotateXYZ(rotation.x.rad, rotation.y.rad, rotation.z.rad)

        frustum.update(matrices.projection * matrices.view)
    }

    fun draw() {
        // Wait for fence to signal that all command buffers are ready
        var fenceRes = SUCCESS
        do {
            stak.longAddress(renderFence.L) { pFence ->
                fenceRes = VkResult(nvkWaitForFences(device, 1, pFence, true.i, 100000000))
            }
        } while (fenceRes == TIMEOUT)
        fenceRes.check()
        device resetFence renderFence

        super.prepareFrame()

        updateCommandBuffers(frameBuffers[currentBuffer])

        submitInfo.commandBuffer = primaryCommandBuffer

        queue.submit(submitInfo, renderFence)

        super.submitFrame()
    }

    override fun prepare() {
        super.prepare()
        // Create a fence for synchronization
        val fenceCreateInfo = vk.FenceCreateInfo(VkFenceCreate.SIGNALED_BIT)
        renderFence = device createFence fenceCreateInfo
        loadAssets()
        setupPipelineLayout()
        preparePipelines()
        prepareMultiThreadedRenderer()
        updateMatrices()

        prepared = true
        window.show()
    }

    override fun render() {
        if (!prepared)
            return
        draw()
    }

    override fun viewChanged() = updateMatrices()

    override fun UIOverlay.onUpdate() {
        if (header("Statistics"))
            text("Active threads: $numThreads")
        if (header("Settings"))
            checkBox("Skybox", ::displaySkybox)
    }
}