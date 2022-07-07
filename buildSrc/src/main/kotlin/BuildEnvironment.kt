@file:Suppress("MemberVisibilityCanBePrivate")

import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

val KonanTarget.displayName: String
  get() = name.split('_').foldRight("") { a, b ->
    "$a${b.capitalize()}"
  }

val KonanTarget.displayNameCapitalized: String
  get() = displayName.capitalize()


enum class GoOS {
  linux, windows, android, darwin
}


enum class GoArch(val altName: String? = null) {
  x86("386"), amd64, arm, arm64;

  override fun toString() = altName ?: name
}

val KonanTarget.goOS: GoOS
  get() = when (family) {
    Family.LINUX -> GoOS.linux
    Family.ANDROID -> GoOS.android
    Family.MINGW -> GoOS.windows
    Family.IOS, Family.OSX, Family.TVOS, Family.WATCHOS -> GoOS.darwin
    else -> TODO("Add goOS support for $this")
  }


val KonanTarget.goArch: GoArch
  get() = when (architecture) {
    Architecture.X64 -> GoArch.amd64
    Architecture.X86 -> GoArch.x86
    Architecture.ARM32 -> GoArch.arm
    Architecture.ARM64 -> GoArch.arm64
    else -> TODO("Add goArch support for $this")
  }

val KonanTarget.goArm: Int
  get() = if (this == KonanTarget.LINUX_ARM32_HFP) 6 else 7

val KonanTarget.host: String
  get() = when (this) {

    KonanTarget.LINUX_X64 -> "x86_64-unknown-linux-gnu"
    KonanTarget.LINUX_ARM64 -> "aarch64-unknown-linux-gnu"
    KonanTarget.LINUX_ARM32_HFP -> "arm-unknown-linux-gnueabihf"

    KonanTarget.MACOS_X64, KonanTarget.IOS_X64 -> "x86_64-apple-darwin"
    KonanTarget.MACOS_ARM64, KonanTarget.IOS_ARM64, KonanTarget.IOS_SIMULATOR_ARM64 -> "darwin64-aarch64"

    KonanTarget.MINGW_X64 -> "x86_64-w64-mingw32"

    KonanTarget.ANDROID_X64 -> "x86_64-linux-android"
    KonanTarget.ANDROID_X86 -> "i686-linux-android"
    KonanTarget.ANDROID_ARM64 -> "aarch64-linux-android"
    KonanTarget.ANDROID_ARM32 -> "armv7a-linux-androideabi"

    else -> TODO("Add host support for $this")
  }


val KonanTarget.androidLibDirName: String
  get() = when (this) {
    KonanTarget.ANDROID_X64 -> "x86"
    KonanTarget.ANDROID_X64 -> "x86_64"
    KonanTarget.ANDROID_ARM64 -> "arm64-v8a"
    KonanTarget.ANDROID_ARM32 -> "armeabi-v7a"
    else -> throw Error("$this is not an android target")
  }


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

  val goCacheDir: File = BuildEnvironment.buildCacheDir.resolve("go")

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


  val androidToolchainDir by lazy {
    val toolchainDir = androidNdkDir.resolve("toolchains/llvm/prebuilt/linux-x86_64").let {
      if (!it.exists()) androidNdkDir.resolve("toolchains/llvm/prebuilt/darwin-x86_64")
      else it
    }
    if (!toolchainDir.exists()) throw Error("Failed to locate toolchain dir at $toolchainDir")
    toolchainDir
  }

  val clangBinDir by lazy {
    File("$konanDir/dependencies").listFiles()?.first {
      it.isDirectory && it.name.contains("essentials")
    }?.resolve("bin") ?: throw Error("Failed to locate clang folder in ${konanDir}/dependencies")
  }

  fun buildEnvironment(target: KonanTarget): Map<String, Any> = target.buildEnvironment

  val KonanTarget.buildEnvironment: Map<String, Any>
    get() = mutableMapOf(
      "CGO_ENABLED" to 1,
      "GOOS" to goOS,
      "GOARM" to goArm,
      "GOARCH" to goArch,
      "GOBIN" to goCacheDir.resolve("${displayName}/bin"),
      "GOCACHE" to goCacheDir.resolve("${displayName}/gobuild"),
      "GOCACHEDIR" to goCacheDir,
      "GOMODCACHE" to goCacheDir.resolve("mod"),
      "GOPATH" to goCacheDir.resolve(displayName),
      "KONAN_DATA_DIR" to goCacheDir.resolve("konan"),
      "CFLAGS" to "-O3  -Wno-macro-redefined -Wno-deprecated-declarations -DOPENSSL_SMALL_FOOTPRINT=1",
      "MAKE" to "make -j${Runtime.getRuntime().availableProcessors() + 1}",
    ).also { env ->
      val path = buildPath.toMutableList()

      env["LD"] = "$clangBinDir/lld"


      when (this) {

        KonanTarget.LINUX_ARM32_HFP -> {
          val clangArgs =
            "--target=$host --gcc-toolchain=$konanDir/dependencies/arm-unknown-linux-gnueabihf-gcc-8.3.0-glibc-2.19-kernel-4.9-2 " + "--sysroot=$konanDir/dependencies/arm-unknown-linux-gnueabihf-gcc-8.3.0-glibc-2.19-kernel-4.9-2/arm-unknown-linux-gnueabihf/sysroot "
          env["CC"] = "$clangBinDir/clang $clangArgs"
          env["CXX"] = "$clangBinDir/clang++ $clangArgs"
          //this["RANLIB"] = "/Users/dan/Library/Android/sdk/ndk/23.1.7779620//toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-ranlib"

        }

        KonanTarget.LINUX_ARM64 -> {
          println("Configuring Linux ARM64 path here")
          val clangArgs =
            "--target=$host --sysroot=$konanDir/dependencies/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2/aarch64-unknown-linux-gnu/sysroot " + "--gcc-toolchain=$konanDir/dependencies/aarch64-unknown-linux-gnu-gcc-8.3.0-glibc-2.25-kernel-4.9-2 "
          env["CC"] = "$clangBinDir/clang $clangArgs"
          env["CXX"] = "$clangBinDir/clang++ $clangArgs"
        }

        KonanTarget.LINUX_X64 -> {
          val clangArgs =
            "--target=$host --gcc-toolchain=$konanDir/dependencies/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2 " +
                "--sysroot=$konanDir/dependencies/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2/x86_64-unknown-linux-gnu/sysroot"
          env["CC"] = "$clangBinDir/clang $clangArgs"
          env["CXX"] = "$clangBinDir/clang++ $clangArgs"

/*        this["RANLIB"] =
          "$konanDir/dependencies/x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2/x86_64-unknown-linux-gnu/bin/ranlib"*/
        }

        KonanTarget.MACOS_X64 -> {
        }
        KonanTarget.MACOS_ARM64 -> {
        }

        KonanTarget.MINGW_X64 -> {

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

          env["CC"] = "gcc"
          env["CXX"] = "g++"


        }

        KonanTarget.ANDROID_ARM32, KonanTarget.ANDROID_ARM64, KonanTarget.ANDROID_X86, KonanTarget.ANDROID_X64 -> {
          path.add(0, androidToolchainDir.resolve("bin").absolutePath)
          val prefix ="${host}${androidNdkApiVersion}"
          env["CC"] = "$prefix-clang"
          env["CXX"] = "$prefix-clang++"
          env["AS"] = "$prefix-as"
          env["AR"] = "llvm-ar"
          env["RANLIB"] = "llvm-ranlib"
/*
          export HOST=arm-unknown-linux-gnueabihf
export CROSS_PREFIX=arm-unknown-linux-gnueabihf
export CC=$CROSS_PREFIX-gcc
export CPP=$CROSS_PREFIX-cpp
export CXX=$CROSS_PREFIX-c++
export AR=$CROSS_PREFIX-ar
export AS=$CROSS_PREFIX-as
export RANDLIB=$CROSS_PREFIX-randlib
export STRIP=$CROSS_PREFIX-strip
export LD=$CROSS_PREFIX-ld
*/

          //env["AR"] = "llvm-ar"
          //env["RANLIB"] = "llvm-ranlib"
          // env["LD"] = "ld"
        }
/*
        PlatformAndroid.AndroidArm, PlatformAndroid.Android386, PlatformAndroid.AndroidArm64, PlatformAndroid.AndroidAmd64 -> {
          path.add(0, androidToolchainDir.resolve("bin").absolutePath)
          this["CC"] = "${platform.host}${androidNdkApiVersion}-clang"
          this["CXX"] = "${platform.host}${androidNdkApiVersion}-clang++"
          this["AR"] = "llvm-ar"
          this["RANLIB"] = "llvm-ranlib"
        }*/
        else -> {
          TODO("add buildEnvironment support for $this")
        }
      }
      env["PATH"] = path.joinToString(File.pathSeparator)

    }
}
