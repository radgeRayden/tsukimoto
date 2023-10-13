using import Array glm Map Option struct
import ..assimp bottle .shaders
using import .common

using bottle.types
using bottle.enums

# TODO: remove this and query limits instead.
# FIXME: add draw call splitting
MAX-TRANSFORM-COUNT := 65536 // (sizeof mat4)

struct Mesh
    vertices : (StorageBuffer VertexAttributes)
    indices : (IndexBuffer u32)

struct RenderObject plain
    mesh-index : usize
    transform : mat4

struct InstancedDraw
    bind-group : BindGroup
    instance-count : usize

struct RendererState
    pipeline : RenderPipeline
    depth-stencil-view : TextureView
    meshes : (Array Mesh)
    uniforms : (UniformBuffer Uniforms)
    transforms : (UniformBuffer mat4)
    mesh-index-map : (Map u32 u32)

    vertex-data : (Array VertexAttributes)
    index-data : (Array u32)
    transform-data : (Array mat4)
    render-objects : (Array RenderObject)
    render-list : (Array InstancedDraw)

    outdated-scene-data? : bool

global renderer-state : (Option RendererState)

fn init ()
    vert := ShaderModule shaders.default-vert ShaderLanguage.SPIRV ShaderStage.Vertex
    frag := ShaderModule shaders.default-frag ShaderLanguage.SPIRV ShaderStage.Fragment

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

    renderer-state =
        RendererState
            pipeline = pipeline
            uniforms = typeinit 1
            transforms = typeinit MAX-TRANSFORM-COUNT
            depth-stencil-view = (TextureView depth-stencil-texture)

fn load-scene (scene-data)
    ctx := 'force-unwrap renderer-state
    'clear ctx.meshes
    'clear ctx.transform-data
    'clear ctx.render-objects
    'clear ctx.render-list
    'clear ctx.mesh-index-map

    for i in (range scene-data.mNumMeshes)
        mesh-data := scene-data.mMeshes @ i
        if (not (mesh-data.mPrimitiveTypes & assimp.PrimitiveType.TRIANGLE))
            continue;

        vertex-count := mesh-data.mNumVertices
        index-count := mesh-data.mNumFaces * 3

        mesh :=
            Mesh
                vertices = typeinit vertex-count
                indices = typeinit index-count
        'set ctx.mesh-index-map (u32 i) (u32 (countof ctx.meshes))

        label copy-vertices
            'resize ctx.vertex-data vertex-count
            for idx v in (enumerate ctx.vertex-data)
                v.position = mesh-data.mVertices @ idx

            # NOTE: obviously these are incomplete, but should be enough for a first version.
            if ((mesh-data.mColors @ 0) != null)
                for idx v in (enumerate ctx.vertex-data)
                    v.color = (mesh-data.mColors @ 0) @ idx

            if ((mesh-data.mTextureCoords @ 0) != null)
                for idx v in (enumerate ctx.vertex-data)
                    tc := (mesh-data.mTextureCoords @ 0) @ idx
                    v.texcoords = (vec2 tc.x tc.y)

            if (mesh-data.mNormals != null)
                for idx v in (enumerate ctx.vertex-data)
                    v.normal = mesh-data.mNormals @ idx

            'frame-write mesh.vertices ctx.vertex-data

        label copy-indices
            'resize ctx.index-data index-count
            for i in (range mesh-data.mNumFaces)
                idx := i * 3
                ctx.index-data @ (idx + 0) = ((mesh-data.mFaces @ i) . mIndices) @ 0
                ctx.index-data @ (idx + 1) = ((mesh-data.mFaces @ i) . mIndices) @ 1
                ctx.index-data @ (idx + 2) = ((mesh-data.mFaces @ i) . mIndices) @ 2
            'frame-write mesh.indices ctx.index-data

        'append ctx.meshes mesh

    fn collect-draw-list (ctx node parent-transform)
        returning void
        for i in (range node.mNumMeshes)
            mesh-idx := node.mMeshes @ i
            let actual-idx =
                try ('get ctx.mesh-index-map mesh-idx)
                else (continue)

            'append ctx.render-objects
                RenderObject
                    mesh-index = actual-idx
                    transform = parent-transform * node.mTransformation

        for i in (range node.mNumChildren)
            child := node.mChildren @ i
            this-function ctx child (parent-transform * node.mTransformation)

    collect-draw-list ctx scene-data.mRootNode (mat4)

    'sort ctx.render-objects
        (x) -> x.mesh-index

    'resize ctx.transform-data MAX-TRANSFORM-COUNT

    ctx.outdated-scene-data? = true
    ()

fn update-render-data (ctx)
    # count instances and generate render commands
    for idx obj in (enumerate ctx.render-objects)
        ctx.transform-data @ idx = obj.transform
        if ((countof ctx.render-list) == obj.mesh-index)
            'append ctx.render-list
                InstancedDraw
                    bind-group =
                        BindGroup ('get-bind-group-layout ctx.pipeline 0)
                            ctx.uniforms
                            ctx.transforms
                            (ctx.meshes @ obj.mesh-index) . vertices
        cmd := 'last ctx.render-list
        cmd.instance-count += 1

    'frame-write ctx.transforms ctx.transform-data

fn render-scene ()
    ctx := 'force-unwrap renderer-state

    if ctx.outdated-scene-data?
        update-render-data ctx
        ctx.outdated-scene-data? = false

    w h := va-map f32 (bottle.window.get-size)
    projection := bottle.math.perspective-projection w h (pi / 2) 1.0
    camera := mat4.translation (vec3 0 0 -4)

    uniforms :=
        Uniforms
            time = f32 (bottle.time.get-time)
            view-projection = projection * camera
    'frame-write ctx.uniforms uniforms

    rp := RenderPass (bottle.gpu.get-cmd-encoder) (ColorAttachment (bottle.gpu.get-swapchain-image) (clear? = false)) ctx.depth-stencil-view
    'set-pipeline rp ctx.pipeline
    fold (first-instance = 0:u32) for idx cmd in (enumerate ctx.render-list)
        mesh := ctx.meshes @ idx
        'set-index-buffer rp mesh.indices
        'set-bind-group rp 0 cmd.bind-group
        'draw-indexed rp ((usize mesh.indices.Capacity) as u32) (u32 cmd.instance-count) (first-instance = first-instance)
        (first-instance + cmd.instance-count) as u32

    'finish rp

do
    let init load-scene render-scene
    local-scope;
