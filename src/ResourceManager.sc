using import Array Buffer .exceptions Map .renderer.RendererScene String struct
import .assimp .assimp-physfs bottle radl.String+

using bottle.types
using bottle.enums
String+ := radl.String+

struct SampledTexture
    texture : Texture
    sampler : Sampler

fn load-scene-assimp (ctx path)
    # TODO: add a non-realtime mode that does more expensive optimizations, including meshoptimizer support
    IF := assimp.PostProcessSteps
    let flags =
        | IF.CalcTangentSpace
            IF.JoinIdenticalVertices
            IF.MakeLeftHanded
            IF.FlipUVs
            IF.Triangulate
            IF.SplitLargeMeshes
            IF.SortByPType
            IF.RemoveRedundantMaterials
            IF.OptimizeMeshes
            IF.OptimizeGraph
            IF.GenBoundingBoxes

    local io = (assimp-physfs.get-IO)
    scene-data := assimp.ImportFileEx path flags &io
    defer (assimp.ReleaseImport scene-data)

    if (scene-data == null)
        err := (assimp.GetErrorString)
        if (err != null)
            print ('from-rawstring String err)
        raise ImporterError.MalformedFile

    local embedded-textures : (Array Texture)
    local embedded-texture-filenames : (Map String usize)
    for i in (range scene-data.mNumTextures)
        embed-tex := scene-data.mTextures @ i
        compressed? := embed-tex.mHeight == 0
        texels := embed-tex.pcData as (mutable@ u8)

        if (embed-tex.mFilename.length > 0)
            filename := String embed-tex.mFilename.data embed-tex.mFilename.length
            'set embedded-texture-filenames filename (countof embedded-textures)

        if (not compressed?)
            w h channels := _ (u32 embed-tex.mWidth) (u32 embed-tex.mHeight) 4
            buf := (Buffer (mutable@ u8)) texels (* w h channels) nodrop
            img-data := ImageData w h 1:u32 none TextureFormat.BGRA8UnormSrgb
            buffercopy img-data.data buf

            'append embedded-textures
                Texture img-data
        else
            format := 'from-rawstring String embed-tex.achFormatHint
            'append embedded-textures
                if ((format == "jpg") or (format == "png"))
                    buf := (Buffer (mutable@ u8)) texels embed-tex.mWidth nodrop
                    bottle.asset.load-texture buf
                else (raise ImporterError.UnsupportedFileType)

    for i in (range scene-data.mNumMaterials)
        mat-data := scene-data.mMaterials @ i

        struct MaterialTextureProperties plain
            path : assimp.String
            mapping : assimp.TextureMapping
            uv-index : u32
            blend-factor : f32
            blend-op : assimp.TextureOp
            map-mode : (array assimp.TextureMapMode 2)
            flags : u32

        count := assimp.GetMaterialTextureCount mat-data 'BASE_COLOR
        if (count > 0)
            local props : MaterialTextureProperties
            let props... = ('explode (storagecast props))

            assimp.GetMaterialTexture material stack 0 (va-map reftoptr props...)
            path := props.path
            assert path.length

            let texture =
                if ((path.data @ 0) == "*")
                    sscanf := . (import C.bindings) extern sscanf
                    local idx : u32
                    assert (sscanf path.data "*%u" &idx)
                    copy (embedded-textures @ idx)
                else
                    path := String path.data path.length
                    try ('get embedded-texture-filenames path)
                    then (idx)
                        copy (embedded-textures @ idx)
                    else
                        'acquire-texture ctx path

            import wgpu
            WM := wgpu.AddressMode
            inline translate-wrap-mode (mode)
                switch mode
                case 'Wrap
                    WM.Repeat
                case 'Clamp
                    WM.ClampToEdge
                case 'Mirror
                    WM.MirrorRepeat
                default
                    WM.ClampToEdge

            let u v w =
                translate-wrap-mode (props.wrap-mode @ 0)
                translate-wrap-mode (props.wrap-mode @ 1)
                WM.ClampToEdge

            material :=
                Material
                    color-map =
                        SampledTexture
                            texture = texture
                            sampler = ('acquire-sampler ctx u v w 'Linear 'Linear 'Linear)

        # TODO: cache materials? Just copy them around and let reference counting take the wheel?

struct ResourceManager
    textures : (Map String Texture)

    fn... acquire-texture (self, path : String)
        try ('get self.textures path)
        then (texture)
            copy texture
        else
            texture := bottle.asset.load-texture path
            'set self.textures path (copy texture)
            texture

    fn... acquire-scene (self, path : String)
        match-extension? := (ext) -> (String+.ends-with? path ext)
        if (or (va-map match-extension? ".glb" ".gltf"))
            return (load-scene-assimp self path)

        raise ImporterError.UnsupportedFileType

    fn unload-all (self)
        self = ((typeof self))

do
    let ResourceManager
    local-scope;
