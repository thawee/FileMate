import kotlinx.coroutines.*

fun main() {
    val scope = CoroutineScope(Dispatchers.Default)
    println(scope.isActive)
}
