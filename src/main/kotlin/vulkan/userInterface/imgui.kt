///*
//* Vulkan Example - imGui (https://github.com/ocornut/imgui)
//*
//* Copyright (C) 2017 by Sascha Willems - www.saschawillems.de
//*
//* This code is licensed under the MIT license (MIT) (http://opensource.org/licenses/MIT)
//*/
//
//package vulkan.userInterface
//
//import glm_.BYTES
//import glm_.L
//import glm_.func.cos
//import glm_.func.rad
//import glm_.func.sin
//import glm_.i
//import glm_.mat4x4.Mat4
//import glm_.max
//import glm_.vec2.Vec2
//import glm_.vec2.Vec2i
//import glm_.vec2.operators.div
//import glm_.vec3.Vec3
//import glm_.vec4.Vec4
//import glm_.vec4.Vec4b
//import imgui.*
//import imgui.ImGui.checkbox
//import imgui.ImGui.inputVec3
//import imgui.ImGui.plotLines
//import imgui.ImGui.setNextWindowPos
//import imgui.ImGui.setNextWindowSize
//import imgui.ImGui.sliderFloat
//import imgui.ImGui.text
//import imgui.ImGui.textUnformatted
//import imgui.functionalProgramming.withWindow
//import kool.adr
//import kool.bufferBig
//import org.lwjgl.glfw.GLFW.*
//import org.lwjgl.system.MemoryUtil.*
//import org.lwjgl.vulkan.VkCommandBuffer
//import org.lwjgl.vulkan.VkQueue
//import vkk.*
//import vulkan.assetPath
//import vulkan.base.*
//import vulkan.last
//
//
//fun main(args: Array<String>) {
//    Imgui().apply {
//        setupWindow()
//        initVulkan()
//        prepare()
//        renderLoop()
//        destroy()
//    }
//}
//
//// Options and values to display/toggle from the UI
//object uiSettings {
//    var displayModels = true
//    var displayLogos = true
//    var displayBackground = true
//    var animateLight = false
//    var lightSpeed = 0.25f
//    val frameTimes = FloatArray(50)
//    var frameTimeMin = 9999f
//    var frameTimeMax = 0f
//    var lightTimer = 0f
//}
//
//// ----------------------------------------------------------------------------
//// ImGUI class
//// ----------------------------------------------------------------------------
//class ImGUI(val example: VulkanExampleBase) {
//}