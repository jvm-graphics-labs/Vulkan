package vulkan.basics

import glm_.*
import glm_.buffer.bufferBig
import glm_.buffer.free
import glm_.func.rad
import glm_.mat4x4.Mat4
import glm_.vec3.Vec3
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.VK10.*
import uno.buffer.floatBufferOf
import uno.buffer.intBufferOf
import uno.buffer.toBuffer
import uno.glfw.glfw
import uno.kotlin.buffers.capacity
import vkk.*
import vkk.LongArrayList.resize
import vulkan.USE_STAGING
import vulkan.assetPath
import vulkan.base.VulkanExampleBase
import vulkan.base.tools.DEFAULT_FENCE_TIMEOUT
import java.io.File
import java.nio.ByteBuffer


fun main(args: Array<String>) {
    TriangleVerbose().apply {
        setupWindow()
        initVulkan()
        prepare()
        renderLoop()
        destroy()
    }
}

private class TriangleVerbose : VulkanExampleBase() {

    init {
        zoom = -2.5f
        title = "Vulkan Example - Basic indexed triangle (verbose)"
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
        lateinit var descriptor: VkDescriptorBufferInfo.Buffer
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
    object uboVS {

        var projectionMatrix = Mat4()
        var modelMatrix = Mat4()
        var viewMatrix = Mat4()

        fun pack() {
            projectionMatrix to buffer
            modelMatrix.to(buffer, Mat4.size)
            viewMatrix.to(buffer, Mat4.size * 2)
        }

        val size = Mat4.size * 3
        val buffer = bufferBig(size)
        val address = MemoryUtil.memAddress(buffer)
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

    override fun destroy() {

        window.destroy()
        window.onWindowClosed()
        glfw.terminate()

        /*  Clean up used Vulkan resources
            Note: Inherited destructor cleans up resources stored in base class         */

        vkDestroyPipeline(device, pipeline, null)

        vkDestroyPipelineLayout(device, pipelineLayout, null)
        vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null)

        vkDestroyBuffer(device, vertices.buffer, null)
        vkFreeMemory(device, vertices.memory, null)

        vkDestroyBuffer(device, indices.buffer, null)
        vkFreeMemory(device, indices.memory, null)

        vkDestroyBuffer(device, uniformBufferVS.buffer, null)
        vkFreeMemory(device, uniformBufferVS.memory, null)

        vkDestroySemaphore(device, presentCompleteSemaphore, null)
        vkDestroySemaphore(device, renderCompleteSemaphore, null)

        for (fence in waitFences)
            vkDestroyFence(device, fence, null)

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
        for (i in 0 until deviceMemoryProperties.memoryTypeCount()) {
            if ((typeBits and 1) == 1 && (deviceMemoryProperties.memoryTypes(i).propertyFlags() and properties) == properties)
                return i
            typeBits = typeBits ushr 1
        }
        throw Error("Could not find a suitable memory type!")
    }

    /** Create the Vulkan synchronization primitives used in this example   */
    fun prepareSynchronizationPrimitives() {
        // Semaphores (Used for correct command ordering)
        val semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
                .pNext(NULL)

        // Semaphore used to ensures that image presentation is complete before starting to submit again
        val pPresentCompleteSemaphore = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkCreateSemaphore(device, semaphoreCreateInfo, null, pPresentCompleteSemaphore))
        presentCompleteSemaphore = pPresentCompleteSemaphore[0]

        // Semaphore used to ensures that all commands submitted have been finished before submitting the image to the queue
        val pRenderCompleteSemaphore = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkCreateSemaphore(device, semaphoreCreateInfo, null, pRenderCompleteSemaphore))
        renderCompleteSemaphore = pRenderCompleteSemaphore[0]

        // Fences (Used to check draw command buffer completion)
        val fenceCreateInfo = VkFenceCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .pNext(NULL)
                // Create in signaled state so we don't wait on first render of each command buffer
                .flags(VK_FENCE_CREATE_SIGNALED_BIT)
        val pFence = MemoryUtil.memAllocLong(1)
        for (i in drawCmdBuffers.indices) {
            VK_CHECK_RESULT(vkCreateFence(device, fenceCreateInfo, null, pFence))
            waitFences += pFence[0]
        }

        semaphoreCreateInfo.free()
        pPresentCompleteSemaphore.free()
        pRenderCompleteSemaphore.free()
        fenceCreateInfo.free()
        pFence.free()
    }

    /** Get a new command buffer from the command pool
     *  If begin is true, the command buffer is also started so we can start adding commands    */
    fun getCommandBuffer(begin: Boolean): VkCommandBuffer {

        val cmdBufAllocateInfo = VkCommandBufferAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .pNext(NULL)
                .commandPool(cmdPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1)
        val pCmdBuffer = MemoryUtil.memAllocPointer(1)
        VK_CHECK_RESULT(vkAllocateCommandBuffers(device, cmdBufAllocateInfo, pCmdBuffer))
        val cmdBuffer = VkCommandBuffer(pCmdBuffer[0], device)

        // If requested, also start the new command buffer
        if (begin) {
            val cmdBufInfo = VkCommandBufferBeginInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .pNext(NULL)
            VK_CHECK_RESULT(vkBeginCommandBuffer(cmdBuffer, cmdBufInfo))
            cmdBufInfo.free()
        }

        cmdBufAllocateInfo.free()
        pCmdBuffer.free()

        return cmdBuffer
    }

    /** End the command buffer and submit it to the queue
     *  Uses a fence to ensure command buffer has finished executing before deleting it */
    fun flushCommandBuffer(commandBuffer: VkCommandBuffer) {

        assert(commandBuffer.adr != NULL)

        VK_CHECK_RESULT(vkEndCommandBuffer(commandBuffer))

        val pCommandBuffer = MemoryUtil.memAllocPointer(1)
        pCommandBuffer[0] = commandBuffer
        val submitInfo = VkSubmitInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pNext(NULL)
                .pCommandBuffers(pCommandBuffer)

        // Create fence to ensure that the command buffer has finished executing
        val fenceCreateInfo = VkFenceCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .pNext(NULL)
                .flags(0)
        val pFence = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkCreateFence(device, fenceCreateInfo, null, pFence))
        val fence: VkFence = pFence[0]

        // Submit to the queue
        VK_CHECK_RESULT(vkQueueSubmit(queue, submitInfo, fence))
        // Wait for the fence to signal that command buffer has finished executing
        VK_CHECK_RESULT(vkWaitForFences(device, fence, true, DEFAULT_FENCE_TIMEOUT))

        vkDestroyFence(device, fence, null)
        vkFreeCommandBuffers(device, cmdPool, commandBuffer)

        pCommandBuffer.free()
        pFence.free()
    }

    /** Build separate command buffers for every framebuffer image
     *  Unlike in OpenGL all rendering commands are recorded once into command buffers that are then resubmitted to the queue
     *  This allows to generate work upfront and from multiple threads, one of the biggest advantages of Vulkan */
    override fun buildCommandBuffers() {

        val cmdBufInfo = VkCommandBufferBeginInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .pNext(NULL)

        // Set clear values for all framebuffer attachments with loadOp set to clear
        // We use two attachments (color and depth) that are cleared at the start of the subpass and as such we need to set clear values for both
        val clearValues = VkClearValue.calloc(2)
        clearValues[0].color
                .float32(0, 0f)
                .float32(1, 0f)
                .float32(2, 0.2f)
                .float32(3, 1f)
        clearValues[1].depthStencil
                .depth(1f)
                .stencil(0)

        val renderPassBeginInfo = VkRenderPassBeginInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .pNext(NULL)
                .renderPass(renderPass)
        renderPassBeginInfo.renderArea().apply {
            offset()
                    .x(0)
                    .y(0)
            extent()
                    .width(size.x)
                    .height(size.y)
        }
        renderPassBeginInfo.pClearValues(clearValues)

        for (i in drawCmdBuffers.indices) {

            // Set target frame buffer
            renderPassBeginInfo.framebuffer(frameBuffers[i])

            VK_CHECK_RESULT(vkBeginCommandBuffer(drawCmdBuffers[i], cmdBufInfo))

            // Start the first sub pass specified in our default render pass setup by the base class
            // This will clear the color and depth attachment
            vkCmdBeginRenderPass(drawCmdBuffers[i], renderPassBeginInfo, VK_SUBPASS_CONTENTS_INLINE)

            // Update dynamic viewport state
            val viewport = VkViewport.calloc(1)
                    .width(size.x.f)
                    .height(size.y.f)
                    .minDepth(0f)
                    .maxDepth(1f)
            vkCmdSetViewport(drawCmdBuffers[i], 0, viewport)

            // Update dynamic scissor state
            val scissor = VkRect2D.calloc(1).apply {
                extent()
                        .width(size.x)
                        .height(size.y)
                offset()
                        .x(0)
                        .y(0)
            }
            vkCmdSetScissor(drawCmdBuffers[i], 0, scissor)

            val pDescriptorSet = MemoryUtil.memAllocLong(1)
            pDescriptorSet[0] = descriptorSet
            // Bind descriptor sets describing shader binding points
            vkCmdBindDescriptorSets(drawCmdBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0, pDescriptorSet, null)

            // Bind the rendering pipeline
            // The pipeline (state object) contains all states of the rendering pipeline, binding it will set all the states specified at pipeline creation time
            vkCmdBindPipeline(drawCmdBuffers[i], VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline)

            // Bind triangle vertex buffer (contains position and colors)
            val offset: VkDeviceSize = 0
            val pBuffer = MemoryUtil.memAllocLong(1)
            pBuffer[0] = vertices.buffer
            val pOffset = MemoryUtil.memAllocLong(1)
            pOffset[0] = offset
            vkCmdBindVertexBuffers(drawCmdBuffers[i], 0, pBuffer, pOffset)

            // Bind triangle index buffer
            vkCmdBindIndexBuffer(drawCmdBuffers[i], indices.buffer, 0, VK_INDEX_TYPE_UINT32)

            // Draw indexed triangle
            vkCmdDrawIndexed(drawCmdBuffers[i], indices.count, 1, 0, 0, 1)

            vkCmdEndRenderPass(drawCmdBuffers[i])

            // Ending the render pass will add an implicit barrier transitioning the frame buffer color attachment to
            // VK_IMAGE_LAYOUT_PRESENT_SRC_KHR for presenting it to the windowing system

            VK_CHECK_RESULT(vkEndCommandBuffer(drawCmdBuffers[i]))

            viewport.free()
            scissor.free()
            pDescriptorSet.free()
            pBuffer.free()
            pOffset.free()
        }
        cmdBufInfo.free()
        clearValues.free()
        renderPassBeginInfo.free()
    }

    fun draw() {
        // Get next image in the swap chain (back/front buffer)
        swapChain.acquireNextImage(presentCompleteSemaphore, ::currentBuffer).check()

        // Use a fence to wait until the command buffer has finished execution before using it again
        VK_CHECK_RESULT(vkWaitForFences(device, waitFences[currentBuffer], true, UINT64_MAX))
        VK_CHECK_RESULT(vkResetFences(device, waitFences[currentBuffer]))

        // Pipeline stage at which the queue submission will wait (via pWaitSemaphores)
        val waitStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
        val pWaitStageMask = MemoryUtil.memAllocInt(1)
        pWaitStageMask[0] = waitStageMask
        val pWaitSemaphore = MemoryUtil.memAllocLong(1)
        pWaitSemaphore[0] = presentCompleteSemaphore
        val pSignalSemaphore = MemoryUtil.memAllocLong(1)
        pSignalSemaphore[0] = renderCompleteSemaphore
        val pCommandBuffer = MemoryUtil.memAllocPointer(1)
        pCommandBuffer[0] = drawCmdBuffers[currentBuffer]
        // The submit info structure specifices a command buffer queue submission batch
        val submitInfo = VkSubmitInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pNext(NULL)
                // Pointer to the list of pipeline stages that the semaphore waits will occur at
                .pWaitDstStageMask(pWaitStageMask)
                // Semaphore(s) to wait upon before the submitted command buffer starts executing
                .pWaitSemaphores(pWaitSemaphore)
                // One wait semaphore
                .waitSemaphoreCount(1)
                // Semaphore(s) to be signaled when command buffers have completed
                .pSignalSemaphores(pSignalSemaphore)
                // One signal semaphore + Command buffers(s) to execute in this batch (submission)
                .pCommandBuffers(pCommandBuffer)

        // Submit to the graphics queue passing a wait fence
        queue.submit(submitInfo, waitFences[currentBuffer])

        /*  Present the current buffer to the swap chain
            Pass the semaphore signaled by the command buffer submission from the submit info as the wait semaphore
            for swap chain presentation
            This ensures that the image is not presented to the windowing system until all commands have been submitted */
        swapChain.queuePresent(queue, currentBuffer, renderCompleteSemaphore)

        pWaitStageMask.free()
        pWaitSemaphore.free()
        pSignalSemaphore.free()
        pCommandBuffer.free()
        submitInfo.free()
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
        val vertexBuffer = floatBufferOf(
                // position    color
                +1f, +1f, +0f, 1f, 0f, 0f,
                -1f, +1f, +0f, 0f, 1f, 0f,
                +0f, -1f, +0f, 0f, 0f, 1f)

        val vertexBufferSize = vertexBuffer.size.L

        // Setup indices
        val indexBuffer = intBufferOf(0, 1, 2)
        indices.count = indexBuffer.capacity
        val indexBufferSize = indexBuffer.size.L

        val memAlloc = VkMemoryAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .pNext(NULL)
        val memReqs = VkMemoryRequirements.calloc()

        val memoryPropertiesFlags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        val pData = MemoryUtil.memAllocPointer(1)

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
            val vertexBufferInfo = VkBufferCreateInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .pNext(NULL)
                    .size(vertexBufferSize)
                    // Buffer is used as the copy source
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
            // Create a host-visible buffer to copy the vertex data to (staging buffer)
            val pBuffer = MemoryUtil.memAllocLong(1)
            VK_CHECK_RESULT(vkCreateBuffer(device, vertexBufferInfo, null, pBuffer))
            stagingBuffers.vertices.buffer = pBuffer[0]
            vkGetBufferMemoryRequirements(device, stagingBuffers.vertices.buffer, memReqs)
            memAlloc.allocationSize(memReqs.size())
            // Request a host visible memory type that can be used to copy our data do
            // Also request it to be coherent, so that writes are visible to the GPU right after unmapping the buffer
            memAlloc.memoryTypeIndex(getMemoryTypeIndex(memReqs.memoryTypeBits(), memoryPropertiesFlags))
            val pMemory = MemoryUtil.memAllocLong(1)
            VK_CHECK_RESULT(vkAllocateMemory(device, memAlloc, null, pMemory))
            stagingBuffers.vertices.memory = pMemory[0]
            // Map and copy
            VK_CHECK_RESULT(vkMapMemory(device, stagingBuffers.vertices.memory, 0, memAlloc.allocationSize(), 0, pData))
            MemoryUtil.memCopy(MemoryUtil.memAddress(vertexBuffer), pData[0], vertexBufferSize)
            vkUnmapMemory(device, stagingBuffers.vertices.memory)
            VK_CHECK_RESULT(vkBindBufferMemory(device, stagingBuffers.vertices.buffer, stagingBuffers.vertices.memory, 0))

            // Create a device local buffer to which the (host local) vertex data will be copied and which will be used for rendering
            vertexBufferInfo.usage(VkBufferUsage.VERTEX_BUFFER_BIT or VkBufferUsage.TRANSFER_DST_BIT)
            VK_CHECK_RESULT(vkCreateBuffer(device, vertexBufferInfo, null, pBuffer))
            vertices.buffer = pBuffer[0]
            vkGetBufferMemoryRequirements(device, vertices.buffer, memReqs)
            memAlloc.allocationSize(memReqs.size())
            memAlloc.memoryTypeIndex(getMemoryTypeIndex(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT))
            VK_CHECK_RESULT(vkAllocateMemory(device, memAlloc, null, pMemory))
            vertices.memory = pMemory[0]
            VK_CHECK_RESULT(vkBindBufferMemory(device, vertices.buffer, vertices.memory, 0))

            // Index buffer
            val indexbufferInfo = VkBufferCreateInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .pNext(NULL)
                    .size(indexBufferSize)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
            // Copy index data to a buffer visible to the host (staging buffer)
            VK_CHECK_RESULT(vkCreateBuffer(device, indexbufferInfo, null, pBuffer))
            stagingBuffers.indices.buffer = pBuffer[0]
            vkGetBufferMemoryRequirements(device, stagingBuffers.indices.buffer, memReqs)
            memAlloc.allocationSize(memReqs.size())
            memAlloc.memoryTypeIndex(getMemoryTypeIndex(memReqs.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT))
            VK_CHECK_RESULT(vkAllocateMemory(device, memAlloc, null, pMemory))
            stagingBuffers.indices.memory = pMemory[0]
            VK_CHECK_RESULT(vkMapMemory(device, stagingBuffers.indices.memory, 0, indexBufferSize, 0, pData))
            MemoryUtil.memCopy(MemoryUtil.memAddress(indexBuffer), pData[0], indexBufferSize)
            vkUnmapMemory(device, stagingBuffers.indices.memory)
            VK_CHECK_RESULT(vkBindBufferMemory(device, stagingBuffers.indices.buffer, stagingBuffers.indices.memory, 0))

            // Create destination buffer with device only visibility
            indexbufferInfo.usage(VK_BUFFER_USAGE_INDEX_BUFFER_BIT or VK_BUFFER_USAGE_TRANSFER_DST_BIT)
            VK_CHECK_RESULT(vkCreateBuffer(device, indexbufferInfo, null, pBuffer))
            indices.buffer = pBuffer[0]
            vkGetBufferMemoryRequirements(device, indices.buffer, memReqs)
            memAlloc.allocationSize(memReqs.size())
            memAlloc.memoryTypeIndex(getMemoryTypeIndex(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT))
            VK_CHECK_RESULT(vkAllocateMemory(device, memAlloc, null, pMemory))
            indices.memory = pMemory[0]
            VK_CHECK_RESULT(vkBindBufferMemory(device, indices.buffer, indices.memory, 0))

            /*  Buffer copies have to be submitted to a queue, so we need a command buffer for them
                Note: Some devices offer a dedicated transfer queue (with only the transfer bit set) that may be faster
                when doing lots of copies             */
            val copyCmd = getCommandBuffer(true)

            // Put buffer region copies into command buffer
            val copyRegion = VkBufferCopy.calloc(1)

            // Vertex buffer
            copyRegion.size(vertexBufferSize)
            vkCmdCopyBuffer(copyCmd, stagingBuffers.vertices.buffer, vertices.buffer, copyRegion)
            // Index buffer
            copyRegion.size(indexBufferSize)
            vkCmdCopyBuffer(copyCmd, stagingBuffers.indices.buffer, indices.buffer, copyRegion)


            // Flushing the command buffer will also submit it to the queue and uses a fence to ensure that all commands have been executed before returning
            flushCommandBuffer(copyCmd)

            // Destroy staging buffers
            // Note: Staging buffer must not be deleted before the copies have been submitted and executed
            vkDestroyBuffer(device, stagingBuffers.vertices.buffer, null)
            vkFreeMemory(device, stagingBuffers.vertices.memory, null)
            vkDestroyBuffer(device, stagingBuffers.indices.buffer, null)
            vkFreeMemory(device, stagingBuffers.indices.memory, null)

            vertexBufferInfo.free()
            pBuffer.free()
            pMemory.free()

        } else {

            /*  Don't use staging
                Create host-visible buffers only and use these for rendering. This is not advised and will usually
                result in lower rendering performance             */

            // Vertex buffer
            val vertexBufferInfo = VkBufferCreateInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .pNext(NULL)
                    .size(vertexBufferSize)
                    .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)

            // Copy vertex data to a buffer visible to the host
            val pBuffer = MemoryUtil.memAllocLong(1)
            VK_CHECK_RESULT(vkCreateBuffer(device, vertexBufferInfo, null, pBuffer))
            vertices.buffer = pBuffer[0]
            vkGetBufferMemoryRequirements(device, vertices.buffer, memReqs)
            memAlloc.allocationSize(memReqs.size())
            // VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT is host visible memory, and VK_MEMORY_PROPERTY_HOST_COHERENT_BIT makes sure writes are directly visible
            memAlloc.memoryTypeIndex(getMemoryTypeIndex(memReqs.memoryTypeBits(), memoryPropertiesFlags))
            val pMemory = MemoryUtil.memAllocLong(1)
            VK_CHECK_RESULT(vkAllocateMemory(device, memAlloc, null, pMemory))
            vertices.memory = pMemory[0]
            VK_CHECK_RESULT(vkMapMemory(device, vertices.memory, 0, memAlloc.allocationSize(), 0, pData))
            MemoryUtil.memCopy(MemoryUtil.memAddress(vertexBuffer), pData[0], vertexBufferSize)
            vkUnmapMemory(device, vertices.memory)
            VK_CHECK_RESULT(vkBindBufferMemory(device, vertices.buffer, vertices.memory, 0))

            // Index buffer
            val indexBufferInfo = VkBufferCreateInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .pNext(NULL)
                    .size(indexBufferSize)
                    .usage(VK_BUFFER_USAGE_INDEX_BUFFER_BIT)

            // Copy index data to a buffer visible to the host
            VK_CHECK_RESULT(vkCreateBuffer(device, indexBufferInfo, null, pBuffer))
            indices.buffer = pBuffer[0]
            vkGetBufferMemoryRequirements(device, indices.buffer, memReqs)
            memAlloc.allocationSize(memReqs.size())
            memAlloc.memoryTypeIndex(getMemoryTypeIndex(memReqs.memoryTypeBits(), memoryPropertiesFlags))
            VK_CHECK_RESULT(vkAllocateMemory(device, memAlloc, null, pMemory))
            indices.memory = pMemory[0]
            VK_CHECK_RESULT(vkMapMemory(device, indices.memory, 0, indexBufferSize, 0, pData))
            MemoryUtil.memCopy(MemoryUtil.memAddress(indexBuffer), pData[0], indexBufferSize)
            vkUnmapMemory(device, indices.memory)
            VK_CHECK_RESULT(vkBindBufferMemory(device, indices.buffer, indices.memory, 0))

            vertexBufferInfo.free()
            pBuffer.free()
            pMemory.free()
        }
        vertexBuffer.free()
        indexBuffer.free()
        memAlloc.free()
        memReqs.free()
        pData.free()
    }

    fun setupDescriptorPool() {
        // We need to tell the API the number of max. requested descriptors per type
        val typeCounts = VkDescriptorPoolSize.calloc(1)
                // This example only uses one descriptor type (uniform buffer) and only requests one descriptor of this type
                .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1)
        // For additional types you need to add new entries in the type count list
        // E.g. for two combined image samplers :
        // typeCounts[1].type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        // typeCounts[1].descriptorCount = 2;

        // Create the global descriptor pool
        // All descriptors used in this example are allocated from this pool
        val descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .pNext(NULL)
                .pPoolSizes(typeCounts)
                // Set the max. number of descriptor sets that can be requested from this pool (requesting beyond this limit will result in an error)
                .maxSets(1)

        val pDescriptorPool = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkCreateDescriptorPool(device, descriptorPoolInfo, null, pDescriptorPool))
        descriptorPool = pDescriptorPool[0]

        typeCounts.free()
        descriptorPoolInfo.free()
        pDescriptorPool.free()
    }

    fun setupDescriptorSetLayout() {
        /*  Setup layout of descriptors used in this example
            Basically connects the different shader stages to descriptors for binding uniform buffers, image samplers, etc.
            So every shader binding should map to one descriptor set layout binding */

        // Binding 0: Uniform buffer (Vertex shader)
        val layoutBinding = VkDescriptorSetLayoutBinding.calloc(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1)
                .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                .pImmutableSamplers(null)

        val descriptorLayout = VkDescriptorSetLayoutCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pNext(NULL)
                .pBindings(layoutBinding)

        val pDescriptorSetLayout = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkCreateDescriptorSetLayout(device, descriptorLayout, null, pDescriptorSetLayout))
        descriptorSetLayout = pDescriptorSetLayout[0]

        // Create the pipeline layout that is used to generate the rendering pipelines that are based on this descriptor set layout
        // In a more complex scenario you would have different pipeline layouts for different descriptor set layouts that could be reused
        val pipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pNext(NULL)
                .pSetLayouts(pDescriptorSetLayout)

        val pPipelineLayout = MemoryUtil.memCallocLong(1)
        VK_CHECK_RESULT(vkCreatePipelineLayout(device, pipelineLayoutCreateInfo, null, pPipelineLayout))
        pipelineLayout = pPipelineLayout[0]

        layoutBinding.free()
        descriptorLayout.free()
        pDescriptorSetLayout.free()
        pipelineLayoutCreateInfo.free()
        pPipelineLayout.free()
    }

    fun setupDescriptorSet() {
        // Allocate a new descriptor set from the global descriptor pool
        val pDescriptorSetLayour = MemoryUtil.memAllocLong(1)
        pDescriptorSetLayour[0] = descriptorSetLayout
        val allocInfo = VkDescriptorSetAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(pDescriptorSetLayour)

        val pDescriptorSet = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkAllocateDescriptorSets(device, allocInfo, pDescriptorSet))
        descriptorSet = pDescriptorSet[0]

        /*  Update the descriptor set determining the shader binding points
            For every binding point used in a shader there needs to be one descriptor set matching that binding point   */

        val writeDescriptorSet = VkWriteDescriptorSet.calloc(1)
                // Binding 0 : Uniform buffer
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .pBufferInfo(uniformBufferVS.descriptor)
                // Binds this uniform buffer to binding point 0
                .dstBinding(0)

        vkUpdateDescriptorSets(device, writeDescriptorSet, null)

        pDescriptorSetLayour.free()
        allocInfo.free()
        pDescriptorSet.free()
        writeDescriptorSet.free()
    }

    /** Create the depth (and stencil) buffer attachments used by our framebuffers
     *  Note: Override of virtual function in the base class and called from within VulkanExampleBase::prepare  */
    override fun setupDepthStencil() {
        // Create an optimal image used as the depth stencil attachment
        val image = VkImageCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .pNext(NULL)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(depthFormat.i)
        // Use example's height and width
        image.extent.set(size.x, size.y, 1)
        image
                .mipLevels(1)
                .arrayLayers(1)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT or VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
        val pImage = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkCreateImage(device, image, null, pImage))
        depthStencil.image = pImage[0]

        // Allocate memory for the image (device local) and bind it to our image
        val memAlloc = VkMemoryAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .pNext(NULL)
        val memReqs = VkMemoryRequirements.calloc()
        vkGetImageMemoryRequirements(device, depthStencil.image, memReqs)
        memAlloc.allocationSize(memReqs.size())
        memAlloc.memoryTypeIndex(getMemoryTypeIndex(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT))
        val pMemory = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkAllocateMemory(device, memAlloc, null, pMemory))
        depthStencil.mem = pMemory[0]
        VK_CHECK_RESULT(vkBindImageMemory(device, depthStencil.image, depthStencil.mem, 0))

        /*  Create a view for the depth stencil image
            Images aren't directly accessed in Vulkan, but rather through views described by a subresource range
            This allows for multiple views of one image with differing ranges (e.g. for different layers)   */
        val depthStencilView = VkImageViewCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .pNext(NULL)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(depthFormat.i)
        depthStencilView.subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT or VK_IMAGE_ASPECT_STENCIL_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1)
        depthStencilView.image(depthStencil.image)
        val pImageView = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkCreateImageView(device, depthStencilView, null, pImageView))
        depthStencil.view = pImageView[0]

        image.free()
        pImage.free()
        memAlloc.free()
        memReqs.free()
        pMemory.free()
        depthStencilView.free()
        pImageView.free()
    }

    /** Create a frame buffer for each swap chain image
     *  Note: Override of virtual function in the base class and called from within VulkanExampleBase::prepare  */
    override fun setupFrameBuffer() {
        // Create a frame buffer for every image in the swapchain
        frameBuffers resize swapChain.imageCount
        for (i in frameBuffers.indices) {
            val attachments = MemoryUtil.memAllocLong(2)
            attachments[0] = swapChain.buffers[i].view  // Color attachment is the view of the swapchain image
            attachments[1] = depthStencil.view            // Depth/Stencil attachment is the same for all frame buffers

            val frameBufferCreateInfo = VkFramebufferCreateInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .pNext(NULL)
                    // All frame buffers use the same renderpass setup
                    .renderPass(renderPass)
                    .pAttachments(attachments)
                    .width(size.x)
                    .height(size.y)
                    .layers(1)
            // Create the framebuffer
            val pFramebuffer = MemoryUtil.memAllocLong(1)
            VK_CHECK_RESULT(vkCreateFramebuffer(device, frameBufferCreateInfo, null, pFramebuffer))
            frameBuffers[i] = pFramebuffer[0]

            pFramebuffer.free()
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
        val attachments = VkAttachmentDescription.calloc(2)

        // Color attachment
        attachments[0]
                .format(swapChain.colorFormat.i)                    // Use the color format selected by the swapchain
                .samples(VK_SAMPLE_COUNT_1_BIT)                     // We don't use multi sampling in this example
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)                // Clear this attachment at the start of the render pass
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)              // Keep it's contents after the render pass is finished (for displaying it)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)     // We don't use stencil, so don't care for load
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)   // Same for store
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)           // Layout at render pass start. Initial doesn't matter, so we use undefined
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)       // Layout to which the attachment is transitioned when the render pass is finished

        // As we want to present the color buffer to the swapchain, we transition to PRESENT_KHR
        // Depth attachment
        attachments[1]
                .format(depthFormat.i)                                          // A proper depth format is selected in the example base
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)                            // Clear depth at start of first subpass
                .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)                      // We don't need depth after render pass has finished (DONT_CARE may result in better performance)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)                 // No stencil
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)               // No Stencil
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)                       // Layout at render pass start. Initial doesn't matter, so we use undefined
                .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)  // Transition to depth/stencil attachment

        // Setup attachment references
        val colorReference = VkAttachmentReference.calloc(1)
                .attachment(0)                                // Attachment 0 is color
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)   // Attachment layout used as color during the subpass

        val depthReference = VkAttachmentReference.calloc()
                .attachment(1)                                        // Attachment 1 is color
                .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)   // Attachment used as depth/stemcil used during the subpass

        // Setup a single subpass reference
        val subpassDescription = VkSubpassDescription.calloc(1)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorReference)                   // Reference to the color attachment in slot 0
                .pDepthStencilAttachment(depthReference)             // Reference to the depth attachment in slot 1
                .pInputAttachments(null)                       // (Input attachments not used by this example)
                .pPreserveAttachments(null)                    // (Preserve attachments not used by this example)
                .pResolveAttachments(null)                     // Resolve attachments are resolved at the end of a sub pass and can be used for e.g. multi sampling

        /*  Setup subpass dependencies
            These will add the implicit ttachment layout transitionss specified by the attachment descriptions
            The actual usage layout is preserved through the layout specified in the attachment reference
            Each subpass dependency will introduce a memory and execution dependency between the source and dest subpass described by
            srcStageMask, dstStageMask, srcAccessMask, dstAccessMask (and dependencyFlags is set)
            Note: VK_SUBPASS_EXTERNAL is a special constant that refers to all commands executed outside of the actual renderpass)  */
        val dependencies = VkSubpassDependency.calloc(2)

        /*  First dependency at the start of the renderpass
            Does the transition from final to initial layout         */
        dependencies[0]
                .srcSubpass(VK_SUBPASS_EXTERNAL)     // Producer of the dependency
                .dstSubpass(0)                      // Consumer is our single subpass that will wait for the execution depdendency
                .srcStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)


        /*  Second dependency at the end the renderpass
            Does the transition from the initial to the final layout         */
        dependencies[1]
                .srcSubpass(0)                      // Producer of the dependency is our single subpass
                .dstSubpass(VK_SUBPASS_EXTERNAL)    // Consumer are all commands outside of the renderpass
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT)
                .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT)
                .dependencyFlags(VK_DEPENDENCY_BY_REGION_BIT)

        // Create the actual renderpass
        val renderPassInfo = VkRenderPassCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pNext(NULL)
                .pAttachments(attachments)                                // Descriptions of the attachments used by the render pass
                .pSubpasses(subpassDescription)                                // Description of that subpass
                .pDependencies(dependencies)                                // Subpass dependencies used by the render pass

        val pRenderPass = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkCreateRenderPass(device, renderPassInfo, null, pRenderPass))
        renderPass = pRenderPass[0]

        attachments.free()
        colorReference.free()
        depthReference.free()
        subpassDescription.free()
        dependencies.free()
        renderPassInfo.free()
        pRenderPass.free()
    }

    /** Vulkan loads it's shaders from an immediate binary representation called SPIR-V
     *  Shaders are compiled offline from e.g. GLSL using the reference glslang compiler
     *  This function loads such a shader from a binary file and returns a shader module structure */
    fun loadSPIRVShader(filename: String): VkShaderModule {

        var shaderCode: ByteBuffer? = null

        val file = File(filename)

        if (file.exists() && file.canRead()) {
            val bytes = file.readBytes()
            // Copy file contents into a buffer
            shaderCode = bytes.toBuffer()
            assert(bytes.isNotEmpty())
        }

        if (shaderCode != null) {
            // Create a new shader module that will be used for pipeline creation
            val moduleCreateInfo = VkShaderModuleCreateInfo.calloc()
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pNext(NULL)
                    .pCode(shaderCode)

            val pShaderModule = MemoryUtil.memAllocLong(1)
            VK_CHECK_RESULT(vkCreateShaderModule(device, moduleCreateInfo, null, pShaderModule))
            val shaderModule = pShaderModule[0]

            shaderCode.free()
            moduleCreateInfo.free()
            pShaderModule.free()

            return shaderModule
        } else {
            System.err.println("Error: Could not open shader file \"$filename\"")
            return NULL
        }
    }

    fun preparePipelines() {
        /*  Create the graphics pipeline used in this example
            Vulkan uses the concept of rendering pipelines to encapsulate fixed states, replacing OpenGL's complex state machine
            A pipeline is then stored and hashed on the GPU making pipeline changes very fast
            Note: There are still a few dynamic states that are not directly part of the pipeline (but the info that they are used is)  */

        val pipelineCreateInfo = VkGraphicsPipelineCreateInfo.calloc(1)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pNext(NULL)
                // The layout used for this pipeline (can be shared among multiple pipelines using the same layout)
                .layout(pipelineLayout)
                // Renderpass this pipeline is attached to
                .renderPass(renderPass)

        /*  Construct the different states making up the pipeline

            Input assembly state describes how primitives are assembled
            This pipeline will assemble vertex data as a triangle lists (though we only use one triangle)   */
        val inputAssemblyState = VkPipelineInputAssemblyStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .pNext(NULL)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)

        // Rasterization state
        val rasterizationState = VkPipelineRasterizationStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .pNext(NULL)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .cullMode(VK_CULL_MODE_NONE)
                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .depthBiasEnable(false)
                .lineWidth(1f)

        /*  Color blend state describes how blend factors are calculated (if used)
            We need one blend attachment state per color attachment (even if blending is not used         */
        val blendAttachmentState = VkPipelineColorBlendAttachmentState.calloc(1)
                .colorWriteMask(0xf)
                .blendEnable(false)

        val colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .pNext(NULL)
                .pAttachments(blendAttachmentState)

        /*  Viewport state sets the number of viewports and scissor used in this pipeline
            Note: This is actually overriden by the dynamic states (see below)         */
        val viewportState = VkPipelineViewportStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .pNext(NULL)
                .viewportCount(1)
                .scissorCount(1)

        /** Enable dynamic states
         *  Most states are baked into the pipeline, but there are still a few dynamic states that can be changed within a command buffer
         *  To be able to change these we need do specify which dynamic states will be changed using this pipeline.
         *  Their actual states are set later on in the command buffer.
         *  For this example we will set the viewport and scissor using dynamic states  */
        val dynamicStateEnables = MemoryUtil.memAllocInt(2)
        dynamicStateEnables[0] = VK_DYNAMIC_STATE_VIEWPORT
        dynamicStateEnables[1] = VK_DYNAMIC_STATE_SCISSOR
        val dynamicState = VkPipelineDynamicStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                .pNext(NULL)
                .pDynamicStates(dynamicStateEnables)

        /*  Depth and stencil state containing depth and stencil compare and test operations
            We only use depth tests and want depth tests and writes to be enabled and compare with less or equal         */
        val depthStencilState = VkPipelineDepthStencilStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                .pNext(NULL)
                .depthTestEnable(true)
                .depthWriteEnable(true)
                .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                .depthBoundsTestEnable(false)
        depthStencilState.back()
                .failOp(VK_STENCIL_OP_KEEP)
                .passOp(VK_STENCIL_OP_KEEP)
                .compareOp(VK_COMPARE_OP_ALWAYS)
        depthStencilState
                .stencilTestEnable(false)
                .front(depthStencilState.back())

        /*  Multi sampling state
            This example does not make use fo multi sampling (for anti-aliasing), the state must still be set and passed to the pipeline         */
        val multisampleState = VkPipelineMultisampleStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .pNext(NULL)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)
                .pSampleMask(null)

        /*  Vertex input descriptions
            Specifies the vertex input parameters for a pipeline

            Vertex input binding
            This example uses a single vertex input binding at binding point 0 (see vkCmdBindVertexBuffers) */
        val vertexInputBinding = VkVertexInputBindingDescription.calloc(1)
                .binding(0)
                .stride(Vertex.size)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

        // Inpute attribute bindings describe shader attribute locations and memory layouts
        val vertexInputAttributs = VkVertexInputAttributeDescription.calloc(2)
        /*  These match the following shader layout (see triangle.vert):
            layout (location = 0) in vec3 inPos;
            layout (location = 1) in vec3 inColor;  */
        // Attribute location 0: Position
        vertexInputAttributs[0]
                .binding(0)
                .location(0)
                // Position attribute is three 32 bit signed (SFLOAT) floats (R32 G32 B32)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(Vertex.offsetPosition)
        // Attribute location 1: Color
        vertexInputAttributs[1]
                .binding(0)
                .location(1)
                // Color attribute is three 32 bit signed (SFLOAT) floats (R32 G32 B32)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(Vertex.offsetColor)

        // Vertex input state used for pipeline creation
        val vertexInputState = VkPipelineVertexInputStateCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pNext(NULL)
                .pVertexBindingDescriptions(vertexInputBinding)
                .pVertexAttributeDescriptions(vertexInputAttributs)

        // Shaders
        val shaderStages = VkPipelineShaderStageCreateInfo.calloc(2)

        // Vertex shader
        val pName = MemoryUtil.memUTF8("main")
        shaderStages[0]
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .pNext(NULL)
                // Set pipeline stage for this shader
                .stage(VK_SHADER_STAGE_VERTEX_BIT)
                // Load binary SPIR-V shader
                .module(loadSPIRVShader("$assetPath/shaders/triangle/triangle.vert.spv"))
                // Main entry point for the shader
                .pName(pName)
        assert(shaderStages[0].module() != NULL)

        // Fragment shader
        shaderStages[1]
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .pNext(NULL)
                // Set pipeline stage for this shader
                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                // Load binary SPIR-V shader
                .module(loadSPIRVShader("$assetPath/shaders/triangle/triangle.frag.spv"))
                // Main entry point for the shader
                .pName(pName)
        assert(shaderStages[1].module() != NULL)

        // Set pipeline shader stage info
        pipelineCreateInfo
                .pStages(shaderStages)
                // Assign the pipeline states to the pipeline creation info structure
                .pVertexInputState(vertexInputState)
                .pInputAssemblyState(inputAssemblyState)
                .pRasterizationState(rasterizationState)
                .pColorBlendState(colorBlendState)
                .pMultisampleState(multisampleState)
                .pViewportState(viewportState)
                .pDepthStencilState(depthStencilState)
                .renderPass(renderPass)
                .pDynamicState(dynamicState)

        // Create rendering pipeline using the specified states
        val pPipeline = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkCreateGraphicsPipelines(device, pipelineCache, pipelineCreateInfo, null, pPipeline))
        pipeline = pPipeline[0]

        // Shader modules are no longer needed once the graphics pipeline has been created
        vkDestroyShaderModule(device, shaderStages[0].module, null)
        vkDestroyShaderModule(device, shaderStages[1].module, null)

        pipelineCreateInfo.free()
        inputAssemblyState.free()
        rasterizationState.free()
        blendAttachmentState.free()
        colorBlendState.free()
        viewportState.free()
        dynamicStateEnables.free()
        dynamicState.free()
        depthStencilState.free()
        multisampleState.free()
        vertexInputBinding.free()
        vertexInputAttributs.free()
        vertexInputState.free()
        shaderStages.free()
        pPipeline.free()
    }

    fun prepareUniformBuffers() {
        /*  Prepare and initialize a uniform buffer block containing shader uniforms
            Single uniforms like in OpenGL are no longer present in Vulkan. All Shader uniforms are passed
            via uniform buffer blocks         */
        val memReqs = VkMemoryRequirements.calloc()

        // Vertex shader uniform buffer block
        val allocInfo = VkMemoryAllocateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .pNext(NULL)
                .allocationSize(0)
                .memoryTypeIndex(0)

        val bufferInfo = VkBufferCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .pNext(NULL)
                .size(uboVS.size.L)
                // This buffer will be used as a uniform buffer
                .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)

        // Create a new buffer
        val pBuffer = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkCreateBuffer(device, bufferInfo, null, pBuffer))
        uniformBufferVS.buffer = pBuffer[0]
        // Get memory requirements including size, alignment and memory type
        vkGetBufferMemoryRequirements(device, uniformBufferVS.buffer, memReqs)
        allocInfo.allocationSize(memReqs.size())
        /*  Get the memory type index that supports host visibile memory access
            Most implementations offer multiple memory types and selecting the correct one to allocate memory from is crucial
            We also want the buffer to be host coherent so we don't have to flush (or sync after every update.
            Note: This may affect performance so you might not want to do this in a real world application that updates
            buffers on a regular base   */
        allocInfo.memoryTypeIndex(getMemoryTypeIndex(memReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT))
        // Allocate memory for the uniform buffer
        val pMemory = MemoryUtil.memAllocLong(1)
        VK_CHECK_RESULT(vkAllocateMemory(device, allocInfo, null, pMemory))
        uniformBufferVS.memory = pMemory[0]
        // Bind memory to buffer
        VK_CHECK_RESULT(vkBindBufferMemory(device, uniformBufferVS.buffer, uniformBufferVS.memory, 0))

        // Store information in the uniform's descriptor that is used by the descriptor set
        uniformBufferVS.descriptor = VkDescriptorBufferInfo.calloc(1)
                .buffer(uniformBufferVS.buffer)
                .offset(0)
                .range(uboVS.size.L)

        updateUniformBuffers()

        memReqs.free()
        allocInfo.free()
        bufferInfo.free()
        pBuffer.free()
        pMemory.free()
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
        val pData = MemoryUtil.memAllocPointer(1)
        VK_CHECK_RESULT(vkMapMemory(device, uniformBufferVS.memory, 0, uboVS.size.L, 0, pData))
        uboVS.pack()
        MemoryUtil.memCopy(uboVS.address, pData[0], uboVS.size.L)
        /* Unmap after data has been copied
            Note: Since we requested a host coherent memory type for the uniform buffer, the write is instantly visible to the GPU */
        vkUnmapMemory(device, uniformBufferVS.memory)
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
        prepared = true
        window.show()
    }

    override fun render() {
        if (!prepared) return
        draw()
    }

    /** This function is called by the base example class each time the view is changed by user input */
    override fun viewChanged() = updateUniformBuffers()
}