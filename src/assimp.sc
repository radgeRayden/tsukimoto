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
            #include <assimp/cfileio.h>
            #include <assimp/cexport.h>
            #include <assimp/cimport.h>

vvv bind exports
do
    using header.extern filter "^ai(.+)$"
    using header.struct filter "^ai(.+)$"
    using header.enum filter "^ai(.+)$"
    using header.typedef filter "^ai(.+)$"

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

exports
