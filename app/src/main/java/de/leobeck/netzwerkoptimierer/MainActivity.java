package de.leobeck.netzwerkoptimierer;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import java.text.DateFormat;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Color;
import android.util.Log;
import android.os.Process;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private WifiManager wifi;
    private TextView report;
    private Spinner floorPicker;
    private boolean meshMode = false;
    private boolean channelMode = false;
    private final int permissionRequest = 42;

    @Override public void onCreate(Bundle state) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                getSharedPreferences("diagnostics", MODE_PRIVATE).edit()
                    .putString("lastCrash", Log.getStackTraceString(error)).commit();
            } catch (Throwable ignored) {}
            Process.killProcess(Process.myPid());
        });
        super.onCreate(state);
        try { initializeUi(); }
        catch (Throwable error) { showFailure("Startfehler", error); }
    }

    /** Compact, readable controls that do not force uppercase labels. */
    private void addCompactButton(LinearLayout root, Button button) {
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(24, 100, 174)));
        button.setElevation(5f);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(18, 15, 18, 15);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 5, 0, 5);
        root.addView(button, params);
    }

    private void addGridButton(android.widget.GridLayout grid, Button button, int row, int column, int color) {
        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int cellWidth = (screenWidth - (int)(60 * density)) / 2;
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setGravity(android.view.Gravity.CENTER);
        button.setMaxLines(3);
        button.setIncludeFontPadding(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding((int)(6*density), (int)(8*density), (int)(6*density), (int)(8*density));
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(4 * density);
        button.setBackground(shape);
        android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams(
            android.widget.GridLayout.spec(row, android.widget.GridLayout.FILL), android.widget.GridLayout.spec(column, android.widget.GridLayout.FILL));
        params.setGravity(android.view.Gravity.FILL);
        params.width = cellWidth;
        params.height = (int)(92 * density);
        params.setMargins((int)(3*density), (int)(5*density), (int)(3*density), (int)(5*density));
        grid.addView(button, params);
    }

    private void setActiveAction(Button active, Button profile, Button scan, Button mesh, Button channels) {
        Button[] buttons = {profile, scan, mesh, channels};
        int[] colors = {Color.rgb(166,91,16), Color.rgb(0,116,204), Color.rgb(104,55,174), Color.rgb(0,132,91)};
        String[] labels = {"Deco-Analyseprofil einstellen", "WLAN jetzt messen", "Mesh und Empfang bewerten", "Kanalbelegung analysieren"};
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < buttons.length; i++) {
            Button button = buttons[i];
            boolean selected = button == active;
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(colors[i]);
            bg.setCornerRadius(4 * density);
            bg.setStroke((int)((selected ? 4 : 1) * density), selected ? Color.WHITE : colors[i]);
            button.setBackground(bg);
            button.setText((selected ? "✓ AKTIV\n" : "") + labels[i]);
            button.setTextSize(selected ? 14 : 15);
            button.setSelected(selected);
        }
    }

    private TextView makeLamp(String label, int color) {
        TextView lamp = new TextView(this);
        lamp.setText(label);
        lamp.setTextSize(16);
        lamp.setTypeface(null, android.graphics.Typeface.BOLD);
        lamp.setTextColor(color);
        lamp.setGravity(android.view.Gravity.CENTER);
        return lamp;
    }

    private void initializeUi() {
        wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setPadding(22, 28, 22, 56);
        root.setBackgroundColor(Color.rgb(12, 22, 37));
        TextView title = new TextView(this);
        title.setText("Mein Netzwerk-Optimierer");
        title.setTextSize(25); title.setTypeface(null, android.graphics.Typeface.BOLD); title.setTextColor(Color.WHITE);
        root.addView(title);
        TextView info = new TextView(this);
        info.setText("WLAN-Analyse und Empfehlungen – ohne Werbung");
        info.setTextColor(Color.rgb(175, 198, 224));
        info.setPadding(0,12,0,8); root.addView(info);
        TextView buildLabel = new TextView(this);
        buildLabel.setText("Version 2.5 · Lesbarkeit & aktive Analyse");
        buildLabel.setTextSize(12);
        buildLabel.setTextColor(Color.rgb(119, 186, 255));
        buildLabel.setPadding(0, 0, 0, 14);
        root.addView(buildLabel);
        TextView floorLabel = new TextView(this);
        floorLabel.setText("Messort auswählen:");
        floorLabel.setTextColor(Color.WHITE);
        floorLabel.setTextSize(17);
        root.addView(floorLabel);
        floorPicker = new Spinner(this);
        ArrayAdapter<String> floorAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{"Keller", "Erdgeschoss", "Obergeschoss", "Büro", "Raum Satellit 1", "Flur", "Garten", "Nebengebäude"}) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE); view.setTextSize(19); view.setPadding(14, 12, 14, 12);
                return view;
            }
        };
        floorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        floorPicker.setAdapter(floorAdapter);
        floorPicker.setSelection(1);
        floorPicker.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(119, 172, 239)));
        root.addView(floorPicker);
        LinearLayout lights = new LinearLayout(this);
        lights.setOrientation(LinearLayout.HORIZONTAL);
        lights.setPadding(0, 18, 0, 10);
        TextView redLamp = makeLamp("● ROT", Color.rgb(255, 90, 90));
        TextView yellowLamp = makeLamp("● GELB", Color.rgb(255, 205, 65));
        TextView greenLamp = makeLamp("● GRÜN", Color.rgb(65, 220, 145));
        lights.setBaselineAligned(false);
        lights.addView(redLamp, new LinearLayout.LayoutParams(0, -2, 1));
        lights.addView(yellowLamp, new LinearLayout.LayoutParams(0, -2, 1));
        lights.addView(greenLamp, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(lights);
        TextView signalStatus = new TextView(this);
        signalStatus.setText("WLAN-Ampel: Noch keine aktuelle Messung");
        signalStatus.setTextSize(17);
        signalStatus.setTextColor(Color.WHITE);
        root.addView(signalStatus);
        TextView safety = new TextView(this);
        safety.setText("ANALYSEMODUS · Router bleibt unverändert");
        safety.setTextColor(Color.rgb(90, 225, 165));
        safety.setPadding(0,12,0,12);
        root.addView(safety);
        TextView section = new TextView(this);
        section.setText("MESSEN & AUSWERTEN");
        section.setTextSize(14);
        section.setTypeface(null, android.graphics.Typeface.BOLD);
        section.setTextColor(Color.rgb(119, 186, 255));
        section.setPadding(0, 14, 0, 8);
        root.addView(section);
        Button profileButton = new Button(this);
        profileButton.setText("Deco-Analyseprofil einstellen");
        

        Button scan = new Button(this);
        scan.setText("WLAN jetzt messen");
        
        Button optimize = new Button(this);
        optimize.setText("Mesh und Empfang bewerten");
        
        Button channelsButton = new Button(this);
        channelsButton.setText("Kanalbelegung analysieren");
        
        android.widget.GridLayout actionsGrid = new android.widget.GridLayout(this);
        actionsGrid.setColumnCount(2);
        actionsGrid.setRowCount(2);
        actionsGrid.setAlignmentMode(android.widget.GridLayout.ALIGN_BOUNDS);
        root.addView(actionsGrid, new LinearLayout.LayoutParams(-1, -2));
        addGridButton(actionsGrid, profileButton, 0, 0, Color.rgb(166, 91, 16));
        addGridButton(actionsGrid, scan, 0, 1, Color.rgb(0, 116, 204));
        addGridButton(actionsGrid, optimize, 1, 0, Color.rgb(104, 55, 174));
        addGridButton(actionsGrid, channelsButton, 1, 1, Color.rgb(0, 132, 91));
        TextView advancedTitle = new TextView(this);
        advancedTitle.setText("ERWEITERTE FUNKTIONEN");
        advancedTitle.setTextSize(14);
        advancedTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        advancedTitle.setTextColor(Color.rgb(119, 186, 255));
        advancedTitle.setPadding(0, 22, 0, 8);
        Button advancedToggle = new Button(this);
        advancedToggle.setText("⚙ Erweiterte Funktionen anzeigen ▼");
        addCompactButton(root, advancedToggle);
        LinearLayout advancedPanel = new LinearLayout(this);
        advancedPanel.setOrientation(LinearLayout.VERTICAL);
        advancedPanel.setVisibility(View.GONE);
        root.addView(advancedPanel);
        advancedPanel.addView(advancedTitle);
        advancedToggle.setOnClickListener(v -> {
            boolean open = advancedPanel.getVisibility() != View.VISIBLE;
            advancedPanel.setVisibility(open ? View.VISIBLE : View.GONE);
            advancedToggle.setText(open ? "⚙ Erweiterte Funktionen schließen ▲" : "⚙ Erweiterte Funktionen anzeigen ▼");
        });
        Button protocolButton = new Button(this);
        protocolButton.setText("Erweiterte Diagnose: Deco-Ports prüfen");
        addCompactButton(advancedPanel, protocolButton);
        Button directButton = new Button(this);
        directButton.setText("Deco-Direktsteuerung: Kompatibilität prüfen");
        addCompactButton(advancedPanel, directButton);
        directButton.setOnClickListener(v -> showDecoControlCompatibility());
        Button decoButton = new Button(this);
        decoButton.setText("Deco-App öffnen (manuelle Einstellungen)");
        addCompactButton(advancedPanel, decoButton);
        Button baseline = new Button(this);
        baseline.setText("Vergleich: Ausgangsmessung speichern");
        addCompactButton(advancedPanel, baseline);
        Button after = new Button(this);
        after.setText("Vergleich: Neue Messung auswerten");
        addCompactButton(advancedPanel, after);
        Button compare = new Button(this);
        compare.setText("Messorte vergleichen");
        addCompactButton(advancedPanel, compare);
        Button settings = new Button(this);
        settings.setText("WLAN-Einstellungen öffnen");
        addCompactButton(advancedPanel, settings);
        Button share = new Button(this);
        share.setText("Messbericht teilen");
        addCompactButton(root, share);
        share.setOnClickListener(v -> shareReport());
        report = new TextView(this);
        report.setTextSize(17); report.setTextColor(Color.WHITE);
        report.setTextIsSelectable(true);
        report.setHorizontallyScrolling(false);
        report.setHorizontalScrollBarEnabled(false);
        report.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
        report.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NORMAL);
        report.setPadding(0,20,0,20);
        report.setText("Tippe auf „WLAN jetzt messen“. Für die Anzeige benachbarter Netze sind Standortberechtigung und aktivierte Standortdienste erforderlich.");
        TextView reportTitle = new TextView(this);
        reportTitle.setText("MESSBERICHT");
        reportTitle.setTextSize(20);
        reportTitle.setTextColor(Color.rgb(119, 186, 255));
        reportTitle.setPadding(0,20,0,0);
        root.addView(reportTitle);
        TextView resultLamp = new TextView(this);
        resultLamp.setText("● Noch keine Messung");
        resultLamp.setTextSize(23);
        resultLamp.setTypeface(null, android.graphics.Typeface.BOLD);
        resultLamp.setTextColor(Color.WHITE);
        resultLamp.setPadding(12, 18, 12, 18);
        root.addView(resultLamp);
        TextView summary = new TextView(this);
        summary.setText("Nach der Messung: Signal, Frequenz und Linkrate.");
        summary.setTextColor(Color.WHITE);
        summary.setTextSize(17);
        summary.setPadding(8, 12, 8, 18);
        root.addView(summary);
        Button details = new Button(this);
        details.setText("Technische Details anzeigen ▼");
        addCompactButton(root, details);
        report.setVisibility(View.GONE);
        root.addView(report);
        details.setOnClickListener(v -> {
            boolean show = report.getVisibility() != View.VISIBLE;
            report.setVisibility(show ? View.VISIBLE : View.GONE);
            details.setText(show ? "Technische Details schließen ▲" : "Technische Details anzeigen ▼");
        });
        report.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)(?:Signal|RSSI)[^\\n]*?(-[0-9]{2,3})\\s*dBm").matcher(text);
                if (matcher.find()) {
                    try {
                        int dbm = Integer.parseInt(matcher.group(1));
                        String grade = dbm >= -55 ? "GRÜN · Sehr gut" : dbm >= -67 ? "GRÜN · Gut" : dbm >= -75 ? "GELB · Mittel" : "ROT · Schwach";
                        signalStatus.setText("WLAN-Ampel: " + grade + " (" + dbm + " dBm)");
                        resultLamp.setText("● " + grade + " · " + dbm + " dBm");
                        resultLamp.setTextColor(dbm >= -67 ? Color.rgb(76,220,142) : dbm >= -75 ? Color.rgb(255,203,77) : Color.rgb(255,102,102));
                        java.util.regex.Matcher fm = java.util.regex.Pattern.compile("Frequenz: (\\d+) MHz").matcher(text);
                        java.util.regex.Matcher lm = java.util.regex.Pattern.compile("Linkrate: (\\d+) Mbit/s").matcher(text);
                        summary.setText("Signal: " + dbm + " dBm\nFrequenz: " + (fm.find() ? fm.group(1) + " MHz" : "–") + "\nLinkrate: " + (lm.find() ? lm.group(1) + " Mbit/s" : "–") + "\nLinkrate ist kein Internet-Speedtest.");
                        signalStatus.setTextColor(dbm >= -67 ? Color.rgb(76, 220, 142) : dbm >= -75 ? Color.rgb(255, 203, 77) : Color.rgb(255, 102, 102));
                    } catch (NumberFormatException ignored) {}
                } else {
                    signalStatus.setText("WLAN-Ampel: Noch keine aktuelle Messung");
                    signalStatus.setTextColor(Color.WHITE);
                    resultLamp.setText("● Noch keine Messung");
                    resultLamp.setTextColor(Color.WHITE);
                    summary.setText("Nach der Messung: Signal, Frequenz und Linkrate.");
                }
            }
            public void afterTextChanged(android.text.Editable text) {}
        });
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setHorizontalScrollBarEnabled(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        page.setClipToPadding(false);
        page.setPadding(0, 0, 0, 48);
        page.addView(root);
        setContentView(page);
        String lastCrash = getSharedPreferences("diagnostics", MODE_PRIVATE).getString("lastCrash", null);
        if (lastCrash != null) {
            report.setText("Vorheriger Absturz:\n" + lastCrash);
            getSharedPreferences("diagnostics", MODE_PRIVATE).edit().remove("lastCrash").apply();
        }
        profileButton.setOnClickListener(v -> { setActiveAction(profileButton, profileButton, scan, optimize, channelsButton); showAnalysisProfile(); });
        setActiveAction(scan, profileButton, scan, optimize, channelsButton);
        scan.setOnClickListener(v -> {
            setActiveAction(scan, profileButton, scan, optimize, channelsButton);
            try { meshMode = false; channelMode = false; checkAndAnalyze(); }
            catch (Throwable error) { showFailure("WLAN-Analyse fehlgeschlagen", error); }
        });
        optimize.setOnClickListener(v -> {
            setActiveAction(optimize, profileButton, scan, optimize, channelsButton);
            try { meshMode = true; channelMode = false; checkAndAnalyze(); }
            catch (Throwable error) { showFailure("Mesh-Bewertung fehlgeschlagen", error); }
        });
        channelsButton.setOnClickListener(v -> {
            setActiveAction(channelsButton, profileButton, scan, optimize, channelsButton);
            try { meshMode = false; channelMode = true; checkAndAnalyze(); }
            catch (Throwable error) { showFailure("Kanaloptimierung fehlgeschlagen", error); }
        });
        protocolButton.setOnClickListener(v -> probeDecoProtocol());
        decoButton.setOnClickListener(v -> openDecoOptimization());
        baseline.setOnClickListener(v -> saveOptimizationBaseline());
        after.setOnClickListener(v -> compareOptimization());
        compare.setOnClickListener(v -> showFloorComparison());
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
    }


    /**
     * Safe, local capability gate. An open TCP port is not proof of an API.
     * No credentials, speculative endpoints, or router writes are attempted.
     */
    private void showDecoControlCompatibility() {
        SharedPreferences prefs = getSharedPreferences("deco_settings", MODE_PRIVATE);
        String host = prefs.getString("main_deco_ip", "192.168.2.220");
        String message = "Deco X50 EU/1.0\n"
            + "Firmware laut Analyseprofil: 1.11.1 Build 26081714 Rel. 32981\n"
            + "Lokale Adresse: " + host + "\n\n"
            + "Status: Direkte Schreibsteuerung NICHT freigegeben.\n\n"
            + "Die zuvor erreichbaren TCP-Ports 80, 443, 20001 und 30001 "
            + "belegen keine dokumentierte, kompatible Verwaltungs-API. "
            + "HTTP 307 ist lediglich eine Weiterleitung.\n\n"
            + "Für eine Freigabe wären eine nachweislich unterstützte Schnittstelle, "
            + "sichere Anmeldung, Firmware-Abgleich, eine Rückleseprüfung und "
            + "eine ausdrückliche Bestätigung jeder Änderung erforderlich.\n\n"
            + "Bis dahin bleiben alle Routereinstellungen unverändert. "
            + "Die offizielle Deco-App ist der unterstützte manuelle Weg.";
        new android.app.AlertDialog.Builder(this)
            .setTitle("Deco-Direktsteuerung – Sicherheitsprüfung")
            .setMessage(message)
            .setNegativeButton("Schließen", null)
            .setPositiveButton("Deco-App öffnen", (dialog, which) -> openDecoOptimization())
            .show();
        if (report != null) report.setText(message);
    }

    private void shareReport() {
        String content = report == null ? "" : report.getText().toString();
        if (content.trim().isEmpty()) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Mein Netzwerk-Optimierer – Messbericht");
        send.putExtra(Intent.EXTRA_TEXT, content);
        startActivity(Intent.createChooser(send, "Messbericht teilen"));
    }

    private void probeDecoProtocol() {
        if (wifi == null || !wifi.isWifiEnabled()) {
            report.setText("Bitte zuerst mit dem eigenen Deco-WLAN verbinden.");
            return;
        }
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        android.net.Network active = cm == null ? null : cm.getActiveNetwork();
        android.net.NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
        if (caps == null || !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Deco-WLAN erforderlich")
                .setMessage("Das Handy verwendet derzeit keine aktive WLAN-Verbindung. Bitte mobile Daten vorübergehend ausschalten, mit Netpolice verbinden und die Prüfung wiederholen. Ohne lokalen WLAN-Zugang ist ein negativer Port-Test nicht aussagekräftig.")
                .setPositiveButton("WLAN-Einstellungen", (d, w) -> startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)))
                .setNegativeButton("Schließen", null).show();
            return;
        }
        final android.widget.EditText address = new android.widget.EditText(this);
        address.setSingleLine(true);
        address.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        android.content.SharedPreferences prefs = getSharedPreferences("deco_settings", MODE_PRIVATE);
        address.setText(prefs.getString("main_deco_ip", "192.168.2.220"));
        TextView device = new TextView(this);
        device.setText("Bekanntes Gerät: Deco X50 Büro, Firmware 1.11.1 Build 26081714 Rel. 32981. Hardware-Version EU/1.0. Nur lesende Verbindungsdiagnose; keine Anmeldung und keine Steuerbefehle.");
        device.setPadding(20, 10, 20, 10);
        new android.app.AlertDialog.Builder(this)
            .setTitle("Haupt-Deco X50 lokal prüfen")
            .setMessage("Deco X50 Büro · Hardware EU/1.0 · Firmware 1.11.1 Build 26081714 Rel. 32981. IP-Adresse aus der Deco-App. Nur TCP-Prüfung, keine Änderungen.")
            .setView(address)
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Verbindung testen", (dialog, which) -> {
                final String host = address.getText().toString().trim();
                if (!host.matches("^(?:\\d{1,3}\\.){3}\\d{1,3}$")) {
                    report.setText("Ungültige IPv4-Adresse. Bitte die IP des Haupt-Deco eingeben.");
                    return;
                }
                String[] octets = host.split("\\.");
                for (String octet : octets) {
                    if (Integer.parseInt(octet) > 255) {
                        report.setText("Ungültige IPv4-Adresse.");
                        return;
                    }
                }
                prefs.edit().putString("main_deco_ip", host).apply();
                report.setText("Prüfe den Haupt-Deco X50 unter " + host + " ...");
                new Thread(() -> {
                    boolean http = canConnect(host, 80);
                    boolean https = canConnect(host, 443);
                    boolean tcp20001 = canConnect(host, 20001);
                    boolean tcp30001 = canConnect(host, 30001);
                    boolean tcp22 = canConnect(host, 22);
                    String httpStatus = http ? readHttpStatus(host) : "Port geschlossen";
                    String message = "DECO X50 – DIREKTSTEUERUNG VORPRÜFUNG\nFirmware: 1.11.1 Build 26081714 Rel. 32981\nHardware-Version: EU/1.0\n\n"
                        + "Deco-IP: " + host
                        + "\nHTTP (80): " + (http ? "TCP erreichbar" : "nicht erreichbar")
                        + "\nHTTP-Status (GET /): " + httpStatus
                        + "\nHTTPS (443): " + (https ? "TCP erreichbar" : "nicht erreichbar")
                        + "\nPort 20001: " + (tcp20001 ? "TCP erreichbar" : "nicht erreichbar")
                        + "\nPort 30001: " + (tcp30001 ? "TCP erreichbar" : "nicht erreichbar")
                        + "\nPort 22: " + (tcp22 ? "TCP erreichbar" : "nicht erreichbar")
                        + "\n\nEin offener Port beweist keine unterstützte Deco-Steuerung. Ein geschlossener Port schließt andere Schnittstellen nicht aus. Direkte Schreibbefehle bleiben deaktiviert, bis Anmeldung und Firmware-Kompatibilität überprüft wurden."
                        + "\n\nEs wurden keine Router-Einstellungen geändert. Für eine echte Kanaloptimierung muss zuerst ein kompatibler, autorisierter Steuerungsweg bestätigt werden.";
                    runOnUiThread(() -> report.setText(message));
                }).start();
            }).show();
    }

    // Unauthenticated, read-only probe of the HTTP root path. Never send credentials.
    // A response does not establish support for any management API.
    private String readHttpStatus(String host) {
        java.net.Socket socket = new java.net.Socket();
        try {
            socket.connect(new java.net.InetSocketAddress(host, 80), 1500);
            socket.setSoTimeout(1500);
            java.io.OutputStream out = socket.getOutputStream();
            out.write(("GET / HTTP/1.1\r\nHost: " + host
                + "\r\nConnection: close\r\nAccept: */*\r\n\r\n")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.flush();
            java.io.InputStream in = socket.getInputStream();
            java.io.ByteArrayOutputStream first = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < 160; i++) {
                int c = in.read();
                if (c == -1 || c == '\n') break;
                if (c != '\r') first.write(c);
            }
            String line = first.toString("US-ASCII").trim();
            if (line.matches("HTTP/1\\.[01] [0-9]{3}.*")) {
                String clean = line.replaceAll("[^\\x20-\\x7E]", "");
                return clean.substring(0, Math.min(90, clean.length()));
            }
            return "Keine gültige HTTP-Statuszeile";
        } catch (Exception e) {
            return "Keine HTTP-Antwort (" + e.getClass().getSimpleName() + ")";
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private boolean canConnect(String host, int port) {
        java.net.Socket socket = new java.net.Socket();
        try {
            socket.connect(new java.net.InetSocketAddress(host, port), 1800);
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void openDecoOptimization() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Deco-Netzwerk optimieren")
            .setMessage("Die Kanaländerung muss das Deco-System selbst ausführen. Die TP-Link-Deco-App wird geöffnet. Dort: Mehr > Netzwerkoptimierung > Scannen > Optimieren (falls angeboten). Währenddessen kann WLAN kurz unterbrochen werden. Unsere App kann die Optimierung derzeit nicht selbst auslösen oder deren Erfolg bestätigen.")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Deco öffnen", (dialog, which) -> {
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage("com.tplink.tpm5");
                    if (intent != null) startActivity(intent);
                    else startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.tplink.tpm5")));
                } catch (Exception e) {
                    report.setText("Die Deco-App konnte nicht geöffnet werden: " + e.getMessage());
                }
            }).show();
    }


    @SuppressWarnings("deprecation")
    private void saveOptimizationBaseline() {
        if (wifi == null || !wifi.isWifiEnabled()) {
            report.setText("Bitte zuerst mit Netpolice per WLAN verbinden.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            report.setText("Bitte zuerst WLAN analysieren und die Standortberechtigung erlauben.");
            return;
        }
        WifiInfo info = wifi.getConnectionInfo();
        if (info == null || info.getRssi() > 0 || info.getRssi() < -110) {
            report.setText("Keine gueltige WLAN-Messung. Bitte WLAN-Verbindung pruefen.");
            return;
        }
        String floor = floorPicker.getSelectedItem().toString();
        getSharedPreferences("optimization_baseline", MODE_PRIVATE).edit()
            .putString("floor", floor)
            .putInt("rssi", info.getRssi())
            .putInt("rate", info.getLinkSpeed())
            .putInt("frequency", info.getFrequency())
            .putString("bssid", info.getBSSID())
            .putLong("time", System.currentTimeMillis()).apply();
        report.setText("VORHER-MESSUNG GESPEICHERT\n\nMessort: " + floor
            + "\nSignal: " + info.getRssi() + " dBm"
            + "\nLinkrate: " + info.getLinkSpeed() + " Mbit/s"
            + "\n\nJetzt in der Deco-App die Netzwerkoptimierung ausfuehren, danach am GLEICHEN Ort erneut messen. Die Linkrate ist kein Internet-Speedtest.");
    }

    @SuppressWarnings("deprecation")
    private void compareOptimization() {
        SharedPreferences prefs = getSharedPreferences("optimization_baseline", MODE_PRIVATE);
        if (!prefs.contains("time")) {
            report.setText("Bitte zuerst 'Messung vor Optimierung speichern' antippen.");
            return;
        }
        String floor = floorPicker.getSelectedItem().toString();
        String savedFloor = prefs.getString("floor", "");
        if (!savedFloor.equals(floor)) {
            report.setText("Messort stimmt nicht ueberein. Vorher: " + savedFloor + "; jetzt: " + floor + ". Bitte dieselbe Etage auswaehlen und am gleichen Platz messen.");
            return;
        }
        if (wifi == null || !wifi.isWifiEnabled()) {
            report.setText("Bitte mit dem WLAN verbinden.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            report.setText("Bitte zuerst WLAN analysieren und die Standortberechtigung erlauben.");
            return;
        }
        WifiInfo now = wifi.getConnectionInfo();
        if (now == null || now.getRssi() > 0 || now.getRssi() < -110) {
            report.setText("Keine gueltige WLAN-Messung.");
            return;
        }
        int before = prefs.getInt("rssi", -100), after = now.getRssi();
        int rateBefore = prefs.getInt("rate", 0), rateAfter = now.getLinkSpeed();
        int delta = after - before;
        String verdict = delta >= 6 ? "Empfang deutlich staerker" : delta <= -6 ? "Empfang deutlich schwaecher" : "Empfang etwa gleich (Messschwankungen moeglich)";
        StringBuilder out = new StringBuilder("DECO-OPTIMIERUNG: VORHER / NACHHER\n\n");
        out.append("Messort: ").append(floor).append("\n");
        out.append("Signal vorher: ").append(before).append(" dBm\n");
        out.append("Signal nachher: ").append(after).append(" dBm\n");
        out.append("Differenz: ").append(delta > 0 ? "+" : "").append(delta).append(" dB\n");
        out.append("Bewertung: ").append(verdict).append("\n\n");
        out.append("Linkrate vorher: ").append(rateBefore).append(" Mbit/s\n");
        out.append("Linkrate nachher: ").append(rateAfter).append(" Mbit/s\n");
        out.append("Frequenz vorher/nachher: ").append(prefs.getInt("frequency", 0)).append(" / ").append(now.getFrequency()).append(" MHz\n");
        out.append("BSSID vorher: ").append(prefs.getString("bssid", "?")).append("\n");
        out.append("BSSID nachher: ").append(now.getBSSID()).append("\n\n");
        out.append("WICHTIG: Der Vergleich zeigt nur die Funkverbindung des Handys. Ein anderer Zugangspunkt, Bewegung, wechselnde Last und Messschwankungen koennen die Werte beeinflussen. Eine tatsaechliche Deco-Kanaloptimierung wird dadurch nicht nachgewiesen.");
        report.setText(out.toString());
    }

    private void showFailure(String title, Throwable error) {
        String details = title + "\n" + error.getClass().getSimpleName() + ": " + error.getMessage();
        if (report != null) report.setText(details);
        else {
            TextView fallback = new TextView(this);
            fallback.setPadding(24, 40, 24, 24);
            fallback.setText(details);
            setContentView(fallback);
        }
    }

    private void checkAndAnalyze() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, permissionRequest);
            return;
        }
        LocationManager lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        if (lm != null && !lm.isLocationEnabled()) {
            report.setText("Bitte aktiviere die Standortdienste in Android. Sie sind für die WLAN-Umgebungssuche erforderlich.");
            return;
        }
        analyze();
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == permissionRequest) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) checkAndAnalyze();
            else report.setText("Ohne Standortberechtigung kann Android keine WLAN-Umgebungsliste bereitstellen.");
        }
    }

    @SuppressWarnings("deprecation")
    private void analyze() {
        if (!wifi.isWifiEnabled()) { report.setText("WLAN ist ausgeschaltet. Bitte aktiviere WLAN."); return; }
        String floor = floorPicker.getSelectedItem().toString();
        StringBuilder out = new StringBuilder("MEIN NETZWERK-OPTIMIERER\nMessort: " + floor + "\n" + (channelMode ? "KANAL- UND WLAN-OPTIMIERUNG" : meshMode ? "MESH-BEWERTUNG" : "WLAN-ANALYSE") + "\n\n");
        WifiInfo current = wifi.getConnectionInfo();
        if (current != null) {
            out.append("Verbindung: ").append(current.getSSID()).append("\n");
            out.append("Verbundener Zugangspunkt (BSSID): ").append(current.getBSSID()).append("\n");
            int rssi = current.getRssi();
            String rating = rssi >= -55 ? "Sehr gut" : rssi >= -67 ? "Gut" : rssi >= -75 ? "Mittel" : "Schwach";
            out.append("Signal: ").append(rssi).append(" dBm – ").append(rating).append("\n");
            out.append("Frequenz: ").append(current.getFrequency()).append(" MHz\n");
            out.append("Linkrate: ").append(current.getLinkSpeed()).append(" Mbit/s\n\n");
            saveFloorMeasurement(floor, current);
        }
        // Android kann WLAN-Scans begrenzen; vorhandene Scanergebnisse sind nicht zwingend aktuell.
        boolean requested = wifi.startScan();
        List<ScanResult> results = wifi.getScanResults();
        if (results == null) results = Collections.emptyList();
        Map<Integer,Integer> channels = new TreeMap<>();
        String ownSsid = current == null ? "" : current.getSSID().replace((char)34, ' ').trim();
        int ownCount = 0;
        int otherCount = 0;
        long newestTimestamp = 0;
        for (ScanResult r : results) {
            if (r.SSID != null && r.SSID.equals(ownSsid)) ownCount++; else otherCount++;
            newestTimestamp = Math.max(newestTimestamp, r.timestamp);
            int ch = channel(r.frequency);
            if (ch > 0) channels.put(ch, channels.getOrDefault(ch,0)+1);
        }
        out.append("Sichtbare Zugangspunkte: ").append(results.size()).append("\n");
        out.append("Gleiche SSID (möglicherweise eigenes Mesh): ").append(ownCount).append("\n");
        out.append("Andere/verborgene SSIDs: ").append(otherCount).append("\n");
        out.append("Hinweis: Mehrere BSSIDs mit gleichem Namen koennen zum selben Deco-Mesh gehoeren. Auch Android kann gespeicherte WLAN-Profile doppelt anzeigen. Diese Zaehlung beweist weder doppelte Router noch Funkstoerungen.\n");
        if (newestTimestamp > 0) {
            long ageSeconds = Math.max(0, (android.os.SystemClock.elapsedRealtimeNanos() / 1000L - newestTimestamp) / 1000000L);
            out.append("Jüngster Scan-Eintrag: vor ca. ").append(ageSeconds).append(" Sekunden\n");
        }
        out.append("Scan angefordert: ").append(requested ? "ja (Ergebnisse können älter sein)" : "nein / durch Android begrenzt").append("\n");
        if (newestTimestamp > 0) {
            long age = Math.max(0, (android.os.SystemClock.elapsedRealtimeNanos() / 1000L - newestTimestamp) / 1000000L);
            if (age > 120) out.append("ACHTUNG: Scan-Ergebnisse älter als 2 Minuten – Kanalwerte nur eingeschränkt brauchbar.\n");
        }
        SharedPreferences profile = getSharedPreferences("analysis_profile", MODE_PRIVATE);
        out.append("Dokumentierte Deco-Bandbreite: 2,4 GHz ").append(profile.getString("w24", "20 MHz"))
            .append("; 5 GHz ").append(profile.getString("w5", "160 MHz")).append("\n");
        out.append("Kanalbelegung (Zugangspunkte):\n");
        for (Map.Entry<Integer,Integer> e : channels.entrySet())
            out.append("Kanal ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");

        // Weighted interference estimate: stronger neighboring signals count more.
        // 2.4 GHz channels overlap even when their center channels differ.
        int[] candidates24 = {1,6,11};
        int[] candidates5 = {36,40,44,48,52,56,60,64,100,104,108,112,116,120,124,128,132,136,140};
        List<ScanResult> external = externalNetworks(results, ownSsid);
        int best24 = bestChannel(external, candidates24, true);
        int best5 = bestChannel(external, candidates5, false);
        out.append("\nAUTOMATISCHE AUSWERTUNG\n");
        out.append("2,4 GHz: Kanal ").append(best24).append(" als Testkandidat (20 MHz).\n");
        out.append("5 GHz: Kanal ").append(best5).append(" nur als 20-MHz-Orientierungswert; nicht als 80/160-MHz-Empfehlung. DFS kann Radarprüfung erfordern.\n");
        out.append("Schätzung aus sichtbaren WLAN-Signalen, keine gemessene Airtime oder vollständige 80/160-MHz-Belegung. Eigene Mesh-Funkmodule koennen die Kanalzaehlung beeinflussen; Kanalvorschlaege sind keine automatischen Router-Aenderungen.\n");
        if (channelMode) {
            appendChannelOptimization(out, results, current, ownSsid, newestTimestamp);
            report.setText(out.toString());
            return;
        }
        if (!meshMode) {
            out.append("\nTipp: Für Etagenhinweise bitte \"Mesh bewerten\" wählen.\n");
            report.setText(out.toString());
            return;
        }
        out.append("\nDECO-MESH-OPTIMIERUNGSPLAN\n");
        if ("Keller".equals(floor)) out.append("Messort Keller: Besonders die Verbindung zum Enpal-Deco und die Stabilität prüfen.\n");
        else if ("Obergeschoss".equals(floor)) out.append("Messort Obergeschoss: Hier steht kein Deco mehr. Empfang von den Geräten anderer Etagen prüfen; bei schwachem Signal zunächst Standort und Roaming kontrollieren.\n");
        else out.append("Messort Erdgeschoss: X50 und drei X1500 auf sinnvolle Abstände prüfen.\n");
        out.append("Setup: Starlink im Bypass-Modus, Deco X50 und sechs drahtlose X1500. Standorte und Uplink-Strecken in der Deco-App überprüfen.\n");
        out.append("1. In der Deco-App unter Netzwerkoptimierung einen Scan starten (falls verfügbar).\n");
        out.append("2. Keller: Mesh-Signal beider X1500 prüfen; der per LAN angeschlossene Enpal-Client braucht eine stabile Verbindung seines X1500 zum Mesh.\n");
        out.append("3. Erdgeschoss: Haupt-Deco X50 plus drei X1500. Überlappung und Backhaul-Qualität prüfen; nur ein Gerät nach dem anderen umstellen.\n");
        out.append("4. Obergeschoss: Kein eigener Deco vorhanden. Empfang und Linkrate an mehreren Stellen messen; bei Bedarf Abdeckung aus dem Erdgeschoss verbessern.\n");
        out.append("5. Bei mehreren Funkstrecken kann die nutzbare Datenrate sinken. Wenn möglich, später kabelgebundenen Backhaul prüfen; Enpal-LAN ist kein Backhaul.\n");
        out.append("Gleiche SSID ist nur ein Hinweis, kein sicherer Nachweis für ein eigenes Deco-Gerät.\n");
        out.append("Hinweis: Diese App kann Deco-Einstellungen nicht selbst ändern und kann den Mesh-Backhaul nicht vollständig messen. Kanalvorschläge sind nur Testkandidaten.\n");
        report.setText(out.toString());
    }

    @SuppressWarnings("deprecation")
    private void saveFloorMeasurement(String floor, WifiInfo info) {
        int rssi = info.getRssi();
        if (rssi > 0 || rssi < -110 || info.getFrequency() <= 0) return;
        SharedPreferences prefs = getSharedPreferences("floor_measurements", MODE_PRIVATE);
        String key = floor + "_";
        prefs.edit()
            .putInt(key + "rssi", rssi)
            .putInt(key + "rate", info.getLinkSpeed())
            .putInt(key + "frequency", info.getFrequency())
            .putString(key + "bssid", info.getBSSID())
            .putLong(key + "time", System.currentTimeMillis())
            .putInt(key + "count", prefs.getInt(key + "count", 0) + 1)
            .apply();
    }

    private void showFloorComparison() {
        SharedPreferences prefs = getSharedPreferences("floor_measurements", MODE_PRIVATE);
        StringBuilder out = new StringBuilder("ETAGENVERGLEICH\n\n");
        String[] floors = {"Keller", "Erdgeschoss", "Obergeschoss", "Büro", "Raum Satellit 1", "Flur", "Garten", "Nebengebäude"};
        String[] bssids = new String[floors.length];
        for (int i = 0; i < floors.length; i++) {
            String key = floors[i] + "_";
            out.append(floors[i]).append("\n");
            if (!prefs.contains(key + "time")) {
                out.append("Noch keine Messung gespeichert.\n\n");
                continue;
            }
            int rssi = prefs.getInt(key + "rssi", -100);
            String rating = rssi >= -55 ? "Sehr gut" : rssi >= -67 ? "Gut" : rssi >= -75 ? "Mittel" : "Schwach";
            bssids[i] = prefs.getString(key + "bssid", "unbekannt");
            out.append("Signal: ").append(rssi).append(" dBm (").append(rating).append(")\n");
            out.append("Linkrate: ").append(prefs.getInt(key + "rate", 0)).append(" Mbit/s\n");
            out.append("Frequenz: ").append(prefs.getInt(key + "frequency", 0)).append(" MHz\n");
            out.append("BSSID: ").append(bssids[i]).append("\n");
            out.append("Messungen: ").append(prefs.getInt(key + "count", 0)).append("\n");
            out.append("Letzte Messung: ").append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(prefs.getLong(key + "time", 0)))).append("\n\n");
        }
        int measuredPlaces = 0;
        Set<String> measuredBssids = new HashSet<>();
        for (String bssid : bssids) if (bssid != null) { measuredPlaces++; measuredBssids.add(bssid); }
        if (measuredPlaces > 1) {
            out.append("Unterschiedliche verbundene BSSIDs: ").append(measuredBssids.size()).append(" von ").append(measuredPlaces).append(" Messorten.\n");
            if (measuredBssids.size() == 1) out.append("Die Messungen zeigen denselben Zugangspunkt. Das kann auf fehlenden Wechsel des Handys zwischen Deco-Funkmodulen hindeuten.\n");
        }
        out.append("\nHinweis: Gespeichert wird jeweils die letzte Messung pro ausgewähltem Messort. Die Etage wird manuell ausgewählt. BSSID identifiziert ein Funkmodul, nicht zwingend ein ganzes Deco-Gerät. WLAN-Linkrate ist kein Internet-Speedtest.");
        report.setText(out.toString());
    }

    private void appendChannelOptimization(StringBuilder out, List<ScanResult> results, WifiInfo current, String ownSsid, long newestTimestamp) {
        out.append("\nKANAL- UND NETZWERKOPTIMIERUNG\n");
        if (results.isEmpty()) {
            out.append("Keine Scanergebnisse verfügbar. Bitte Standortdienste prüfen und später erneut messen.\n");
            return;
        }
        long age = newestTimestamp <= 0 ? Long.MAX_VALUE :
            Math.max(0, (android.os.SystemClock.elapsedRealtimeNanos() / 1000L - newestTimestamp) / 1000000L);
        if (age > 120) out.append("WARNUNG: Scan-Daten älter als zwei Minuten. Kanalempfehlung nur eingeschränkt aussagekräftig.\n");
        int own = 0, foreign = 0, hidden = 0;
        List<ScanResult> external = externalNetworks(results, ownSsid);
        for (ScanResult r : results) {
            String ssid = r.SSID == null ? "" : r.SSID.trim();
            if (!ownSsid.isEmpty() && ownSsid.equals(ssid)) own++;
            else {
                if (ssid.isEmpty()) hidden++; else foreign++;
            }
        }
        out.append("Gleiche SSID: ").append(own).append(" | Andere SSIDs: ").append(foreign)
            .append(" | Verborgene: ").append(hidden).append("\n");
        out.append("Gleiche SSID wird bei aktivierter Profiloption ausgeklammert. Versteckte SSIDs sind nicht sicher zuzuordnen. Der Index ist keine gemessene Auslastung.\n");
        int[] c24 = {1,6,11};
        int[] c5 = {36,40,44,48,52,56,60,64,100,104,108,112,116,120,124,128,132,136,140};
        out.append("\n2,4 GHz (20 MHz, Schätzung):\n");
        for (int ch : c24) out.append("Kanal ").append(ch).append(": Belastungsindex ")
            .append(String.format(java.util.Locale.GERMANY, "%.1f", channelScore(external, ch, true))).append("\n");
        out.append("Testkandidat: Kanal ").append(bestChannel(external, c24, true)).append("\n");
        out.append("\n5 GHz (Kanalbreiten aus Scan-Daten, soweit verfügbar):\n");
        for (int ch : new int[]{36,40,44,48,100,104,108,112}) out.append("Kanal ").append(ch).append(": Belastungsindex ")
            .append(String.format(java.util.Locale.GERMANY, "%.1f", channelScore(external, ch, false))).append("\n");
        out.append("20-MHz-Orientierungswert: Kanal ").append(bestChannel(external, c5, false)).append("\n");
        out.append("Bei 80/160 MHz sind mehrere 20-MHz-Kanäle gemeinsam belegt. Einzelkanal-Rankings reichen dafür nicht. Die Deco-Kanalbreite im Analyseprofil dokumentieren.\n");
        if (current != null) {
            int rssi = current.getRssi();
            if (rssi < -75) out.append("\nPRIORITÄT: Empfang schwach. Standort, Deco-Uplink und Roaming prüfen, bevor Kanäle verändert werden.\n");
            else if (rssi < -67) out.append("\nPRIORITÄT: Empfang mittelmäßig. Vor Kanaländerungen die Abdeckung am Messort prüfen.\n");
            else out.append("\nEmpfang am Handy ausreichend. Kanalwechsel nur bei tatsächlich auftretenden Problemen testen.\n");
        }
        out.append("\nDECO-SCHRITTE\n");
        out.append("1. In der TP-Link-Deco-App die Netzwerkoptimierung ausführen, falls verfügbar.\n");
        out.append("2. Verbindung und Signal der sechs X1500 zum Mesh prüfen; besonders den Enpal-Deco im Keller.\n");
        out.append("3. Bei Problemen Messungen am gleichen Ort vor und nach einer Änderung vergleichen.\n");
        out.append("4. Im Obergeschoss ohne eigenen Deco die Abdeckung und den verbundenen Zugangspunkt prüfen.\n");
        out.append("5. Keine Kanäle erzwingen, die das Deco-System nicht zur manuellen Auswahl anbietet.\n");
        out.append("\nWICHTIG: Der Index ist keine gemessene Auslastung. Kanalbreiten, DFS, verdeckte Stationen, Airtime und Funk-Backhaul werden nicht vollständig erfasst. Die App ändert keine Router-Einstellungen.\n");
    }

    private void showAnalysisProfile() {
        SharedPreferences p = getSharedPreferences("analysis_profile", MODE_PRIVATE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 8, 32, 8);
        TextView hint = new TextView(this);
        hint.setText("Nur lokale Dokumentation. Diese App verändert keine Deco-Einstellungen. Bitte die tatsächlichen Werte aus der Deco-App übernehmen.");
        layout.addView(hint);
        String[] options24 = {"20 MHz", "Automatisch", "40 MHz"};
        Spinner w24 = new Spinner(this);
        w24.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options24));
        w24.setSelection(Math.max(0, Arrays.asList(options24).indexOf(p.getString("w24", "20 MHz"))));
        layout.addView(w24);
        String[] options5 = {"160 MHz", "80 MHz"};
        Spinner w5 = new Spinner(this);
        w5.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options5));
        w5.setSelection(Math.max(0, Arrays.asList(options5).indexOf(p.getString("w5", "160 MHz"))));
        layout.addView(w5);
        CheckBox exclude = new CheckBox(this);
        exclude.setText("Eigene SSID aus Fremdnetz-Schätzung ausklammern");
        exclude.setChecked(p.getBoolean("exclude", true));
        layout.addView(exclude);
        new android.app.AlertDialog.Builder(this).setTitle("Deco-Analyseprofil")
            .setView(layout).setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern", (d,w) -> {
                p.edit().putString("w24", w24.getSelectedItem().toString())
                    .putString("w5", w5.getSelectedItem().toString())
                    .putBoolean("exclude", exclude.isChecked()).apply();
                report.setText("Profil gespeichert: 2,4 GHz " + w24.getSelectedItem()
                    + ", 5 GHz " + w5.getSelectedItem()
                    + ".\nKeine Router-Einstellungen geändert.");
            }).show();
    }

    private List<ScanResult> externalNetworks(List<ScanResult> results, String ownSsid) {
        if (!getSharedPreferences("analysis_profile", MODE_PRIVATE).getBoolean("exclude", true))
            return new ArrayList<>(results);
        List<ScanResult> external = new ArrayList<>();
        for (ScanResult r : results) {
            if (!ownSsid.isEmpty() && ownSsid.equals(r.SSID)) continue;
            external.add(r);
        }
        return external;
    }

    private double channelScore(List<ScanResult> results, int candidate, boolean band24) {
        double score = 0;
        int candidateMhz = band24 ? 2407 + 5 * candidate : 5000 + 5 * candidate;
        for (ScanResult r : results) {
            boolean is24 = r.frequency >= 2400 && r.frequency < 2500;
            if (is24 != band24 || (!band24 && (r.frequency < 5000 || r.frequency >= 5900))) continue;
            int width = 20;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                if (r.channelWidth == ScanResult.CHANNEL_WIDTH_40MHZ) width = 40;
                else if (r.channelWidth == ScanResult.CHANNEL_WIDTH_80MHZ) width = 80;
                else if (r.channelWidth == ScanResult.CHANNEL_WIDTH_160MHZ) width = 160;
            }
            int center = width > 20 && r.centerFreq0 > 0 ? r.centerFreq0 : r.frequency;
            double separation = Math.abs(candidateMhz - center);
            double overlap;
            if (band24) {
                overlap = Math.max(0, 1.0 - Math.abs(candidateMhz - r.frequency) / 25.0);
            } else {
                // An approximate spectral overlap between a 20 MHz candidate and a detected channel.
                overlap = Math.max(0, (width / 2.0 + 10.0 - separation) / 20.0);
                overlap = Math.min(1.0, overlap);
            }
            double strength = Math.pow(10.0, (Math.max(-95, Math.min(-30, r.level)) + 95) / 25.0);
            score += overlap * strength;
        }
        return score;
    }

    private int bestChannel(List<ScanResult> results, int[] candidates, boolean band24) {
        int best = candidates[0];
        double lowest = Double.POSITIVE_INFINITY;
        for (int candidate : candidates) {
            double score = channelScore(results, candidate, band24);
            // Prefer non-DFS 5 GHz channels on ties to avoid radar-related interruptions.
            if (!band24 && candidate >= 52) score += 0.001;
            if (score < lowest) { lowest = score; best = candidate; }
        }
        return best;
    }

    private int channel(int mhz) {
        if (mhz == 2484) return 14;
        if (mhz >= 2412 && mhz <= 2472) return (mhz-2407)/5;
        if (mhz >= 5000 && mhz < 5900) return (mhz-5000)/5;
        if (mhz >= 5955 && mhz <= 7115) return (mhz-5950)/5;
        return -1;
    }
}
