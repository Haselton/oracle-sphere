import bpy, math, os, random
from mathutils import Vector

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
ROOT=os.path.abspath(os.path.join(os.path.dirname(__file__),'..'))
OUT=os.path.join(ROOT,'godot','assets');PREVIEW=os.path.join(ROOT,'build','oracle-sphere-preview.png')
os.makedirs(OUT,exist_ok=True);os.makedirs(os.path.dirname(PREVIEW),exist_ok=True)

def principled(name,color,metallic=0.0,rough=.35,emission=None,strength=0.0):
    m=bpy.data.materials.new(name);m.use_nodes=True;bs=m.node_tree.nodes.get('Principled BSDF')
    bs.inputs['Base Color'].default_value=(*color,1);bs.inputs['Metallic'].default_value=metallic;bs.inputs['Roughness'].default_value=rough
    if emission:
        inp=bs.inputs.get('Emission Color') or bs.inputs.get('Emission')
        if inp:inp.default_value=(*emission,1)
        inp=bs.inputs.get('Emission Strength')
        if inp:inp.default_value=strength
    return m

def emission(name,color,strength):
    m=bpy.data.materials.new(name);m.use_nodes=True;nt=m.node_tree;nt.nodes.clear()
    out=nt.nodes.new('ShaderNodeOutputMaterial');em=nt.nodes.new('ShaderNodeEmission');em.inputs['Color'].default_value=(*color,1);em.inputs['Strength'].default_value=strength;nt.links.new(em.outputs[0],out.inputs[0]);return m

gold=principled('Etched Gold',(.60,.30,.055),.94,.16,(.9,.42,.08),.55)
stone=principled('Matte Charcoal Stone',(.012,.016,.022),.04,.78)
die_dark=principled('Obsidian Faces',(.006,.025,.034),.42,.17)
teal_glow=emission('Nebula Teal',(.005,.72,.74),7.0);gold_glow=emission('Nebula Gold',(1.0,.34,.045),5.0)

# Exact regular dodecahedron: 20 vertices, 30 edges, 12 pentagons.
p=(1+5**.5)/2;q=1/p
verts=[(x,y,z) for x in (-1,1) for y in (-1,1) for z in (-1,1)]
for a in (-q,q):
    for b in (-p,p):verts += [(0,a,b),(a,b,0),(b,0,a)]
faces=[(4,8,14,6,13),(8,0,10,2,14),(10,0,9,1,16),(2,10,16,3,12),(9,15,5,11,1),(0,8,4,15,9),(4,13,19,5,15),(1,11,17,3,16),(11,5,19,7,17),(6,14,2,12,18),(13,6,18,7,19),(18,12,3,17,7)]
mesh=bpy.data.meshes.new('RegularDodecahedronMesh');mesh.from_pydata(verts,[],faces);mesh.update()
die=bpy.data.objects.new('OracleDodecahedron',mesh);bpy.context.collection.objects.link(die);die.scale=(.72,)*3;die.data.materials.append(die_dark);die.data.materials.append(gold)
bev=die.modifiers.new('Gold filigree edges','BEVEL');bev.width=.035;bev.segments=3;bev.material=1

# Sparse luminous filaments create suspended depth without an opaque fluid shell.
rig=bpy.data.objects.new('NebulaRig',None);bpy.context.collection.objects.link(rig)
for band in range(11):
    curve=bpy.data.curves.new('Suspended filament','CURVE');curve.dimensions='3D';curve.resolution_u=2;curve.bevel_depth=.018 if band%3 else .028;curve.bevel_resolution=2
    spline=curve.splines.new('NURBS');steps=84;spline.points.add(steps-1);phase=band*1.37
    for i in range(steps):
        t=(i/(steps-1))*math.tau*1.45+phase;radius=1.52+.48*math.sin(t*.47+phase)+.10*math.sin(t*3.)
        spline.points[i].co=(radius*math.cos(t),.35*math.sin(t*1.8+phase)+(.10*band-.5),.74*radius*math.sin(t)+.16*math.sin(t*2.7+phase),1)
    spline.use_endpoint_u=True;spline.order_u=4
    obj=bpy.data.objects.new('NebulaFilament_%02d'%band,curve);bpy.context.collection.objects.link(obj);obj.parent=rig;obj.data.materials.append(teal_glow if band%3 else gold_glow)

random.seed(23);dust_parts=[]
for _ in range(90):
    ang=random.random()*math.tau;r=random.uniform(.8,2.55)
    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=random.uniform(.012,.035),location=(r*math.cos(ang),random.uniform(-.75,.75),.72*r*math.sin(ang)));dust_parts.append(bpy.context.object)
bpy.ops.object.select_all(action='DESELECT')
for o in dust_parts:o.select_set(True)
bpy.context.view_layer.objects.active=dust_parts[0];bpy.ops.object.join();dust=bpy.context.object;dust.name='NebulaGoldDust';dust.parent=rig;dust.data.materials.append(gold_glow)

glass=principled('Obsidian Sapphire Glass',(.004,.018,.027),.08,.06);bs=glass.node_tree.nodes.get('Principled BSDF')
trans=bs.inputs.get('Transmission Weight') or bs.inputs.get('Transmission')
if trans:trans.default_value=.88
if bs.inputs.get('IOR'):bs.inputs['IOR'].default_value=1.47
bpy.ops.mesh.primitive_uv_sphere_add(segments=96,ring_count=64,radius=3.45,location=(0,0,1.05));globe=bpy.context.object;globe.name='GlassSphere';globe.data.materials.append(glass);bpy.ops.object.shade_smooth()

bpy.ops.mesh.primitive_cylinder_add(vertices=128,radius=3.62,depth=1.05,location=(0,0,-2.30));base=bpy.context.object;base.name='KugelStoneBase';base.data.materials.append(stone)
bev=base.modifiers.new('Stone rounding','BEVEL');bev.width=.38;bev.segments=7
bpy.ops.mesh.primitive_torus_add(major_radius=2.78,minor_radius=.32,major_segments=128,minor_segments=28,location=(0,0,-1.82));socket=bpy.context.object;socket.name='WaterBearingRing';socket.data.materials.append(stone)
bpy.ops.mesh.primitive_torus_add(major_radius=2.82,minor_radius=.045,major_segments=128,minor_segments=10,location=(0,0,-1.72));film=bpy.context.object;film.name='SuspensionWaterFilm';film.data.materials.append(teal_glow)

# Deterministic portrait product checkpoint.
preview_only=[]
bpy.ops.mesh.primitive_plane_add(size=40,location=(0,0,-2.84));floor=bpy.context.object;floor.data.materials.append(stone);preview_only.append(floor)
bpy.ops.object.camera_add(location=(0,-20.5,1.45));cam=bpy.context.object;preview_only.append(cam);cam.rotation_euler=(Vector((0,0,.45))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.lens=53;bpy.context.scene.camera=cam
for loc,color,energy,size in [((-4,-5,7),(1.,.68,.30),1150,4.),((4,-1,4),(.02,.72,.78),850,3.),((0,3,7),(.35,.55,1.),650,3.)]:
    bpy.ops.object.light_add(type='AREA',location=loc);lamp=bpy.context.object;preview_only.append(lamp);lamp.data.energy=energy;lamp.data.color=color;lamp.data.shape='DISK';lamp.data.size=size;lamp.rotation_euler=(Vector((0,0,.4))-lamp.location).to_track_quat('-Z','Y').to_euler()
scene=bpy.context.scene;scene.render.engine='BLENDER_EEVEE';scene.render.resolution_x=720;scene.render.resolution_y=1280;scene.render.resolution_percentage=50;scene.render.image_settings.file_format='PNG';scene.render.filepath=PREVIEW;scene.world.color=(.001,.003,.006);scene.view_settings.look='AgX - Medium High Contrast'
scene.use_nodes=True;nodes=scene.node_tree.nodes;links=scene.node_tree.links
for n in list(nodes):nodes.remove(n)
rl=nodes.new('CompositorNodeRLayers');glare=nodes.new('CompositorNodeGlare');glare.glare_type='FOG_GLOW';glare.quality='HIGH';glare.threshold=.8;glare.size=7;comp=nodes.new('CompositorNodeComposite');links.new(rl.outputs['Image'],glare.inputs['Image']);links.new(glare.outputs['Image'],comp.inputs['Image'])
bpy.ops.render.render(write_still=True)
for obj in preview_only:bpy.data.objects.remove(obj,do_unlink=True)
bpy.ops.export_scene.gltf(filepath=os.path.join(OUT,'oracle_models.glb'),export_format='GLB',use_selection=False,export_apply=True)
print('Rendered',PREVIEW);print('Exported',os.path.join(OUT,'oracle_models.glb'))
