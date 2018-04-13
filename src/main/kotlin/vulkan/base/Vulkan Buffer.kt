/*
* Vulkan buffer class
*
* Encapsulates a Vulkan buffer
*
* Copyright (C) 2016 by Sascha Willems - www.saschawillems.de
*
* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
*/

package vulkan.base

import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDevice
import vkn.*

/**
 * @brief Encapsulates access to a Vulkan buffer backed up by device memory
 * @note To be filled by an external source like the VulkanDevice
 */
class Buffer {

    lateinit var device: VkDevice
    var buffer: VkBuffer = NULL
    var memory: VkDeviceMemory = NULL
    lateinit var descriptor: VkDescriptorBufferInfo
    var size: VkDeviceSize = 0
    var alignment: VkDeviceSize = 0
//    void * mapped = nullptr;

    /** @brief Usage flags to be filled by external source at buffer creation (to query at some later point) */
    var usageFlags: VkBufferUsageFlags = 0
    /** @brief Memory propertys flags to be filled by external source at buffer creation (to query at some later point) */
    var memoryPropertyFlags: VkMemoryPropertyFlags = 0

    /**
     * Map a memory range of this buffer. If successful, mapped points to the specified buffer range.
     *
     * @param size (Optional) Size of the memory range to map. Pass VK_WHOLE_SIZE to map the complete buffer range.
     * @param offset (Optional) Byte offset from beginning
     *
     * @return VkResult of the buffer mapping call
     */
    fun map(size: VkDeviceSize = VK_WHOLE_SIZE, offset: VkDeviceSize = 0): VkResult {
        TODO()
//        return VkResult of vk.mapMemory(device, memory, offset, size, 0, & mapped)
    }

    /**
     * Unmap a mapped memory range
     *
     * @note Does not return a result as vkUnmapMemory can't fail
     */
    fun unmap() {
        TODO()
//        if (mapped) {
//            vkUnmapMemory(device, memory);
//            mapped = nullptr;
//        }
    }

    /**
     * Attach the allocated memory block to the buffer
     *
     * @param offset (Optional) Byte offset (from the beginning) for the memory region to bind
     *
     * @return VkResult of the bindBufferMemory call
     */
    fun bind(offset: VkDeviceSize = 0): VkResult = TODO()//vk.bindBufferMemory(device, buffer, memory, offset)

    /**
     * Setup the default descriptor for this buffer
     *
     * @param size (Optional) Size of the memory range of the descriptor
     * @param offset (Optional) Byte offset from beginning
     *
     */
    fun setupDescriptor(size: VkDeviceSize = VK_WHOLE_SIZE, offset: VkDeviceSize = 0) {
        descriptor.offset = offset
        descriptor.buffer = buffer
        descriptor.range = size
    }

    /**
     * Copies the specified data to the mapped buffer
     *
     * @param data Pointer to the data to copy
     * @param size Size of the data to copy in machine units
     *
     */
//    void copyTo (void * data , VkDeviceSize size)
//    {
//        assert(mapped);
//        memcpy(mapped, data, size);
//    }

    /**
     * Flush a memory range of the buffer to make it visible to the device
     *
     * @note Only required for non-coherent memory
     *
     * @param size (Optional) Size of the memory range to flush. Pass VK_WHOLE_SIZE to flush the complete buffer range.
     * @param offset (Optional) Byte offset from beginning
     *
     * @return VkResult of the flush call
     */
    fun flush(size: VkDeviceSize = VK_WHOLE_SIZE, offset: VkDeviceSize = 0): VkResult {
        val mappedRange = vk.MappedMemoryRange {
            type = VkStructureType.MAPPED_MEMORY_RANGE
            this.memory = memory
            this.offset = offset
            this.size = size
        }
        return vk.flushMappedMemoryRange(device, mappedRange)
    }

    /**
     * Invalidate a memory range of the buffer to make it visible to the host
     *
     * @note Only required for non-coherent memory
     *
     * @param size (Optional) Size of the memory range to invalidate. Pass VK_WHOLE_SIZE to invalidate the complete buffer range.
     * @param offset (Optional) Byte offset from beginning
     *
     * @return VkResult of the invalidate call
     */
    fun invalidate (size: VkDeviceSize = VK_WHOLE_SIZE, offset: VkDeviceSize = 0): VkResult    {
        val mappedRange = vk.MappedMemoryRange {
            type = VkStructureType.MAPPED_MEMORY_RANGE
            this.memory = memory
            this.offset = offset
            this.size = size
        }
        return vk.invalidateMappedMemoryRanges(device, mappedRange)
    }

    /**
     * Release all Vulkan resources held by this buffer
     */
    fun destroy ()    {
        if (buffer != NULL)
            vk.destroyBuffer(device, buffer)
        if (memory != NULL)
            vk.freeMemory(device, memory)
    }
}