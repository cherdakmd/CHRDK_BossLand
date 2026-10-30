package com.twistylime.bossLand.guidebook.pages;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.core.BossLandRecipes;
import com.twistylime.bossLand.guidebook.PaginatedMenu;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtility;
import com.twistylime.bossLand.guidebook.search.SearchPromptManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Map;

/**
 * Все рецепты предметов призыва. Список строится из recipes.yml: раньше он был вписан в код,
 * и из-за этого в раздел не попадали ни Ядро Льда, ни Чешуя Бездны, ни предметы призыва 3.8.
 */
public class RecipesPageMenu extends PaginatedMenu {

    public RecipesPageMenu(PlayerMenuUtility playerMenuUtility) {
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName() {
        return "BossLand | Рецепты";
    }

    @Override
    public int getSlots() {
        return 54;
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        ItemStack itemClicked = e.getCurrentItem();
        if (itemClicked == null)
            return;
        switch (itemClicked.getType()) {
            case BARRIER -> e.getWhoClicked().closeInventory();
            case NAME_TAG -> SearchPromptManager.begin((Player) e.getWhoClicked());
            case WRITABLE_BOOK -> new GuideMainMenu(playerMenuUtility).open();
            default -> {
                // ключ рецепта лежит в самом предмете: так работает любой материал, а не только
                // перечисленные в коде
                String key = recipeKeyOf(itemClicked);
                if (key == null)
                    return;
                playerMenuUtility.setItemRecipeToShow(key);
                new IndividualItemRecipePageMenu(playerMenuUtility).open();
            }
        }
    }

    @Override
    public void setMenuItems() {
        addBigMenuBorder();

        BossLandRecipes recipesManager = BossLand.getRecipeManager();
        ArrayList<ItemStack> items = new ArrayList<>();
        if (recipesManager != null) {
            for (String key : recipesManager.recipeKeys()) {
                Map<String, Object> recipe = recipesManager.getRecipes(key);
                if (recipe == null)
                    continue;
                ItemStack result = (ItemStack) recipe.get("result");
                if (result == null)
                    continue;
                items.add(addDataToItem(result, key));
            }
        }

        if (!items.isEmpty()) {
            for (int i = 0; i < getMaxItemsPerPage(); i++) {
                index = getMaxItemsPerPage() * page + i;
                if (index >= items.size()) break;
                if (items.get(index) != null) {
                    ItemStack recipeItem = items.get(index);
                    inventory.addItem(recipeItem);
                }
            }
        }
    }

    /** Ключ рецепта, записанный в предмет, или {@code null}. */
    private String recipeKeyOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;
        NamespacedKey key = new NamespacedKey(BossLand.getPlugin(), "data");
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private ItemStack addDataToItem(ItemStack itemToAddData, String data) {
        ItemMeta itemToAddDataMeta = itemToAddData.getItemMeta();
        if (itemToAddDataMeta != null) {
            NamespacedKey key = new NamespacedKey(BossLand.getPlugin(), "data");
            itemToAddDataMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, data);
        }
        itemToAddData.setItemMeta(itemToAddDataMeta);
        return itemToAddData;
    }
}
