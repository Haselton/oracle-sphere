extends SceneTree

func _initialize():
    var settings = EditorInterface.get_editor_settings()
    settings.set_setting("export/android/android_sdk_path", OS.get_environment("ANDROID_HOME"))
    settings.set_setting("export/android/java_sdk_path", OS.get_environment("JAVA_HOME"))
    settings.save()
    print("Android SDK configured: ", settings.get_setting("export/android/android_sdk_path"))
    print("Java SDK configured: ", settings.get_setting("export/android/java_sdk_path"))
    quit()
