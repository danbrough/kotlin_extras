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
  get() = project.buildDir.resolve("curl/$version/$displayName")

val KonanTarget.prefixDir: File
  get() = project.file("lib/$displayName")


val gitSrcDir = project.buildDir.resolve("curl.git")
val srcTaskGroup = "Source"

val srcClone by tasks.registering(Exec::class) {
  commandLine(BuildEnvironment.gitBinary, "clone", "--bare", GIT_URL, gitSrcDir)
  outputs.dir(gitSrcDir)
  onlyIf { !gitSrcDir.exists() }
}


fun srcPrepare(target: KonanTarget): TaskProvider<Exec> {

  return tasks.register("srcPrepare${target.displayName.capitalize()}", Exec::class) {
    group = srcTaskGroup
    val srcDir = target.srcDir
    dependsOn(srcClone)
    onlyIf { !srcDir.exists() }
    commandLine(BuildEnvironment.gitBinary, "clone", "--branch", GIT_TAG, gitSrcDir, srcDir)

    finalizedBy("srcAutoconf${target.displayName.capitalizeAsciiOnly()}")
  }
}

fun srcAutoconf(target: KonanTarget): TaskProvider<Exec> {
  val srcPrepare = srcPrepare(target)

  return tasks.register("srcAutoconf${target.displayName.capitalize()}", Exec::class) {
    doFirst {
      println("running autoconf in ${target.srcDir}")
    }
    group = srcTaskGroup
    workingDir(target.srcDir)
    commandLine("autoreconf", "-fi")
    onlyIf {
      srcPrepare.get().didWork
    }
    dependsOn(srcPrepare)
  }
}

fun srcConfigure(target: KonanTarget): TaskProvider<Exec> {
  val srcAutoConf = srcAutoconf(target)
  return tasks.register<Exec>("configure${target.displayNameCapitalized}") {
    dependsOn(srcAutoConf)
    onlyIf { srcAutoConf.get().didWork }
    workingDir(target.srcDir)
    commandLine(
      """
./configure  --with-openssl --prefix=${target.prefixDir}  
--with-pic --enable-shared --enable-static --enable-libgcc --disable-dependency-tracking 
--disable-ftp --disable-gopher --disable-file --disable-imap --disable-ldap --disable-ldaps 
--disable-pop3 --disable-proxy --disable-rtsp --disable-smb --disable-smtp --disable-telnet --disable-tftp 
--without-gnutls --without-libidn --without-librtmp --disable-dic 
      """.trim().split("\\s+".toRegex())
    )
  }
}


fun compile(target: KonanTarget): TaskProvider<Exec> {
  val srcConfigure = srcConfigure(target)
  return tasks.register<Exec>("compile${target.displayNameCapitalized}") {
    dependsOn(srcConfigure)
    environment("MAKE" to "make -j3")
    workingDir(target.srcDir)
    onlyIf { !target.prefixDir.exists() }
    commandLine("make", "install")
  }
}


/*
    ./configure  --with-openssl --prefix=$CURL_DIR   \
--with-pic --enable-shared --enable-static --enable-libgcc --disable-dependency-tracking \
--disable-ftp --disable-gopher --disable-file --disable-imap --disable-ldap --disable-ldaps \
--disable-pop3 --disable-proxy --disable-rtsp --disable-smb --disable-smtp --disable-telnet --disable-tftp \
--without-gnutls --without-libidn --without-librtmp --disable-dict 2>&1 | tee configure.log*/

kotlin {
  linuxX64()
  linuxArm64()
  linuxArm32Hfp()
  macosArm64()
  macosX64()
  iosX64()
  iosArm64()
  iosSimulatorArm64()


  targets.withType<KotlinNativeTarget>().all {
    //println("Setting up target: ${konanTarget.displayNameCapitalized}")
    compile(konanTarget)
  }

}