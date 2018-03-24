package vulkan.base

import gli_.has
import glm_.vec2.Vec2i
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memAllocLong
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR
import org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR
import org.lwjgl.vulkan.VK10.vkDestroyImageView
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceFormatProperties
import vkn.*
import vkn.VkMemoryStack.Companion.withStack
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

    var colorFormat = VkFormat_UNDEFINED
    var colorSpace = VkColorSpace_SRGB_NONLINEAR_KHR
    /** @brief Handle to the current swap chain, required for recreation */
    var swapChain: VkSwapchainKHR = NULL
    var imageCount = 0
    var images = ArrayList<VkImage>()
    val buffers = ArrayList<SwapChainBuffer>()
    /** @brief Queue family index of the detected graphics and presenting device queue */
    var queueNodeIndex = Int.MAX_VALUE

    /** @brief Creates the platform specific surface abstraction of the native platform window used for presentation */
    fun initSurface() {

        VkMemoryStack.withStack {

            // Create GLFW window
            glfwDefaultWindowHints()
            glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
            val window = glfwCreateWindow(800, 600, "GLFW Vulkan Demo", NULL, NULL)
            glfwSetKeyCallback(window, { window, key, scancode, action, mods ->
                if (action == GLFW_RELEASE && key == GLFW_KEY_ESCAPE)
                    glfwSetWindowShouldClose(window, true)
            })
            val pSurface = memAllocLong(1)
            val err = glfwCreateWindowSurface(instance, window, null, pSurface)
            surface = pSurface.get(0)
            if (err())
                throw AssertionError("Failed to create surface: $err")

            // Get available queue family properties
            val queueProps = vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice)

            /*  Iterate over each queue to learn whether it supports presenting:
                Find a queue with present support
                Will be used to present the swap chain images to the windowing system   */
            val supportsPresent = vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, queueProps.size, surface)

            // Search for a graphics and a present queue in the array of queue families, try to find one that supports both
            var graphicsQueueNodeIndex = Int.MAX_VALUE
            var presentQueueNodeIndex = Int.MAX_VALUE
            for (i in queueProps.indices) {

                if (queueProps[i].queueFlags has VkQueue_GRAPHICS_BIT) {

                    if (graphicsQueueNodeIndex == Int.MAX_VALUE)
                        graphicsQueueNodeIndex = i

                    if (supportsPresent[i]) {
                        graphicsQueueNodeIndex = i
                        presentQueueNodeIndex = i
                        break
                    }
                }
            }
            if (presentQueueNodeIndex == Int.MAX_VALUE) {
                // If there's no queue that supports both present and graphics, try to find a separate present queue
                val index = supportsPresent.indexOfFirst { it }
                if (index != -1)
                    presentQueueNodeIndex = index
            }

            // Exit if either a graphics or a presenting queue hasn't been found
            if (graphicsQueueNodeIndex == Int.MAX_VALUE || presentQueueNodeIndex == Int.MAX_VALUE)
                tools.exitFatal("Could not find a graphics and/or presenting queue!", -1)

            // todo : Add support for separate graphics and presenting queue
            if (graphicsQueueNodeIndex != presentQueueNodeIndex)
                tools.exitFatal("Separate graphics and presenting queues are not supported yet!", -1)

            queueNodeIndex = graphicsQueueNodeIndex

            // Get list of supported surface formats
            val surfaceFormats = ArrayList<VkSurfaceFormatKHR>()
            vrGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, surfaceFormats).check()

            /*  If the surface format list only includes one entry with VK_FORMAT_UNDEFINED,
                there is no preferered format, so we assume VK_FORMAT_B8G8R8A8_UNORM             */
            if (surfaceFormats.size == 1 && surfaceFormats[0].format == VkFormat_UNDEFINED) {
                colorFormat = VK10.VK_FORMAT_B8G8R8A8_UNORM
                colorSpace = surfaceFormats[0].colorSpace
            } else {
                /*  iterate over the list of available surface format and check for the presence of
                    VK_FORMAT_B8G8R8A8_UNORM, in case it's not available select the first available color format                 */
                val bgra8unorm = surfaceFormats.find { it.format == VkFormat_B8G8R8A8_UNORM }
                colorFormat = bgra8unorm?.format ?: surfaceFormats[0].format
                colorSpace = bgra8unorm?.colorSpace ?: surfaceFormats[0].colorSpace
            }
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
    fun create(size: Vec2i, vsync: Boolean = false) = withStack {

        val oldSwapchain = swapChain

        // Get physical device surface properties and formats
        val surfCaps = cVkSurfaceCapabilitiesKHR()
        vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, surfCaps).check()

        // Get available present modes
        val presentModes = ArrayList<VkPresentModeKHR>()
        vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModes).check()

        var swapchainExtent = cVkExtent2D()
        // If width (and height) equals the special value 0xFFFFFFFF, the size of the surface will be set by the swapchain
        if (surfCaps.currentExtent.width == -1)
        // If the surface size is undefined, the size is set to the size of the images requested.
            swapchainExtent.size(size)
        else {
            // If the surface size is defined, the swap chain size must match
            swapchainExtent = surfCaps.currentExtent
//            size(surfCaps.currentExtent.size)   TODO BUG
            size.put(surfCaps.currentExtent.width, surfCaps.currentExtent.height)
        }


        /*  Select a present mode for the swapchain

            The VK_PRESENT_MODE_FIFO_KHR mode must always be present as per spec
            This mode waits for the vertical blank ("v-sync")   */
        var swapchainPresentMode: VkPresentModeKHR = VkPresentMode_FIFO_KHR

        /*  If v-sync is not requested, try to find a mailbox mode
            It's the lowest latency non-tearing present mode available         */
        if (!vsync)
            for (i in presentModes.indices) {
                if (presentModes[i] == VkPresentMode_MAILBOX_KHR) {
                    swapchainPresentMode = VkPresentMode_MAILBOX_KHR
                    break
                }
                if (swapchainPresentMode != VkPresentMode_MAILBOX_KHR && presentModes[i] == VkPresentMode_IMMEDIATE_KHR)
                    swapchainPresentMode = VkPresentMode_IMMEDIATE_KHR
            }

        // Determine the number of images
        var desiredNumberOfSwapchainImages = surfCaps.minImageCount + 1
        if (surfCaps.maxImageCount in 1 until desiredNumberOfSwapchainImages)
            desiredNumberOfSwapchainImages = surfCaps.maxImageCount

        // Find the transformation of the surface
        val preTransform =
                if (surfCaps.supportedTransforms has VkSurfaceTransform_IDENTITY_BIT_KHR)
                // We prefer a non-rotated transform
                    VkSurfaceTransform_IDENTITY_BIT_KHR
                else surfCaps.currentTransform

        // Find a supported composite alpha format (not all devices support alpha opaque), Simply select the first composite alpha format available
        val compositeAlpha = arrayOf(VkCompositeAlpha_OPAQUE_BIT_KHR,
                VkCompositeAlpha_PRE_MULTIPLIED_BIT_KHR,
                VkCompositeAlpha_POST_MULTIPLIED_BIT_KHR,
                VkCompositeAlpha_INHERIT_BIT_KHR).find { surfCaps.supportedCompositeAlpha has it }
                ?: VkCompositeAlpha_OPAQUE_BIT_KHR


        val swapchainCI = cVkSwapchainCreateInfoKHR {
            type = VkStructureType_SWAPCHAIN_CREATE_INFO_KHR
            next = NULL
            surface = this@VulkanSwapChain.surface
            minImageCount = desiredNumberOfSwapchainImages
            imageFormat = colorFormat
            imageColorSpace = colorSpace
            imageExtent(swapchainExtent.width, swapchainExtent.height)
            imageUsage = VkImageUsage_COLOR_ATTACHMENT_BIT
            this.preTransform = preTransform
            imageArrayLayers = 1
            imageSharingMode = VkSharingMode_EXCLUSIVE
            queueFamilyIndices = null
            presentMode = swapchainPresentMode
            this.oldSwapchain = oldSwapchain
            // Setting clipped to VK_TRUE allows the implementation to discard rendering outside of the surface area
            clipped = true
            this.compositeAlpha = compositeAlpha
        }

        // Set additional usage flag for blitting from the swapchain images if supported
        val formatProps = mVkFormatProperties()
        vkGetPhysicalDeviceFormatProperties(physicalDevice, colorFormat, formatProps)
        if (formatProps.optimalTilingFeatures has VkFormatFeature_TRANSFER_SRC_BIT_KHR || formatProps.optimalTilingFeatures has VkFormatFeature_BLIT_SRC_BIT)
            swapchainCI.imageUsage = swapchainCI.imageUsage or VkImageUsage_TRANSFER_SRC_BIT

        vkCreateSwapchainKHR(device, swapchainCI, null, ::swapChain)

        /*  If an existing swap chain is re-created, destroy the old swap chain
            This also cleans up all the presentable images         */
        if (oldSwapchain != NULL) {
            for (i in 0 until imageCount)
                vkDestroyImageView(device, buffers[i].view, null)
            vkDestroySwapchainKHR(device, oldSwapchain, null)
        }

        // Get the swap chain images
        images = vkGetSwapchainImagesKHR(device, swapChain)

        // Get the swap chain buffers containing the image and imageview
        buffers resize images.size
        for (i in images.indices) {

            val colorAttachmentView = mVkImageViewCreateInfo().apply {
                type = VkStructureType_IMAGE_VIEW_CREATE_INFO
                next = NULL
                format = colorFormat
                components(VkComponentSwizzle_R, VkComponentSwizzle_G, VkComponentSwizzle_B, VkComponentSwizzle_A)
                subresourceRange.apply {
                    aspectMask = VkImageAspect_COLOR_BIT
                    baseMipLevel = 0
                    levelCount = 1
                    baseArrayLayer = 0
                    layerCount = 1
                }
                viewType = VkImageViewType_2D
                flags = 0

                buffers[i].image = images[i]

                image = buffers[i].image
            }

            vkCreateImageView(device, colorAttachmentView, null, buffers[i]::view).check()
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
    fun acquireNextImage(presentCompleteSemaphore: VkSemaphore, imageIndex: KMutableProperty0<Int>): VkResult = withStack {
        // By setting timeout to UINT64_MAX we will always wait until the next image has been acquired or an actual error is thrown
        // With that we don't have to handle VK_NOT_READY
        vkAcquireNextImageKHR(device, swapChain, Long.MAX_VALUE, presentCompleteSemaphore, NULL, imageIndex)
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
    fun queuePresent(queue: VkQueue, imageIndex: Int, waitSemaphore: VkSemaphore = NULL): VkResult = withStack {
        val presentInfo = cVkPresentInfoKHR {
            type = VkStructureType_PRESENT_INFO_KHR
            next = NULL
            swapchains = longs(swapChain)
            imageIndices = ints(imageIndex)
            // Check if a wait semaphore has been specified to wait for before presenting the image
            if (waitSemaphore != NULL)
                this.waitSemaphores = longs(waitSemaphore)
        }
        return vkQueuePresentKHR(queue, presentInfo)
    }


    /**
     * Destroy and free Vulkan resources used for the swapchain
     */
    fun cleanup() {
        if (swapChain != NULL) {
            for (i in 0 until imageCount)
                vkDestroyImageView(device, buffers[i].view, null)
        }
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