-injars war/WEB-INF/classes
-injars war/WEB-INF/lib/xmlenc-0.52.jar
-injars war/WEB-INF/lib/uuid-3.3.jar
-injars war/WEB-INF/lib/eve-core.jar
-libraryjars war/WEB-INF/lib/
-libraryjars <java.home>/lib/rt.jar
-libraryjars /usr/share/java/servlet-api.jar
-libraryjars war/WEB-INF/lib/appengine-api-1.0-sdk-1.6.2.1.jar
-libraryjars war/WEB-INF/lib/appengine-api-labs-1.6.2.1.jar
-outjars war/WEB-INF/lib/dialogHandler.jar

-dontnote
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,
                SourceFile,LineNumberTable,*Annotation*,EnclosingMethod
-keepdirectories
-verbose

-keep public class * implements javax.servlet.Servlet
-keep public class com.almende.dialog.** {
    public protected *;
}
-keepclassmembernames class * {
    java.lang.Class class$(java.lang.String);
    java.lang.Class class$(java.lang.String, boolean);
}
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
