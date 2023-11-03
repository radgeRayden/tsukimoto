using import struct
import bottle
using bottle.types

struct VertexAttributes plain
    position : vec3
    texcoords : vec2
    color : vec4

struct Mesh
    attribute-buffer : (StorageBuffer VertexAttributes)
