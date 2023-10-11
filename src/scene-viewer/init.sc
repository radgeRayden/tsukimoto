using import Array glm Option String struct

import ..assimp bottle
using bottle.types
using bottle.enums
ig := bottle.imgui

type+ mat4
    inline... translation (v : vec3)
        mat4
            vec4 1   0   0   0
            vec4 0   1   0   0
            vec4 0   0   1   0
            vec4 v.x v.y v.z 1

    inline... rotation (v : vec3)
        x y z := unpack v
        cx cy cz := va-map cos x y z
        sx sy sz := va-map sin x y z
        m00 := cx * cy
        m01 := cx * sy * sz - sx * cz
        m02 := cx * sy * cz - sx * sz
        m10 := sx * cy
        m11 := sx * sy * sz + cx * cz
        m12 := sx * cy * cz - cx * sz
        m20 := -sy
        m21 := cx * sz
        m22 := cy * cz

        mat4
            vec4 m00 m01 m02 0.0
            vec4 m10 m11 m12 0.0
            vec4 m20 m21 m22 0.0
            vec4 0.0 0.0 0.0 1.0

@@ 'on bottle.configure
fn (cfg)
    cfg.window.title = "tsukimoto"

struct VertexAttributes plain
    position : vec3
    texcoords : vec2
    color : vec4

struct Uniforms plain
    mvp : mat4

struct GraphicsContext
    mesh-vertices : (StorageBuffer VertexAttributes)
    mesh-indices : (IndexBuffer u16)
    uniforms : (UniformBuffer Uniforms)
    pipeline : RenderPipeline
    bind-group : BindGroup
    depth-stencil-view : TextureView

    scene-vertices : (StorageBuffer VertexAttributes)
    scene-indices : (IndexBuffer u32)
    scene-transform : mat4

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
    ndc-pos := uniforms.mvp * (vec4 v.position 1.0)
    vcolor = (vec4 ((vec3 ndc-pos.z) / 10) 1.0)
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
    IO := (ig.GetIO)
    IO.FontGlobalScale = 2.0
    style := (ig.GetStyle)
    ig.StyleColorsLight style

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
                    color = (vec4 (v + (vec3 0.5)) 1)

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
                true

        w h := va-map u32 (bottle.window.get-size)
        depth-stencil-texture :=
            Texture w h TextureFormat.Depth32FloatStencil8 (render-target? = true)

        import ..importer
        scene := importer.load-scene "/assets/frank.glb"
        mesh := scene.mMeshes @ 0
        mesh-vertices := (StorageBuffer VertexAttributes) mesh.mNumVertices
        mesh-indices := (IndexBuffer u32) (mesh.mNumFaces * 3)

        local vertices : (Array VertexAttributes)
        for i in (range mesh.mNumVertices)
            p := mesh.mVertices @ i
            'append vertices
                VertexAttributes
                    position = vec3 p.x p.y p.z
                    color = (vec4 (vec3 p.x p.y p.z) 1)
        'frame-write mesh-vertices vertices

        local indices : (Array u32)
        for i in (range mesh.mNumFaces)
            f := mesh.mFaces @ i
            va-map
                inline (i)
                    'append indices (f.mIndices @ i)
                va-range 3
        'frame-write mesh-indices indices
        inline mat4-from-transform (t)
            transpose
                mat4
                    va-map
                        inline (fT)
                            k := keyof fT
                            getattr t k
                        elementsof assimp.Matrix4x4

        fn find-mesh-node (node)
            returning (mutable@ assimp.Node)
            if (node.mNumMeshes > 0)
                node
            else
                for i in (range node.mNumChildren)
                    result := this-function (node.mChildren @ i)
                    if (result != null)
                        return result
                null

        mesh-node := find-mesh-node scene.mRootNode
        print (String mesh-node.mName.data mesh-node.mName.length)
        let scene-transform =
            loop (transform node = (mat4-from-transform mesh-node.mTransformation) mesh-node)
                if (node.mParent == null)
                    break transform
                print (String node.mParent.mName.data node.mParent.mName.length)
                _ (* (mat4-from-transform node.mParent.mTransformation) transform) (deref node.mParent)

        gfx-context =
            GraphicsContext
                mesh-vertices = storage-buffer
                mesh-indices = index-buffer
                uniforms = uniform-buffer
                pipeline = pipeline
                bind-group = BindGroup ('get-bind-group-layout pipeline 0) uniform-buffer mesh-vertices
                depth-stencil-view = (TextureView depth-stencil-texture)

                scene-vertices = mesh-vertices
                scene-indices = mesh-indices

                scene-transform = scene-transform


    else ()

@@ 'on bottle.update
fn (dt)

struct GuiState plain
    scene-selector-open? : bool = true

global gui-state : GuiState

fn render-UI ()
    if gui-state.scene-selector-open?
        ig.Begin "Scene Selector" &gui-state.scene-selector-open? 0
        ig.End;

@@ 'on bottle.render
fn ()
    ctx := 'force-unwrap gfx-context
    rp := RenderPass (bottle.gpu.get-cmd-encoder) (ColorAttachment (bottle.gpu.get-swapchain-image) (clear? = false)) ctx.depth-stencil-view

    w h := va-map f32 (bottle.window.get-size)
    time := (bottle.time.get-time)

    projection := bottle.math.perspective-projection w h (pi / 2) 1.0
    camera := mat4.translation (vec3 0 0 -4)
    model :=
        * (mat4.rotation (vec3 0 time 0)) ctx.scene-transform

    mvp :=  projection * camera * model
    uniforms := (Uniforms mvp)
    'frame-write ctx.uniforms uniforms

    'set-pipeline rp ctx.pipeline
    'set-index-buffer rp ctx.scene-indices
    'set-bind-group rp 0 ctx.bind-group

    'draw-indexed rp ((usize ctx.scene-indices.Capacity) as u32) 1:u32
    'finish rp

    render-UI;
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
