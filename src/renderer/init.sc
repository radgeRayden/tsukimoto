using import Array Buffer glm Map Option String struct
import ..assimp bottle .shaders
using import .common

using bottle.types
using bottle.enums

# TODO: remove this and query limits instead.
# FIXME: add draw call splitting
MAX-TRANSFORM-COUNT := 65536 // (sizeof mat4)

struct Material
    color-map : (Option TextureView)

struct Mesh
    vertices : (StorageBuffer VertexAttributes)
    indices : (IndexBuffer u32)
    material-index : u32

struct RenderObject plain
    mesh-index : usize
    transform : mat4

struct ShaderBindings
    object-data : BindGroup
    material-data : BindGroup
    global-uniforms : BindGroup

struct InstancedDraw
    bind-group : BindGroup
    pipeline : RenderPipeline
    instance-count : u32

struct RendererState
    pipeline : RenderPipeline
    textured-pipeline : RenderPipeline
    sampler : Sampler
    depth-stencil-view : TextureView
    textures : (Array Texture)
    meshes : (Array Mesh)
    materials : (Array Material)
    uniforms : (UniformBuffer Uniforms)
    transforms : (UniformBuffer mat4)
    mesh-index-map : (Map u32 u32)
    texture-index-map : (Map String u32)

    vertex-data : (Array VertexAttributes)
    index-data : (Array u32)
    transform-data : (Array mat4)
    render-objects : (Array RenderObject)
    render-list : (Array InstancedDraw)
    AABB-min : vec3
    AABB-max : vec3

    outdated-scene-data? : bool

global renderer-state : (Option RendererState)

inline gen-pipeline (vert frag)
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

fn init ()
    vert := ShaderModule shaders.default-vert ShaderLanguage.SPIRV ShaderStage.Vertex
    frag := ShaderModule shaders.default-frag ShaderLanguage.SPIRV ShaderStage.Fragment
    pipeline := gen-pipeline vert frag

    vert := ShaderModule shaders.textured-vert ShaderLanguage.SPIRV ShaderStage.Vertex
    frag := ShaderModule shaders.textured-frag ShaderLanguage.SPIRV ShaderStage.Fragment
    textured-pipeline := gen-pipeline vert frag

    w h := va-map u32 (bottle.window.get-size)
    depth-stencil-texture :=
        Texture w h TextureFormat.Depth32FloatStencil8 (render-target? = true)

    renderer-state =
        RendererState
            pipeline = pipeline
            textured-pipeline = textured-pipeline
            sampler = (Sampler)
            uniforms = typeinit 1
            transforms = typeinit MAX-TRANSFORM-COUNT
            depth-stencil-view = (TextureView depth-stencil-texture)

fn assimp-load-texture (ctx data)
    compressed? := data.mHeight == 0
    texels := data.pcData as (mutable@ u8)
    if (data.mFilename.length > 0)
        'set ctx.texture-index-map (String data.mFilename.data data.mFilename.length) (u32 (countof ctx.textures))
    if (not compressed?)
        w h channels := _ (u32 data.mWidth) (u32 data.mHeight) 4
        buf := (Buffer (mutable@ u8)) texels (* w h channels) nodrop
        img-data := ImageData w h 1:u32 none TextureFormat.BGRA8UnormSrgb
        buffercopy img-data.data buf

        Texture w h TextureFormat.BGRA8UnormSrgb img-data
    else
        format := 'from-rawstring String data.achFormatHint
        if ((format == "jpg") or (format == "png"))
            buf := (Buffer (mutable@ u8)) texels data.mWidth nodrop
            img-data := bottle.asset.load-image buf
            Texture img-data.width img-data.height TextureFormat.RGBA8UnormSrgb img-data
        else (assert false "format not supported")

fn load-scene (scene-data)
    ctx := 'force-unwrap renderer-state
    'clear ctx.meshes
    'clear ctx.transform-data
    'clear ctx.render-objects
    'clear ctx.render-list
    'clear ctx.mesh-index-map
    'clear ctx.texture-index-map
    'clear ctx.vertex-data
    'clear ctx.materials
    'clear ctx.textures

    ctx.AABB-min = (vec3)
    ctx.AABB-max = (vec3)

    for i in (range scene-data.mNumTextures)
        'append ctx.textures (assimp-load-texture ctx (scene-data.mTextures @ i))

    for i in (range scene-data.mNumMaterials)
        material := scene-data.mMaterials @ i

        local texture-stacks = arrayof assimp.TextureType 'BASE_COLOR 'EMISSIVE

        vvv bind texture-found?
        fold (any-textures? = false) for texture-stack in texture-stacks
            texture-count := assimp.GetMaterialTextureCount material texture-stack
            if (texture-count > 0)
                local path : assimp.String
                local flags : u32
                # TODO: failure handling
                assimp.GetMaterialTexture material texture-stack 0 &path null null null null null &flags
                assert path.length

                let texture-index =
                    if ((path.data @ 0) == "*")
                        sscanf := . (import C.bindings) extern sscanf
                        local idx : u32
                        assert (sscanf path.data "*%u" &idx)
                        copy idx
                    else
                        'getdefault ctx.texture-index-map (String path.data path.length) 0:u32

                'append ctx.materials
                    Material
                        color-map = TextureView (ctx.textures @ texture-index)
                break true
            false

        if (not texture-found?)
            'append ctx.materials (Material)

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
                material-index = mesh-data.mMaterialIndex
        'set ctx.mesh-index-map (u32 i) (u32 (countof ctx.meshes))

        label copy-vertices
            'resize ctx.vertex-data vertex-count
            for idx v in (enumerate ctx.vertex-data)
                v.position = mesh-data.mVertices @ idx

            # NOTE: obviously these are incomplete, but should be enough for a first version.
            if ((mesh-data.mColors @ 0) != null)
                for idx v in (enumerate ctx.vertex-data)
                    v.color = (mesh-data.mColors @ 0) @ idx
                    v.color.w = 1

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
            mesh := ctx.meshes @ obj.mesh-index
            material := ctx.materials @ mesh.material-index
            let cmd =
                try ('unwrap material.color-map)
                then (color-map)
                    InstancedDraw
                        bind-group =
                            BindGroup ('get-bind-group-layout ctx.textured-pipeline 0)
                                ctx.uniforms
                                ctx.transforms
                                mesh.vertices
                                ctx.sampler
                                color-map
                        pipeline = copy ctx.textured-pipeline
                else
                    InstancedDraw
                        bind-group =
                            BindGroup ('get-bind-group-layout ctx.pipeline 0)
                                ctx.uniforms
                                ctx.transforms
                                mesh.vertices
                        pipeline = copy ctx.pipeline

            'append ctx.render-list cmd
        cmd := 'last ctx.render-list
        cmd.instance-count += 1

        # mesh := scene-data.mMeshes @ obj.mesh-index
        # AABB-min := (vec4 (imply mesh.mAABB.mMin vec3) 1) * obj.transform
        # AABB-max := (vec4 (imply mesh.mAABB.mMax vec3) 1) * obj.transform
        # ctx.AABB-min = min ctx.AABB-min AABB-min.xyz
        # ctx.AABB-max = max ctx.AABB-max AABB-max.xyz
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
    fold (first-instance = 0:u32) for idx cmd in (enumerate ctx.render-list)
        mesh := ctx.meshes @ idx
        'set-pipeline rp cmd.pipeline
        'set-index-buffer rp mesh.indices
        'set-bind-group rp 0 cmd.bind-group
        'draw-indexed rp ((usize mesh.indices.Capacity) as u32) cmd.instance-count (first-instance = first-instance)
        first-instance + cmd.instance-count

    'finish rp

do
    let init load-scene render-scene
    local-scope;
