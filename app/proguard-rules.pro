# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# =============================================================================
# ENUM CONSTANTS  --  RELEASE-BLOCKING. DO NOT REMOVE.
# =============================================================================
# R8 runs in full mode with `android.r8.strictFullModeForKeepRules=true`
# (see gradle.properties). The default rule inherited from
# proguard-android-optimize.txt only keeps the *methods*:
#
#     -keepclassmembers enum * { values(); valueOf(); }
#
# ...it does NOT keep the enum *constant fields*. R8 additionally
# constant-folds expressions like `MyEnum.CONST.name` into a plain string
# literal, which orphans the backing field and lets the shrinker delete it.
#
# The observable result was that `mapping.txt` kept SyncMode.INCREMENTAL and
# SyncMode.REBUILD but `usage.txt` listed SyncMode.FULL as REMOVED, so
# `SyncMode.valueOf("FULL")` threw IllegalArgumentException in release builds
# only -- silently killing the post-onboarding library scan and leaving Home
# permanently empty. Roughly a dozen other enums were pruned the same way
# (AppLanguage lost 11 of 12 languages, LibraryTabId lost all 7, etc).
#
# Keeping the fields costs a negligible amount of dex size and removes an
# entire class of release-only failure. Enum constants must never be stripped.
-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep javax.lang.model classes (often needed by annotation processors or code generation libraries)
-keep class javax.lang.model.** { *; }
-keep interface javax.lang.model.** { *; }

# Keep javax.sound.sampled classes (for audio processing libraries like JFLAC)
-keep class javax.sound.sampled.** { *; }
-keep interface javax.sound.sampled.** { *; }

# Specific rules for JavaPoet if the above is not enough
-keep class com.squareup.javapoet.** { *; }
-keep interface com.squareup.javapoet.** { *; }

# Specific rules for AutoValue if it's directly used or a transitive dependency
# (though usually AutoValue is a compile-time dependency and shouldn't need this)
# -keep class com.google.auto.value.** { *; }
# -keep interface com.google.auto.value.** { *; }

# Rules for TagLib
-keep class com.kyant.taglib.** { *; }

# Rules for JAudioTagger (fallback metadata reader)
-keep class org.jaudiotagger.** { *; }

# [NUEVO] Regla general para mantener metadatos de Kotlin, puede ayudar a R8
-keep class kotlin.Metadata { *; }

# ExoPlayer FFmpeg extension
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.exoplayer.ffmpeg.** { *; }

# ExoPlayer MIDI extension and JSyn synthesizer
-keep class androidx.media3.decoder.midi.** { *; }
-keep class com.jsyn.** { *; }
-keep class com.softsynth.** { *; }
-dontwarn com.jsyn.**
-dontwarn com.softsynth.**

# Mantener clases de datos y sus miembros para evitar que R8 Full elimine campos
-keepclassmembers class com.theveloper.pixelplay.data.model.** { *; }
-keepclassmembers class com.theveloper.pixelplay.domain.model.** { *; }

-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault, *Annotation*

# Cast framework classes loaded via manifest/reflective entry points.
-keep class com.theveloper.pixelplay.data.service.cast.CastOptionsProvider { *; }
-keep class * implements com.google.android.gms.cast.framework.OptionsProvider

# Gson generic type capture for backup/restore in release builds.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.theveloper.pixelplay.data.preferences.PreferenceBackupEntry { *; }
-keep class com.theveloper.pixelplay.data.backup.model.** { *; }
-keep class com.theveloper.pixelplay.data.backup.module.** { *; }
# Backup payload entities are part of the persisted .pxpl contract.
-keep class com.theveloper.pixelplay.data.database.FavoritesEntity { *; }
-keep class com.theveloper.pixelplay.data.database.SongEngagementEntity { *; }
-keep class com.theveloper.pixelplay.data.database.LyricsEntity { *; }
-keep class com.theveloper.pixelplay.data.database.SearchHistoryEntity { *; }
-keep class com.theveloper.pixelplay.data.database.TransitionRuleEntity { *; }

# Netty channel classes are instantiated reflectively and require public no-arg constructors.
# Without these, release builds can fail with:
# "IllegalArgumentException: Class NioServerSocketChannel does not have a public non-arg constructor"
-keep class io.netty.channel.socket.nio.NioServerSocketChannel { public <init>(); }
-keep class io.netty.channel.socket.nio.NioSocketChannel { public <init>(); }
-keep class io.netty.channel.epoll.EpollServerSocketChannel { public <init>(); }
-keep class io.netty.channel.epoll.EpollSocketChannel { public <init>(); }
-keep class io.netty.channel.kqueue.KQueueServerSocketChannel { public <init>(); }
-keep class io.netty.channel.kqueue.KQueueSocketChannel { public <init>(); }

# Ktor server engine classes (CIO and internals) — prevent R8 from stripping
# service-loaded or reflectively-accessed engine wiring.
-keep class io.ktor.server.engine.** { *; }
-keep class io.ktor.server.cio.** { *; }

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.

# [NUEVO] Reglas para solucionar el error de Ktor y R8
-dontwarn java.lang.management.**
-dontwarn reactor.blockhound.**

-dontwarn java.awt.Graphics2D
-dontwarn java.awt.Image
-dontwarn java.awt.geom.AffineTransform
-dontwarn java.awt.image.BufferedImage
-dontwarn java.awt.image.ImageObserver
-dontwarn java.awt.image.RenderedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.ImageWriter
-dontwarn javax.imageio.stream.ImageInputStream
-dontwarn javax.imageio.stream.ImageOutputStream
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8
-dontwarn javax.sound.sampled.AudioFileFormat$Type
-dontwarn javax.sound.sampled.AudioFileFormat
-dontwarn javax.sound.sampled.AudioFormat$Encoding
-dontwarn javax.sound.sampled.AudioFormat
-dontwarn javax.sound.sampled.AudioInputStream
-dontwarn javax.sound.sampled.UnsupportedAudioFileException
-dontwarn javax.sound.sampled.spi.AudioFileReader
-dontwarn javax.sound.sampled.spi.FormatConversionProvider
-dontwarn javax.swing.filechooser.FileFilter

-dontwarn io.netty.internal.tcnative.AsyncSSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.AsyncTask
-dontwarn io.netty.internal.tcnative.Buffer
-dontwarn io.netty.internal.tcnative.CertificateCallback
-dontwarn io.netty.internal.tcnative.CertificateCompressionAlgo
-dontwarn io.netty.internal.tcnative.CertificateVerifier
-dontwarn io.netty.internal.tcnative.Library
-dontwarn io.netty.internal.tcnative.SSL
-dontwarn io.netty.internal.tcnative.SSLContext
-dontwarn io.netty.internal.tcnative.SSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.SSLSessionCache
-dontwarn io.netty.internal.tcnative.SessionTicketKey
-dontwarn io.netty.internal.tcnative.SniHostNameMatcher
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.logging.log4j.Level
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.apache.logging.log4j.message.MessageFactory
-dontwarn org.apache.logging.log4j.spi.ExtendedLogger
-dontwarn org.apache.logging.log4j.spi.ExtendedLoggerWrapper
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ClientProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$Provider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ServerProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego$ServerProvider
-dontwarn org.eclipse.jetty.npn.NextProtoNego

# TDLib (Telegram Database Library) rules
-keep class org.drinkless.tdlib.** { *; }
-keep interface org.drinkless.tdlib.** { *; }

# Ktor & Netty Rules (Crucial for StreamProxy)
-keep class org.slf4j.** { *; }

# Ktor Specific
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn io.netty.**

# Ensure internal server can start
-keep class com.theveloper.pixelplay.data.telegram.TelegramStreamProxy { *; }

# Keep Kotlin reflection if needed by Ktor/Serialization in Release
-keep class kotlin.reflect.** { *; }

# Kuromoji
-keep class com.atilika.kuromoji.** { *; }
-keepnames class com.atilika.kuromoji.** { *; }
-dontwarn com.atilika.kuromoji.**

# Pinyin4J
-keep class net.sourceforge.pinyin4j.** { *; }
-keepclassmembers class net.sourceforge.pinyin4j.** { *; }
-dontwarn net.sourceforge.pinyin4j.**

# Glance Widget
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }

# =============================================================================
# TIMBER LOGGING OPTIMIZATION FOR RELEASE BUILDS
# =============================================================================
# Strip VERBOSE and DEBUG log calls entirely from release builds.
# This removes the method calls at bytecode level, eliminating any overhead
# from string concatenation or log message building.

-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
}

# Also strip Timber.Tree methods used by custom trees (belt and suspenders)
-assumenosideeffects class timber.log.Timber$Tree {
    public void v(...);
    public void d(...);
    public void i(...);
}

# Strip Android Log.v and Log.d calls as well
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Optional desktop-only APIs referenced by bundled JVM libraries (Rhino/audio tooling).
# These code paths are not used on Android, but R8 full mode still resolves their types.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8
-dontwarn javax.sound.sampled.AudioFileFormat$Type
-dontwarn javax.sound.sampled.AudioFileFormat
-dontwarn javax.sound.sampled.AudioFormat$Encoding
-dontwarn javax.sound.sampled.AudioFormat
-dontwarn javax.sound.sampled.AudioInputStream
-dontwarn javax.sound.sampled.UnsupportedAudioFileException
-dontwarn javax.sound.sampled.spi.AudioFileReader
-dontwarn javax.sound.sampled.spi.FormatConversionProvider
-dontwarn javax.swing.filechooser.FileFilter

# =============================================================================
# HILT / DAGGER: Prevent R8 from stripping Hilt-generated injection entry points.
# Without these, @AndroidEntryPoint classes silently lose their injected fields
# in release builds, causing NullPointerExceptions or ClassNotFoundExceptions.
# =============================================================================
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }
-keepclassmembers class * {
    @dagger.* <fields>;
    @javax.inject.* <fields>;
    @dagger.hilt.* <fields>;
}
# Keep Hilt ViewModel factory / generated components
-keep class **_HiltModules* { *; }
-keep class **_Hilt* { *; }
-keep class *_MembersInjector { *; }
-keep class *_Factory { *; }

# =============================================================================
# ROOM DATABASE: Prevent R8 from stripping Room DAOs and generated impls.
# =============================================================================
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class **_Impl extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    abstract *;
}

# =============================================================================
# WORKMANAGER + HILT WORKER: Keep @HiltWorker annotated classes.
# =============================================================================
-keep class * extends androidx.work.Worker { <init>(...); }
-keep class * extends androidx.work.CoroutineWorker { <init>(...); }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# =============================================================================
# KOTLIN COROUTINES: prevent R8 from breaking coroutine state machines.
# =============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }

# =============================================================================
# MEDIA3 / EXOPLAYER: Keep MediaSession/MediaLibraryService entry points
# =============================================================================
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.datasource.** { *; }
