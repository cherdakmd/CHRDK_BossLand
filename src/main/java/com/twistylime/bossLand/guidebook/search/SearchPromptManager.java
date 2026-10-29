package com.twistylime.bossLand.guidebook.search;

import com.twistylime.bossLand.BossLand;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtility;
import com.twistylime.bossLand.guidebook.menuutility.PlayerMenuUtilityManager;
import com.twistylime.bossLand.guidebook.pages.GuideMainMenu;
import com.twistylime.bossLand.guidebook.pages.SearchResultsPageMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ввод поискового запроса: после клика по «Поиску» игрок пишет название в чат,
 * следующее сообщение перехватывается и открывает результаты.
 *
 * <p>Поддерживаются оба события чата: современное {@code io.papermc.paper.event.player.AsyncChatEvent}
 * (Paper/Purpur) и классическое {@link AsyncPlayerChatEvent}. Современное подключается через рефлексию,
 * потому что плагин собирается против Spigot API, где его нет.</p>
 */
public final class SearchPromptManager {

    private static final int TIMEOUT_SECONDS = 60;
    private static final Set<String> CANCEL_WORDS = Set.of("отмена", "cancel", "выход", "exit", "нет");

    private static final Map<UUID, Long> PENDING = new ConcurrentHashMap<>();

    private SearchPromptManager() {
    }

    /** Начинает поиск: закрывает меню и ждёт сообщение игрока в чат. */
    public static void begin(Player player) {
        if (player == null) return;
        PENDING.put(player.getUniqueId(), System.currentTimeMillis());
        player.closeInventory();
        player.sendMessage("§6§lПоиск по руководству");
        player.sendMessage("§7Напишите в чат название босса или предмета.");
        player.sendMessage("§7Чтобы передумать — напишите §fотмена§7. Ждём §f" + TIMEOUT_SECONDS + " §7сек.");
    }

    public static boolean isWaiting(Player player) {
        return player != null && PENDING.containsKey(player.getUniqueId());
    }

    /** Снимает ожидание и открывает результаты (или меню руководства, если ничего не нашлось). */
    public static void complete(Player player, String message) {
        if (player == null) return;
        boolean wasWaiting = PENDING.remove(player.getUniqueId()) != null;
        if (!wasWaiting) return;
        showResults(player, message);
    }

    /**
     * Открывает страницу результатов поиска. Используется и из чата, и командой {@code /bl search}.
     *
     * @param query то, что ввёл игрок
     */
    public static void showResults(Player player, String query) {
        if (player == null) return;

        String cleaned = query == null ? "" : query.trim();
        if (cleaned.isEmpty() || CANCEL_WORDS.contains(cleaned.toLowerCase(Locale.ROOT))) {
            player.sendMessage("§7Поиск отменён.");
            reopenGuide(player);
            return;
        }

        List<GuideSearch.Entry> results = GuideSearch.search(cleaned);
        if (results.isEmpty()) {
            player.sendMessage("§7По запросу §f" + cleaned + " §7ничего не найдено.");
            reopenGuide(player);
            return;
        }

        PlayerMenuUtility utility = PlayerMenuUtilityManager.getPlayerMenuUtility(player);
        utility.setSearchQuery(cleaned);
        player.sendMessage("§aНайдено: §f" + results.size());
        new SearchResultsPageMenu(utility).open();
    }

    private static void reopenGuide(Player player) {
        if (!player.isOnline()) return;
        new GuideMainMenu(PlayerMenuUtilityManager.getPlayerMenuUtility(player)).open();
    }

    /** Убирает просроченные ожидания: игрок мог закрыть игру или просто уйти. */
    private static void expire() {
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(entry -> now - entry.getValue() > TIMEOUT_SECONDS * 1000L);
    }

    /**
     * Подключает перехват чата и таймаут. Вызывается один раз при включении плагина.
     *
     * @return {@code true}, если удалось подключить современное событие Paper
     */
    public static boolean register(BossLand plugin) {
        Bukkit.getPluginManager().registerEvents(new QuitListener(), plugin);

        boolean paperEvent = registerPaperChatEvent(plugin);
        if (!paperEvent) {
            Bukkit.getPluginManager().registerEvents(new LegacyChatListener(), plugin);
        }

        Bukkit.getScheduler().runTaskTimer(plugin, SearchPromptManager::expire, 20L * 10, 20L * 10);
        return paperEvent;
    }

    /** Paper/Purpur: io.papermc.paper.event.player.AsyncChatEvent. */
    private static boolean registerPaperChatEvent(BossLand plugin) {
        Class<?> chatEventClass;
        try {
            chatEventClass = Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
        } catch (ClassNotFoundException missing) {
            return false;
        }

        if (!Event.class.isAssignableFrom(chatEventClass)) return false;

        @SuppressWarnings("unchecked")
        Class<? extends Event> eventClass = (Class<? extends Event>) chatEventClass;

        EventExecutor executor = new EventExecutor() {
            @Override
            public void execute(Listener listener, Event event) throws EventException {
                try {
                    Object rawPlayer = event.getClass().getMethod("getPlayer").invoke(event);
                    if (!(rawPlayer instanceof Player player)) return;
                    if (!isWaiting(player)) return;

                    // у Paper это Component message(), у совместимых форков — getMessage()
                    Object component = readComponent(event);
                    String text = serializeComponent(component);

                    try {
                        event.getClass().getMethod("setCancelled", boolean.class).invoke(event, true);
                    } catch (ReflectiveOperationException ignored) {
                        // событие не отменяемое — сообщение всё равно не должно уйти дальше
                    }

                    // событие асинхронное: меню открываем в основном потоке
                    Bukkit.getScheduler().runTask(plugin, () -> complete(player, text));
                } catch (ReflectiveOperationException exception) {
                    plugin.getLogger().warning("Не удалось прочитать сообщение чата: " + exception.getMessage());
                }
            }
        };

        plugin.getServer().getPluginManager().registerEvent(
                eventClass, new Listener() { }, EventPriority.LOWEST, executor, plugin);
        return true;
    }

    /** Достаёт текст сообщения из события чата Paper любым из известных способов. */
    private static Object readComponent(Event event) throws ReflectiveOperationException {
        String[] names = {"message", "getMessage", "originalMessage"};
        for (String name : names) {
            try {
                Method method = event.getClass().getMethod(name);
                if (method.getParameterCount() != 0) continue;
                return method.invoke(event);
            } catch (NoSuchMethodException ignored) {
                // пробуем следующее имя
            }
        }
        throw new NoSuchMethodException("событие чата не отдаёт текст сообщения");
    }

    /** Adventure-компонент -> обычный текст. Класс есть только на Paper, поэтому через рефлексию. */
    private static String serializeComponent(Object component) {
        if (component == null) return "";
        if (component instanceof String text) return text;
        try {
            Class<?> serializerClass = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Object serializer = serializerClass.getMethod("plainText").invoke(null);
            Object text = serializerClass.getMethod("serialize", Class.forName("net.kyori.adventure.text.Component"))
                    .invoke(serializer, component);
            return text == null ? "" : text.toString();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return component.toString();
        }
    }

    /** Spigot и старые серверы: классическое событие чата. */
    private static final class LegacyChatListener implements Listener {
        @SuppressWarnings("deprecation")
        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncPlayerChatEvent event) {
            Player player = event.getPlayer();
            if (!isWaiting(player)) return;
            event.setCancelled(true);
            String message = event.getMessage();
            Bukkit.getScheduler().runTask(BossLand.getPlugin(), () -> complete(player, message));
        }
    }

    private static final class QuitListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            PENDING.remove(event.getPlayer().getUniqueId());
        }
    }
}
