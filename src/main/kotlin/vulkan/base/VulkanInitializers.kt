package vulkan.base

import glm_.f
import glm_.vec2.Vec2i
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED
import vkk.*

object initializers {

    //    fun VkMemoryAllocateInfo memoryAllocateInfo()
//    {
//        VkMemoryAllocateInfo memAllocInfo {};
//        memAllocInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
//        return memAllocInfo;
//    }
//
//    inline VkMappedMemoryRange mappedMemoryRange()
//    {
//        VkMappedMemoryRange mappedMemoryRange {};
//        mappedMemoryRange.sType = VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE;
//        return mappedMemoryRange;
//    }
//
    fun cCommandBufferAllocateInfo(
            commandPool: VkCommandPool,
            level: VkCommandBufferLevel,
            bufferCount: Int): VkCommandBufferAllocateInfo {
        return VkCommandBufferAllocateInfo.calloc().apply {
            this.commandPool = commandPool
            this.level = level
            commandBufferCount = bufferCount
        }
    }

    fun commandBufferAllocateInfo(
            commandPool: VkCommandPool,
            level: VkCommandBufferLevel,
            bufferCount: Int): VkCommandBufferAllocateInfo {
        return vk.CommandBufferAllocateInfo {
            this.commandPool = commandPool
            this.level = level
            commandBufferCount = bufferCount
        }
    }

    //
//    inline VkCommandPoolCreateInfo commandPoolCreateInfo()
//    {
//        VkCommandPoolCreateInfo cmdPoolCreateInfo {};
//        cmdPoolCreateInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
//        return cmdPoolCreateInfo;
//    }
//

    //    inline VkCommandBufferInheritanceInfo commandBufferInheritanceInfo()
//    {
//        VkCommandBufferInheritanceInfo cmdBufferInheritanceInfo {};
//        cmdBufferInheritanceInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_INHERITANCE_INFO;
//        return cmdBufferInheritanceInfo;
//    }
//
//    inline VkRenderPassBeginInfo renderPassBeginInfo()
//    {
//        VkRenderPassBeginInfo renderPassBeginInfo {};
//        renderPassBeginInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
//        return renderPassBeginInfo;
//    }
//
//    inline VkRenderPassCreateInfo renderPassCreateInfo()
//    {
//        VkRenderPassCreateInfo renderPassCreateInfo {};
//        renderPassCreateInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
//        return renderPassCreateInfo;
//    }
//
    /** @brief Initialize an image memory barrier with no image transfer ownership */
    inline fun cImageMemoryBarrier(): VkImageMemoryBarrier {
        return VkImageMemoryBarrier.create().apply {
            type = VkStructureType.IMAGE_MEMORY_BARRIER
            srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED
            dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED
        }
    }
//
//    /** @brief Initialize a buffer memory barrier with no image transfer ownership */
//    inline VkBufferMemoryBarrier bufferMemoryBarrier()
//    {
//        VkBufferMemoryBarrier bufferMemoryBarrier {};
//        bufferMemoryBarrier.sType = VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER;
//        bufferMemoryBarrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
//        bufferMemoryBarrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
//        return bufferMemoryBarrier;
//    }
//
//    inline VkMemoryBarrier memoryBarrier()
//    {
//        VkMemoryBarrier memoryBarrier {};
//        memoryBarrier.sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER;
//        return memoryBarrier;
//    }
//
//    inline VkImageCreateInfo imageCreateInfo()
//    {
//        VkImageCreateInfo imageCreateInfo {};
//        imageCreateInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
//        return imageCreateInfo;
//    }
//
//    inline VkSamplerCreateInfo samplerCreateInfo()
//    {
//        VkSamplerCreateInfo samplerCreateInfo {};
//        samplerCreateInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
//        samplerCreateInfo.maxAnisotropy = 1.0f;
//        return samplerCreateInfo;
//    }
//
//    inline VkImageViewCreateInfo imageViewCreateInfo()
//    {
//        VkImageViewCreateInfo imageViewCreateInfo {};
//        imageViewCreateInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
//        return imageViewCreateInfo;
//    }
//
//    inline VkFramebufferCreateInfo framebufferCreateInfo()
//    {
//        VkFramebufferCreateInfo framebufferCreateInfo {};
//        framebufferCreateInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
//        return framebufferCreateInfo;
//    }
//

    //
//    inline VkFenceCreateInfo fenceCreateInfo(VkFenceCreateFlags flags = 0)
//    {
//        VkFenceCreateInfo fenceCreateInfo {};
//        fenceCreateInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
//        fenceCreateInfo.flags = flags;
//        return fenceCreateInfo;
//    }
//
//    inline VkEventCreateInfo eventCreateInfo()
//    {
//        VkEventCreateInfo eventCreateInfo {};
//        eventCreateInfo.sType = VK_STRUCTURE_TYPE_EVENT_CREATE_INFO;
//        return eventCreateInfo;
//    }
//
//
    inline fun viewport(size: Vec2i, minDepth: Float, maxDepth: Float): VkViewport {
        return vk.Viewport {
            width = size.x.f
            height = size.y.f
            this.minDepth = minDepth
            this.maxDepth = maxDepth
        }
    }

    //    inline VkRect2D rect2D(
//            int32_t width,
//            int32_t height,
//            int32_t offsetX,
//            int32_t offsetY)
//    {
//        VkRect2D rect2D {};
//        rect2D.extent.width = width;
//        rect2D.extent.height = height;
//        rect2D.offset.x = offsetX;
//        rect2D.offset.y = offsetY;
//        return rect2D;
//    }
//
//    inline VkBufferCreateInfo bufferCreateInfo()
//    {
//        VkBufferCreateInfo bufCreateInfo {};
//        bufCreateInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
//        return bufCreateInfo;
//    }
//
//    inline VkBufferCreateInfo bufferCreateInfo(
//            VkBufferUsageFlags usage,
//            VkDeviceSize size)
//    {
//        VkBufferCreateInfo bufCreateInfo {};
//        bufCreateInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
//        bufCreateInfo.usage = usage;
//        bufCreateInfo.size = size;
//        return bufCreateInfo;
//    }
//
    inline fun descriptorPoolCreateInfo(poolSizes: VkDescriptorPoolSize.Buffer, maxSets: Int): VkDescriptorPoolCreateInfo {
        return vk.DescriptorPoolCreateInfo {
            this.poolSizes = poolSizes
            this.maxSets = maxSets
        }
    }
//
//    inline VkDescriptorPoolCreateInfo descriptorPoolCreateInfo(
//            const std::vector<VkDescriptorPoolSize>& poolSizes,
//    uint32_t maxSets)
//    {
//        VkDescriptorPoolCreateInfo descriptorPoolInfo{};
//        descriptorPoolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
//        descriptorPoolInfo.poolSizeCount = static_cast<uint32_t>(poolSizes.size());
//        descriptorPoolInfo.pPoolSizes = poolSizes.data();
//        descriptorPoolInfo.maxSets = maxSets;
//        return descriptorPoolInfo;
//    }

    inline fun descriptorPoolSize(type: VkDescriptorType, descriptorCount: Int): VkDescriptorPoolSize {
        return vk.DescriptorPoolSize {
            this.type = type
            this.descriptorCount = descriptorCount
        }
    }
//
//    inline VkDescriptorSetLayoutBinding descriptorSetLayoutBinding(
//            VkDescriptorType type,
//            VkShaderStageFlags stageFlags,
//            uint32_t binding,
//            uint32_t descriptorCount = 1)
//    {
//        VkDescriptorSetLayoutBinding setLayoutBinding {};
//        setLayoutBinding.descriptorType = type;
//        setLayoutBinding.stageFlags = stageFlags;
//        setLayoutBinding.binding = binding;
//        setLayoutBinding.descriptorCount = descriptorCount;
//        return setLayoutBinding;
//    }
//
//    inline VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo(
//            const VkDescriptorSetLayoutBinding* pBindings,
//            uint32_t bindingCount)
//    {
//        VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo {};
//        descriptorSetLayoutCreateInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
//        descriptorSetLayoutCreateInfo.pBindings = pBindings;
//        descriptorSetLayoutCreateInfo.bindingCount = bindingCount;
//        return descriptorSetLayoutCreateInfo;
//    }
//
//    inline VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo(
//            const std::vector<VkDescriptorSetLayoutBinding>& bindings)
//    {
//        VkDescriptorSetLayoutCreateInfo descriptorSetLayoutCreateInfo{};
//        descriptorSetLayoutCreateInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
//        descriptorSetLayoutCreateInfo.pBindings = bindings.data();
//        descriptorSetLayoutCreateInfo.bindingCount = static_cast<uint32_t>(bindings.size());
//        return descriptorSetLayoutCreateInfo;
//    }
//
//    inline VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo(
//            const VkDescriptorSetLayout* pSetLayouts,
//            uint32_t setLayoutCount = 1)
//    {
//        VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo {};
//        pipelineLayoutCreateInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
//        pipelineLayoutCreateInfo.setLayoutCount = setLayoutCount;
//        pipelineLayoutCreateInfo.pSetLayouts = pSetLayouts;
//        return pipelineLayoutCreateInfo;
//    }
//
//    inline VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo(
//            uint32_t setLayoutCount = 1)
//    {
//        VkPipelineLayoutCreateInfo pipelineLayoutCreateInfo{};
//        pipelineLayoutCreateInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
//        pipelineLayoutCreateInfo.setLayoutCount = setLayoutCount;
//        return pipelineLayoutCreateInfo;
//    }
//
//    inline VkDescriptorSetAllocateInfo descriptorSetAllocateInfo(
//            VkDescriptorPool descriptorPool,
//            const VkDescriptorSetLayout* pSetLayouts,
//            uint32_t descriptorSetCount)
//    {
//        VkDescriptorSetAllocateInfo descriptorSetAllocateInfo {};
//        descriptorSetAllocateInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
//        descriptorSetAllocateInfo.descriptorPool = descriptorPool;
//        descriptorSetAllocateInfo.pSetLayouts = pSetLayouts;
//        descriptorSetAllocateInfo.descriptorSetCount = descriptorSetCount;
//        return descriptorSetAllocateInfo;
//    }
//
//    inline VkDescriptorImageInfo descriptorImageInfo(VkSampler sampler, VkImageView imageView, VkImageLayout imageLayout)
//    {
//        VkDescriptorImageInfo descriptorImageInfo {};
//        descriptorImageInfo.sampler = sampler;
//        descriptorImageInfo.imageView = imageView;
//        descriptorImageInfo.imageLayout = imageLayout;
//        return descriptorImageInfo;
//    }
//
//    inline VkWriteDescriptorSet writeDescriptorSet(
//            VkDescriptorSet dstSet,
//            VkDescriptorType type,
//            uint32_t binding,
//            VkDescriptorBufferInfo* bufferInfo,
//            uint32_t descriptorCount = 1)
//    {
//        VkWriteDescriptorSet writeDescriptorSet {};
//        writeDescriptorSet.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
//        writeDescriptorSet.dstSet = dstSet;
//        writeDescriptorSet.descriptorType = type;
//        writeDescriptorSet.dstBinding = binding;
//        writeDescriptorSet.pBufferInfo = bufferInfo;
//        writeDescriptorSet.descriptorCount = descriptorCount;
//        return writeDescriptorSet;
//    }
//
//    inline VkWriteDescriptorSet writeDescriptorSet(
//            VkDescriptorSet dstSet,
//            VkDescriptorType type,
//            uint32_t binding,
//            VkDescriptorImageInfo *imageInfo,
//            uint32_t descriptorCount = 1)
//    {
//        VkWriteDescriptorSet writeDescriptorSet {};
//        writeDescriptorSet.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
//        writeDescriptorSet.dstSet = dstSet;
//        writeDescriptorSet.descriptorType = type;
//        writeDescriptorSet.dstBinding = binding;
//        writeDescriptorSet.pImageInfo = imageInfo;
//        writeDescriptorSet.descriptorCount = descriptorCount;
//        return writeDescriptorSet;
//    }
//


    //    inline VkPipelineVertexInputStateCreateInfo pipelineVertexInputStateCreateInfo()
//    {
//        VkPipelineVertexInputStateCreateInfo pipelineVertexInputStateCreateInfo {};
//        pipelineVertexInputStateCreateInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
//        return pipelineVertexInputStateCreateInfo;
//    }
//
//    inline VkPipelineInputAssemblyStateCreateInfo pipelineInputAssemblyStateCreateInfo(
//            VkPrimitiveTopology topology,
//            VkPipelineInputAssemblyStateCreateFlags flags,
//            VkBool32 primitiveRestartEnable)
//    {
//        VkPipelineInputAssemblyStateCreateInfo pipelineInputAssemblyStateCreateInfo {};
//        pipelineInputAssemblyStateCreateInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
//        pipelineInputAssemblyStateCreateInfo.topology = topology;
//        pipelineInputAssemblyStateCreateInfo.flags = flags;
//        pipelineInputAssemblyStateCreateInfo.primitiveRestartEnable = primitiveRestartEnable;
//        return pipelineInputAssemblyStateCreateInfo;
//    }
//
    inline fun pipelineRasterizationStateCreateInfo(
            polygonMode: VkPolygonMode,
            cullMode: VkCullModeFlags,
            frontFace: VkFrontFace,
            flags: VkPipelineRasterizationStateCreateFlags = 0): VkPipelineRasterizationStateCreateInfo {
        return vk.PipelineRasterizationStateCreateInfo {
            this.polygonMode = polygonMode
            this.cullMode = cullMode
            this.frontFace = frontFace
            this.flags = flags
            this.depthClampEnable = false
            this.lineWidth = 1f
        }
    }

    //
//    inline VkPipelineColorBlendAttachmentState pipelineColorBlendAttachmentState(
//            VkColorComponentFlags colorWriteMask,
//            VkBool32 blendEnable)
//    {
//        VkPipelineColorBlendAttachmentState pipelineColorBlendAttachmentState {};
//        pipelineColorBlendAttachmentState.colorWriteMask = colorWriteMask;
//        pipelineColorBlendAttachmentState.blendEnable = blendEnable;
//        return pipelineColorBlendAttachmentState;
//    }
//
//    inline VkPipelineColorBlendStateCreateInfo pipelineColorBlendStateCreateInfo(
//            uint32_t attachmentCount,
//            const VkPipelineColorBlendAttachmentState * pAttachments)
//    {
//        VkPipelineColorBlendStateCreateInfo pipelineColorBlendStateCreateInfo {};
//        pipelineColorBlendStateCreateInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
//        pipelineColorBlendStateCreateInfo.attachmentCount = attachmentCount;
//        pipelineColorBlendStateCreateInfo.pAttachments = pAttachments;
//        return pipelineColorBlendStateCreateInfo;
//    }
//
    inline fun pipelineDepthStencilStateCreateInfo(
            depthTestEnable: Boolean,
            depthWriteEnable: Boolean,
            depthCompareOp: VkCompareOp): VkPipelineDepthStencilStateCreateInfo {
        return vk.PipelineDepthStencilStateCreateInfo {
            this.depthTestEnable = depthTestEnable
            this.depthWriteEnable = depthWriteEnable
            this.depthCompareOp = depthCompareOp
            front = back
            back.compareOp = VkCompareOp.ALWAYS
        }
    }

//    inline VkPipelineViewportStateCreateInfo pipelineViewportStateCreateInfo(
//            uint32_t viewportCount,
//            uint32_t scissorCount,
//            VkPipelineViewportStateCreateFlags flags = 0)
//    {
//        VkPipelineViewportStateCreateInfo pipelineViewportStateCreateInfo {};
//        pipelineViewportStateCreateInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
//        pipelineViewportStateCreateInfo.viewportCount = viewportCount;
//        pipelineViewportStateCreateInfo.scissorCount = scissorCount;
//        pipelineViewportStateCreateInfo.flags = flags;
//        return pipelineViewportStateCreateInfo;
//    }
//
//    inline VkPipelineMultisampleStateCreateInfo pipelineMultisampleStateCreateInfo(
//            VkSampleCountFlagBits rasterizationSamples,
//            VkPipelineMultisampleStateCreateFlags flags = 0)
//    {
//        VkPipelineMultisampleStateCreateInfo pipelineMultisampleStateCreateInfo {};
//        pipelineMultisampleStateCreateInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
//        pipelineMultisampleStateCreateInfo.rasterizationSamples = rasterizationSamples;
//        pipelineMultisampleStateCreateInfo.flags = flags;
//        return pipelineMultisampleStateCreateInfo;
//    }
//
//    inline VkPipelineDynamicStateCreateInfo pipelineDynamicStateCreateInfo(
//            const VkDynamicState * pDynamicStates,
//            uint32_t dynamicStateCount,
//            VkPipelineDynamicStateCreateFlags flags = 0)
//    {
//        VkPipelineDynamicStateCreateInfo pipelineDynamicStateCreateInfo {};
//        pipelineDynamicStateCreateInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
//        pipelineDynamicStateCreateInfo.pDynamicStates = pDynamicStates;
//        pipelineDynamicStateCreateInfo.dynamicStateCount = dynamicStateCount;
//        pipelineDynamicStateCreateInfo.flags = flags;
//        return pipelineDynamicStateCreateInfo;
//    }
//
//    inline VkPipelineDynamicStateCreateInfo pipelineDynamicStateCreateInfo(
//            const std::vector<VkDynamicState>& pDynamicStates,
//    VkPipelineDynamicStateCreateFlags flags = 0)
//    {
//        VkPipelineDynamicStateCreateInfo pipelineDynamicStateCreateInfo{};
//        pipelineDynamicStateCreateInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
//        pipelineDynamicStateCreateInfo.pDynamicStates = pDynamicStates.data();
//        pipelineDynamicStateCreateInfo.dynamicStateCount = static_cast<uint32_t>(pDynamicStates.size());
//        pipelineDynamicStateCreateInfo.flags = flags;
//        return pipelineDynamicStateCreateInfo;
//    }
//
//    inline VkPipelineTessellationStateCreateInfo pipelineTessellationStateCreateInfo(uint32_t patchControlPoints)
//    {
//        VkPipelineTessellationStateCreateInfo pipelineTessellationStateCreateInfo {};
//        pipelineTessellationStateCreateInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_TESSELLATION_STATE_CREATE_INFO;
//        pipelineTessellationStateCreateInfo.patchControlPoints = patchControlPoints;
//        return pipelineTessellationStateCreateInfo;
//    }

    inline fun pipelineCreateInfo(
            layout: VkPipelineLayout,
            renderPass: VkRenderPass,
            flags: VkPipelineCreateFlags = 0): VkGraphicsPipelineCreateInfo {
        return vk.GraphicsPipelineCreateInfo {
            this.layout = layout
            this.renderPass = renderPass
            this.flags = flags
            basePipelineIndex = -1
//            basePipelineHandle = VK_NULL_HANDLE
        }
    }
//
//    inline VkComputePipelineCreateInfo computePipelineCreateInfo(
//            VkPipelineLayout layout,
//            VkPipelineCreateFlags flags = 0)
//    {
//        VkComputePipelineCreateInfo computePipelineCreateInfo {};
//        computePipelineCreateInfo.sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
//        computePipelineCreateInfo.layout = layout;
//        computePipelineCreateInfo.flags = flags;
//        return computePipelineCreateInfo;
//    }
//
//    inline VkPushConstantRange pushConstantRange(
//            VkShaderStageFlags stageFlags,
//            uint32_t size,
//            uint32_t offset)
//    {
//        VkPushConstantRange pushConstantRange {};
//        pushConstantRange.stageFlags = stageFlags;
//        pushConstantRange.offset = offset;
//        pushConstantRange.size = size;
//        return pushConstantRange;
//    }
//
//    inline VkBindSparseInfo bindSparseInfo()
//    {
//        VkBindSparseInfo bindSparseInfo{};
//        bindSparseInfo.sType = VK_STRUCTURE_TYPE_BIND_SPARSE_INFO;
//        return bindSparseInfo;
//    }
//
//    /** @brief Initialize a map entry for a shader specialization constant */
//    inline VkSpecializationMapEntry specializationMapEntry(uint32_t constantID, uint32_t offset, size_t size)
//    {
//        VkSpecializationMapEntry specializationMapEntry{};
//        specializationMapEntry.constantID = constantID;
//        specializationMapEntry.offset = offset;
//        specializationMapEntry.size = size;
//        return specializationMapEntry;
//    }
//
//    /** @brief Initialize a specialization constant info structure to pass to a shader stage */
//    inline VkSpecializationInfo specializationInfo(uint32_t mapEntryCount, const VkSpecializationMapEntry* mapEntries, size_t dataSize, const void* data)
//    {
//        VkSpecializationInfo specializationInfo{};
//        specializationInfo.mapEntryCount = mapEntryCount;
//        specializationInfo.pMapEntries = mapEntries;
//        specializationInfo.dataSize = dataSize;
//        specializationInfo.pData = data;
//        return specializationInfo;
//    }
}