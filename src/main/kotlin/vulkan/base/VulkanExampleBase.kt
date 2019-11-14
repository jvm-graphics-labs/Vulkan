package vulkan.base

import glm_.detail.GLM_DEPTH_CLIP_SPACE
import glm_.detail.GlmDepthClipSpace
import glm_.f
import glm_.i
import glm_.vec2.Vec2
import glm_.vec2.Vec2i
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import graphics.scenery.spirvcrossj.Loader
import graphics.scenery.spirvcrossj.libspirvcrossj
//import imgui.*
//import imgui.dsl.withStyleVar
import kool.intBufferOf
import kool.isValid
import kool.set
import org.lwjgl.PointerBuffer
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack.stackGet
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.VK_EXT_DEBUG_REPORT_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.*
import vkk.*
import vulkan.ENABLE_VALIDATION
import vulkan.assetPath
import vulkan.base.initializers.commandBufferAllocateInfo
import vulkan.base.tools.loadShader
import vulkan.reset
import java.nio.file.Paths
import kotlin.system.measureTimeMillis
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VK10.VK_SUCCESS
import org.lwjgl.vulkan.VK10.vkCreateInstance
import org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions
import org.lwjgl.system.MemoryUtil.*
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO
import org.lwjgl.vulkan.VkInstanceCreateInfo
import org.lwjgl.vulkan.EXTDebugReport.VK_EXT_DEBUG_REPORT_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.VK_MAKE_VERSION
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO
import org.lwjgl.vulkan.VkApplicationInfo
import uno.glfw.*
import uno.glfw.windowHint.Api
import vkk.VkResult.Companion.ERROR_OUT_OF_DATE_KHR
import vkk.VkResult.Companion.SUBOPTIMAL_KHR
import vkk.entities.*
import vkk.extensionFunctions.*
import vkk.stak
import vulkan.base.VulkanExampleBase.settings.validation
import java.nio.ByteBuffer


abstract class VulkanExampleBase {

    /** fps timer (one second interval) */
    var fpsTimer = 0f
    /** Get window title with example name, device, et. */
    val windowTitle: String
        get() {
            val device = deviceProperties.deviceName
            var windowTitle = "$title - $device"
            if (!settings.overlay)
                windowTitle += " - $frameCounter fps"
            return windowTitle
        }
    /** brief Indicates that the view (position, rotation) has changed and buffers containing camera matrices need to be updated */
    var viewUpdated = false
    /** Destination dimensions for resizing the window  */
    val destSize = Vec2i()
    var resizing = false
//    val uiOverlay = UIOverlay() TODO

    /** Frame counter to display fps    */
    var frameCounter = 0
    var lastFPS = 0
    /** Vulkan instance, stores all per-application states  */
    lateinit var instance: VkInstance
    /** Physical device (GPU) that Vulkan will use  */
    lateinit var physicalDevice: VkPhysicalDevice
    /** Stores physical device properties (for e.g. checking device limits) */
    val deviceProperties = VkPhysicalDeviceProperties.calloc()
    /** Stores the features available on the selected physical device (for e.g. checking if a feature is available) */
    val deviceFeatures = VkPhysicalDeviceFeatures.calloc()
    /** Stores all available memory (type) properties for the physical device   */
    val deviceMemoryProperties = VkPhysicalDeviceMemoryProperties.calloc()
    /**
     * Set of physical device features to be enabled for this example (must be set in the derived constructor)
     *
     * @note By default no phyiscal device features are enabled
     */
    val enabledFeatures = VkPhysicalDeviceFeatures.calloc()
    /** @brief Set of device extensions to be enabled for this example (must be set in the derived constructor) */
    val enabledDeviceExtensions = ArrayList<String>()
    val enabledInstanceExtensions = ArrayList<String>()
    /** @brief Logical device, application's view of the physical device (GPU) */
    // todo: getter? should always point to VulkanDevice->device
    lateinit var device: VkDevice
    /** Handle to the device graphics queue that command buffers are submitted to   */
    lateinit var queue: VkQueue
    /** Depth buffer format (selected during Vulkan initialization) */
    var depthFormat = VkFormat.UNDEFINED
    /** Command buffer pool */
    var cmdPool = VkCommandPool.NULL
    /** @brief Pipeline stages used to wait at for graphics queue submissions */
    val submitPipelineStages = intBufferOf(VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i)
    /** Contains command buffers and semaphores to be presented to the queue    */
    lateinit var submitInfo: VkSubmitInfo
    /** Command buffers used for rendering  */
    lateinit var drawCmdBuffers: Array<VkCommandBuffer>
    /** Global render pass for frame buffer writes  */
    var renderPass = VkRenderPass.NULL
    /** List of available frame buffers (same as number of swap chain images)   */
    var frameBuffers = VkFramebuffer_Array()
    /** Active frame buffer index   */
    var currentBuffer = 0
    /** Descriptor set pool */
    var descriptorPool = VkDescriptorPool.NULL
    /** List of shader modules created (stored for cleanup) */
    val shaderModules = ArrayList<VkShaderModule>()
    // Pipeline cache object
    var pipelineCache = VkPipelineCache.NULL
    // Wraps the swap chain to present images (framebuffers) to the windowing system
    var swapChain = VulkanSwapChain()

    // Synchronization semaphores
    object semaphores {
        // Swap chain image presentation
        var presentComplete = VkSemaphore.NULL
        // Command buffer submission and execution
        var renderComplete = VkSemaphore.NULL
    }

    var waitFences = VkFence_Array()

    var prepared = false
    val size = Vec2i(1280, 720)

    /** @brief Last frame time measured using a high performance timer (if available) */
    var frameTimer = 1f
//    /** @brief Returns os specific base asset path (for shaders, models, textures) */
//    const std::string getAssetPath()

    val benchmark = Benchmark()

    /** @brief Encapsulated physical and logical vulkan device */
    lateinit var vulkanDevice: VulkanDevice

    /** @brief Example settings that can be changed e.g. by command line arguments */
    object settings {
        /** @brief Activates validation layers (and message output) when set to true */
        var validation = ENABLE_VALIDATION
        /** @brief Set to true if fullscreen mode has been requested via command line */
        var fullscreen = false
        /** @brief Set to true if v-sync will be forced for the swapchain */
        var vsync = false
        /** @brief Enable UI overlay */
        var overlay = false
    }

    val defaultClearColor = Vec4(0.025f, 0.025f, 0.025f, 1f)

    var zoom = 0f

//    static std::vector<const char*> args;
//
    /** Defines a frame rate independent timer value clamped from -1.0...1.0
     *  For use in animations, rotations, etc.  */
    var timer = 0f
    /** Multiplier for speeding up (or slowing down) the global timer   */
    var timerSpeed = 0.25f

    var paused = false

    /** Use to adjust mouse rotation speed  */
    var rotationSpeed = 1f
    /** Use to adjust mouse zoom speed  */
    var zoomSpeed = 1f

    val camera = Camera()

    val rotation = Vec3()
    val cameraPos = Vec3()
    val mousePos = Vec2()
//
//    std::string title = "Vulkan Example";
//    std::string name = "vulkanExample";
//
//    struct
//    {
//        VkImage image;
//        VkDeviceMemory mem;
//        VkImageView view;
//    } depthStencil;
//
//    struct {
//        glm::vec2 axisLeft = glm::vec2(0.0f);
//        glm::vec2 axisRight = glm::vec2(0.0f);
//    } gamePadState;
//
//    struct {
//        bool left = false;
//        bool right = false;
//        bool middle = false;
//    } mouseButtons;

    lateinit var window: GlfwWindow

    var spirvCrossLoaded = false

    init {

        GLM_DEPTH_CLIP_SPACE = GlmDepthClipSpace.ZERO_TO_ONE

        settings.validation = ENABLE_VALIDATION

        // Parse command line arguments
//        for (size_t i = 0; i < args.size(); i++)
//        {
//            if (args[i] == std::string("-validation")) {
//                settings.validation = true;
//            }
//            if (args[i] == std::string("-vsync")) {
//                settings.vsync = true;
//            }
//            if ((args[i] == std::string("-f")) || (args[i] == std::string("--fullscreen"))) {
//                settings.fullscreen = true;
//            }
//            if ((args[i] == std::string("-w")) || (args[i] == std::string("-width"))) {
//                uint32_t w = strtol(args[i + 1], &numConvPtr, 10);
//                if (numConvPtr != args[i + 1]) { width = w; };
//            }
//            if ((args[i] == std::string("-h")) || (args[i] == std::string("-height"))) {
//                uint32_t h = strtol(args[i + 1], &numConvPtr, 10);
//                if (numConvPtr != args[i + 1]) { height = h; };
//            }
//            // Benchmark
//            if ((args[i] == std::string("-b")) || (args[i] == std::string("--benchmark"))) {
//                benchmark.active = true;
//                vks::tools::errorModeSilent = true;
//            }
//            // Warmup time (in seconds)
//            if ((args[i] == std::string("-bw")) || (args[i] == std::string("--benchwarmup"))) {
//                if (args.size() > i + 1) {
//                    uint32_t num = strtol(args[i + 1], &numConvPtr, 10);
//                    if (numConvPtr != args[i + 1]) {
//                        benchmark.warmup = num;
//                    } else {
//                        std::cerr << "Warmup time for benchmark mode must be specified as a number!" << std::endl;
//                    }
//                }
//            }
//            // Benchmark runtime (in seconds)
//            if ((args[i] == std::string("-br")) || (args[i] == std::string("--benchruntime"))) {
//                if (args.size() > i + 1) {
//                    uint32_t num = strtol(args[i + 1], &numConvPtr, 10);
//                    if (numConvPtr != args[i + 1]) {
//                        benchmark.duration = num;
//                    }
//                    else {
//                        std::cerr << "Benchmark run duration must be specified as a number!" << std::endl;
//                    }
//                }
//            }
//            // Bench result save filename (overrides default)
//            if ((args[i] == std::string("-bf")) || (args[i] == std::string("--benchfilename"))) {
//                if (args.size() > i + 1) {
//                    if (args[i + 1][0] == '-') {
//                        std::cerr << "Filename for benchmark results must not start with a hyphen!" << std::endl;
//                    } else {
//                        benchmark.filename = args[i + 1];
//                    }
//                }
//            }
//            // Output frame times to benchmark result file
//            if ((args[i] == std::string("-bt")) || (args[i] == std::string("--benchframetimes"))) {
//                benchmark.outputFrameTimes = true;
//            }
//        }
    }

    //
//    VkClearColorValue defaultClearColor = { { 0.025f, 0.025f, 0.025f, 1.0f } }
//
//    float zoom = 0
//
//    static std::vector<const char*> args
//
//    // Defines a frame rate independent timer value clamped from -1.0...1.0
//    // For use in animations, rotations, etc.
//    float timer = 0.0f
//    // Multiplier for speeding up (or slowing down) the global timer
//    float timerSpeed = 0.25f
//
//    bool paused = false
//
//    // Use to adjust mouse rotation speed
//    float rotationSpeed = 1.0f
//    // Use to adjust mouse zoom speed
//    float zoomSpeed = 1.0f
//
//    Camera camera
//
//    glm::vec3 rotation = glm::vec3()
//    glm::vec3 cameraPos = glm::vec3()
//    glm::vec2 mousePos
//
    var title = "Vulkan Example"
    val name = "vulkanExample"
    val apiVersion = VK_API_VERSION_1_0

    protected val depthStencil = DepthStencil()

    class DepthStencil {
        var image = VkImage.NULL
        var mem = VkDeviceMemory.NULL
        var view = VkImageView.NULL
    }

//    struct {
//    glm::vec2 axisLeft = glm::vec2(0.0f)
//        glm::vec2 axisRight = glm::vec2(0.0f)
//    } gamePadState
//
//    struct {
//    bool left = false
//        bool right = false
//        bool middle = false
//    } mouseButtons;

    /** dtor    */
    open fun destroy() {

        // Clean up Vulkan resources
        swapChain.cleanup()

        device.apply {
            if (descriptorPool.L != NULL)
                destroyDescriptorPool(descriptorPool)
            destroyCommandBuffers()
            destroyRenderPass(renderPass)
            frameBuffers.forEach { destroyFramebuffer(it) }

            destroyShaderModules(shaderModules)
            destroyImageView(depthStencil.view)
            destroyImage(depthStencil.image)
            freeMemory(depthStencil.mem)

            destroyPipelineCache(pipelineCache)

            destroyCommandPool(cmdPool)

            destroySemaphores(semaphores.presentComplete, semaphores.renderComplete)
        }

        if (settings.overlay)
            TODO()
//            uiOverlay.freeResources()

        vulkanDevice.destroy()

        if (settings.validation)
            debug.freeDebugCallback(instance)

        instance.destroy()

        if (spirvCrossLoaded)
            libspirvcrossj.finalizeProcess()

        window.destroy()
        window.onWindowClosed()
        glfw.terminate()
    }

    /** Setup the vulkan instance, enable required extensions and connect to the physical device (GPU)  */
    fun initVulkan() {

        // Vulkan instance
        createInstance().check("Could not create Vulkan instance")

        // If requested, we enable the default validation layers for debugging
        if (settings.validation) {
            /*  The report flags determine what type of messages for the layers will be displayed
                For validating (debugging) an appplication the error and warning bits should suffice             */
            val debugReportFlags = VkDebugReport.ERROR_BIT_EXT or VkDebugReport.WARNING_BIT_EXT // or VkDebugReport.INFORMATION_BIT_EXT
            // Additional flags include performance info, loader and layer debug messages, etc.
            debug.setupDebugging(instance, debugReportFlags, null)
        }

        // Get number of available physical devices and Enumerate devices
        val physicalDevices = instance.enumeratePhysicalDevices

        // Physical device
        val gpuCount = physicalDevices.rem
        assert(gpuCount > 0)

        // GPU selection

        /*  Select physical device to be used for the Vulkan example
            Defaults to the first device unless specified by command line         */
        val selectedDevice = 0

        // GPU selection via command line argument TODO
        /*      for (i in 0; i < args.size(); i++)
              {
                  // Select GPU
                  if ((args[i] == std::string("-g")) || (args[i] == std::string("-gpu"))) {
                      char * endptr;
                      uint32_t index = strtol (args[i + 1], &endptr, 10);
                      if (endptr != args[i + 1]) {
                          if (index > gpuCount - 1) {
                              std::cerr < < "Selected device index " << index << " is out of range, reverting to device 0 (use -listgpus to show available Vulkan devices)" << std::endl;
                          } else {
                              std::cout < < "Selected Vulkan device " << index << std::endl;
                              selectedDevice = index;
                          }
                      };
                      break;
                  }
                  // List available GPUs
                  if (args[i] == std::string("-listgpus")) {
                      uint32_t gpuCount = 0;
                      VK_CHECK_RESULT(vkEnumeratePhysicalDevices(instance, & gpuCount, nullptr));
                      if (gpuCount == 0) {
                          std::cerr < < "No Vulkan devices found!" << std::endl;
                      } else {
                          // Enumerate devices
                          std::cout < < "Available Vulkan devices" << std::endl;
                          std::vector<VkPhysicalDevice> devices (gpuCount);
                          VK_CHECK_RESULT(vkEnumeratePhysicalDevices(instance, & gpuCount, devices.data()));
                          for (uint32_t i = 0; i < gpuCount; i++) {
                              VkPhysicalDeviceProperties deviceProperties;
                              vkGetPhysicalDeviceProperties(devices[i], & deviceProperties);
                              std::cout < < "Device [" << i << "] : " << deviceProperties.deviceName << std::endl;
                              std::cout < < " Type: " << vks::tools::physicalDeviceTypeString(deviceProperties.deviceType) << std::endl;
                              std::cout < < " API: " << (deviceProperties.apiVersion >> 22) << "." << ((deviceProperties.apiVersion >> 12) & 0x3ff) << "." << (deviceProperties.apiVersion & 0xfff) << std::endl;
                          }
                      }
                  }
             */

        physicalDevice = physicalDevices[selectedDevice]

        // Store properties (including limits), features and memory properties of the physical device (so that examples can check against them)
        physicalDevice.apply {
            getProperties(deviceProperties)
            getFeatures(deviceFeatures)
            getMemoryProperties(deviceMemoryProperties)
        }

        // Derived examples can override this to set actual features (based on above readings) to enable for logical device creation
        getEnabledFeatures()

        /*  Vulkan device creation
            This is handled by a separate class that gets a logical device representation and encapsulates functions related to a device    */
        vulkanDevice = VulkanDevice(physicalDevice)
        vulkanDevice.createLogicalDevice(enabledFeatures, enabledDeviceExtensions).check("Could not create Vulkan device")

        device = vulkanDevice.logicalDevice!!

        // Get a graphics queue from the device
        queue = device.getQueue(vulkanDevice.queueFamilyIndices.graphics, 0)

        // Find a suitable depth format
        depthFormat = tools getSupportedDepthFormat physicalDevice
        assert(depthFormat != VkFormat.UNDEFINED)

        swapChain.connect(instance, physicalDevice, device)

        // Create synchronization objects
        val semaphoreCreateInfo = vk.SemaphoreCreateInfo()
        semaphores.apply {
            /*  Create a semaphore used to synchronize image presentation
                Ensures that the image is displayed before we start submitting new commands to the queue */
            presentComplete = device createSemaphore semaphoreCreateInfo
            /*  Create a semaphore used to synchronize command submission
                Ensures that the image is not presented until all commands have been submitted and executed */
            renderComplete = device createSemaphore semaphoreCreateInfo
        }

        /*  Set up submit info structure
            Semaphores will stay the same during application lifetime
            Command buffer submission info is set by each example   */
        submitInfo = VkSubmitInfo.calloc().apply {
            waitDstStageMask = submitPipelineStages
            waitSemaphoreCount = 1
            waitSemaphore = semaphores.presentComplete
            signalSemaphore = semaphores.renderComplete
        }
    }

    /** Create GLFW window  */
    fun setupWindow() {
        with(glfw) {
            val fullscreen = false
            init()
            if (!vulkanSupported)
                throw AssertionError("GLFW failed to find the Vulkan loader")
            windowHint {
                default()
                api = Api.None
                visible = false
            }
            window = when {
                fullscreen -> GlfwWindow(videoMode.size, windowTitle, primaryMonitor)
                else -> GlfwWindow(size, windowTitle)
            }
            window.autoSwap = false
        }
        with(window) {
            keyCallback = keyboardHandler
            mouseButtonCallback = mouseHandler
            cursorPosCallback = mouseMoveHandler
            windowCloseCallback = closeHandler
            framebufferSizeCallback = framebufferSizeHandler
            scrollCallback = mouseScrollHandler
        }
    }

    /**
     * Create the application wide Vulkan instance
     *
     * @note Virtual, can be overriden by derived example class for custom instance creation
     */
    open fun createInstance(): VkResult {

        val appInfo = vk.ApplicationInfo {
            applicationName = name
            engineName = name
            apiVersion = this@VulkanExampleBase.apiVersion
        }

        val instanceExtensions = glfw.requiredInstanceExtensions
        if (enabledInstanceExtensions.isNotEmpty())
            instanceExtensions += enabledInstanceExtensions

        val instanceCreateInfo = vk.InstanceCreateInfo {
            applicationInfo = appInfo
            if (instanceExtensions.isNotEmpty()) {
                if (settings.validation)
                    instanceExtensions += VK_EXT_DEBUG_REPORT_EXTENSION_NAME
                ppEnabledExtensionNames(instanceExtensions.toP())
            }
            if (settings.validation)
                ppEnabledLayerNames(debug.validationLayerNames.toP())
        }
        return vk.createInstance(instanceCreateInfo, ::instance)
    }

    private fun Collection<String>.toP(): PointerBuffer {
        val stack = MemoryStack.stackGet()
        val pointers = stack.mallocPointer(size)
        for (i in indices)
            pointers[i] = stack.UTF8(elementAt(i))
        return pointers
    }


    /** Pure virtual render function (override in derived class)    */
    abstract fun render()

    /** Called when view change occurs
     *  Can be overriden in derived class to e.g. update uniform buffers
     *  Containing view dependant matrices  */
    open fun viewChanged() {}
//    /** @brief (Virtual) Called after a key was pressed, can be used to do custom key handling */
//    virtual void keyPressed(uint32_t);
//    /** @brief (Virtual) Called after th mouse cursor moved and before internal events (like camera rotation) is handled */
//    virtual void mouseMoved(double x, double y, bool &handled);
    /** Called when the window has been resized
     *  Can be overriden in derived class to recreate or rebuild resources attached to the frame buffer / swapchain */
    open fun windowResized() {}

    /** Pure virtual function to be overriden by the dervice class
     *  Called in case of an event where e.g. the framebuffer has to be rebuild and thus
     *  all command buffers that may reference this */
    open fun buildCommandBuffers() {}

    fun createSynchronizationPrimitives() {
        // Wait fences to sync command buffer access
        val fenceCreateInfo = vk.FenceCreateInfo(VK_FENCE_CREATE_SIGNALED_BIT)
        waitFences = VkFence_Array(drawCmdBuffers.size) { device createFence fenceCreateInfo }
    }

    /** Creates a new (graphics) command pool object storing command buffers    */
    fun createCommandPool() {

        val cmdPoolInfo = vk.CommandPoolCreateInfo {
            queueFamilyIndex = swapChain.queueNodeIndex
            flags = VkCommandPoolCreate.RESET_COMMAND_BUFFER_BIT.i
        }
        cmdPool = device createCommandPool cmdPoolInfo
    }

    /** Setup default depth and stencil views   */
    open fun setupDepthStencil() {

        val image = vk.ImageCreateInfo {
            imageType = VkImageType._2D
            format = depthFormat
            extent.set(size.x, size.y, 1)
            mipLevels = 1
            arrayLayers = 1
            samples = VkSampleCount._1_BIT
            tiling = VkImageTiling.OPTIMAL
            usage = VkImageUsage.DEPTH_STENCIL_ATTACHMENT_BIT or VkImageUsage.TRANSFER_SRC_BIT
            flags = 0
        }

        val memAlloc = vk.MemoryAllocateInfo {
            allocationSize = VkDeviceSize(0)
            memoryTypeIndex = 0
        }

        val depthStencilView = vk.ImageViewCreateInfo {
            viewType = VkImageViewType._2D
            format = depthFormat
            flags = 0
            subresourceRange.apply {
                aspectMask = VkImageAspect.DEPTH_BIT or VkImageAspect.STENCIL_BIT
                baseMipLevel = 0
                levelCount = 1
                baseArrayLayer = 0
                layerCount = 1
            }
        }

        depthStencil.image = device createImage image
        val memReqs = device.getImageMemoryRequirements(depthStencil.image)
        memAlloc.allocationSize = memReqs.size
        memAlloc.memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty.DEVICE_LOCAL_BIT)
        depthStencil.mem = device allocateMemory memAlloc
        device.bindImageMemory(depthStencil.image, depthStencil.mem)

        depthStencilView.image = depthStencil.image
        depthStencil.view = device createImageView depthStencilView
    }

    /** Create framebuffers for all requested swap chain images
     *  Can be overriden in derived class to setup a custom framebuffer (e.g. for MSAA) */
    open fun setupFrameBuffer() = stak {

        val attachments = vk.ImageView_Buffer(2) // TODO check

        // Depth/Stencil attachment is the same for all frame buffers
        attachments[1] = depthStencil.view

        val (w, h) = size // TODO BUG

        val frameBufferCreateInfo = vk.FramebufferCreateInfo {
            renderPass = this@VulkanExampleBase.renderPass
            this.attachments = attachments
            width = w
            height = h
            layers = 1
        }

        // Create frame buffers for every swap chain image
        frameBuffers = VkFramebuffer_Array(swapChain.imageCount) {
            attachments[0] = swapChain.buffers[it].view
            device createFramebuffer frameBufferCreateInfo
        }
    }

    /** Setup a default render pass
     *  Can be overriden in derived class to setup a custom render pass (e.g. for MSAA) */
    open fun setupRenderPass() {

        val attachments = vk.AttachmentDescription(2)
        // Color attachment
        with(attachments[0]) {
            format = swapChain.colorFormat
            samples = VkSampleCount._1_BIT
            loadOp = VkAttachmentLoadOp.CLEAR
            storeOp = VkAttachmentStoreOp.STORE
            stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
            stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
            initialLayout = VkImageLayout.UNDEFINED
            finalLayout = VkImageLayout.PRESENT_SRC_KHR
        }
        // Depth attachment
        with(attachments[1]) {
            format = depthFormat
            samples = VkSampleCount._1_BIT
            loadOp = VkAttachmentLoadOp.CLEAR
            storeOp = VkAttachmentStoreOp.STORE
            stencilLoadOp = VkAttachmentLoadOp.CLEAR
            stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
            initialLayout = VkImageLayout.UNDEFINED
            finalLayout = VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL
        }

        val colorReference = vk.AttachmentReference(1) // TODO more functional?
        colorReference[0].attachment = 0
        colorReference[0].layout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL

        val depthReference = vk.AttachmentReference {
            attachment = 1
            layout = VkImageLayout.DEPTH_STENCIL_ATTACHMENT_OPTIMAL
        }

        val subpassDescription = vk.SubpassDescription {
            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
            colorAttachmentCount = 1
            colorAttachments = colorReference
            depthStencilAttachment = depthReference
            inputAttachments = null
            preserveAttachments = null
            resolveAttachments = null
        }

        // Subpass dependencies for layout transitions
        val dependencies = vk.SubpassDependency(2)

        with(dependencies[0]) {
            srcSubpass = VK_SUBPASS_EXTERNAL
            dstSubpass = 0
            srcStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
            dstStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
            srcAccessMask = VkAccess.MEMORY_READ_BIT.i
            dstAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
            dependencyFlags = VkDependency.BY_REGION_BIT.i
        }

        with(dependencies[1]) {
            srcSubpass = 0
            dstSubpass = VK_SUBPASS_EXTERNAL
            srcStageMask = VkPipelineStage.COLOR_ATTACHMENT_OUTPUT_BIT.i
            dstStageMask = VkPipelineStage.BOTTOM_OF_PIPE_BIT.i
            srcAccessMask = VkAccess.COLOR_ATTACHMENT_READ_BIT or VkAccess.COLOR_ATTACHMENT_WRITE_BIT
            dstAccessMask = VkAccess.MEMORY_READ_BIT.i
            dependencyFlags = VkDependency.BY_REGION_BIT.i
        }

        val renderPassInfo = vk.RenderPassCreateInfo {
            this.attachments = attachments
            subpass = subpassDescription
            this.dependencies = dependencies
        }

        vk.createRenderPass(device, renderPassInfo, ::renderPass).check()
    }

    /** @brief (Virtual) Called after the physical device features have been read, can be used to set features to enable on the device */
    open fun getEnabledFeatures() {} // Can be overriden in derived class

    /** Connect and prepare the swap chain  */
    fun initSwapchain() = swapChain.initSurface(instance, window)

    /** Create swap chain images    */
    fun setupSwapChain() = swapChain.create(size, settings.vsync)

    /** Check if command buffers are valid (!= NULL) */
    fun checkCommandBuffers() = drawCmdBuffers.all { it.isValid }

    /** Create command buffers for drawing commands */
    fun createCommandBuffers() {
        // Create one command buffer for each swap chain image and reuse for rendering
        drawCmdBuffers = device allocateCommandBuffers commandBufferAllocateInfo(
                cmdPool,
                VkCommandBufferLevel.PRIMARY,
                swapChain.imageCount)
    }

    /** Destroy all command buffers and set their handles to VK_NULL_HANDLE
     *  May be necessary during runtime if options are toggled  */
    fun destroyCommandBuffers() = Unit//device.freeCommandBuffers(cmdPool, drawCmdBuffers)

    /** Command buffer creation
     *  Creates and returns a new command buffer */
    fun createCommandBuffer(level: VkCommandBufferLevel, begin: Boolean): VkCommandBuffer {

        val cmdBufAllocateInfo = vk.CommandBufferAllocateInfo {
            commandPool = cmdPool
            this.level = level
            commandBufferCount = 1
        }

        val cmdBuffer: VkCommandBuffer = device.allocateCommandBuffers(cmdBufAllocateInfo)

        // If requested, also start the new command buffer
        if (begin)
            cmdBuffer begin vk.CommandBufferBeginInfo { }

        return cmdBuffer
    }

    /** End the command buffer, submit it to the queue and free (if requested)
     *  Note : Waits for the queue to become idle   */
    fun flushCommandBuffer(commandBuffer: VkCommandBuffer, queue: VkQueue, free: Boolean) {

        commandBuffer.end()

        val submitInfo = vk.SubmitInfo {
            this.commandBuffer = commandBuffer
        }

        queue submit submitInfo
        queue.waitIdle()

        if (free)
            device.freeCommandBuffer(cmdPool, commandBuffer)
    }

    /** Create a cache pool for rendering pipelines */
    fun createPipelineCache() {
        pipelineCache = device createPipelineCache vk.PipelineCacheCreateInfo()
    }

    /** Prepare commonly used Vulkan functions  */
    open fun prepare() {

        if (vulkanDevice.enableDebugMarkers)
            debugMarker.device = device
        initSwapchain()
        createCommandPool()
        setupSwapChain()
        createCommandBuffers()
        setupDepthStencil()
        setupRenderPass()
        createPipelineCache()
        setupFrameBuffer()
        settings.overlay = settings.overlay && !benchmark.active
        if (settings.overlay)
            TODO()
//            uiOverlay.apply {
//                device = vulkanDevice
//                queue = this@VulkanExampleBase.queue
//                shaders[0].loadShader("$assetPath/shaders/base/uioverlay.vert.spv", VkShaderStage.VERTEX_BIT)
//                shaders[1].loadShader("$assetPath/shaders/base/uioverlay.frag.spv", VkShaderStage.FRAGMENT_BIT)
//                prepareResources()
//                preparePipeline(pipelineCache, renderPass)
//            }
    }

    /** Load a SPIR-V shader    */
    fun VkPipelineShaderStageCreateInfo.loadShader(fileName: String, stage: VkShaderStage, add: Boolean = true) {

        val isSpirV = fileName.substringAfterLast('.') == "spv"
        if (!isSpirV && !spirvCrossLoaded) {
            // if it's a glsl shader, load spirvCross for conversion if not done yet
            Loader.loadNatives()
            if (!libspirvcrossj.initializeProcess())
                throw RuntimeException("glslang failed to initialize.")
            spirvCrossLoaded = true
        }
        type = VkStructureType.PIPELINE_SHADER_STAGE_CREATE_INFO
        this.stage = stage
        module = when {
            isSpirV -> device loadShader fileName
            else -> {
                val bytes = glslToSpirv(Paths.get(fileName))
                device loadShader bytes
            }
        }
        name = "main" // todo : make param
        assert(module.L != NULL)
        if (add)
            shaderModules += module
    }

    /** Start the main render loop  */
    fun renderLoop() {

        if (benchmark.active) {
            TODO()
//            benchmark.run([=] { render(); }, vulkanDevice->properties);
//            vkDeviceWaitIdle(device);
//            if (benchmark.filename != "") {
//                benchmark.saveResults();
//            }
//            return;
        }

        destSize(size)

        window.loop {
            /*  Handle window messages. Resize events happen exactly here (before lambda).
                So it is safe to use the new swapchain images and framebuffers afterwards.             */
            if (!window.isIconified)
                renderFrame()
        }

        // Flush device to make sure all resources can be freed
        if (device.isValid)
            device.waitIdle()
    }

    /** Render one frame of a render loop on platforms that sync rendering  */
    fun renderFrame() {
        val tDiff = measureTimeMillis {
            if (viewUpdated) {
                viewUpdated = false
                viewChanged()
            }
            render()
            frameCounter++
        }
        frameTimer = tDiff.f / 1000f
        camera update frameTimer
        if (camera.moving)
            viewUpdated = true

        // Convert to clamped timer value
        if (!paused) {
            timer += timerSpeed * frameTimer
            if (timer > 1.0)
                timer -= 1f
        }
        fpsTimer += tDiff.f
        if (fpsTimer > 1000f) {
            lastFPS = (frameCounter.f * (1000f / fpsTimer)).i
            if (!settings.overlay)
                window.title = windowTitle
            fpsTimer = 0f
            frameCounter = 0
        }
        // TODO: Cap UI overlay update rates
        updateOverlay()

        // reset pointer
        stackGet().reset()
    }

    fun updateOverlay() {

        if (!settings.overlay) return
        TODO()
//        ImGui.apply {
//
//            io.apply {
//
//                displaySize put size
//                deltaTime = frameTimer
//
//                mousePos put this@VulkanExampleBase.mousePos
//                TODO()
////                mouseDown[0] = window.mouseButton(GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS
////                mouseDown[1] = window.mouseButton(GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS
//            }
//
//            newFrame()
//
//            withStyleVar(StyleVar.WindowRounding, 0f) {
//
//                setNextWindowPos(Vec2(10))
//                setNextWindowSize(Vec2(), Cond.FirstUseEver)
//
//                dsl.window("Vulkan Example", null, WindowFlag.AlwaysAutoResize or WindowFlag.NoResize or WindowFlag.NoMove) {
//
//                    textUnformatted(title)
//                    textUnformatted(deviceProperties.deviceName)
////                    text("%.2f ms/frame (%.1d fps)", 1000f / lastFPS, lastFPS)
//
//                    pushItemWidth(110f * uiOverlay.scale)
//                    uiOverlay.onUpdate()
//                    popItemWidth()
//                }
//            }
//            render()
//            TODO()//ImplGL3.renderDrawData(ImGui.drawData!!)
//
//            if (uiOverlay.update() || uiOverlay.updated) {
//                buildCommandBuffers()
//                uiOverlay.updated = false
//            }
//        }
    }

    fun VkCommandBuffer.drawUI() {

        if (settings.overlay) {

            setViewport(vk.Viewport(size))
            setScissor(vk.Rect2D(size))
            TODO()
//            uiOverlay.apply { draw() }
        }
    }

    /** Prepare the frame for workload submission
     *      - Acquires the next image from the swap chain
     *      - Sets the default wait and signal semaphores   */
    fun prepareFrame() {
        // Acquire the next image from the swap chain
        val err = swapChain.acquireNextImage(semaphores.presentComplete, ::currentBuffer)
        // Recreate the swapchain if it's no longer compatible with the surface (OUT_OF_DATE) or no longer optimal for presentation (SUBOPTIMAL)
        if (err == ERROR_OUT_OF_DATE_KHR || err == SUBOPTIMAL_KHR)
            windowResize(window.size)
        else
            err.check()
    }

    /** Submit the frames' workload */
    fun submitFrame() {

        val res = swapChain.queuePresent(queue, currentBuffer, semaphores.renderComplete)

        if (res != VkResult.SUCCESS && res != SUBOPTIMAL_KHR)
            if (res == ERROR_OUT_OF_DATE_KHR) {
                // Swap chain is no longer compatible with the surface and needs to be recreated
                windowResize(window.size)
                return
            } else
                res.check()

        queue.waitIdle()
    }

//    /** @brief (Virtual) Called before the UI overlay is created, can be used to do a custom setup e.g. with different renderpass */
//    virtual void OnSetupUIOverlay(vks::UIOverlayCreateInfo &createInfo);
    /** @brief (Virtual) Called when the UI overlay is updating, can be used to add custom elements to the overlay */
//    open fun UIOverlay.onUpdate() {} TODO

    /** Called if the window is resized and some resources have to be recreated    */
    fun windowResize(newSize: Vec2i) {

        if (!prepared) return
        prepared = false

        // Ensure all operations on the device have been finished before destroying resources
        device.waitIdle()

        // Recreate swap chain
        size put newSize
        setupSwapChain()

        // Recreate the frame buffers
        device.apply {
            destroyImageView(depthStencil.view)
            destroyImage(depthStencil.image)
            freeMemory(depthStencil.mem)
        }
        setupDepthStencil()
        frameBuffers.forEach(device::destroyFramebuffer)
        setupFrameBuffer()

        // Command buffers need to be recreated as they may store
        // references to the recreated frame buffer
        destroyCommandBuffers()
        createCommandBuffers()
        buildCommandBuffers()

        device.waitIdle()

        if (newSize allGreaterThan 0) {
            if (settings.overlay)
                TODO()
//                uiOverlay.resize(newSize)
            camera.updateAspectRatio(newSize.aspect)
        }

        // Notify derived class
        windowResized()
        viewChanged()

        prepared = true
    }

    var keyboardHandler: KeyCallbackT = { key, _, action, _ ->
        when (action) {
            GLFW_PRESS -> keyPressBase(key)
            GLFW_RELEASE -> keyReleaseBase(key)
        }
    }

    var mouseHandler: MouseButtonCallbackT = { _, action, _ ->
        if (action == GLFW_PRESS)
            mousePos.put(window.cursorPos)
    }

    var mouseMoveHandler: CursorPosCallbackT = { pos -> mouseMoved(pos) }

    var closeHandler: WindowCloseCallbackT = {
        prepared = false
        window.shouldClose = true
    }

    var framebufferSizeHandler: FramebufferSizeCallbackT = { size -> windowResize(size) }

    var mouseScrollHandler: ScrollCallbackT = { scroll -> mouseScrolled(scroll.y.f) }

    fun keyPressBase(key: Int) {
        when (key) {
            GLFW_KEY_P -> paused = !paused
            GLFW_KEY_F1 -> TODO()//if (settings.overlay) uiOverlay.visible = !uiOverlay.visible
            GLFW_KEY_ESCAPE -> window.shouldClose = true
        }
        if (camera.type == Camera.CameraType.firstPerson)
            when (key) {
                GLFW_KEY_W -> camera.keys.up = true
                GLFW_KEY_S -> camera.keys.down = true
                GLFW_KEY_A -> camera.keys.left = true
                GLFW_KEY_D -> camera.keys.right = true
            }
        keyPressed(key)
    }

    /** Can be overriden in derived class */
    open fun keyPressed(keyCode: Int) {}

    fun keyReleaseBase(key: Int) {
        if (camera.type == Camera.CameraType.firstPerson)
            when (key) {
                GLFW_KEY_W -> camera.keys.up = false
                GLFW_KEY_S -> camera.keys.down = false
                GLFW_KEY_A -> camera.keys.left = false
                GLFW_KEY_D -> camera.keys.right = false
            }
    }

    fun mouseMoved(newPos: Vec2) {

        val deltaPos = mousePos - newPos
        if (deltaPos.allEqual(0f)) return

//        if (settings.overlay) {
//            ImGuiIO& io = ImGui::GetIO();
//            handled = io.WantCaptureMouse;
//        }

        TODO()
//        if (GLFW_PRESS == window.mouseButton(GLFW_MOUSE_BUTTON_RIGHT)) {
//            zoom += deltaPos.y * .005f * zoomSpeed
//            camera translate Vec3(0f, 0f, deltaPos.y * .005f * zoomSpeed)
//            mousePos put newPos
//            viewChanged()
//        }
//        if (GLFW_PRESS == window.mouseButton(GLFW_MOUSE_BUTTON_LEFT)) {
//            val deltaPos3 = Vec3(deltaPos.y, -deltaPos.x, 0f) * 1.25f * rotationSpeed
//            rotation += deltaPos3
//            camera rotate deltaPos3
//            mousePos put newPos
//            viewChanged()
//        }
//        if (GLFW_PRESS == window.mouseButton(GLFW_MOUSE_BUTTON_MIDDLE)) {
//            val deltaPos3 = Vec3(deltaPos, 0f) * 0.01f
//            cameraPos -= deltaPos3
//            camera translate -deltaPos3
//            mousePos put newPos
//            viewChanged()
//        }
        mousePos(newPos)
    }

    fun mouseScrolled(wheelDelta: Float) {
        zoom += wheelDelta * zoomSpeed
        camera translate Vec3(0f, 0f, wheelDelta * zoomSpeed)
        viewChanged()
    }
}