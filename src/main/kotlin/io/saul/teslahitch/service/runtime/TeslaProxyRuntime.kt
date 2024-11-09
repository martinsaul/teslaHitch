package io.saul.teslahitch.service.runtime

import org.slf4j.LoggerFactory
import java.io.*

class TeslaProxyRuntime : Runnable {
    private val logger = LoggerFactory.getLogger(TeslaProxyRuntime::class.java)

    val runtime: Runtime = Runtime.getRuntime()
    val process: Process

    constructor() {
        val arrayOf = arrayOf(
            "./tesla/tesla-control",
            "-tls-key config/tls-key.pem",
            "-cert config/tls-cert.pem",
            "-key-file config/fleet-key.pem",
            "-port 4443"
        )
        val arrayOf1 = arrayOf("tree", "./tesla/")
        this.process = runtime.exec(arrayOf("tree", "/var/keys/"))
    }

    override fun run() {
        try {
//                process.waitFor() // Holds until execution termination.

            var line: String?

            // The logic below needs to be done in parallel.
            val error = BufferedReader(InputStreamReader(process.errorStream))
            while ((error.readLine().also { line = it }) != null) {
                logger.info(line)
            }
            error.close()

            val input = BufferedReader(InputStreamReader(process.inputStream))
            while ((input.readLine().also { line = it }) != null) {
                logger.error(line)
            }

            input.close()

            val outputStream: OutputStream = process.outputStream
            val printStream = PrintStream(outputStream)
            printStream.println()
            printStream.flush()
            printStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}