package vulkan.base

import glfw_.GlfwWindow
import glfw_.appBuffer
import gli_.has
import glm_.vec2.Vec2i
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.KHRSurface.VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR
import org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR
import org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR
import org.lwjgl.vulkan.VK10.vkDestroyImageView
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue
import vkn.*
import kotlin.reflect.KMutableProperty0

class VulkanSwapChain {

    lateinit var instance: VkInstance
    lateinit var device: VkDevice
    lateinit var physicalDevice: VkPhysicalDevice
    var surface: VkSurfaceKHR = NULL
//    // Function pointers
//    PFN_vkGetPhysicalDeviceSurfaceSupportKHR fpGetPhysicalDeviceSurfaceSupportKHR;
//    PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR fpGetPhysicalDeviceSurfaceCapabilitiesKHR;
//    PFN_vkGetPhysicalDeviceSurfaceFormatsKHR fpGetPhysicalDeviceSurfaceFormatsKHR;
//    PFN_vkGetPhysicalDeviceSurfacePresentModesKHR fpGetPhysicalDeviceSurfacePresentModesKHR;
//    PFN_vkCreateSwapchainKHR fpCreateSwapchainKHR;
//    PFN_vkDestroySwapchainKHR fpDestroySwapchainKHR;
//    PFN_vkGetSwapchainImagesKHR fpGetSwapchainImagesKHR;
//    PFN_vkAcquireNextImageKHR fpAcquireNextImageKHR;
//    PFN_vkQueuePresentKHR fpQueuePresentKHR;

    var colorFormat = VkFormat.UNDEFINED
    var colorSpace = VkColorSpace.SRGB_NONLINEAR_KHR
    /** @brief Handle to the current swap chain, required for recreation */
    var swapChain: VkSwapchainKHR = NULL
    var imageCount = 0
    lateinit var images: ArrayList<VkImage>
    val buffers = ArrayList<SwapChainBuffer>()
    /** @brief Queue family index of the detected graphics and presenting device queue */
    var queueNodeIndex = UINT32_MAX

    /** @brief Creates the platform specific surface abstraction of the native platform window used for presentation */
    fun initSurface(instance: VkInstance, window: GlfwWindow) {

        // Create the os-specific surface
        surface = window createSurface instance

        // Get available queue family properties
        val queueProps = physicalDevice.queueFamilyProperties

        /*  Iterate over each queue to learn whether it supports presenting:
            Find a queue with present support
            Will be used to present the swap chain images to the windowing system   */
        val supportsPresent = physicalDevice.getSurfaceSupportKHR(queueProps, surface)

        // Search for a graphics and a present queue in the array of queue families, try to find one that supports both
        var graphicsQueueNodeIndex = UINT32_MAX
        var presentQueueNodeIndex = UINT32_MAX
        for (i in queueProps.indices) {

            if (queueProps[i].queueFlags has VkQueueFlag.GRAPHICS_BIT) {

                if (graphicsQueueNodeIndex == UINT32_MAX)
                    graphicsQueueNodeIndex = i

                if (supportsPresent[i]) {
                    graphicsQueueNodeIndex = i
                    presentQueueNodeIndex = i
                    break
                }
            }
        }
        if (presentQueueNodeIndex == UINT32_MAX) {
            // If there's no queue that supports both present and graphics, try to find a separate present queue
            val index = supportsPresent.indexOfFirst { it }
            if (index != -1)
                presentQueueNodeIndex = index
        }

        // Exit if either a graphics or a presenting queue hasn't been found
        if (graphicsQueueNodeIndex == UINT32_MAX || presentQueueNodeIndex == UINT32_MAX)
            tools.exitFatal("Could not find a graphics and/or presenting queue!", -1)

        // todo : Add support for separate graphics and presenting queue
        if (graphicsQueueNodeIndex != presentQueueNodeIndex)
            tools.exitFatal("Separate graphics and presenting queues are not supported yet!", -1)

        queueNodeIndex = graphicsQueueNodeIndex

        // Get list of supported surface formats
        val surfaceFormats = physicalDevice getSurfaceFormatsKHR surface

        /*  If the surface format list only includes one entry with VK_FORMAT_UNDEFINED,
            there is no preferered format, so we assume VK_FORMAT_B8G8R8A8_UNORM             */
        if (surfaceFormats.size == 1 && surfaceFormats[0].format == VkFormat.UNDEFINED) {
            colorFormat = VkFormat.B8G8R8A8_UNORM
            colorSpace = surfaceFormats[0].colorSpace
        } else {
            /*  iterate over the list of available surface format and check for the presence of
                VK_FORMAT_B8G8R8A8_UNORM, in case it's not available select the first available color format                 */
            val bgra8unorm = surfaceFormats.find { it.format == VkFormat.B8G8R8A8_UNORM }
            colorFormat = bgra8unorm?.format ?: surfaceFormats[0].format
            colorSpace = bgra8unorm?.colorSpace ?: surfaceFormats[0].colorSpace
        }
    }

    /**
     * Set instance, physical and logical device to use for the swapchain and get all required function pointers
     *
     * @param instance Vulkan instance to use
     * @param physicalDevice Physical device used to query properties and formats relevant to the swapchain
     * @param device Logical representation of the device to create the swapchain for
     *
     */
    fun connect(instance: VkInstance, physicalDevice: VkPhysicalDevice, device: VkDevice) {
        this.instance = instance
        this.physicalDevice = physicalDevice
        this.device = device
    }

    /**
     * Create the swapchain and get it's images with given width and height
     *
     * @param width Pointer to the width of the swapchain (may be adjusted to fit the requirements of the swapchain)
     * @param height Pointer to the height of the swapchain (may be adjusted to fit the requirements of the swapchain)
     * @param vsync (Optional) Can be used to force vsync'd rendering (by using VK_PRESENT_MODE_FIFO_KHR as presentation mode)
     */
    fun create(size: Vec2i, vsync: Boolean = false) {

        val oldSwapchain = swapChain

        // Get physical device surface properties and formats
        val surfCaps = physicalDevice getSurfaceCapabilitiesKHR surface

        // Get available present modes
        val presentModes = physicalDevice getSurfacePresentModesKHR surface

        var swapchainExtent = vk.Extent2D {}
        // If width (and height) equals the special value 0xFFFFFFFF, the size of the surface will be set by the swapchain
        if (surfCaps.currentExtent.width == -1)
        // If the surface size is undefined, the size is set to the size of the images requested.
            swapchainExtent.set(size.x, size.y) // TODO BUG
        else {
            // If the surface size is defined, the swap chain size must match
            swapchainExtent = surfCaps.currentExtent
            size.put(surfCaps.currentExtent.width, surfCaps.currentExtent.height) // TODO BUG
        }


        /*  Select a present mode for the swapchain

            The VK_PRESENT_MODE_FIFO_KHR mode must always be present as per spec
            This mode waits for the vertical blank ("v-sync")   */
        var swapchainPresentMode = VkPresentMode.FIFO_KHR

        /*  If v-sync is not requested, try to find a mailbox mode
            It's the lowest latency non-tearing present mode available         */
        if (!vsync)
            for (i in presentModes.indices) {
                if (presentModes[i] == VkPresentMode.MAILBOX_KHR) {
                    swapchainPresentMode = VkPresentMode.MAILBOX_KHR
                    break
                }
                if (swapchainPresentMode != VkPresentMode.MAILBOX_KHR && presentModes[i] == VkPresentMode.IMMEDIATE_KHR)
                    swapchainPresentMode = VkPresentMode.IMMEDIATE_KHR
            }

        // Determine the number of images
        var desiredNumberOfSwapchainImages = surfCaps.minImageCount + 1
        if (surfCaps.maxImageCount in 1 until desiredNumberOfSwapchainImages)
            desiredNumberOfSwapchainImages = surfCaps.maxImageCount

        // Find the transformation of the surface
        val preTransform =
                if (surfCaps.supportedTransforms has VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)
                // We prefer a non-rotated transform
                    VkSurfaceTransform.IDENTITY_BIT_KHR
                else surfCaps.currentTransform

        // Find a supported composite alpha format (not all devices support alpha opaque), Simply select the first composite alpha format available
        val compositeAlpha = arrayOf(VkCompositeAlpha.OPAQUE_BIT_KHR,
                VkCompositeAlpha.PRE_MULTIPLIED_BIT_KHR,
                VkCompositeAlpha.POST_MULTIPLIED_BIT_KHR,
                VkCompositeAlpha.INHERIT_BIT_KHR).find { surfCaps.supportedCompositeAlpha has it }
                ?: VkCompositeAlpha.OPAQUE_BIT_KHR


        val swapchainCI = vk.SwapchainCreateInfoKHR {
            this.surface = this@VulkanSwapChain.surface
            minImageCount = desiredNumberOfSwapchainImages
            imageFormat = colorFormat
            imageColorSpace = colorSpace
            imageExtent.set(swapchainExtent.width, swapchainExtent.height) // TODO BUG
            imageUsage = VkImageUsage.COLOR_ATTACHMENT_BIT.i
            this.preTransform = preTransform
            imageArrayLayers = 1
            imageSharingMode = VkSharingMode.EXCLUSIVE
            queueFamilyIndices = null
            presentMode = swapchainPresentMode
            this.oldSwapchain = oldSwapchain
            // Setting clipped to VK_TRUE allows the implementation to discard rendering outside of the surface area
            clipped = true
            this.compositeAlpha = compositeAlpha
        }

        // Set additional usage flag for blitting from the swapchain images if supported
        val formatProps = physicalDevice getFormatProperties colorFormat
        if (formatProps.optimalTilingFeatures has VkFormatFeature.TRANSFER_SRC_BIT_KHR || formatProps.optimalTilingFeatures has VkFormatFeature.BLIT_SRC_BIT)
            swapchainCI.imageUsage = swapchainCI.imageUsage or VkImageUsage.TRANSFER_SRC_BIT

        swapChain = device createSwapchainKHR swapchainCI

        /*  If an existing swap chain is re-created, destroy the old swap chain
            This also cleans up all the presentable images         */
        if (oldSwapchain != NULL) {
            for (i in 0 until imageCount)
                device destroyImageView buffers[i].view
            device destroySwapchainKHR oldSwapchain
        }

        // Get the swap chain images
        images = device getSwapchainImagesKHR swapChain
        imageCount = images.size

        // Get the swap chain buffers containing the image and imageview
        buffers resize images.size
        for (i in images.indices) {

            val colorAttachmentView = vk.ImageViewCreateInfo {
                format = colorFormat
                components(VkComponentSwizzle.R, VkComponentSwizzle.G, VkComponentSwizzle.B, VkComponentSwizzle.A)
                subresourceRange.apply {
                    aspectMask = VkImageAspect.COLOR_BIT.i
                    baseMipLevel = 0
                    levelCount = 1
                    baseArrayLayer = 0
                    layerCount = 1
                }
                viewType = VkImageViewType.`2D`
                flags = 0

                images[i].also {
                    buffers[i].image = it
                    image(it) // TODO BUG
                }
            }

            buffers[i].view = device createImageView colorAttachmentView
        }
    }

    /**
     * Acquires the next image in the swap chain
     *
     * @param presentCompleteSemaphore (Optional) Semaphore that is signaled when the image is ready for use
     * @param imageIndex Pointer to the image index that will be increased if the next image could be acquired
     *
     * @note The function will always wait until the next image has been acquired by setting timeout to UINT64_MAX
     *
     * @return VkResult of the image acquisition
     */
    fun acquireNextImage(presentCompleteSemaphore: VkSemaphore, imageIndex: KMutableProperty0<Int>): VkResult {
        // By setting timeout to UINT64_MAX we will always wait until the next image has been acquired or an actual error is thrown
        // With that we don't have to handle VK_NOT_READY
        return vk.acquireNextImageKHR(device, swapChain, UINT64_MAX, presentCompleteSemaphore, NULL, imageIndex)
    }

    /**
     * Queue an image for presentation
     *
     * @param queue Presentation queue for presenting the image
     * @param imageIndex Index of the swapchain image to queue for presentation
     * @param waitSemaphore (Optional) Semaphore that is waited on before the image is presented (only used if != VK_NULL_HANDLE)
     *
     * @return VkResult of the queue presentation
     */
    fun queuePresent(queue: VkQueue, imageIndex: Int, waitSemaphore: VkSemaphore = NULL) {
        val presentInfo = vk.PresentInfoKHR {
            swapchainCount = 1
            swapchains = appBuffer.longBufferOf(swapChain)
            imageIndices = appBuffer.intBufferOf(imageIndex)
            // Check if a wait semaphore has been specified to wait for before presenting the image
            if (waitSemaphore != NULL)
                waitSemaphores = appBuffer.longBufferOf(waitSemaphore)
        }
        queue presentKHR presentInfo
    }


    /**
     * Destroy and free Vulkan resources used for the swapchain
     */
    fun cleanup() {
        if (swapChain != NULL)
            for (i in 0 until imageCount)
                vkDestroyImageView(device, buffers[i].view, null)
        if (surface != NULL) {
            vkDestroySwapchainKHR(device, swapChain, null)
            vkDestroySurfaceKHR(instance, surface, null)
        }
        surface = NULL
        swapChain = NULL
    }

    inline infix fun ArrayList<SwapChainBuffer>.resize(newSize: Int) {
        if (size < newSize)
            for (i in size until newSize)
                add(SwapChainBuffer())
        else if (size > newSize)
            for (i in size downTo newSize + 1)
                removeAt(lastIndex)
    }
}

class SwapChainBuffer {
    var image: VkImage = NULL
    var view: VkImageView = NULL
}