package com.twistylime.bossLand.guidebook.pages;

import com.twistylime.bossLand.guidebook.PaginatedMenu;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtility;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;


public class GuideMainMenu extends PaginatedMenu {

    public GuideMainMenu(PlayerMenuUtility playerMenuUtility) {
        super(playerMenuUtility);
    }

    @Override
    public String getMenuName() {
        return "BossLand | Руководство";
    }

    @Override
    public int getSlots() {
        return 27;
    }

    @Override
    public void handleMenu(InventoryClickEvent e) {
        ItemStack itemClicked = e.getCurrentItem();
        Player p = (Player) e.getWhoClicked();
        if(itemClicked == null) return;
        switch (itemClicked.getType()){
            case BARRIER:
                p.closeInventory();
                break;
            case NAME_TAG:
                p.sendMessage("Эта функция находится в разработке и будет доступна в ближайших обновлениях.");
                p.closeInventory();
                break;
            case FIRE_CORAL:
                new BossesPageMenu(playerMenuUtility).open();
                break;
            case BLAZE_ROD:
                new ItemsPageMenu(playerMenuUtility).open();
                break;
            case LAPIS_LAZULI:
                new ShardsPageMenu(playerMenuUtility).open();
                break;
            case ENCHANTED_GOLDEN_APPLE:
                new RecipesPageMenu(playerMenuUtility).open();
                break;
            case NETHERITE_CHESTPLATE:
                new ArmorsPageMenu(playerMenuUtility).open();
                break;
            case PAPER:
                new CreditsPageMenu(playerMenuUtility).open();
                break;
            case NETHER_STAR:
                new InfoPageMenu(playerMenuUtility).open();
                break;
            default:
                break;
        }
    }

    @Override
    public void setMenuItems() {
        addSmallMenuBorder();

        inventory.setItem(10, decorateItem(Material.FIRE_CORAL, "Боссы", true, "Призвать и сражаться", "Мощные кастомные боссы"));
        inventory.setItem(11, decorateItem(Material.BLAZE_ROD, "Предметы", false, "Уникальные дропы", "Создайте легендарное снаряжение"));
        inventory.setItem(12, decorateItem(Material.LAPIS_LAZULI, "Осколки", true, "Осколки боссов", "Используются для крафта"));
        inventory.setItem(13, decorateItem(Material.ENCHANTED_GOLDEN_APPLE, "Рецепты", false, "Узнайте как крафтить", "Мощные предметы"));
        inventory.setItem(14, decorateItem(Material.NETHERITE_CHESTPLATE, "Броня", true, "Наборы брони", "С особыми способностями"));
        inventory.setItem(15, decorateItem(Material.PAPER, "Авторы", false, "Разработано", "§eTwistyLime"));
        inventory.setItem(16, decorateItem(Material.NETHER_STAR, "О плагине", false, "§7BossLand", "§7Версия 2.4"));
    }


    private ItemStack decorateItem(Material material, String displayName, boolean glint, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6§l" + displayName);

            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add("§7" + line);
            }
            meta.setLore(loreList);

            if (glint) {
                meta.addEnchant(Enchantment.MENDING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

}
