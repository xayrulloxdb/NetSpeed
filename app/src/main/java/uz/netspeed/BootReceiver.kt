package uz.netspeed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Телефон қайта ёқилганда (ёки илова янгиланганда) ўзи ишга тушади. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.isEnabled(context)) {
            try {
                SpeedService.start(context)
            } catch (_: Exception) {
                // Баъзи ҳолатларда тизим рухсат бермаслиги мумкин — илова очилганда яна ёқилади
            }
        }
    }
}
