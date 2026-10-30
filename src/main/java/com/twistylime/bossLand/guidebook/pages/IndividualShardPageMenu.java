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
 * Страница одного осколка: с каких боссов он падает и что из него крафтится.
 *
 * Раньше клик по осколку в разделе «Осколки» не делал ничего, и узнать, где его добыть,
 * можно было только через поиск. Теперь разделы книги связаны: босс → осколок → рецепт.
 */
public class IndividualShardPageMenu extends PaginatedMenu {

    /** Слоты иконок боссов и предметов, которые крафтятся из осколка. */
    private static final int[] BOSS_SLOTS = {28, 29, 30, 31, 32, 33, 34};
    private static final int[] CRAFT_SLOTS = {38, 39, 40, 41, 42, 43};

    public IndividualShardPageMenu(PlayerMenuUtility playerMenuUtility) {
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName() {
        String name = playerMenuUtility.getShardToShow();
        if (name == null) return "BossLand | Осколок";
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
            case WRITABLE_BOOK -> new ShardsPageMenu(playerMenuUtility).open();
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

        ItemStack shard = findShard(playerMenuUtility.getShardToShow());
        if (shard == null) {
            inventory.setItem(22, createItem(Material.BARRIER, "§cОсколок не найден", false));
            return;
        }

        inventory.setItem(13, shard);
        inventory.setItem(22, createItem(Material.PAPER, "§6§lПадает с этих боссов", false));

        List<ItemStack> bosses = bossesDropping(shard);
        if (bosses.isEmpty()) {
            // осколок Края не падает с боссов — его крафтят, и это надо сказать прямо
            boolean crafted = isCrafted(shard);
            inventory.setItem(31, createItem(crafted ? Material.CRAFTING_TABLE : Material.BARRIER,
                    crafted ? "§7Этот осколок не падает с боссов — его крафтят"
                            : "§7Ни один босс из руководства не роняет этот осколок", false));
        } else {
            for (int i = 0; i < bosses.size() && i < BOSS_SLOTS.length; i++) {
                inventory.setItem(BOSS_SLOTS[i], bosses.get(i));
            }
        }

        inventory.setItem(37, createItem(Material.PAPER, "§6§lИдёт в крафт", false));

        List<ItemStack> crafts = craftsFromShard(shard);
        if (crafts.isEmpty()) {
            inventory.setItem(40, createItem(Material.BARRIER,
                    "§7Этот осколок не участвует ни в одном рецепте", false));
        } else {
            for (int i = 0; i < crafts.size() && i < CRAFT_SLOTS.length; i++) {
                inventory.setItem(CRAFT_SLOTS[i], crafts.get(i));
            }
        }
    }

    /** Сам осколок по отображаемому имени — из того же списка, что показан в разделе «Осколки». */
    private ItemStack findShard(String displayName) {
        if (displayName == null)
            return null;
        BossLandLoot lootManager = BossLand.getLootManager();
        if (lootManager == null)
            return null;
        for (ItemStack shard : lootManager.getShards()) {
            ItemMeta meta = shard.getItemMeta();
            if (meta == null)
                continue;
            if (displayName.equals(meta.getDisplayName()))
                return shard;
        }
        return null;
    }

    /** Боссы, в дропе которых есть этот осколок. */
    private List<ItemStack> bossesDropping(ItemStack shard) {
        List<ItemStack> found = new ArrayList<>();
        String wanted = displayName(shard);
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
            if (!dropsShard(bossRecipe, wanted))
                continue;
            ItemStack icon = bossIcon(bossRecipe, key);
            if (icon != null)
                found.add(withTag(icon, "boss", key));
        }
        return found;
    }

    private boolean dropsShard(Map<String, Object> bossRecipe, String wanted) {
        Object loot = bossRecipe.get("loot");
        if (!(loot instanceof List<?> drops))
            return false;
        for (Object drop : drops) {
            if (drop instanceof ItemStack item && wanted.equals(displayName(item)))
                return true;
        }
        return false;
    }

    /** Иконка босса — та же, что в разделе «Боссы». */
    private ItemStack bossIcon(Map<String, Object> bossRecipe, String key) {
        Object result = bossRecipe.get("result");
        if (result instanceof ItemStack icon)
            return icon.clone();
        if (result instanceof Map<?, ?> resultMap) {
            Object item = resultMap.get("item");
            if (item instanceof ItemStack icon)
                return icon.clone();
        }
        return null;
    }

    /** Есть ли рецепт, результатом которого является этот осколок. */
    private boolean isCrafted(ItemStack shard) {
        BossLandRecipes recipesManager = BossLand.getRecipeManager();
        if (recipesManager == null)
            return false;
        String wanted = displayName(shard);
        for (String key : recipesManager.recipeKeys()) {
            Map<String, Object> recipe = recipesManager.getRecipes(key);
            if (recipe == null)
                continue;
            if (recipe.get("result") instanceof ItemStack result && wanted.equals(displayName(result)))
                return true;
        }
        return false;
    }

    /** Предметы, которые крафтятся из этого осколка: по клику открывается их рецепт. */
    private List<ItemStack> craftsFromShard(ItemStack shard) {
        List<ItemStack> found = new ArrayList<>();
        BossLandRecipes recipesManager = BossLand.getRecipeManager();
        if (recipesManager == null)
            return found;
        for (String key : recipesManager.recipeKeysUsing(shard)) {
            Map<String, Object> recipe = recipesManager.getRecipes(key);
            if (recipe == null)
                continue;
            if (recipe.get("result") instanceof ItemStack result)
                found.add(withTag(result.clone(), "item", key));
        }
        return found;
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
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;
        return meta.getPersistentDataContainer().get(key(tag), PersistentDataType.STRING);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(BossLand.getPlugin(), name);
    }

    private static String displayName(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null)
            return null;
        String name = stack.getItemMeta().getDisplayName();
        return name == null || name.isBlank() ? null : name;
    }

    /** Помечает осколок в дропе босса так, чтобы клик открывал его страницу. */
    public static ItemStack markShard(ItemStack shard) {
        if (shard == null)
            return null;
        ItemStack marked = shard.clone();
        ItemMeta meta = marked.getItemMeta();
        if (meta == null)
            return marked;
        String name = meta.getDisplayName();
        if (name == null || name.isBlank())
            return marked;
        NamespacedKey kind = new NamespacedKey(BossLand.getPlugin(), "kind");
        NamespacedKey data = new NamespacedKey(BossLand.getPlugin(), "data");
        meta.getPersistentDataContainer().set(kind, PersistentDataType.STRING, "shard");
        meta.getPersistentDataContainer().set(data, PersistentDataType.STRING, name);
        marked.setItemMeta(meta);
        return marked;
    }
}
