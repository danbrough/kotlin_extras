pluginManagement {

  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
  }
}



plugins {
  id("de.fayard.refreshVersions") version "0.40.2"
}


rootProject.name = "kotlin_extras"

include(":openssl")


include(":test")
