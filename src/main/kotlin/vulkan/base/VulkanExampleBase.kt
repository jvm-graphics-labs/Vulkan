package vulkan.base

import glm_.f
import glm_.i
import glm_.set
import glm_.vec2.Vec2i
import glm_.vec3.Vec3
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memAllocLong
import org.lwjgl.system.Platform
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.EXTDebugReport.VK_EXT_DEBUG_REPORT_EXTENSION_NAME
import org.lwjgl.vulkan.KHRSurface.VK_KHR_SURFACE_EXTENSION_NAME
import org.lwjgl.vulkan.KHRWin32Surface.VK_KHR_WIN32_SURFACE_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.*
import uno.buffer.intBufferOf
import uno.glfw.glfw
import vkn.*
import vkn.ArrayListLong.resize
import vkn.VkMemoryStack.Companion.withStack
import vulkan.base.initializers.commandBufferAllocateInfo
import vulkan.base.initializers.semaphoreCreateInfo
import vulkan.base.initializers.submitInfo
import kotlin.system.measureTimeMillis

abstract class VulkanExampleBase(enableValidation: Boolean) {

    /** fps timer (one second interval) */
    var fpsTimer = 0.0f
    /** Get window title with example name, device, et. */
    val windowTitle: String
        get() {
            val device = deviceProperties.deviceName
            var windowTitle = "$title - $device"
            if (!settings.overlay)
                windowTitle += " - $frameCounter fps"
            return windowTitle
        }
    /** brief Indicates that the view (position, rotation) has changed and */
    var viewUpdated = false
    /** Destination dimensions for resizing the window  */
    val destSize = Vec2i()
    var resizing = false
    var uiOverlay: UIOverlay? = null
    /** Called if the window is resized and some resources have to be recreated    */
//    void windowResize()
//    void handleMouseMove(int32_t x, int32_t y)
//    protected:
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
    var depthFormat: VkFormat = VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT
    /** Command buffer pool */
    var cmdPool: VkCommandPool = NULL
    /** @brief Pipeline stages used to wait at for graphics queue submissions */
    val submitPipelineStages = intBufferOf(VkPipelineStage_COLOR_ATTACHMENT_OUTPUT_BIT)
    /** Contains command buffers and semaphores to be presented to the queue    */
    lateinit var submitInfo: VkSubmitInfo
    /** Command buffers used for rendering  */
    val drawCmdBuffers = ArrayList<VkCommandBuffer>()
    /** Global render pass for frame buffer writes  */
    var renderPass: VkRenderPass = NULL
    /** List of available frame buffers (same as number of swap chain images)   */
    val frameBuffers = ArrayList<VkFramebuffer>()
    /** Active frame buffer index   */
    var currentBuffer = 0
    /** Descriptor set pool */
    var descriptorPool: VkDescriptorPool = NULL
    /** List of shader modules created (stored for cleanup) */
    val shaderModules = ArrayList<VkShaderModule>()
    // Pipeline cache object
    var pipelineCache: VkPipelineCache = NULL
    // Wraps the swap chain to present images (framebuffers) to the windowing system
    var swapChain = VulkanSwapChain()
    // Synchronization semaphores
    private val semaphores = object {
        // Swap chain image presentation
        var presentComplete: VkSemaphore = NULL
        // Command buffer submission and execution
        var renderComplete: VkSemaphore = NULL
        // UI overlay submission and execution
        var overlayComplete: VkSemaphore = NULL
    }

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
    private val settings = object {
        /** @brief Activates validation layers (and message output) when set to true */
        var validation = false
        /** @brief Set to true if fullscreen mode has been requested via command line */
        var fullscreen = false
        /** @brief Set to true if v-sync will be forced for the swapchain */
        var vsync = false
        /** @brief Enable UI overlay */
        var overlay = false
    }

//    VkClearColorValue defaultClearColor = { { 0.025f, 0.025f, 0.025f, 1.0f } };

    var zoom = 0f

//    static std::vector<const char*> args;
//
    /** Defines a frame rate independent timer value clamped from -1.0...1.0
     *  For use in animations, rotations, etc.  */
    var timer = 0f
    /** Multiplier for speeding up (or slowing down) the global timer   */
    var timerSpeed = 0.25f

    var paused = false
    //
//    // Use to adjust mouse rotation speed
//    float rotationSpeed = 1.0f;
//    // Use to adjust mouse zoom speed
//    float zoomSpeed = 1.0f;
//
    val camera = Camera()

    val rotation = Vec3()
    val cameraPos = Vec3()
    val mousePos = Vec2i()
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

    var window = NULL
    var surface = NULL

    init {

        settings.validation = enableValidation

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

    protected val depthStencil = DepthStencil()

    class DepthStencil {
        var image: VkImage = NULL
        var mem: VkDeviceMemory = NULL
        var view: VkImageView = NULL
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
        if (descriptorPool != NULL)
            vkDestroyDescriptorPool(device, descriptorPool)
        destroyCommandBuffers()
        vkDestroyRenderPass(device, renderPass)
        vkDestroyFramebuffer(device, frameBuffers)

        vkDestroyShaderModule(device, shaderModules)
        vkDestroyImageView(device, depthStencil.view)
        vkDestroyImage(device, depthStencil.image)
        vkFreeMemory(device, depthStencil.mem)

        vkDestroyPipelineCache(device, pipelineCache)

        vkDestroyCommandPool(device, cmdPool)

        vkDestroySemaphore(device, semaphores.presentComplete)
        vkDestroySemaphore(device, semaphores.renderComplete)
        vkDestroySemaphore(device, semaphores.overlayComplete)

        uiOverlay?.destroy()

        vulkanDevice.destroy()

        if (settings.validation)
            debug.freeDebugCallback(instance)

        vkDestroyInstance(instance)
    }

    /** Setup the vulkan instance, enable required extensions and connect to the physical device (GPU)  */
    fun initVulkan() {

        // Vulkan instance
        var err = createInstance(settings.validation)
        if (err())
            tools.exitFatal("Could not create Vulkan instance : \n${err.string}", err)

        // If requested, we enable the default validation layers for debugging
        if (settings.validation) {
            /*  The report flags determine what type of messages for the layers will be displayed
                For validating (debugging) an appplication the error and warning bits should suffice             */
            val debugReportFlags = VkDebugReport_ERROR_BIT_EXT or VkDebugReport_WARNING_BIT_EXT
            // Additional flags include performance info, loader and layer debug messages, etc.
            debug.setupDebugging(instance, debugReportFlags, null)
        }

        // Get number of available physical devices and Enumerate devices
        val physicalDevices = ArrayList<VkPhysicalDevice>()
        err = vkEnumeratePhysicalDevices(instance, physicalDevices)
        if (err())
            tools.exitFatal("Could not enumerate physical devices : \n${err.string}", err)
        // Physical device
        val gpuCount = physicalDevices.size
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
        vkGetPhysicalDeviceProperties(physicalDevice, deviceProperties)
        vkGetPhysicalDeviceFeatures(physicalDevice, deviceFeatures)
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, deviceMemoryProperties)

        // Derived examples can override this to set actual features (based on above readings) to enable for logical device creation
        getEnabledFeatures()

        /*  Vulkan device creation
            This is handled by a separate class that gets a logical device representation and encapsulates functions related to a device    */
        vulkanDevice = VulkanDevice(physicalDevice)
        err = vulkanDevice.createLogicalDevice(enabledFeatures, enabledDeviceExtensions)
        if (err != Vk_SUCCESS)
            tools.exitFatal("Could not create Vulkan device: \n$${err.string}", err)

        device = vulkanDevice.logicalDevice!!

        // Get a graphics queue from the device
        vkGetDeviceQueue(device, vulkanDevice.queueFamilyIndices.graphics, 0, ::queue)

        // Find a suitable depth format
        val validDepthFormat = tools.getSupportedDepthFormat(physicalDevice, ::depthFormat)
        assert(validDepthFormat)

        swapChain.connect(instance, physicalDevice, device)

        withStack {
            // Create synchronization objects
            val semaphoreCreateInfo = semaphoreCreateInfo()
            /*  Create a semaphore used to synchronize image presentation
                Ensures that the image is displayed before we start submitting new commands to the queu             */
            vkCreateSemaphore(device, semaphoreCreateInfo, null, semaphores::presentComplete).check()
            /*  Create a semaphore used to synchronize command submission
                Ensures that the image is not presented until all commands have been sumbitted and executed             */
            vkCreateSemaphore(device, semaphoreCreateInfo, null, semaphores::renderComplete).check()
            // Create a semaphore used to synchronize command submission
            // Ensures that the image is not presented until all commands for the UI overlay have been sumbitted and executed
            // Will be inserted after the render complete semaphore if the UI overlay is enabled
            vkCreateSemaphore(device, semaphoreCreateInfo, null, semaphores::overlayComplete).check()

            /*  Set up submit info structure
                Semaphores will stay the same during application lifetime
                Command buffer submission info is set by each example   */
            submitInfo = submitInfo().apply {
                waitDstStageMask = submitPipelineStages
                waitSemaphoreCount = 1
                waitSemaphores = longs(semaphores.presentComplete)
                signalSemaphores = longs(semaphores.renderComplete)
            }
        }
    }

    fun setupWindow() {
        // Create GLFW window
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        window = glfwCreateWindow(size.x, size.y, "GLFW Vulkan Demo", NULL, NULL)
        glfwSetKeyCallback(window, { window, key, scancode, action, mods ->
            if (action == GLFW_RELEASE && key == GLFW_KEY_ESCAPE)
                glfwSetWindowShouldClose(window, true)
        })
        val pSurface = memAllocLong(1)
        val err = glfwCreateWindowSurface(instance, window, null, pSurface)
        surface = pSurface.get(0)
        if (err != VK_SUCCESS)
            throw AssertionError("Failed to create surface: ${err.string}")
    }

    /**
     * Create the application wide Vulkan instance
     *
     * @note Virtual, can be overriden by derived example class for custom instance creation
     */
    open fun createInstance(enableValidation: Boolean): VkResult = withStack {

        settings.validation = enableValidation

        val appInfo = cVkApplicationInfo {
            type = VkStructureType_APPLICATION_INFO
            applicationName = name
            engineName = name
            apiVersion = VK_API_VERSION_1_0
        }

        val requiredExtensions = glfw.requiredInstanceExtensions
        val instanceExtensions = ArrayList<String>()
        instanceExtensions += requiredExtensions
        instanceExtensions += VK_KHR_SURFACE_EXTENSION_NAME
        // Enable surface extensions depending on os TODO others
        if (Platform.get() == Platform.WINDOWS)
            instanceExtensions += VK_KHR_WIN32_SURFACE_EXTENSION_NAME
        if (enabledInstanceExtensions.isNotEmpty())
            instanceExtensions += enabledInstanceExtensions

        val instanceCreateInfo = cVkInstanceCreateInfo {
            type = VkStructureType_INSTANCE_CREATE_INFO
            next = NULL
            applicationInfo = appInfo
            if (instanceExtensions.isNotEmpty()) {
                if (settings.validation)
                    instanceExtensions += VK_EXT_DEBUG_REPORT_EXTENSION_NAME
                enabledExtensionNames = instanceExtensions.toPointerBuffer()
            }
            if (settings.validation)
                enabledLayerNames = debug.validationLayerNames.toPointerBuffer()
        }
        return vkCreateInstance(instanceCreateInfo, null, ::instance)
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
//    // Called when the window has been resized
//    // Can be overriden in derived class to recreate or rebuild resources attached to the frame buffer / swapchain
//    virtual void windowResized();
    /** Pure virtual function to be overriden by the dervice class
     *  Called in case of an event where e.g. the framebuffer has to be rebuild and thus
     *  all command buffers that may reference this */
    abstract fun buildCommandBuffers()

    /** Creates a new (graphics) command pool object storing command buffers    */
    fun createCommandPool() = withStack {

        val cmdPoolInfo = cVkCommandPoolCreateInfo {
            type = VkStructureType_COMMAND_POOL_CREATE_INFO
            queueFamilyIndex = swapChain.queueNodeIndex
            flags = VkCommandPoolCreate_RESET_COMMAND_BUFFER_BIT

        }
        vkCreateCommandPool(device, cmdPoolInfo, null, ::cmdPool).check()
    }

    /** Setup default depth and stencil views   */
    open fun setupDepthStencil() = withStack {

        val image = cVkImageCreateInfo {
            type = VkStructureType_IMAGE_CREATE_INFO
            next = NULL
            imageType = VkImageType_2D
            format = depthFormat
            extent.wtf(this@VulkanExampleBase.size.x, this@VulkanExampleBase.size.y, 1)
            mipLevels = 1
            arrayLayers = 1
            samples = VkSampleCount_1_BIT
            tiling = VkImageTiling_OPTIMAL
            usage = VkImageUsage_DEPTH_STENCIL_ATTACHMENT_BIT or VkImageUsage_TRANSFER_SRC_BIT
            flags = 0
        }

        val memAlloc = cVkMemoryAllocateInfo {
            type = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO
            next = NULL
            allocationSize = 0
            memoryTypeIndex = 0
        }

        val depthStencilView = cVkImageViewCreateInfo {
            type = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO
            next = NULL
            viewType = VkImageViewType_2D
            format = depthFormat
            flags = 0
            subresourceRange.apply {
                aspectMask = VkImageAspect_DEPTH_BIT or VkImageAspect_STENCIL_BIT
                baseMipLevel = 0
                levelCount = 1
                baseArrayLayer = 0
                layerCount = 1
            }
        }

        val memReqs = cVkMemoryRequirements()

        vkCreateImage(device, image, null, depthStencil::image)
        vkGetImageMemoryRequirements(device, depthStencil.image, memReqs)
        memAlloc.allocationSize = memReqs.size
        memAlloc.memoryTypeIndex = vulkanDevice.getMemoryType(memReqs.memoryTypeBits, VkMemoryProperty_DEVICE_LOCAL_BIT)
        vkAllocateMemory(device, memAlloc, null, depthStencil::mem).check()
        vkBindImageMemory(device, depthStencil.image, depthStencil.mem, 0).check()

        depthStencilView.image = depthStencil.image
        vkCreateImageView(device, depthStencilView, null, depthStencil::view).check()
    }

    /** Create framebuffers for all requested swap chain images
     *  Can be overriden in derived class to setup a custom framebuffer (e.g. for MSAA) */
    open fun setupFrameBuffer() = withStack {

        val attachments = cVkImageView(2)

        // Depth/Stencil attachment is the same for all frame buffers
        attachments[1] = depthStencil.view

        val frameBufferCreateInfo = cVkFramebufferCreateInfo {
            type = VkStructureType_FRAMEBUFFER_CREATE_INFO
            next = NULL
            renderPass = this@VulkanExampleBase.renderPass
            this.attachments = attachments
            width = size.x
            height = size.y
            layers = 1
        }

        // Create frame buffers for every swap chain image
        frameBuffers resize swapChain.imageCount
        for (i in frameBuffers.indices) {
            attachments[0] = swapChain.buffers[i].view
            vkCreateFramebuffer(device, frameBufferCreateInfo, null, frameBuffers, i).check()
        }
    }
//    // Setup a default render pass
    /** Can be overriden in derived class to setup a custom render pass (e.g. for MSAA) */
    open fun setupRenderPass() = withStack {

        val attachments = cVkAttachmentDescription(2)
        // Color attachment
        with(attachments[0]) {
            format = swapChain.colorFormat
            samples = VkSampleCount_1_BIT
            loadOp = VkAttachmentLoadOp_CLEAR
            storeOp = VkAttachmentStoreOp_STORE
            stencilLoadOp = VkAttachmentLoadOp_DONT_CARE
            stencilStoreOp = VkAttachmentStoreOp_DONT_CARE
            initialLayout = VkImageLayout_UNDEFINED
            finalLayout = VkImageLayout_PRESENT_SRC_KHR
        }
        // Depth attachment
        with(attachments[1]) {
            format = depthFormat
            samples = VkSampleCount_1_BIT
            loadOp = VkAttachmentLoadOp_CLEAR
            storeOp = VkAttachmentStoreOp_STORE
            stencilLoadOp = VkAttachmentLoadOp_CLEAR
            stencilStoreOp = VkAttachmentStoreOp_DONT_CARE
            initialLayout = VkImageLayout_UNDEFINED
            finalLayout = VkImageLayout_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
        }

        val colorReference = cVkAttachmentReference(1) {
            attachment = 0
            layout = VkImageLayout_COLOR_ATTACHMENT_OPTIMAL
        }

        val depthReference = cVkAttachmentReference {
            attachment = 1
            layout = VkImageLayout_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
        }

        val subpassDescription = cVkSubpassDescription(1) {
            pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS
            colorAttachmentCount= 1
            colorAttachments = colorReference
            depthStencilAttachment = depthReference
            inputAttachments = null
            preserveAttachments = null
            resolveAttachments = null
        }

        // Subpass dependencies for layout transitions
        val dependencies = cVkSubpassDependency(2)

        with(dependencies[0]) {
            srcSubpass = VK_SUBPASS_EXTERNAL
            dstSubpass = 0
            srcStageMask = VkPipelineStage_BOTTOM_OF_PIPE_BIT
            dstStageMask = VkPipelineStage_COLOR_ATTACHMENT_OUTPUT_BIT
            srcAccessMask = VkAccess_MEMORY_READ_BIT
            dstAccessMask = VkAccess_COLOR_ATTACHMENT_READ_BIT or VkAccess_COLOR_ATTACHMENT_WRITE_BIT
            dependencyFlags = VkDependency_BY_REGION_BIT
        }

        with(dependencies[1]) {
            srcSubpass = 0
            dstSubpass = VK_SUBPASS_EXTERNAL
            srcStageMask = VkPipelineStage_COLOR_ATTACHMENT_OUTPUT_BIT
            dstStageMask = VkPipelineStage_BOTTOM_OF_PIPE_BIT
            srcAccessMask = VkAccess_COLOR_ATTACHMENT_READ_BIT or VkAccess_COLOR_ATTACHMENT_WRITE_BIT
            dstAccessMask = VkAccess_MEMORY_READ_BIT
            dependencyFlags = VkDependency_BY_REGION_BIT
        }

        val renderPassInfo = cVkRenderPassCreateInfo {
            type = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO
            this.attachments = attachments
            subpasses = subpassDescription
            this.dependencies = dependencies
        }

        vkCreateRenderPass(device, renderPassInfo, null, ::renderPass).check()
    }

    /** @brief (Virtual) Called after the physical device features have been read, can be used to set features to enable on the device */
    open fun getEnabledFeatures() {} // Can be overriden in derived class

    /** Connect and prepare the swap chain  */
    fun initSwapchain() {
        swapChain.surface = surface
        swapChain.initSurface(size)
    }

    /** Create swap chain images    */
    fun setupSwapChain() = swapChain.create(size, settings.vsync)
//
//    // Check if command buffers are valid (!= VK_NULL_HANDLE)
//    bool checkCommandBuffers();
    /** Create command buffers for drawing commands */
    fun createCommandBuffers() = withStack {
        // Create one command buffer for each swap chain image and reuse for rendering
        drawCmdBuffers.ensureCapacity(swapChain.imageCount)

        val cmdBufAllocateInfo = commandBufferAllocateInfo(
                cmdPool,
                VkCommandBufferLevel_PRIMARY,
                swapChain.imageCount)

        vkAllocateCommandBuffers(device, cmdBufAllocateInfo, swapChain.imageCount, drawCmdBuffers).check()
    }

    /** Destroy all command buffers and set their handles to VK_NULL_HANDLE
     *  May be necessary during runtime if options are toggled  */
    fun destroyCommandBuffers() = withStack { vkFreeCommandBuffers(device, cmdPool, drawCmdBuffers) }
//
//    // Command buffer creation
//    // Creates and returns a new command buffer
//    VkCommandBuffer createCommandBuffer(VkCommandBufferLevel level, bool begin);
//    // End the command buffer, submit it to the queue and free (if requested)
//    // Note : Waits for the queue to become idle
//    void flushCommandBuffer(VkCommandBuffer commandBuffer, VkQueue queue, bool free);
//
    /** Create a cache pool for rendering pipelines */
    fun createPipelineCache() {
        withStack {
            val pipelineCacheCreateInfo = cVkPipelineCacheCreateInfo {
                type = VkStructureType_PIPELINE_CACHE_CREATE_INFO
            }
            vkCreatePipelineCache(device, pipelineCacheCreateInfo, null, ::pipelineCache)
        }
    }

    /** Prepare commonly used Vulkan functions  */
    open fun prepare() {

        if (vulkanDevice.enableDebugMarkers)
            debugMarker.setup(device)
        initSwapchain()
        createCommandPool()
        setupSwapChain()
        createCommandBuffers()
        setupDepthStencil()
        setupRenderPass()
        createPipelineCache()
        setupFrameBuffer()
        settings.overlay = settings.overlay && !benchmark.active
        if (settings.overlay) {
            TODO()
//            vks::UIOverlayCreateInfo overlayCreateInfo = {}
//            // Setup default overlay creation info
//            overlayCreateInfo.device = vulkanDevice
//            overlayCreateInfo.copyQueue = queue
//            overlayCreateInfo.framebuffers = frameBuffers
//            overlayCreateInfo.colorformat = swapChain.colorFormat
//            overlayCreateInfo.depthformat = depthFormat
//            overlayCreateInfo.width = width
//            overlayCreateInfo.height = height
//            // Virtual function call for example to customize overlay creation
//            OnSetupUIOverlay(overlayCreateInfo)
//            // Load default shaders if not specified by example
//            if (overlayCreateInfo.shaders.size() == 0) {
//                overlayCreateInfo.shaders = {
//                    loadShader(getAssetPath() + "shaders/base/uioverlay.vert.spv", VK_SHADER_STAGE_VERTEX_BIT),
//                    loadShader(getAssetPath() + "shaders/base/uioverlay.frag.spv", VK_SHADER_STAGE_FRAGMENT_BIT),
//                }
//            }
//            UIOverlay = new vks ::UIOverlay(overlayCreateInfo)
//            updateOverlay()
        }
    }
//
//    // Load a SPIR-V shader
//    VkPipelineShaderStageCreateInfo loadShader(std::string fileName, VkShaderStageFlagBits stage);
//
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

        while (!glfwWindowShouldClose(window)) {
            /*  Handle window messages. Resize events happen exactly here.
                So it is safe to use the new swapchain images and framebuffers afterwards.             */
            glfwPollEvents()
            renderFrame()
        }
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
        frameTimer = tDiff.f / 1000.0f
        camera update frameTimer
        if (camera.moving)
            viewUpdated = true

        // Convert to clamped timer value
        if (!paused) {
            timer += timerSpeed * frameTimer
            if (timer > 1.0) {
                timer -= 1.0f
            }
        }
        fpsTimer += tDiff.f
        if (fpsTimer > 1000f) {
            lastFPS = (frameCounter.f * (1000f / fpsTimer)).i
            if (!settings.overlay)
                glfwSetWindowTitle(window, windowTitle)
            fpsTimer = 0f
            frameCounter = 0
        }
        // TODO: Cap UI overlay update rates
        updateOverlay()
    }

    fun updateOverlay() {

        if (!settings.overlay) return

        TODO()
//        ImGuiIO& io = ImGui::GetIO();
//
//        io.DisplaySize = ImVec2((float)width, (float)height);
//        io.DeltaTime = frameTimer;
//
//        io.MousePos = ImVec2(mousePos.x, mousePos.y);
//        io.MouseDown[0] = mouseButtons.left;
//        io.MouseDown[1] = mouseButtons.right;
//
//        ImGui::NewFrame();
//
//        ImGui::PushStyleVar(ImGuiStyleVar_WindowRounding, 0);
//        ImGui::SetNextWindowPos(ImVec2(10, 10));
//        ImGui::SetNextWindowSize(ImVec2(0, 0), ImGuiSetCond_FirstUseEver);
//        ImGui::Begin("Vulkan Example", nullptr, ImGuiWindowFlags_AlwaysAutoResize | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove);
//        ImGui::TextUnformatted(title.c_str());
//        ImGui::TextUnformatted(deviceProperties.deviceName);
//        ImGui::Text("%.2f ms/frame (%.1d fps)", (1000.0f / lastFPS), lastFPS);
//
//        #if defined(VK_USE_PLATFORM_ANDROID_KHR)
//        ImGui::PushStyleVar(ImGuiStyleVar_ItemSpacing, ImVec2(0.0f, 5.0f * UIOverlay->scale));
//        #endif
//        ImGui::PushItemWidth(110.0f * UIOverlay->scale);
//        OnUpdateUIOverlay(UIOverlay);
//        ImGui::PopItemWidth();
//        #if defined(VK_USE_PLATFORM_ANDROID_KHR)
//        ImGui::PopStyleVar();
//        #endif
//
//        ImGui::End();
//        ImGui::PopStyleVar();
//        ImGui::Render();
//
//        UIOverlay->update();
    }

//    // Prepare the frame for workload submission
//    // - Acquires the next image from the swap chain
//    // - Sets the default wait and signal semaphores
//    void prepareFrame();
//
//    // Submit the frames' workload
//    void submitFrame();
//
//    /** @brief (Virtual) Called before the UI overlay is created, can be used to do a custom setup e.g. with different renderpass */
//    virtual void OnSetupUIOverlay(vks::UIOverlayCreateInfo &createInfo);
//    /** @brief (Virtual) Called when the UI overlay is updating, can be used to add custom elements to the overlay */
//    virtual void OnUpdateUIOverlay(vks::UIOverlay *overlay);
}