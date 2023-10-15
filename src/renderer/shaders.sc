using import .common glm glsl struct

MAX-TRANSFORM-COUNT := 65536 // (sizeof mat4)

do
    fn default-vert ()
        uniform uniforms : Uniforms
            set = 0
            binding = 0

        uniform transforms :
            struct TransformsInputData plain
                data : (array mat4 MAX-TRANSFORM-COUNT)
            set = 0
            binding = 1

        struct VertexInputData plain
            attributes : (array VertexAttributes)

        buffer vertex-data : VertexInputData readonly
            set = 0
            binding = 2

        # out vtexcoords : vec2 (location = 0)
        out vcolor : vec4 (location = 1)

        idx := gl_VertexIndex
        v := vertex-data.attributes @ idx
        transform := transforms.data @ gl_InstanceIndex

        # vtexcoords = v.texcoords
        vcolor = v.color
        gl_Position = uniforms.view-projection * transform * (vec4 v.position 1.0)

    fn default-frag ()
        in vtexcoords : vec2 (location = 0)
        in vcolor : vec4 (location = 1)
        out fcolor : vec4 (location = 0)

        fcolor = vcolor

    local-scope;
