using import Array glm Option struct

import bottle
using bottle.types
using bottle.enums

type+ mat4
    inline... translation (v : vec3)
        mat4
            vec4 1   0   0   0
            vec4 0   1   0   0
            vec4 0   0   1   0
            vec4 v.x v.y v.z 1

@@ 'on bottle.configure
fn (cfg)
    cfg.window.title = "tsukimoto"

struct VertexAttributes plain
    position : vec3
    texcoords : vec2
    color : vec4

struct Uniforms plain
    mvp : mat4
    time : f32

struct GraphicsContext
    mesh-vertices : (StorageBuffer VertexAttributes)
    mesh-indices : (IndexBuffer u16)
    uniforms : (UniformBuffer Uniforms)
    pipeline : RenderPipeline
    bind-group : BindGroup

global gfx-context : (Option GraphicsContext)

fn shaderf-vert ()
    using import glsl
    using import glm

    uniform uniforms : Uniforms
        set = 0
        binding = 0

    buffer data :
        struct VertexInputData plain
            attributes : (array VertexAttributes)
        set = 0
        binding = 1

    # out vtexcoords : vec2 (location = 0)
    out vcolor : vec4 (location = 1)

    idx := gl_VertexIndex
    v := data.attributes @ idx

    # vtexcoords = v.texcoords
    vcolor = v.color
    gl_Position = uniforms.mvp * (vec4 v.position 1.0)

fn shaderf-frag ()
    using import glsl
    using import glm

    in vtexcoords : vec2 (location = 0)
    in vcolor : vec4 (location = 1)
    out fcolor : vec4 (location = 0)

    fcolor = vcolor

@@ 'on bottle.load
fn ()
    try
        local vertices : (array vec3 8)
            vec3 -0.5 -0.5 -0.5 # blf
            vec3  0.5 -0.5 -0.5 # brf
            vec3 -0.5  0.5 -0.5 # tlf
            vec3  0.5  0.5 -0.5 # trf
            vec3 -0.5 -0.5  0.5 # blb
            vec3  0.5 -0.5  0.5 # brb
            vec3 -0.5  0.5  0.5 # tlb
            vec3  0.5  0.5  0.5 # trb

        local vertex-attributes : (Array VertexAttributes)
        for v in vertices
            'append vertex-attributes
                VertexAttributes
                    position = v
                    texcoords = (vec2 1)
                    color = (vec4 1)

        local indices : (Array u16) \
            2 0 1 2 1 3 \ # front
            3 1 5 3 5 7 \ # right
            7 4 6 4 7 5 \ # back
            4 0 2 4 2 6 \ # left
            2 3 6 6 3 7 \ # top
            4 5 0 0 5 1   # bottom

        storage-buffer := (StorageBuffer VertexAttributes) (countof vertices)
        index-buffer   := (IndexBuffer u16) (countof indices)
        uniform-buffer := (UniformBuffer Uniforms) 1
        'frame-write storage-buffer vertex-attributes
        'frame-write index-buffer indices

        vert := ShaderModule shaderf-vert ShaderLanguage.SPIRV ShaderStage.Vertex
        frag := ShaderModule shaderf-frag ShaderLanguage.SPIRV ShaderStage.Fragment

        pipeline :=
            RenderPipeline
                layout = (nullof PipelineLayout)
                topology = PrimitiveTopology.TriangleList
                winding = FrontFace.CCW
                vertex-stage =
                    VertexStage
                        module = vert
                        "main"
                fragment-stage =
                    FragmentStage
                        module = frag
                        "main"
                        color-targets =
                            typeinit
                                ColorTarget
                                    format = (bottle.gpu.get-preferred-surface-format)
                msaa-samples = (bottle.gpu.get-msaa-sample-count)

        gfx-context =
            GraphicsContext
                mesh-vertices = storage-buffer
                mesh-indices = index-buffer
                uniforms = uniform-buffer
                pipeline = pipeline
                bind-group = BindGroup ('get-bind-group-layout pipeline 0) uniform-buffer storage-buffer
    else ()

@@ 'on bottle.update
fn (dt)

@@ 'on bottle.render
fn ()
    ctx := 'force-unwrap gfx-context
    rp := RenderPass (bottle.gpu.get-cmd-encoder) (ColorAttachment (bottle.gpu.get-swapchain-image) (clear? = false))

    aspect-ratio := / (bottle.window.get-size)
    projection := bottle.math.perspective-projection aspect-ratio (pi / 2) 100.0 0.001
    mvp := projection * (mat4.translation (vec3))
    uniforms := (Uniforms mvp ((bottle.time.get-time) as f32))
    'frame-write ctx.uniforms uniforms

    'set-pipeline rp ctx.pipeline
    'set-index-buffer rp ctx.mesh-indices
    'set-bind-group rp 0 ctx.bind-group

    'draw-indexed rp 36:u32 1:u32
    'finish rp
    ()


fn main (argc argv)
    bottle.run;

sugar-if main-module?
    name argc argv := (script-launch-args)

    # make it appear as if it was launched as a regular executable
    argv* := alloca-array rawstring (argc + 1)
    argv* @ 0 = name as rawstring
    for i in (range argc)
        argv* @ (i + 1) = (argv @ i)
    main (argc + 1) argv*
else
    main
