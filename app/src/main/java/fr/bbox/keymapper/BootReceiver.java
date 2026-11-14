package fr.bbox.keymapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Lance le pont ADB local dès que le décodeur a démarré. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            context.startService(new Intent(context, AdbWatchService.class));
        }
    }
}
