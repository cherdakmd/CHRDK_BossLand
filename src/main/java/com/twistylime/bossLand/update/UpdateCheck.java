package com.twistylime.bossLand.update;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;

public class UpdateCheck {

    /** Откуда узнавать последнюю версию: релизы нашего репозитория. */
    private static final String DEFAULT_VERSION_URL =
            "https://api.github.com/repos/cherdakmd/CHRDK_BossLand/releases/latest";

    /** Ссылка из старых версий плагина: gist автора оригинального BossLand, который больше не обновляется. */
    private static final String STALE_VERSION_URL =
            "https://gist.githubusercontent.com/TwistyLime/334114c49e15e97bcfeea6f724201109/raw/BossLandCurrentVersion.json";

    private final Plugin plugin;
    private final String versionUrl;

    public UpdateCheck(Plugin plugin) {
        this.plugin = plugin;
        String configured = plugin.getConfig().getString("update-check.version-url");
        // config.yml на существующих серверах не перезаписывается, поэтому старую ссылку
        // узнаём сами и молча подменяем: иначе админ вечно видит чужую версию как «новую»
        if (configured == null || configured.isBlank() || STALE_VERSION_URL.equals(configured.trim())) {
            configured = DEFAULT_VERSION_URL;
        }
        this.versionUrl = configured;
    }

    public void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("update-check.enabled", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JSONObject data = getJsonObject();
                String latest = latestVersion(data);
                String current = plugin.getDescription().getVersion().trim();

                if (latest != null && isNewer(latest, current)) {
                    Bukkit.getConsoleSender().sendMessage("§e[BossLand] Доступна новая версия: §a" + latest + "§e (установлена: §c" + current + "§e)");
                    String changelog = data.has("changelog") ? data.getString("changelog")
                            : (data.has("name") ? data.getString("name") : null);
                    if (changelog != null && !changelog.isBlank()) {
                        Bukkit.getConsoleSender().sendMessage("§6Что нового: " + changelog);
                    }
                } else {
                    Bukkit.getConsoleSender().sendMessage("§a[BossLand] Установлена последняя версия плагина (" + current + ").");
                }

            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage("§c[BossLand] Не удалось проверить обновления: " + e.getMessage());
            }
        });
    }

    /**
     * Последняя версия из ответа. Понимает и наш формат (ключ {@code full_version}),
     * и ответ GitHub про релизы (ключ {@code tag_name} — с ведущей «v», её убираем).
     */
    private static String latestVersion(JSONObject data) {
        String raw = data.has("full_version") ? data.getString("full_version")
                : (data.has("tag_name") ? data.getString("tag_name") : null);
        if (raw == null) return null;
        String version = raw.trim();
        if (!version.isEmpty() && (version.charAt(0) == 'v' || version.charAt(0) == 'V'))
            version = version.substring(1);
        return version.isEmpty() ? null : version;
    }

    /**
     * Новее ли remote, чем current. Сравниваем по числам, а не по строкам: строковое
     * сравнение считает «3.9» новее «3.13», из-за чего серверу предлагают откатиться.
     */
    private static boolean isNewer(String remote, String current) {
        int[] a = numbers(remote);
        int[] b = numbers(current);
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i] != b[i]) return a[i] > b[i];
        }
        return false;
    }

    /** Числа из версии: {@code 3.13-v1.21.5} → {@code [3, 13, 1, 21, 5]}. */
    private static int[] numbers(String version) {
        String[] parts = version.split("[^0-9]+");
        int[] parsed = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            try {
                parsed[count++] = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                // нечисловой кусок версии — просто пропускаем
            }
        }
        return java.util.Arrays.copyOf(parsed, count);
    }

    private JSONObject getJsonObject() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(versionUrl).openConnection();
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);

        InputStream inputStream = connection.getInputStream();
        String json;
        try (Scanner scanner = new Scanner(inputStream)) {
            scanner.useDelimiter("\\A");
            json = scanner.hasNext() ? scanner.next() : "";
        }

        return new JSONObject(json);
    }
}
