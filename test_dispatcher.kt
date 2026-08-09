import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

fun main() = runBlocking {
    val proxyDispatcher = Dispatchers.IO
    val scope = CoroutineScope(proxyDispatcher + SupervisorJob())
    
    var started = false
    scope.launch {
        started = true
        println("Coroutine runs!")
    }.join()
    
    println("Started: $started")
    // Clean up proxy dispatcher if needed, but not required for IO.
}
