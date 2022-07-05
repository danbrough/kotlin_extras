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


val KonanTarget.opensslPlatform
  get() = when (this) {
    KonanTarget.LINUX_X64 -> "linux-x86_64"
    KonanTarget.LINUX_ARM64 -> "linux-aarch64"
    KonanTarget.LINUX_ARM32_HFP -> "linux-armv4"
    KonanTarget.MACOS_X64 -> "darwin64-x86_64-cc"
    KonanTarget.MACOS_ARM64 -> "darwin64-arm64-cc"
    KonanTarget.MINGW_X64 -> "mingw64"
    KonanTarget.ANDROID_ARM32 -> "android-arm"
    KonanTarget.ANDROID_ARM64 -> "android-arm64"
    KonanTarget.ANDROID_X86 -> "android-x86"
    KonanTarget.ANDROID_X64 -> "android-x86_64"
    else -> TODO("Add opensslPlatform support for $this")
  }


val opensslGitDir = project.buildDir.resolve("openssl.git")

val KonanTarget.opensslSrcDir: File
  get() = project.buildDir.resolve("src/$version/$displayName")

val KonanTarget.opensslPrefixDir: File
  get() = rootProject.file("openssl/lib/${displayName}")

val srcClone by tasks.registering(Exec::class) {
  commandLine(
    BuildEnvironment.gitBinary,
    "clone",
    "--bare",
    "https://github.com/openssl/openssl",
    opensslGitDir
  )
  outputs.dir(opensslGitDir)
  onlyIf { !opensslGitDir.exists() }
}

fun srcPrepare(target: KonanTarget): TaskProvider<Exec> {
  return tasks.register("srcPrepare${target.displayNameCapitalized}", Exec::class) {
    val srcDir = target.opensslSrcDir
    dependsOn(srcClone)
    onlyIf { !srcDir.exists() }
    commandLine(
      BuildEnvironment.gitBinary,
      "clone",
      "-v",
      "--branch",
      opensslTag,
      opensslGitDir,
      srcDir
    )
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
    )

    when (target.family) {
      Family.ANDROID ->
        args.add("-D__ANDROID_API__=${BuildEnvironment.androidNdkApiVersion} ")
      Family.MINGW ->
        args.add("--cross-compile-prefix=${target.host}-")
      else -> {}
    }

    commandLine(args)

    doFirst {
      println("WORK DIR: $workingDir")
      println("ENV: $env")
      println("CC: ${env["CC"]}")
      println("RUNNING ${args.joinToString(" ")}")
    }

  }
}


fun compileTask(target: KonanTarget): TaskProvider<Exec> {
  val configureTask = configureTask(target)

  return tasks.register<Exec>("compile${target.displayNameCapitalized}") {

    environment(target.buildEnvironment)
    workingDir(target.opensslSrcDir)

    outputs.file(target.opensslPrefixDir.resolve("lib/libssl.a"))
    isEnabled = false
    if (!target.opensslPrefixDir.resolve("lib/libssl.a").exists()) {
      dependsOn(configureTask)
      isEnabled = true
    }

    commandLine("make", "install_sw")


    doLast {
      println("FINISHED .. getting executing result ..")
      executionResult.get().also {
        println("EXEC RESULT: $it")
        if (it.exitValue == 0 && target.opensslSrcDir.exists()) {
          println("DELETING: ${target.opensslSrcDir}")
          target.opensslSrcDir.deleteRecursively()
        }
      }
    }

  }
}

kotlin {
  linuxX64()
  linuxArm64()
  linuxArm32Hfp()

  mingwX64()

  if (BuildEnvironment.hostIsMac) {
    macosX64()
  }

  androidNativeArm32()
  androidNativeArm64()
  androidNativeX86()
  androidNativeX64()

  val compileAll by tasks.creating {
    group = BasePlugin.BUILD_GROUP
    description = "Builds openssl for all configured targets"
  }

  targets.withType<KotlinNativeTarget>().all {
    compileTask(konanTarget).also {
      compileAll.dependsOn(it)
    }

    compilations["main"].apply {

      cinterops.create("openssl") {
        packageName("org.danbrough.openssl")
        defFile(project.file("src/openssl.def"))
        extraOpts(listOf("-libraryPath", konanTarget.opensslPrefixDir.resolve("lib")))
      }
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

