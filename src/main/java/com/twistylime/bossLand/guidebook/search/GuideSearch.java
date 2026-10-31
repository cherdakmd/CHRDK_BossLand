package com.twistylime.bossLand.guidebook.search;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.core.BossLandLoot;
import com.twistylime.bossLand.core.BossLandRecipes;
import com.twistylime.bossLand.core.BossLandShrines;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Поиск по руководству: индексирует боссов, рецепты, осколки и весь именованный лут,
 * чтобы игрок мог найти нужную страницу по названию, а не листать меню.
 *
 * Раньше ищлись только боссы да предметы призыва, из-за чего «Морозный Клинок» или
 * «Зеленый осколок» поиск не находил — хотя в книге они были показаны.
 */
public final class GuideSearch {

    /** Что нашлось и куда ведёт клик по результату. */
    public enum Kind {
        BOSS("Босс"),
        ITEM("Предмет"),
        SHARD("Осколок"),
        LOOT("Добыча");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Одна запись индекса: ключ рецепта, отображаемое имя и иконка. */
    public record Entry(String key, String name, Kind kind, ItemStack icon) {
    }

    private GuideSearch() {
    }

    /** Полный список того, что можно найти. Строится заново: рецепты могут перезагружаться командой. */
    public static List<Entry> index() {
        // Имя встречается и среди рецептов, и среди лута (ядро льда, чешуя бездны) —
        // в поиске такая вещь должна быть один раз, и первым делом ведём на рецепт.
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entry entry : bossEntries()) {
            if (seen.add(entry.name())) entries.add(entry);
        }
        for (Entry entry : itemEntries()) {
            if (seen.add(entry.name())) entries.add(entry);
        }
        for (Entry entry : shardEntries()) {
            if (seen.add(entry.name())) entries.add(entry);
        }
        for (Entry entry : lootEntries()) {
            if (seen.add(entry.name())) entries.add(entry);
        }
        return entries;
    }

    /** Осколки: ведут на страницу осколка — кто роняет и что из него крафтится. */
    private static List<Entry> shardEntries() {
        List<Entry> entries = new ArrayList<>();
        BossLandLoot lootManager = BossLand.getLootManager();
        if (lootManager == null) return entries;

        for (ItemStack shard : lootManager.getShards()) {
            String name = displayName(shard);
            if (name == null || name.isBlank()) continue;
            entries.add(new Entry(name, name, Kind.SHARD, shard));
        }
        return entries;
    }

    /** Вещи из дропа боссов: оружие, броня, артефакты. */
    private static List<Entry> lootEntries() {
        List<Entry> entries = new ArrayList<>();
        BossLandLoot lootManager = BossLand.getLootManager();
        if (lootManager == null) return entries;

        List<ItemStack> all = new ArrayList<>(lootManager.getWeaponsAndItems());
        all.addAll(lootManager.getArmors());
        for (ItemStack item : all) {
            String name = displayName(item);
            if (name == null || name.isBlank()) continue;
            entries.add(new Entry(name, name, Kind.LOOT, item));
        }
        return entries;
    }

    private static List<Entry> bossEntries() {
        List<Entry> entries = new ArrayList<>();
        BossLand plugin = BossLand.getPlugin();
        BossLandShrines shrines = BossLand.getShrinesManager();
        if (plugin == null || shrines == null) return entries;

        ConfigurationSection section = plugin.getBossConfig().getRecipeConfiguration("shrine_recipes");
        if (section == null) return entries;

        for (String key : section.getKeys(false)) {
            ItemStack icon = shrineResult(shrines, key);
            String name = displayName(icon);
            if (name == null || name.isBlank()) continue;
            entries.add(new Entry(key, name, Kind.BOSS, icon));
        }
        return entries;
    }

    private static List<Entry> itemEntries() {
        List<Entry> entries = new ArrayList<>();
        BossLand plugin = BossLand.getPlugin();
        BossLandRecipes recipes = BossLand.getRecipeManager();
        if (plugin == null || recipes == null) return entries;

        ConfigurationSection section = plugin.getBossConfig().getRecipeConfiguration("item_recipes");
        if (section == null) return entries;

        for (String key : section.getKeys(false)) {
            ItemStack icon = itemResult(recipes, key);
            String name = displayName(icon);
            if (name == null || name.isBlank()) continue;
            entries.add(new Entry(key, name, Kind.ITEM, icon));
        }
        return entries;
    }

    private static ItemStack shrineResult(BossLandShrines shrines, String key) {
        try {
            Map<String, Object> recipe = shrines.getShrineRecipe(key);
            if (recipe == null) return null;
            Object result = recipe.get("result");
            if (result instanceof ItemStack stack) return stack;
            if (result instanceof Map<?, ?> map) {
                Object item = map.get("item");
                if (item instanceof ItemStack stack) return stack;
            }
        } catch (RuntimeException ignored) {
            // битый рецепт не должен ронять меню поиска
        }
        return null;
    }

    private static ItemStack itemResult(BossLandRecipes recipes, String key) {
        try {
            Map<String, Object> recipe = recipes.getRecipes(key);
            if (recipe == null) return null;
            Object result = recipe.get("result");
            if (result instanceof ItemStack stack) return stack;
        } catch (RuntimeException ignored) {
            // битый рецепт не должен ронять меню поиска
        }
        return null;
    }

    /**
     * Ищет совпадения по вхождению подстроки в название.
     *
     * @param query то, что ввёл игрок (цветовые коды и регистр не важны)
     * @return сначала точные совпадения, затем те, что начинаются с запроса, затем остальные
     */
    public static List<Entry> search(String query) {
        String needle = normalize(query);
        if (needle.isEmpty()) return List.of();

        List<Entry> matches = new ArrayList<>();
        for (Entry entry : index()) {
            String name = normalize(entry.name());
            if (name.isEmpty()) continue;
            if (name.contains(needle) || entry.key().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(entry);
            }
        }

        matches.sort(Comparator
                .comparingInt((Entry entry) -> rank(entry, needle))
                .thenComparing(entry -> normalize(entry.name())));
        return matches;
    }

    private static int rank(Entry entry, String needle) {
        String name = normalize(entry.name());
        if (name.equals(needle)) return 0;
        if (name.startsWith(needle)) return 1;
        if (entry.key().toLowerCase(Locale.ROOT).contains(needle)) return 3;
        return 2;
    }

    /** Убирает цветовые коды и приводит к нижнему регистру. */
    public static String normalize(String value) {
        if (value == null) return "";
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', value))
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String displayName(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) return null;
        ItemMeta meta = stack.getItemMeta();
        String name = meta.hasDisplayName() ? meta.getDisplayName() : null;
        if (name == null || name.isBlank()) {
            return stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        return name;
    }
}
