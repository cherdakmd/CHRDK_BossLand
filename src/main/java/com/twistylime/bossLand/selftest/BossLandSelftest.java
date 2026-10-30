package com.twistylime.bossLand.selftest;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.core.BossLandItems;
import com.twistylime.bossLand.core.BossLandLoot;
import com.twistylime.bossLand.core.BossLandRecipes;
import com.twistylime.bossLand.core.BossLandShrines;
import com.twistylime.bossLand.core.BossLandShrines.Ritual;
import com.twistylime.bossLand.guidebook.search.GuideSearch;
import com.twistylime.bossLand.utility.CompatibilityResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Самопроверка установки: проверяет то, что не видно при обычном старте сервера —
 * что осколки, выпадающие с боссов, называются ровно так, как ожидает проверка крафта,
 * и что каждый предмет призыва действительно собирается из осколков
 * (валидатор крафта не отбрасывает результат и не пропускает обычные материалы),
 * а также что ритуалы призыва ждут ровно те предметы и структуры, которые плагин выдаёт
 * и показывает в руководстве.
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

    /**
     * Сетка верстака по рецепту из руководства.
     *
     * @param named {@code true} — положить настоящие осколки, {@code false} — те же материалы без имени
     */
    @SuppressWarnings("unchecked")
    private static ItemStack[] craftGrid(Map<String, Object> recipe, boolean named) {
        ItemStack[] grid = new ItemStack[9];
        Object details = recipe.get("recipe");
        if (!(details instanceof Map<?, ?> recipeDetails))
            return grid;
        Object shape = recipeDetails.get("shape");
        Object items = recipeDetails.get("items");
        if (!(shape instanceof List<?> rows) || !(items instanceof Map<?, ?> ingredientItems))
            return grid;
        for (int row = 0; row < rows.size() && row < 3; row++) {
            Object cells = rows.get(row);
            if (!(cells instanceof List<?> columns))
                continue;
            for (int column = 0; column < columns.size() && column < 3; column++) {
                Object ingredient = ingredientItems.get(String.valueOf(columns.get(column)).toLowerCase(Locale.ROOT));
                if (!(ingredient instanceof ItemStack item) || item.getType() == Material.AIR)
                    continue;
                grid[row * 3 + column] = named ? item.clone() : new ItemStack(item.getType(), 1);
            }
        }
        return grid;
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

        // 5) призыв боссов: каждый ритуал требует крафтовый предмет, а не постройку и не ванильный материал.
        //    Здесь ловится главная беда призыва: код ждёт одно имя предмета, а плагин выдаёт другое,
        //    предмет нельзя скрафтить, или два босса делят один и тот же предмет призыва.
        BossLandShrines shrines = BossLand.getShrinesManager();
        List<Ritual> rituals = shrines == null ? Collections.emptyList() : shrines.rituals();
        ConfigurationSection shrineSection = plugin.getBossConfig().getRecipeConfiguration("shrine_recipes");
        boolean requireShrine = plugin.getConfig().getBoolean("rituals.requireShrine", false);
        Set<String> craftable = craftableItemIds(plugin);
        Map<String, String> activatorOwner = new LinkedHashMap<>();
        lines.add("§7Ритуалов призыва: §f" + rituals.size());
        checks++;
        if (rituals.isEmpty()) {
            failed++;
            lines.add("§c✗ §7не зарегистрировано ни одного ритуала призыва");
        }
        Set<String> covered = new HashSet<>();
        for (Ritual ritual : rituals) {
            // 5.1 босс ритуала есть в config.yml
            checks++;
            if (plugin.getConfig().getConfigurationSection("bosses." + ritual.boss()) == null) {
                failed++;
                lines.add("§c✗ §f" + ritual.label() + " §7— в config.yml нет босса §f" + ritual.boss());
            }

            // 5.2 предмет призыва: материал и имя совпадают с тем, что реально выдаёт плагин
            checks++;
            ItemStack activator = shrines == null ? null : shrines.getMentionedItem(ritual.activatorId(),
                    ritual.activatorAmount());
            if (ritual.activatorMaterial() == null || activator == null
                    || activator.getType() != ritual.activatorMaterial()) {
                failed++;
                lines.add("§c✗ §f" + ritual.label() + " §7— ритуал ждёт предмет §f" + ritual.activatorId()
                        + " §7(материал §f" + ritual.activatorMaterial() + "§7), а плагин выдаёт §f"
                        + (activator == null ? "ничего" : activator.getType().name()));
            }
            if (ritual.customActivator()) {
                checks++;
                String expected = displayName(activator);
                if (expected == null || expected.isBlank() || !expected.equals(ritual.activatorName())) {
                    failed++;
                    lines.add("§c✗ §f" + ritual.label() + " §7— имя предмета призыва разошлось: ритуал ждёт §f"
                            + ritual.activatorName() + "§7, плагин выдаёт §f" + expected);
                }
            }

            // 5.3 структура блоков совпадает с той, что нарисована в руководстве
            //     (проверяем, только когда постройка святилища включена настройкой rituals.requireShrine)
            if (requireShrine) {
                checks++;
                String blocksProblem = verifyShrineBlocks(shrineSection, ritual);
                if (blocksProblem != null) {
                    failed++;
                    lines.add("§c✗ §f" + ritual.label() + " §7— " + blocksProblem);
                }
            }

            // 5.4 призыв идёт крафтовым предметом BossLand, а не ванильным материалом
            checks++;
            if (!ritual.customActivator()) {
                failed++;
                lines.add("§c✗ §f" + ritual.label() + " §7— призыв требует ванильный предмет §f"
                        + ritual.activatorId() + "§7, а должен крафтовый предмет призыва");
            }

            // 5.5 предмет призыва должен реально крафтиться: иначе босса не призвать
            checks++;
            if (!craftable.contains(ritual.activatorId())) {
                failed++;
                lines.add("§c✗ §f" + ritual.label() + " §7— предмет призыва §f" + ritual.activatorId()
                        + " §7нельзя скрафтить: его нет в результатах item_recipes");
            }

            // 5.6 в руководстве показан тот же активатор, что требует ритуал
            checks++;
            ConfigurationSection entry = shrineSection == null ? null
                    : shrineSection.getConfigurationSection(ritual.recipe());
            String bookActivator = entry == null ? null : entry.getString("activator.item");
            if (bookActivator == null || !bookActivator.equalsIgnoreCase(ritual.activatorId())) {
                failed++;
                lines.add("§c✗ §f" + ritual.label() + " §7— в руководстве активатор §f" + bookActivator
                        + "§7, а ритуал требует §f" + ritual.activatorId());
            }
            if (entry != null)
                covered.add(ritual.recipe());

            // 5.8 предмет призыва не должен принадлежать двум боссам сразу
            String previous = activatorOwner.put(ritual.activatorId(), ritual.boss());
            checks++;
            if (previous != null) {
                failed++;
                lines.add("§c✗ §7предмет призыва §f" + ritual.activatorId() + " §7призывает и §f" + previous
                        + "§7, и §f" + ritual.boss() + " §7— у каждого босса должен быть свой предмет");
            }
        }

        // 5.7 у каждой страницы руководства со структурой должен быть работающий ритуал
        if (shrineSection != null) {
            for (String key : shrineSection.getKeys(false)) {
                if (shrineSection.getStringList(key + ".shape").isEmpty())
                    continue;
                checks++;
                if (!covered.contains(key)) {
                    failed++;
                    lines.add("§c✗ §f" + key + " §7— страница руководства без работающего ритуала");
                }
            }
        }

        // 6) осколок должен во что-то крафтиться: иначе он бесполезен, а игрок про это не узнает
        for (String shardKey : SHARD_KEYS) {
            checks++;
            ItemStack shard = loot.get(plugin.getBossConfig().getLang(shardKey));
            if (shard == null) continue; // об отсутствии осколка сообщили в проверке 1
            List<String> crafts = recipes.craftsFrom(shard);
            if (crafts.isEmpty()) {
                failed++;
                lines.add("§c✗ §f" + shardKey + " §7— осколок §f" + displayName(shard)
                        + " §7не идёт ни в один рецепт из recipes.yml");
            }
        }

        // 7) у каждого рецепта должен быть понятный результат: иначе страница в руководстве пустая
        ConfigurationSection itemRecipes = plugin.getBossConfig().getRecipeConfiguration("item_recipes");
        if (itemRecipes != null) {
            for (String key : itemRecipes.getKeys(false)) {
                checks++;
                Map<String, Object> recipe = recipes.getRecipes(key);
                ItemStack result = recipe == null ? null : (ItemStack) recipe.get("result");
                if (result == null || displayName(result) == null) {
                    failed++;
                    lines.add("§c✗ §f" + key
                            + " §7— рецепт не показывается в руководстве: у результата нет названия");
                }
            }
        }

        // 8) рецепт из обычных материалов должен не только отбрасываться, но и объясняться:
        //    иначе игрок видит пустой верстак и не понимает, что нужны именно осколки
        for (BossLandRecipes.CraftGuard guard : recipes.craftGuards()) {
            checks++;
            ItemStack[] plainGrid = new ItemStack[9];
            for (Map.Entry<Integer, ItemStack> required : guard.ingredients().entrySet()) {
                ItemStack requiredItem = required.getValue();
                plainGrid[required.getKey()] = new ItemStack(requiredItem.getType(), requiredItem.getAmount());
            }
            if (recipes.isValidCraft(guard.result(), plainGrid)) {
                failed++;
                lines.add("§c✗ §f" + guard.name() + " §7— обычные материалы проходят крафт, хотя должны требоваться осколки");
            } else if (!recipes.isCompleteButInvalidCraft(guard.result(), plainGrid)) {
                failed++;
                lines.add("§c✗ §f" + guard.name() + " §7— игрок не получит подсказку, почему крафт не прошёл");
            }
        }

        // 9) у осколка должна быть страница в книге: иначе клик по нему ведёт в пустоту
        if (shrines != null && shrineSection != null) {
            for (String shardKey : SHARD_KEYS) {
                checks++;
                String shardName = plugin.getBossConfig().getLang(shardKey);
                boolean dropped = false;
                for (String bossKey : shrineSection.getKeys(false)) {
                    Map<String, Object> bossRecipe = shrines.getShrineRecipe(bossKey);
                    if (bossRecipe == null) continue;
                    if (!(bossRecipe.get("loot") instanceof List<?> drops)) continue;
                    for (Object drop : drops) {
                        if (drop instanceof ItemStack item && shardName.equals(displayName(item))) {
                            dropped = true;
                            break;
                        }
                    }
                    if (dropped) break;
                }
                if (!dropped) {
                    failed++;
                    lines.add("§c✗ §f" + shardKey + " §7— осколок §f" + shardName
                            + " §7не падает ни с одного босса: на странице осколка будет пусто");
                }
            }
        }

        // 10) подбор рецептов в верстаке: настоящие осколки дают результат,
        //     обычные материалы — не дают ничего, но объясняются
        Set<String> guarded = new HashSet<>();
        for (BossLandRecipes.CraftGuard guard : recipes.craftGuards())
            guarded.add(guard.name());
        for (String key : recipes.recipeKeys()) {
            Map<String, Object> recipe = recipes.getRecipes(key);
            if (recipe == null || recipe.get("result") == null)
                continue;

            checks++;
            BossLandRecipes.CraftMatch good = recipes.matchCraft(craftGrid(recipe, true), 3);
            if (good == null || !good.namesValid() || !key.equals(good.recipeKey())) {
                failed++;
                lines.add("§c✗ §f" + key + " §7— рецепт из настоящих осколков не собирается: верстак молча пустеет");
                continue;
            }
            ItemStack crafted = recipes.resultOf(key);
            if (crafted == null) {
                failed++;
                lines.add("§c✗ §f" + key + " §7— у рецепта нет результата, хотя узор находится");
                continue;
            }

            if (!guarded.contains(key))
                continue; // рецепту не нужны осколки с именем — обычные материалы в нём законны
            checks++;
            BossLandRecipes.CraftMatch wrong = recipes.matchCraft(craftGrid(recipe, false), 3);
            if (wrong == null || wrong.namesValid()) {
                failed++;
                lines.add("§c✗ §f" + key + " §7— обычные материалы проходят крафт, либо подсказка не сработает");
            }
        }

        // 11) весь именованный лут боссов должен быть виден в руководстве: иначе разделы
        //     «Осколки», «Брони» и «Предметы» молча теряют вещи новых боссов
        BossLandLoot lootManager = BossLand.getLootManager();
        if (lootManager != null) {
            Set<String> inBook = new HashSet<>();
            for (ItemStack item : lootManager.getShards()) addName(inBook, item);
            for (ItemStack item : lootManager.getArmors()) addName(inBook, item);
            for (ItemStack item : lootManager.getWeaponsAndItems()) addName(inBook, item);

            List<String> missing = new ArrayList<>();
            for (String name : loot.keySet()) {
                if (!inBook.contains(name)) missing.add(name);
            }
            checks++;
            if (!missing.isEmpty()) {
                failed++;
                lines.add("§c✗ §7в руководстве не показаны предметы дропа: §f" + String.join(", ", missing));
            }
        }

        boolean ok = failed == 0;
        lines.add(ok
                ? "§aСАМОПРОВЕРКА: OK §7(" + checks + " проверок)"
                : "§cСАМОПРОВЕРКА: ОШИБОК " + failed + " из " + checks);
        return new Outcome(ok, checks, failed, lines);
    }

    /** Идентификаторы предметов, которые реально можно скрафтить (результаты item_recipes). */
    private static Set<String> craftableItemIds(BossLand plugin) {
        Set<String> ids = new HashSet<>();
        ConfigurationSection items = plugin.getBossConfig().getRecipeConfiguration("item_recipes");
        if (items == null) return ids;
        for (String recipe : items.getKeys(false)) {
            String result = items.getString(recipe + ".result.item");
            if (result != null && !result.isBlank()) ids.add(result);
        }
        return ids;
    }

    /**
     * Сверяет структуру, которую ждёт ритуал, с сеткой из recipes.yml — той самой, что нарисована
     * в руководстве. Сетку разбираем заново, не reused код плагина: иначе рассинхрон не найти.
     *
     * @return описание расхождения или {@code null}, если структура совпадает
     */
    private static String verifyShrineBlocks(ConfigurationSection section, Ritual ritual) {
        if (section == null)
            return "в recipes.yml нет секции shrine_recipes";
        ConfigurationSection entry = section.getConfigurationSection(ritual.recipe());
        if (entry == null)
            return "в recipes.yml нет записи «" + ritual.recipe() + "»";
        List<String> shape = entry.getStringList("shape");
        if (shape.size() != 3)
            return "в recipes.yml у записи «" + ritual.recipe() + "» сетка не 3×3";
        if (ritual.blocks().length != 3)
            return "у ритуала не задана структура блоков";

        for (int row = 0; row < 3; row++) {
            String expected = shrineBlockRow(entry, shape.get(row));
            if (expected == null)
                return "в recipes.yml не читается строка " + (row + 1) + " структуры «" + ritual.recipe() + "»";
            if (!expected.equals(ritual.blocks()[row])) {
                return "структура разошлась с руководством: в строке " + (row + 1) + " ритуал ждёт §f"
                        + ritual.blocks()[row] + "§7, а в книге §f" + expected;
            }
        }
        return null;
    }

    /** Одна строка структуры в том виде, в котором её проверяет ритуал: MATERIAL:MATERIAL:MATERIAL. */
    private static String shrineBlockRow(ConfigurationSection entry, String shapeRow) {
        ConfigurationSection ingredients = entry.getConfigurationSection("ingredients");
        if (ingredients == null || shapeRow.length() != 3)
            return null;
        StringBuilder line = new StringBuilder();
        for (int col = 0; col < 3; col++) {
            String id = ingredients.getString(String.valueOf(shapeRow.charAt(col)));
            if (id == null)
                return null;
            // в книге ингредиенты записаны предметами, а в мире стоят блоками
            Material material = switch (id.toUpperCase(Locale.ROOT)) {
                case "REDSTONE" -> CompatibilityResolver.resolveMaterial("REDSTONE_WIRE", "REDSTONE");
                case "CARROT" -> CompatibilityResolver.resolveMaterial("CARROTS", "CARROT");
                default -> CompatibilityResolver.resolveMaterial(id.toUpperCase(Locale.ROOT));
            };
            if (material == null)
                return null;
            if (col > 0)
                line.append(':');
            line.append(material.name());
        }
        return line.toString();
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

    private static void addName(Set<String> names, ItemStack item) {
        String name = displayName(item);
        if (name != null) names.add(name);
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
