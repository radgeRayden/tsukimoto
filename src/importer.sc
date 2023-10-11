using import String
import .assimp physfs

fn ftell (file)
    handle := file.UserData as (mutable@ physfs.File)
    (physfs.tell handle) as u64

fn flen (file)
    handle := file.UserData as (mutable@ physfs.File)
    size := (physfs.fileLength handle)
    assert (size != -1)
    size as u64

fn fseek (file position origin)
    handle := file.UserData as (mutable@ physfs.File)
    let result =
        switch origin
        case assimp.Origin.SET
            physfs.seek handle position
        case assimp.Origin.CUR
            cur := (ftell file) as i64
            offset := position as i64
            physfs.seek handle (u64 (cur + offset))
        case assimp.Origin.END
            size := (flen file) as i64
            offset := position as i64
            physfs.seek handle (u64 (size + offset))
        default 0
    if (result != 0)
        assimp.Return.SUCCESS
    else
        assimp.Return.FAILURE

fn read-bytes (handle ptr count)
    total-read := physfs.readBytes handle (ptr as voidstar) count
    assert (total-read != -1)
    total-read as u64

fn fread (file ptr element-size count)
    handle := file.UserData as (mutable@ physfs.File)
    expected-bytes := count * element-size

    start-position := ftell file
    bytes-read := read-bytes handle ptr expected-bytes
    elements-read := bytes-read // element-size

    if (bytes-read < expected-bytes)
        # For larger elements, incomplete reads may occur.
        # We should set the cursor back to the end of the last element read.
        expected-advance := elements-read * element-size
        if (expected-advance < bytes-read)
            fseek file (start-position + expected-advance) assimp.Origin.SET

    elements-read as u64

fn write-bytes (handle ptr count)
    total-written := physfs.writeBytes handle (ptr as voidstar) count
    assert (total-written != -1)
    total-written as u64

fn fwrite (file ptr element-size count)
    handle := file.UserData as (mutable@ physfs.File)
    bytes-written := write-bytes handle ptr (count * element-size)
    elements-written := bytes-written // element-size

fn fflush (file)
    handle := file.UserData as (mutable@ physfs.File)
    physfs.flush handle
    ()

fn fopen (io path mode)
    let handle =
        switch (mode @ 0)
        case char"r"
            physfs.openRead path
        case char"w"
            physfs.openWrite path
        case char"a"
            physfs.openAppend path
        default (assert false "unhandled file open mode")

    if (handle == null)
        return (nullof (mutable@ assimp.File))

    assimp-file := malloc assimp.File
    store
        assimp.File
            ReadProc = fread
            WriteProc = fwrite
            TellProc = ftell
            FileSizeProc = flen
            SeekProc = fseek
            FlushProc = fflush
            UserData = handle as assimp.UserData
        assimp-file
    assimp-file

fn fclose (io file)
    handle := file.UserData as (mutable@ physfs.File)
    physfs.close handle
    free file

do
    fn load-scene (path)
        local io : assimp.FileIO
            OpenProc = fopen
            CloseProc = fclose

        assimp.ImportFileEx path 0 &io
    local-scope;
