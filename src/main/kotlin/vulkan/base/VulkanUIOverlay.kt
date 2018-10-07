package vulkan.base

import glm_.vec2.Vec2i
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkQueue
import vkk.VkFramebuffer
import vkk.VkFramebufferArray
import vkk.VkPipelineCache
import vkk.VkRenderPass

// TODO redo completely
class UIOverlayCreateInfo {

    var device: VulkanDevice? = null
    var copyQueue: VkQueue? = null
//    var renderPass: VkRenderPass
//    std::vector<VkFramebuffer> framebuffers;
//    VkFormat colorformat;
//    VkFormat depthformat;
//    uint32_t width;
//    uint32_t height;
//    std::vector<VkPipelineShaderStageCreateInfo> shaders;
//    VkSampleCountFlagBits rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;
//    uint32_t subpassCount = 1;
//    std::vector<VkClearValue> clearValues = {};
//    uint32_t attachmentCount = 1;
}

class UIOverlay {
//    private:
//    vks::Buffer vertexBuffer;
//    vks::Buffer indexBuffer;
//    int32_t vertexCount = 0;
//    int32_t indexCount = 0;
//
//    VkDescriptorPool descriptorPool;
//    VkDescriptorSetLayout descriptorSetLayout;
//    VkDescriptorSet descriptorSet;
//    VkPipelineLayout pipelineLayout;
//    VkPipelineCache pipelineCache;
//    VkPipeline pipeline;
//    VkRenderPass renderPass;
//    VkCommandPool commandPool;
//    VkFence fence;
//
//    VkDeviceMemory fontMemory = VK_NULL_HANDLE;
//    VkImage fontImage = VK_NULL_HANDLE;
//    VkImageView fontView = VK_NULL_HANDLE;
//    VkSampler sampler;
//
//    struct PushConstBlock {
//    glm::vec2 scale;
//    glm::vec2 translate;
//} pushConstBlock;
//
//    UIOverlayCreateInfo createInfo = {};
//
//    void prepareResources();
//    void preparePipeline();
//    void prepareRenderPass();
//    void updateCommandBuffers();
//    public:
    val visible = true
//    float scale = 1.0f;
//
//    std::vector<VkCommandBuffer> cmdBuffers;
//
//    UIOverlay(vks::UIOverlayCreateInfo createInfo);
    /** Free up all Vulkan resources acquired by the UI overlay */
    fun destroy() {
        TODO()
//        vertexBuffer.destroy();
//        indexBuffer.destroy();
//        vkDestroyImageView(createInfo.device->logicalDevice, fontView, nullptr);
//        vkDestroyImage(createInfo.device->logicalDevice, fontImage, nullptr);
//        vkFreeMemory(createInfo.device->logicalDevice, fontMemory, nullptr);
//        vkDestroySampler(createInfo.device->logicalDevice, sampler, nullptr);
//        vkDestroyDescriptorSetLayout(createInfo.device->logicalDevice, descriptorSetLayout, nullptr);
//        vkDestroyDescriptorPool(createInfo.device->logicalDevice, descriptorPool, nullptr);
//        vkDestroyPipelineLayout(createInfo.device->logicalDevice, pipelineLayout, nullptr);
//        vkDestroyPipelineCache(createInfo.device->logicalDevice, pipelineCache, nullptr);
//        vkDestroyPipeline(createInfo.device->logicalDevice, pipeline, nullptr);
//        if (createInfo.renderPass == VK_NULL_HANDLE) {
//            vkDestroyRenderPass(createInfo.device->logicalDevice, renderPass, nullptr);
//        }
//        vkFreeCommandBuffers(createInfo.device->logicalDevice, commandPool, static_cast<uint32_t>(cmdBuffers.size()), cmdBuffers.data());
//        vkDestroyCommandPool(createInfo.device->logicalDevice, commandPool, nullptr);
//        vkDestroyFence(createInfo.device->logicalDevice, fence, nullptr);
    }

    //
//    void update();
    fun resize(size: Vec2i) {

//        ImGuiIO& io = ImGui::GetIO();
//        io.DisplaySize = ImVec2((float)(width), (float)(height));
//        createInfo.width = width;
//        createInfo.height = height;
//        createInfo.framebuffers = framebuffers;
//        updateCommandBuffers()
    }

    fun preparePipeline(pipelineCache: VkPipelineCache, renderPass: VkRenderPass) = Unit

    fun update() = Unit

    fun draw(commandBuffer: VkCommandBuffer) = Unit
//
//    void submit(VkQueue queue, uint32_t bufferindex, VkSubmitInfo submitInfo);
//
//    bool header(const char* caption);
//    bool checkBox(const char* caption, bool* value);
//    bool checkBox(const char* caption, int32_t* value);
//    bool inputFloat(const char* caption, float* value, float step, uint32_t precision);
//    bool sliderFloat(const char* caption, float* value, float min, float max);
//    bool sliderInt(const char* caption, int32_t* value, int32_t min, int32_t max);
//    bool comboBox(const char* caption, int32_t* itemindex, std::vector<std::string> items);
//    bool button(const char* caption);
//    void text(const char* formatstr, ...);
}