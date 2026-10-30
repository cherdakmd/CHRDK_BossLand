package com.twistylime.bossLand.guidebook.pages;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.core.BossLandRecipes;
import com.twistylime.bossLand.core.BossLandShrines;
import com.twistylime.bossLand.guidebook.PaginatedMenu;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtility;
import com.twistylime.bossLand.guidebook.search.SearchPromptManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;

public class IndividualBossRecipePageMenu extends PaginatedMenu {
    public IndividualBossRecipePageMenu(PlayerMenuUtility playerMenuUtility) {
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName() {
        String key = super.playerMenuUtility.getBossRecipeToShow();
        if (key == null) return "BossLand | Рецепт";
        return "BossLand | " + key.toUpperCase().replace('_', ' ');
    }

    @Override
    public int getSlots() {
        return 54;
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        ItemStack itemClicked = e.getCurrentItem();
        Player playerWhoClicked = (Player) e.getWhoClicked();
        if(itemClicked != null){
            switch (itemClicked.getType()){
                case BARRIER:
                    playerWhoClicked.closeInventory();
                    break;
                case NAME_TAG:
                    SearchPromptManager.begin(playerWhoClicked);
                    break;
                case WRITABLE_BOOK:
                    new BossesPageMenu(playerMenuUtility).open();
                    break;
                default:
                    // предмет призыва: открываем страницу его крафта
                    String itemRecipe = recipeKeyOf(itemClicked);
                    if (itemRecipe == null)
                        break;
                    playerMenuUtility.setItemRecipeToShow(itemRecipe);
                    new IndividualItemRecipePageMenu(playerMenuUtility).open();
                    break;
            }
        }
    }

    @Override
    public void setMenuItems() {
        boolean requireShrine = BossLand.getPlugin().getConfig().getBoolean("rituals.requireShrine", false);
        String recipeKey = super.playerMenuUtility.getBossRecipeToShow();
        if (recipeKey == null) {
            addBigMenuBorder();
            inventory.setItem(4, createItem(Material.BARRIER, "§cРецепт не найден", false));
            return;
        }
        BossLandShrines shrinesManager = BossLand.getShrinesManager();
        Map<String, Object> bossRecipe = shrinesManager.getShrineRecipe(recipeKey);

        if (bossRecipe == null) {
            addBigMenuBorder();
            inventory.setItem(4, createItem(Material.BARRIER, "§cРецепт не найден в конфиге", false));
            return;
        }

        addBigMenuBorder();

        if(recipeKey.contains("god") || recipeKey.contains("devil") || recipeKey.contains("death")){
            addBossRecipeTemplateForTopTier();

            ItemStack resultBoss = (ItemStack) bossRecipe.get("result");
            ItemStack recipeItem = (ItemStack) bossRecipe.get("recipe");
            ItemStack instructionsItem = (ItemStack) bossRecipe.get("instructions");


            inventory.setItem(20,recipeItem);
            inventory.setItem(22,instructionsItem);
            inventory.setItem(24,resultBoss);

        }else{
            addBossRecipeTemplate();

            int[] reservedSlotsForRecipe = {10,11,12,19,20,21,28,29,30};

            // Показываем то, что игроку реально нужно сделать: если святилище строиить не надо —
            // сетка крафта предмета призыва, иначе — схема постройки из recipes.yml
            BossLandRecipes recipesManager = BossLand.getRecipeManager();
            String activatorId = shrineActivatorId(recipeKey);
            String itemRecipeKey = recipesManager == null ? null : recipesManager.recipeKeyFor(activatorId);
            Map<String, Object> craftRecipe = itemRecipeKey == null || recipesManager == null
                    ? null : recipesManager.getRecipes(itemRecipeKey);

            if (!requireShrine && craftRecipe != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> craftDetails = (Map<String, Object>) craftRecipe.get("recipe");
                @SuppressWarnings("unchecked")
                List<List<String>> craftShape = (List<List<String>>) craftDetails.get("shape");
                @SuppressWarnings("unchecked")
                Map<String, ItemStack> craftIngredients = (Map<String, ItemStack>) craftDetails.get("items");

                for (int row = 0; row < craftShape.size(); row++) {
                    for (int col = 0; col < craftShape.get(row).size(); col++) {
                        int slotIndex = reservedSlotsForRecipe[row * 3 + col];
                        String key = craftShape.get(row).get(col);

                        if (key.equalsIgnoreCase(" ")) continue; // skip empty spaces

                        ItemStack ingItem = craftIngredients.get(key.toLowerCase());
                        if (ingItem != null) {
                            inventory.setItem(slotIndex, ingItem);
                        }
                    }
                }
            } else if (bossRecipe.get("recipe") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> recipeMap = (Map<String, Object>) bossRecipe.get("recipe");
                @SuppressWarnings("unchecked")
                List<List<String>> shape = (List<List<String>>) recipeMap.get("shape");
                @SuppressWarnings("unchecked")
                Map<String, ItemStack> ingredients = (Map<String, ItemStack>) recipeMap.get("items");

                for (int row = 0; row < shape.size(); row++) {
                    for (int col = 0; col < shape.get(row).size(); col++) {
                        int slotIndex = reservedSlotsForRecipe[row * 3 + col]; // map shape[row][col] → slot
                        String key = shape.get(row).get(col);

                        if (key.equalsIgnoreCase(" ")) continue; // skip empty spaces

                        ItemStack ingItem = ingredients.get(key.toLowerCase());
                        if (ingItem != null) {
                            inventory.setItem(slotIndex, ingItem);
                        }
                    }
                }
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> activatorMap = (Map<String, Object>) bossRecipe.get("activator");
            if (activatorMap != null) {
                ItemStack activatorItem = (ItemStack) activatorMap.get("item");
                if (activatorItem != null) {
                    // по клику открываем страницу крафта этого предмета
                    inventory.setItem(23, itemRecipeKey == null ? activatorItem
                            : withRecipeKey(activatorItem, itemRecipeKey));
                }
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) bossRecipe.get("result");
            if (resultMap != null) {
                ItemStack resultItem = (ItemStack) resultMap.get("item");
                if (resultItem != null) {
                    inventory.setItem(25, resultItem);
                }
            }
        }

        @SuppressWarnings("unchecked")
        List<ItemStack> loot = (List<ItemStack>) bossRecipe.get("loot");

        if (loot != null) {
            for (int i = 0; i < loot.size(); i++) {
                if (i > 8) break;
                ItemStack lootItem = loot.get(i);
                if (lootItem != null) {
                    inventory.setItem(45 + i, loot.get(i));
                }
            }
        }
    }
    /** Идентификатор предмета призыва из записи святилища (например SLIME_CORE). */
    private String shrineActivatorId(String recipeKey) {
        if (recipeKey == null)
            return null;
        ConfigurationSection shrines = BossLand.getPlugin().getBossConfig()
                .getRecipeConfiguration("shrine_recipes");
        return shrines == null ? null : shrines.getString(recipeKey + ".activator.item");
    }

    /** Ключ рецепта предмета, записанный в сам предмет, или {@code null}. */
    private String recipeKeyOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;
        NamespacedKey key = new NamespacedKey(BossLand.getPlugin(), "data");
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private ItemStack withRecipeKey(ItemStack item, String recipeKey) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        NamespacedKey key = new NamespacedKey(BossLand.getPlugin(), "data");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, recipeKey);
        item.setItemMeta(meta);
        return item;
    }
}
