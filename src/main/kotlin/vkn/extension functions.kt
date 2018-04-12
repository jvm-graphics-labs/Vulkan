package vkn

import org.lwjgl.vulkan.*


inline infix fun VkCommandBuffer.begin(beginInfo: VkCommandBufferBeginInfo): VkResult {
    return VkResult of VK10.nvkBeginCommandBuffer(this, beginInfo.adr)
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

inline fun VkCommandBuffer.setViewport(firstViewport: Int, viewports: VkViewport.Buffer) {
    VK10.nvkCmdSetViewport(this, firstViewport, viewports.remaining(), viewports.adr)
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