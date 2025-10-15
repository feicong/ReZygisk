import java.nio.file.Paths
import org.gradle.internal.os.OperatingSystem

fun getLatestNDKPath(): String {
  val android_home = System.getenv("ANDROID_HOME")
  if (android_home == null) {
    throw Exception("ANDROID_HOME not set")
  }

  val ndkPath = android_home + "/ndk"

  val ndkDir = Paths.get(ndkPath)
  if (!ndkDir.toFile().exists()) {
    throw Exception("NDK not found at $ndkPath")
  }

  val ndkVersion = ndkDir.toFile().listFiles().filter { it.isDirectory }.map { it.name }.sorted().last()
  return ndkPath + "/" + ndkVersion
}

fun hostPrebuiltDir(): String {
  val os = System.getProperty("os.name").lowercase()
  return when {
    os.contains("mac") || os.contains("darwin") -> "darwin-x86_64"
    os.contains("win") -> "windows-x86_64"
    else -> "linux-x86_64"
  }
}

fun exeSuffix(): String {
  val os = System.getProperty("os.name").lowercase()
  // On Windows NDK provides clang wrapper scripts with .cmd suffix
  return if (os.contains("win")) ".cmd" else ""
}

val minAPatchVersion: Int by rootProject.extra
val minKsuVersion: Int by rootProject.extra
val maxKsuVersion: Int by rootProject.extra
val minMagiskVersion: Int by rootProject.extra
val verCode: Int by rootProject.extra
val verName: String by rootProject.extra
val commitHash: String by rootProject.extra

fun getVersionDefine(): String {
  val os = System.getProperty("os.name").lowercase()
  // Windows .cmd wrappers need extra escaping
  return if (os.contains("win")) {
    "-DZKSU_VERSION=\\\"$verName\\\""
  } else {
    "-DZKSU_VERSION=\"$verName\""
  }
}

val CStandardFlags = arrayOf(
  "-D_GNU_SOURCE", "-std=c99", "-Wpedantic", "-Wall", "-Wextra", "-Werror",
  "-Wformat", "-Wuninitialized", "-Wshadow", "-Wno-zero-length-array",
  "-Wconversion", "-Wno-c23-extensions", "-Wno-error=fixed-enum-extension", "-Iroot_impl", "-llog",
  "-Wno-error",
  "-DMIN_APATCH_VERSION=$minAPatchVersion",
  "-DMIN_KSU_VERSION=$minKsuVersion",
  "-DMAX_KSU_VERSION=$maxKsuVersion",
  "-DMIN_MAGISK_VERSION=$minMagiskVersion",
  getVersionDefine()
)

val CFlagsRelease = arrayOf(
  "-Wl,--strip-all", "-flto=thin", "-O3", "-ffast-math"
)

val CFlagsDebug = arrayOf(
  "-g", "-O0", "-DDEBUG"
)

val Files = arrayOf(
  "root_impl/apatch.c",
  "root_impl/common.c",
  "root_impl/kernelsu.c",
  "root_impl/magisk.c",
  "companion.c",
  "main.c",
  "utils.c",
  "zygiskd.c"
)

task("buildAndStrip") {
  group = "build"
  description = "Build the native library and strip the debug symbols."

  val isDebug = gradle.startParameter.taskNames.any { it.lowercase().contains("debug") }
  doLast {
    val ndkPath = getLatestNDKPath()

  val hostDir = hostPrebuiltDir()
  val ext = exeSuffix()

  val aarch64Compiler = Paths.get(ndkPath, "toolchains", "llvm", "prebuilt", hostDir, "bin", "aarch64-linux-android34-clang" + ext).toString()
  val armv7aCompiler = Paths.get(ndkPath, "toolchains", "llvm", "prebuilt", hostDir, "bin", "armv7a-linux-androideabi34-clang" + ext).toString()
  val x86Compiler = Paths.get(ndkPath, "toolchains", "llvm", "prebuilt", hostDir, "bin", "i686-linux-android34-clang" + ext).toString()
  val x86_64Compiler = Paths.get(ndkPath, "toolchains", "llvm", "prebuilt", hostDir, "bin", "x86_64-linux-android34-clang" + ext).toString()

    if (!Paths.get(aarch64Compiler).toFile().exists()) {
      throw Exception("aarch64 compiler not found at $aarch64Compiler")
    }

    if (!Paths.get(armv7aCompiler).toFile().exists()) {
      throw Exception("armv7a compiler not found at $armv7aCompiler")
    }

    if (!Paths.get(x86Compiler).toFile().exists()) {
      throw Exception("x86 compiler not found at $x86Compiler")
    }

    if (!Paths.get(x86_64Compiler).toFile().exists()) {
      throw Exception("x86_64 compiler not found at $x86_64Compiler")
    }

    val Files = Files.map { Paths.get(project.projectDir.toString(), "src", it).toString() }.toTypedArray()

    val buildDir = getLayout().getBuildDirectory().getAsFile().get()
    buildDir.mkdirs()

    val aarch64OutputDir = Paths.get(buildDir.toString(), "arm64-v8a").toFile()
    val armv7aOutputDir = Paths.get(buildDir.toString(), "armeabi-v7a").toFile()
    val x86OutputDir = Paths.get(buildDir.toString(), "x86").toFile()
    val x86_64OutputDir = Paths.get(buildDir.toString(), "x86_64").toFile()

    aarch64OutputDir.mkdirs()
    armv7aOutputDir.mkdirs()
    x86OutputDir.mkdirs()
    x86_64OutputDir.mkdirs()

    val compileArgs = (if (isDebug) CFlagsDebug else CFlagsRelease) + CStandardFlags

    exec {
      commandLine(aarch64Compiler, "-o", Paths.get(aarch64OutputDir.toString(), "zygiskd").toString(), *compileArgs, *Files)
    }
    exec {
      commandLine(armv7aCompiler, "-o", Paths.get(armv7aOutputDir.toString(), "zygiskd").toString(), *compileArgs, *Files)
    }
    exec {
      commandLine(x86Compiler, "-o", Paths.get(x86OutputDir.toString(), "zygiskd").toString(), *compileArgs, *Files)
    }
    exec {
      commandLine(x86_64Compiler, "-o", Paths.get(x86_64OutputDir.toString(), "zygiskd").toString(), *compileArgs, *Files)
    }
  }
}

