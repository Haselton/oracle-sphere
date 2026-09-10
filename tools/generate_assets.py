import bpy, math, os
from mathutils import Vector

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)

OUT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'godot', 'assets'))
os.makedirs(OUT, exist_ok=True)

def mat(name, color, metallic=0.0, rough=.35):
    m=bpy.data.materials.new(name)
    m.diffuse_color=(*color,1)
    m.use_nodes=True
    bs=m.node_tree.nodes.get('Principled BSDF')
    bs.inputs['Base Color'].default_value=(*color,1)
    bs.inputs['Metallic'].default_value=metallic
    bs.inputs['Roughness'].default_value=rough
    return m

gold=mat('Etched Gold',(0.72,.43,.10),.92,.17)
stone=mat('Matte Charcoal Stone',(.025,.032,.038),.05,.72)
die_dark=mat('Obsidian Faces',(.012,.055,.062),.28,.20)

# Exact regular dodecahedron: 20 vertices and the 12 convex-hull pentagons.
p=(1+5**.5)/2; q=1/p
verts=[(x,y,z) for x in (-1,1) for y in (-1,1) for z in (-1,1)]
for a in (-q,q):
    for b in (-p,p): verts += [(0,a,b),(a,b,0),(b,0,a)]
faces=[(4,8,14,6,13),(8,0,10,2,14),(10,0,9,1,16),(2,10,16,3,12),
       (9,15,5,11,1),(0,8,4,15,9),(4,13,19,5,15),(1,11,17,3,16),
       (11,5,19,7,17),(6,14,2,12,18),(13,6,18,7,19),(18,12,3,17,7)]
mesh=bpy.data.meshes.new('RegularDodecahedronMesh');mesh.from_pydata(verts,[],faces);mesh.update()
die=bpy.data.objects.new('OracleDodecahedron',mesh);bpy.context.collection.objects.link(die);die.scale=(.92,.92,.92);die.data.materials.append(die_dark);die.data.materials.append(gold)
bev=die.modifiers.new('Precision bevel','BEVEL');bev.width=.055;bev.segments=3;bev.material=1
bpy.context.view_layer.objects.active=die;die.select_set(True);bpy.ops.object.shade_smooth();die.select_set(False)

# Outer glass globe; material is replaced with a realtime refractive shader in Godot.
bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=64, radius=3.65, location=(0,0,1.15))
globe=bpy.context.object;globe.name='GlassSphere'

# Kugel sculpture base: a massive stone body with a fitted hemispherical socket.
bpy.ops.mesh.primitive_cylinder_add(vertices=96, radius=3.85, depth=1.72, location=(0,0,-2.52))
base=bpy.context.object;base.name='KugelStoneBase';base.data.materials.append(stone)
bev=base.modifiers.new('Stone rounding','BEVEL');bev.width=.52;bev.segments=8
bpy.ops.mesh.primitive_torus_add(major_radius=2.92,minor_radius=.44,major_segments=96,minor_segments=24,location=(0,0,-1.62))
socket=bpy.context.object;socket.name='WaterBearingRing';socket.data.materials.append(stone)

# Thin luminous water ring marks the pressurized suspension gap.
water=mat('Water Film',(.03,.55,.59),.15,.08)
bpy.ops.mesh.primitive_torus_add(major_radius=3.02,minor_radius=.055,major_segments=128,minor_segments=12,location=(0,0,-1.50))
bpy.context.object.name='SuspensionWaterFilm';bpy.context.object.data.materials.append(water)

bpy.ops.export_scene.gltf(filepath=os.path.join(OUT,'oracle_models.glb'),export_format='GLB',use_selection=False,export_apply=True)
print('Exported',os.path.join(OUT,'oracle_models.glb'))
