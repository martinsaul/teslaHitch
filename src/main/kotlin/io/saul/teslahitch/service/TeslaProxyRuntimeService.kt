package io.saul.teslahitch.service

import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Service
import java.io.*


@Service
class TeslaProxyRuntimeService {

    @PostConstruct
    fun init() {TeslaProxyRuntime().run()}

    class TeslaProxyRuntime : Runnable {

        val runtime: Runtime = Runtime.getRuntime()
        val process: Process

        public constructor() {
            this.process = runtime.exec(kotlin.arrayOf("ls", "-la"))
        }

        override fun run() {
            try {
//                process.waitFor() // Holds until execution termination.

                var line: String?

                // The logic below needs to be done in parallel.
                val error = BufferedReader(InputStreamReader(process.errorStream))
                while ((error.readLine().also { line = it }) != null) {
                    println(line)
                }
                error.close()

                val input = BufferedReader(InputStreamReader(process.inputStream))
                while ((input.readLine().also { line = it }) != null) {
                    println(line)
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
}