package vkn

import glfw_.appBuffer
import glm_.i
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*


/*
    VkCommandBuffer
 */

inline infix fun VkCommandBuffer.begin(beginInfo: VkCommandBufferBeginInfo): VkResult {
    return VkResult of VK10.nvkBeginCommandBuffer(this, beginInfo.adr)
}

inline fun VkCommandBuffer.beginRenderPass(renderPassBegin: VkRenderPassBeginInfo, contents: VkSubpassContents) {
    VK10.nvkCmdBeginRenderPass(this, renderPassBegin.adr, contents.i)
}

inline fun VkCommandBuffer.copyBuffer(srcBuffer: VkBuffer, dstBuffer: VkBuffer, regions: VkBufferCopy.Buffer) {
    VK10.nvkCmdCopyBuffer(this, srcBuffer, dstBuffer, regions.remaining(), regions.adr)
}

inline fun VkCommandBuffer.end(): VkResult {
    return VkResult of VK10.vkEndCommandBuffer(this)
}

inline fun VkCommandBuffer.reset(flags: VkCommandBufferResetFlags): VkResult {
    return VkResult of VK10.vkResetCommandBuffer(this, flags)
}

inline fun VkCommandBuffer.bindPipeline(pipelineBindPoint: VkPipelineBindPoint, pipeline: VkPipeline) {
    VK10.vkCmdBindPipeline(this, pipelineBindPoint.i, pipeline)
}

inline infix fun VkCommandBuffer.setViewport(viewport: VkViewport) {
    VK10.nvkCmdSetViewport(this, 0, 1, viewport.adr)
}

inline fun VkCommandBuffer.setViewport(firstViewport: Int, viewports: VkViewport.Buffer) {
    VK10.nvkCmdSetViewport(this, firstViewport, viewports.remaining(), viewports.adr)
}

inline infix fun VkCommandBuffer.setScissor(scissor: VkRect2D) {
    VK10.nvkCmdSetScissor(this, 0, 1, scissor.adr)
}

inline fun VkCommandBuffer.setScissor(firstScissor: Int, scissors: VkRect2D.Buffer) {
    VK10.nvkCmdSetScissor(this, firstScissor, scissors.remaining(), scissors.adr)
}

inline infix fun VkCommandBuffer.setLineWidth(lineWidth: Float) {
    VK10.vkCmdSetLineWidth(this, lineWidth)
}

inline fun VkCommandBuffer.setDepthBias(depthBiasConstantFactor: Float, depthBiasClamp: Float, depthBiasSlopeFactor: Float) {
    VK10.vkCmdSetDepthBias(this, depthBiasConstantFactor, depthBiasClamp, depthBiasSlopeFactor)
}
//inline fun VkCommandBuffer.setBlendConstants(depthBiasConstantFactor: Float, depthBiasClamp: Float, depthBiasSlopeFactor: Float) {
//    VK10.setBlendConstants(this, depthBiasConstantFactor, depthBiasClamp, depthBiasSlopeFactor)
//}


/*
    VkDevice
 */

inline infix fun VkDevice.allocateCommandBuffer(allocateInfo: VkCommandBufferAllocateInfo): VkCommandBuffer {
    val pCmdBuffer = appBuffer.pointer
    VK_CHECK_RESULT(VK10.nvkAllocateCommandBuffers(this, allocateInfo.adr, pCmdBuffer))
    return VkCommandBuffer(memGetAddress(pCmdBuffer), this)
}

inline infix fun VkDevice.allocateCommandBuffers(allocateInfo: VkCommandBufferAllocateInfo): ArrayList<VkCommandBuffer> {
    val count = allocateInfo.commandBufferCount
    val pCommandBuffer = appBuffer.pointerArray(count)
    val commandBuffers = ArrayList<VkCommandBuffer>(count)
    VK_CHECK_RESULT(VK10.nvkAllocateCommandBuffers(this, allocateInfo.adr, pCommandBuffer))
    for (i in 0 until count)
        commandBuffers += VkCommandBuffer(memGetAddress(pCommandBuffer + Pointer.POINTER_SIZE * i), this)
    return commandBuffers
}

inline infix fun VkDevice.allocateDescriptorSets(allocateInfo: VkDescriptorSetAllocateInfo): VkDescriptorSet {
    val pDescriptorSets = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkAllocateDescriptorSets(this, allocateInfo.adr, pDescriptorSets))
    return memGetLong(pDescriptorSets)
}

inline infix fun VkDevice.allocateMemory(allocateInfo: VkMemoryAllocateInfo): VkDeviceMemory {
    val pMemory = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkAllocateMemory(this, allocateInfo.adr, NULL, pMemory))
    return memGetLong(pMemory)
}

inline fun VkDevice.bindBufferMemory(buffer: VkBuffer, memory: VkDeviceMemory, memoryOffset: VkDeviceSize) {
    VK_CHECK_RESULT(VK10.vkBindBufferMemory(this, buffer, memory, memoryOffset))
}

inline fun VkDevice.bindImageMemory(image: VkImage, memory: VkDeviceMemory, memoryOffset: VkDeviceSize = 0L) {
    VK_CHECK_RESULT(VK10.vkBindImageMemory(this, image, memory, memoryOffset))
}

inline infix fun VkDevice.createBuffer(createInfo: VkBufferCreateInfo): VkBuffer {
    val pBuffer = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreateBuffer(this, createInfo.adr, NULL, pBuffer))
    return memGetLong(pBuffer)
}

inline infix fun VkDevice.createCommandPool(createInfo: VkCommandPoolCreateInfo): VkCommandPool {
    val pCommandPool = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreateCommandPool(this, createInfo.adr, NULL, pCommandPool))
    return memGetLong(pCommandPool)
}

inline infix fun VkDevice.createDescriptorPool(createInfo: VkDescriptorPoolCreateInfo): VkDescriptorPool {
    val pDescriptorPool = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreateDescriptorPool(this, createInfo.adr, NULL, pDescriptorPool))
    return memGetLong(pDescriptorPool)
}

inline infix fun VkDevice.createDescriptorSetLayout(createInfo: VkDescriptorSetLayoutCreateInfo): VkDescriptorSetLayout {
    val pSetLayout = appBuffer.long
    VK10.nvkCreateDescriptorSetLayout(this, createInfo.adr, NULL, pSetLayout)
    return memGetLong(pSetLayout)
}

inline infix fun VkDevice.createFence(createInfo: VkFenceCreateInfo): VkFence {
    val pFence = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreateFence(this, createInfo.adr, NULL, pFence))
    return memGetLong(pFence)
}

inline fun VkDevice.createFences(createInfo: VkFenceCreateInfo, fences: ArrayList<VkFence>) {
    val pFence = appBuffer.long
    for (i in fences.indices) {
        VK_CHECK_RESULT(VK10.nvkCreateFence(this, createInfo.adr, NULL, pFence))
        fences[i] = memGetLong(pFence)
    }
}

inline infix fun VkDevice.createFramebuffer(createInfo: VkFramebufferCreateInfo): VkFramebuffer {
    val pFramebuffer = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreateFramebuffer(this, createInfo.adr, NULL, pFramebuffer))
    return memGetLong(pFramebuffer)
}

inline fun VkDevice.createGraphicsPipelines(pipelineCache: VkPipelineCache, createInfo: VkGraphicsPipelineCreateInfo): VkPipeline {
    val pPipelines = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreateGraphicsPipelines(this, pipelineCache, 1, createInfo.adr, NULL, pPipelines))
    return memGetLong(pPipelines)
}

inline infix fun VkDevice.createImage(createInfo: VkImageCreateInfo): VkImage {
    val pImage = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreateImage(this, createInfo.adr, NULL, pImage))
    return memGetLong(pImage)
}

inline infix fun VkDevice.createImageView(createInfo: VkImageViewCreateInfo): VkImageView {
    val pView = appBuffer.long
    VK10.nvkCreateImageView(this, createInfo.adr, NULL, pView)
    return memGetLong(pView)
}

inline infix fun VkDevice.createPipelineCache(createInfo: VkPipelineCacheCreateInfo): VkPipelineCache {
    val pPipelineCache = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreatePipelineCache(this, createInfo.adr, NULL, pPipelineCache))
    return memGetLong(pPipelineCache)
}

inline infix fun VkDevice.createPipelineLayout(createInfo: VkPipelineLayoutCreateInfo): VkPipelineLayout {
    val pPipelineLayout = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreatePipelineLayout(this, createInfo.adr, NULL, pPipelineLayout))
    return memGetLong(pPipelineLayout)
}

inline infix fun VkDevice.createRenderPass(createInfo: VkRenderPassCreateInfo): VkRenderPass {
    val pRenderPass = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreateRenderPass(this, createInfo.adr, NULL, pRenderPass))
    return memGetLong(pRenderPass)
}

inline infix fun VkDevice.createSemaphore(createInfo: VkSemaphoreCreateInfo): VkSemaphore {
    val pSemaphore = appBuffer.long
    VK_CHECK_RESULT(VK10.nvkCreateSemaphore(this, createInfo.adr, NULL, pSemaphore))
    return memGetLong(pSemaphore)
}

inline infix fun VkDevice.createSwapchainKHR(createInfo: VkSwapchainCreateInfoKHR): VkSwapchainKHR {
    val pSwapchain = appBuffer.long
    KHRSwapchain.nvkCreateSwapchainKHR(this, createInfo.adr, NULL, pSwapchain)
    return memGetLong(pSwapchain)
}

inline infix fun VkDevice.destroyBuffer(buffer: VkBuffer) {
    VK10.nvkDestroyBuffer(this, buffer, NULL)
}

inline infix fun VkDevice.destroyShaderModules(shaderModules: VkPipelineShaderStageCreateInfo.Buffer) {
    for (i in shaderModules)
        VK10.nvkDestroyShaderModule(this, i.module, NULL)
}

inline infix fun VkDevice.destroyDescriptorSetLayout(descriptorSetLayout: VkDescriptorSetLayout) {
    VK10.nvkDestroyDescriptorSetLayout(this, descriptorSetLayout, NULL)
}

inline infix fun VkDevice.destroyFence(fence: VkFence) {
    VK10.nvkDestroyFence(this, fence, NULL)
}

inline infix fun VkDevice.destroyFences(fences: ArrayList<VkFence>) {
    for (fence in fences)
        VK10.nvkDestroyFence(this, fence, NULL)
}

inline infix fun VkDevice.destroyImageView(imageView: VkImageView) {
    VK10.nvkDestroyImageView(this, imageView, NULL)
}

inline infix fun VkDevice.destroyPipeline(pipeline: VkPipeline) {
    VK10.nvkDestroyPipeline(this, pipeline, NULL)
}

inline infix fun VkDevice.destroyPipelineLayout(pipelineLayout: VkPipelineLayout) {
    VK10.nvkDestroyPipelineLayout(this, pipelineLayout, NULL)
}

inline infix fun VkDevice.destroySemaphore(semaphore: VkSemaphore) {
    VK10.nvkDestroySemaphore(this, semaphore, NULL)
}

inline infix fun VkDevice.destroySwapchainKHR(swapchain: VkSwapchainKHR) {
    KHRSwapchain.nvkDestroySwapchainKHR(this, swapchain, NULL)
}

inline fun VkDevice.freeCommandBuffer(commandPool: VkCommandPool, commandBuffer: VkCommandBuffer) {
    val pCommandBuffer = appBuffer.pointer
    memPutAddress(pCommandBuffer, commandBuffer.adr)
    VK10.nvkFreeCommandBuffers(this, commandPool, 1, pCommandBuffer)
}

inline infix fun VkDevice.freeMemory(memory: VkDeviceMemory) {
    VK10.nvkFreeMemory(this, memory, NULL)
}

inline fun VkDevice.getBufferMemoryRequirements(buffer: VkBuffer, memoryRequirements: VkMemoryRequirements) {
    VK10.nvkGetBufferMemoryRequirements(this, buffer, memoryRequirements.adr)
}

inline fun VkDevice.mapMemory(memory: Long, offset: Long, size: Long, flags: Int, data: Long) {
    VK_CHECK_RESULT(VK10.nvkMapMemory(this, memory, offset, size, flags, data))
}

inline fun VkDevice.getQueue(queueFamilyIndex: Int, queueIndex: Int): VkQueue {
    val pQueue = appBuffer.pointer
    VK10.nvkGetDeviceQueue(this, queueFamilyIndex, queueIndex, pQueue)
    return VkQueue(memGetLong(pQueue), this)
}

inline infix fun VkDevice.getSwapchainImagesKHR(swapchain: VkSwapchainKHR): ArrayList<VkImageView> {
    return vk.getSwapchainImagesKHR(this, swapchain)
}

inline infix fun VkDevice.unmapMemory(memory: VkDeviceMemory) {
    VK10.vkUnmapMemory(this, memory)
}

inline infix fun VkDevice.updateDescriptorSets(descriptorWrites: VkWriteDescriptorSet) {
    VK10.nvkUpdateDescriptorSets(this, 1, descriptorWrites.adr, 0, NULL)
}

//inline fun VkDevice.updateDescriptorSets(descriptorWrites: VkWriteDescriptorSet.Buffer,
//                                descriptorCopies: VkCopyDescriptorSet.Buffer? = null) {
//    VK10.nvkUpdateDescriptorSets(device, descriptorWrites.remaining(), descriptorWrites.adr,
//            descriptorCopies?.remaining() ?: 0, descriptorCopies?.adr ?: NULL)
//}

inline fun VkDevice.waitForFence(fence: VkFence, waitAll: Boolean, timeout: Long) {
    val pFence = appBuffer.long
    memPutLong(pFence, fence)
    VK_CHECK_RESULT(VK10.nvkWaitForFences(this, 1, pFence, waitAll.i, timeout))
}

inline fun VkDevice.waitIdle(): VkResult {
    return VkResult of VK10.vkDeviceWaitIdle(this)
}


/*
    VkInstance
 */

inline infix fun VkInstance.createDebugReportCallbackEXT(createInfo: VkDebugReportCallbackCreateInfoEXT): VkDebugReportCallbackEXT {
    val long = appBuffer.long
    VK_CHECK_RESULT(EXTDebugReport.nvkCreateDebugReportCallbackEXT(this, createInfo.adr, NULL, long))
    return memGetLong(long)
}

inline infix fun VkInstance.destroyDebugReportCallbackEXT(debugReportCallback: VkDebugReportCallbackEXT) {
    EXTDebugReport.nvkDestroyDebugReportCallbackEXT(this, debugReportCallback, NULL)
}


/*
    VkPhysicalDevice
 */

inline val VkPhysicalDevice.features: VkPhysicalDeviceFeatures
    get() = vk.PhysicalDeviceFeatures().also(::getFeatures)

inline infix fun VkPhysicalDevice.getFeatures(features: VkPhysicalDeviceFeatures) {
    VK10.nvkGetPhysicalDeviceFeatures(this, features.adr)
}

inline infix fun VkPhysicalDevice.getFormatProperties(format: VkFormat): VkFormatProperties {
    return vk.getPhysicalDeviceFormatProperties(this, format)
}

inline val VkPhysicalDevice.memoryProperties: VkPhysicalDeviceMemoryProperties
    get() = vk.PhysicalDeviceMemoryProperties().also(::getMemoryProperties)

inline infix fun VkPhysicalDevice.getMemoryProperties(memoryProperties: VkPhysicalDeviceMemoryProperties) {
    VK10.nvkGetPhysicalDeviceMemoryProperties(this, memoryProperties.adr)
}

inline val VkPhysicalDevice.queueFamilyProperties: ArrayList<VkQueueFamilyProperties>
    get() = vk.getPhysicalDeviceQueueFamilyProperties(this)

inline val VkPhysicalDevice.properties: VkPhysicalDeviceProperties
    get() = vk.PhysicalDeviceProperties().also(::getProperties)

inline infix fun VkPhysicalDevice.getProperties(properties: VkPhysicalDeviceProperties) {
    VK10.nvkGetPhysicalDeviceProperties(this, properties.adr)
}

inline infix fun VkPhysicalDevice.createDevice(createInfo: VkDeviceCreateInfo): VkDevice? {
    val pDevice = appBuffer.pointer
    VK_CHECK_RESULT(VK10.nvkCreateDevice(this, createInfo.adr, NULL, pDevice))
    return VkDevice(memGetLong(pDevice), this, createInfo)
}

inline infix fun VkPhysicalDevice.getSurfaceCapabilitiesKHR(surface: VkSurfaceKHR): VkSurfaceCapabilitiesKHR {
    return vk.SurfaceCapabilitiesKHR {
        VK_CHECK_RESULT(KHRSurface.nvkGetPhysicalDeviceSurfaceCapabilitiesKHR(this@getSurfaceCapabilitiesKHR, surface, adr))
    }
}

inline infix fun VkPhysicalDevice.getSurfaceFormatsKHR(surface: VkSurfaceKHR): ArrayList<VkSurfaceFormatKHR> {
    return vk.getPhysicalDeviceSurfaceFormatsKHR(this, surface)
}

inline fun VkPhysicalDevice.getSurfaceSupportKHR(queueFamilyProperties: ArrayList<VkQueueFamilyProperties>,
                                                 surface: VkSurfaceKHR): BooleanArray {
    return vk.getPhysicalDeviceSurfaceSupportKHR(this, queueFamilyProperties, surface)
}

inline infix fun VkPhysicalDevice.getSurfacePresentModesKHR(surface: VkSurfaceKHR): ArrayList<VkPresentMode> {
    return vk.getPhysicalDeviceSurfacePresentModesKHR(this, surface)
}


/*
    VkQueue
 */

inline fun VkQueue.submit(submits: VkSubmitInfo, fence: VkFence): VkResult {
    return VkResult of VK10.nvkQueueSubmit(this, 1, submits.adr, fence)
}

inline fun VkQueue.waitIdle(): VkResult {
    return VkResult of VK10.vkQueueWaitIdle(this)
}


