package vulkan

import glfw_.appBuffer
import glfw_.glfw
import glm_.L
import glm_.f
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.size
import glm_.vec3.Vec3
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import uno.buffer.intBufferOf
import uno.buffer.toBuffer
import uno.buffer.use
import uno.kotlin.buffers.capacity
import vkn.*
import vkn.ArrayListLong.resize
import vkn.VkMemoryStack.Companion.withStack
import vulkan.base.VulkanExampleBase
import vulkan.base.tools.DEFAULT_FENCE_TIMEOUT
import java.io.File


fun main(args: Array<String>) {
    Triangle().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

/** Set to "true" to enable Vulkan's validation layers (see vulkandebug.cpp for details)    */
const val ENABLE_VALIDATION = true
/** Set to "true" to use staging buffers for uploading vertex and index data to device local memory
 *  See "prepareVertices" for details on what's staging and on why to use it    */
const val USE_STAGING = true

private class Triangle : VulkanExampleBase(ENABLE_VALIDATION) {

    init {
        zoom = -2.5f
        title = "Vulkan Example - Basic indexed triangle"
        // Values not set here are initialized in the base class constructor
    }

    val memoryPropertiesFlags = VkMemoryProperty.HOST_VISIBLE_BIT or VkMemoryProperty.HOST_COHERENT_BIT

    /** Vertex layout used in this example  */
    object Vertex {
        //        float position [3];
//        float color [3];
        val size = Vec3.size * 2
        val offsetPosition = 0
        val offsetColor = Vec3.size
    }

    /** Vertex buffer and attributes    */
    private val vertices = object {
        /** Handle to the device memory for this buffer */
        var memory: VkDeviceMemory = NULL
        /** Handle to the Vulkan buffer object that the memory is bound to  */
        var buffer: VkBuffer = NULL
    }

    /** Index buffer    */
    private val indices = object {
        var memory: VkDeviceMemory = NULL
        var buffer: VkBuffer = NULL
        var count = 0
    }

    /** Uniform buffer block object */
    private val uniformBufferVS = object {
        var memory = NULL
        var buffer = NULL
        var descriptor = cVkDescriptorBufferInfo(1)
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
    private val uboVS = object {
        var projectionMatrix = Mat4()
        var modelMatrix = Mat4()
        var viewMatrix = Mat4()
        val size = Mat4.size * 3L
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
    val waitFences = ArrayList<VkFence>()

    override fun destroy() {

        window.destroy()
        glfw.terminate()

        /*  Clean up used Vulkan resources
            Note: Inherited destructor cleans up resources stored in base class         */
        vk.destroyPipeline(device, pipeline)

        vk.destroyPipelineLayout(device, pipelineLayout)
        vk.destroyDescriptorSetLayout(device, descriptorSetLayout)

        vk.destroyBuffer(device, vertices.buffer)
        vk.freeMemory(device, vertices.memory)

        vk.destroyBuffer(device, indices.buffer)
        vk.freeMemory(device, indices.memory)

        vk.destroyBuffer(device, uniformBufferVS.buffer)
        vk.freeMemory(device, uniformBufferVS.memory)

        vk.destroySemaphore(device, presentCompleteSemaphore)
        vk.destroySemaphore(device, renderCompleteSemaphore)

        vk.destroyFences(device, waitFences)

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
        val semaphoreCreateInfo = vk.SemaphoreCreateInfo {}

        // Semaphore used to ensures that image presentation is complete before starting to submit again
        vk.createSemaphore(device, semaphoreCreateInfo, ::presentCompleteSemaphore).check()

        // Semaphore used to ensures that all commands submitted have been finished before submitting the image to the queue
        vk.createSemaphore(device, semaphoreCreateInfo, ::renderCompleteSemaphore).check()

        // Fences (Used to check draw command buffer completion)
        val fenceCreateInfo = vk.FenceCreateInfo {
            // Create in signaled state so we don't wait on first render of each command buffer
            flags = VkFenceCreate.SIGNALED_BIT.i
        }
        waitFences resize drawCmdBuffers.size
        vk.createFences(device, fenceCreateInfo, waitFences)  // check inside singularly
    }

    /** Get a new command buffer from the command pool
     *  If begin is true, the command buffer is also started so we can start adding commands    */
    fun getCommandBuffer(begin: Boolean): VkCommandBuffer {

        val cmdBufAllocateInfo = vk.CommandBufferAllocateInfo {
            commandPool = cmdPool
            level = VkCommandBufferLevel.PRIMARY
            commandBufferCount = 1
        }

        val pCmdBuffer = appBuffer.pointerBuffer
        VK_CHECK_RESULT(vkAllocateCommandBuffers(device, cmdBufAllocateInfo, pCmdBuffer))

        val cmdBuffer = VkCommandBuffer(pCmdBuffer[0], device)

        // If requested, also start the new command buffer
        if (begin)
            VK_CHECK_RESULT(vkBeginCommandBuffer(cmdBuffer, vk.CommandBufferBeginInfo {}))

        return cmdBuffer
    }

    /** End the command buffer and submit it to the queue
     *  Uses a fence to ensure command buffer has finished executing before deleting it */
    fun flushCommandBuffer(commandBuffer: VkCommandBuffer) {

        assert(commandBuffer.adr != NULL)

        VK_CHECK_RESULT(vkEndCommandBuffer(commandBuffer))

        val submitInfo = vk.SubmitInfo { commandBuffers = appBuffer.pointerBufferOf(commandBuffer) }

        // Create fence to ensure that the command buffer has finished executing
        val fenceCreateInfo = vk.FenceCreateInfo { flags = 0 }
        val fence: VkFence = getLong { vk.createFence(device, fenceCreateInfo, it).check() }

        // Submit to the queue
        VK_CHECK_RESULT(vkQueueSubmit(queue, submitInfo, fence))
        // Wait for the fence to signal that command buffer has finished executing
        VK_CHECK_RESULT(vkWaitForFences(device, fence, true, DEFAULT_FENCE_TIMEOUT))

        vk.destroyFence(device, fence)
        vkFreeCommandBuffers(device, cmdPool, commandBuffer)
    }

    /** Build separate command buffers for every framebuffer image
     *  Unlike in OpenGL all rendering commands are recorded once into command buffers that are then resubmitted to the queue
     *  This allows to generate work upfront and from multiple threads, one of the biggest advantages of Vulkan */
    override fun buildCommandBuffers() {

        val cmdBufInfo = vk.CommandBufferBeginInfo {}

        /*  Set clear values for all framebuffer attachments with loadOp set to clear
            We use two attachments (color and depth) that are cleared at the start of the subpass and
            as such we need to set clear values for both         */
        val clearValues = vk.ClearValue(2)
        clearValues[0].color(0f, 0f, 0.2f, 1f)
        clearValues[1].depthStencil(1f, 0)

        val renderPassBeginInfo = vk.RenderPassBeginInfo {
            renderPass = this@Triangle.renderPass
            renderArea.apply {
                offset.set(0, 0)
                extent.set(size.x, size.y)
            }
            this.clearValues = clearValues
        }

        for (i in drawCmdBuffers.indices) {
            // Set target frame buffer
            renderPassBeginInfo.framebuffer(frameBuffers[i]) // TODO =, BUG

            VK_CHECK_RESULT(vkBeginCommandBuffer(drawCmdBuffers[i], cmdBufInfo))

            /*  Start the first sub pass specified in our default render pass setup by the base class
                This will clear the color and depth attachment             */
            vk.cmdBeginRenderPass(drawCmdBuffers[i], renderPassBeginInfo, VkSubpassContents.INLINE)

            // Update dynamic viewport state
            val viewport = vk.Viewport(1) {
                //                size(this@Triangle.size) TODO bug
                width = size.x.f
                height = size.y.f
                //                depth.put(0f, 1f) same
                minDepth = 0f
                maxDepth = 1f
            }
            vkCmdSetViewport(drawCmdBuffers[i], 0, viewport)

            // Update dynamic scissor state
            val scissor = vk.Rect2D(1) {
                extent.set(size.x, size.y)
                offset.set(0, 0)
            }
            vkCmdSetScissor(drawCmdBuffers[i], 0, scissor)

            // Bind descriptor sets describing shader binding points
            vk.cmdBindDescriptorSets(drawCmdBuffers[i], VkPipelineBindPoint.GRAPHICS, pipelineLayout, 0, ::descriptorSet)

            /*  Bind the rendering pipeline
                The pipeline (state object) contains all states of the rendering pipeline, binding it will set all
                the states specified at pipeline creation time             */
            vkCmdBindPipeline(drawCmdBuffers[i], VkPipelineBindPoint.GRAPHICS.i, pipeline)

            // Bind triangle vertex buffer (contains position and colors)
            vk.cmdBindVertexBuffer(drawCmdBuffers[i], 0, vertices::buffer)

            // Bind triangle index buffer
            vk.cmdBindIndexBuffer(drawCmdBuffers[i], indices.buffer, 0, VkIndexType.UINT32)

            // Draw indexed triangle
            vkCmdDrawIndexed(drawCmdBuffers[i], indices.count, 1, 0, 0, 1)

            vkCmdEndRenderPass(drawCmdBuffers[i])

            /*  Ending the render pass will add an implicit barrier transitioning the frame buffer color attachment to
                VK_IMAGE_LAYOUT_PRESENT_SRC_KHR for presenting it to the windowing system             */

            VK_CHECK_RESULT(vkEndCommandBuffer(drawCmdBuffers[i]))
        }
    }

    fun draw() {
        // Get next image in the swap chain (back/front buffer)
        swapChain.acquireNextImage(presentCompleteSemaphore, ::currentBuffer).check()

        // Use a fence to wait until the command buffer has finished execution before using it again
        VK_CHECK_RESULT(vkWaitForFences(device, waitFences[currentBuffer], true, UINT64_MAX))
        VK_CHECK_RESULT(vkResetFences(device, waitFences[currentBuffer]))

        // Pipeline stage at which the queue submission will wait (via pWaitSemaphores)
        val waitStageMask = appBuffer.intBufferOf(VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i)
        // The submit info structure specifices a command buffer queue submission batch
        val submitInfo = vk.SubmitInfo {
            // Pointer to the list of pipeline stages that the semaphore waits will occur at
            waitDstStageMask = waitStageMask
            // Semaphore(s) to wait upon before the submitted command buffer starts executing
            waitSemaphores = appBuffer.longBufferOf(presentCompleteSemaphore)
            // One wait semaphore
            waitSemaphoreCount = 1
            // Semaphore(s) to be signaled when command buffers have completed
            signalSemaphores = appBuffer.longBufferOf(renderCompleteSemaphore)
            // One signal semaphore + Command buffers(s) to execute in this batch (submission)
            commandBuffers = appBuffer.pointerBufferOf(drawCmdBuffers[currentBuffer])
        }

        // Submit to the graphics queue passing a wait fence
        VK_CHECK_RESULT(vkQueueSubmit(queue, submitInfo, waitFences[currentBuffer]))

        /*  Present the current buffer to the swap chain
            Pass the semaphore signaled by the command buffer submission from the submit info as the wait semaphore
            for swap chain presentation
            This ensures that the image is not presented to the windowing system until all commands have been submitted */
        swapChain.queuePresent(queue, currentBuffer, renderCompleteSemaphore).check()
    }

    /** Prepare vertex and index buffers for an indexed triangle
     *  Also uploads them to device local memory using staging and initializes vertex input and attribute binding
     *  to match the vertex shader  */
    fun prepareVertices(useStagingBuffers: Boolean) {
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
        val indexBuffer = intBufferOf(0, 1, 2)
        indices.count = indexBuffer.capacity
        val indexBufferSize = indexBuffer.size.L

        val memAlloc = vk.MemoryAllocateInfo {}
        val memReqs = vk.MemoryRequirements {}

        val data = appBuffer.pointer

        if (useStagingBuffers) {
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
            vk.createBuffer(device, vertexBufferInfo, stagingBuffers.vertices::buffer).check()
            vkGetBufferMemoryRequirements(device, stagingBuffers.vertices.buffer, memReqs)
            memAlloc.allocationSize = memReqs.size
            // Request a host visible memory type that can be used to copy our data do
            // Also request it to be coherent, so that writes are visible to the GPU right after unmapping the buffer
            memAlloc.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, memoryPropertiesFlags)
            vk.allocateMemory(device, memAlloc, stagingBuffers.vertices::memory).check()
            // Map and copy
            VK_CHECK_RESULT(nvkMapMemory(device, stagingBuffers.vertices.memory, 0, memAlloc.allocationSize, 0, data))
            memCopy(vertexBuffer.adr, memGetAddress(data), vertexBufferSize)
            vkUnmapMemory(device, stagingBuffers.vertices.memory)
            VK_CHECK_RESULT(vkBindBufferMemory(device, stagingBuffers.vertices.buffer, stagingBuffers.vertices.memory, 0))

            // Create a device local buffer to which the (host local) vertex data will be copied and which will be used for rendering
            vertexBufferInfo.usage = VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT
            vk.createBuffer(device, vertexBufferInfo, vertices::buffer).check()
            vkGetBufferMemoryRequirements(device, vertices.buffer, memReqs)
            memAlloc.allocationSize = memReqs.size
            memAlloc.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT.i)
            vk.allocateMemory(device, memAlloc, vertices::memory).check()
            VK_CHECK_RESULT(vkBindBufferMemory(device, vertices.buffer, vertices.memory, 0))

            // Index buffer
            val indexbufferInfo = vk.BufferCreateInfo {
                size = indexBufferSize
                usage = VkBufferUsage.TRANSFER_SRC_BIT.i
            }
            // Copy index data to a buffer visible to the host (staging buffer)
            vk.createBuffer(device, indexbufferInfo, stagingBuffers.indices::buffer).check()
            vkGetBufferMemoryRequirements(device, stagingBuffers.indices.buffer, memReqs)
            memAlloc.allocationSize = memReqs.size
            memAlloc.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, memoryPropertiesFlags)
            vk.allocateMemory(device, memAlloc, stagingBuffers.indices::memory).check()
            VK_CHECK_RESULT(nvkMapMemory(device, stagingBuffers.indices.memory, 0, indexBufferSize, 0, data))
            memCopy(indexBuffer.adr, memGetAddress(data), indexBufferSize)
            vkUnmapMemory(device, stagingBuffers.indices.memory)
            VK_CHECK_RESULT(vkBindBufferMemory(device, stagingBuffers.indices.buffer, stagingBuffers.indices.memory, 0))

            // Create destination buffer with device only visibility
            indexbufferInfo.usage = VkBufferUsage.INDEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT
            vk.createBuffer(device, indexbufferInfo, indices::buffer).check()
            vkGetBufferMemoryRequirements(device, indices.buffer, memReqs)
            memAlloc.allocationSize = memReqs.size
            memAlloc.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT.i)
            vk.allocateMemory(device, memAlloc, indices::memory).check()
            VK_CHECK_RESULT(vkBindBufferMemory(device, indices.buffer, indices.memory, 0))

//            val cmdBufferBeginInfo = VkCommandBufferBeginInfo.calloc().apply {
//                sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
//                pNext(NULL)
//            }

            /*  Buffer copies have to be submitted to a queue, so we need a command buffer for them
                Note: Some devices offer a dedicated transfer queue (with only the transfer bit set) that may be faster
                when doing lots of copies             */
            val copyCmd = getCommandBuffer(true)

            // Put buffer region copies into command buffer
            val copyRegion = vk.BufferCopy(1)
            // Vertex buffer
            copyRegion.size = vertexBufferSize
            vkCmdCopyBuffer(copyCmd, stagingBuffers.vertices.buffer, vertices.buffer, copyRegion)
            // Index buffer
            copyRegion.size = indexBufferSize
            vkCmdCopyBuffer(copyCmd, stagingBuffers.indices.buffer, indices.buffer, copyRegion)


            // Flushing the command buffer will also submit it to the queue and uses a fence to ensure that all commands have been executed before returning
            flushCommandBuffer(copyCmd)

            // Destroy staging buffers
            // Note: Staging buffer must not be deleted before the copies have been submitted and executed
            vk.destroyBuffer(device, stagingBuffers.vertices.buffer)
            vk.freeMemory(device, stagingBuffers.vertices.memory)
            vk.destroyBuffer(device, stagingBuffers.indices.buffer)
            vk.freeMemory(device, stagingBuffers.indices.memory)

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
            vk.createBuffer(device, vertexBufferInfo, vertices::buffer).check()
            vkGetBufferMemoryRequirements(device, vertices.buffer, memReqs)
            memAlloc.allocationSize = memReqs.size
            // VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT is host visible memory, and VK_MEMORY_PROPERTY_HOST_COHERENT_BIT makes sure writes are directly visible
            memAlloc.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, memoryPropertiesFlags)
            vk.allocateMemory(device, memAlloc, vertices::memory).check()
            VK_CHECK_RESULT(nvkMapMemory(device, vertices.memory, 0, memAlloc.allocationSize, 0, data))
            memCopy(vertexBuffer.adr, memGetAddress(data), vertexBufferSize)
            vkUnmapMemory(device, vertices.memory)
            VK_CHECK_RESULT(vkBindBufferMemory(device, vertices.buffer, vertices.memory, 0))

            // Index buffer
            val indexbufferInfo = vk.BufferCreateInfo {
                size = indexBufferSize
                usage = VkBufferUsage.INDEX_BUFFER_BIT.i
            }

            // Copy index data to a buffer visible to the host
            vk.createBuffer(device, indexbufferInfo, indices::buffer).check()
            vkGetBufferMemoryRequirements(device, indices.buffer, memReqs)
            memAlloc.allocationSize = memReqs.size
            memAlloc.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, memoryPropertiesFlags)
            vk.allocateMemory(device, memAlloc, indices::memory).check()
            VK_CHECK_RESULT(nvkMapMemory(device, indices.memory, 0, indexBufferSize, 0, data))
            memCopy(indexBuffer.adr, memGetAddress(data), indexBufferSize)
            vkUnmapMemory(device, indices.memory)
            VK_CHECK_RESULT(vkBindBufferMemory(device, indices.buffer, indices.memory, 0))
        }
    }

    fun setupDescriptorPool() = withStack {
        // We need to tell the API the number of max. requested descriptors per type
        val typeCounts = vk.DescriptorPoolSize(1) {
            // This example only uses one descriptor type (uniform buffer) and only requests one descriptor of this type
            type = VkDescriptorType.UNIFORM_BUFFER
            descriptorCount = 1
        }
        // For additional types you need to add new entries in the type count list
        // E.g. for two combined image samplers :
        // typeCounts[1].type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        // typeCounts[1].descriptorCount = 2;

        // Create the global descriptor pool
        // All descriptors used in this example are allocated from this pool
        val descriptorPoolInfo = vk.DescriptorPoolCreateInfo {
            poolSizes = typeCounts
            // Set the max. number of descriptor sets that can be requested from this pool (requesting beyond this limit will result in an error)
            maxSets = 1
        }
        vk.createDescriptorPool(device, descriptorPoolInfo, ::descriptorPool).check()
    }

    fun setupDescriptorSetLayout() {
        /*  Setup layout of descriptors used in this example
            Basically connects the different shader stages to descriptors for binding uniform buffers, image samplers, etc.
            So every shader binding should map to one descriptor set layout binding */

        // Binding 0: Uniform buffer (Vertex shader)
        val layoutBinding = vk.DescriptorSetLayoutBinding(1) {
            descriptorType = VkDescriptorType.UNIFORM_BUFFER
            descriptorCount = 1
            stageFlags = VkShaderStage.VERTEX_BIT.i
            immutableSamplers = null
        }

        val descriptorLayout = vk.DescriptorSetLayoutCreateInfo { bindings = layoutBinding }

        vk.createDescriptorSetLayout(device, descriptorLayout, ::descriptorSetLayout).check()

        // Create the pipeline layout that is used to generate the rendering pipelines that are based on this descriptor set layout
        // In a more complex scenario you would have different pipeline layouts for different descriptor set layouts that could be reused
        val pipelineLayoutCreateInfo = vk.PipelineLayoutCreateInfo {
            setLayouts = appBuffer.longBufferOf(descriptorSetLayout)
        }

        vk.createPipelineLayout(device, pipelineLayoutCreateInfo, ::pipelineLayout).check()
    }

    fun setupDescriptorSet() {
        // Allocate a new descriptor set from the global descriptor pool
        val allocInfo = vk.DescriptorSetAllocateInfo {
            descriptorPool = this@Triangle.descriptorPool
            setLayouts = appBuffer.longBufferOf(descriptorSetLayout)
        }
        vk.allocateDescriptorSets(device, allocInfo, ::descriptorSet).check()

        /*  Update the descriptor set determining the shader binding points
            For every binding point used in a shader there needs to be one descriptor set matching that binding point   */

        val writeDescriptorSet = vk.WriteDescriptorSet(1) {
            // Binding 0 : Uniform buffer
            type = VkStructureType.WRITE_DESCRIPTOR_SET
            dstSet = descriptorSet
            descriptorType = VkDescriptorType.UNIFORM_BUFFER
            bufferInfo = uniformBufferVS.descriptor
            // Binds this uniform buffer to binding point 0
            dstBinding = 0
        }

        vk.updateDescriptorSets(device, writeDescriptorSet)
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
        vk.createImage(device, image, depthStencil::image)

        // Allocate memory for the image (device local) and bind it to our image
        val memAlloc = vk.MemoryAllocateInfo {
            val memReqs = vk.MemoryRequirements { vkGetImageMemoryRequirements(device, depthStencil.image, this) }
            allocationSize = memReqs.size
            memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT.i)
        }
        vk.allocateMemory(device, memAlloc, depthStencil::mem).check()
        VK_CHECK_RESULT(vkBindImageMemory(device, depthStencil.image, depthStencil.mem, 0))

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
        vk.createImageView(device, depthStencilView, depthStencil::view).check()
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
                //it.size(size, 1) TODO
                width = w
                height = h
                layers = 1
            }
            // Create the framebuffer
            vk.createFramebuffer(device, frameBufferCreateInfo, frameBuffers, i).check()
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
        val attachments = vk.AttachmentDescription(2)

        // Color attachment
        attachments[0].apply {
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
        attachments[1].apply {
            format = depthFormat                                            // A proper depth format is selected in the example base
            samples = VkSampleCount.`1_BIT`
            loadOp = VkAttachmentLoadOp.CLEAR                               // Clear depth at start of first subpass
            storeOp = VkAttachmentStoreOp.DONT_CARE                         // We don't need depth after render pass has finished (DONT_CARE may result in better performance)
            stencilLoadOp = VkAttachmentLoadOp.DONT_CARE                    // No stencil
            stencilStoreOp = VkAttachmentStoreOp.DONT_CARE                  // No Stencil
            initialLayout = VkImageLayout.UNDEFINED                         // Layout at render pass start. Initial doesn't matter, so we use undefined
            finalLayout = VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL    // Transition to depth/stencil attachment
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
        val subpassDescription = vk.SubpassDescription(1) {
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
        val dependencies = vk.SubpassDependency(2)

        /*  First dependency at the start of the renderpass
            Does the transition from final to initial layout         */
        dependencies[0].apply {
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
        dependencies[1].apply {
            srcSubpass = 0                      // Producer of the dependency is our single subpass
            dstSubpass = VK_SUBPASS_EXTERNAL    // Consumer are all commands outside of the renderpass
            srcStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
            dstStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
            srcAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
            dstAccessMask = VkAccess.MEMORY_READ_BIT.i
            dependencyFlags = VkDependency.BY_REGION_BIT.i
        }

        // Create the actual renderpass
        val renderPassInfo = vk.RenderPassCreateInfo {
            this.attachments = attachments      // Descriptions of the attachments used by the render pass
            subpasses = subpassDescription      // We only use one subpass in this example, Description of that subpass
            this.dependencies = dependencies    // Subpass dependencies used by the render pass
        }

        vk.createRenderPass(device, renderPassInfo, ::renderPass).check()
    }

    /** Vulkan loads its shaders from an immediate binary representation called SPIR-V
     *  Shaders are compiled offline from e.g. GLSL using the reference glslang compiler
     *  This function loads such a shader from a binary file and returns a shader module structure  */
    fun loadSPIRVShader(filename: String): VkShaderModule = withStack {

        val file = File(ClassLoader.getSystemResource(filename).toURI())

        var shaderModule = NULL

        if (file.exists() && file.canRead()) {

            file.readBytes().toBuffer().use { shaderCode ->
                // Create a new shader module that will be used for pipeline creation
                val moduleCreateInfo = vk.ShaderModuleCreateInfo { code = shaderCode }

                shaderModule = getLong { vk.createShaderModule(device, moduleCreateInfo, it).check() }
            }
        } else
            System.err.println("Error: Could not open shader file \"$filename\"")

        return shaderModule
    }

    fun preparePipelines() {
        /*  Create the graphics pipeline used in this example
            Vulkan uses the concept of rendering pipelines to encapsulate fixed states, replacing OpenGL's complex state machine
            A pipeline is then stored and hashed on the GPU making pipeline changes very fast
            Note: There are still a few dynamic states that are not directly part of the pipeline (but the info that they are used is)  */

        val pipelineCreateInfo = vk.GraphicsPipelineCreateInfo(1) {
            // The layout used for this pipeline (can be shared among multiple pipelines using the same layout)
            layout = pipelineLayout
            // Renderpass this pipeline is attached to
            renderPass = this@Triangle.renderPass
        }

        /*  Construct the different states making up the pipeline

            Input assembly state describes how primitives are assembled
            This pipeline will assemble vertex data as a triangle lists (though we only use one triangle)   */
        val inputAssemblyState = vk.PipelineInputAssemblyStateCreateInfo {
            topology = VkPrimitiveTopology.TRIANGLE_LIST
        }

        // Rasterization state
        val rasterizationState = vk.PipelineRasterizationStateCreateInfo {
            polygonMode = VkPolygonMode.FILL
            cullMode = VkCullMode.NONE.i
            frontFace = VkFrontFace.COUNTER_CLOCKWISE
            depthClampEnable = false
            rasterizerDiscardEnable = false
            depthBiasEnable = false
            lineWidth = 1f
        }

        /*  Color blend state describes how blend factors are calculated (if used)
            We need one blend attachment state per color attachment (even if blending is not used         */
        val blendAttachmentState = vk.PipelineColorBlendAttachmentState(1) {
            colorWriteMask = 0xf
            blendEnable = false
        }
        val colorBlendState = vk.PipelineColorBlendStateCreateInfo { attachments = blendAttachmentState }

        /*  Viewport state sets the number of viewports and scissor used in this pipeline
            Note: This is actually overriden by the dynamic states (see below)         */
        val viewportState = vk.PipelineViewportStateCreateInfo {
            viewportCount = 1
            scissorCount = 1
        }

        /** Enable dynamic states
         *  Most states are baked into the pipeline, but there are still a few dynamic states that can be changed within a command buffer
         *  To be able to change these we need do specify which dynamic states will be changed using this pipeline.
         *  Their actual states are set later on in the command buffer.
         *  For this example we will set the viewport and scissor using dynamic states  */
        val dynamicStateEnables = appBuffer.intBufferOf(VkDynamicState.VIEWPORT.i, VkDynamicState.SCISSOR.i)
        val dynamicState = vk.PipelineDynamicStateCreateInfo { dynamicStates = dynamicStateEnables }

        /*  Depth and stencil state containing depth and stencil compare and test operations
            We only use depth tests and want depth tests and writes to be enabled and compare with less or equal         */
        val depthStencilState = vk.PipelineDepthStencilStateCreateInfo {
            depthTestEnable = true
            depthWriteEnable = true
            depthCompareOp = VkCompareOp.LESS_OR_EQUAL
            depthBoundsTestEnable = false
            back.apply {
                failOp = VkStencilOp.KEEP
                passOp = VkStencilOp.KEEP
                compareOp = VkCompareOp.ALWAYS
            }
            stencilTestEnable = false
            front = back
        }

        /*  Multi sampling state
            This example does not make use fo multi sampling (for anti-aliasing), the state must still be set and passed to the pipeline         */
        val multisampleState = vk.PipelineMultisampleStateCreateInfo {
            rasterizationSamples = VkSampleCount.`1_BIT`
            sampleMask = null
        }

        /*  Vertex input descriptions
            Specifies the vertex input parameters for a pipeline

            Vertex input binding
            This example uses a single vertex input binding at binding point 0 (see vkCmdBindVertexBuffers) */
        val vertexInputBinding = vk.VertexInputBindingDescription(1) {
            binding = 0
            stride = Vertex.size
            inputRate = VkVertexInputRate.VERTEX
        }

        // Inpute attribute bindings describe shader attribute locations and memory layouts
        val vertexInputAttributs = vk.VertexInputAttributeDescription(2)
        /*  These match the following shader layout (see triangle.vert):
            layout (location = 0) in vec3 inPos;
            layout (location = 1) in vec3 inColor;  */

        vertexInputAttributs[0].apply {
            // Attribute location 0: Position
            binding = 0
            location = 0
            // Position attribute is three 32 bit signed (SFLOAT) floats (R32 G32 B32)
            format = VkFormat.R32G32B32_SFLOAT
            offset = Vertex.offsetPosition
        }
        vertexInputAttributs[1].apply {
            // Attribute location 1: Color
            binding = 0
            location = 1
            // Color attribute is three 32 bit signed (SFLOAT) floats (R32 G32 B32)
            format = VkFormat.R32G32B32_SFLOAT
            offset = Vertex.offsetColor
        }

        // Vertex input state used for pipeline creation
        val vertexInputState = vk.PipelineVertexInputStateCreateInfo {
            vertexBindingDescriptions = vertexInputBinding
            vertexAttributeDescriptions = vertexInputAttributs
        }

        // Shaders
        val shaderStages = vk.PipelineShaderStageCreateInfo(2)

        // Vertex shader
        shaderStages[0].apply {
            type = VkStructureType.PIPELINE_SHADER_STAGE_CREATE_INFO
            // Set pipeline stage for this shader
            stage = VkShaderStage.VERTEX_BIT
            // Load binary SPIR-V shader
            module = loadSPIRVShader("shaders/triangle/triangle.vert.spv")
            // Main entry point for the shader
            name = "main"
            assert(module != NULL)
        }

        // Fragment shader
        shaderStages[1].apply {
            type = VkStructureType.PIPELINE_SHADER_STAGE_CREATE_INFO
            // Set pipeline stage for this shader
            stage = VkShaderStage.FRAGMENT_BIT
            // Load binary SPIR-V shader
            module = loadSPIRVShader("shaders/triangle/triangle.frag.spv")
            // Main entry point for the shader
            name = "main"
            assert(module != NULL)
        }

        // Set pipeline shader stage info
        pipelineCreateInfo[0].also {
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
        vk.createGraphicsPipelines(device, pipelineCache, pipelineCreateInfo, ::pipeline).check()

        // Shader modules are no longer needed once the graphics pipeline has been created
        vk.destroyShaderModules(device, shaderStages)
    }

    fun prepareUniformBuffers() {
        /*  Prepare and initialize a uniform buffer block containing shader uniforms
            Single uniforms like in OpenGL are no longer present in Vulkan. All Shader uniforms are passed
            via uniform buffer blocks         */
        val memReqs = vk.MemoryRequirements {}

        // Vertex shader uniform buffer block
        val allocInfo = vk.MemoryAllocateInfo {
            allocationSize = 0
            memoryTypeIndex = 0
        }

        val bufferInfo = vk.BufferCreateInfo {
            size = uboVS.size
            // This buffer will be used as a uniform buffer
            usage = VkBufferUsage.UNIFORM_BUFFER_BIT.i
        }

        // Create a new buffer
        vk.createBuffer(device, bufferInfo, uniformBufferVS::buffer).check()
        // Get memory requirements including size, alignment and memory type
        vkGetBufferMemoryRequirements(device, uniformBufferVS.buffer, memReqs)
        allocInfo.allocationSize = memReqs.size
        /*  Get the memory type index that supports host visibile memory access
            Most implementations offer multiple memory types and selecting the correct one to allocate memory from is crucial
            We also want the buffer to be host coherent so we don't have to flush (or sync after every update.
            Note: This may affect performance so you might not want to do this in a real world application that updates
            buffers on a regular base   */
        allocInfo.memoryTypeIndex = getMemoryTypeIndex(memReqs.memoryTypeBits, memoryPropertiesFlags)
        // Allocate memory for the uniform buffer
        vk.allocateMemory(device, allocInfo, uniformBufferVS::memory).check()
        // Bind memory to buffer
        VK_CHECK_RESULT(vkBindBufferMemory(device, uniformBufferVS.buffer, uniformBufferVS.memory, 0))

        // Store information in the uniform's descriptor that is used by the descriptor set
        uniformBufferVS.descriptor.apply {
            buffer = uniformBufferVS.buffer
            offset = 0
            range = uboVS.size
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
        val pData = appBuffer.pointer
        nvkMapMemory(device, uniformBufferVS.memory, 0, uboVS.size, 0, pData)
        val buffer = MemoryUtil.memByteBuffer(memGetAddress(pData), Mat4.size * 3)
        uboVS.apply {
            projectionMatrix to buffer
            modelMatrix.to(buffer, Mat4.size)
            viewMatrix.to(buffer, Mat4.size * 2)
        }
        /*  Unmap after data has been copied
            Note: Since we requested a host coherent memory type for the uniform buffer, the write is instantly visible to the GPU         */
        vkUnmapMemory(device, uniformBufferVS.memory)
    }

    override fun prepare() {
        super.prepare()
        prepareSynchronizationPrimitives()
        prepareVertices(USE_STAGING)
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

    override fun viewChanged() {
        // This function is called by the base example class each time the view is changed by user input
        updateUniformBuffers()
    }
}