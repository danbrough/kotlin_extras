@file:Suppress("MemberVisibilityCanBePrivate")

import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

val KonanTarget.displayName: String
  get() = name.split('_').foldRight("") { a, b ->
    "$a${b.capitalize()}"
  }

val KonanTarget.displayNameCapitalized: String
  get() = displayName.capitalize()


object BuildEnvironment {

  val goBinary: String
    get() = ProjectProperties.getProperty("go.binary", "/usr/bin/go")
  val gitBinary: String
    get() = ProjectProperties.getProperty("git.binary", "/usr/bin/git")

  val javah: String
    get() = ProjectProperties.getProperty("javah.path")

  val buildCacheDir: File
    get() = File(ProjectProperties.getProperty("build.cache"))

  val konanDir: File
    get() = File(
      ProjectProperties.getProperty(
        "konan.dir", "${System.getProperty("user.home")}/.konan"
      )
    )
  val androidNdkDir: File
    get() = File(ProjectProperties.getProperty("android.ndk.dir"))
  val androidNdkApiVersion: Int
    get() = ProjectProperties.getProperty("android.ndk.api.version", "23").toInt()
  val buildPath: List<String>
    get() = ProjectProperties.getProperty("build.path").split("[\\s]+".toRegex())

  val hostIsWindows: Boolean
    get() = System.getProperty("os.name").startsWith("Windows")

  val hostIsMac: Boolean
    get() = System.getProperty("os.name").startsWith("Mac")

  val hostIsLinux: Boolean
    get() = System.getProperty("os.name").startsWith("Linux")

  val hostPlatform: PlatformNative<*>
    get() = when {
      hostIsLinux -> PlatformNative.LinuxX64
      hostIsMac -> PlatformNative.MacosX64
      hostIsWindows -> PlatformNative.MingwX64
      else -> throw Error("Host ${System.getProperty("os.name")}:${System.getProperty("os.arch")} not supported")
    }

  val nativeTargets: List<PlatformNative<*>>
    get() = mutableListOf<PlatformNative<*>>().apply {
      if (ProjectProperties.IDEA_ACTIVE) add(hostPlatform) else {

        if (hostIsLinux) {
          add(PlatformNative.LinuxX64)
          add(PlatformNative.LinuxArm64)
          add(PlatformNative.LinuxArm)
          add(PlatformNative.MingwX64)
        } else if (hostIsWindows) {
          add(PlatformNative.MingwX64)
        } else if (hostIsMac) {
          add(PlatformNative.LinuxX64)

          add(PlatformNative.MacosX64)
          add(PlatformNative.MacosArm64)
          add(PlatformNative.IosX64)
        }

        add(PlatformAndroid.AndroidArm)
        add(PlatformAndroid.AndroidArm64)
        add(PlatformAndroid.Android386)
        add(PlatformAndroid.AndroidAmd64)

        // PlatformNative.MacosX64,
      }
    }

  val androidToolchainDir by lazy {
    val toolchainDir = androidNdkDir.resolve("toolchains/llvm/prebuilt/linux-x86_64").let {
      if (!it.exists())
        androidNdkDir.resolve("toolchains/llvm/prebuilt/darwin-x86_64")
      else it
    }
    if (!toolchainDir.exists()) throw Error("Failed to locate toolchain dir")
    toolchainDir
  }

  val clangBinDir by lazy {
    File("$konanDir/dependencies").listFiles()?.first {
      it.isDirectory && it.name.contains("essentials")
    }?.resolve("bin")
      ?: throw Error("Failed to locate clang folder in ${konanDir}/dependencies")
  }

  fun environment(platform: PlatformNative<*>): Map<String, Any> = mutableMapOf(
    "CGO_ENABLED" to 1,
    "GOOS" to platform.goOS,
    "GOARM" to platform.goArm,
    "GOARCH" to platform.goArch,
    "GOBIN" to platform.goCacheDir.resolve("${platform.name}/bin"),
    "GOCACHE" to platform.goCacheDir.resolve("${platform.name}/gobuild"),
    "GOCACHEDIR" to platform.goCacheDir,
    "GOMODCACHE" to platform.goCacheDir.resolve("mod"),
    "GOPATH" to platform.goCacheDir.resolve(platform.name.toString()),
    "KONAN_DATA_DIR" to platform.goCacheDir.resolve("konan"),
    "CFLAGS" to "-O3  -Wno-macro-redefined -Wno-deprecated-declarations -DOPENSSL_SMALL_FOOTPRINT=1",
    "MAKE" to "make -j4",
  ).apply {

    val path = buildPath.toMutableList()

    //this["AR"] =  "$clangBinDir/llvm-ar"
    this["LD"] = "$clangBinDir/lld"
    //this["RANLIB"] = "$clangBinDir/llvm-ar"

    when (platform) {

      PlatformNative.LinuxArm -> {
        val clangArgs =
          "--target=${platform.host} " + "--gcc-toolchain=$konanDir/dependencies/arm-unknown-linux-gnueabihf-gcc-8.3.0-glibc-2.19-kernel-4.9-2 " + "--sysroot=$konanDir/dependencies/arm-unknown-linux-gnueabihf-gcc-8.3.0-glibc-2.19-kernel-4.9-2/arm-unknown-linux-gnueabihf/sysroot "
        this["CC"] = "$clangBinDir/clang $clangArgs"
        this["CXX"] = "$clangBinDir/clang++ $clangArgs"
        //this["RANLIB"] = "/Users/dan/Library/Android/sdk/ndk/23.1.7779620//toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ranlib"

      }

      PlatformNative.LinuxArm64 -> {
        val clangArgs = "--target=${platform.host} " +
            "--sysroot=$konanDir/dependencies/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2/aarch64-unknown-linux-gnu/sysroot " +
            "--gcc-toolchain=$konanDir/dependencies/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2 "
        this["CC"] = "$clangBinDir/clang $clangArgs"
        this["CXX"] = "$clangBinDir/clang++ $clangArgs"
      }

      PlatformNative.LinuxX64 -> {
        val clangArgs =
          "--target=${platform.host} " + "--gcc-toolchain=$konanDir/dependencies/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2 " + "--sysroot=$konanDir/dependencies/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2/x86_64-unknown-linux-gnu/sysroot"
        this["CC"] = "$clangBinDir/clang $clangArgs"
        this["CXX"] = "$clangBinDir/clang++ $clangArgs"
/*        this["RANLIB"] =
          "$konanDir/dependencies/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2/x86_64-unknown-linux-gnu/bin/ranlib"*/
      }

      PlatformNative.MacosX64 -> {


      }


      PlatformNative.MingwX64 -> {

        /*  export HOST=x86_64-w64-mingw32
  export GOOS=windows
  export CFLAGS="$CFLAGS -pthread"
  #export WINDRES=winres
  export WINDRES=/usr/bin/x86_64-w64-mingw32-windres
  export RC=$WINDRES
  export GOARCH=amd64
  export OPENSSL_PLATFORM=mingw64
  export LIBNAME="libkipfs.dll"
  #export PATH=/usr/x86_64-w64-mingw32/bin:$PATH
  export TARGET=$HOST
  #export PATH=$(dir_path bin $TOOLCHAIN):$PATH
  export CROSS_PREFIX=$TARGET-
  export CC=$TARGET-gcc
  export CXX=$TARGET-g++
        */
/*
        this["WINDRES"] = "x86_64-w64-mingw32-windres"
        this["RC"] = this["WINDRES"] as String*/
        /*this["CROSS_PREFIX"] = "${platform.host}-"
        val toolChain = "$konanDir/dependencies/msys2-mingw-w64-x86_64-1"
        this["PATH"] = "$toolChain/bin:${this["PATH"]}"*/

        this["CC"] = "gcc"
        this["CXX"] = "g++"


      }

      PlatformAndroid.AndroidArm, PlatformAndroid.Android386, PlatformAndroid.AndroidArm64, PlatformAndroid.AndroidAmd64 -> {
        path.add(0, androidToolchainDir.resolve("bin").absolutePath)
        this["CC"] = "${platform.host}${androidNdkApiVersion}-clang"
        this["CXX"] = "${platform.host}${androidNdkApiVersion}-clang++"
        this["AR"] = "llvm-ar"
        this["RANLIB"] = "llvm-ranlib"
      }
    }

    this["PATH"] = path.joinToString(File.pathSeparator)

  }


}

enum class GoOS {
  linux, windows, android, darwin
}


enum class GoArch(val altName: String? = null) {
  x86("386"), amd64, arm, arm64;

  override fun toString() = altName ?: name
}

enum class PlatformName {
  Android,
  AndroidNativeArm32, AndroidNativeArm64, AndroidNativeX64, AndroidNativeX86,
  IosArm32, IosArm64, IosSimulatorArm64, IosX64,
  JS, JsBoth, JsIr,
  Jvm, JvmWithJava,
  LinuxArm32Hfp, LinuxArm64, LinuxMips32, LinuxMipsel32, LinuxX64,
  MacosArm64, MacosX64,
  MingwX64, MingwX86,
  TvosArm64, TvosSimulatorArm64, TvosX64,
  Wasm, Wasm32,
  WatchosArm32, WatchosArm64, WatchosSimulatorArm64, WatchosX64, WatchosX86;

  override fun toString() = name.toString().decapitalize()

  companion object {
    fun forName(name: String): Platform<*> = when (valueOf(name)) {
      Android -> TODO()
      AndroidNativeArm32 -> PlatformAndroid.AndroidArm
      AndroidNativeArm64 -> PlatformAndroid.AndroidArm64
      AndroidNativeX64 -> PlatformAndroid.AndroidAmd64
      AndroidNativeX86 -> PlatformAndroid.Android386
      IosArm32 -> TODO()
      IosArm64 -> TODO()
      IosSimulatorArm64 -> TODO()
      IosX64 -> PlatformNative.IosX64
      JS -> TODO()
      JsBoth -> TODO()
      JsIr -> TODO()
      Jvm -> TODO()
      JvmWithJava -> TODO()
      LinuxArm32Hfp -> PlatformNative.LinuxArm
      LinuxArm64 -> PlatformNative.LinuxArm64
      LinuxMips32 -> TODO()
      LinuxMipsel32 -> TODO()
      LinuxX64 -> TODO()
      MacosArm64 -> PlatformNative.MacosArm64
      MacosX64 -> PlatformNative.MacosX64
      MingwX64 -> PlatformNative.MingwX64
      MingwX86 -> TODO()
      TvosArm64 -> TODO()
      TvosSimulatorArm64 -> TODO()
      TvosX64 -> TODO()
      Wasm -> TODO()
      Wasm32 -> TODO()
      WatchosArm32 -> TODO()
      WatchosArm64 -> TODO()
      WatchosSimulatorArm64 -> TODO()
      WatchosX64 -> TODO()
      WatchosX86 -> TODO()
    }
  }

}

sealed class Platform<T : KotlinTarget>(
  val name: PlatformName,
) {
  override fun toString() = name.toString()
}


open class PlatformNative<T : KotlinNativeTarget>(
  name: PlatformName, val host: String, val goOS: GoOS, val goArch: GoArch, val goArm: Int = 7
) : Platform<T>(name) {
  val goCacheDir: File = BuildEnvironment.buildCacheDir.resolve("go")
  val isAndroid = goOS == GoOS.android
  val isLinux = goOS == GoOS.linux
  val isWindows = goOS == GoOS.windows

  object LinuxX64 : PlatformNative<KotlinNativeTargetWithHostTests>(
    PlatformName.LinuxX64, "x86_64-unknown-linux-gnu", GoOS.linux, GoArch.amd64
  )

  object LinuxArm64 : PlatformNative<KotlinNativeTarget>(
    PlatformName.LinuxArm64, "aarch64-unknown-linux-gnu", GoOS.linux, GoArch.arm64
  )

  object LinuxArm : PlatformNative<KotlinNativeTarget>(
    PlatformName.LinuxArm32Hfp, "arm-unknown-linux-gnueabihf", GoOS.linux, GoArch.arm
  )

  object MingwX64 : PlatformNative<KotlinNativeTargetWithHostTests>(
    PlatformName.MingwX64, "x86_64-w64-mingw32", GoOS.windows, GoArch.amd64
  )

  object MacosX64 : PlatformNative<KotlinNativeTargetWithHostTests>(
    PlatformName.MacosX64, "darwin64-x86_64-cc", GoOS.darwin, GoArch.amd64
  )

  object IosX64 : PlatformNative<KotlinNativeTargetWithHostTests>(
    PlatformName.MacosArm64, "darwin64-x86_64-cc", GoOS.darwin, GoArch.arm64
  )

  object MacosArm64 : PlatformNative<KotlinNativeTargetWithHostTests>(
    PlatformName.MacosArm64, "darwin64-aarch64-cc", GoOS.darwin, GoArch.arm64
  )
}


open class PlatformAndroid<T : KotlinNativeTarget>(
  name: PlatformName,
  host: String,
  goOS: GoOS,
  goArch: GoArch,
  goArm: Int = 7,
  val androidLibDir: String
) : PlatformNative<T>(name, host, goOS, goArch, goArm) {

  object AndroidArm : PlatformAndroid<KotlinNativeTarget>(
    PlatformName.AndroidNativeArm32,
    "armv7a-linux-androideabi",
    GoOS.android,
    GoArch.arm,
    androidLibDir = "armeabi-v7a"
  )

  object AndroidArm64 : PlatformAndroid<KotlinNativeTarget>(
    PlatformName.AndroidNativeArm64,
    "aarch64-linux-android",
    GoOS.android,
    GoArch.arm64,
    androidLibDir = "arm64-v8a",
  )

  object Android386 : PlatformAndroid<KotlinNativeTarget>(
    PlatformName.AndroidNativeX86,
    "i686-linux-android",
    GoOS.android,
    GoArch.x86,
    androidLibDir = "x86",
  )

  object AndroidAmd64 : PlatformAndroid<KotlinNativeTarget>(
    PlatformName.AndroidNativeX64,
    "x86_64-linux-android",
    GoOS.android,
    GoArch.amd64,
    androidLibDir = "x86_64",
  )
}


/*
android
androidNativeArm32
androidNativeArm64
androidNativeX64
androidNativeX86
iosArm32
iosArm64
iosSimulatorArm64
iosX64
js
jsBoth
jsIr
jvm
jvmWithJava
linuxArm32Hfp
linuxArm64
linuxMips32
linuxMipsel32
linuxX64
macosArm64
macosX64
mingwX64
mingwX86
tvosArm64
tvosSimulatorArm64
tvosX64
wasm
wasm32
watchosArm32
watchosArm64
watchosSimulatorArm64
watchosX64
watchosX86
 */
