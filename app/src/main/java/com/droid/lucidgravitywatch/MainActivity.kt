package com.droid.lucidgravitywatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import com.droid.lucidgravitywatch.network.OkHttpLucidGrpcTransport
import com.droid.lucidgravitywatch.repository.LucidCloudVehicleRepository
import com.droid.lucidgravitywatch.storage.SharedPreferencesSessionStore
import com.droid.lucidgravitywatch.theme.LucidGravityWatchTheme
import com.droid.lucidgravitywatch.ui.GravityWatchApp
import com.droid.lucidgravitywatch.viewmodel.GravityWatchViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GravityWatchViewModel by viewModels {
        val sessionStore = SharedPreferencesSessionStore(applicationContext)
        val transport = OkHttpLucidGrpcTransport()
        val repository = LucidCloudVehicleRepository(
            transport = transport,
            sessionStore = sessionStore,
            autoWakeSleepingVehicle = true
        )
        GravityWatchViewModel.Factory(repository)
    }

    private var ambientWakeLock: PowerManager.WakeLock? = null

    private val powerStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> syncInteractivePowerState(forceInteractive = true)
                Intent.ACTION_SCREEN_OFF -> viewModel.setInteractivePowerState(false)
                else -> syncInteractivePowerState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acquireAmbientWakeLock()
        setContent {
            LucidGravityWatchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GravityWatchApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(powerStateReceiver, filter)
        syncInteractivePowerState()
    }

    override fun onResume() {
        super.onResume()
        syncInteractivePowerState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        syncInteractivePowerState()
    }

    override fun onPause() {
        // Transition to Ambient power state; polling remains active in Ambient mode
        viewModel.setInteractivePowerState(false)
        super.onPause()
    }

    override fun onStop() {
        // Keep polling active in Ambient power state when the watch display dims/dozes
        viewModel.setInteractivePowerState(false)
        runCatching { unregisterReceiver(powerStateReceiver) }
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            viewModel.suspendPolling()
        }
        releaseAmbientWakeLock()
        super.onDestroy()
    }

    private fun acquireAmbientWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (ambientWakeLock == null) {
            ambientWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LucidCars::AmbientPollingWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        runCatching {
            if (ambientWakeLock?.isHeld == false) {
                ambientWakeLock?.acquire()
            }
        }
    }

    private fun releaseAmbientWakeLock() {
        runCatching {
            if (ambientWakeLock?.isHeld == true) {
                ambientWakeLock?.release()
            }
        }
    }

    private fun syncInteractivePowerState(forceInteractive: Boolean? = null) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenInteractive = forceInteractive ?: (powerManager?.isInteractive ?: true)
        val isActivityResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        viewModel.setInteractivePowerState(isActivityResumed && isScreenInteractive)
    }
}

