fun main() {
    val bytes = 1024L
    if (bytes < 1024) {
        println("$bytes B")
    } else {
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        println(String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre))
    }
}
