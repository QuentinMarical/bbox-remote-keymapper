package fr.bbox.keymapper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

import fr.bbox.keymapper.adb.AdbConnection;

/**
 * Se connecte au démon ADB du décodeur (127.0.0.1:5555), lit les touches brutes
 * de la télécommande Bbox et envoie le code Android correspondant.
 * Aucun PC n'est nécessaire.
 */
public class AdbWatchService extends Service {

    private static final String TAG = "AdbWatchService";
    private static final int ADB_PORT = 5555;
    /** Nom du périphérique de la télécommande Bbox ; le numéro /dev/input/eventN change au redémarrage. */
    private static final String REMOTE_NAME = "Telecommande";

    /** Marqueur : la touche lance l'application choisie par l'utilisateur. */
    public static final String APP = "APP";

    /**
     * Touches de la télécommande Bbox : code scan (hex), libellé affiché, commande shell.
     * VOD passe par le nom KEYCODE_SDMCEXTEND6 (le code numérique 2010 n'est pas traité par GlobalKey).
     * Netflix est lancé directement par son paquet, car GlobalKey ne le fait pas pour cette télécommande.
     */
    public static final String[][] BUTTONS = {
            {"00ba", "VOD", "input keyevent KEYCODE_SDMCEXTEND6"},
            {"0001", "Retour", "input keyevent KEYCODE_BACK"},
            {"0066", "Accueil", "input keyevent KEYCODE_HOME"},
            {"00b7", "TV", APP},
            {"0082", "Menu", APP},
            {"00bb", "Favoris", APP},
    };

    /** Application par défaut des touches de type APP. */
    private static final Map<String, String> DEFAULT_APPS = new HashMap<>();

    static {
        DEFAULT_APPS.put("0082", "com.google.android.youtube.tv");
        DEFAULT_APPS.put("00bb", "com.netflix.ninja");
    }

    /** Action d'origine d'une touche APP, utilisée tant que l'utilisateur n'a pas choisi d'application. */
    public static final Map<String, String> ORIGINAL_COMMANDS = new HashMap<>();

    static {
        ORIGINAL_COMMANDS.put("00b7", "input keyevent KEYCODE_SDMCEXTEND2");
    }

    private static final String PREFS = "touches";

    private static final Map<String, String> MAPPINGS = new HashMap<>();

    static {
        for (String[] button : BUTTONS) {
            MAPPINGS.put(button[0], button[2]);
        }
    }

    /** Paquet choisi pour une touche APP, ou son défaut. */
    public static String appFor(android.content.Context context, String scan) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE_VALUE)
                .getString("app_" + scan, DEFAULT_APPS.get(scan));
    }

    /** Enregistre le paquet choisi pour une touche APP. */
    public static void setApp(android.content.Context context, String scan, String packageName) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE_VALUE)
                .edit().putString("app_" + scan, packageName).apply();
    }

    private static final int MODE_PRIVATE_VALUE = android.content.Context.MODE_PRIVATE;

    private static final int LOCAL_GETEVENT = 1;

    private Thread worker;
    private volatile boolean running;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (worker == null) {
            running = true;
            worker = new Thread(this::loop, "adb-watch");
            worker.start();
        }
        return START_STICKY;
    }

    /**
     * Service au premier plan : sans cela, Android arrête le service quand l'application est inactive.
     */
    private void startAsForeground() {
        String channelId = "adb_watch";
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(
                    channelId, "Pont ADB local", NotificationManager.IMPORTANCE_LOW));
            builder = new Notification.Builder(this, channelId);
        } else {
            builder = new Notification.Builder(this);
        }
        Notification notification = builder
                .setContentTitle("Bbox Remote KeyMapper")
                .setContentText("Pont ADB actif (VOD → REPLAY)")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void loop() {
        while (running) {
            try {
                KeyPair keys = AdbConnection.loadOrCreateKeys(getFilesDir());
                AdbConnection conn = new AdbConnection("127.0.0.1", ADB_PORT, keys);
                try {
                    conn.handshake();
                    Log.i(TAG, "Connecté au démon ADB local");
                    String path = findRemotePath(conn);
                    if (path == null) {
                        throw new java.io.IOException("Télécommande " + REMOTE_NAME + " introuvable");
                    }
                    Log.i(TAG, "Télécommande trouvée sur " + path);
                    conn.open(LOCAL_GETEVENT, "shell:getevent " + path);
                    readEvents(conn);
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                // Juste après le démarrage, la télécommande peut apparaître quelques secondes plus tard : on réessaie vite.
                boolean remoteMissing = e.getMessage() != null && e.getMessage().contains("introuvable");
                long delay = remoteMissing ? 3000 : 10000;
                Log.w(TAG, "Connexion ADB impossible, nouvel essai dans " + delay / 1000 + " s : " + e.getMessage());
                sleep(delay);
                continue;
            }
            sleep(10000);
        }
    }

    /** Interroge `getevent -p` et renvoie le chemin du périphérique nommé REMOTE_NAME, ou null. */
    private String findRemotePath(AdbConnection conn) throws Exception {
        final int probe = LOCAL_GETEVENT;
        conn.open(probe, "shell:getevent -p");
        StringBuilder out = new StringBuilder();
        while (true) {
            AdbConnection.Message m = conn.read();
            if (m.command == 0x45545257 && m.arg1 == probe) { // WRTE
                conn.ack(probe, m.arg0);
                out.append(new String(m.data, "UTF-8"));
            } else if (m.command == 0x45534c43 && m.arg1 == probe) { // CLSE
                conn.close(probe, m.arg0);
                break;
            }
        }
        String currentPath = null;
        for (String line : out.toString().split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("add device")) {
                currentPath = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            } else if (trimmed.startsWith("name:") && trimmed.contains("\"" + REMOTE_NAME + "\"")) {
                return currentPath;
            }
        }
        return null;
    }

    private void readEvents(AdbConnection conn) throws Exception {
        int nextLocalId = 2;
        StringBuilder pending = new StringBuilder();
        while (running) {
            AdbConnection.Message m = conn.read();
            switch (m.command) {
                case 0x45545257: // WRTE : données d'un flux du décodeur
                    conn.ack(m.arg1, m.arg0);
                    if (m.arg1 == LOCAL_GETEVENT) {
                        pending.append(new String(m.data, "UTF-8"));
                        int end;
                        while ((end = pending.indexOf("\n")) >= 0) {
                            String line = pending.substring(0, end);
                            pending.delete(0, end + 1);
                            onEventLine(conn, line, nextLocalId);
                            nextLocalId++;
                        }
                    }
                    break;
                case 0x45534c43: // CLSE : le décodeur ferme un flux
                    conn.close(m.arg1, m.arg0);
                    if (m.arg1 == LOCAL_GETEVENT) {
                        // Le flux de la télécommande est fermé : il faut se reconnecter.
                        throw new java.io.IOException("Flux getevent fermé");
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /** Format getevent : type code valeur (hex). 0001 = EV_KEY, 00000001 = appui. */
    private void onEventLine(AdbConnection conn, String line, int localId) throws Exception {
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 3 || !"0001".equals(parts[0]) || !"00000001".equals(parts[2])) {
            return;
        }
        String command = MAPPINGS.get(parts[1]);
        if (APP.equals(command)) {
            String packageName = appFor(this, parts[1]);
            if (packageName == null || packageName.isEmpty()) {
                command = ORIGINAL_COMMANDS.get(parts[1]);
            } else {
                command = "monkey -p " + packageName + " -c " + launcherCategory(packageName) + " 1";
            }
        }
        if (command == null) {
            return;
        }
        Log.i(TAG, "Touche scan " + parts[1] + " -> " + command);
        conn.open(localId, "shell:" + command);
    }

    /** Catégorie de lancement : LEANBACK_LAUNCHER si l'application en a une, sinon LAUNCHER. */
    private String launcherCategory(String packageName) {
        android.content.Intent leanback = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory("android.intent.category.LEANBACK_LAUNCHER")
                .setPackage(packageName);
        boolean hasLeanback = !getPackageManager().queryIntentActivities(leanback, 0).isEmpty();
        return hasLeanback ? "android.intent.category.LEANBACK_LAUNCHER" : "android.intent.category.LAUNCHER";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
