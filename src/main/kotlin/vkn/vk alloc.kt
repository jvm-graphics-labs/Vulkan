package vkn

import glfw_.appBuffer.ptr
import glfw_.advance
import org.lwjgl.vulkan.VkApplicationInfo
import org.lwjgl.vulkan.VkDescriptorBufferInfo


//fun VmDescriptorBufferInfo(): VkDescriptorBufferInfo = VkDescriptorBufferInfo.malloc()
//fun VmDescriptorBufferInfo(capacity: Int): VkDescriptorBufferInfo.Buffer = VkDescriptorBufferInfo.malloc(capacity)
fun cVkDescriptorBufferInfo(): VkDescriptorBufferInfo = VkDescriptorBufferInfo.calloc()
fun cVkDescriptorBufferInfo(capacity: Int): VkDescriptorBufferInfo.Buffer = VkDescriptorBufferInfo.calloc(capacity)


fun VbApplicationInfo(block: VkApplicationInfo.() -> Unit): VkApplicationInfo = VkApplicationInfo.create(ptr.advance(VkApplicationInfo.SIZEOF)).also(block)