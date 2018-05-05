///**
// *  ImGui GLFW binding with Vulkan + shaders
// *
// *  Missing features:
// *  [ ] User texture binding. Changes of ImTextureID aren't supported by this binding! See https://github.com/ocornut/imgui/pull/914
// *
// *  You can copy and use unmodified imgui_impl_* files in your project. See main.cpp for an example of using this.
// *  If you use this binding you'll need to call 5 functions: ImplXXXX_Init(), ImplXXX_CreateFontsTexture(), ImplXXXX_NewFrame(),
// *  ImplXXXX_Render() and ImplXXXX_Shutdown().
// *  If you are new to ImGui, see examples/README.txt and documentation at the top of imgui.cpp.
// *  https://github.com/ocornut/imgui
// */
//
//package vkn
//
//import glfw_.GlfwWindow
//import org.lwjgl.system.MemoryUtil.NULL
//import org.lwjgl.vulkan.VkCommandBuffer
//import org.lwjgl.vulkan.VkDevice
//import org.lwjgl.vulkan.VkPhysicalDevice
//import uno.buffer.intBufferOf
//import uno.buffer.longBufferBig
//
//const val VK_QUEUED_FRAMES = 2
//
////struct ImGui_ImplGlfwVulkan_Init_Data
////{
////    VkAllocationCallbacks* allocator;
////    VkPhysicalDevice       gpu;
////    VkDevice               device;
////    VkRenderPass           render_pass;
////    VkPipelineCache        pipeline_cache;
////    VkDescriptorPool       descriptor_pool;
////    void (*check_vk_result)(VkResult err);
////};
//
//// GLFW data
//var gWindow: GlfwWindow? = null
//var gTime = 0.0
//var gMouseJustPressed = BooleanArray(3)
////static GLFWcursor*  g_MouseCursors[ImGuiMouseCursor_COUNT] = { 0 };
//
//// Vulkan data
////static VkAllocationCallbacks* g_Allocator = NULL;
//lateinit var gGpu: VkPhysicalDevice
//lateinit var gDevice: VkDevice
//var gRenderPass: VkRenderPass = NULL
//var gPipelineCache: VkPipelineCache = NULL
//var gDescriptorPool: VkDescriptorPool = NULL
////static void (*g_CheckVkResult)(VkResult err) = NULL;
//
//lateinit var gCommandBuffer: VkCommandBuffer
//var gBufferMemoryAlignment: VkDeviceSize = 256
//var gPipelineCreateFlags: VkPipelineCreateFlags = 0
//var gFrameIndex = 0
//
//var gDescriptorSetLayout: VkDescriptorSetLayout = NULL
//var gPipelineLayout: VkPipelineLayout = NULL
//var gDescriptorSet: VkDescriptorSet = NULL
//var gPipeline: VkPipeline = NULL
//
//var gFontSampler: VkSampler = NULL
//var gFontMemory: VkDeviceMemory = NULL
//var gFontImage: VkImage = NULL
//var gFontView: VkImageView = NULL
//
//var gVertexBufferMemory: VkDeviceMemoryBuffer = longBufferBig(VK_QUEUED_FRAMES)
//var gIndexBufferMemory: VkDeviceMemoryBuffer = longBufferBig(VK_QUEUED_FRAMES)
//var gVertexBufferSize: VkDeviceSizeBuffer = longBufferBig(VK_QUEUED_FRAMES)
//var gIndexBufferSize: VkDeviceSizeBuffer = longBufferBig(VK_QUEUED_FRAMES)
//var gVertexBuffer: VkBufferBuffer = longBufferBig(VK_QUEUED_FRAMES)
//var gIndexBuffer: VkBufferBuffer = longBufferBig(VK_QUEUED_FRAMES)
//
//var gUploadBufferMemory: VkDeviceMemory = NULL
//var gUploadBuffer: VkBuffer = NULL
//
//val glslShaderVertSpv = intBufferOf(
//    0x07230203,0x00010000,0x00080001,0x0000002e,0x00000000,0x00020011,0x00000001,0x0006000b,
//    0x00000001,0x4c534c47,0x6474732e,0x3035342e,0x00000000,0x0003000e,0x00000000,0x00000001,
//    0x000a000f,0x00000000,0x00000004,0x6e69616d,0x00000000,0x0000000b,0x0000000f,0x00000015,
//    0x0000001b,0x0000001c,0x00030003,0x00000002,0x000001c2,0x00040005,0x00000004,0x6e69616d,
//    0x00000000,0x00030005,0x00000009,0x00000000,0x00050006,0x00000009,0x00000000,0x6f6c6f43,
//    0x00000072,0x00040006,0x00000009,0x00000001,0x00005655,0x00030005,0x0000000b,0x0074754f,
//    0x00040005,0x0000000f,0x6c6f4361,0x0000726f,0x00030005,0x00000015,0x00565561,0x00060005,
//    0x00000019,0x505f6c67,0x65567265,0x78657472,0x00000000,0x00060006,0x00000019,0x00000000,
//    0x505f6c67,0x7469736f,0x006e6f69,0x00030005,0x0000001b,0x00000000,0x00040005,0x0000001c,
//    0x736f5061,0x00000000,0x00060005,0x0000001e,0x73755075,0x6e6f4368,0x6e617473,0x00000074,
//    0x00050006,0x0000001e,0x00000000,0x61635375,0x0000656c,0x00060006,0x0000001e,0x00000001,
//    0x61725475,0x616c736e,0x00006574,0x00030005,0x00000020,0x00006370,0x00040047,0x0000000b,
//    0x0000001e,0x00000000,0x00040047,0x0000000f,0x0000001e,0x00000002,0x00040047,0x00000015,
//    0x0000001e,0x00000001,0x00050048,0x00000019,0x00000000,0x0000000b,0x00000000,0x00030047,
//    0x00000019,0x00000002,0x00040047,0x0000001c,0x0000001e,0x00000000,0x00050048,0x0000001e,
//    0x00000000,0x00000023,0x00000000,0x00050048,0x0000001e,0x00000001,0x00000023,0x00000008,
//    0x00030047,0x0000001e,0x00000002,0x00020013,0x00000002,0x00030021,0x00000003,0x00000002,
//    0x00030016,0x00000006,0x00000020,0x00040017,0x00000007,0x00000006,0x00000004,0x00040017,
//    0x00000008,0x00000006,0x00000002,0x0004001e,0x00000009,0x00000007,0x00000008,0x00040020,
//    0x0000000a,0x00000003,0x00000009,0x0004003b,0x0000000a,0x0000000b,0x00000003,0x00040015,
//    0x0000000c,0x00000020,0x00000001,0x0004002b,0x0000000c,0x0000000d,0x00000000,0x00040020,
//    0x0000000e,0x00000001,0x00000007,0x0004003b,0x0000000e,0x0000000f,0x00000001,0x00040020,
//    0x00000011,0x00000003,0x00000007,0x0004002b,0x0000000c,0x00000013,0x00000001,0x00040020,
//    0x00000014,0x00000001,0x00000008,0x0004003b,0x00000014,0x00000015,0x00000001,0x00040020,
//    0x00000017,0x00000003,0x00000008,0x0003001e,0x00000019,0x00000007,0x00040020,0x0000001a,
//    0x00000003,0x00000019,0x0004003b,0x0000001a,0x0000001b,0x00000003,0x0004003b,0x00000014,
//    0x0000001c,0x00000001,0x0004001e,0x0000001e,0x00000008,0x00000008,0x00040020,0x0000001f,
//    0x00000009,0x0000001e,0x0004003b,0x0000001f,0x00000020,0x00000009,0x00040020,0x00000021,
//    0x00000009,0x00000008,0x0004002b,0x00000006,0x00000028,0x00000000,0x0004002b,0x00000006,
//    0x00000029,0x3f800000,0x00050036,0x00000002,0x00000004,0x00000000,0x00000003,0x000200f8,
//    0x00000005,0x0004003d,0x00000007,0x00000010,0x0000000f,0x00050041,0x00000011,0x00000012,
//    0x0000000b,0x0000000d,0x0003003e,0x00000012,0x00000010,0x0004003d,0x00000008,0x00000016,
//    0x00000015,0x00050041,0x00000017,0x00000018,0x0000000b,0x00000013,0x0003003e,0x00000018,
//    0x00000016,0x0004003d,0x00000008,0x0000001d,0x0000001c,0x00050041,0x00000021,0x00000022,
//    0x00000020,0x0000000d,0x0004003d,0x00000008,0x00000023,0x00000022,0x00050085,0x00000008,
//    0x00000024,0x0000001d,0x00000023,0x00050041,0x00000021,0x00000025,0x00000020,0x00000013,
//    0x0004003d,0x00000008,0x00000026,0x00000025,0x00050081,0x00000008,0x00000027,0x00000024,
//    0x00000026,0x00050051,0x00000006,0x0000002a,0x00000027,0x00000000,0x00050051,0x00000006,
//    0x0000002b,0x00000027,0x00000001,0x00070050,0x00000007,0x0000002c,0x0000002a,0x0000002b,
//    0x00000028,0x00000029,0x00050041,0x00000011,0x0000002d,0x0000001b,0x0000000d,0x0003003e,
//    0x0000002d,0x0000002c,0x000100fd,0x00010038)
//
//val glslShaderFragSpv = intBufferOf(
//    0x07230203,0x00010000,0x00080001,0x0000001e,0x00000000,0x00020011,0x00000001,0x0006000b,
//    0x00000001,0x4c534c47,0x6474732e,0x3035342e,0x00000000,0x0003000e,0x00000000,0x00000001,
//    0x0007000f,0x00000004,0x00000004,0x6e69616d,0x00000000,0x00000009,0x0000000d,0x00030010,
//    0x00000004,0x00000007,0x00030003,0x00000002,0x000001c2,0x00040005,0x00000004,0x6e69616d,
//    0x00000000,0x00040005,0x00000009,0x6c6f4366,0x0000726f,0x00030005,0x0000000b,0x00000000,
//    0x00050006,0x0000000b,0x00000000,0x6f6c6f43,0x00000072,0x00040006,0x0000000b,0x00000001,
//    0x00005655,0x00030005,0x0000000d,0x00006e49,0x00050005,0x00000016,0x78655473,0x65727574,
//    0x00000000,0x00040047,0x00000009,0x0000001e,0x00000000,0x00040047,0x0000000d,0x0000001e,
//    0x00000000,0x00040047,0x00000016,0x00000022,0x00000000,0x00040047,0x00000016,0x00000021,
//    0x00000000,0x00020013,0x00000002,0x00030021,0x00000003,0x00000002,0x00030016,0x00000006,
//    0x00000020,0x00040017,0x00000007,0x00000006,0x00000004,0x00040020,0x00000008,0x00000003,
//    0x00000007,0x0004003b,0x00000008,0x00000009,0x00000003,0x00040017,0x0000000a,0x00000006,
//    0x00000002,0x0004001e,0x0000000b,0x00000007,0x0000000a,0x00040020,0x0000000c,0x00000001,
//    0x0000000b,0x0004003b,0x0000000c,0x0000000d,0x00000001,0x00040015,0x0000000e,0x00000020,
//    0x00000001,0x0004002b,0x0000000e,0x0000000f,0x00000000,0x00040020,0x00000010,0x00000001,
//    0x00000007,0x00090019,0x00000013,0x00000006,0x00000001,0x00000000,0x00000000,0x00000000,
//    0x00000001,0x00000000,0x0003001b,0x00000014,0x00000013,0x00040020,0x00000015,0x00000000,
//    0x00000014,0x0004003b,0x00000015,0x00000016,0x00000000,0x0004002b,0x0000000e,0x00000018,
//    0x00000001,0x00040020,0x00000019,0x00000001,0x0000000a,0x00050036,0x00000002,0x00000004,
//    0x00000000,0x00000003,0x000200f8,0x00000005,0x00050041,0x00000010,0x00000011,0x0000000d,
//    0x0000000f,0x0004003d,0x00000007,0x00000012,0x00000011,0x0004003d,0x00000014,0x00000017,
//    0x00000016,0x00050041,0x00000019,0x0000001a,0x0000000d,0x00000018,0x0004003d,0x0000000a,
//    0x0000001b,0x0000001a,0x00050057,0x00000007,0x0000001c,0x00000017,0x0000001b,0x00050085,
//    0x00000007,0x0000001d,0x00000012,0x0000001c,0x0003003e,0x00000009,0x0000001d,0x000100fd,
//    0x00010038)
//
//fun ImplGlfwVulkan_Init(window: GlfwWindow, installCallbacks: Boolean, ImGui_ImplGlfwVulkan_Init_Data *init_data) {
////    g_Allocator = init_data->allocator;
//    g_Gpu = init_data->gpu;
//    g_Device = init_data->device;
//    g_RenderPass = init_data->render_pass;
//    g_PipelineCache = init_data->pipeline_cache;
//    g_DescriptorPool = init_data->descriptor_pool;
//    g_CheckVkResult = init_data->check_vk_result;
//
//    g_Window = window;
//
//    // Setup back-end capabilities flags
//    ImGuiIO& io = ImGui::GetIO();
//    io.BackendFlags |= ImGuiBackendFlags_HasMouseCursors;   // We can honor GetMouseCursor() values (optional)
//
//    // Keyboard mapping. ImGui will use those indices to peek into the io.KeysDown[] array.
//    io.KeyMap[ImGuiKey_Tab] = GLFW_KEY_TAB;
//    io.KeyMap[ImGuiKey_LeftArrow] = GLFW_KEY_LEFT;
//    io.KeyMap[ImGuiKey_RightArrow] = GLFW_KEY_RIGHT;
//    io.KeyMap[ImGuiKey_UpArrow] = GLFW_KEY_UP;
//    io.KeyMap[ImGuiKey_DownArrow] = GLFW_KEY_DOWN;
//    io.KeyMap[ImGuiKey_PageUp] = GLFW_KEY_PAGE_UP;
//    io.KeyMap[ImGuiKey_PageDown] = GLFW_KEY_PAGE_DOWN;
//    io.KeyMap[ImGuiKey_Home] = GLFW_KEY_HOME;
//    io.KeyMap[ImGuiKey_End] = GLFW_KEY_END;
//    io.KeyMap[ImGuiKey_Insert] = GLFW_KEY_INSERT;
//    io.KeyMap[ImGuiKey_Delete] = GLFW_KEY_DELETE;
//    io.KeyMap[ImGuiKey_Backspace] = GLFW_KEY_BACKSPACE;
//    io.KeyMap[ImGuiKey_Space] = GLFW_KEY_SPACE;
//    io.KeyMap[ImGuiKey_Enter] = GLFW_KEY_ENTER;
//    io.KeyMap[ImGuiKey_Escape] = GLFW_KEY_ESCAPE;
//    io.KeyMap[ImGuiKey_A] = GLFW_KEY_A;
//    io.KeyMap[ImGuiKey_C] = GLFW_KEY_C;
//    io.KeyMap[ImGuiKey_V] = GLFW_KEY_V;
//    io.KeyMap[ImGuiKey_X] = GLFW_KEY_X;
//    io.KeyMap[ImGuiKey_Y] = GLFW_KEY_Y;
//    io.KeyMap[ImGuiKey_Z] = GLFW_KEY_Z;
//
//    io.SetClipboardTextFn = ImGui_ImplGlfwVulkan_SetClipboardText;
//    io.GetClipboardTextFn = ImGui_ImplGlfwVulkan_GetClipboardText;
//    io.ClipboardUserData = g_Window;
//    #ifdef _WIN32
//            io.ImeWindowHandle = glfwGetWin32Window(g_Window);
//    #endif
//
//    // Load cursors
//    // FIXME: GLFW doesn't expose suitable cursors for ResizeAll, ResizeNESW, ResizeNWSE. We revert to arrow cursor for those.
//    g_MouseCursors[ImGuiMouseCursor_Arrow] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
//    g_MouseCursors[ImGuiMouseCursor_TextInput] = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR);
//    g_MouseCursors[ImGuiMouseCursor_ResizeAll] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
//    g_MouseCursors[ImGuiMouseCursor_ResizeNS] = glfwCreateStandardCursor(GLFW_VRESIZE_CURSOR);
//    g_MouseCursors[ImGuiMouseCursor_ResizeEW] = glfwCreateStandardCursor(GLFW_HRESIZE_CURSOR);
//    g_MouseCursors[ImGuiMouseCursor_ResizeNESW] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
//    g_MouseCursors[ImGuiMouseCursor_ResizeNWSE] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
//
//    if (install_callbacks)
//        ImGui_ImplGlfw_InstallCallbacks(window);
//
//    ImGui_ImplGlfwVulkan_CreateDeviceObjects();
//
//    return true;
//}
//IMGUI_API void        ImGui_ImplGlfwVulkan_Shutdown()
//IMGUI_API void        ImGui_ImplGlfwVulkan_NewFrame()
//IMGUI_API void        ImGui_ImplGlfwVulkan_Render(VkCommandBuffer command_buffer)
//
//// Use if you want to reset your rendering device without losing ImGui state.
//IMGUI_API void        ImGui_ImplGlfwVulkan_InvalidateFontUploadObjects()
//IMGUI_API void        ImGui_ImplGlfwVulkan_InvalidateDeviceObjects()
//IMGUI_API bool        ImGui_ImplGlfwVulkan_CreateFontsTexture(VkCommandBuffer command_buffer)
//IMGUI_API bool        ImGui_ImplGlfwVulkan_CreateDeviceObjects()
//
//// GLFW callbacks (installed by default if you enable 'install_callbacks' during initialization)
//// Provided here if you want to chain callbacks.
//// You can also handle inputs yourself and use those as a reference.
//IMGUI_API void        ImGui_ImplGlfw_MouseButtonCallback(GLFWwindow* window, int button, int action, int mods)
//IMGUI_API void        ImGui_ImplGlfw_ScrollCallback(GLFWwindow* window, double xoffset, double yoffset)
//IMGUI_API void        ImGui_ImplGlfw_KeyCallback(GLFWwindow* window, int key, int scancode, int action, int mods)
//IMGUI_API void ImGui_ImplGlfw_CharCallback(GLFWwindow* window, unsigned int c)