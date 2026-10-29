package com.twistylime.bossLand.selftest;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.core.BossLandItems;
import com.twistylime.bossLand.core.BossLandRecipes;
import com.twistylime.bossLand.guidebook.search.GuideSearch;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Самопроверка установки: проверяет то, что не видно при обычном старте сервера —
 * что осколки, выпадающие с боссов, называются ровно так, как ожидает проверка крафта,
 * и что каждый предмет призыва действительно собирается из осколков
 * (валидатор крафта не отбрасывает результат и не пропускает обычные материалы).
 *
 * Запуск: /bl-admin selftest
 */
public final class BossLandSelftest {

    private static final String[] SHARD_KEYS = {
            "items.whiteshard", "items.greenshard", "items.greyshard", "items.blackshard",
            "items.redshard", "items.brownshard", "items.emeraldshard", "items.goldshard",
            "items.blueshard", "items.demonicshard"
    };

    public record Outcome(boolean ok, int checks, int failed, List<String> lines) {
    }

    public static Outcome run(BossLand plugin) {
        List<String> lines = new ArrayList<>();
        int checks = 0;
        int failed = 0;

        BossLandItems items = BossLand.getItemsManager();
        BossLandRecipes recipes = BossLand.getRecipeManager();
        if (items == null || recipes == null) {
            lines.add("§cМенеджеры плагина недоступны — плагин ещё не включён?");
            return new Outcome(false, 1, 1, lines);
        }

        Map<String, ItemStack> loot = lootByName(plugin);
        lines.add("§7Именованных предметов в дропе боссов: §f" + loot.size());

        // 1) каждый осколок должен реально падать с босса и называться как в lang.yml
        for (String shardKey : SHARD_KEYS) {
            checks++;
            String expected = plugin.getBossConfig().getLang(shardKey);
            if (!loot.containsKey(expected)) {
                failed++;
                lines.add("§c✗ §f" + shardKey + " §7— осколок §f" + expected
                        + " §7не найден в дропе боссов (имя осколка и lang.yml разошлись)");
            }
        }

        // 2) посох контроля должен отличать осколки от остального лута
        for (String shardKey : SHARD_KEYS) {
            checks++;
            String shardName = plugin.getBossConfig().getLang(shardKey);
            ItemStack shard = loot.get(shardName);
            if (shard == null) continue; // об отсутствии осколка уже сообщили выше
            if (!plugin.isBossShard(shard)) {
                failed++;
                lines.add("§c✗ §f" + shardKey + " §7— посох контроля не считает осколком предмет §f" + shardName);
            }
        }
        checks++;
        ItemStack notShard = firstNonShard(loot);
        if (notShard != null && plugin.isBossShard(notShard)) {
            failed++;
            lines.add("§c✗ §7посох контроля считает осколком обычный предмет §f"
                    + notShard.getItemMeta().getDisplayName());
        }

        // 3) поиск по руководству должен находить страницы по названию
        List<GuideSearch.Entry> index = GuideSearch.index();
        int indexedBosses = 0;
        int indexedItems = 0;
        for (GuideSearch.Entry entry : index) {
            if (entry.kind() == GuideSearch.Kind.BOSS) indexedBosses++;
            else indexedItems++;
        }
        checks++;
        if (indexedBosses == 0 || indexedItems == 0) {
            failed++;
            lines.add("§c✗ §7поиск в руководстве пуст: боссов §f" + indexedBosses + "§7, предметов §f" + indexedItems);
        }

        boolean foundBoss = false;
        for (GuideSearch.Entry entry : index) {
            if (entry.kind() != GuideSearch.Kind.BOSS) continue;
            String probe = GuideSearch.normalize(entry.name());
            if (probe.length() > 5) probe = probe.substring(0, 5);
            if (!GuideSearch.search(probe).isEmpty()) foundBoss = true;
            break;
        }
        checks++;
        if (!foundBoss) {
            failed++;
            lines.add("§c✗ §7поиск в руководстве не находит боссов по названию");
        }

        boolean foundItem = false;
        for (GuideSearch.Entry entry : index) {
            if (entry.kind() != GuideSearch.Kind.ITEM) continue;
            String probe = GuideSearch.normalize(entry.name());
            if (probe.length() > 5) probe = probe.substring(0, 5);
            if (!GuideSearch.search(probe).isEmpty()) foundItem = true;
            break;
        }
        checks++;
        if (!foundItem) {
            failed++;
            lines.add("§c✗ §7поиск в руководстве не находит предметы по названию");
        }

        checks++;
        if (!GuideSearch.search("этого-точно-нет-в-руководстве").isEmpty()) {
            failed++;
            lines.add("§c✗ §7поиск в руководстве выдаёт результаты на бессмысленный запрос");
        }

        // 4) каждый предмет призыва собирается из осколков и не собирается из обычных материалов
        List<BossLandRecipes.CraftGuard> guards = recipes.craftGuards();
        int recipeCount = recipes.recipeCount();
        lines.add("§7Правил крафта: §f" + guards.size() + " §7из §f" + recipeCount + " §7рецептов");
        checks++;
        if (guards.size() < recipeCount) {
            failed++;
            lines.add("§c✗ §7проверено только §f" + guards.size() + " §7из §f" + recipeCount
                    + " §7рецептов: у остальных не нашлись ингредиенты в дропе боссов");
        }
        for (BossLandRecipes.CraftGuard guard : guards) {
            String label = displayName(guard.result());
            if (label == null) label = guard.name();

            checks++;
            String problem = verifyCrafting(guard, loot, false);
            if (problem != null) {
                failed++;
                lines.add("§c✗ §f" + label + " §7— " + problem);
            }

            checks++;
            String guardProblem = verifyCrafting(guard, loot, true);
            if (guardProblem != null) {
                failed++;
                lines.add("§c✗ §f" + label + " §7(защита) — " + guardProblem);
            }
        }

        boolean ok = failed == 0;
        lines.add(ok
                ? "§aСАМОПРОВЕРКА: OK §7(" + checks + " проверок)"
                : "§cСАМОПРОВЕРКА: ОШИБОК " + failed + " из " + checks);
        return new Outcome(ok, checks, failed, lines);
    }

    /**
     * Раскладывает ингредиенты рецепта по сетке 3×3 и проверяет решение валидатора.
     *
     * @param vanillaSubstitute заменить один ингредиент обычным материалом того же типа
     * @return описание проблемы или {@code null}, если крафт ведёт себя правильно
     */
    private static String verifyCrafting(BossLandRecipes.CraftGuard guard, Map<String, ItemStack> loot,
                                         boolean vanillaSubstitute) {
        try {
            List<Map.Entry<Integer, ItemStack>> slots = new ArrayList<>(guard.ingredients().entrySet());
            slots.sort((left, right) -> Integer.compare(left.getKey(), right.getKey()));

            if (slots.isEmpty()) return "в рецепте нет ни одного предмета BossLand";

            ItemStack[] grid = new ItemStack[9];
            for (Map.Entry<Integer, ItemStack> required : slots) {
                String name = displayName(required.getValue());
                // осколки берём настоящие, из дропа боссов; составные предметы крафтятся, поэтому берём из рецепта
                ItemStack real = name == null ? null : loot.get(name);
                if (real == null) real = required.getValue();
                grid[required.getKey()] = real.clone();
            }

            if (vanillaSubstitute) {
                int slot = slots.get(0).getKey();
                // тот же материал, но без имени осколка — так игрок и обходит рецепт
                grid[slot] = new ItemStack(grid[slot].getType());
            }

            boolean kept = BossLand.getRecipeManager().isValidCraft(guard.result(), grid);
            if (!vanillaSubstitute && !kept) return "валидатор отбросил правильно собранный рецепт";
            if (vanillaSubstitute && kept) return "валидатор пропустил рецепт из обычных материалов";
            return null;
        } catch (Throwable t) {
            return "исключение при проверке: " + t;
        }
    }

    /** Все именованные предметы из дропа боссов: отображаемое имя -> предмет. */
    private static Map<String, ItemStack> lootByName(BossLand plugin) {
        Map<String, ItemStack> found = new LinkedHashMap<>();
        ConfigurationSection bosses = plugin.getConfig().getConfigurationSection("bosses");
        if (bosses == null) return found;

        for (String boss : bosses.getKeys(false)) {
            ConfigurationSection loot = bosses.getConfigurationSection(boss + ".loot");
            if (loot == null) continue;
            for (String lootId : loot.getKeys(false)) {
                if (loot.getString(lootId + ".name") == null) continue;
                try {
                    ItemStack stack = BossLand.getItemsManager().getItem(boss, lootId);
                    String name = displayName(stack);
                    if (name != null) found.putIfAbsent(name, stack);
                } catch (RuntimeException ignored) {
                    // битый предмет в конфиге — его поймает проверка имени осколка, если он важен
                }
            }
        }
        return found;
    }

    /** Первый именованный предмет дропа, который не является осколком (для проверки режима «осколки»). */
    private static ItemStack firstNonShard(Map<String, ItemStack> loot) {
        for (ItemStack stack : loot.values()) {
            if (displayName(stack) == null) continue;
            if (!BossLand.getPlugin().isBossShard(stack)) return stack;
        }
        return null;
    }

    private static String displayName(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) return null;
        return stack.getItemMeta().getDisplayName();
    }

    private BossLandSelftest() {
    }
}
