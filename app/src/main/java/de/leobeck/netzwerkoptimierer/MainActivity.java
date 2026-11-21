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

    private void initializeUi() {
        wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 32, 28, 24);
        root.setBackgroundColor(Color.rgb(245,248,252));
        TextView title = new TextView(this);
        title.setText("Mein Netzwerk-Optimierer");
        title.setTextSize(24); title.setTextColor(Color.rgb(20,45,80));
        root.addView(title);
        TextView info = new TextView(this);
        info.setText("WLAN-Analyse und Empfehlungen – ohne Werbung");
        info.setPadding(0,14,0,18); root.addView(info);
        TextView floorLabel = new TextView(this);
        floorLabel.setText("Messort auswählen:");
        root.addView(floorLabel);
        floorPicker = new Spinner(this);
        ArrayAdapter<String> floorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Keller", "Erdgeschoss", "Obergeschoss"});
        floorPicker.setAdapter(floorAdapter);
        floorPicker.setSelection(1);
        root.addView(floorPicker);
        Button scan = new Button(this);
        scan.setText("WLAN analysieren");
        root.addView(scan);
        Button optimize = new Button(this);
        optimize.setText("Mesh bewerten und Optimierung planen");
        root.addView(optimize);
        Button channelsButton = new Button(this);
        channelsButton.setText("Kanäle und WLAN optimieren");
        root.addView(channelsButton);
        Button compare = new Button(this);
        compare.setText("Etagen vergleichen");
        root.addView(compare);
        Button settings = new Button(this);
        settings.setText("WLAN-Einstellungen öffnen");
        root.addView(settings);
        ScrollView scroll = new ScrollView(this);
        report = new TextView(this);
        report.setTextSize(16); report.setTextColor(Color.rgb(25,35,45));
        report.setPadding(0,20,0,20);
        report.setText("Tippe auf „WLAN analysieren“. Für die Anzeige benachbarter Netze sind Standortberechtigung und aktivierte Standortdienste erforderlich.");
        scroll.addView(report); root.addView(scroll);
        setContentView(root);
        String lastCrash = getSharedPreferences("diagnostics", MODE_PRIVATE).getString("lastCrash", null);
        if (lastCrash != null) {
            report.setText("Vorheriger Absturz:\n" + lastCrash);
            getSharedPreferences("diagnostics", MODE_PRIVATE).edit().remove("lastCrash").apply();
        }
        scan.setOnClickListener(v -> {
            try { meshMode = false; channelMode = false; checkAndAnalyze(); }
            catch (Throwable error) { showFailure("WLAN-Analyse fehlgeschlagen", error); }
        });
        optimize.setOnClickListener(v -> {
            try { meshMode = true; channelMode = false; checkAndAnalyze(); }
            catch (Throwable error) { showFailure("Mesh-Bewertung fehlgeschlagen", error); }
        });
        channelsButton.setOnClickListener(v -> {
            try { meshMode = false; channelMode = true; checkAndAnalyze(); }
            catch (Throwable error) { showFailure("Kanaloptimierung fehlgeschlagen", error); }
        });
        compare.setOnClickListener(v -> showFloorComparison());
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
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
        if (newestTimestamp > 0) {
            long ageSeconds = Math.max(0, (android.os.SystemClock.elapsedRealtimeNanos() / 1000L - newestTimestamp) / 1000000L);
            out.append("Jüngster Scan-Eintrag: vor ca. ").append(ageSeconds).append(" Sekunden\n");
        }
        out.append("Scan angefordert: ").append(requested ? "ja (Ergebnisse können älter sein)" : "nein / durch Android begrenzt").append("\n");
        out.append("Kanalbelegung (Zugangspunkte):\n");
        for (Map.Entry<Integer,Integer> e : channels.entrySet())
            out.append("Kanal ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");

        // Weighted interference estimate: stronger neighboring signals count more.
        // 2.4 GHz channels overlap even when their center channels differ.
        int[] candidates24 = {1,6,11};
        int[] candidates5 = {36,40,44,48,52,56,60,64,100,104,108,112,116,120,124,128,132,136,140};
        int best24 = bestChannel(results, candidates24, true);
        int best5 = bestChannel(results, candidates5, false);
        out.append("\nAUTOMATISCHE AUSWERTUNG\n");
        out.append("2,4 GHz: Kanal ").append(best24).append(" als Testkandidat (20 MHz).\n");
        out.append("5 GHz: Kanal ").append(best5).append(" als Testkandidat; DFS-Kanäle können Radarprüfung erfordern.\n");
        out.append("Bewertung berücksichtigt Signalstärke und Kanalüberlappung, aber nicht tatsächliche Airtime, Störquellen oder Router-Kanalbreite.\n");
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
        out.append("Konfiguration: Starlink per LAN am X50; 5 X1500 drahtlos. Keller: 2 X1500 (einer versorgt Enpal per LAN). Erdgeschoss: X50 und 3 X1500. Obergeschoss: kein Deco.\n");
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
        String[] floors = {"Keller", "Erdgeschoss", "Obergeschoss"};
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
        if (bssids[0] != null && bssids[1] != null && bssids[2] != null) {
            Set<String> distinct = new HashSet<>(Arrays.asList(bssids));
            out.append("Unterschiedliche verbundene BSSIDs: ").append(distinct.size()).append(" von 3 Messorten.\n");
            if (distinct.size() == 1) out.append("Alle drei Messungen zeigen denselben Zugangspunkt. Das kann auf fehlenden Wechsel des Handys zwischen Deco-Funkmodulen hindeuten.\n");
        }
        out.append("\nHinweis: Gespeichert wird jeweils die letzte Messung pro Etage, nicht der genaue Raum. Die Etage wird manuell ausgewählt. BSSID identifiziert ein Funkmodul, nicht zwingend ein ganzes Deco-Gerät. WLAN-Linkrate ist kein Internet-Speedtest.");
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
        List<ScanResult> external = new ArrayList<>();
        for (ScanResult r : results) {
            String ssid = r.SSID == null ? "" : r.SSID.trim();
            if (!ownSsid.isEmpty() && ownSsid.equals(ssid)) own++;
            else {
                external.add(r);
                if (ssid.isEmpty()) hidden++; else foreign++;
            }
        }
        out.append("Gleiche SSID: ").append(own).append(" | Andere SSIDs: ").append(foreign)
            .append(" | Verborgene: ").append(hidden).append("\n");
        out.append("Gleiche SSID wird separat ausgewiesen; fremde/verborgene WLANs bestimmen den Vergleichsindex. Eigene Mesh-Funkmodule und versteckte SSIDs können die tatsächliche Belastung verändern.\n");
        int[] c24 = {1,6,11};
        int[] c5 = {36,40,44,48,52,56,60,64,100,104,108,112,116,120,124,128,132,136,140};
        out.append("\n2,4 GHz (20 MHz, Schätzung):\n");
        for (int ch : c24) out.append("Kanal ").append(ch).append(": Belastungsindex ")
            .append(String.format(java.util.Locale.GERMANY, "%.1f", channelScore(external, ch, true))).append("\n");
        out.append("Testkandidat: Kanal ").append(bestChannel(external, c24, true)).append("\n");
        out.append("\n5 GHz (Kanalbreiten aus Scan-Daten, soweit verfügbar):\n");
        for (int ch : new int[]{36,40,44,48,100,104,108,112}) out.append("Kanal ").append(ch).append(": Belastungsindex ")
            .append(String.format(java.util.Locale.GERMANY, "%.1f", channelScore(external, ch, false))).append("\n");
        out.append("Testkandidat (20 MHz): Kanal ").append(bestChannel(external, c5, false)).append("\n");
        out.append("Bei 80-MHz-Betrieb sind mehrere benachbarte 20-MHz-Kanäle gemeinsam belegt. Die tatsächliche Deco-Kanalbreite bitte in der Deco-App prüfen.\n");
        if (current != null) {
            int rssi = current.getRssi();
            if (rssi < -75) out.append("\nPRIORITÄT: Empfang schwach. Standort, Deco-Uplink und Roaming prüfen, bevor Kanäle verändert werden.\n");
            else if (rssi < -67) out.append("\nPRIORITÄT: Empfang mittelmäßig. Vor Kanaländerungen die Abdeckung am Messort prüfen.\n");
            else out.append("\nEmpfang am Handy ausreichend. Kanalwechsel nur bei tatsächlich auftretenden Problemen testen.\n");
        }
        out.append("\nDECO-SCHRITTE\n");
        out.append("1. In der TP-Link-Deco-App die Netzwerkoptimierung ausführen, falls verfügbar.\n");
        out.append("2. Verbindung und Signal der fünf X1500 zum Mesh prüfen; besonders den Enpal-Deco im Keller.\n");
        out.append("3. Bei Problemen Messungen am gleichen Ort vor und nach einer Änderung vergleichen.\n");
        out.append("4. Im Obergeschoss ohne eigenen Deco die Abdeckung und den verbundenen Zugangspunkt prüfen.\n");
        out.append("5. Keine Kanäle erzwingen, die das Deco-System nicht zur manuellen Auswahl anbietet.\n");
        out.append("\nWICHTIG: Der Index ist keine gemessene Auslastung. Kanalbreiten, DFS, verdeckte Stationen, Airtime und Funk-Backhaul werden nicht vollständig erfasst. Die App ändert keine Router-Einstellungen.\n");
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
