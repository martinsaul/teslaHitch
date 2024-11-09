package io.saul.teslahitch.service.runtime

import org.slf4j.LoggerFactory
import java.io.*

class TeslaKeyGenRuntime : Runnable {
    private val logger = LoggerFactory.getLogger(TeslaKeyGenRuntime::class.java)

    val runtime: Runtime = Runtime.getRuntime()
    val process: Process

    constructor() {
        this.process = runtime.exec(arrayOf("./tesla/tesla-keygen", "-output", "/var/keys/dummy.pem", "-key-name", "name", "-keyring-type", "file", "create"))
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