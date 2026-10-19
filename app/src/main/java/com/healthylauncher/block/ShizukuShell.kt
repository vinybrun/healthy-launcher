package com.healthylauncher.block

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.healthylauncher.BuildConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import rikka.shizuku.Shizuku

object ShizukuShell {
    @Volatile private var appContext: Context? = null
    private val service = AtomicReference<IShellService?>(null)

    fun install(context: Context) {
        appContext = context.applicationContext
    }

    fun ready(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (error: Throwable) {
            Log.d(TAG, "shizuku unavailable: ${error.message}")
            false
        }
    }

    fun run(command: String): Int {
        val shell = binder() ?: return -1
        return try {
            shell.run(command)
        } catch (error: Throwable) {
            Log.w(TAG, "shizuku command failed: $command", error)
            service.set(null)
            -1
        }
    }

    private fun binder(): IShellService? {
        service.get()?.let { return it }
        if (!ready()) return null
        val context = appContext ?: return null
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(1)
        val latch = CountDownLatch(1)
        val bound = AtomicReference<IShellService?>(null)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                bound.set(IShellService.Stub.asInterface(binder))
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service.set(null)
            }
        }
        return try {
            Shizuku.bindUserService(args, connection)
            if (!latch.await(8, TimeUnit.SECONDS)) {
                Log.w(TAG, "shizuku bind timed out")
                return null
            }
            val value = bound.get()
            service.set(value)
            value
        } catch (error: Throwable) {
            Log.w(TAG, "shizuku bind failed", error)
            null
        }
    }

    private const val TAG = "healthy-launcher"
}
