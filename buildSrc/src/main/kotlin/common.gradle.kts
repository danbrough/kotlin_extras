import groovy.lang.Closure
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.kotlin.dsl.support.serviceOf
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetPreset
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.KonanTarget


/*
object GoLib {


  fun <T : KotlinNativeTarget> Project.registerGoLibBuild(
    platform: PlatformNative<T>,
    goDir: File,
    outputDir: File,
    libBaseName: String,
    modules: String = ".",
    name: String = "golibBuild${platform.name.toString().capitalized()}"
  ): TaskProvider<GoLibBuildTask<T>> =
    tasks.register(name, platform, goDir, outputDir, libBaseName, modules)


  fun Project.libsDir(platform: PlatformNative<*>): File =
    rootProject.file("golib/build/lib/${platform.name}")


}*/


abstract class GreetingTask : DefaultTask() {
  @get:Input
  @get:Optional
  abstract val greeting: Property<String?>

  @TaskAction
  fun greet() {
    println("hello from GreetingTask: ${greeting.orNull}")
  }
}



/*
tasks.register("styleTest") {
  doLast {
    val out = project.serviceOf<StyledTextOutputFactory>().create("testOutput")
    StyledTextOutput.Style.values().forEach {
      out.style(it).println("This line has the style: $it")

      if (hasProperty("message")) {
        out.style(it).println("The message is: ${property("message")}")
      }
    }
  }
}
*/



abstract class GoLibBuildTask<T : KotlinNativeTarget> @Inject constructor(
  private val target: KonanTarget,
  private val goDir: File,
  private val outputDir: File,
  private val outputBaseName: String,
  private val modules: String = ","
) : Exec() {


  init {
    group = BasePlugin.BUILD_GROUP
    // println("PLATFORM $platform  godir: $goDir: libDir: ${libDir.orNull}")

    environment("PLATFORM", target.displayName)


    inputs.files(project.fileTree(goDir) {
      include("**/*.go")
      include("**/*.c")
      include("**/*.h")
      include("**/*.mod")
    })


    val libFile = outputDir.resolve("lib${outputBaseName}.so")
    val headerFile = outputDir.resolve("lib${outputBaseName}.so")
    outputs.files(libFile, headerFile)


    workingDir(goDir)

    val commandEnvironment = BuildEnvironment.buildEnvironment(target)
    environment(commandEnvironment)

    commandLine(
      listOf(
        BuildEnvironment.goBinary, "build", "-v",//"-x",
        "-trimpath", "-buildmode=c-shared", "-o", libFile, modules
      )
    )


    val out = project.serviceOf<StyledTextOutputFactory>().create("golibOutput")

    doFirst {
      out.style(StyledTextOutput.Style.Info).println("Building golib for $this")
      out.style(StyledTextOutput.Style.ProgressStatus).println("environment: $commandEnvironment")
      out.style(StyledTextOutput.Style.ProgressStatus)
        .println("commandLine: ${commandLine.joinToString(" ")}")
    }
    doLast {
      if (didWork) out.style(StyledTextOutput.Style.Success)
        .println("Finished building golib for $this")
    }
  }


  fun appendToEnvironment(key: String, value: String, separator: String = " ") {
    environment(key, environment.getOrDefault(key, null).let {
      if (it == null) value else "$it$separator$value"
    })
  }

}


