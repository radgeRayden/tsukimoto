using import glm struct

do
    struct Uniforms plain
        time : f32
        view-projection : mat4

    struct VertexAttributes plain
        position : vec3
        texcoords : vec2
        color : vec4 = (vec4 1)
        normal : vec3
    local-scope;
