# Vulkan Kotlin examples and demos

A comprehensive collection of open source Kotlin examples for [Vulkan®](https://www.khronos.org/vulkan/), the new graphics and compute API from Khronos, based on the excellent examples of [SaschaWillems](https://github.com/SaschaWillems/Vulkan)

## Examples

### Basics

#### [01 - Triangle](src/main/kotlin/vulkan/01%20triangle.kt)
Basic and verbose example for getting a colored triangle rendered to the screen using Vulkan. This is meant as a starting point for learning Vulkan from the ground up. A huge part of the code is boilerplate that is abstracted away in later examples.

#### [02 - Pipelines](examples/pipelines/)

Using pipeline state objects (pso) that bake state information (rasterization states, culling modes, etc.) along with the shaders into a single object, making it easy for an implementation to optimize usage (compared to OpenGL's dynamic state machine). Also demonstrates the use of pipeline derivatives.
