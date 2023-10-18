using import String
import .assimp .assimp-physfs

do
    fn load-scene (path)
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
    local-scope;
