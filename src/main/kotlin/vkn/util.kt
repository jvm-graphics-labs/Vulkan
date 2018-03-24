package vkn

import glm_.vec2.Vec2i
import glm_.vec3.Vec3i
import org.lwjgl.PointerBuffer
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Pointer
import org.lwjgl.vulkan.*
import vkn.VkMemoryStack.Companion.withStack
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import kotlin.reflect.KMutableProperty0


fun vkEnumeratePhysicalDevices(instance: VkInstance, physicalDevices: ArrayList<VkPhysicalDevice>): VkResult = withStack {
    // Physical device
    val count = mallocInt()
    // Get number of available physical devices
    VK10.vkEnumeratePhysicalDevices(instance, count, null).check()
    // Enumerate devices
    val devices = mallocPointer(count)
    val ret = VK10.vkEnumeratePhysicalDevices(instance, count, devices)
    for (i in 0 until count)
        physicalDevices += VkPhysicalDevice(devices[i], instance)
    return ret
}

fun vkGetDeviceQueue(device: VkDevice, queueFamilyIndex: Int, queueIndex: Int, queue: KMutableProperty0<VkQueue>) = withStack {
    val pQueue = mallocPointer()
    VK10.vkGetDeviceQueue(device, queueFamilyIndex, queueIndex, pQueue)
    queue.set(VkQueue(pQueue[0], device))
}


var VkSemaphoreCreateInfo.type: VkStructureType
    get() = sType()
    set(value) {
        sType(value)
    }
var VkSemaphoreCreateInfo.next
    get() = pNext()
    set(value) {
        pNext(value)
    }
var VkSemaphoreCreateInfo.flags: VkSemaphoreCreateFlags
    get() = flags()
    set(value) {
        flags(value)
    }


val VkSurfaceFormatKHR.format: VkFormat get() = format()
val VkSurfaceFormatKHR.colorSpace: VkColorSpaceKHR get() = colorSpace()

val VkSurfaceCapabilitiesKHR.minImageCount get() = minImageCount()
val VkSurfaceCapabilitiesKHR.maxImageCount get() = maxImageCount()
val VkSurfaceCapabilitiesKHR.currentExtent: VkExtent2D get() = currentExtent()
val VkSurfaceCapabilitiesKHR.minImageExtent: VkExtent2D get() = minImageExtent()
val VkSurfaceCapabilitiesKHR.maxImageExtent: VkExtent2D get() = maxImageExtent()
val VkSurfaceCapabilitiesKHR.supportedTransforms: VkSurfaceTransformFlagsKHR get() = supportedTransforms()
val VkSurfaceCapabilitiesKHR.currentTransform: VkSurfaceTransformFlagBitsKHR get() = currentTransform()
val VkSurfaceCapabilitiesKHR.supportedCompositeAlpha: VkCompositeAlphaFlagsKHR get() = supportedCompositeAlpha()
val VkSurfaceCapabilitiesKHR.supportedUsageFlags: VkImageUsageFlags get() = supportedUsageFlags()


var VkSwapchainCreateInfoKHR.type: VkStructureType
    get() = sType()
    set(value) {
        sType(value)
    }
var VkSwapchainCreateInfoKHR.next: Long
    get() = pNext()
    set(value) {
        pNext(value)
    }
var VkSwapchainCreateInfoKHR.flags: VkSwapchainCreateFlagsKHR
    get() = flags()
    set(value) {
        flags(value)
    }
var VkSwapchainCreateInfoKHR.surface: VkSurfaceKHR
    get() = surface()
    set(value) {
        surface(value)
    }
var VkSwapchainCreateInfoKHR.minImageCount
    get() = minImageCount()
    set(value) {
        minImageCount(value)
    }
var VkSwapchainCreateInfoKHR.imageFormat: VkFormat
    get() = imageFormat()
    set(value) {
        imageFormat(value)
    }
var VkSwapchainCreateInfoKHR.imageColorSpace: VkFormat
    get() = imageColorSpace()
    set(value) {
        imageColorSpace(value)
    }
var VkSwapchainCreateInfoKHR.imageExtent: VkExtent2D
    get() = imageExtent()
    set(value) {
        imageExtent(value)
    }
var VkSwapchainCreateInfoKHR.imageArrayLayers
    get() = imageArrayLayers()
    set(value) {
        imageArrayLayers(value)
    }
var VkSwapchainCreateInfoKHR.imageUsage: VkImageUsageFlags
    get() = imageUsage()
    set(value) {
        imageUsage(value)
    }
var VkSwapchainCreateInfoKHR.imageSharingMode: VkSharingMode
    get() = imageSharingMode()
    set(value) {
        imageSharingMode(value)
    }
val VkSwapchainCreateInfoKHR.queueFamilyIndexCount get() = queueFamilyIndexCount()
var VkSwapchainCreateInfoKHR.queueFamilyIndices
    get() = pQueueFamilyIndices()
    set(value) {
        pQueueFamilyIndices(value)
    }
var VkSwapchainCreateInfoKHR.preTransform: VkSurfaceTransformFlagBitsKHR
    get() = preTransform()
    set(value) {
        preTransform(value)
    }
var VkSwapchainCreateInfoKHR.compositeAlpha: VkCompositeAlphaFlagBitsKHR
    get() = compositeAlpha()
    set(value) {
        compositeAlpha(value)
    }
var VkSwapchainCreateInfoKHR.presentMode: VkPresentModeKHR
    get() = presentMode()
    set(value) {
        presentMode(value)
    }
var VkSwapchainCreateInfoKHR.clipped
    get() = clipped()
    set(value) {
        clipped(value)
    }
var VkSwapchainCreateInfoKHR.oldSwapchain: VkSwapchainKHR
    get() = oldSwapchain()
    set(value) {
        oldSwapchain(value)
    }


var VkImageViewCreateInfo.type: VkStructureType
    get() = sType()
    set(value) {
        sType(value)
    }
var VkImageViewCreateInfo.next
    get() = pNext()
    set(value) {
        pNext(value)
    }
var VkImageViewCreateInfo.flags: VkImageViewCreateFlags
    get() = flags()
    set(value) {
        flags(value)
    }
var VkImageViewCreateInfo.image: VkImage
    get() = image()
    set(value) {
        image(value)
    }
var VkImageViewCreateInfo.viewType: VkImageViewType
    get() = viewType()
    set(value) {
        viewType(value)
    }
var VkImageViewCreateInfo.format: VkFormat
    get() = format()
    set(value) {
        format(value)
    }
var VkImageViewCreateInfo.components: VkComponentMapping
    get() = components()
    set(value) {
        components(value)
    }
var VkImageViewCreateInfo.subresourceRange: VkImageSubresourceRange
    get() = subresourceRange()
    set(value) {
        subresourceRange(value)
    }

var VkImageSubresourceRange.aspectMask: VkImageAspectFlags
    get() = aspectMask()
    set(value) {
        aspectMask(value)
    }
var VkImageSubresourceRange.baseMipLevel
    get() = baseMipLevel()
    set(value) {
        baseMipLevel(value)
    }
var VkImageSubresourceRange.levelCount
    get() = levelCount()
    set(value) {
        levelCount(value)
    }
var VkImageSubresourceRange.baseArrayLayer
    get() = baseArrayLayer()
    set(value) {
        baseArrayLayer(value)
    }
var VkImageSubresourceRange.layerCount
    get() = layerCount()
    set(value) {
        layerCount(value)
    }


var VkCommandBufferAllocateInfo.type: VkStructureType
    get() = sType()
    set(value) {
        sType(value)
    }
var VkCommandBufferAllocateInfo.next
    get() = pNext()
    set(value) {
        pNext(value)
    }
var VkCommandBufferAllocateInfo.commandPool: VkCommandPool
    get() = commandPool()
    set(value) {
        commandPool(value)
    }
var VkCommandBufferAllocateInfo.level: VkCommandBufferLevel
    get() = level()
    set(value) {
        level(value)
    }
var VkCommandBufferAllocateInfo.commandBufferCount
    get() = commandBufferCount()
    set(value) {
        commandBufferCount(value)
    }


var VkImageCreateInfo.type: VkStructureType
    get() = sType()
    set(value) {
        sType(value)
    }
var VkImageCreateInfo.next
    get() = pNext()
    set(value) {
        pNext(value)
    }
var VkImageCreateInfo.flags: VkImageCreateFlags
    get() = flags()
    set(value) {
        flags(value)
    }
var VkImageCreateInfo.imageType: VkImageType
    get() = imageType()
    set(value) {
        imageType(value)
    }
var VkImageCreateInfo.format: VkFormat
    get() = format()
    set(value) {
        format(value)
    }
var VkImageCreateInfo.extent: VkExtent3D
    get() = extent()
    set(value) {
        extent(value)
    }
var VkImageCreateInfo.mipLevels
    get() = mipLevels()
    set(value) {
        mipLevels(value)
    }
var VkImageCreateInfo.arrayLayers
    get() = arrayLayers()
    set(value) {
        arrayLayers(value)
    }
var VkImageCreateInfo.samples: VkSampleCountFlagBits
    get() = samples()
    set(value) {
        samples(value)
    }
var VkImageCreateInfo.tiling: VkImageTiling
    get() = tiling()
    set(value) {
        tiling(value)
    }
var VkImageCreateInfo.usage: VkImageUsageFlags
    get() = usage()
    set(value) {
        usage(value)
    }
var VkImageCreateInfo.sharingMode: VkSharingMode
    get() = sharingMode()
    set(value) {
        sharingMode(value)
    }
val VkImageCreateInfo.queueFamilyIndexCount get() = queueFamilyIndexCount()
//val VkImageCreateInfo.pQueueFamilyIndices get() = queueFamilyIndexCount()

var VkImageCreateInfo.initialLayout: VkImageLayout
    get() = initialLayout()
    set(value) {
        initialLayout(value)
    }


var VkMemoryAllocateInfo.type: VkStructureType
    get() = sType()
    set(value) {
        sType(value)
    }
var VkMemoryAllocateInfo.next
    get() = pNext()
    set(value) {
        pNext(value)
    }
var VkMemoryAllocateInfo.allocationSize
    get() = allocationSize()
    set(value) {
        allocationSize(value)
    }
var VkMemoryAllocateInfo.memoryTypeIndex
    get() = memoryTypeIndex()
    set(value) {
        memoryTypeIndex(value)
    }


val VkMemoryRequirements.size get() = size()
val VkMemoryRequirements.alignment get() = alignment()
val VkMemoryRequirements.memoryTypeBits get() = memoryTypeBits()


val VkPhysicalDeviceMemoryProperties.memoryTypeCount get() = memoryTypeCount()
val VkPhysicalDeviceMemoryProperties.memoryTypes get() = memoryTypes()

val VkMemoryType.propertyFlags get() = propertyFlags()

var VkAttachmentDescription.flags: VkAttachmentDescriptionFlags
    get() = flags()
    set(value) {
        flags(value)
    }
var VkAttachmentDescription.format: VkFormat
    get() = format()
    set(value) {
        format(value)
    }
var VkAttachmentDescription.samples: VkSampleCountFlagBits
    get() = samples()
    set(value) {
        samples(value)
    }
var VkAttachmentDescription.loadOp: VkAttachmentLoadOp
    get() = loadOp()
    set(value) {
        loadOp(value)
    }
var VkAttachmentDescription.storeOp: VkAttachmentStoreOp
    get() = storeOp()
    set(value) {
        storeOp(value)
    }
var VkAttachmentDescription.stencilLoadOp: VkAttachmentLoadOp
    get() = stencilLoadOp()
    set(value) {
        stencilLoadOp(value)
    }
var VkAttachmentDescription.stencilStoreOp: VkAttachmentStoreOp
    get() = stencilStoreOp()
    set(value) {
        stencilStoreOp(value)
    }
var VkAttachmentDescription.initialLayout: VkImageLayout
    get() = initialLayout()
    set(value) {
        initialLayout(value)
    }
var VkAttachmentDescription.finalLayout: VkImageLayout
    get() = finalLayout()
    set(value) {
        finalLayout(value)
    }


var VkAttachmentReference.attachment
    get() = attachment()
    set(value) {
        attachment(value)
    }
var VkAttachmentReference.layout: VkImageLayout
    get() = layout()
    set(value) {
        layout(value)
    }


var VkSubpassDescription.flags: VkSubpassDescriptionFlags
    get() = flags()
    set(value) {
        flags(value)
    }
var VkSubpassDescription.pipelineBindPoint: VkPipelineBindPoint
    get() = pipelineBindPoint()
    set(value) {
        pipelineBindPoint(value)
    }
val VkSubpassDescription.inputAttachmentCount get() = inputAttachmentCount()
var VkSubpassDescription.inputAttachments
    get() = pInputAttachments()
    set(value) {
        pInputAttachments(value)
    }
val VkSubpassDescription.colorAttachmentCount get() = colorAttachmentCount()
var VkSubpassDescription.colorAttachments
    get() = pColorAttachments()
    set(value) {
        pColorAttachments(value)
    }
var VkSubpassDescription.resolveAttachments
    get() = pResolveAttachments()
    set(value) {
        pResolveAttachments(value)
    }
var VkSubpassDescription.depthStencilAttachment
    get() = pDepthStencilAttachment()
    set(value) {
        pDepthStencilAttachment(value)
    }
val VkSubpassDescription.preserveAttachmentCount get() = preserveAttachmentCount()
var VkSubpassDescription.preserveAttachments
    get() = pPreserveAttachments()
    set(value) {
        pPreserveAttachments(value)
    }


var VkSubpassDependency.srcSubpass
    get() = srcSubpass()
    set(value) {
        srcSubpass(value)
    }
var VkSubpassDependency.dstSubpass
    get() = dstSubpass()
    set(value) {
        dstSubpass(value)
    }
var VkSubpassDependency.srcStageMask: VkPipelineStageFlags
    get() = srcStageMask()
    set(value) {
        srcStageMask(value)
    }
var VkSubpassDependency.dstStageMask: VkPipelineStageFlags
    get() = dstStageMask()
    set(value) {
        dstStageMask(value)
    }
var VkSubpassDependency.srcAccessMask: VkAccessFlags
    get() = srcAccessMask()
    set(value) {
        srcAccessMask(value)
    }
var VkSubpassDependency.dstAccessMask: VkAccessFlags
    get() = dstAccessMask()
    set(value) {
        dstAccessMask(value)
    }
var VkSubpassDependency.dependencyFlags: VkDependencyFlags
    get() = dstAccessMask()
    set(value) {
        dstAccessMask(value)
    }


var VkRenderPassCreateInfo.type: VkStructureType
    get() = sType()
    set(value) {
        sType(value)
    }
var VkRenderPassCreateInfo.next
    get() = pNext()
    set(value) {
        pNext(value)
    }
var VkRenderPassCreateInfo.flags: VkRenderPassCreateFlags
    get() = flags()
    set(value) {
        flags(value)
    }
val VkRenderPassCreateInfo.attachmentCount get() = attachmentCount()
var VkRenderPassCreateInfo.attachments
    get() = pAttachments()
    set(value) {
        pAttachments(value)
    }
val VkRenderPassCreateInfo.subpassCount get() = subpassCount()
var VkRenderPassCreateInfo.subpasses
    get() = pSubpasses()
    set(value) {
        pSubpasses(value)
    }
val VkRenderPassCreateInfo.dependencyCount get() = dependencyCount()
var VkRenderPassCreateInfo.dependencies
    get() = pDependencies()
    set(value) {
        pDependencies(value)
    }


var VkPipelineCacheCreateInfo.type: VkStructureType
    get() = sType()
    set(value) {
        sType(value)
    }
var VkPipelineCacheCreateInfo.next
    get() = pNext()
    set(value) {
        pNext(value)
    }
var VkPipelineCacheCreateInfo.flags: VkPipelineCacheCreateFlags
    get() = flags()
    set(value) {
        flags(value)
    }
val VkPipelineCacheCreateInfo.initialDataSize get() = initialDataSize()
var VkPipelineCacheCreateInfo.pInitialData
    get() = pInitialData()
    set(value) {
        pInitialData(value)
    }


var VkFramebufferCreateInfo.type: VkStructureType
    get() = sType()
    set(value) {
        sType(value)
    }
var VkFramebufferCreateInfo.next
    get() = pNext()
    set(value) {
        pNext(value)
    }
var VkFramebufferCreateInfo.flags: VkFramebufferCreateFlags
    get() = flags()
    set(value) {
        flags(value)
    }
var VkFramebufferCreateInfo.renderPass: VkRenderPass
    get() = renderPass()
    set(value) {
        renderPass(value)
    }
val VkFramebufferCreateInfo.attachmentCount get() = attachmentCount()
var VkFramebufferCreateInfo.attachments:VkImageViewPtr?
    get() = pAttachments()
    set(value) {
        pAttachments(value)
    }
var VkFramebufferCreateInfo.width
    get() = width()
    set(value) {
        width(value)
    }
var VkFramebufferCreateInfo.height
    get() = height()
    set(value) {
        height(value)
    }
var VkFramebufferCreateInfo.layers
    get() = layers()
    set(value) {
        layers(value)
    }
var VkFramebufferCreateInfo.size
    get() = Vec3i(width, height, layers)
    set(value) {
        width = value.x
        height = value.y
        layers = value.z
    }


var VkPresentInfoKHR.type: VkStructureType
    get() = sType()
    set(value) {
        sType(value)
    }
var VkPresentInfoKHR.next
    get() = pNext()
    set(value) {
        pNext(value)
    }
val VkPresentInfoKHR.waitSemaphoreCount get() = waitSemaphoreCount()
var VkPresentInfoKHR.waitSemaphores: VkSemaphorePtr?
    get() = pWaitSemaphores()
    set(value) {
        pWaitSemaphores(value)
    }
val VkPresentInfoKHR.swapchainCount get() = swapchainCount()
var VkPresentInfoKHR.swapchains: VkSwapchainKHRptr
    get() = pSwapchains()
    set(value) {
        pSwapchains(value)
    }
var VkPresentInfoKHR.imageIndices: IntBuffer
    get() = pImageIndices()
    set(value) {
        pImageIndices(value)
    }
var VkPresentInfoKHR.results: VkResultPtr?
    get() = pResults()
    set(value) {
        pResults(value)
    }


val VkPhysicalDeviceProperties.apiVersion get() = apiVersion()
val VkPhysicalDeviceProperties.driverVersion get() = driverVersion()
val VkPhysicalDeviceProperties.vendorID get() = vendorID()
val VkPhysicalDeviceProperties.deviceID get() = deviceID()
val VkPhysicalDeviceProperties.deviceType: VkPhysicalDeviceType get() = deviceType()
val VkPhysicalDeviceProperties.deviceName get() = deviceNameString()
var VkPhysicalDeviceProperties.pipelineCacheUUID: ByteBuffer
    get() = pipelineCacheUUID()
    set(value) {
        TODO()
//        pipelineCacheUUID(value)
    }
val VkPhysicalDeviceProperties.limits: VkPhysicalDeviceLimits get() = limits()
val VkPhysicalDeviceProperties.sparseProperties: VkPhysicalDeviceSparseProperties get() = sparseProperties()

//fun pointerBufferOf(vararg strings: String): PointerBuffer {
//    val buf = pointerBufferBig(strings.size)
//    for (i in strings.indices)
//        buf[i] = strings[i]
//    return buf
//}
//
//operator fun PointerBuffer.set(index: Int, string: String) {
//    put(index, string.memUTF16)
//}
inline operator fun PointerBuffer.set(index: Int, long: Long) {
    put(index, long)
}

inline operator fun PointerBuffer.set(index: Int, pointer: Pointer) {
    put(index, pointer)
}

//operator fun PointerBuffer.plusAssign(string: String) {
//    put(string.stackUTF16)
//}

//operator fun <T> PointerBuffer.plusAssign(elements: Iterable<T>) {
//    for (item in elements)
//        if (item is String)
//            put(item.memUTF16)
//        else
//            throw Error()
//}
//
//fun PointerBuffer.isNotEmpty() = position() > 0


public infix fun Int.until(to: IntBuffer): IntRange {
    if (to[0] <= Int.MIN_VALUE) return IntRange.EMPTY
    return this..(to[0] - 1)
}

typealias VkCommandPool = Long
typealias VkSurfaceKHR = Long
typealias VkSwapchainKHR = Long
typealias VkImage = Long
typealias VkImageView = Long
typealias VkDeviceMemory = Long
typealias VkDeviceSize = Long
typealias VkRenderPass = Long
typealias VkPipelineCache = Long
typealias VkFramebuffer = Long
typealias VkSemaphore = Long
typealias VkFence = Long
typealias VkDescriptorPool = Long
typealias VkShaderModule = Long
typealias VkPipelineLayout = Long
typealias VkPipeline = Long
typealias VkDescriptorSetLayout = Long
typealias VkDescriptorSet = Long
typealias VkBuffer = Long

typealias VkSemaphorePtr = LongBuffer
typealias VkSwapchainKHRptr = LongBuffer
typealias VkResultPtr = IntBuffer
typealias VkSamplerPtr = LongBuffer
typealias VkImageViewPtr = LongBuffer

val VkQueueFamilyProperties.queueFlags: VkQueueFlags get() = queueFlags()
operator fun VkExtent2D.invoke(width: Int, height: Int) {
    width(width)
    height(height)
}

operator fun VkExtent3D.invoke(width: Int, height: Int, depth: Int) {
    width(width)
    height(height)
    depth(depth)
}

//operator fun VkExtent3D.invoke(size: Vec2i, depth: Int) {
fun VkExtent3D.wtf(x: Int, y: Int, z: Int) {
    width = x
    height = y
    depth = z
}

operator fun VkComponentMapping.invoke(r: Int, g: Int, b: Int, a: Int) {
    r(r)
    g(g)
    b(b)
    a(a)
}

object ArrayList_Long {
    operator fun ArrayList<Long>.set(index: Int, long: LongBuffer) {
        set(index, long[0])
    }

    infix fun ArrayList<Long>.resize(newSize: Int) {
        if (size < newSize)
            for (i in size until newSize)
                add(NULL)
        else if (size > newSize)
            for (i in size downTo newSize + 1)
                removeAt(lastIndex)
    }
}

// @formatter:off
inline fun vkDestroyDescriptorPool(device: VkDevice, descriptorPool: VkDescriptorPool, allocator: VkAllocationCallbacks? = null) = VK10.nvkDestroyDescriptorPool(device, descriptorPool, allocator?.address() ?: NULL)
inline fun vkDestroyRenderPass(device: VkDevice, renderPass: VkRenderPass, allocator: VkAllocationCallbacks? = null) = VK10.nvkDestroyRenderPass(device, renderPass, allocator?.address() ?: NULL)
inline fun vkDestroyFramebuffer(device: VkDevice, framebuffers: Iterable<VkFramebuffer>, allocator: VkAllocationCallbacks? = null) {
    for (i in framebuffers)
        VK10.nvkDestroyFramebuffer(device, i, allocator?.address() ?: NULL)
}

inline fun vkDestroyShaderModule(device: VkDevice, shaderModules: Iterable<VkShaderModule>, allocator: VkAllocationCallbacks? = null) {
    for (i in shaderModules)
        VK10.nvkDestroyShaderModule(device, i, allocator?.address() ?: NULL)
}
inline fun vkDestroyShaderModule(device: VkDevice, shaderModules: VkPipelineShaderStageCreateInfo.Buffer, allocator: VkAllocationCallbacks? = null) {
    for (i in shaderModules)
        VK10.vkDestroyShaderModule(device, i.address, allocator)
}

inline fun vkDestroyFence(device: VkDevice, fences: Iterable<VkFence>, allocator: VkAllocationCallbacks? = null) {
    for (i in fences)
        VK10.nvkDestroyFence(device, i, allocator?.address() ?: NULL)
}

inline fun vkDestroyImageView(device: VkDevice, imageView: VkImageView) = VK10.nvkDestroyImageView(device, imageView, NULL)
inline fun vkDestroyImage(device: VkDevice, image: VkImage) = VK10.nvkDestroyImage(device, image, NULL)
inline fun vkFreeMemory(device: VkDevice, memory: VkDeviceMemory) = VK10.nvkFreeMemory(device, memory,  NULL)
inline fun vkDestroyPipelineCache(device: VkDevice, pipelineCache: VkPipelineCache) = VK10.nvkDestroyPipelineCache(device, pipelineCache, NULL)
inline fun vkDestroyCommandPool(device: VkDevice, commandPool: VkCommandPool)  = VK10.nvkDestroyCommandPool(device, commandPool,NULL)
inline fun vkDestroySemaphore(device: VkDevice, semaphore: VkSemaphore) = VK10.nvkDestroySemaphore(device, semaphore,  NULL)

inline fun vkDestroyDebugReportCallback(instance: VkInstance, callbackAddress: Long) = EXTDebugReport.nvkDestroyDebugReportCallbackEXT(instance, callbackAddress,NULL)
inline fun vkDestroyInstance(instance: VkInstance) = VK10.nvkDestroyInstance(instance,  NULL)
inline fun vkDestroyPipeline(device: VkDevice, pipeline: VkPipeline) = VK10.nvkDestroyPipeline(device, pipeline,  NULL)
inline fun vkDestroyPipelineLayout(device: VkDevice, pipelineLayout: VkPipelineLayout) = VK10.nvkDestroyPipelineLayout(device, pipelineLayout, NULL)
inline fun vkDestroyDescriptorSetLayout(device: VkDevice, descriptorSetLayout: VkDescriptorSetLayout)  = VK10.nvkDestroyDescriptorSetLayout(device, descriptorSetLayout, NULL)
inline fun vkDestroyBuffer(device: VkDevice, buffer: VkBuffer)  = VK10.nvkDestroyBuffer(device, buffer, NULL)
inline fun vkDestroyShaderModule(device: VkDevice, shaderModule: VkShaderModule) = VK10.vkDestroyShaderModule(device, shaderModule, null)


val FloatBuffer.address get() = MemoryUtil.memAddress(this)
val IntBuffer.address get() = MemoryUtil.memAddress(this)

val Pointer.address get() = address()

// TODO glm
operator fun Vec3i.invoke(v: Vec2i, i: Int) {
    put(v.x,v.y,i)
}