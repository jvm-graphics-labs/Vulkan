# Vulkan Kotlin examples and demos

A comprehensive collection of open source Kotlin examples for [Vulkan®](https://www.khronos.org/vulkan/), the new graphics and compute API from Khronos, based on the excellent examples of [Sascha Willems](https://github.com/SaschaWillems/Vulkan)

Enhancements:

The examples uses a wrapper/library I'm developing in order to easier and push tedious code behind the curtains without overhead (in `inline` we trust). The idea is pretty simple, a buffer (64k at the moment) gets allocated for the whole time and used as a pool memory for once-time frequent allocations, by moving the pointer for the requested amount of memory. 

No manual management, no stack pushes and pops. Dead simple. And also faster, because you don't have any additional overhead given by continuous memory `malloc`s/`calloc`s or Thread Local Store lookups (which can easily add up in hot loops).

At the begin of every frame, the pointer gets reset to the initial position and the whole buffer gets zeroed/cleared.

The expressiveness of Kotlin meets the power of Vulkan.

## Examples

### Basics

#### [01 - Triangle](src/main/kotlin/vulkan/01%20triangle.kt)
Basic and verbose example for getting a colored triangle rendered to the screen using Vulkan. This is meant as a starting point for learning Vulkan from the ground up. A huge part of the code is boilerplate that is abstracted away in later examples.

#### [02 - Pipelines](examples/pipelines/) TODO

Using pipeline state objects (pso) that bake state information (rasterization states, culling modes, etc.) along with the shaders into a single object, making it easy for an implementation to optimize usage (compared to OpenGL's dynamic state machine). Also demonstrates the use of pipeline derivatives.

#### [03 - Descriptor sets](examples/descriptorsets) TODO

Descriptors are used to pass data to shader binding points. Sets up descriptor sets, layouts, pools, creates a single pipeline based on the set layout and renders multiple objects with different descriptor sets.

#### [04 - Dynamic uniform buffers](examples/dynamicuniformbuffer/) TODO

Dynamic uniform buffers are used for rendering multiple objects with multiple matrices stored in a single uniform buffer object. Individual matrices are dynamically addressed upon descriptor binding time, minimizing the number of required descriptor sets.

#### [05 - Push constants](examples/pushconstants/) TODO

Uses push constants, small blocks of uniform data stored within a command buffer, to pass data to a shader without the need for uniform buffers.

#### [06 - Specialization constants](examples/specializationconstants/) TODO

Uses SPIR-V specialization constants to create multiple pipelines with different lighting paths from a single "uber" shader.

#### [07 - Texture mapping](src/main/kotlin/vulkan/07%20texture.kt)

Loads a 2D texture from disk (including all mip levels), uses staging to upload it into video memory and samples from it using combined image samplers.

##### Credits:

- [Sasha](https://github.com/SaschaWillems), awesome repo (except for avoiding using glfw)
- [Spasi](https://github.com/Spasi), for assisting (and tolerating) my continuous boring questions :), also the tip about the buffer came from him 
- [Kai](https://github.com/httpdigest), for his first Hello Triangle in java
- [Urlick](https://github.com/skalarproduktraum), for some tips and the glsl -> spir-v converter
