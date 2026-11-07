# Referenced by the release build type. Compact sidetest builds enable shrinking.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ShizukuHelper looks up Shizuku.newProcess by name.
-keep class rikka.shizuku.Shizuku {
    public static *** newProcess(...);
}
