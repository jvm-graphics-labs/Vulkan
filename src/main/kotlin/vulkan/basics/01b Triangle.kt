package vulkan.basics

import ab.appBuffer
import glm_.L
import glm_.buffer.adr
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.size
import glm_.vec3.Vec3
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memCopy
import org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDevice
import uno.glfw.glfw
import uno.kotlin.buffers.capacity
import vkk.*
import vkk.LongArrayList.resize
import vulkan.USE_STAGING
import vulkan.assetPath
import vulkan.base.VulkanExampleBase
import vulkan.base.tools.DEFAULT_FENCE_TIMEOUT


fun main(args: Array<String>) {
    Triangle().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class Triangle : VulkanExampleBase() {

    init {
        zoom = -2.5f
        title = "Vulkan Example - Basic indexed triangle"
        // Values not set here are initialized in the base class constructor
    }

    /** Vertex layout used in this example  */
    object Vertex {
        //        float position [3];
//        float color [3];
        val size = Vec3.size * 2
        val offsetPosition = 0
        val offsetColor = Vec3.size
    }

    /** Vertex buffer and attributes    */
    object vertices {
        /** Handle to the device memory for this buffer */
        var memory: VkDeviceMemory = NULL
        /** Handle to the Vulkan buffer object that the memory is bound to  */
        var buffer: VkBuffer = NULL
    }

    /** Index buffer    */
    object indices {
        var memory: VkDeviceMemory = NULL
        var buffer: VkBuffer = NULL
        var count = 0
    }

    /** Uniform buffer block object */
    object uniformBufferVS {
        var memory: VkDeviceMemory = NULL
        var buffer: VkBuffer = NULL
        lateinit var descriptor: VkDescriptorBufferInfo
    }

    /*
        For simplicity we use the same uniform block layout as in the shader:

        layout(set = 0, binding = 0) uniform UBO {
            mat4 projectionMatrix;
            mat4 modelMatrix;
            mat4 viewMatrix;
        } ubo;

        This way we can just memcopy the ubo data to the ubo
        Note: You should use data types that align with the GPU in order to avoid manual padding (vec4, mat4)   */
    object uboVS : Bufferizable() {

        var projectionMatrix = Mat4()
        @Order(1)
        var modelMatrix = Mat4()
        var viewMatrix = Mat4()
    }

    /** The pipeline layout is used by a pipline to access the descriptor sets
     *  It defines interface (without binding any actual data) between the shader stages used by the pipeline and the
     *  shader resources
     *  A pipeline layout can be shared among multiple pipelines as long as their interfaces match  */
    var pipelineLayout: VkPipelineLayout = NULL

    /** Pipelines (often called "pipeline state objects") are used to bake all states that affect a pipeline
     *  While in OpenGL every state can be changed at (almost) any time, Vulkan requires to layout the graphics
     *  (and compute) pipeline states upfront
     *  So for each combination of non-dynamic pipeline states you need a new pipeline (there are a few exceptions to
     *  this not discussed here)
     *  Even though this adds a new dimension of planing ahead, it's a great opportunity for performance optimizations
     *  by the driver   */
    var pipeline: VkPipeline = NULL

    /** The descriptor set layout describes the shader binding layout (without actually referencing descriptor)
     *  Like the pipeline layout it's pretty much a blueprint and can be used with different descriptor sets as long as
     *  their layout matches    */
    var descriptorSetLayout: VkDescriptorSetLayout = NULL

    /** The descriptor set stores the resources bound to the binding points in a shader
     *  It connects the binding points of the different shaders with the buffers and images used for those bindings */
    var descriptorSet: VkDescriptorSet = NULL


    /*  Synchronization primitives
        Synchronization is an important concept of Vulkan that OpenGL mostly hid away.
        Getting this right is crucial to using Vulkan.     */

    /** Semaphores
     *  Used to coordinate operations within the graphics queue and ensure correct command ordering */
    var presentCompleteSemaphore: VkSemaphore = NULL
    var renderCompleteSemaphore: VkSemaphore = NULL

    /** Fences
     *  Used to check the completion of queue operations (e.g. command buffer execution)    */
//    val waitFences = ArrayList<VkFence>()

    override fun destroy() { // TODO add to verbose

        window.destroy()
        window.onWindowClosed()
        glfw.terminate()

        /*  Clean up used Vulkan resources
            Note: Inherited destructor cleans up resources stored in base class         */
        device.apply {

            destroyPipeline(pipeline)

            destroyPipelineLayout(pipelineLayout)
            destroyDescriptorSetLayout(descriptorSetLayout)

            destroyBuffer(vertices.buffer)
            freeMemory(vertices.memory)

            destroyBuffer(indices.buffer)
            freeMemory(indices.memory)

            destroyBuffer(uniformBufferVS.buffer)
            freeMemory(uniformBufferVS.memory)

            destroySemaphores(presentCompleteSemaphore, renderCompleteSemaphore)

            destroyFences(waitFences)
        }
        super.destroy()
    }

    /** This function is used to request a device memory type that supports all the property flags we request
     *  (e.g. device local, host visibile)
     *  Upon success it will return the index of the memory type that fits our requestes memory properties
     *  This is necessary as implementations can offer an arbitrary number of memory types with different
     *  memory properties.
     *  You can check http://vulkan.gpuinfo.org/ for details on different memory configurations */
    fun getMemoryTypeIndex(typeBits: Int, properties: VkMemoryPropertyFlags): Int {
        var typeBits = typeBits
        // Iterate over all memory types available for the device used in this example
        for (i in 0 until deviceMemoryProperties.memoryTypeCount) {
            if ((typeBits and 1) == 1 && (deviceMemoryProperties.memoryTypes[i].propertyFlags and properties) == properties)
                return i
            typeBits = typeBits ushr 1
        }
        throw Error("Could not find a suitable memory type!")
    }

    /** Create the Vulkan synchronization primitives used in this example   */
    fun prepareSynchronizationPrimitives() {
        // Semaphores (Used for correct command ordering)
        val semaphoreCreateInfo = vk.SemaphoreCreateInfo()

        // Semaphore used to ensures that image presentation is complete before starting to submit again
        presentCompleteSemaphore = device createSemaphore semaphoreCreateInfo

        // Semaphore used to ensures that all commands submitted have been finished before submitting the image to the queue
        renderCompleteSemaphore = device createSemaphore semaphoreCreateInfo

        // Fences (Used to check draw command buffer completion)
        val fenceCreateInfo = vk.FenceCreateInfo(
                // Create in signaled state so we don't wait on first render of each command buffer
                VkFenceCreate.SIGNALED_BIT.i)
        for (i in drawCmdBuffers.indices)
            waitFences += device createFence fenceCreateInfo
    }

    /** Get a new command buffer from the command pool
     *  If begin is true, the command buffer is also started so we can start adding commands    */
    fun getCommandBuffer(begin: Boolean): VkCommandBuffer {

        val cmdBufAllocateInfo = vk.CommandBufferAllocateInfo(cmdPool, VkCommandBufferLevel.PRIMARY, 1)

        val cmdBuffer = device allocateCommandBuffer cmdBufAllocateInfo

        // If requested, also start the new command buffer
        if (begin)
            cmdBuffer begin vk.CommandBufferBeginInfo()

        return cmdBuffer
    }

    /** End the command buffer and submit it to the queue
     *  Uses a fence to ensure command buffer has finished executing before deleting it */
    fun flushCommandBuffer(commandBuffer: VkCommandBuffer) {

        assert(commandBuffer.adr != NULL)

        commandBuffer.end()

        val submitInfo = vk.SubmitInfo { this.commandBuffer = commandBuffer }

        // Create fence to ensure that the command buffer has finished executing
        val fenceCreateInfo = vk.FenceCreateInfo()
        val fence = device createFence fenceCreateInfo

        // Submit to the queue
        queue.submit(submitInfo, fence)
        // Wait for the fence to signal that command buffer has finished executing
        device.waitForFence(fence, true, DEFAULT_FENCE_TIMEOUT)

        device destroyFence fence
        device.freeCommandBuffer(cmdPool, commandBuffer)
    }

    /** Build separate command buffers for every framebuffer image
     *  Unlike in OpenGL all rendering commands are recorded once into command buffers that are then resubmitted to the queue
     *  This allows to generate work upfront and from multiple threads, one of the biggest advantages of Vulkan */
    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo()

        /*  Set clear values for all framebuffer attachments with loadOp set to clear
            We use two attachments (color and depth) that are cleared at the start of the subpass and
            as such we need to set clear values for both         */
        val clearValues = vk.ClearValue(2).also {
            it[0].color(0f, 0f, 0.2f, 1f)
            it[1].depthStencil(1f, 0)
        }
        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@Triangle.renderPass
            renderArea.apply {
                offset(0)
                extent(size)
            }
            this.clearValues = clearValues
        }

        for (i in drawCmdBuffers.indices) {
            // Set target frame buffer
            renderPassBeginInfo.framebuffer(frameBuffers[i]) // TODO =, BUG

            drawCmdBuffers[i].apply {

                begin(cmdBufInfo)

                /*  Start the first sub pass specified in our default render pass setup by the base class
                    This will clear the color and depth attachment             */
                beginRenderPass(renderPassBeginInfo, VkSubpassContents.INLINE)

                // Update dynamic viewport state
                setViewport(size)

                // Update dynamic scissor state
                setScissor(size)

                // Bind descriptor sets describing shader binding points
                bindDescriptorSets(VkPipelineBindPoint.GRAPHICS, pipelineLayout, descriptorSet)

                /*  Bind the rendering pipeline
                    The pipeline (state object) contains all states of the rendering pipeline, binding it will set all
                    the states specified at pipeline creation time             */
                bindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)

                // Bind triangle vertex buffer (contains position and colors)
                bindVertexBuffers(0, vertices.buffer)

                // Bind triangle index buffer
                bindIndexBuffer(indices.buffer, 0, VkIndexType.UINT32)
                // Draw indexed triangle
                drawIndexed(indices.count, 1, 0, 0, 1)

                endRenderPass()

                /*  Ending the render pass will add an implicit barrier transitioning the frame buffer color attachment to
                    VK_IMAGE_LAYOUT_PRESENT_SRC_KHR for presenting it to the windowing system             */

                end()
            }
        }
    }

    fun draw() {
        // Get next image in the swap chain (back/front buffer)
        swapChain.acquireNextImage(presentCompleteSemaphore, ::currentBuffer).check()

        // Use a fence to wait until the command buffer has finished execution before using it again
        device.waitForFence(waitFences[currentBuffer], true, UINT64_MAX)
        device resetFence waitFences[currentBuffer]

        // Pipeline stage at which the queue submission will wait (via pWaitSemaphores)
        val waitStageMask = appBuffer.intBufferOf(VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i)
        // The submit info structure specifices a command buffer queue submission batch
        val submitInfo = vk.SubmitInfo {
            // Pointer to the list of pipeline stages that the semaphore waits will occur at
            waitDstStageMask = waitStageMask
            // Semaphore(s) to wait upon before the submitted command buffer starts executing
            waitSemaphore = presentCompleteSemaphore
            // One wait semaphore
            waitSemaphoreCount = 1
            // Semaphore(s) to be signaled when command buffers have completed
            signalSemaphore = renderCompleteSemaphore
            // One signal semaphore + Command buffers(s) to execute in this batch (submission)
            commandBuffer = drawCmdBuffers[currentBuffer]
        }

        // Submit to the graphics queue passing a wait fence
        queue.submit(submitInfo, waitFences[currentBuffer])

        /*  Present the current buffer to the swap chain
            Pass the semaphore signaled by the command buffer submission from the submit info as the wait semaphore
            for swap chain presentation
            This ensures that the image is not presented to the windowing system until all commands have been submitted */
        swapChain.queuePresent(queue, currentBuffer, renderCompleteSemaphore)
    }

    /** Prepare vertex and index buffers for an indexed triangle
     *  Also uploads them to device local memory using staging and initializes vertex input and attribute binding
     *  to match the vertex shader  */
    fun prepareVertices() {
        /*  A note on memory management in Vulkan in general:
            This is a very complex topic and while it's fine for an example application to to small individual memory
            allocations that is not what should be done a real-world application, where you should allocate large
            chunks of memory at once instead.   */

        // Setup vertices
        val vertexBuffer = appBuffer.floatBufferOf(
                // position    color
                +1f, +1f, +0f, 1f, 0f, 0f,
                -1f, +1f, +0f, 0f, 1f, 0f,
                +0f, -1f, +0f, 0f, 0f, 1f)

        val vertexBufferSize = vertexBuffer.size.L

        // Setup indices
        val indexBuffer = appBuffer.intBufferOf(0, 1, 2)
        indices.count = indexBuffer.capacity
        val indexBufferSize = indexBuffer.size.L

        val memoryPropertiesFlags = VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT

        if (USE_STAGING) {

            /*  Static data like vertex and index buffer should be stored on the device memory for optimal (and fastest)
                access by the GPU

                To achieve this we use so-called "staging buffers" :
                - Create a buffer that's visible to the host (and can be mapped)
                - Copy the data to this buffer
                - Create another buffer that's local on the device (VRAM) with the same size
                - Copy the data from the host to the device using a command buffer
                - Delete the host visible (staging) buffer
                - Use the device local buffers for rendering    */

            class StagingBuffer {
                var memory: VkDeviceMemory = NULL
                var buffer: VkBuffer = NULL
            }

            val stagingBuffers = object {
                val vertices = StagingBuffer()
                val indices = StagingBuffer()
            }

            // Vertex buffer
            val vertexBufferInfo = vk.BufferCreateInfo {
                size = vertexBufferSize
                // Buffer is used as the copy source
                usage = VkBufferUsage.TRANSFER_SRC_BIT.i
            }
            // Create a host-visible buffer to copy the vertex data to (staging buffer)
            stagingBuffers.vertices.buffer = device createBuffer vertexBufferInfo
            val memReqs = device getBufferMemoryRequirements stagingBuffers.vertices.buffer
            val memAlloc = vk.MemoryAllocateInfo {
                allocationSize = memReqs.size
                // Request a host visible memory type that can be used to copy our data do
                // Also request it to be coherent, so that writes are visible to the GPU right after unmapping the buffer
                memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, memoryPropertiesFlags)
            }
            stagingBuffers.vertices.memory = device allocateMemory memAlloc
            // Map and copy
            device.mappingMemory(stagingBuffers.vertices.memory, 0, memAlloc.allocationSize) { data ->
                memCopy(vertexBuffer.adr, data, vertexBufferSize)
            }
            device.bindBufferMemory(stagingBuffers.vertices.buffer, stagingBuffers.vertices.memory)

            // Create a device local buffer to which the (host local) vertex data will be copied and which will be used for rendering
            vertexBufferInfo.usage = VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT
            vertices.buffer = device createBuffer vertexBufferInfo
            device.getBufferMemoryRequirements(vertices.buffer, memReqs)
            memAlloc.allocationSize = memReqs.size
            memAlloc.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT.i)
            vertices.memory = device allocateMemory memAlloc
            device.bindBufferMemory(vertices.buffer, vertices.memory)

            // Index buffer
            val indexbufferInfo = vk.BufferCreateInfo {
                size = indexBufferSize
                usage = VkBufferUsage.TRANSFER_SRC_BIT.i
            }
            // Copy index data to a buffer visible to the host (staging buffer)
            stagingBuffers.indices.buffer = device createBuffer indexbufferInfo
            device.getBufferMemoryRequirements(stagingBuffers.indices.buffer, memReqs)
            memAlloc.allocationSize = memReqs.size
            memAlloc.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, memoryPropertiesFlags)
            stagingBuffers.indices.memory = device allocateMemory memAlloc
            device.mappingMemory(stagingBuffers.indices.memory, 0, indexBufferSize) { data ->
                memCopy(indexBuffer.adr, data, indexBufferSize)
            }
            device.bindBufferMemory(stagingBuffers.indices.buffer, stagingBuffers.indices.memory)

            // Create destination buffer with device only visibility
            indexbufferInfo.usage = VkBufferUsage.INDEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT
            indices.buffer = device createBuffer indexbufferInfo
            device.getBufferMemoryRequirements(indices.buffer, memReqs)
            memAlloc.allocationSize = memReqs.size
            memAlloc.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT.i)
            indices.memory = device allocateMemory memAlloc
            device.bindBufferMemory(indices.buffer, indices.memory)

            /*  Buffer copies have to be submitted to a queue, so we need a command buffer for them
                Note: Some devices offer a dedicated transfer queue (with only the transfer bit set) that may be faster
                when doing lots of copies             */
            val copyCmd = getCommandBuffer(true)

            // Put buffer region copies into command buffer
            val copyRegion = vk.BufferCopy()

            // Vertex buffer
            copyRegion.size = vertexBufferSize
            copyCmd.copyBuffer(stagingBuffers.vertices.buffer, vertices.buffer, copyRegion)
            // Index buffer
            copyRegion.size = indexBufferSize
            copyCmd.copyBuffer(stagingBuffers.indices.buffer, indices.buffer, copyRegion)


            // Flushing the command buffer will also submit it to the queue and uses a fence to ensure that all commands have been executed before returning
            flushCommandBuffer(copyCmd)

            // Destroy staging buffers
            // Note: Staging buffer must not be deleted before the copies have been submitted and executed
            device.apply {
                destroyBuffer(stagingBuffers.vertices.buffer)
                freeMemory(stagingBuffers.vertices.memory)
                destroyBuffer(stagingBuffers.indices.buffer)
                freeMemory(stagingBuffers.indices.memory)
            }

        } else {

            /*  Don't use staging
                Create host-visible buffers only and use these for rendering. This is not advised and will usually
                result in lower rendering performance             */

            // Vertex buffer
            val vertexBufferInfo = vk.BufferCreateInfo {
                size = vertexBufferSize
                usage = VkBufferUsage.VERTEX_BUFFER_BIT.i
            }

            // Copy vertex data to a buffer visible to the host
            vertices.buffer = device createBuffer vertexBufferInfo
            val memReqs = device getBufferMemoryRequirements vertices.buffer
            val memAlloc = vk.MemoryAllocateInfo {
                allocationSize = memReqs.size
                // VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT is host visible memory, and VK_MEMORY_PROPERTY_HOST_COHERENT_BIT makes sure writes are directly visible
                memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, memoryPropertiesFlags)
            }
            vertices.memory = device allocateMemory memAlloc
            device.mappingMemory(vertices.memory, 0, memAlloc.allocationSize) { data ->
                memCopy(vertexBuffer.adr, data, vertexBufferSize)
            }
            device.bindBufferMemory(vertices.buffer, vertices.memory)

            // Index buffer
            val indexbufferInfo = vk.BufferCreateInfo {
                size = indexBufferSize
                usage = VkBufferUsage.INDEX_BUFFER_BIT.i
            }

            // Copy index data to a buffer visible to the host
            indices.buffer = device createBuffer indexbufferInfo
            device.getBufferMemoryRequirements(indices.buffer, memReqs)
            memAlloc.allocationSize = memReqs.size
            memAlloc.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, memoryPropertiesFlags)
            indices.memory = device allocateMemory memAlloc
            device.mappingMemory(indices.memory, 0, indexBufferSize) { data ->
                memCopy(indexBuffer.adr, data, indexBufferSize)
            }
            device.bindBufferMemory(indices.buffer, indices.memory)
        }
    }

    fun setupDescriptorPool() {
        // We need to tell the API the number of max. requested descriptors per type
        // This example only uses one descriptor type (uniform buffer) and only requests one descriptor of this type
        val typeCounts = vk.DescriptorPoolSize(VkDescriptorType.UNIFORM_BUFFER, 1)

        // For additional types you need to add new entries in the type count list
        // E.g. for two combined image samplers :
        // typeCounts[1].type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        // typeCounts[1].descriptorCount = 2;

        // Create the global descriptor pool
        // All descriptors used in this example are allocated from this pool
        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo(typeCounts,
                // Set the max. number of descriptor sets that can be requested from this pool (requesting beyond this limit will result in an error)
                maxSets = 1)

        descriptorPool = device createDescriptorPool descriptorPoolInfo
    }

    fun setupDescriptorSetLayout() {
        /*  Setup layout of descriptors used in this example
            Basically connects the different shader stages to descriptors for binding uniform buffers, image samplers, etc.
            So every shader binding should map to one descriptor set layout binding */

        // Binding 0: Uniform buffer (Vertex shader)
        val layoutBinding = vk.DescriptorSetLayoutBinding {
            descriptorType = VkDescriptorType.UNIFORM_BUFFER
            descriptorCount = 1
            stageFlags = VkShaderStage.VERTEX_BIT.i
        }

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo(layoutBinding)

        descriptorSetLayout = device createDescriptorSetLayout descriptorLayout

        // Create the pipeline layout that is used to generate the rendering pipelines that are based on this descriptor set layout
        // In a more complex scenario you would have different pipeline layouts for different descriptor set layouts that could be reused
        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo(descriptorSetLayout)

        pipelineLayout = device createPipelineLayout pipelineLayoutCreateInfo
    }

    fun setupDescriptorSet() {
        // Allocate a new descriptor set from the global descriptor pool
        val allocInfo = vk.DescriptorSetAllocateInfo(descriptorPool, descriptorSetLayout)

        descriptorSet = device allocateDescriptorSets allocInfo

        /*  Update the descriptor set determining the shader binding points
            For every binding point used in a shader there needs to be one descriptor set matching that binding point   */

        val writeDescriptorSet = vk.WriteDescriptorSet(descriptorSet, VkDescriptorType.UNIFORM_BUFFER, 0, uniformBufferVS.descriptor)

        device updateDescriptorSets writeDescriptorSet
    }

    /** Create the depth (and stencil) buffer attachments used by our framebuffers
     *  Note: Override of virtual function in the base class and called from within VulkanExampleBase::prepare  */
    override fun setupDepthStencil() {
        // Create an optimal image used as the depth stencil attachment
        val image = vk.ImageCreateInfo {
            imageType = VkImageType.`2D`
            format = depthFormat
            // Use example's height and width
            extent.set(size.x, size.y, 1)
            mipLevels = 1
            arrayLayers = 1
            samples = VkSampleCount.`1_BIT`
            tiling = VkImageTiling.OPTIMAL
            usage = VkImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT or VkImageUsage.TRANSFER_SRC_BIT
            initialLayout = VkImageLayout.UNDEFINED
        }
        depthStencil.image = device createImage image

        // Allocate memory for the image (device local) and bind it to our image
        val memAlloc = vk.MemoryAllocateInfo {
            val memReqs = device getImageMemoryRequirements depthStencil.image
            allocationSize = memReqs.size
            memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT.i)
        }
        depthStencil.mem = device allocateMemory memAlloc
        device.bindImageMemory(depthStencil.image, depthStencil.mem)

        /*  Create a view for the depth stencil image
            Images aren't directly accessed in Vulkan, but rather through views described by a subresource range
            This allows for multiple views of one image with differing ranges (e.g. for different layers)   */
        val depthStencilView = vk.ImageViewCreateInfo {
            viewType = VkImageViewType.`2D`
            format = depthFormat
            subresourceRange.apply {
                aspectMask = VkImageAspect.DEPTH_BIT or VkImageAspect.STENCIL_BIT
                baseMipLevel = 0
                levelCount = 1
                baseArrayLayer = 0
                layerCount = 1
            }
            this.image = depthStencil.image
        }
        depthStencil.view = device createImageView depthStencilView
    }

    /** Create a frame buffer for each swap chain image
     *  Note: Override of virtual function in the base class and called from within VulkanExampleBase::prepare  */
    override fun setupFrameBuffer() {
        // Create a frame buffer for every image in the swapchain
        frameBuffers resize swapChain.imageCount
        for (i in frameBuffers.indices) {
            val attachments = appBuffer.longBufferOf(
                    swapChain.buffers[i].view,  // Color attachment is the view of the swapchain image
                    depthStencil.view)          // Depth/Stencil attachment is the same for all frame buffers

            val (w, h) = size // TODO BUG

            val frameBufferCreateInfo = vk.FramebufferCreateInfo {
                // All frame buffers use the same renderpass setup
                renderPass = this@Triangle.renderPass
                this.attachments = attachments
                //it.size(size, 1) TODO BUG
                width = w
                height = h
                layers = 1
            }
            // Create the framebuffer
            frameBuffers[i] = device createFramebuffer frameBufferCreateInfo
        }
    }

    /** Render pass setup
     *  Render passes are a new concept in Vulkan. They describe the attachments used during rendering and may contain
     *  multiple subpasses with attachment dependencies
     *  This allows the driver to know up-front what the rendering will look like and is a good opportunity to optimize
     *  especially on tile-based renderers (with multiple subpasses)
     *  Using sub pass dependencies also adds implicit layout transitions for the attachment used, so we don't need
     *  to add explicit image memory barriers to transform them
     *  Note: Override of virtual function in the base class and called from within VulkanExampleBase::prepare  */
    override fun setupRenderPass() {
        // This example will use a single render pass with one subpass

        // Descriptors for the attachments used by this renderpass
        val attachments = vk.AttachmentDescription(2).also {
            // Color attachment
            it[0].apply {
                format = swapChain.colorFormat                  // Use the color format selected by the swapchain
                samples = VkSampleCount.`1_BIT`                 // We don't use multi sampling in this example
                loadOp = VkAttachmentLoadOp.CLEAR               // Clear this attachment at the start of the render pass
                storeOp = VkAttachmentStoreOp.STORE             // Keep it's contents after the render pass is finished (for displaying it)
                stencilLoadOp = VkAttachmentLoadOp.DONT_CARE    // We don't use stencil, so don't care for load
                stencilStoreOp = VkAttachmentStoreOp.DONT_CARE  // Same for store
                initialLayout = VkImageLayout.UNDEFINED         // Layout at render pass start. Initial doesn't matter, so we use undefined
                finalLayout = VkImageLayout.PRESENT_SRC_KHR     // Layout to which the attachment is transitioned when the render pass is finished
            }
            // As we want to present the color buffer to the swapchain, we transition to PRESENT_KHR
            // Depth attachment
            it[1].apply {
                format = depthFormat                                            // A proper depth format is selected in the example base
                samples = VkSampleCount.`1_BIT`
                loadOp = VkAttachmentLoadOp.CLEAR                               // Clear depth at start of first subpass
                storeOp = VkAttachmentStoreOp.DONT_CARE                         // We don't need depth after render pass has finished (DONT_CARE may result in better performance)
                stencilLoadOp = VkAttachmentLoadOp.DONT_CARE                    // No stencil
                stencilStoreOp = VkAttachmentStoreOp.DONT_CARE                  // No Stencil
                initialLayout = VkImageLayout.UNDEFINED                         // Layout at render pass start. Initial doesn't matter, so we use undefined
                finalLayout = VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL    // Transition to depth/stencil attachment
            }
        }
        // Setup attachment references
        val colorReference = vk.AttachmentReference(1) {
            attachment = 0                                      // Attachment 0 is color
            layout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL     // Attachment layout used as color during the subpass
        }

        val depthReference = vk.AttachmentReference {
            attachment = 1                                              // Attachment 1 is color
            layout = VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL     // Attachment used as depth/stemcil used during the subpass
        }

        // Setup a single subpass reference
        val subpassDescription = vk.SubpassDescription {
            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
            colorAttachmentCount = 1
            colorAttachments = colorReference                   // Reference to the color attachment in slot 0
            depthStencilAttachment = depthReference             // Reference to the depth attachment in slot 1
            inputAttachments = null                             // (Input attachments not used by this example)
            preserveAttachments = null                          // (Preserve attachments not used by this example)
            resolveAttachments = null                           // Resolve attachments are resolved at the end of a sub pass and can be used for e.g. multi sampling
        }

        /*  Setup subpass dependencies
            These will add the implicit ttachment layout transitionss specified by the attachment descriptions
            The actual usage layout is preserved through the layout specified in the attachment reference
            Each subpass dependency will introduce a memory and execution dependency between the source and dest subpass described by
            srcStageMask, dstStageMask, srcAccessMask, dstAccessMask (and dependencyFlags is set)
            Note: VK_SUBPASS_EXTERNAL is a special constant that refers to all commands executed outside of the actual renderpass)  */
        val dependencies = vk.SubpassDependency(2).also {
            /*  First dependency at the start of the renderpass
                Does the transition from final to initial layout         */
            it[0].apply {
                srcSubpass = VK_SUBPASS_EXTERNAL     // Producer of the dependency
                dstSubpass = 0                      // Consumer is our single subpass that will wait for the execution depdendency
                srcStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
                dstStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
                srcAccessMask = VkAccess.MEMORY_READ_BIT.i
                dstAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
                dependencyFlags = VkDependency.BY_REGION_BIT.i
            }
            /*  Second dependency at the end the renderpass
                Does the transition from the initial to the final layout         */
            it[1].apply {
                srcSubpass = 0                      // Producer of the dependency is our single subpass
                dstSubpass = VK_SUBPASS_EXTERNAL    // Consumer are all commands outside of the renderpass
                srcStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
                dstStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
                srcAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
                dstAccessMask = VkAccess.MEMORY_READ_BIT.i
                dependencyFlags = VkDependency.BY_REGION_BIT.i
            }
        }
        // Create the actual renderpass
        val renderPassInfo = vk.RenderPassCreateInfo {
            this.attachments = attachments      // Descriptions of the attachments used by the render pass
            subpass = subpassDescription      // We only use one subpass in this example, Description of that subpass
            this.dependencies = dependencies    // Subpass dependencies used by the render pass
        }

        renderPass = device createRenderPass renderPassInfo
    }

    fun preparePipelines() {
        /*  Create the graphics pipeline used in this example
            Vulkan uses the concept of rendering pipelines to encapsulate fixed states, replacing OpenGL's complex state machine
            A pipeline is then stored and hashed on the GPU making pipeline changes very fast
            Note: There are still a few dynamic states that are not directly part of the pipeline (but the info that they are used is)  */

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(
                // The layout used for this pipeline (can be shared among multiple pipelines using the same layout)
                pipelineLayout,
                // Renderpass this pipeline is attached to
                renderPass)

        /*  Construct the different states making up the pipeline

            Input assembly state describes how primitives are assembled
            This pipeline will assemble vertex data as a triangle lists (though we only use one triangle)   */
        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo(VkPrimitiveTopology.TRIANGLE_LIST)

        // Rasterization state
        val rasterizationState = vk.PipelineRasterizationStateCreateInfo(VkPolygonMode.FILL, VkCullMode.NONE.i, VkFrontFace.COUNTER_CLOCKWISE)

        /*  Color blend state describes how blend factors are calculated (if used)
            We need one blend attachment state per color attachment (even if blending is not used         */
        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(0xf, false)
        val colorBlendState = vk.PipelineColorBlendStateCreateInfo(blendAttachmentState)

        /*  Viewport state sets the number of viewports and scissor used in this pipeline
            Note: This is actually overriden by the dynamic states (see below)         */
        val viewportState = vk.PipelineViewportStateCreateInfo(1, 1)

        /** Enable dynamic states
         *  Most states are baked into the pipeline, but there are still a few dynamic states that can be changed within a command buffer
         *  To be able to change these we need do specify which dynamic states will be changed using this pipeline.
         *  Their actual states are set later on in the command buffer.
         *  For this example we will set the viewport and scissor using dynamic states  */
        val dynamicStateEnables = listOf(VkDynamicState.VIEWPORT, VkDynamicState.SCISSOR)
        val dynamicState = vk.PipelineDynamicStateCreateInfo(dynamicStateEnables)

        /*  Depth and stencil state containing depth and stencil compare and test operations
            We only use depth tests and want depth tests and writes to be enabled and compare with less or equal         */
        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo(true, true, VkCompareOp.LESS_OR_EQUAL)

        /*  Multi sampling state
            This example does not make use fo multi sampling (for anti-aliasing), the state must still be set and passed to the pipeline         */
        val multisampleState = vk.PipelineMultisampleStateCreateInfo(VkSampleCount.`1_BIT`)

        /*  Vertex input descriptions
            Specifies the vertex input parameters for a pipeline

            Vertex input binding
            This example uses a single vertex input binding at binding point 0 (see vkCmdBindVertexBuffers) */
        val vertexInputBinding = vk.VertexInputBindingDescription(0, Vertex.size, VkVertexInputRate.VERTEX)

        // Inpute attribute bindings describe shader attribute locations and memory layouts
        val vertexInputAttributs = vk.VertexInputAttributeDescription(2).also {
            /*  These match the following shader layout (see triangle.vert):
                layout (location = 0) in vec3 inPos;
                layout (location = 1) in vec3 inColor;  */
            it[0].apply {
                // Attribute location 0: Position
                binding = 0
                location = 0
                // Position attribute is three 32 bit signed (SFLOAT) floats (R32 G32 B32)
                format = VkFormat.R32G32B32_SFLOAT
                offset = Vertex.offsetPosition
            }
            it[1].apply {
                // Attribute location 1: Color
                binding = 0
                location = 1
                // Color attribute is three 32 bit signed (SFLOAT) floats (R32 G32 B32)
                format = VkFormat.R32G32B32_SFLOAT
                offset = Vertex.offsetColor
            }
        }
        // Vertex input state used for pipeline creation
        val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescription = vertexInputBinding
            vertexAttributeDescriptions = vertexInputAttributs
        }

        // Shaders
        val shaderStages = vk.PipelineShaderStageCreateInfo(2).also {
            // Vertex shader
            it[0].loadShader("$assetPath/shaders/triangle/triangle.vert.spv", VkShaderStage.VERTEX_BIT, false)
            // Fragment shader
            it[1].loadShader("$assetPath/shaders/triangle/triangle.frag.spv", VkShaderStage.FRAGMENT_BIT, false)
        }
        // Set pipeline shader stage info
        pipelineCreateInfo.also {
            it.stages = shaderStages
            // Assign the pipeline states to the pipeline creation info structure
            it.vertexInputState = vertexInputState
            it.inputAssemblyState = inputAssemblyState
            it.rasterizationState = rasterizationState
            it.colorBlendState = colorBlendState
            it.multisampleState = multisampleState
            it.viewportState = viewportState
            it.depthStencilState = depthStencilState
            it.renderPass = renderPass
            it.dynamicState = dynamicState
            it.tessellationState = null
        }
        // Create rendering pipeline using the specified states
        pipeline = device.createGraphicsPipelines(pipelineCache, pipelineCreateInfo)

        // Shader modules are no longer needed once the graphics pipeline has been created,
        // normally they will be destroyed in ::destroy, but we pass false at creation above to delete them now
        device destroyShaderModules shaderStages
    }

    fun prepareUniformBuffers() {
        /*  Prepare and initialize a uniform buffer block containing shader uniforms
            Single uniforms like in OpenGL are no longer present in Vulkan. All Shader uniforms are passed
            via uniform buffer blocks         */

        val bufferInfo = vk.BufferCreateInfo {
            size = uboVS.size.L
            // This buffer will be used as a uniform buffer
            usage = VkBufferUsage.UNIFORM_BUFFER_BIT.i
        }

        // Create a new buffer
        uniformBufferVS.buffer = device createBuffer bufferInfo
        // Get memory requirements including size, alignment and memory type
        val memReqs = device getBufferMemoryRequirements uniformBufferVS.buffer
        // Vertex shader uniform buffer block
        val allocInfo = vk.MemoryAllocateInfo {
            allocationSize = memReqs.size
            /*  Get the memory type index that supports host visibile memory access
            Most implementations offer multiple memory types and selecting the correct one to allocate memory from is crucial
            We also want the buffer to be host coherent so we don't have to flush (or sync after every update.
            Note: This may affect performance so you might not want to do this in a real world application that updates
            buffers on a regular base   */
            memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT)
        }
        // Allocate memory for the uniform buffer
        uniformBufferVS.memory = device allocateMemory allocInfo
        // Bind memory to buffer
        device.bindBufferMemory(uniformBufferVS.buffer, uniformBufferVS.memory)

        // Store information in the uniform's descriptor that is used by the descriptor set
        uniformBufferVS.descriptor = vk.DescriptorBufferInfo {
            buffer = uniformBufferVS.buffer
            range = uboVS.size.L
        }
        updateUniformBuffers()
    }

    fun updateUniformBuffers() {

        // Update matrices
        uboVS.projectionMatrix = glm.perspective(60f.rad, size.aspect, 0.1f, 256f)

        uboVS.viewMatrix = glm.translate(Mat4(1f), 0f, 0f, zoom)

        uboVS.modelMatrix = Mat4(1f)
                .rotate(rotation.x.rad, 1f, 0f, 0f)
                .rotate(rotation.y.rad, 0f, 1f, 0f)
                .rotate(rotation.z.rad, 0f, 0f, 1f)

        // Map uniform buffer and update it
        device.mappingMemory(uniformBufferVS.memory, 0, uboVS.size.L, 0) { data ->
            uboVS to data
        }/* Unmap after data has been copied
            Note: Since we requested a host coherent memory type for the uniform buffer, the write is instantly visible to the GPU */
    }

    override fun prepare() {
        super.prepare()
        prepareSynchronizationPrimitives()
        prepareVertices()
        prepareUniformBuffers()
        setupDescriptorSetLayout()
        preparePipelines()
        setupDescriptorPool()
        setupDescriptorSet()
        buildCommandBuffers()

        window.show()

        prepared = true
    }

    override fun render() {
        if (!prepared) return
        draw()
    }

    /** This function is called by the base example class each time the view is changed by user input */
    override fun viewChanged() = updateUniformBuffers()
}