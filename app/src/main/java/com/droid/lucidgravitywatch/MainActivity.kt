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
        // Still visible but not interactive (e.g. display dimming): poll slowly until stopped
        viewModel.setInteractivePowerState(false)
        super.onPause()
    }

    override fun onStop() {
        // Not visible any more (backgrounded, or the watch returned to its face): stop polling so a
        // hidden app never spends the watch's battery or Lucid's rate limit.
        viewModel.suspendPolling()
        runCatching { unregisterReceiver(powerStateReceiver) }
        super.onStop()
    }

    private fun syncInteractivePowerState(forceInteractive: Boolean? = null) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isScreenInteractive = forceInteractive ?: (powerManager?.isInteractive ?: true)
        val isActivityResumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        viewModel.setInteractivePowerState(isActivityResumed && isScreenInteractive)
    }
}

