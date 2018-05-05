//package vkn
//
//import glfw_.GlfwWindow
//import glfw_.glfw
//import org.lwjgl.system.MemoryUtil.NULL
//import org.lwjgl.vulkan.*
//
//
//var VULKAN_DEBUG_REPORT = true
//
//var UNLIMITED_FRAME_RATE = false
//
//
//fun main(args: Array<String>) {
//
//
//}
//
//class Example {
//    //#define IMGUI_MAX_POSSIBLE_BACK_BUFFERS 16
//////#define IMGUI_UNLIMITED_FRAME_RATE
////#ifdef _DEBUG
////#define IMGUI_VULKAN_DEBUG_REPORT
////#endif
////
////static VkAllocationCallbacks*   g_Allocator = NULL;
//    lateinit var instance: VkInstance
//    var surface: VkSurfaceKHR = NULL
//    lateinit var gpu: VkPhysicalDevice
//    lateinit var device: VkDevice
////static VkSwapchainKHR           g_Swapchain = VK_NULL_HANDLE;
////static VkRenderPass             g_RenderPass = VK_NULL_HANDLE;
//    var queueFamily = 0
//    lateinit var queue: VkQueue
////static VkDebugReportCallbackEXT g_Debug_Report = VK_NULL_HANDLE;
////
//    val surfaceFormat: VkSurfaceFormatKHR = VkSurfaceFormatKHR.calloc()
//    //static VkImageSubresourceRange  g_ImageRange = {VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1};
//    lateinit var presentMode: VkPresentMode
////
////static VkPipelineCache          g_PipelineCache = VK_NULL_HANDLE;
////static VkDescriptorPool         g_DescriptorPool = VK_NULL_HANDLE;
////
////static int                      fb_width = 0, fb_height = 0;
////static bool                     g_ResizeWanted = false;
////static int                      g_ResizeWidth = 0, g_ResizeHeight = 0;
////static uint32_t                 g_BackbufferIndices[IMGUI_VK_QUEUED_FRAMES];    // keep track of recently rendered swapchain frame indices
////static uint32_t                 g_BackBufferCount = 0;
////static VkImage                  g_BackBuffer[IMGUI_MAX_POSSIBLE_BACK_BUFFERS] = {};
////static VkImageView              g_BackBufferView[IMGUI_MAX_POSSIBLE_BACK_BUFFERS] = {};
////static VkFramebuffer            g_Framebuffer[IMGUI_MAX_POSSIBLE_BACK_BUFFERS] = {};
////
////static uint32_t                 g_FrameIndex = 0;
////static VkCommandPool            g_CommandPool[IMGUI_VK_QUEUED_FRAMES];
////static VkCommandBuffer          g_CommandBuffer[IMGUI_VK_QUEUED_FRAMES];
////static VkFence                  g_Fence[IMGUI_VK_QUEUED_FRAMES];
////static VkSemaphore              g_PresentCompleteSemaphore[IMGUI_VK_QUEUED_FRAMES];
////static VkSemaphore              g_RenderCompleteSemaphore[IMGUI_VK_QUEUED_FRAMES];
////
////static VkClearValue g_ClearValue = {};
//
//    init {
//        // Setup window
////    glfwSetErrorCallback(glfw_error_callback);
//        glfw.init()
//        glfw.windowHint {
//            api = "none"
//        }
//
//        val window = GlfwWindow(1280, 720, "ImGui GLFW+Vulkan example")
//
//        // Setup Vulkan
//        if (!glfw.vulkanSupported)
//            throw Error("GLFW: Vulkan Not Supported")
//        setupVulkan(window)
//        glfwSetFramebufferSizeCallback(window, glfw_resize_callback)
//
//        // Setup Dear ImGui binding
//        IMGUI_CHECKVERSION()
//        ImGui::CreateContext()
//        ImGuiIO& io = ImGui::GetIO(); (void) io
//                ImGui_ImplGlfwVulkan_Init_Data init_data = {}
//        init_data.allocator = g_Allocator
//        init_data.gpu = g_Gpu
//        init_data.device = g_Device
//        init_data.render_pass = g_RenderPass
//        init_data.pipeline_cache = g_PipelineCache
//        init_data.descriptor_pool = g_DescriptorPool
//        init_data.check_vk_result = check_vk_result
//
//        //io.ConfigFlags |= ImGuiConfigFlags_NavEnableKeyboard;  // Enable Keyboard Controls
//        ImGui_ImplGlfwVulkan_Init(window, true, & init_data)
//
//        // Setup style
//        ImGui::StyleColorsDark();
//        //ImGui::StyleColorsClassic();
//
//        // Load Fonts
//        // - If no fonts are loaded, dear imgui will use the default font. You can also load multiple fonts and use ImGui::PushFont()/PopFont() to select them.
//        // - AddFontFromFileTTF() will return the ImFont* so you can store it if you need to select the font among multiple.
//        // - If the file cannot be loaded, the function will return NULL. Please handle those errors in your application (e.g. use an assertion, or display an error and quit).
//        // - The fonts will be rasterized at a given size (w/ oversampling) and stored into a texture when calling ImFontAtlas::Build()/GetTexDataAsXXXX(), which ImGui_ImplXXXX_NewFrame below will call.
//        // - Read 'misc/fonts/README.txt' for more instructions and details.
//        // - Remember that in C/C++ if you want to include a backslash \ in a string literal you need to write a double backslash \\ !
//        //io.Fonts->AddFontDefault();
//        //io.Fonts->AddFontFromFileTTF("../../misc/fonts/Roboto-Medium.ttf", 16.0f);
//        //io.Fonts->AddFontFromFileTTF("../../misc/fonts/Cousine-Regular.ttf", 15.0f);
//        //io.Fonts->AddFontFromFileTTF("../../misc/fonts/DroidSans.ttf", 16.0f);
//        //io.Fonts->AddFontFromFileTTF("../../misc/fonts/ProggyTiny.ttf", 10.0f);
//        //ImFont* font = io.Fonts->AddFontFromFileTTF("c:\\Windows\\Fonts\\ArialUni.ttf", 18.0f, NULL, io.Fonts->GetGlyphRangesJapanese());
//        //IM_ASSERT(font != NULL);
//
//        // Upload Fonts
//        {
//            VkResult err
//                    err = vkResetCommandPool(g_Device, g_CommandPool[g_FrameIndex], 0)
//            check_vk_result(err)
//            VkCommandBufferBeginInfo begin_info = {}
//            begin_info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO
//            begin_info.flags | = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT
//            err = vkBeginCommandBuffer(g_CommandBuffer[g_FrameIndex], & begin_info)
//            check_vk_result(err)
//
//            ImGui_ImplGlfwVulkan_CreateFontsTexture(g_CommandBuffer[g_FrameIndex])
//
//            VkSubmitInfo end_info = {}
//            end_info.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO
//            end_info.commandBufferCount = 1
//            end_info.pCommandBuffers = & g_CommandBuffer [g_FrameIndex]
//            err = vkEndCommandBuffer(g_CommandBuffer[g_FrameIndex])
//            check_vk_result(err)
//            err = vkQueueSubmit(g_Queue, 1, & end_info, VK_NULL_HANDLE)
//            check_vk_result(err)
//
//            err = vkDeviceWaitIdle(g_Device)
//            check_vk_result(err)
//            ImGui_ImplGlfwVulkan_InvalidateFontUploadObjects()
//        }
//
//        bool show_demo_window = true
//        bool show_another_window = false
//        ImVec4 clear_color = ImVec4 (0.45f, 0.55f, 0.60f, 1.00f)
//
//
//        // Main loop
//        while (!glfwWindowShouldClose(window)) {
//            // You can read the io.WantCaptureMouse, io.WantCaptureKeyboard flags to tell if dear imgui wants to use your inputs.
//            // - When io.WantCaptureMouse is true, do not dispatch mouse input data to your main application.
//            // - When io.WantCaptureKeyboard is true, do not dispatch keyboard input data to your main application.
//            // Generally you may always pass all inputs to dear imgui, and hide them from your application based on those two flags.
//            glfwPollEvents()
//
//            if (g_ResizeWanted)
//                resize_vulkan(g_ResizeWidth, g_ResizeHeight)
//            g_ResizeWanted = false
//
//            ImGui_ImplGlfwVulkan_NewFrame();
//
//            // 1. Show a simple window.
//            // Tip: if we don't call ImGui::Begin()/ImGui::End() the widgets automatically appears in a window called "Debug".
//            {
//                static float f = 0.0f
//                static int counter = 0
//                ImGui::Text("Hello, world!")                           // Display some text (you can use a format string too)
//                ImGui::SliderFloat("float", & f, 0.0f, 1.0f)            // Edit 1 float using a slider from 0.0f to 1.0f
//                ImGui::ColorEdit3("clear color", (float *)& clear_color) // Edit 3 floats representing a color
//
//                ImGui::Checkbox("Demo Window", & show_demo_window)      // Edit bools storing our windows open/close state
//                ImGui::Checkbox("Another Window", & show_another_window)
//
//                if (ImGui::Button("Button"))                            // Buttons return true when clicked (NB: most widgets return true when edited/activated)
//                    counter++
//                ImGui::SameLine()
//                ImGui::Text("counter = %d", counter)
//
//                ImGui::Text("Application average %.3f ms/frame (%.1f FPS)", 1000.0f / ImGui::GetIO().Framerate, ImGui::GetIO().Framerate)
//            }
//
//            // 2. Show another simple window. In most cases you will use an explicit Begin/End pair to name your windows.
//            if (show_another_window) {
//                ImGui::Begin("Another Window", & show_another_window)
//                ImGui::Text("Hello from another window!")
//                if (ImGui::Button("Close Me"))
//                    show_another_window = false
//                ImGui::End()
//            }
//
//            // 3. Show the ImGui demo window. Most of the sample code is in ImGui::ShowDemoWindow(). Read its code to learn more about Dear ImGui!
//            if (show_demo_window) {
//                ImGui::SetNextWindowPos(ImVec2(650, 20), ImGuiCond_FirstUseEver) // Normally user code doesn't need/want to call this because positions are saved in .ini file anyway. Here we just want to make the demo initial state a bit more friendly!
//                ImGui::ShowDemoWindow(& show_demo_window)
//            }
//
//            memcpy(& g_ClearValue . color . float32 [0], &clear_color, 4 * sizeof(float))
//            frame_begin()
//            ImGui_ImplGlfwVulkan_Render(g_CommandBuffer[g_FrameIndex])
//            frame_end()
//            frame_present()
//        }
//
//        // Cleanup
//        VkResult err = vkDeviceWaitIdle (g_Device)
//        check_vk_result(err)
//        ImGui_ImplGlfwVulkan_Shutdown()
//        ImGui::DestroyContext()
//        cleanup_vulkan()
//
//        glfwDestroyWindow(window)
//        glfwTerminate()
//
//        return 0
//    }
//
//
//    fun setupVulkan(window: GlfwWindow) {
//
//        // Create Vulkan Instance
//        run {
//            val extensions = glfw.requiredInstanceExtensions
//
//            val createInfo = vk.InstanceCreateInfo()
//
//            if (VULKAN_DEBUG_REPORT) {
//                // enabling multiple validation layers grouped as lunarg standard validation
//                val layers = listOf("VK_LAYER_LUNARG_standard_validation")
//                createInfo.enabledLayerNames = layers
//
//                // need additional storage for char pointer to debug report extension
//                extensions += "VK_EXT_debug_report"
//                createInfo.enabledExtensionNames = extensions
//            }
//
//            instance = vk.createInstance(createInfo)
//
//            if (VULKAN_DEBUG_REPORT) {
//                // create the debug report callback
////            val debugReportCi = vk.DebugReportCallbackCreateInfoEXT {  }
////            debugReportCi.sType = VK_STRUCTURE_TYPE_DEBUG_REPORT_CALLBACK_CREATE_INFO_EXT
////            debugReportCi.flags = VK_DEBUG_REPORT_ERROR_BIT_EXT | VK_DEBUG_REPORT_WARNING_BIT_EXT | VK_DEBUG_REPORT_PERFORMANCE_WARNING_BIT_EXT
////            debugReportCi.pfnCallback = debug_report
////            debugReportCi.pUserData = NULL
////
////            // get the proc address of the function pointer, required for used extensions
////            PFN_vkCreateDebugReportCallbackEXT vkCreateDebugReportCallbackEXT =
////            (PFN_vkCreateDebugReportCallbackEXT) vkGetInstanceProcAddr (g_Instance, "vkCreateDebugReportCallbackEXT")
////
////            err = vkCreateDebugReportCallbackEXT(g_Instance, & debug_report_ci, g_Allocator, &g_Debug_Report)
//            }
//        }
//
//        // Create Window Surface
//        surface = window createSurface instance
//
//        // Get GPU
//        gpu = instance.enumeratePhysicalDevices()[0]
//
//        // Get queue
//        run {
//            val queues = gpu.queueFamilyProperties
//            for (i in queues.indices)
//                if (queues[i].queueFlags has VkQueueFlag.GRAPHICS_BIT) {
//                    queueFamily = i
//                    break
//                }
//        }
//
//        // Check for WSI support
//        if (!gpu.getSurfaceSupportKHR(queueFamily, surface))
//            throw Error("Error no WSI support on physical device 0")
//
//        // Get Surface Format
//        run {
//            /*  Per Spec Format and View Format are expected to be the same unless VK_IMAGE_CREATE_MUTABLE_BIT was set
//                at image creation
//                Assuming that the default behavior is without setting this bit, there is no need for separate Spawchain
//                image and image view format
//                additionally several new color spaces were introduced with Vulkan Spec v1.0.40
//                hence we must make sure that a format with the mostly available color space,
//                VK_COLOR_SPACE_SRGB_NONLINEAR_KHR, is found and used */
//            val formats = gpu getSurfaceFormatsKHR surface
//
//            // first check if only one format, VK_FORMAT_UNDEFINED, is available, which would imply that any format is available
//            if (formats.size == 1) {
//                if (formats[0].format == VkFormat.UNDEFINED) {
//                    surfaceFormat.format = VkFormat.B8G8R8A8_UNORM
//                    surfaceFormat.colorSpace = VkColorSpace.SRGB_NONLINEAR_KHR
//                } else    // no point in searching another format
//                    surfaceFormat(formats[0])
//            } else {
//                // request several formats, the first found will be used
//                val requestSurfaceImageFormat = arrayOf(VkFormat.B8G8R8A8_UNORM, VkFormat.R8G8B8A8_UNORM, VkFormat.B8G8R8_UNORM, VkFormat.R8G8B8_UNORM)
//                val requestSurfaceColorSpace = VkColorSpace.SRGB_NONLINEAR_KHR
//                var requestedFound = false
//                for (i in requestSurfaceImageFormat.indices) {
//                    if (requestedFound)
//                        break
//                    for (j in formats.indices)
//                        if (formats[j].format == requestSurfaceImageFormat[i] && formats[j].colorSpace == requestSurfaceColorSpace) {
//                            surfaceFormat(formats[j])
//                            requestedFound = true
//                        }
//                }
//
//                // if none of the requested image formats could be found, use the first available
//                if (!requestedFound)
//                    surfaceFormat(formats[0])
//            }
//        }
//
//
//        // Get Present Mode
//        run {
//            // Request a certain mode and confirm that it is available. If not use VK_PRESENT_MODE_FIFO_KHR which is mandatory
//            presentMode = when {
//                UNLIMITED_FRAME_RATE -> VkPresentMode.MAILBOX_KHR //VK_PRESENT_MODE_IMMEDIATE_KHR;
//                else -> VkPresentMode.FIFO_KHR
//            }
//            val presentModes = gpu getSurfaceFormatsKHR surface
//            val presentModeAvailable = presentModes.any { it == presentMode }
//            if (!presentModeAvailable)
//                presentMode = VkPresentMode.FIFO_KHR   // always available
//        }
//
//
//        // Create Logical Device
//        run {
//            val deviceExtensions = listOf("VK_KHR_swapchain")
//            val queueIndex = 0
//            val queueCount = 1
//            val queuePriority = 1f
//            val queueInfo = vk.DeviceQueueCreateInfo {
//                queueFamilyIndex = queueFamily
//                this.queuePriority = queuePriority
//            }
//            val createInfo = vk.DeviceCreateInfo {
//                queueCreateInfo = queueInfo
//                enabledExtensionNames = deviceExtensions
//            }
//            device = gpu createDevice createInfo
//            queue = device.getQueue(queueFamily, queueIndex)
//        }
//
//        // Create Framebuffers
//        run {
//            int w, h
//            glfwGetFramebufferSize(window, & w, &h)
//            resize_vulkan(w, h)
//        }
//
//        // Create Command Buffers
//        for (int i = 0; i < IMGUI_VK_QUEUED_FRAMES; i++)
//        {
//            {
//                VkCommandPoolCreateInfo info = {}
//                info.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO
//                info.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT
//                info.queueFamilyIndex = g_QueueFamily
//                err = vkCreateCommandPool(g_Device, & info, g_Allocator, &g_CommandPool[i])
//                check_vk_result(err)
//            }
//            {
//                VkCommandBufferAllocateInfo info = {}
//                info.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO
//                info.commandPool = g_CommandPool[i]
//                info.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY
//                info.commandBufferCount = 1
//                err = vkAllocateCommandBuffers(g_Device, & info, &g_CommandBuffer[i])
//                check_vk_result(err)
//            }
//            {
//                VkFenceCreateInfo info = {}
//                info.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO
//                info.flags = VK_FENCE_CREATE_SIGNALED_BIT
//                err = vkCreateFence(g_Device, & info, g_Allocator, &g_Fence[i])
//                check_vk_result(err)
//            }
//            {
//                VkSemaphoreCreateInfo info = {}
//                info.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO
//                err = vkCreateSemaphore(g_Device, & info, g_Allocator, &g_PresentCompleteSemaphore[i])
//                check_vk_result(err)
//                err = vkCreateSemaphore(g_Device, & info, g_Allocator, &g_RenderCompleteSemaphore[i])
//                check_vk_result(err)
//            }
//        }
//
//        // Create Descriptor Pool
//        {
//            VkDescriptorPoolSize pool_size [11] =
//                    {
//                        { VK_DESCRIPTOR_TYPE_SAMPLER, 1000 },
//                        { VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, 1000 },
//                        { VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE, 1000 },
//                        { VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1000 },
//                        { VK_DESCRIPTOR_TYPE_UNIFORM_TEXEL_BUFFER, 1000 },
//                        { VK_DESCRIPTOR_TYPE_STORAGE_TEXEL_BUFFER, 1000 },
//                        { VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, 1000 },
//                        { VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 1000 },
//                        { VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, 1000 },
//                        { VK_DESCRIPTOR_TYPE_STORAGE_BUFFER_DYNAMIC, 1000 },
//                        { VK_DESCRIPTOR_TYPE_INPUT_ATTACHMENT, 1000 }
//                    }
//            VkDescriptorPoolCreateInfo pool_info = {}
//            pool_info.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO
//            pool_info.flags = VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT
//            pool_info.maxSets = 1000 * 11
//            pool_info.poolSizeCount = 11
//            pool_info.pPoolSizes = pool_size
//            err = vkCreateDescriptorPool(g_Device, & pool_info, g_Allocator, &g_DescriptorPool)
//            check_vk_result(err)
//        }
//    }
//}