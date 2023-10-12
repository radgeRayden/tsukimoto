using import Array Option radl.strfmt String struct
import .assimp .importer .renderer
import bottle

ig fs   := bottle.imgui, bottle.filesystem
String+ := import radl.String+

@@ 'on bottle.configure
fn (cfg)
    cfg.window.title = "tsukimoto scene viewer"

struct AppContext
    scene-files : (Array String)
    scene-files-display : (Array String)
    selected-scene : usize

global app-context : (Option AppContext)

fn discover-scene-files (parent file-list)
    returning void
    items := fs.get-directory-items parent
    for item in items
        full-path := parent .. "/" .. item
        if (fs.is-directory? full-path)
            this-function full-path file-list
            continue;

        file-types... := _ ".glb" ".gltf"
        loadable-file? := or (va-map ((ext) -> ((String+.common-suffix item ext) == ext)) file-types...)
        if loadable-file?
            'append file-list full-path

fn import-selected-scene ()
    ctx := 'force-unwrap app-context

    if (empty? ctx.scene-files)
        return;

    scene-data := importer.load-scene (ctx.scene-files @ ctx.selected-scene)
    try (renderer.load-scene scene-data)
    else ()

    # FIXME: manage this automatically
    assimp.ReleaseImport scene-data

@@ 'on bottle.load
fn ()
    try (renderer.init)
    else (assert false)

    local scene-files : (Array String)
    discover-scene-files S"/" scene-files
    local scene-files-display : (Array String)
    for f in scene-files
        path-components := String+.split f "/"
        'append scene-files-display (copy ('last path-components))

    app-context =
        AppContext
            scene-files = scene-files
            scene-files-display = scene-files-display

    import-selected-scene;

fn render-UI (ctx)
    ig := bottle.imgui
    WF := ig.GuiWindowFlags
    ww wh := (bottle.window.get-size)

    ig.SetNextWindowSize (ig.Vec2 210 (f32 wh)) ig.GuiCond.Always
    ig.SetNextWindowPos (ig.Vec2) ig.GuiCond.Always (ig.Vec2)
    ig.Begin "Scene Selector" null
        | WF.NoResize WF.NoScrollbar WF.NoCollapse WF.NoTitleBar
    ig.Text "Scene"
    ig.BeginListBox "##" (ig.Vec2 200 400)
    for idx path display-name in (enumerate (zip ctx.scene-files ctx.scene-files-display))
        is-selected? := idx == ctx.selected-scene
        if (ig.Selectable_Bool f"${display-name}##${idx}" is-selected? 0 (ig.Vec2))
            ctx.selected-scene = idx
            print path
            import-selected-scene;

    ig.EndListBox;
    ig.End;

@@ 'on bottle.render
fn ()
    ctx := 'force-unwrap app-context
    renderer.render-scene;
    render-UI ctx

bottle.run;
