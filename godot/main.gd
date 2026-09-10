extends Node3D

var shell: Node3D
var fluid: Node3D
var die: MeshInstance3D
var angular_velocity=Vector2.ZERO
var fluid_velocity=Vector2.ZERO
var die_velocity=Vector2.ZERO
var viscosity=1.15
var dragging=false
var settle_time=0.0
var answers=["IT FAVORS YOU","THE PATH IS OPEN","PROCEED","LIKELY","THE MIST HAS NOT LIFTED","ASK WHEN THE HOUR TURNS","TWO TRUTHS CONTEND","NOT YET WRITTEN","TURN AWAY","THE DOOR STAYS SHUT","UNLIKELY","LET IT REST"]
var answer_label: Label

func _ready():
    RenderingServer.set_default_clear_color(Color("03080d"))
    var world=WorldEnvironment.new();var env=Environment.new();env.background_mode=Environment.BG_COLOR;env.background_color=Color("03080d");env.ambient_light_source=Environment.AMBIENT_SOURCE_COLOR;env.ambient_light_color=Color("48727a");env.ambient_light_energy=0.42;env.glow_enabled=true;world.environment=env;add_child(world)
    # Blender's Z-up is converted to Godot's Y-up on glTF import. Face the
    # sculpture along Godot's Z axis instead of looking down its vertical axis.
    var cam=Camera3D.new();cam.position=Vector3(0,1.15,20.0);cam.look_at_from_position(cam.position,Vector3(0,.35,0));cam.fov=36;add_child(cam)
    var key=DirectionalLight3D.new();key.rotation_degrees=Vector3(-32,-24,18);key.light_color=Color("ffe1a0");key.light_energy=2.2;add_child(key)
    var rim=OmniLight3D.new();rim.position=Vector3(-4,4,5);rim.omni_range=12;rim.light_color=Color("31d7d1");rim.light_energy=7.;add_child(rim)
    var packed=load("res://assets/oracle_models.glb") as PackedScene
    var models=packed.instantiate();add_child(models)
    shell=Node3D.new();shell.name="FingerRolledKugel";shell.position=Vector3(0,1.15,0);add_child(shell)
    var glass=models.find_child("GlassSphere",true,false) as MeshInstance3D;glass.reparent(shell,false);glass.position=Vector3.ZERO;glass.material_override=ShaderMaterial.new();glass.material_override.shader=load("res://glass.gdshader")
    die=models.find_child("OracleDodecahedron",true,false) as MeshInstance3D;die.reparent(shell,false);die.position=Vector3.ZERO
    fluid=models.find_child("NebulaRig",true,false) as Node3D;fluid.reparent(shell,false);fluid.position=Vector3.ZERO;shell.move_child(fluid,0)
    build_ui()

func build_ui():
    var layer=CanvasLayer.new();add_child(layer)
    var title=Label.new();title.text="O R A C L E   S P H E R E";title.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;title.add_theme_color_override("font_color",Color("e7c878"));title.add_theme_font_size_override("font_size",26);title.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE);title.position.y=36;layer.add_child(title)
    answer_label=Label.new();answer_label.text="ROLL THE SPHERE";answer_label.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;answer_label.vertical_alignment=VERTICAL_ALIGNMENT_CENTER;answer_label.add_theme_color_override("font_color",Color("ffe49a"));answer_label.add_theme_font_size_override("font_size",22);answer_label.set_anchors_preset(Control.PRESET_CENTER);answer_label.position=Vector2(-220,-45);answer_label.size=Vector2(440,90);layer.add_child(answer_label)
    var hint=Label.new();hint.text="TOUCH · ROLL · RELEASE";hint.horizontal_alignment=HORIZONTAL_ALIGNMENT_CENTER;hint.add_theme_color_override("font_color",Color("91a9aa"));hint.add_theme_font_size_override("font_size",14);hint.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_WIDE);hint.position.y=-86;layer.add_child(hint)

func _unhandled_input(event):
    if event is InputEventScreenTouch:
        dragging=event.pressed
        if dragging:settle_time=0.;answer_label.text="THE VEIL IS MOVING"
    elif event is InputEventScreenDrag:
        angular_velocity=Vector2(event.relative.y,-event.relative.x)*.013
        shell.rotate_x(event.relative.y*.006);shell.rotate_z(-event.relative.x*.006)

func _process(delta):
    var tau=1.28/viscosity
    angular_velocity*=exp(-delta*3.5)
    fluid_velocity=fluid_velocity.lerp(angular_velocity,1.-exp(-delta/(tau*.25)))
    die_velocity=die_velocity.lerp(fluid_velocity,1.-exp(-delta/(tau*.48)))
    fluid.rotate_x(fluid_velocity.x*delta);fluid.rotate_z(fluid_velocity.y*delta)
    die.rotate_x(die_velocity.x*delta);die.rotate_z(die_velocity.y*delta);die.rotate_y((die_velocity.length()+fluid_velocity.length())*.21*delta)
    if not dragging and angular_velocity.length()+fluid_velocity.length()+die_velocity.length()<.018:
        settle_time+=delta
        if settle_time>0.42 and answer_label.text=="THE VEIL IS MOVING":commit_face()

func commit_face():
    var dirs=[Vector3(0,.526,.851),Vector3(0,-.526,.851),Vector3(0,.526,-.851),Vector3(0,-.526,-.851),Vector3(.526,.851,0),Vector3(-.526,.851,0),Vector3(.526,-.851,0),Vector3(-.526,-.851,0),Vector3(.851,0,.526),Vector3(.851,0,-.526),Vector3(-.851,0,.526),Vector3(-.851,0,-.526)]
    var best=0;var score=-99.0
    for i in dirs.size():
        var s=(die.global_transform.basis*dirs[i]).dot(Vector3(0,0,1))
        if s>score:score=s;best=i
    answer_label.text=answers[best]
