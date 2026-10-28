package com.twistylime.bossLand.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

public class BossLandConfiguration {
    private Plugin plugin;
    private File saveYML;
    private File langYML;
    private File recipesYML;
    private YamlConfiguration saveFile;
    private YamlConfiguration langFile;
    private YamlConfiguration recipesFile;
    /** ключи, о которых уже предупредили, чтобы не заливать лог одним и тем же */
    private final Set<String> missingLangWarned = new HashSet<>();

    public void loadConfig(Plugin plugin){
        this.plugin = plugin;
        saveYML = new File(plugin.getDataFolder(), "save.yml");
        saveFile = YamlConfiguration.loadConfiguration(saveYML);

        langYML = new File(plugin.getDataFolder(), "lang.yml");
        langFile = YamlConfiguration.loadConfiguration(langYML);

        recipesYML = new File(plugin.getDataFolder(), "recipes.yml");
        recipesFile = YamlConfiguration.loadConfiguration(recipesYML);

        saveConfig();
        reloadLang();
        reloadRecipes();
    }

    private void saveConfig(){
        if (!new File(plugin.getDataFolder(), "config.yml").exists()) {
            plugin.saveDefaultConfig();
        }

        if (!this.saveYML.exists()) {
            try {
                if(saveYML.createNewFile()){
                    plugin.getLogger().log(Level.INFO, "Создан новый save.yml.");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!langYML.exists()) {
            plugin.getLogger().log(Level.INFO, "lang.yml не найден, создаю...");
            plugin.saveResource("lang.yml", false);
            plugin.getLogger().log(Level.INFO, Bukkit.getVersion() + " lang.yml успешно создан!");
            reloadLang();
        }

        if (!recipesYML.exists()) {
            plugin.getLogger().log(Level.INFO, "recipes.yml не найден, создаю...");
            plugin.saveResource("recipes.yml", false);
            plugin.getLogger().log(Level.INFO, Bukkit.getVersion() + " recipes.yml успешно создан!");
            reloadRecipes();
        }
    }

    public void reloadConfigs(){
        plugin.reloadConfig();
        reloadLang();
        reloadRecipes();
    }

    private void reloadLang() {
        if (this.langYML == null) {
            this.langYML = new File(plugin.getDataFolder(), "lang.yml");
        }
        this.langFile = YamlConfiguration.loadConfiguration(this.langYML);

        // Дефолты берём из lang.yml внутри jar: файл сервера может остаться от старой версии,
        // и тогда новых ключей в нём просто нет.
        YamlConfiguration defaults = bundledDefaults("lang.yml");
        if (defaults == null) return;

        if (fillMissingKeys(this.langFile, defaults, true)) {
            try {
                this.langFile.save(this.langYML);
                plugin.getLogger().log(Level.INFO, "lang.yml дополнен ключами из версии плагина.");
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Не удалось сохранить lang.yml: " + e.getMessage());
            }
        }
        this.langFile.setDefaults(defaults);
        this.missingLangWarned.clear();
    }

    private void reloadRecipes() {
        if (this.recipesYML == null) {
            this.recipesYML = new File(plugin.getDataFolder(), "recipes.yml");
        }
        this.recipesFile = YamlConfiguration.loadConfiguration(this.recipesYML);

        // recipes.yml не перезаписываем (в нём есть комментарии), но недостающие ключи
        // всё равно будут читаться из версии в jar.
        YamlConfiguration defaults = bundledDefaults("recipes.yml");
        if (defaults != null) {
            this.recipesFile.setDefaults(defaults);
        }
    }

    /** Читает файл по умолчанию из jar, чтобы у пользователя всегда были актуальные ключи. */
    private YamlConfiguration bundledDefaults(String resource) {
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Не удалось прочитать " + resource + " из jar: " + e.getMessage());
            return null;
        }
    }

    /**
     * Дописывает в файл сервера ключи, которых в нём нет.
     *
     * @param repairPlaceholders заменить значения "Missing!", которые старые версии плагина записали в файл
     * @return {@code true}, если файл изменился
     */
    private boolean fillMissingKeys(YamlConfiguration file, YamlConfiguration defaults, boolean repairPlaceholders) {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) continue;
            boolean absent = !file.contains(key, true);
            boolean broken = repairPlaceholders && "Missing!".equals(file.getString(key));
            if (!absent && !broken) continue;
            file.set(key, defaults.get(key));
            changed = true;
        }
        return changed;
    }

    public String getLang(String s) {
        String value = langFile.getString(s);
        if (value == null) {
            value = missingLangFallback(s);
        }
        return value.replace("&", "§");
    }

    /**
     * Значение для ключа, которого нет ни в файле, ни в jar.
     * Файл не трогаем: раньше сюда записывалось "Missing!", и оно оставалось в игре навсегда.
     */
    private String missingLangFallback(String s) {
        if (missingLangWarned.add(s)) {
            plugin.getLogger().log(Level.WARNING,
                    "В lang.yml нет ключа «" + s + "» — показываю значение по умолчанию.");
        }
        int dot = s.lastIndexOf('.');
        return dot >= 0 && dot + 1 < s.length() ? s.substring(dot + 1).replace('_', ' ') : s;
    }

    public ConfigurationSection getRecipeConfiguration(String section){
        return recipesFile.getConfigurationSection(section);
    }

    @SuppressWarnings("unchecked")
    public ArrayList<String> getLangList(String s) {
        ArrayList<String> list = (ArrayList<String>) langFile.getList(s);
        ArrayList<String> colored = new ArrayList<>();
        if (list == null) {
            if (missingLangWarned.add(s)) {
                plugin.getLogger().log(Level.WARNING, "В lang.yml нет списка «" + s + "».");
            }
            return colored;
        }
        for (String line : list) {
            if (line != null) colored.add(line.replace("&", "§"));
        }
        return colored;
    }

    public void saveBossData() {
        try {
            this.saveFile.save(this.saveYML);
        } catch (IOException localIOException) {
            plugin.getLogger().log(Level.WARNING,"Ошибка при сохранении save.yml.");
        }
    }

    public void setSaveData(String path, Object value){
        this.saveFile.set(path, value);
    }

    public boolean godsDead() {
        return saveFile.getInt("drownedGodDeaths") > 0 && saveFile.getInt("pharaohGodDeaths") > 0
                && saveFile.getInt("aetherGodDeaths") > 0;
    }

    public String getDataFrom(String file, String key){
        if(file.equals("save")){
            return saveFile.getString(key);
        }
        else{
            return langFile.getString(key);
        }
    }

    public int getIntDataFrom(String file, String key){
        if(file.equals("save")){
            return saveFile.getInt(key);
        }
        else{
            return langFile.getInt(key);
        }
    }

    public Map<String, String> getBossLootIdMap() {
        Map<String, String> lootIdMap = new HashMap<>();

        ConfigurationSection bosses = plugin.getConfig().getConfigurationSection("bosses");
        if (bosses == null) {
            plugin.getLogger().warning("В config.yml нет секции bosses!");
            return lootIdMap;
        }

        for (String bossKey : bosses.getKeys(false)) {
            ConfigurationSection bossSection = bosses.getConfigurationSection(bossKey);
            if (bossSection == null) continue;

            ConfigurationSection lootSection = bossSection.getConfigurationSection("loot");
            if (lootSection == null) continue;

            for (String lootKey : lootSection.getKeys(false)) {
                ConfigurationSection lootItem = lootSection.getConfigurationSection(lootKey);
                if (lootItem == null) continue;

                // Default to item material name
                String itemId = lootItem.getString("item", "UNKNOWN_ITEM");

                // Prefer custom name if it exists
                String displayName = lootItem.getString("name");
                if (displayName != null) {
                    itemId = displayName.replaceAll("&.","");
                }

                // Create a unique key so map values don't overwrite each other
                String mapKey = bossKey + ":" + lootKey;

                lootIdMap.put(mapKey, itemId);
            }
        }

        return lootIdMap;
    }

}
