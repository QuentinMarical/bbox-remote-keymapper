package fr.bbox.keymapper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Écran d'accueil : le pont ADB démarre tout seul. On voit ici les touches reliées,
 * et on peut changer l'application ouverte par YouTube et Netflix.
 */
public class MainActivity extends Activity {

    private static final int BLUE = 0xFF0055A4;
    private static final int ORANGE_RED = 0xFFE4572E;
    private static final int BACKGROUND = 0xFF0E1217;
    private static final int CARD = 0xFF1C2430;
    private static final int MUTED = 0xFFAEB8C4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, AdbWatchService.class));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(BACKGROUND);
        content.setPadding(0, 0, 0, dp(32));

        content.addView(header());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(32), dp(24), dp(32), 0);
        content.addView(body);

        body.addView(statusCard());
        body.addView(restartButton());

        body.addView(sectionTitle("Touches reliées"));
        for (String[] button : AdbWatchService.BUTTONS) {
            body.addView(buttonRow(button));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
    }

    /** Bandeau en dégradé bleu vers orangé, aux couleurs de l'icône. */
    private LinearLayout header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(32), dp(32), dp(32), dp(24));
        header.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{BLUE, ORANGE_RED}));

        TextView title = text(30, getString(R.string.app_name), Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        header.addView(title);

        header.addView(text(18, "Télécommande Bbox Miami sur SFR Connect TV", 0xE6FFFFFF));
        return header;
    }

    private LinearLayout statusCard() {
        LinearLayout card = card();
        card.addView(text(20, "Pont ADB : actif", Color.WHITE));
        card.addView(text(16, "Il démarre tout seul au démarrage du décodeur.", MUTED));
        return card;
    }

    private Button restartButton() {
        Button button = gradientButton("Relancer le pont");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(16), 0, dp(8));
        button.setLayoutParams(params);
        button.setOnClickListener(v -> startService(new Intent(this, AdbWatchService.class)));
        return button;
    }

    /** Une ligne par touche ; les touches à choisir ont un bouton « Changer ». */
    private LinearLayout buttonRow(String[] button) {
        boolean choosable = AdbWatchService.APP.equals(button[2]);

        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View icon = iconFor(button[0]);
        if (icon != null) {
            row.addView(icon);
        }

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        names.addView(text(22, button[1], Color.WHITE));

        if (choosable) {
            TextView action = text(16, "Ouvre : " + targetOf(button[0]), MUTED);
            names.addView(action);
            row.addView(names);

            Button change = gradientButton("Changer");
            change.setOnClickListener(v -> pickApp(button[0], button[1], action));
            row.addView(change);
        } else {
            names.addView(text(16, "Action : " + button[2], MUTED));
            row.addView(names);
        }

        TextView code = text(16, "scan " + Integer.parseInt(button[0], 16), ORANGE_RED);
        code.setGravity(Gravity.END);
        code.setPadding(dp(16), 0, 0, 0);
        row.addView(code);
        return row;
    }

    /** Icône d'une touche : un cœur pour Favoris, trois carrés empilés verticalement pour Menu. */
    private View iconFor(String scan) {
        if ("00bb".equals(scan)) {
            ImageView heart = new ImageView(this);
            heart.setImageResource(R.drawable.ic_favoris);
            heart.setLayoutParams(iconParams(dp(40), dp(40)));
            return heart;
        }
        if ("0082".equals(scan)) {
            LinearLayout stack = new LinearLayout(this);
            stack.setOrientation(LinearLayout.VERTICAL);
            stack.setGravity(Gravity.CENTER);
            for (int i = 0; i < 3; i++) {
                View square = new View(this);
                square.setBackgroundColor(Color.WHITE);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(10), dp(10));
                params.setMargins(0, i == 0 ? 0 : dp(3), 0, 0);
                square.setLayoutParams(params);
                stack.addView(square);
            }
            stack.setLayoutParams(iconParams(dp(40), dp(40)));
            return stack;
        }
        if ("00b7".equals(scan)) {
            ImageView tv = new ImageView(this);
            tv.setImageResource(R.drawable.ic_tv);
            tv.setLayoutParams(iconParams(dp(40), dp(40)));
            return tv;
        }
        return null;
    }

    private LinearLayout.LayoutParams iconParams(int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(0, 0, dp(20), 0);
        return params;
    }

    /** Sélecteur des applications installées par l'utilisateur (ni système, ni préinstallées). */
    private void pickApp(String scan, String touche, TextView actionView) {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String category : new String[]{"android.intent.category.LAUNCHER",
                "android.intent.category.LEANBACK_LAUNCHER"}) {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo info : pm.queryIntentActivities(main, 0)) {
                ApplicationInfo app = info.activityInfo.applicationInfo;
                boolean self = app.packageName.equals(getPackageName());
                if (!self && seen.add(app.packageName)) {
                    apps.add(new String[]{pm.getApplicationLabel(app).toString(), app.packageName});
                }
            }
        }
        Collections.sort(apps, Comparator.comparing(a -> a[0].toLowerCase()));
        if (AdbWatchService.ORIGINAL_COMMANDS.containsKey(scan)) {
            // Action d'origine en tête : son paquet vide signifie « pas d'application ».
            apps.add(0, new String[]{"BOUTON TV du SFR ConnectTV", ""});
        }

        String[] labels = new String[apps.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = apps.get(i)[0];
        }

        new AlertDialog.Builder(this)
                .setTitle("Quelle application pour " + touche + " ?")
                .setItems(labels, (dialog, which) -> {
                    String packageName = apps.get(which)[1];
                    AdbWatchService.setApp(this, scan, packageName);
                    actionView.setText("Ouvre : " + targetOf(scan));
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /** Ce que la touche ouvre aujourd'hui : une application choisie, ou l'action d'origine. */
    private String targetOf(String scan) {
        String packageName = AdbWatchService.appFor(this, scan);
        if (packageName == null || packageName.isEmpty()) {
            return AdbWatchService.ORIGINAL_COMMANDS.containsKey(scan) ? "BOUTON TV du SFR ConnectTV" : "rien";
        }
        return labelOf(packageName);
    }

    /** Nom affiché d'une application, ou son paquet si elle n'est plus installée. */
    private String labelOf(String packageName) {
        if (packageName == null) {
            return "rien";
        }
        try {
            ApplicationInfo app = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(app).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    /** Bouton plein en dégradé, avec un contour orange quand il a le focus de la télécommande. */
    private Button gradientButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setFocusable(true);
        GradientDrawable normal = rounded(BLUE, ORANGE_RED, 12);
        GradientDrawable focused = rounded(BLUE, ORANGE_RED, 12);
        focused.setStroke(dp(4), Color.WHITE);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        button.setBackground(states);
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);

        // Contour orange quand la carte a le focus (utile pour la télécommande).
        GradientDrawable normal = rounded(CARD, CARD, 16);
        GradientDrawable focused = rounded(CARD, CARD, 16);
        focused.setStroke(dp(4), ORANGE_RED);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, normal);
        card.setBackground(states);
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(22, value, MUTED);
        view.setTypeface(null, Typeface.BOLD);
        view.setPadding(0, dp(20), 0, dp(8));
        return view;
    }

    private GradientDrawable rounded(int start, int end, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private TextView text(int sp, String value, int color) {
        TextView view = new TextView(this);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setPadding(0, dp(4), 0, dp(4));
        view.setText(value);
        return view;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
