import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  kotlin("multiplatform")
  id("common")
}



kotlin {

  jvm()

  linuxX64()
/*
  linuxArm32Hfp()
  linuxArm64()
  mingwX64()
*/

  if (System.getProperty("os.name").startsWith("Mac")) {
    macosX64()
    macosArm64()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()

    watchosArm64()
    watchosX64()
    watchosSimulatorArm64()

/*
addTarget(presets.iosArm64)
addTarget(presets.iosArm32)
addTarget(presets.iosX64)
addTarget(presets.macosX64)

addTarget(presets.tvosArm64)
addTarget(presets.tvosX64)
addTarget(presets.watchosArm32)
addTarget(presets.watchosArm64)
addTarget(presets.watchosX86)
addTarget(presets.watchosX64)

addTarget(presets.iosSimulatorArm64)
addTarget(presets.watchosSimulatorArm64)
addTarget(presets.tvosSimulatorArm64)
addTarget(presets.macosArm64)*/

  }


  val nativeTest by sourceSets.creating

  targets.withType(KotlinNativeTarget::class).all {

    compilations["test"].apply {
      defaultSourceSet {
        dependsOn(nativeTest)
      }
    }
  }

  sourceSets {
    commonMain {
      dependencies {
        implementation(Ktor.utils)
      }
    }

    commonTest {
      dependencies{
        implementation(kotlin("test"))
      }
    }
  }
}
