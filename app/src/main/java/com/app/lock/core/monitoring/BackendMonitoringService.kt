package com.app.lock.core.monitoring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.app.lock.core.utils.appLockRepository
import com.app.lock.data.repository.BackendImplementation
import com.app.lock.services.ExperimentalAppLockService

class BackendMonitoringService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var monitoringRunnable: Runnable? = null
    private val monitoringInterval = 5000L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoring()
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    private fun startMonitoring() {
        Log.d("BackendMonitor", "Starting backend monitoring")

        monitoringRunnable = object : Runnable {
            override fun run() {
                try {
                    checkAndSwitchBackend()
                } catch (e: Exception) {
                    Log.e("BackendMonitor", "Error during backend check", e)
                }

                handler.postDelayed(this, monitoringInterval)
            }
        }

        handler.post(monitoringRunnable!!)
    }

    private fun stopMonitoring() {
        Log.d("BackendMonitor", "Stopping backend monitoring")
        monitoringRunnable?.let { handler.removeCallbacks(it) }
        monitoringRunnable = null
    }

    private fun checkAndSwitchBackend() {
        val appLockRepository = applicationContext.appLockRepository()
        val currentActive = appLockRepository.getActiveBackend()

        val newActiveBackend = appLockRepository.validateAndSwitchBackend(applicationContext)

        if (newActiveBackend != currentActive) {
            Log.i("BackendMonitor", "Backend switched from $currentActive to $newActiveBackend")
            handleBackendSwitch(currentActive!!, newActiveBackend)
        }
    }

    private fun handleBackendSwitch(
        oldBackend: BackendImplementation,
        newBackend: BackendImplementation
    ) {
        Log.d("BackendMonitor", "Switching from $oldBackend to $newBackend")

        stopAllServices()

        startBackendService(newBackend)
    }

    private fun stopAllServices() {
        Log.d("BackendMonitor", "Stopping all app lock services")
        stopService(Intent(this, ExperimentalAppLockService::class.java))
        Log.d("BackendMonitor", "All stoppable services have been stopped")
    }

    private fun stopBackendService(backend: BackendImplementation) {
        when (backend) {
            BackendImplementation.USAGE_STATS -> {
                Log.d("BackendMonitor", "Stopping ExperimentalAppLockService")
                stopService(Intent(this, ExperimentalAppLockService::class.java))
            }

            BackendImplementation.ACCESSIBILITY -> {
                Log.d("BackendMonitor", "Cannot stop accessibility service programmatically")
            }
        }
    }

    private fun startBackendService(backend: BackendImplementation) {
        when (backend) {
            BackendImplementation.USAGE_STATS -> {
                Log.d("BackendMonitor", "Starting ExperimentalAppLockService")
                startService(Intent(this, ExperimentalAppLockService::class.java))
            }

            BackendImplementation.ACCESSIBILITY -> {
                Log.d("BackendMonitor", "Accessibility service managed by system")
            }
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, BackendMonitoringService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackendMonitoringService::class.java)
            context.stopService(intent)
        }
    }
}
