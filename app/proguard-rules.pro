-repackageclasses
-allowaccessmodification
-overloadaggressively
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void check*(...);
    public static void throw*(...);
}
-assumenosideeffects class java.util.Objects {
    ** requireNonNull(...);
}
-dontwarn dalvik.system.VMRuntime

-keepclassmembers class io.github.vvb2060.packageinstaller.viewmodel.InstallViewModel {
    <init>(android.app.Application);
}
-keepnames class moe.shizuku.api.BinderContainer

# IBinder#getExtension is invoked reflectively by the OEM framework
# (e.g. ColorOS OplusPackageManager). Keep our override or it'll be
# stripped and fall through to IBinder's default impl, which throws.
-keepclassmembers class * extends rikka.shizuku.ShizukuBinderWrapper {
    public android.os.IBinder getExtension();
}
