@file:Suppress("MemberVisibilityCanBePrivate")

import org.gradle.api.Project


//@file:Suppress("MemberVisibilityCanBePrivate")


object BuildVersion {


  var buildVersion = 0
  var buildVersionOffset: Int = 0
  var buildVersionFormat: String = "0.0.1-alpha%02d"


  val buildVersionName: String
    get() = buildVersionFormat.format(buildVersion)


  fun init(project: Project) {

    val getProjectProperty: (String, String) -> String = { key, defValue ->
      project.rootProject.findProperty(key)?.toString()?.trim() ?: run {
        project.rootProject.file("gradle.properties").appendText("\n$key=$defValue\n")
        defValue
      }
    }

    val snapshotVersion = getProjectProperty("build.snapshot", "true").toBoolean()
    val snapshotFormat = getProjectProperty("build.snapshot.format", "0.0.1-SNAPSHOT")

    buildVersion = getProjectProperty("build.version", "0").toInt()
    buildVersionFormat =
      if (snapshotVersion) snapshotFormat else
        getProjectProperty("build.version.format", "0.0.1-alpha%02d")
    buildVersionOffset = getProjectProperty("build.version.offset", "0").toInt()


    project.task("buildVersionIncrement") {
      doLast {
        val currentVersion = buildVersion
        project.rootProject.file("gradle.properties").readLines().map {
          if (it.contains("build.version=")) "build.version=${currentVersion + 1}"
          else it
        }.also { lines ->
          project.rootProject.file("gradle.properties").writer().use { writer ->
            lines.forEach {
              writer.write("$it\n")
            }
          }
        }
      }
      // buildVersionName()
      // nextBuildVersionName()
    }

    project.task("buildVersion") {
      doLast {
        println(buildVersion)
      }
    }

    project.task("buildVersionName") {
      doLast {
        println(buildVersionName)
      }
    }


    project.task("nextBuildVersionName") {
      doLast {
        println(buildVersionFormat.format(buildVersion + 1))
      }
    }


    project.task("printHostProperties") {
      doLast {
        System.getProperties().forEach { key, value ->
          println("PROP $key -> $value")
        }
      }
//os.name -> Mac OS X // Linux

      //os.arch -> x86_64
    }


  }
}
