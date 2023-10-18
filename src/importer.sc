using import .exceptions String
import .assimp .assimp-physfs radl.String+
String+ := radl.String+

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
            # NOTE: try disabling these if scene switching is too slow RT.
            IF.OptimizeMeshes
            IF.OptimizeGraph
            IF.GenBoundingBoxes

    local io = (assimp-physfs.get-IO)
    assimp.ImportFileEx path flags &io

do
    fn load-model (path)
        local model-extensions-assimp =
            arrayof String ".glb" ".gltf"

        for ext in model-extensions-assimp
            if (String+.ends-with? path ext)
                return (load-model-assimp path)

        raise ImporterError.UnsupportedFileType

    local-scope;
