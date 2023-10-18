switch operating-system
case 'linux
    shared-library "libassimp.so"
case 'windows
    shared-library "assimp.dll"
default
    error "Unsupported OS"

using import Array slice

let header =
    include
        """"#include <assimp/types.h>
            #include <assimp/aabb.h>
            #include <assimp/material.h>
            #include <assimp/matrix4x4.h>
            #include <assimp/mesh.h>
            #include <assimp/postprocess.h>
            #include <assimp/scene.h>
            #include <assimp/vector3.h>
            #include <assimp/cfileio.h>
            #include <assimp/cexport.h>
            #include <assimp/cimport.h>

vvv bind exports
do
    using header.extern filter "^ai(.+)$"
    using header.struct filter "^ai(.+)$"
    using header.enum filter "^ai(.+)$"
    using header.typedef filter "^ai(.+)$"
    using header.define filter "^(_AI_.+|AI_.+)$"

    local-scope;

for k v in exports
    if (('typeof v) != type)
        continue;

    local old-symbols : (Array Symbol)
    T := (v as type)
    if (T < CEnum)
        for k v in ('symbols T)
            original-symbol  := k as Symbol
            original-name    := original-symbol as string
            match? start end := 'match? str"^ai.+?_" original-name

            if match?
                field := (Symbol (rslice original-name end))
                'set-symbol T field v
                'append old-symbols original-symbol

        for sym in old-symbols
            sc_type_del_symbol T sym

inline make-unpack (T)
    inline (x)
        va-map
            (field) -> (getattr x (keyof field))
            elementsof T

using import glm

inline augment-matrix (T MT)
    type+ T
        __unpack := (make-unpack T)

        inline __imply (thisT otherT)
            static-if (otherT == MT)
                inline (self)
                    transpose (MT (unpack self))

inline augment-vector (T VT)
    type+ T
        __unpack := (make-unpack T)

        inline __imply (thisT otherT)
            static-if (otherT == VT)
                inline (self)
                    VT (unpack self)

augment-matrix exports.Matrix3x3 mat3
augment-matrix exports.Matrix4x4 mat4
augment-vector exports.Vector2D vec2
augment-vector exports.Vector3D vec3
augment-vector exports.Color4D vec4

exports
