package com.twistylime.bossLand.core;

import com.twistylime.bossLand.config.BossLandConfiguration;
import com.twistylime.bossLand.utility.CompatibilityResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

import static org.bukkit.Bukkit.getServer;

public class BossLandRecipes {

    private final JavaPlugin plugin;
    private final BossLandConfiguration config;
    private final BossLandItems itemManager;
    private final ConfigurationSection recipesSection;
    private final BossLandLoot lootManager;

    public BossLandRecipes(JavaPlugin plugin, BossLandConfiguration config, BossLandItems itemManager, ConfigurationSection recipesSection, BossLandLoot lootManager) {
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.recipesSection = recipesSection;
        this.lootManager = lootManager;
    }

    public void addRecipes(ConfigurationSection recipesSection) {
        if (recipesSection == null) {
            plugin.getLogger().warning("В конфиге нет секции item_recipes!");
            return;
        }

        for (String recipeName : recipesSection.getKeys(false)) {
            ConfigurationSection recipeData = recipesSection.getConfigurationSection(recipeName);
            if (recipeData == null) continue;

            // ===== Result =====
            String resultItemName = Objects.requireNonNull(recipeData.getConfigurationSection("result")).getString("item");
            int resultAmount = Objects.requireNonNull(recipeData.getConfigurationSection("result")).getInt("amount", 1);
            if(resultItemName == null) return;
            ItemStack resultItem = getResultItem(resultItemName, false);
            if (resultItem == null) {
                plugin.getLogger().warning("Не найден кастомный предмет: " + resultItemName);
                continue;
            }
            resultItem.setAmount(resultAmount);

            // ===== Shape =====
            java.util.List<String> shapeList = recipeData.getStringList("shape");
            if (shapeList.size() != 3) {
                plugin.getLogger().warning("Неверная форма рецепта: " + recipeName);
                continue;
            }
            String[] shape = shapeList.toArray(new String[0]);

            // ===== Create ShapedRecipe =====
            NamespacedKey key = new NamespacedKey(plugin, recipeName.toLowerCase());
            ShapedRecipe shapedRecipe = new ShapedRecipe(key, resultItem);
            shapedRecipe.shape(shape);

            // ===== Ingredients =====
            ConfigurationSection ingredientsSection = recipeData.getConfigurationSection("ingredients");
            if (ingredientsSection != null) {
                for (String charKey : ingredientsSection.getKeys(false)) {
                    String materialName = ingredientsSection.getString(charKey);
                    if(materialName == null) return;
                    if(materialName.equals("AIR")) continue;
                    Material mat = CompatibilityResolver.resolveMaterial(materialName);
                    shapedRecipe.setIngredient(charKey.charAt(0), mat);
                }
            }

            // ===== Register Recipe =====
            Bukkit.addRecipe(shapedRecipe);
        }
    }

    public Map<String, Object> getRecipes(String itemName){
        if (recipesSection == null) {
            plugin.getLogger().warning("В конфиге нет секции item_recipes!");
            return null;
        }

        ConfigurationSection requiredItemRecipe = (ConfigurationSection) recipesSection.get(itemName);
        if (requiredItemRecipe == null) {
            plugin.getLogger().warning("В конфиге не найден рецепт для предмета!");
            return null;
        }

        Map<String, Object> recipeMap = new HashMap<>();

        // ------------------- RESULT -------------------
        ConfigurationSection resultSec = requiredItemRecipe.getConfigurationSection("result");
        ItemStack resultItem = null;
        if (resultSec != null) {
            String item = resultSec.getString("item", "STONE");
            if(item != null){
                resultItem = getResultItem(item, true);
            }
        }

        recipeMap.put("result", resultItem);

        // ------------------- RECIPE (SHAPE + INGREDIENTS) -------------------
        Map<String, Object> recipeDetails = new HashMap<>();

        // Shape
        List<String> shapeList = requiredItemRecipe.getStringList("shape");
        List<List<String>> shapedGrid = new ArrayList<>();
        for (String row : shapeList) {
            List<String> rowChars = new ArrayList<>();
            for (char c : row.toCharArray()) {
                rowChars.add(String.valueOf(c).toLowerCase()); // normalize to lowercase
            }
            shapedGrid.add(rowChars);
        }
        recipeDetails.put("shape", shapedGrid);

        // Ingredients
        Map<String, ItemStack> ingredientItems = new HashMap<>();
        ConfigurationSection ingSec = requiredItemRecipe.getConfigurationSection("ingredients");
        if (ingSec != null) {
            for (String key : ingSec.getKeys(false)) {
                String mat = ingSec.getString(key,"STONE");
                if(mat != null){
                    ItemStack ingItem = getResultItem(mat, true);
                    ingredientItems.put(key.toLowerCase(), ingItem);
                }
            }
        }
        recipeDetails.put("items", ingredientItems);

        recipeMap.put("recipe", recipeDetails);

        return recipeMap;
    }

    // ------------------------------------------------------------------
    //  Ограничение крафта: предметы призыва собираются только из осколков
    // ------------------------------------------------------------------

    /**
     * Материал рецепта -> ключ lang.yml с именем кастомного предмета, который обязан лежать в слоте.
     * Раньше осколки искались по внутренним именам лута (WHITE_BOSS_SHARD и т.п.), которых в конфиге
     * уже нет: ингредиенты в книге-руководстве оставались пустыми, а проверка крафта пропускала слоты.
     */
    private static final Map<String, String> CUSTOM_INGREDIENTS = Map.ofEntries(
            Map.entry("QUARTZ", "items.whiteshard"),
            Map.entry("GREEN_DYE", "items.greenshard"),
            Map.entry("CLAY_BALL", "items.greyshard"),
            Map.entry("COAL", "items.blackshard"),
            Map.entry("RED_DYE", "items.redshard"),
            Map.entry("BROWN_DYE", "items.brownshard"),
            Map.entry("EMERALD", "items.emeraldshard"),
            Map.entry("YELLOW_DYE", "items.goldshard"),
            Map.entry("LAPIS_LAZULI", "items.blueshard"),
            Map.entry("FIRE_CORAL", "items.demonicshard"),
            Map.entry("ENCHANTED_BOOK", "items.knowledgebook"),
            Map.entry("ENCHANTED_GOLDEN_APPLE", "items.forbiddenfruit"),
            Map.entry("ICE", "items.frostcore"),
            Map.entry("PRISMARINE_SHARD", "items.abyssalscale"),
            Map.entry("MAGMA_CREAM", "items.infernalcore"),
            Map.entry("ENDER_PEARL", "items.endercrystalshard"));

    /**
     * Правило крафта: что должно получиться и какие кастомные предметы обязаны лежать в сетке.
     * Строится из recipes.yml, поэтому правка рецепта автоматически правит и проверку.
     */
    public record CraftGuard(String name, ItemStack result, Map<Integer, ItemStack> ingredients) {
    }

    private List<CraftGuard> craftGuards;

    /** @return сколько рецептов предметов призыва описано в recipes.yml */
    public int recipeCount() {
        return recipesSection == null ? 0 : recipesSection.getKeys(false).size();
    }

    /** @return ключи всех рецептов предметов призыва — в том порядке, в каком они в recipes.yml */
    public List<String> recipeKeys() {
        return recipesSection == null ? List.of() : List.copyOf(recipesSection.getKeys(false));
    }

    /**
     * Ключ рецепта по идентификатору результата: {@code SLIME_CORE} → {@code slime_core}.
     * Нужен, чтобы из страницы босса открыть страницу крафта его предмета призыва.
     */
    public String recipeKeyFor(String resultItemId) {
        if (resultItemId == null || resultItemId.isBlank() || recipesSection == null)
            return null;
        for (String key : recipesSection.getKeys(false)) {
            String result = recipesSection.getString(key + ".result.item");
            if (result != null && result.equalsIgnoreCase(resultItemId))
                return key;
        }
        return null;
    }

    /**
     * Что можно скрафтить из этого предмета: отображаемые имена результатов тех рецептов,
     * где этот предмет лежит в сетке. Строится из recipes.yml, поэтому не расходится с крафтом.
     */
    public List<String> craftsFrom(ItemStack ingredient) {
        List<String> names = new ArrayList<>();
        String wanted = displayName(ingredient);
        if (wanted == null || recipesSection == null)
            return names;
        for (String key : recipesSection.getKeys(false)) {
            ConfigurationSection recipe = recipesSection.getConfigurationSection(key);
            if (recipe == null)
                continue;
            ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
            if (ingredients == null)
                continue;
            boolean used = false;
            for (String charKey : ingredients.getKeys(false)) {
                String material = ingredients.getString(charKey);
                if (material == null || material.equalsIgnoreCase("AIR"))
                    continue;
                ItemStack custom = customIngredientFor(material);
                if (custom != null && wanted.equals(displayName(custom))) {
                    used = true;
                    break;
                }
            }
            if (!used)
                continue;
            String resultItemName = recipe.getString("result.item", "");
            if (resultItemName.isBlank())
                continue;
            ItemStack result = getResultItem(resultItemName, false);
            String name = displayName(result);
            if (name != null && !names.contains(name))
                names.add(name);
        }
        return names;
    }

    /** @return правила крафта, собранные из recipes.yml (слоты 0–8 — сетка верстака) */
    public List<CraftGuard> craftGuards() {
        if (craftGuards != null) return craftGuards;
        List<CraftGuard> guards = new ArrayList<>();
        if (recipesSection == null) return craftGuards = guards;

        for (String recipeName : recipesSection.getKeys(false)) {
            ConfigurationSection recipe = recipesSection.getConfigurationSection(recipeName);
            if (recipe == null) continue;
            ConfigurationSection resultSection = recipe.getConfigurationSection("result");
            ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
            List<String> shape = recipe.getStringList("shape");
            if (resultSection == null || ingredients == null || shape.size() != 3) continue;

            String resultItemName = resultSection.getString("item", "");
            if (resultItemName.isBlank()) continue;
            ItemStack result = getResultItem(resultItemName, false);
            String resultName = displayName(result);
            if (resultName == null) continue;

            Map<Integer, ItemStack> required = new LinkedHashMap<>();
            for (int row = 0; row < 3; row++) {
                String line = shape.get(row);
                for (int column = 0; column < 3 && column < line.length(); column++) {
                    String material = ingredients.getString(String.valueOf(line.charAt(column)));
                    if (material == null || material.equalsIgnoreCase("AIR")) continue;
                    ItemStack custom = customIngredientFor(material);
                    if (custom == null || displayName(custom) == null) continue;
                    required.put(row * 3 + column, custom);
                }
            }
            if (!required.isEmpty()) {
                guards.add(new CraftGuard(recipeName, result, Map.copyOf(required)));
            }
        }
        return craftGuards = guards;
    }

    /** Кэш: имя предмета в игре -> предмет из дропа боссов. */
    private Map<String, ItemStack> itemsByDisplayName;

    /** Все именованные предметы из дропа боссов: имя в игре -> предмет. */
    private Map<String, ItemStack> lootByDisplayName() {
        if (itemsByDisplayName != null) return itemsByDisplayName;

        Map<String, ItemStack> found = new LinkedHashMap<>();
        ConfigurationSection bosses = plugin.getConfig().getConfigurationSection("bosses");
        if (bosses != null) {
            for (String boss : bosses.getKeys(false)) {
                ConfigurationSection loot = bosses.getConfigurationSection(boss + ".loot");
                if (loot == null) continue;
                for (String lootId : loot.getKeys(false)) {
                    if (loot.getString(lootId + ".name") == null) continue;
                    ItemStack stack = itemManager.getItem(boss, lootId);
                    String display = displayName(stack);
                    if (display != null) found.putIfAbsent(display, stack);
                }
            }
        }
        return itemsByDisplayName = found;
    }

    /** Кастомный предмет BossLand, который должен лежать вместо материала рецепта, либо {@code null}. */
    private ItemStack customIngredientFor(String materialName) {
        if (materialName == null) return null;
        Material material = CompatibilityResolver.resolveMaterial(materialName.toUpperCase(Locale.ROOT));
        if (material == null) return null;

        String langKey = CUSTOM_INGREDIENTS.get(material.name());
        if (langKey == null) return null;

        // осколки и книгу знаний ищем среди настоящего дропа боссов
        ItemStack dropped = lootByDisplayName().get(config.getLang(langKey));
        return dropped != null ? dropped : craftedIngredient(langKey);
    }

    /** Составные предметы (ядро льда, чешуя бездны и т.п.) крафтятся, а не падают с боссов. */
    private ItemStack craftedIngredient(String langKey) {
        return switch (langKey) {
            case "items.frostcore" -> itemManager.getFrostCore();
            case "items.abyssalscale" -> itemManager.getAbyssalScale();
            case "items.infernalcore" -> itemManager.getInfernalCore();
            case "items.endercrystalshard" -> itemManager.getEnderCrystalShard();
            case "items.forbiddenfruit" -> itemManager.getGodItem();
            default -> null;
        };
    }

    /**
     * Проверяет результат крафта по правилам из recipes.yml. Обычные предметы не ограничиваются.
     *
     * @param result итоговый предмет
     * @param grid   содержимое сетки 3×3 (индексы 0–8, как в верстаке)
     * @return {@code true}, если крафт разрешён
     */
    public boolean isValidCraft(ItemStack result, ItemStack[] grid) {
        String resultName = displayName(result);
        if (resultName == null) return true;

        for (CraftGuard guard : craftGuards()) {
            if (!resultName.equals(displayName(guard.result()))) continue;
            for (Map.Entry<Integer, ItemStack> required : guard.ingredients().entrySet()) {
                int slot = required.getKey();
                ItemStack item = slot < grid.length ? grid[slot] : null;
                if (isInvalidShard(item, displayName(required.getValue()))) return false;
            }
            return true;
        }
        return true;
    }

    public void restrictCraftingForBossLandItems(CraftingInventory ci) {
        try {
            ItemStack result = ci.getResult();
            if (result == null) return;

            ItemStack[] grid = new ItemStack[9];
            int size = Math.min(9, ci.getSize());
            for (int slot = 0; slot < size; slot++) {
                grid[slot] = ci.getItem(slot);
            }
            if (!isValidCraft(result, grid)) ci.setResult(null);
        } catch (Exception localException) {
            plugin.getLogger().log(Level.WARNING, "Ошибка при ограничении крафта предметами-заменителями вместо осколков.");
        }
    }

    private static String displayName(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) return null;
        return stack.getItemMeta().getDisplayName();
    }

    public void disableCrafterRecipes() {
        try {
            Class<?> crafterCraftEventClass = Class.forName("org.bukkit.event.block.CrafterCraftEvent");

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) crafterCraftEventClass;

            Listener dummyListener = new Listener() {};

            EventExecutor executor = (listener, event) -> {
                try {
                    Method getBlockMethod = event.getClass().getMethod("getBlock");
                    Method getRecipeMethod = event.getClass().getMethod("getRecipe");
                    Method getResultMethod = event.getClass().getMethod("getResult");
                    Method setCancelledMethod = event.getClass().getMethod("setCancelled", boolean.class);

                    Object block = getBlockMethod.invoke(event);
                    Object recipe = getRecipeMethod.invoke(event);
                    ItemStack result = (ItemStack) getResultMethod.invoke(event);

                    if (recipe != null && result != null) {
                        Method getStateMethod = block.getClass().getMethod("getState");
                        Object blockState = getStateMethod.invoke(block);
                        Method getInventoryMethod = blockState.getClass().getMethod("getInventory");
                        Object inventory = getInventoryMethod.invoke(blockState);

                        if (result.getItemMeta() != null) {
                            validateCrafterRecipe((Inventory) inventory, result, setCancelledMethod, event);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Ошибка в обработчике события Crafter: " + e.getMessage());
                }
            };

            getServer().getPluginManager().registerEvent(
                    eventClass,
                    dummyListener,
                    EventPriority.NORMAL,
                    executor,
                    plugin
            );

        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("События Crafter не поддерживаются в этой версии сервера");
        }
    }

    private void validateCrafterRecipe(Inventory inv, ItemStack result, Method setCancelledMethod, Object event) throws Exception {
        ItemStack[] grid = new ItemStack[9];
        int size = Math.min(9, inv.getSize());
        for (int slot = 0; slot < size; slot++) {
            grid[slot] = inv.getItem(slot);
        }
        if (!isValidCraft(result, grid)) {
            setCancelledMethod.invoke(event, true);
        }
    }

    private boolean isInvalidShard(ItemStack item, String expectedName) {
        if (item == null || item.getType() == Material.AIR) return true;
        if (expectedName == null) return false;
        String actual = displayName(item);
        return actual == null || !actual.equals(expectedName);
    }

    private ItemStack getResultItem(String name, boolean isGuideRecipe) {
        ItemStack crafted = craftedItemById(name);
        if (crafted != null) return crafted;

        if (isGuideRecipe) {
            // в рецептах материал означает кастомный предмет BossLand (осколок, ядро, книгу знаний)
            ItemStack ingredient = customIngredientFor(name);
            if (ingredient != null) return ingredient;
        }

        return new ItemStack(CompatibilityResolver.resolveMaterial(name), 1);
    }

    /** Предметы BossLand, которые создаёт сам плагин. */
    private ItemStack craftedItemById(String id) {
        if (id == null) return null;
        return switch (id) {
            case "DEATH_NOTE" -> itemManager.getDeathItem();
            case "FORBIDDEN_FRUIT" -> itemManager.getGodItem();
            case "ABHORRENT_FRUIT" -> itemManager.getDevilItem();
            case "BELL_OF_DOOM" -> itemManager.getIllagerItem();
            case "BOOK_OF_SPELLS" -> itemManager.getWizardItem();
            case "POTION_OF_GIANT_GROWTH" -> itemManager.getGiantIem();
            case "ELDER_EGG" -> itemManager.getDragonItem();
            case "NATURES_ESSENCE" -> itemManager.getNaturesEssence();
            case "FROST_CORE" -> itemManager.getFrostCore();
            case "ABYSSAL_SCALE" -> itemManager.getAbyssalScale();
            case "INFERNAL_CORE" -> itemManager.getInfernalCore();
            case "ENDER_CRYSTAL_SHARD" -> itemManager.getEnderCrystalShard();
            // предметы призыва 3.8: строить святилище не нужно, босс призывается самим предметом
            case "SLIME_CORE" -> itemManager.getSlimeCore();
            case "PANDA_CHARM" -> itemManager.getPandaCharm();
            case "BUNNY_FOOT" -> itemManager.getBunnyFoot();
            case "UNDEAD_SIGIL" -> itemManager.getUndeadSigil();
            case "WITHER_SIGIL" -> itemManager.getWitherSigil();
            case "GHAST_SIGIL" -> itemManager.getGhastSigil();
            case "ANGER_SEAL" -> itemManager.getAngerSeal();
            case "DEMON_SEAL" -> itemManager.getDemonSeal();
            case "AETHER_FRUIT" -> itemManager.getAetherFruit();
            case "PHARAOH_FRUIT" -> itemManager.getPharaohFruit();
            case "DROWNED_FRUIT" -> itemManager.getDrownedFruit();
            default -> null;
        };
    }
}