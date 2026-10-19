package com.healthylauncher.block

import android.util.Log

/** Instantiated inside Shizuku's process. Runs shell commands as the shell user. */
class ShellService() : IShellService.Stub() {
    override fun destroy() {
        System.exit(0)
    }

    override fun run(command: String): Int {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            Log.i(TAG, "$code $command $output")
            code
        } catch (error: Throwable) {
            Log.w(TAG, command, error)
            -1
        }
    }

    private companion object {
        const val TAG = "healthy-launcher"
    }
}
