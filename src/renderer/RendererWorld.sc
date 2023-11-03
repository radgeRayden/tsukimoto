using import Array struct

import bottle
using bottle.types
using bottle.enums

using import .Mesh .Material

struct RendererWorld
    meshes : (Array Mesh)
    materials : (Array Material)
    transforms : (Array mat4)

    fn load-model-file (filename)
