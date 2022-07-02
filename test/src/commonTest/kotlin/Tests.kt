import io.ktor.util.*
import kotlin.test.Test

class Tests {
  @Test
  fun test() {
    println("Test worked!!")
    val bytes = (0..256).map { it.toByte() }.toByteArray().encodeBase64().also {
      println("BYRTES: $it")
    }
    "123".encodeBase64().also {
      println("DATA $it")
    }
  }
}