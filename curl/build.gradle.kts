

//see: https://stackoverflow.com/questions/65397852/how-to-build-openssl-for-ios-and-osx

plugins {
  kotlin("multiplatform")
  id("common")
}


val GIT_TAG = "curl-7_82_0"
val GIT_URL="https://github.com/curl/curl.git"

group = "org.danbrough"
version = GIT_TAG.substringAfter('_').replace('_', '.')


val gitSrcDir = project.file("src/curl.git")

val srcClone by tasks.registering(Exec::class) {
  commandLine(BuildEnvironment.gitBinary, "clone", "--bare", GIT_URL, gitSrcDir)
  outputs.dir(gitSrcDir)
  onlyIf { !gitSrcDir.exists() }
}

/*
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

*/


kotlin {
  jvm()
}