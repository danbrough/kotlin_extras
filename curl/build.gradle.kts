import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

//see: https://stackoverflow.com/questions/65397852/how-to-build-openssl-for-ios-and-osx

plugins {
  kotlin("multiplatform")
  id("common")
}


val GIT_TAG = "curl-7_84_0"
val GIT_URL = "https://github.com/curl/curl.git"

group = "org.danbrough"
version = GIT_TAG.substringAfter('-').replace('_', '.')

val PlatformNative<*>.srcDir: File
  get() = project.buildDir.resolve("curl/$name/$version")


val gitSrcDir = project.file("src/curl.git")

val srcClone by tasks.registering(Exec::class) {
  commandLine(BuildEnvironment.gitBinary, "clone", "--bare", GIT_URL, gitSrcDir)
  outputs.dir(gitSrcDir)
  onlyIf { !gitSrcDir.exists() }
}

val srcUpdate by tasks.registering(Exec::class) {
  workingDir(gitSrcDir)
  commandLine(BuildEnvironment.gitBinary, "fetch", "--all")
  dependsOn(srcClone)
}


fun srcPrepare(platform: PlatformNative<*>): Exec =
  tasks.create("srcPrepare${platform.name.toString().capitalize()}", Exec::class) {
    val srcDir = platform.srcDir
    dependsOn(srcUpdate)
    onlyIf { !srcDir.exists() }
    commandLine(BuildEnvironment.gitBinary, "clone", "--branch", GIT_TAG, gitSrcDir, srcDir)
  }



kotlin {
  linuxX64()
  linuxArm64()
  linuxArm32Hfp()
  macosArm64()
  macosX64()
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  targets.withType(KotlinNativeTarget::class).all {
    println("TARGET: $this")

  }
}