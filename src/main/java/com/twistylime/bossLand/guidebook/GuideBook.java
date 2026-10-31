package com.twistylime.bossLand.guidebook;

import com.twistylime.bossLand.BossLand;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Книга руководства.
 *
 * Раньше это была пустая писаная книга: ни названия, ни автора, ни страниц. Меню открывалось
 * по правому клику, но из-за того, что книга была пустой, игрок видел именно пустоту — и если
 * правый клик не срабатывал, руководство было не открыть никак. Теперь у книги есть содержание,
 * а меню открывается и по клику, и сразу командой /bl guide.
 */
public final class GuideBook {

    private GuideBook() {
    }

    /** Книга с оглавлением. Даже если правый клик не сработает, она не пустая. */
    public static ItemStack create() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK, 1);
        if (!(book.getItemMeta() instanceof BookMeta meta))
            return book;

        meta.setDisplayName(BossLand.getPlugin().getBossConfig().getLang("items.guidebook"));
        meta.setLore(List.of(
                "§7Держи в руке и нажми ПКМ — откроется руководство",
                "§7или просто напиши §f/bl guide"));

        // без названия и автора клиент показывает писаную книгу пустой
        meta.setTitle("Полное руководство по BossLand");
        meta.setAuthor("TwistyLime");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        meta.setPages(pages());

        meta.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) 1);
        book.setItemMeta(meta);
        return book;
    }

    /** Эта ли вещь — книга руководства. */
    public static boolean isGuideBook(ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK)
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
        if (meta.getPersistentDataContainer().get(key(), PersistentDataType.BYTE) != null)
            return true;
        // книги, выданные старыми версиями: метки в них нет, узнаём по имени из lang.yml
        String name = meta.getDisplayName();
        return name != null && name.equals(BossLand.getPlugin().getBossConfig().getLang("items.guidebook"));
    }

    private static NamespacedKey key() {
        return new NamespacedKey(BossLand.getPlugin(), "guidebook");
    }

    private static List<String> pages() {
        return List.of(
                "§c§lBossLand§r\n" +
                        "§7Руководство по плагину\n\n" +
                        "§0Держи книгу в руке и нажми §8ПКМ§0 — откроется меню руководства.\n" +
                        "§0Можно и без книги: §8/bl guide",

                "§6§lРазделы§r\n\n" +
                        "§0→ §8Боссы§0 — как призвать и что с них падает\n" +
                        "§0→ §8Предметы§0 — уникальный лут\n" +
                        "§0→ §8Осколки§0 — из чего что крафтится\n" +
                        "§0→ §8Рецепты§0 — все рецепты призыва\n" +
                        "§0→ §8Броня§0 — доспехи боссов",

                "§6§lКак призвать босса§r\n\n" +
                        "§01. Скрафти предмет призыва из осколков (раздел «Рецепты»).\n" +
                        "§02. Возьми его в руку.\n" +
                        "§03. Нажми ПКМ — босс появится там, где ты кликнул.\n\n" +
                        "§7Строить святилище не нужно.",

                "§6§lКоманды§r\n\n" +
                        "§8/bl guide§0 — книга и меню\n" +
                        "§8/bl search <слово>§0 — найти босса или предмет\n" +
                        "§8/bl help§0 — все команды\n\n" +
                        "§7Кнопка «Поиск» есть и в самом меню."
        );
    }
}
