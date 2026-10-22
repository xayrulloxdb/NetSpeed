package uz.netspeed

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var toggle: Button
    private lateinit var batteryBtn: Button
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toggle = findViewById(R.id.toggle)
        batteryBtn = findViewById(R.id.battery)
        status = findViewById(R.id.status)

        toggle.setOnClickListener {
            if (SpeedService.isRunning) {
                Prefs.setEnabled(this, false)
                SpeedService.stop(this)
            } else {
                Prefs.setEnabled(this, true)
                SpeedService.start(this)
            }
            toggle.postDelayed({ refresh() }, 300)
        }

        batteryBtn.setOnClickListener { askIgnoreBatteryOptimization() }

        // Android 13+ да билдиришнома учун рухсат сўраймиз
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        } else if (!SpeedService.isRunning) {
            // Илова очилганда дарҳол ёқиб қўямиз
            Prefs.setEnabled(this, true)
            SpeedService.start(this)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        Prefs.setEnabled(this, true)
        SpeedService.start(this)
        toggle.postDelayed({ refresh() }, 300)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val running = SpeedService.isRunning
        toggle.text = getString(if (running) R.string.turn_off else R.string.turn_on)
        status.text = getString(if (running) R.string.status_on else R.string.status_off)

        val pm = getSystemService(PowerManager::class.java)
        val ignoring = pm.isIgnoringBatteryOptimizations(packageName)
        batteryBtn.isEnabled = !ignoring
        batteryBtn.text = getString(if (ignoring) R.string.battery_ok else R.string.battery_fix)
    }

    @SuppressLint("BatteryLife")
    private fun askIgnoreBatteryOptimization() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
