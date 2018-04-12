package vkn

import glfw_.advance
import glfw_.appBuffer
import glfw_.appBuffer.ptr
import glm_.*
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.reflect.KMutableProperty0

object vk {

    inline fun ApplicationInfo(block: VkApplicationInfo.() -> Unit): VkApplicationInfo = VkApplicationInfo.create(ptr.advance(VkApplicationInfo.SIZEOF)).also(block)

    inline fun InstanceCreateInfo(block: VkInstanceCreateInfo.() -> Unit): VkInstanceCreateInfo = VkInstanceCreateInfo.create(ptr.advance(VkInstanceCreateInfo.SIZEOF)).also(block)

    inline fun DebugReportCallbackCreateInfoEXT(block: VkDebugReportCallbackCreateInfoEXT.() -> Unit): VkDebugReportCallbackCreateInfoEXT = VkDebugReportCallbackCreateInfoEXT.create(ptr.advance(VkDebugReportCallbackCreateInfoEXT.SIZEOF)).also(block)

    inline fun DeviceQueueCreateInfo(block: VkDeviceQueueCreateInfo.() -> Unit): VkDeviceQueueCreateInfo = VkDeviceQueueCreateInfo.create(ptr.advance(VkDeviceQueueCreateInfo.SIZEOF)).also(block)
    inline fun DeviceQueueCreateInfo(capacity: Int): VkDeviceQueueCreateInfo.Buffer = VkDeviceQueueCreateInfo.create(ptr.advance(VkDeviceQueueCreateInfo.SIZEOF * capacity), capacity)

    inline fun SurfaceFormatKHR(capacity: Int): VkSurfaceFormatKHR.Buffer = VkSurfaceFormatKHR.create(ptr.advance(VkSurfaceFormatKHR.SIZEOF * capacity), capacity)

    inline fun DeviceCreateInfo(block: VkDeviceCreateInfo.() -> Unit): VkDeviceCreateInfo = VkDeviceCreateInfo.create(ptr.advance(VkDeviceCreateInfo.SIZEOF)).also(block)

    inline fun CommandPoolCreateInfo(block: VkCommandPoolCreateInfo.() -> Unit): VkCommandPoolCreateInfo = VkCommandPoolCreateInfo.create(ptr.advance(VkCommandPoolCreateInfo.SIZEOF)).also(block)

    inline fun FormatProperties(block: VkFormatProperties.() -> Unit): VkFormatProperties = VkFormatProperties.create(ptr.advance(VkFormatProperties.SIZEOF)).also(block)

    inline fun SemaphoreCreateInfo(block: VkSemaphoreCreateInfo.() -> Unit): VkSemaphoreCreateInfo = VkSemaphoreCreateInfo.create(ptr.advance(VkSemaphoreCreateInfo.SIZEOF)).also(block)

    inline fun SubmitInfo(block: VkSubmitInfo.() -> Unit): VkSubmitInfo = VkSubmitInfo.create(ptr.advance(VkSubmitInfo.SIZEOF)).also(block)

    inline fun SurfaceCapabilitiesKHR(block: VkSurfaceCapabilitiesKHR.() -> Unit): VkSurfaceCapabilitiesKHR = VkSurfaceCapabilitiesKHR.create(ptr.advance(VkSurfaceCapabilitiesKHR.SIZEOF)).also(block)

    inline fun Extent2D(block: VkExtent2D.() -> Unit): VkExtent2D = VkExtent2D.create(ptr.advance(VkExtent2D.SIZEOF)).also(block)

    inline fun SwapchainCreateInfoKHR(block: VkSwapchainCreateInfoKHR.() -> Unit): VkSwapchainCreateInfoKHR = VkSwapchainCreateInfoKHR.create(ptr.advance(VkSwapchainCreateInfoKHR.SIZEOF)).also(block)

    inline fun ImageViewCreateInfo(block: VkImageViewCreateInfo.() -> Unit): VkImageViewCreateInfo = VkImageViewCreateInfo.create(ptr.advance(VkImageViewCreateInfo.SIZEOF)).also(block)

    inline fun CommandBufferAllocateInfo(block: VkCommandBufferAllocateInfo.() -> Unit): VkCommandBufferAllocateInfo = VkCommandBufferAllocateInfo.create(ptr.advance(VkCommandBufferAllocateInfo.SIZEOF)).also(block)

    inline fun ImageCreateInfo(block: VkImageCreateInfo.() -> Unit): VkImageCreateInfo = VkImageCreateInfo.create(ptr.advance(VkImageCreateInfo.SIZEOF)).also(block)

    inline fun MemoryAllocateInfo(block: VkMemoryAllocateInfo.() -> Unit): VkMemoryAllocateInfo = VkMemoryAllocateInfo.create(ptr.advance(VkMemoryAllocateInfo.SIZEOF)).also(block)

    inline fun MemoryRequirements(block: VkMemoryRequirements.() -> Unit): VkMemoryRequirements = VkMemoryRequirements.create(ptr.advance(VkMemoryRequirements.SIZEOF)).also(block)

    inline fun RenderPassCreateInfo(block: VkRenderPassCreateInfo.() -> Unit): VkRenderPassCreateInfo = VkRenderPassCreateInfo.create(ptr.advance(VkRenderPassCreateInfo.SIZEOF)).also(block)

    inline fun PipelineCacheCreateInfo(block: VkPipelineCacheCreateInfo.() -> Unit): VkPipelineCacheCreateInfo = VkPipelineCacheCreateInfo.create(ptr.advance(VkPipelineCacheCreateInfo.SIZEOF)).also(block)

    inline fun FramebufferCreateInfo(block: VkFramebufferCreateInfo.() -> Unit): VkFramebufferCreateInfo = VkFramebufferCreateInfo.create(ptr.advance(VkFramebufferCreateInfo.SIZEOF)).also(block)

    inline fun FenceCreateInfo(block: VkFenceCreateInfo.() -> Unit): VkFenceCreateInfo = VkFenceCreateInfo.create(ptr.advance(VkFenceCreateInfo.SIZEOF)).also(block)

    inline fun BufferCreateInfo(block: VkBufferCreateInfo.() -> Unit): VkBufferCreateInfo = VkBufferCreateInfo.create(ptr.advance(VkBufferCreateInfo.SIZEOF)).also(block)

    inline fun CommandBufferBeginInfo(block: VkCommandBufferBeginInfo.() -> Unit): VkCommandBufferBeginInfo = VkCommandBufferBeginInfo.create(ptr.advance(VkCommandBufferBeginInfo.SIZEOF)).also(block)

    inline fun DescriptorSetLayoutCreateInfo(block: VkDescriptorSetLayoutCreateInfo.() -> Unit): VkDescriptorSetLayoutCreateInfo = VkDescriptorSetLayoutCreateInfo.create(ptr.advance(VkDescriptorSetLayoutCreateInfo.SIZEOF)).also(block)

    inline fun PipelineLayoutCreateInfo(block: VkPipelineLayoutCreateInfo.() -> Unit): VkPipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.create(ptr.advance(VkPipelineLayoutCreateInfo.SIZEOF)).also(block)

    inline fun PipelineInputAssemblyStateCreateInfo(block: VkPipelineInputAssemblyStateCreateInfo.() -> Unit): VkPipelineInputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.create(ptr.advance(VkPipelineInputAssemblyStateCreateInfo.SIZEOF)).also(block)

    inline fun PipelineRasterizationStateCreateInfo(block: VkPipelineRasterizationStateCreateInfo.() -> Unit): VkPipelineRasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.create(ptr.advance(VkPipelineRasterizationStateCreateInfo.SIZEOF)).also(block)

    inline fun PipelineColorBlendStateCreateInfo(block: VkPipelineColorBlendStateCreateInfo.() -> Unit): VkPipelineColorBlendStateCreateInfo = VkPipelineColorBlendStateCreateInfo.create(ptr.advance(VkPipelineColorBlendStateCreateInfo.SIZEOF)).also(block)

    inline fun PipelineViewportStateCreateInfo(block: VkPipelineViewportStateCreateInfo.() -> Unit): VkPipelineViewportStateCreateInfo = VkPipelineViewportStateCreateInfo.create(ptr.advance(VkPipelineViewportStateCreateInfo.SIZEOF)).also(block)

    inline fun PipelineDynamicStateCreateInfo(block: VkPipelineDynamicStateCreateInfo.() -> Unit): VkPipelineDynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.create(ptr.advance(VkPipelineDynamicStateCreateInfo.SIZEOF)).also(block)

    inline fun PipelineDepthStencilStateCreateInfo(block: VkPipelineDepthStencilStateCreateInfo.() -> Unit): VkPipelineDepthStencilStateCreateInfo = VkPipelineDepthStencilStateCreateInfo.create(ptr.advance(VkPipelineDepthStencilStateCreateInfo.SIZEOF)).also(block)

    inline fun PipelineMultisampleStateCreateInfo(block: VkPipelineMultisampleStateCreateInfo.() -> Unit): VkPipelineMultisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.create(ptr.advance(VkPipelineMultisampleStateCreateInfo.SIZEOF)).also(block)

    inline fun PipelineVertexInputStateCreateInfo(block: VkPipelineVertexInputStateCreateInfo.() -> Unit): VkPipelineVertexInputStateCreateInfo = VkPipelineVertexInputStateCreateInfo.create(ptr.advance(VkPipelineVertexInputStateCreateInfo.SIZEOF)).also(block)

    inline fun ShaderModuleCreateInfo(block: VkShaderModuleCreateInfo.() -> Unit): VkShaderModuleCreateInfo = VkShaderModuleCreateInfo.create(ptr.advance(VkShaderModuleCreateInfo.SIZEOF)).also(block)

    inline fun DescriptorPoolCreateInfo(block: VkDescriptorPoolCreateInfo.() -> Unit): VkDescriptorPoolCreateInfo = VkDescriptorPoolCreateInfo.create(ptr.advance(VkDescriptorPoolCreateInfo.SIZEOF)).also(block)

    inline fun DescriptorSetAllocateInfo(block: VkDescriptorSetAllocateInfo.() -> Unit): VkDescriptorSetAllocateInfo = VkDescriptorSetAllocateInfo.create(ptr.advance(VkDescriptorSetAllocateInfo.SIZEOF)).also(block)

    inline fun RenderPassBeginInfo(block: VkRenderPassBeginInfo.() -> Unit): VkRenderPassBeginInfo = VkRenderPassBeginInfo.create(ptr.advance(VkRenderPassBeginInfo.SIZEOF)).also(block)


    inline fun ExtensionProperties(capacity: Int): VkExtensionProperties.Buffer = VkExtensionProperties.create(ptr.advance(VkExtensionProperties.SIZEOF * capacity), capacity)

    inline fun PipelineShaderStageCreateInfo(capacity: Int): VkPipelineShaderStageCreateInfo.Buffer = VkPipelineShaderStageCreateInfo.create(ptr.advance(VkPipelineShaderStageCreateInfo.SIZEOF * capacity), capacity)

    inline fun VertexInputAttributeDescription(capacity: Int): VkVertexInputAttributeDescription.Buffer = VkVertexInputAttributeDescription.create(ptr.advance(VkVertexInputAttributeDescription.SIZEOF * capacity), capacity)

    inline fun BufferCopy(capacity: Int): VkBufferCopy.Buffer = VkBufferCopy.create(ptr.advance(VkBufferCopy.SIZEOF * capacity), capacity)

    inline fun AttachmentDescription(capacity: Int): VkAttachmentDescription.Buffer = VkAttachmentDescription.create(ptr.advance(VkAttachmentDescription.SIZEOF * capacity), capacity)

    inline fun SubpassDependency(capacity: Int): VkSubpassDependency.Buffer = VkSubpassDependency.create(ptr.advance(VkSubpassDependency.SIZEOF * capacity), capacity)

    inline fun ClearValue(capacity: Int): VkClearValue.Buffer = VkClearValue.create(ptr.advance(VkClearValue.SIZEOF * capacity), capacity)

    inline fun AttachmentReference(block: VkAttachmentReference.() -> Unit): VkAttachmentReference = VkAttachmentReference.create(ptr.advance(VkAttachmentReference.SIZEOF)).also(block)
    inline fun AttachmentReference(capacity: Int, block: VkAttachmentReference.() -> Unit): VkAttachmentReference.Buffer = VkAttachmentReference.create(ptr.advance(VkAttachmentReference.SIZEOF * capacity), capacity).also { it[0].block() }

    inline fun DescriptorSetLayoutBinding(capacity: Int, block: VkDescriptorSetLayoutBinding.() -> Unit): VkDescriptorSetLayoutBinding.Buffer = VkDescriptorSetLayoutBinding.create(ptr.advance(VkDescriptorSetLayoutBinding.SIZEOF * capacity), capacity).also { it[0].block() }

    inline fun SubpassDescription(block: VkSubpassDescription.() -> Unit): VkSubpassDescription = VkSubpassDescription.create(ptr.advance(VkSubpassDescription.SIZEOF)).also(block)
    inline fun SubpassDescription(capacity: Int, block: VkSubpassDescription.() -> Unit): VkSubpassDescription.Buffer = VkSubpassDescription.create(ptr.advance(VkSubpassDescription.SIZEOF * capacity), capacity).also { it[0].block() }

    inline fun GraphicsPipelineCreateInfo(capacity: Int, block: VkGraphicsPipelineCreateInfo.() -> Unit): VkGraphicsPipelineCreateInfo.Buffer = VkGraphicsPipelineCreateInfo.create(ptr.advance(VkGraphicsPipelineCreateInfo.SIZEOF * capacity), capacity).also { it[0].block() }

    inline fun PipelineColorBlendAttachmentState(capacity: Int, block: VkPipelineColorBlendAttachmentState.() -> Unit): VkPipelineColorBlendAttachmentState.Buffer = VkPipelineColorBlendAttachmentState.create(ptr.advance(VkPipelineColorBlendAttachmentState.SIZEOF * capacity), capacity).also { it[0].block() }

    inline fun VertexInputBindingDescription(capacity: Int, block: VkVertexInputBindingDescription.() -> Unit): VkVertexInputBindingDescription.Buffer = VkVertexInputBindingDescription.create(ptr.advance(VkVertexInputBindingDescription.SIZEOF * capacity), capacity).also { it[0].block() }

    inline fun DescriptorPoolSize(capacity: Int, block: VkDescriptorPoolSize.() -> Unit): VkDescriptorPoolSize.Buffer = VkDescriptorPoolSize.create(ptr.advance(VkDescriptorPoolSize.SIZEOF * capacity), capacity).also { it[0].block() }

    inline fun WriteDescriptorSet(capacity: Int, block: VkWriteDescriptorSet.() -> Unit): VkWriteDescriptorSet.Buffer = VkWriteDescriptorSet.create(ptr.advance(VkWriteDescriptorSet.SIZEOF * capacity), capacity).also { it[0].block() }

    inline fun Viewport(capacity: Int, block: VkViewport.() -> Unit): VkViewport.Buffer = VkViewport.create(ptr.advance(VkViewport.SIZEOF * capacity), capacity).also { it[0].block() }

    inline fun Rect2D(capacity: Int, block: VkRect2D.() -> Unit): VkRect2D.Buffer = VkRect2D.create(ptr.advance(VkRect2D.SIZEOF * capacity), capacity).also { it[0].block() }


    inline fun createCommandPool(device: VkDevice, createInfo: VkCommandPoolCreateInfo, commandPool: LongBuffer) = VkResult of VK10.nvkCreateCommandPool(device, createInfo.adr, NULL, memAddress(commandPool))


    inline fun createInstance(createInfo: VkInstanceCreateInfo, instance: KMutableProperty0<VkInstance>): VkResult {
        val pInstance = appBuffer.pointer
        val res = VK10.nvkCreateInstance(createInfo.adr, NULL, pInstance)
        instance.set(VkInstance(MemoryUtil.memGetLong(pInstance), createInfo))
        return VkResult of res
    }

    inline fun createDebugReportCallbackEXT(instance: VkInstance, createInfo: VkDebugReportCallbackCreateInfoEXT,
                                            callback: KMutableProperty0<Long>): VkResult {
        val long = appBuffer.long
        return VkResult of EXTDebugReport.nvkCreateDebugReportCallbackEXT(instance, createInfo.adr, NULL, long).also {
            callback.set(MemoryUtil.memGetLong(long))
        }
    }

    inline fun enumeratePhysicalDevices(instance: VkInstance): ArrayList<VkPhysicalDevice> {
        // Physical device
        val pCount = appBuffer.int
        // Get number of available physical devices
        VK_CHECK_RESULT(VK10.nvkEnumeratePhysicalDevices(instance, pCount, NULL))
        // Enumerate devices
        val count = memGetInt(pCount)
        val devices = appBuffer.pointerBuffer(count)
        VK_CHECK_RESULT(VK10.nvkEnumeratePhysicalDevices(instance, pCount, devices.adr))
        val res = arrayListOf<VkPhysicalDevice>()
        for (i in 0 until count)
            res += VkPhysicalDevice(devices[i], instance)
        return res
    }

    inline fun getPhysicalDeviceQueueFamilyProperties(physicalDevice: VkPhysicalDevice): ArrayList<VkQueueFamilyProperties> {
        val pCount = appBuffer.int
        VK10.nvkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pCount, NULL)
        val count = memGetInt(pCount)
        val pQueueFamilyProperties = VkQueueFamilyProperties.calloc(count)
        VK10.nvkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pCount, pQueueFamilyProperties.adr)
        return pQueueFamilyProperties.toCollection(arrayListOf())
    }

    inline fun enumerateDeviceExtensionProperties(physicalDevice: VkPhysicalDevice, layerName: String? = null): ArrayList<String> {
        val pCount = appBuffer.int
        val pLayerName = layerName?.utf8?.let(::memAddress) ?: NULL
        VK_CHECK_RESULT(VK10.nvkEnumerateDeviceExtensionProperties(physicalDevice, pLayerName, pCount, NULL))
        val count = memGetInt(pCount)
        val res = ArrayList<String>(count)
        if (count > 0) {
            val properties = ExtensionProperties(count)
            VK_CHECK_RESULT(VK10.nvkEnumerateDeviceExtensionProperties(physicalDevice, pLayerName, pCount, properties.adr))
            properties.map { it.extensionNameString() }.toCollection(res)
        }
        return res
    }

    inline fun createDevice(physicalDevice: VkPhysicalDevice, createInfo: VkDeviceCreateInfo, device: KMutableProperty0<VkDevice?>)
            : VkResult {
        val pDevice = appBuffer.pointer
        return VkResult of VK10.nvkCreateDevice(physicalDevice, createInfo.adr, NULL, pDevice).also {
            device.set(VkDevice(memGetLong(pDevice), physicalDevice, createInfo))
        }
    }

    inline fun getDeviceQueue(device: VkDevice, queueFamilyIndex: Int, queueIndex: Int, queue: KMutableProperty0<VkQueue>) {
        val pQueue = appBuffer.pointer
        VK10.nvkGetDeviceQueue(device, queueFamilyIndex, queueIndex, pQueue)
        queue.set(VkQueue(memGetLong(pQueue), device))
    }

    inline fun getPhysicalDeviceFormatProperties(physicalDevice: VkPhysicalDevice, format: VkFormat): VkFormatProperties =
            FormatProperties {
                VK10.nvkGetPhysicalDeviceFormatProperties(physicalDevice, format.i, adr)
            }

    inline fun createSemaphore(device: VkDevice, createInfo: VkSemaphoreCreateInfo, semaphore: KMutableProperty0<VkSemaphore>): VkResult {
        val pSemaphore = appBuffer.long
        return VkResult of VK10.nvkCreateSemaphore(device, createInfo.adr, NULL, pSemaphore).also {
            semaphore.set(memGetLong(pSemaphore))
        }
    }

    inline fun createSemaphore(device: VkDevice, createInfo: VkSemaphoreCreateInfo, semaphore: VkSemaphorePtr) =
            VkResult of VK10.nvkCreateSemaphore(device, createInfo.adr, NULL, memAddress(semaphore))

    inline fun getPhysicalDeviceSurfaceFormatsKHR(physicalDevice: VkPhysicalDevice, surface: VkSurfaceKHR): ArrayList<VkSurfaceFormatKHR> {
        val pCount = appBuffer.int
        VK_CHECK_RESULT(KHRSurface.nvkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pCount, NULL))
        val count = memGetInt(pCount)
        assert(count > 0)
        val surfaceFormats = SurfaceFormatKHR(count)
        VK_CHECK_RESULT(KHRSurface.nvkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, pCount, surfaceFormats.adr))
        return surfaceFormats.toCollection(arrayListOf())
    }

    inline fun createCommandPool(device: VkDevice, createInfo: VkCommandPoolCreateInfo, commandPool: KMutableProperty0<VkCommandPool>): VkResult {
        val pCommandPool = appBuffer.long
        return VkResult of VK10.nvkCreateCommandPool(device, createInfo.adr, NULL, pCommandPool).also {
            commandPool.set(memGetLong(pCommandPool))
        }
    }

    inline fun getPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice: VkPhysicalDevice, surface: VkSurfaceKHR): VkSurfaceCapabilitiesKHR =
            SurfaceCapabilitiesKHR {
                VK_CHECK_RESULT(KHRSurface.nvkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, adr))
            }

    inline fun getPhysicalDeviceSurfacePresentModesKHR(physicalDevice: VkPhysicalDevice, surface: VkSurfaceKHR): ArrayList<VkPresentMode> {
        val pCount = appBuffer.int
        VK_CHECK_RESULT(KHRSurface.nvkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pCount, NULL))
        val count = memGetInt(pCount)
        assert(count > 0)
        val presentModes = appBuffer.intArray(count)
        KHRSurface.nvkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, pCount, presentModes)
        val res = ArrayList<VkPresentMode>()
        for (i in 0 until count) res += VkPresentMode of memGetInt(presentModes + Int.BYTES * i)
        return res
    }

    inline fun createSwapchainKHR(device: VkDevice, createInfo: VkSwapchainCreateInfoKHR, swapchain: KMutableProperty0<VkSwapchainKHR>)
            : VkResult {
        val pSwapchain = appBuffer.long
        return VkResult of KHRSwapchain.nvkCreateSwapchainKHR(device, createInfo.adr, NULL, pSwapchain).also {
            swapchain.set(memGetLong(pSwapchain))
        }
    }

    inline fun destroyImageView(device: VkDevice, imageView: VkImageView) = VK10.nvkDestroyImageView(device, imageView, NULL)
    inline fun destroySwapchainKHR(device: VkDevice, swapchain: VkSwapchainKHR) = KHRSwapchain.nvkDestroySwapchainKHR(device, swapchain, NULL)

    inline fun getSwapchainImagesKHR(device: VkDevice, swapchain: VkSwapchainKHR): ArrayList<VkImageView> {
        val pCount = appBuffer.int
        VK_CHECK_RESULT(KHRSwapchain.nvkGetSwapchainImagesKHR(device, swapchain, pCount, NULL))
        val count = memGetInt(pCount)
        val images = appBuffer.longArray(count)
        VK_CHECK_RESULT(KHRSwapchain.nvkGetSwapchainImagesKHR(device, swapchain, pCount, images))
        val res = ArrayList<VkImageView>()
        for (i in 0 until count) res += memGetLong(images + Long.BYTES * i)
        return res
    }

    inline fun createImageView(device: VkDevice, createInfo: VkImageViewCreateInfo, view: KMutableProperty0<VkImageView>): VkResult {
        val pView = appBuffer.long
        return VkResult of VK10.nvkCreateImageView(device, createInfo.adr, NULL, pView).also {
            view.set(memGetLong(pView))
        }
    }

    inline fun allocateCommandBuffers(device: VkDevice, allocateInfo: VkCommandBufferAllocateInfo, count: Int, commandBuffers: ArrayList<VkCommandBuffer>)
            : VkResult {
        val pCommandBuffer = appBuffer.pointerArray(count)
        return VkResult of VK10.nvkAllocateCommandBuffers(device, allocateInfo.adr, pCommandBuffer).also {
            for (i in 0 until count)
                commandBuffers += VkCommandBuffer(memGetAddress(pCommandBuffer + Pointer.POINTER_SIZE * i), device)
        }
    }

    inline fun createImage(device: VkDevice, createInfo: VkImageCreateInfo, image: KMutableProperty0<VkImage>): VkResult {
        val pImage = appBuffer.long
        return VkResult of VK10.nvkCreateImage(device, createInfo.adr, NULL, pImage).also {
            image.set(memGetLong(pImage))
        }
    }

    inline fun allocateMemory(device: VkDevice, allocateInfo: VkMemoryAllocateInfo, memory: KMutableProperty0<VkDeviceMemory>): VkResult {
        val pMemory = appBuffer.long
        return VkResult of VK10.nvkAllocateMemory(device, allocateInfo.adr, NULL, pMemory).also {
            memory.set(memGetLong(pMemory))
        }
    }

    inline fun createRenderPass(device: VkDevice, createInfo: VkRenderPassCreateInfo, renderPass: KMutableProperty0<VkRenderPass>): VkResult {
        val pRenderPass = appBuffer.long
        return VkResult of VK10.nvkCreateRenderPass(device, createInfo.adr, NULL, pRenderPass).also {
            renderPass.set(memGetLong(pRenderPass))
        }
    }

    inline fun createPipelineCache(device: VkDevice, createInfo: VkPipelineCacheCreateInfo, pipelineCache: KMutableProperty0<VkPipelineCache>): VkResult {
        val pPipelineCache = appBuffer.long
        return VkResult of VK10.nvkCreatePipelineCache(device, createInfo.adr, NULL, pPipelineCache).also {
            pipelineCache.set(memGetLong(pPipelineCache))
        }
    }

    inline fun createFramebuffer(device: VkDevice, createInfo: VkFramebufferCreateInfo, framebuffer: ArrayList<VkFramebuffer>, index: Int): VkResult {
        val pFramebuffer = appBuffer.long
        return VkResult of VK10.nvkCreateFramebuffer(device, createInfo.adr, NULL, pFramebuffer).also {
            framebuffer[index] = memGetLong(pFramebuffer)
        }
    }

    inline fun createFence(device: VkDevice, createInfo: VkFenceCreateInfo, fence: LongBuffer): VkResult {
        val pFence = appBuffer.long
        return VkResult of VK10.nvkCreateFence(device, createInfo.adr, NULL, pFence).also {
            fence[0] = memGetLong(pFence)
        }
    }

    inline fun createFences(device: VkDevice, createInfo: VkFenceCreateInfo, fences: ArrayList<VkFence>) {
        val pFence = appBuffer.long
        for (i in fences.indices) {
            VK_CHECK_RESULT(VK10.nvkCreateFence(device, createInfo.adr, NULL, pFence))
            fences[i] = memGetLong(pFence)
        }
    }

    inline fun createBuffer(device: VkDevice, createInfo: VkBufferCreateInfo, buffer: KMutableProperty0<VkBuffer>): VkResult {
        val pBuffer = appBuffer.long
        return VkResult of VK10.nvkCreateBuffer(device, createInfo.adr, NULL, pBuffer).also {
            buffer.set(memGetLong(pBuffer))
        }
    }

    inline fun createDescriptorSetLayout(device: VkDevice, createInfo: VkDescriptorSetLayoutCreateInfo,
                                         setLayout: KMutableProperty0<VkDescriptorSetLayout>): VkResult {
        val pSetLayout = appBuffer.long
        return VkResult of VK10.nvkCreateDescriptorSetLayout(device, createInfo.adr, NULL, pSetLayout).also {
            setLayout.set(memGetLong(pSetLayout))
        }
    }

    inline fun createPipelineLayout(device: VkDevice, createInfo: VkPipelineLayoutCreateInfo,
                                    pipelineLayout: KMutableProperty0<VkPipelineLayout>): VkResult {
        val pPipelineLayout = appBuffer.long
        return VkResult of VK10.nvkCreatePipelineLayout(device, createInfo.adr, NULL, pPipelineLayout).also {
            pipelineLayout.set(memGetLong(pPipelineLayout))
        }
    }

    inline fun createShaderModule(device: VkDevice, createInfo: VkShaderModuleCreateInfo, shaderModule: LongBuffer): VkResult =
            VkResult of VK10.nvkCreateShaderModule(device, createInfo.adr, NULL, memAddress(shaderModule))

    inline fun createGraphicsPipelines(device: VkDevice, pipelineCache: VkPipelineCache, createInfos: VkGraphicsPipelineCreateInfo.Buffer,
                                       pipelines: KMutableProperty0<VkPipeline>): VkResult {
        val pPipelines = appBuffer.long
        return VkResult of VK10.nvkCreateGraphicsPipelines(device, pipelineCache, createInfos.remaining(), createInfos.adr, NULL, pPipelines).also {
            pipelines.set(memGetLong(pPipelines))
        }
    }

    inline fun createDescriptorPool(device: VkDevice, createInfo: VkDescriptorPoolCreateInfo, descriptorPool: KMutableProperty0<VkDescriptorPool>): VkResult {
        val pDescriptorPool = appBuffer.long
        return VkResult of VK10.nvkCreateDescriptorPool(device, createInfo.adr, NULL, pDescriptorPool).also {
            descriptorPool.set(memGetLong(pDescriptorPool))
        }
    }

    inline fun allocateDescriptorSets(device: VkDevice, allocateInfo: VkDescriptorSetAllocateInfo,
                                      descriptorSets: KMutableProperty0<VkDescriptorSet>): VkResult {
        val pDescriptorSets = appBuffer.long
        return VkResult of VK10.nvkAllocateDescriptorSets(device, allocateInfo.adr, pDescriptorSets).also {
            descriptorSets.set(memGetLong(pDescriptorSets))
        }
    }

    inline fun updateDescriptorSets(device: VkDevice, descriptorWrites: VkWriteDescriptorSet.Buffer, descriptorCopies: VkCopyDescriptorSet.Buffer? = null) =
            VK10.nvkUpdateDescriptorSets(device, descriptorWrites.remaining(), descriptorWrites.adr, descriptorCopies?.remaining()
                    ?: 0, descriptorCopies?.adr ?: NULL)

    inline fun cmdBindDescriptorSets(commandBuffer: VkCommandBuffer, pipelineBindPoint: VkPipelineBindPoint, layout: VkPipelineLayout,
                                     firstSet: Int, descriptorSets: KMutableProperty0<VkDescriptorSet>, dynamicOffsets: IntBuffer? = null) {
        val pDescriptorSets = appBuffer.long
        memPutLong(pDescriptorSets, descriptorSets())
        VK10.nvkCmdBindDescriptorSets(commandBuffer, pipelineBindPoint.i, layout, firstSet, 1, pDescriptorSets,
                dynamicOffsets?.remaining() ?: 0, dynamicOffsets?.let(::memAddress) ?: NULL).also {
            descriptorSets.set(memGetLong(pDescriptorSets))
        }
    }

    inline fun cmdBindVertexBuffer(commandBuffer: VkCommandBuffer, firstBinding: Int, buffer: KMutableProperty0<VkBuffer>) {
        val pBuffer = appBuffer.long
        memPutLong(pBuffer, buffer())
        val pOffset = appBuffer.long
        memPutLong(pOffset, 0L) // TODO remove since calloc?
        VK10.nvkCmdBindVertexBuffers(commandBuffer, firstBinding, 1, pBuffer, pOffset)
        buffer.set(memGetLong(pBuffer))
    }

    inline fun cmdBeginRenderPass(commandBuffer: VkCommandBuffer, renderPassBegin: VkRenderPassBeginInfo, contents: VkSubpassContents) {
        VK10.nvkCmdBeginRenderPass(commandBuffer, renderPassBegin.adr, contents.i)
    }

    inline fun cmdBindIndexBuffer(commandBuffer: VkCommandBuffer, buffer: VkBuffer, offset: VkDeviceSize, indexType: VkIndexType) {
        VK10.vkCmdBindIndexBuffer(commandBuffer, buffer, offset, indexType.i)
    }


    inline fun destroyFence(device: VkDevice, fence: VkFence) = VK10.nvkDestroyFence(device, fence, NULL)
    inline fun destroyBuffer(device: VkDevice, buffer: VkBuffer) = VK10.nvkDestroyBuffer(device, buffer, NULL)
    inline fun freeMemory(device: VkDevice, memory: VkDeviceMemory) = VK10.nvkFreeMemory(device, memory, NULL)
    inline fun destroyShaderModule(device: VkDevice, shaderModules: Iterable<VkShaderModule>, allocator: VkAllocationCallbacks? = null) {
        for (i in shaderModules)
            VK10.nvkDestroyShaderModule(device, i, allocator?.address() ?: NULL)
    }

    inline fun destroyShaderModule(device: VkDevice, shaderModules: VkPipelineShaderStageCreateInfo.Buffer, allocator: VkAllocationCallbacks? = null) {
        for (i in shaderModules)
            VK10.nvkDestroyShaderModule(device, i.module, allocator?.adr ?: NULL)
    }
}