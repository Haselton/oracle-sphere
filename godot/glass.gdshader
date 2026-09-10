shader_type spatial;
render_mode blend_mix,depth_draw_never,cull_disabled;
void fragment(){
    float rim=pow(1.0-abs(dot(NORMAL,VIEW)),3.2);
    float glint=pow(max(dot(reflect(-VIEW,NORMAL),normalize(vec3(-.35,.55,.75))),0.0),48.0);
    ALBEDO=vec3(.002,.012,.022)+vec3(.01,.09,.12)*rim;
    METALLIC=.08;ROUGHNESS=.06;ALPHA=.035+rim*.20+glint*.18;
    EMISSION=vec3(.01,.16,.18)*rim*.22+vec3(1.0,.72,.32)*glint*.32;
}
