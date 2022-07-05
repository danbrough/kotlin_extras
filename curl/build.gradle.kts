import BuildEnvironment.buildEnvironment
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

//see: https://stackoverflow.com/questions/65397852/how-to-build-openssl-for-ios-and-osx

plugins {
  kotlin("multiplatform")
  id("common")
}


val GIT_TAG = "curl-7_84_0"
val GIT_URL = "https://github.com/curl/curl.git"

group = "org.danbrough"
version = GIT_TAG.substringAfter('-').replace('_', '.')

val KonanTarget.srcDir: File
  get() = project.buildDir.resolve("src/$version/$displayName")

val KonanTarget.prefixDir: File
  get() = project.file("lib/$displayName")

val gitSrcDir = project.buildDir.resolve("curl.git")
val srcTaskGroup = "Source"

val srcClone by tasks.registering(Exec::class) {
  commandLine(BuildEnvironment.gitBinary, "clone", "--bare", GIT_URL, gitSrcDir)
  outputs.dir(gitSrcDir)
  onlyIf { !gitSrcDir.exists() }
}

fun srcPrepare(target: KonanTarget): TaskProvider<Exec> =
  tasks.register("srcPrepare${target.displayName.capitalize()}", Exec::class) {
    group = srcTaskGroup
    val srcDir = target.srcDir
    dependsOn(srcClone)
    outputs.dir(target.srcDir)
    commandLine(BuildEnvironment.gitBinary, "clone", "--branch", GIT_TAG, gitSrcDir, srcDir)
  }


fun srcAutoconf(target: KonanTarget): TaskProvider<Exec> {
  val srcPrepare = srcPrepare(target)

  return tasks.register("srcAutoconf${target.displayName.capitalize()}", Exec::class) {
    outputs.file(target.srcDir.resolve("configure"))
    group = srcTaskGroup
    dependsOn(srcPrepare)
    workingDir(target.srcDir)
    commandLine("autoreconf", "-fi")
  }
}

fun srcConfigure(target: KonanTarget): TaskProvider<Exec> {
  val srcAutoConf = srcAutoconf(target)
  return tasks.register<Exec>("configure${target.displayNameCapitalized}") {
    val openSSLTask = tasks.getByPath(":openssl:compile${target.displayNameCapitalized}")
    val openSSLDir = openSSLTask.outputs.files.files.first().parentFile.parentFile
    dependsOn(srcAutoConf, openSSLTask)
    group = srcTaskGroup
    outputs.file(target.srcDir.resolve("Makefile"))
    workingDir(target.srcDir)
    environment(target.buildEnvironment)

    val command = """
./configure --with-openssl=${openSSLDir} --prefix=${target.prefixDir}  
--with-pic --enable-shared --enable-static 
--disable-ftp --disable-gopher --disable-file --disable-imap --disable-ldap --disable-ldaps 
--disable-pop3 --disable-proxy --disable-rtsp --disable-smb --disable-smtp --disable-telnet --disable-tftp 
--without-gnutls 
      """.trim()
    doFirst {
      println("running command: $command")
    }

    commandLine(command.split("\\s+".toRegex()))
  }
}

fun compileTask(target: KonanTarget): TaskProvider<Exec> {
  val srcConfigure = srcConfigure(target)
  return tasks.register<Exec>("compile${target.displayNameCapitalized}") {
    environment(target.buildEnvironment)
    workingDir(target.srcDir)
    outputs.file(target.prefixDir.resolve("lib/libcurl.a"))
    isEnabled = false
    if (!target.prefixDir.resolve("lib/libcurl.a").exists()) {
      dependsOn(srcConfigure)
      isEnabled = true
    }

    commandLine("make", "install")
    doLast {
      println("FINISHED .. getting executing result ..")
      executionResult.get().also {
        println("EXEC RESULT: $it")
        if (it.exitValue == 0 && target.srcDir.exists()) {
          println("DELETING: ${target.srcDir}")
          target.srcDir.deleteRecursively()
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
    macosArm64()
  }

  androidNativeArm32()
  androidNativeArm64()
  androidNativeX86()
  androidNativeX64()

  val compileAll by tasks.creating {
    group = BasePlugin.BUILD_GROUP
    description = "Builds curl for all available targets"
  }

  targets.withType<KotlinNativeTarget>().all {
    compileTask(konanTarget).also {
      compileAll.dependsOn(it)
    }
  }

}