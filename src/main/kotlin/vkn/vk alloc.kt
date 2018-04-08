package vkn

import org.lwjgl.vulkan.VkDescriptorBufferInfo

//fun VmDescriptorBufferInfo(): VkDescriptorBufferInfo = VkDescriptorBufferInfo.malloc()
//fun VmDescriptorBufferInfo(capacity: Int): VkDescriptorBufferInfo.Buffer = VkDescriptorBufferInfo.malloc(capacity)
fun cVkDescriptorBufferInfo(): VkDescriptorBufferInfo = VkDescriptorBufferInfo.calloc()
fun cVkDescriptorBufferInfo(capacity: Int): VkDescriptorBufferInfo.Buffer = VkDescriptorBufferInfo.calloc(capacity)