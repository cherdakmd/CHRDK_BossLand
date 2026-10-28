package com.twistylime.bossLand.selftest;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.core.BossLandItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Самопроверка установки: проверяет то, что не видно при обычном старте сервера —
 * что осколки, выпадающие с боссов, называются ровно так, как ожидает проверка рецептов,
 * и что предметы призыва действительно собираются (валидатор крафта не отбрасывает результат).
 *
 * Запуск: /bl-admin selftest
 */
public final class BossLandSelftest {

    private static final String[] SHARD_KEYS = {
            "items.whiteshard", "items.greenshard", "items.greyshard", "items.blackshard",
            "items.redshard", "items.brownshard", "items.emeraldshard", "items.goldshard",
            "items.blueshard", "items.demonicshard"
    };

    /** Одно правило крафта: что должно получиться и что должно лежать в сетке (слот -> ингредиент). */
    private record Rule(String label, String resultGetter, Map<Integer, String> slots) {
    }

    public record Outcome(boolean ok, int checks, int failed, List<String> lines) {
    }

    private static List<Rule> rules() {
        List<Rule> list = new ArrayList<>();
        list.add(rule("Колокол Рока", "getIllagerItem",
                Map.of(4, "items.whiteshard", 5, "items.greenshard", 6, "items.greyshard")));
        list.add(rule("Книга Заклинаний", "getWizardItem",
                Map.of(4, "items.blackshard", 6, "items.redshard")));
        list.add(rule("Зелье Роста Гиганта", "getGiantIem",
                Map.of(4, "items.greenshard", 5, "items.redshard", 6, "items.brownshard")));
        list.add(rule("Яйцо Старшего Дракона", "getDragonItem",
                Map.of(5, "items.whiteshard", 8, "items.blackshard")));
        list.add(rule("Запретный Плод", "getGodItem",
                Map.of(7, "items.emeraldshard", 8, "items.goldshard", 9, "items.blueshard")));
        list.add(rule("Отвратительный Плод", "getDevilItem",
                Map.of(2, "items.demonicshard", 4, "items.demonicshard", 5, "item:getGodItem",
                        6, "items.demonicshard", 8, "items.demonicshard")));
        list.add(rule("Смертная Тетрадь", "getDeathItem",
                Map.of(5, "items.knowledgebook")));
        list.add(rule("Ядро Льда", "getFrostCore",
                Map.of(4, "items.blueshard", 5, "items.emeraldshard")));
        list.add(rule("Чешуя Бездны", "getAbyssalScale",
                Map.of(4, "items.blackshard", 5, "items.redshard")));
        list.add(rule("Инфернальное Ядро", "getInfernalCore",
                Map.of(4, "item:getFrostCore", 5, "item:getAbyssalScale")));
        list.add(rule("Осколок Кристалла Края", "getEnderCrystalShard",
                Map.of(4, "item:getInfernalCore", 5, "item:getFrostCore")));
        list.add(rule("Эссенция Природы", "getNaturesEssence",
                Map.of(4, "items.greenshard", 5, "items.brownshard", 6, "items.redshard")));
        return list;
    }

    private static Rule rule(String label, String resultGetter, Map<Integer, String> slots) {
        return new Rule(label, resultGetter, new LinkedHashMap<>(slots));
    }

    public static Outcome run(BossLand plugin) {
        List<String> lines = new ArrayList<>();
        int checks = 0;
        int failed = 0;

        BossLandItems items = BossLand.getItemsManager();
        if (items == null || BossLand.getRecipeManager() == null) {
            lines.add("§cМенеджеры плагина недоступны — плагин ещё не включён?");
            return new Outcome(false, 1, 1, lines);
        }

        Map<String, ItemStack> shards = collectShards(plugin);
        lines.add("§7Осколков найдено в дропе боссов: §f" + shards.size() + " §7из §f" + SHARD_KEYS.length);

        // 1) имя осколка в дропе должно совпадать с именем, которое ждёт валидатор крафта
        for (String shardKey : SHARD_KEYS) {
            checks++;
            String expected = plugin.getBossConfig().getLang(shardKey);
            ItemStack dropped = shards.get(shardKey);
            if (dropped == null) {
                failed++;
                lines.add("§c✗ §f" + shardKey + " §7— не падает ни с одного босса");
                continue;
            }
            String actual = dropped.getItemMeta() == null ? null : dropped.getItemMeta().getDisplayName();
            if (actual == null || !actual.equals(expected)) {
                failed++;
                lines.add("§c✗ §f" + shardKey + " §7— имя не совпадает: дроп §f" + actual + "§7, ожидается §f" + expected);
            }
        }

        // 2) каждый предмет призыва собирается из нужных осколков и не собирается из чужих
        for (Rule rule : rules()) {
            checks++;
            String problem = verifyCrafting(rule, shards, items, false);
            if (problem != null) {
                failed++;
                lines.add("§c✗ §f" + rule.label() + " §7— " + problem);
            }
            checks++;
            String guardProblem = verifyCrafting(rule, shards, items, true);
            if (guardProblem != null) {
                failed++;
                lines.add("§c✗ §f" + rule.label() + " §7(защита) — " + guardProblem);
            }
        }

        boolean ok = failed == 0;
        lines.add(ok
                ? "§aСАМОПРОВЕРКА: OK §7(" + checks + " проверок)"
                : "§cСАМОПРОВЕРКА: ОШИБОК " + failed + " из " + checks);
        return new Outcome(ok, checks, failed, lines);
    }

    /** @return описание проблемы или {@code null}, если крафт ведёт себя правильно */
    @SuppressWarnings("unchecked")
    private static String verifyCrafting(Rule rule, Map<String, ItemStack> shards, BossLandItems items, boolean wrongIngredients) {
        try {
            Object created = Bukkit.createInventory(null, InventoryType.WORKBENCH);
            if (!(created instanceof CraftingInventory grid)) {
                return "не удалось создать сетку крафта";
            }

            ItemStack result = build(items, rule.resultGetter());
            if (result == null) return "предмет результата не создан (" + rule.resultGetter() + ")";

            ItemStack filler = shards.values().iterator().hasNext() ? shards.values().iterator().next() : null;
            for (int slot = 0; slot < 9; slot++) {
                grid.setItem(slot, filler == null ? null : filler.clone());
            }

            for (Map.Entry<Integer, String> required : rule.slots().entrySet()) {
                String wanted = required.getValue();
                ItemStack ingredient = wanted.startsWith("item:")
                        ? build(items, wanted.substring("item:".length()))
                        : shards.get(wanted);
                if (ingredient == null) return "не найден ингредиент " + wanted;

                if (wrongIngredients) {
                    ItemStack other = differentShard(shards, wanted);
                    if (other == null) return "нечем подменить ингредиент " + wanted;
                    ingredient = other;
                }
                grid.setItem(required.getKey(), ingredient.clone());
            }

            grid.setResult(result.clone());
            BossLand.getRecipeManager().restrictCraftingForBossLandItems(grid);

            ItemStack after = grid.getResult();
            boolean kept = after != null && after.getType() != Material.AIR;
            if (!wrongIngredients && !kept) return "валидатор отбросил результат правильно собранного рецепта";
            if (wrongIngredients && kept) return "валидатор пропустил рецепт из неправильных осколков";
            return null;
        } catch (Throwable t) {
            return "исключение при проверке: " + t;
        }
    }

    private static ItemStack build(BossLandItems items, String getter) {
        try {
            Object value = BossLandItems.class.getMethod(getter).invoke(items);
            return value instanceof ItemStack stack ? stack : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static ItemStack differentShard(Map<String, ItemStack> shards, String wanted) {
        for (Map.Entry<String, ItemStack> entry : shards.entrySet()) {
            if (!entry.getKey().equals(wanted)) return entry.getValue();
        }
        return null;
    }

    /** Ищет в дропе боссов реальные ItemStack'и осколков, сопоставляя их по имени из lang.yml. */
    private static Map<String, ItemStack> collectShards(BossLand plugin) {
        Map<String, ItemStack> found = new LinkedHashMap<>();
        ConfigurationSection bosses = plugin.getConfig().getConfigurationSection("bosses");
        if (bosses == null) return found;

        for (String boss : bosses.getKeys(false)) {
            ConfigurationSection loot = bosses.getConfigurationSection(boss + ".loot");
            if (loot == null) continue;
            for (String lootId : loot.getKeys(false)) {
                String name = loot.getString(lootId + ".name");
                if (name == null) continue;
                String shardKey = matchShardKey(plugin, name);
                if (shardKey == null || found.containsKey(shardKey)) continue;
                ItemStack stack = BossLand.getItemsManager().getItem(boss, lootId);
                if (stack != null && stack.getItemMeta() != null) found.put(shardKey, stack);
            }
        }
        return found;
    }

    private static String matchShardKey(BossLand plugin, String displayName) {
        String normalized = normalize(displayName);
        for (String key : SHARD_KEYS) {
            String lang = normalize(plugin.getBossConfig().getLang(key));
            if (lang != null && lang.equals(normalized)) return key;
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? null : value.replace('&', '§').trim().toLowerCase(Locale.ROOT);
    }

    private BossLandSelftest() {
    }
}
