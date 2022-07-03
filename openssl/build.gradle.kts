import Common_gradle.Common.createTarget
import Common_gradle.OpenSSL.opensslPrefix
import org.gradle.configurationcache.extensions.capitalized

//see: https://stackoverflow.com/questions/65397852/how-to-build-openssl-for-ios-and-osx

plugins {
  kotlin("multiplatform")
  id("common")
}

//val opensslTag = "openssl-3.0.3"
val opensslTag = "OpenSSL_1_1_1o"
group = "org.danbrough"
version = opensslTag.substringAfter('_').replace('_', '.')

/*
pick os/compiler from:
BS2000-OSD BSD-generic32 BSD-generic64 BSD-ia64 BSD-riscv64 BSD-sparc64
BSD-sparcv8 BSD-x86 BSD-x86-elf BSD-x86_64 Cygwin Cygwin-i386 Cygwin-i486
Cygwin-i586 Cygwin-i686 Cygwin-x86 Cygwin-x86_64 DJGPP MPE/iX-gcc UEFI UWIN
VC-CE VC-WIN32 VC-WIN32-ARM VC-WIN32-ONECORE VC-WIN64-ARM VC-WIN64A
VC-WIN64A-ONECORE VC-WIN64A-masm VC-WIN64I aix-cc aix-gcc aix64-cc aix64-gcc
android-arm android-arm64 android-armeabi android-mips android-mips64
android-x86 android-x86_64 android64 android64-aarch64 android64-mips64
android64-x86_64 bsdi-elf-gcc cc darwin-i386-cc darwin-ppc-cc
darwin64-arm64-cc darwin64-debug-test-64-clang darwin64-ppc-cc
darwin64-x86_64-cc gcc haiku-x86 haiku-x86_64 hpux-ia64-cc hpux-ia64-gcc
hpux-parisc-cc hpux-parisc-gcc hpux-parisc1_1-cc hpux-parisc1_1-gcc
hpux64-ia64-cc hpux64-ia64-gcc hpux64-parisc2-cc hpux64-parisc2-gcc hurd-x86
ios-cross ios-xcrun ios64-cross ios64-xcrun iossimulator-xcrun iphoneos-cross
irix-mips3-cc irix-mips3-gcc irix64-mips4-cc irix64-mips4-gcc linux-aarch64
linux-alpha-gcc linux-aout linux-arm64ilp32 linux-armv4 linux-c64xplus
linux-elf linux-generic32 linux-generic64 linux-ia64 linux-mips32 linux-mips64
linux-ppc linux-ppc64 linux-ppc64le linux-sparcv8 linux-sparcv9 linux-x32
linux-x86 linux-x86-clang linux-x86_64 linux-x86_64-clang linux32-s390x
linux64-mips64 linux64-riscv64 linux64-s390x linux64-sparcv9 mingw mingw64
nextstep nextstep3.3 purify sco5-cc sco5-gcc solaris-sparcv7-cc
solaris-sparcv7-gcc solaris-sparcv8-cc solaris-sparcv8-gcc solaris-sparcv9-cc
solaris-sparcv9-gcc solaris-x86-gcc solaris64-sparcv9-cc solaris64-sparcv9-gcc
solaris64-x86_64-cc solaris64-x86_64-gcc tru64-alpha-cc tru64-alpha-gcc
uClinux-dist uClinux-dist64 unixware-2.0 unixware-2.1 unixware-7
unixware-7-gcc vms-alpha vms-alpha-p32 vms-alpha-p64 vms-ia64 vms-ia64-p32
vms-ia64-p64 vos-gcc vxworks-mips vxworks-ppc405 vxworks-ppc60x vxworks-ppc750
vxworks-ppc750-debug vxworks-ppc860 vxworks-ppcgen vxworks-simlinux debug
debug-erbridge debug-linux-ia32-aes debug-linux-pentium debug-linux-ppro
debug-test-64-clang
 */
val PlatformNative<*>.opensslPlatform
  get() = when (this) {
    PlatformNative.LinuxX64 -> "linux-x86_64"
    PlatformNative.LinuxArm64 -> "linux-aarch64"
    PlatformNative.LinuxArm -> "linux-armv4"
    PlatformAndroid.AndroidArm -> "android-arm"
    PlatformAndroid.AndroidArm64 -> "android-arm64"
    PlatformAndroid.Android386 -> "android-x86"
    PlatformAndroid.AndroidAmd64 -> "android-x86_64"
    PlatformNative.MingwX64 -> "mingw64"
    PlatformNative.MacosX64 -> "darwin64-x86_64-cc"
    PlatformNative.MacosArm64 -> "darwin64-arm64-cc"
    else -> TODO("Add support for $this")
  }


val PlatformNative<*>.opensslSrcDir: File
  get() = BuildEnvironment.buildCacheDir.resolve("openssl/$version/$name")


val opensslGitDir = project.file("src/openssl.git")

val srcClone by tasks.registering(Exec::class) {
  commandLine(
    BuildEnvironment.gitBinary,
    "clone",
    "--bare",
    "https://github.com/openssl/openssl",
    opensslGitDir
  )
  outputs.dir(opensslGitDir)
  onlyIf {
    !opensslGitDir.exists()
  }

}

fun srcPrepare(platform: PlatformNative<*>): Exec =
  tasks.create("srcPrepare${platform.name.toString().capitalized()}", Exec::class) {
    val srcDir = platform.opensslSrcDir
    dependsOn(srcClone)
    onlyIf {
      !srcDir.exists()
    }
    commandLine(
      BuildEnvironment.gitBinary, "clone", "--branch", opensslTag, opensslGitDir, srcDir
    )
  }


fun configureTask(platform: PlatformNative<*>): Exec {

  val srcPrepare = srcPrepare(platform)

  return tasks.create("configure${platform.name.toString().capitalized()}", Exec::class) {
    dependsOn(srcPrepare)
    workingDir(platform.opensslSrcDir)
    //println("configuring with platform: ${platform.opensslPlatform}")
    environment(BuildEnvironment.environment(platform))
    val args = mutableListOf(
      "./Configure", platform.opensslPlatform,
      //"no-shared",
      "no-tests", "--prefix=${opensslPrefix(platform)}"
    )
    if (platform.isAndroid) args += "-D__ANDROID_API__=${BuildEnvironment.androidNdkApiVersion} "
    else if (platform.isWindows) args += "--cross-compile-prefix=${platform.host}-"
    commandLine(args)
    doFirst {
      println("ENVIRONMENT: ${BuildEnvironment.environment(platform)}")
      println("RUNNING $args")
    }
  }
}

fun buildTask(platform: PlatformNative<*>) {
  val configureTask = configureTask(platform)

  tasks.create("build${platform.name.toString().capitalized()}", Exec::class) {

    opensslPrefix(platform).resolve("lib/libssl.a").exists().also {
      isEnabled = !it
      configureTask.isEnabled = !it
    }
    dependsOn(configureTask.name)


    tasks.getAt("buildAll").dependsOn(this)
    workingDir(platform.opensslSrcDir)
    outputs.files(fileTree(opensslPrefix(platform)) {
      include("lib/*.a", "lib/*.so", "lib/*.h", "lib/*.dylib")
    })
    environment(BuildEnvironment.environment(platform))
    group = BasePlugin.BUILD_GROUP
    commandLine("make", "install_sw")
    doLast {
      if (!project.properties.getOrDefault("openssl.keepsrc", "false").toString().toBoolean())
        platform.opensslSrcDir.deleteRecursively()
    }

  }
}


kotlin {

  val buildAll by tasks.registering
  val commonTest by sourceSets.getting {
    dependencies {
      implementation(kotlin("test"))
    }
  }


  BuildEnvironment.nativeTargets.forEach { platform ->

    createTarget(platform) {

      compilations["main"].apply {

        cinterops.create("openssl") {
          defFile(project.file("src/openssl.def"))
          extraOpts(listOf("-libraryPath", opensslPrefix(platform).resolve("lib")))
        }

        dependencies {
          //  implementation("com.github.danbrough.klog:klog:_")
        }

      }


/*      binaries {
        executable("testApp") {
          entryPoint = "openssl.TestApp"
        }
      }*/
    }

    buildTask(platform)
  }
}

repositories {
  maven("https://jitpack.io")
  maven("https://h1.danbrough.org/maven")
}

/*publishing {
  publications {
  }

  repositories {
    maven(ProjectProperties.MAVEN_REPO)
  }
}*/

