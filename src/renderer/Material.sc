using import glm String struct
import ..assimp
print assimp.AI_MATKEY_TEXTURE_DIFFUSE

import bottle
using bottle.types

struct Material
    name : String = "unnamed material"
    color-diffuse : vec4
    color-ambient : vec4
    color-emissive : vec4

    color-map : Texture
    emissive-map : Texture
    AO-map : Texture

    fn from-assimp (cls mat)
        '__typecall cls
            name = name

do
    let Material
    local-scope;
