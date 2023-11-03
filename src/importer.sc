using import Array .exceptions Map Option renderer.RendererScene String
import .assimp .assimp-physfs radl.String+
String+ := radl.String+

fn load-texture-assimp (data)
    compressed? := data.mHeight == 0
    texels := data.pcData as (mutable@ u8)

    local filename : (Option String)
    if (data.mFilename.length > 0)
        filename = String data.mFilename.data data.mFilename.length

    if (not compressed?)
        w h channels := _ (u32 data.mWidth) (u32 data.mHeight) 4
        buf := (Buffer (mutable@ u8)) texels (* w h channels) nodrop
        img-data := ImageData w h 1:u32 none TextureFormat.BGRA8UnormSrgb
        buffercopy img-data.data buf

        _
            Texture w h TextureFormat.BGRA8UnormSrgb img-data
            filename
    else
        format := 'from-rawstring String data.achFormatHint
        if ((format == "jpg") or (format == "png"))
            buf := (Buffer (mutable@ u8)) texels data.mWidth nodrop
            img-data := bottle.asset.load-image buf
            _
                Texture img-data.width img-data.height TextureFormat.RGBA8UnormSrgb img-data
                filename
        else (raise ImporterError.UnsupportedFileType)

fn load-material-assimp (data textures)

fn load-mesh-assimp (data)
    if

fn load-model-assimp (path)
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

    # TODO: texture / material deduplication across overall scene (multiple model files)
    local textures : (Array Texture)
    local texture-index-map : (Map String usize)

    for i in (range scene-data.mNumTextures)
        # TODO: in case of error, load default texture
        texture filename := load-texture-assimp (scene-data.mTextures @ i)
        try ('unwrap filename)
        then (filename)
            'set texture-index-map filename (countof textures)
            'append textures texture
        else
            'add-texture scene texture

    local scene : RendererScene
    for i in (range scene-data.mNumMaterials)
        'add-material scene (load-material-assimp (scene-data.mMaterials @ i) textures)

    for i in (range scene-data.mNumMeshes)
        mesh-data := scene-data.mMeshes @ i
        'add-mesh scene (load-mesh-assimp mesh-data)

    assimp.ReleaseImport scene-data

do
    fn load-model (path)
        local model-extensions-assimp =
            arrayof String ".glb" ".gltf"

        for ext in model-extensions-assimp
            if (String+.ends-with? path ext)
                return (load-model-assimp path)

        raise ImporterError.UnsupportedFileType

    local-scope;
