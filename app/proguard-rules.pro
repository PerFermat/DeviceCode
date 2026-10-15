# ML Kit verdrahtet seine Komponenten zur Laufzeit per Reflexion. Im R8-Vollmodus (Standard seit
# AGP 8) gehen dabei interne Felder verloren, und TextRecognition.getClient() stürzt mit einer
# NullPointerException ab. Die ML-Kit-Klassen bleiben deshalb unangetastet.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
