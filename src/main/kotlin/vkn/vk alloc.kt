package vkn

import glfw_.appBuffer
import glfw_.appBuffer.pointer
import glfw_.getAndAdd
import org.lwjgl.system.CallbackI
import org.lwjgl.system.NativeType
import org.lwjgl.vulkan.VkApplicationInfo
import org.lwjgl.vulkan.VkDescriptorBufferInfo


//fun VmDescriptorBufferInfo(): VkDescriptorBufferInfo = VkDescriptorBufferInfo.malloc()
//fun VmDescriptorBufferInfo(capacity: Int): VkDescriptorBufferInfo.Buffer = VkDescriptorBufferInfo.malloc(capacity)
fun cVkDescriptorBufferInfo(): VkDescriptorBufferInfo = VkDescriptorBufferInfo.calloc()
fun cVkDescriptorBufferInfo(capacity: Int): VkDescriptorBufferInfo.Buffer = VkDescriptorBufferInfo.calloc(capacity)


fun VbApplicationInfo(block: VkApplicationInfo.() -> Unit): VkApplicationInfo = VkApplicationInfo.create(pointer.getAndAdd(VkApplicationInfo.SIZEOF)).also(block)