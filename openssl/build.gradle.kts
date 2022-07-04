import BuildEnvironment.buildEnvironment
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.Family

//see: https://stackoverflow.com/questions/65397852/how-to-build-openssl-for-ios-and-osx

plugins {
  kotlin("multiplatform")
  id("common")
}

//val opensslTag = "openssl-3.0.3"
val opensslTag = "OpenSSL_1_1_1p"
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
val KonanTarget.opensslPlatform
  get() = when (this) {
    KonanTarget.LINUX_X64 -> "linux-x86_64"
    KonanTarget.LINUX_ARM64 -> "linux-arm64"
    KonanTarget.LINUX_ARM32_HFP -> "linux-armv4"
    KonanTarget.MACOS_X64 -> "darwin64-x86_64-cc"
    /*PlatformNative.LinuxArm64 -> "linux-aarch64"
    PlatformNative.LinuxArm -> "linux-armv4"
    PlatformAndroid.AndroidArm -> "android-arm"

    PlatformAndroid.Android386 -> "android-x86"
    PlatformAndroid.AndroidAmd64 -> "android-x86_64"
    PlatformNative.MingwX64 -> "mingw64"
    PlatformNative.MacosX64 -> "darwin64-x86_64-cc"
    PlatformNative.MacosArm64 -> "darwin64-arm64-cc"*/
    else -> TODO("Add support for $this")
  }


val opensslGitDir = project.buildDir.resolve("openssl.git")

val KonanTarget.opensslSrcDir: File
  get() = project.buildDir.resolve("openssl/$version/$displayName")

val KonanTarget.opensslPrefixDir: File
  get() = rootProject.file("openssl/lib/${displayName}")

val srcClone by tasks.registering(Exec::class) {
  commandLine(BuildEnvironment.gitBinary, "clone", "--bare", "https://github.com/openssl/openssl", opensslGitDir)
  outputs.dir(opensslGitDir)
  onlyIf { !opensslGitDir.exists() }
}

fun srcPrepare(target: KonanTarget): TaskProvider<Exec> {
  return tasks.register("srcPrepare${target.displayNameCapitalized}", Exec::class) {
    val srcDir = target.opensslSrcDir
    dependsOn(srcClone)
    onlyIf { !srcDir.exists() }
    commandLine(BuildEnvironment.gitBinary, "clone", "-v","--branch", opensslTag, opensslGitDir, srcDir)
  }
}

fun configureTask(target: KonanTarget): TaskProvider<Exec> {

  val srcPrepare = srcPrepare(target)

  return tasks.register("configure${target.displayNameCapitalized}", Exec::class) {
    dependsOn(srcPrepare)
    workingDir(target.opensslSrcDir)
    //println("configuring with platform: ${platform.opensslPlatform}")
    //environment(BuildEnvironment.environment(platform))


    val env = target.buildEnvironment

    environment(env)

    val args = mutableListOf(
      "./Configure", target.opensslPlatform,
      "no-tests", "--prefix=${target.opensslPrefixDir}"
    ).apply {
      if (target.family == Family.ANDROID) add("-D__ANDROID_API__=${BuildEnvironment.androidNdkApiVersion} ")
      else if (target.family == Family.MINGW) add("--cross-compile-prefix=${target.host}-")
    }



    commandLine(args)
    doFirst {
      println("WORK DIR: $workingDir")
      println("ENV: $env")
      println("RUNNING ${args.joinToString(" ")}")
    }
  }
}


fun compileTask(target: KonanTarget): TaskProvider<Exec> {
  val configureTask = configureTask(target)

  return tasks.register<Exec>("compile${target.displayNameCapitalized}") {
    dependsOn(configureTask)

    target.opensslPrefixDir.resolve("lib/libssl.a").exists().also {
      isEnabled = !it
      configureTask.get().isEnabled = !it
    }
    workingDir(target.opensslSrcDir)

    commandLine("make", "install_sw")


  }
}

kotlin {
  linuxX64()
  linuxArm64()
  macosX64()

  val buildAll by tasks.creating {
    group = BasePlugin.BUILD_GROUP
    description = "Builds openssl for all configured targets"
  }

  targets.withType<KotlinNativeTarget>().all {
    compileTask(konanTarget).also {
      buildAll.dependsOn(this)
    }

  }
}

/*fun buildTask(target: KotlinNativeTarget) {
  val configureTask = configureTask(target)


 tasks.create("build${platform.name.toString().capitalized()}", Exec::class) {

    opensslPrefix(platform).resolve("lib/libssl.a").exists().also {
      isEnabled = !it
      configureTask.get().isEnabled = !it
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
}*/

kotlin {

  val buildAll by tasks.registering
  val commonTest by sourceSets.getting {
    dependencies {
      implementation(kotlin("test"))
    }
  }

  linuxX64()
  macosX64()


  targets.withType<KotlinNativeTarget>().all {
    cinterops.create("openssl$platform") {
      defFile(project.file("src/openssl.def"))
      extraOpts(listOf("-libraryPath", opensslPrefix(platform).resolve("lib")))
    }
  }
/*
    createTarget(platform) {

      compilations["main"].apply {

        cinterops.create("openssl$platform") {
          defFile(project.file("src/openssl.def"))
          extraOpts(listOf("-libraryPath", opensslPrefix(platform).resolve("lib")))
        }

        dependencies {
          //  implementation("com.github.danbrough.klog:klog:_")
        }

      }


*//*      binaries {
        executable("testApp") {
          entryPoint = "openssl.TestApp"
        }
      }*//*
    }

    buildTask(platform)
  }*/
}

repositories {
  maven("https://h1.danbrough.org/maven")
}

/*publishing {
  publications {
  }

  repositories {
    maven(ProjectProperties.MAVEN_REPO)
  }
}*/

