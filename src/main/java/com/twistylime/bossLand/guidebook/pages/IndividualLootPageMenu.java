package com.twistylime.bossLand.guidebook.pages;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.core.BossLandLoot;
import com.twistylime.bossLand.core.BossLandRecipes;
import com.twistylime.bossLand.core.BossLandShrines;
import com.twistylime.bossLand.guidebook.PaginatedMenu;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtility;
import com.twistylime.bossLand.guidebook.search.SearchPromptManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Страница вещи из дропа боссов: кто её роняет и как её скрафтить.
 *
 * Разделы «Предметы» и «Броня» показывали вещь, но клик по ней не делал ничего: узнать,
 * с какого босса она падает, можно было только перебором. Теперь связь двусторонняя.
 */
public class IndividualLootPageMenu extends PaginatedMenu {

    private static final int[] BOSS_SLOTS = {28, 29, 30, 31, 32, 33, 34};

    public IndividualLootPageMenu(PlayerMenuUtility playerMenuUtility) {
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName() {
        String name = playerMenuUtility.getLootToShow();
        if (name == null) return "BossLand | Предмет";
        return "BossLand | " + ChatColor.stripColor(name);
    }

    @Override
    public int getSlots() {
        return 54;
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        ItemStack itemClicked = e.getCurrentItem();
        Player playerWhoClicked = (Player) e.getWhoClicked();
        if (itemClicked == null)
            return;
        switch (itemClicked.getType()) {
            case BARRIER -> playerWhoClicked.closeInventory();
            case NAME_TAG -> SearchPromptManager.begin(playerWhoClicked);
            case WRITABLE_BOOK -> {
                // назад — в тот раздел, откуда эта вещь: броня или предметы
                if (isArmorByName(playerMenuUtility.getLootToShow()))
                    new ArmorsPageMenu(playerMenuUtility).open();
                else
                    new ItemsPageMenu(playerMenuUtility).open();
            }
            default -> {
                String kind = tagOf(itemClicked, "kind");
                String data = tagOf(itemClicked, "data");
                if (kind == null || data == null)
                    return;
                switch (kind) {
                    case "boss" -> {
                        playerMenuUtility.setBossRecipeToShow(data);
                        new IndividualBossRecipePageMenu(playerMenuUtility).open();
                    }
                    case "item" -> {
                        playerMenuUtility.setItemRecipeToShow(data);
                        new IndividualItemRecipePageMenu(playerMenuUtility).open();
                    }
                    case "shard" -> {
                        playerMenuUtility.setShardToShow(data);
                        new IndividualShardPageMenu(playerMenuUtility).open();
                    }
                    default -> {
                    }
                }
            }
        }
    }

    @Override
    public void setMenuItems() {
        addBigMenuBorder();

        ItemStack loot = findLoot(playerMenuUtility.getLootToShow());
        if (loot == null) {
            inventory.setItem(22, createItem(Material.BARRIER, "§cПредмет не найден", false));
            return;
        }

        inventory.setItem(13, loot);
        inventory.setItem(22, createItem(Material.PAPER, "§6§lПадает с этих боссов", false));

        List<ItemStack> bosses = bossesDropping(loot);
        if (bosses.isEmpty()) {
            inventory.setItem(31, createItem(Material.BARRIER,
                    "§7Ни один босс из руководства не роняет этот предмет", false));
        } else {
            for (int i = 0; i < bosses.size() && i < BOSS_SLOTS.length; i++) {
                inventory.setItem(BOSS_SLOTS[i], bosses.get(i));
            }
        }

        // если вещь ещё и крафтится — показываем, где её рецепт
        BossLandRecipes recipesManager = BossLand.getRecipeManager();
        String recipeKey = recipesManager == null ? null : recipesManager.recipeKeyByResult(loot);
        if (recipeKey != null) {
            inventory.setItem(37, createItem(Material.PAPER, "§6§lКрафтится", false));
            Map<String, Object> recipe = recipesManager.getRecipes(recipeKey);
            if (recipe != null && recipe.get("result") instanceof ItemStack result) {
                inventory.setItem(40, withTag(result.clone(), "item", recipeKey));
            }
        }
    }

    /** Сама вещь по отображаемому имени — из тех же списков, что показаны в разделах. */
    private ItemStack findLoot(String displayName) {
        if (displayName == null)
            return null;
        BossLandLoot lootManager = BossLand.getLootManager();
        if (lootManager == null)
            return null;

        List<ItemStack> all = new ArrayList<>(lootManager.getWeaponsAndItems());
        all.addAll(lootManager.getArmors());
        for (ItemStack item : all) {
            if (displayName.equals(displayName(item)))
                return item;
        }
        return null;
    }

    /** Боссы, в дропе которых есть эта вещь. */
    private List<ItemStack> bossesDropping(ItemStack loot) {
        List<ItemStack> found = new ArrayList<>();
        String wanted = displayName(loot);
        if (wanted == null)
            return found;

        BossLandShrines shrinesManager = BossLand.getShrinesManager();
        ConfigurationSection shrineRecipes = BossLand.getPlugin().getBossConfig()
                .getRecipeConfiguration("shrine_recipes");
        if (shrinesManager == null || shrineRecipes == null)
            return found;

        for (String key : shrineRecipes.getKeys(false)) {
            Map<String, Object> bossRecipe = shrinesManager.getShrineRecipe(key);
            if (bossRecipe == null)
                continue;
            if (!dropsLoot(bossRecipe, wanted))
                continue;
            ItemStack icon = bossIcon(bossRecipe);
            if (icon != null)
                found.add(withTag(icon, "boss", key));
        }
        return found;
    }

    private boolean dropsLoot(Map<String, Object> bossRecipe, String wanted) {
        if (!(bossRecipe.get("loot") instanceof List<?> drops))
            return false;
        for (Object drop : drops) {
            if (drop instanceof ItemStack item && wanted.equals(displayName(item)))
                return true;
        }
        return false;
    }

    /** Иконка босса — та же, что в разделе «Боссы». */
    private ItemStack bossIcon(Map<String, Object> bossRecipe) {
        Object result = bossRecipe.get("result");
        if (result instanceof ItemStack icon)
            return icon.clone();
        if (result instanceof Map<?, ?> resultMap && resultMap.get("item") instanceof ItemStack icon)
            return icon.clone();
        return null;
    }

    private ItemStack withTag(ItemStack item, String kind, String data) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        meta.getPersistentDataContainer().set(key("kind"), PersistentDataType.STRING, kind);
        meta.getPersistentDataContainer().set(key("data"), PersistentDataType.STRING, data);
        item.setItemMeta(meta);
        return item;
    }

    private String tagOf(ItemStack item, String tag) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null)
            return null;
        return meta.getPersistentDataContainer().get(key(tag), PersistentDataType.STRING);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(BossLand.getPlugin(), name);
    }

    private boolean isArmorByName(String displayName) {
        return displayName != null && BossLandLoot.isArmor(findLoot(displayName));
    }

    private static String displayName(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null)
            return null;
        String name = stack.getItemMeta().getDisplayName();
        return name == null || name.isBlank() ? null : name;
    }

    /**
     * Помечает вещь в дропе босса так, чтобы клик открывал её страницу:
     * осколок — страницу осколка, всё остальное — страницу предмета.
     */
    public static ItemStack mark(ItemStack loot) {
        if (loot == null || loot.getItemMeta() == null)
            return loot;
        String name = loot.getItemMeta().getDisplayName();
        if (name == null || name.isBlank())
            return loot;

        ItemStack marked = loot.clone();
        ItemMeta meta = marked.getItemMeta();
        if (meta == null)
            return marked;
        String kind = BossLand.getPlugin().isBossShard(loot) ? "shard" : "loot";
        meta.getPersistentDataContainer().set(new NamespacedKey(BossLand.getPlugin(), "kind"),
                PersistentDataType.STRING, kind);
        meta.getPersistentDataContainer().set(new NamespacedKey(BossLand.getPlugin(), "data"),
                PersistentDataType.STRING, name);
        marked.setItemMeta(meta);
        return marked;
    }
}
