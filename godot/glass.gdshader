shader_type spatial;
render_mode blend_mix,depth_draw_always,cull_back;
void fragment(){
    float rim=pow(1.0-max(dot(NORMAL,VIEW),0.0),2.4);
    ALBEDO=vec3(.005,.035,.052)+vec3(.02,.16,.19)*rim;
    METALLIC=.12;ROUGHNESS=.08;ALPHA=.16+rim*.34;
    EMISSION=vec3(.02,.28,.30)*rim*.45;
}
