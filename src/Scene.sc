using import Array enum struct
using import radl.ArrayMap

struct 3DNode
NodeList := ArrayMap 3DNode

enum NodeKind

struct Scene
    meshes : (Array Mesh)
    nodes : NodeList

struct 3DNode
    parent : NodeList.IndexType
    inner : NodeKind
