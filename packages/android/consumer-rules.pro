# Keep this module's own classes: LynxUIVlcVideo's methods are called by
# name from the lynx-processor-generated PropsSetter/MethodInvoker (reflective
# dispatch), which R8 in a consuming app's release build can't see through.
-keep class com.carlossweb.lynxvlcvideo.** { *; }
-keep class com.carlossweb.lynxvlcvideo.BehaviorGenerator { *; }
