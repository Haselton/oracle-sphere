shader_type spatial;
render_mode blend_add, depth_draw_opaque, cull_back, diffuse_burley;

uniform float motion : hint_range(0.0,3.0)=0.0;
uniform vec2 flow=vec2(0.0);
uniform vec3 teal : source_color=vec3(0.01,0.75,0.78);
uniform vec3 gold : source_color=vec3(1.0,0.52,0.12);

float hash(vec3 p){p=fract(p*.3183099+.1);p*=17.0;return fract(p.x*p.y*p.z*(p.x+p.y+p.z));}
float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
float fbm(vec3 p){float v=0.;float a=.5;for(int i=0;i<5;i++){v+=a*noise(p);p=p*2.03+vec3(1.7,2.1,.8);a*=.5;}return v;}

void fragment(){
    vec3 p=NORMAL*2.15;
    float t=TIME*(.10+motion*.13);
    float twist=atan(p.y,p.x)*2.4+length(p.xy)*3.0;
    p+=vec3(cos(twist+t+flow.x),sin(twist-t+flow.y),sin(t*.7+twist))*.42;
    float a=fbm(p*1.25+vec3(t,-t*.45,t*.2));
    float b=fbm(p*2.1-vec3(t*.4,t*.8,-t));
    float filament=smoothstep(.53,.76,a+b*.34);
    float gold_ridge=smoothstep(.62,.83,b+a*.22);
    ALBEDO=mix(teal,gold,gold_ridge)*1.25;
    EMISSION=mix(teal,gold,gold_ridge)*(filament*3.6+gold_ridge*2.0);
    ALPHA=clamp(filament*.82+gold_ridge*.38,0.,.88);
    ROUGHNESS=.18;
}
