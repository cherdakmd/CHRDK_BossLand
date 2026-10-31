package com.twistylime.bossLand.core;

import com.twistylime.bossLand.config.BossLandConfiguration;
import com.twistylime.bossLand.utility.CompatibilityResolver;
import com.twistylime.bossLand.utility.MCUtility;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

public class BossLandShrines {
    private final JavaPlugin plugin;
    private final BossLandConfiguration config;
    private final BossLandItems itemManager;
    private final BossLandBosses bossManager;
    private final ConfigurationSection recipesSection;
    /** Все ритуалы призыва: единственная таблица, по которой работают и призыв, и самопроверка. */
    private final List<Ritual> rituals;
    /** Кому и когда последний раз подсказывали, что структура святилища не та. */
    private final Map<UUID, Long> lastHint = new HashMap<>();
    /** Кто и когда призвал босса: защита от двойного срабатывания предмета призыва. */
    private final Map<UUID, Long> lastSummon = new HashMap<>();
    /** Ошибки ритуалов пишем в консоль по одному разу, чтобы не засорять логи. */
    private final Set<String> warnedRituals = new HashSet<>();

    public BossLandShrines(JavaPlugin plugin, BossLandConfiguration config, BossLandItems itemManager, BossLandBosses bossManager, ConfigurationSection recipesSection) {
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.bossManager = bossManager;
        this.recipesSection = recipesSection;
        this.rituals = buildRituals();
    }

    /** Таблица ритуалов призыва — для самопроверки и отладки. */
    public List<Ritual> rituals() {
        return rituals;
    }

    public Map<String, Object> getShrineRecipe(String bossName){
        if (recipesSection == null) {
            plugin.getLogger().warning("В конфиге нет секции shrine_recipes!");
            return null;
        }

        ConfigurationSection requiredBossShrineRecipe = (ConfigurationSection) recipesSection.get(bossName);
        if (requiredBossShrineRecipe == null) {
            plugin.getLogger().warning("В конфиге нет рецепта святилища для босса!");
            return null;
        }

        Map<String, Object> recipeMap = new HashMap<>();

        // ------------------- RESULT -------------------
        ConfigurationSection resultSec = requiredBossShrineRecipe.getConfigurationSection("result");
        ItemStack resultItem = null;
        if (resultSec != null) {
            String item = resultSec.getString("item", "STONE");
            int amount = resultSec.getInt("amount", 1);
            String displayName = resultSec.getString("display_name", null);
            List<String> lore = resultSec.getStringList("lore");
            Material material = CompatibilityResolver.resolveMaterial(item);
            resultItem = new ItemStack(material, amount);
            ItemMeta meta = resultItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                meta.addEnchant(Enchantment.MENDING,1,true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                resultItem.setItemMeta(meta);
            }
        }

        if(bossName.contains("god") || bossName.contains("devil") || bossName.contains("death")){
            recipeMap.put("result", resultItem);

            // ------------------- ACTIVATOR -------------------
            ConfigurationSection activatorSec = requiredBossShrineRecipe.getConfigurationSection("activator");
            if (activatorSec != null) {
                String item = activatorSec.getString("item", "STONE");
                int amount = activatorSec.getInt("amount", 1);

                ItemStack activatorItem = getMentionedItem(item, amount);

                recipeMap.put("recipe", activatorItem);
            }

            // ------------------- Instructor -------------------
            ConfigurationSection instructorSec = requiredBossShrineRecipe.getConfigurationSection("instructor");
            if (instructorSec != null) {
                String item = instructorSec.getString("item", "STONE");
                int amount = instructorSec.getInt("amount", 1);
                List<String> lore = instructorSec.getStringList("lore");

                ItemStack instructorItem = getMentionedItem(item, amount);
                ItemMeta meta = instructorItem.getItemMeta();
                if(meta != null){
                    meta.setLore(lore);
                    meta.setDisplayName("§6§lInstructions");
                    instructorItem.setItemMeta(meta);
                }

                recipeMap.put("instructions", instructorItem);
            }
        }
        else{
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("item", resultItem);

            recipeMap.put("result", resultMap);

            // ------------------- RECIPE (SHAPE + INGREDIENTS) -------------------
            Map<String, Object> recipeDetails = new HashMap<>();

            // Shape
            List<String> shapeList = requiredBossShrineRecipe.getStringList("shape");
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
            ConfigurationSection ingSec = requiredBossShrineRecipe.getConfigurationSection("ingredients");
            if (ingSec != null) {
                for (String key : ingSec.getKeys(false)) {
                    String mat = ingSec.getString(key,"STONE");
                    ItemStack ingItem = getMentionedItem(mat, 1); // use get item from bosslanditems class
                    ingredientItems.put(key.toLowerCase(), ingItem);
                }
            }
            recipeDetails.put("items", ingredientItems);

            recipeMap.put("recipe", recipeDetails);


            // ------------------- ACTIVATOR -------------------
            ConfigurationSection activatorSec = requiredBossShrineRecipe.getConfigurationSection("activator");
            if (activatorSec != null) {
                String item = activatorSec.getString("item", "STONE");
                int amount = activatorSec.getInt("amount", 1);

                ItemStack activatorItem = getMentionedItem(item, amount);

                Map<String, Object> activatorMap = new HashMap<>();
                activatorMap.put("item", activatorItem);

                recipeMap.put("activator", activatorMap);
            }
        }

        // ------------------- LOOT -------------------
        List<String> lootIds = requiredBossShrineRecipe.getStringList("loot");
        String lootName = requiredBossShrineRecipe.getString("lootName");

        List<ItemStack> loot = new ArrayList<>();
        for(String id: lootIds){
            // все варианты имени (мечи Гнева): иначе на странице босса был бы один случайный
            // меч из четырёх, и страница такого меча не нашла бы босса, который его роняет
            loot.addAll(itemManager.getItemVariants(lootName,id));
        }
        recipeMap.put("loot",loot);

        return recipeMap;
    }

    public ItemStack getMentionedItem(String name, int amount) {
        return switch (name) {
            case "BELL_OF_DOOM" -> itemManager.getIllagerItem();
            case "BOOK_OF_SPELLS" -> itemManager.getWizardItem();
            case "POTION_OF_GIANT_GROWTH" -> itemManager.getGiantIem();
            case "FORBIDDEN_FRUIT" -> itemManager.getGodItem();
            case "ABHORRENT_FRUIT" -> itemManager.getDevilItem();
            case "DEATH_NOTE" -> itemManager.getDeathItem();
            case "FROST_CORE" -> itemManager.getFrostCore();
            case "ABYSSAL_SCALE" -> itemManager.getAbyssalScale();
            case "INFERNAL_CORE" -> itemManager.getInfernalCore();
            case "ENDER_CRYSTAL_SHARD" -> itemManager.getEnderCrystalShard();
            case "NATURES_ESSENCE" -> itemManager.getNaturesEssence();
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
            case "FIRE" -> representFireItem();
            default -> new ItemStack(CompatibilityResolver.resolveMaterial(name), amount);
        };
    }

    private ItemStack representFireItem(){
        ItemStack fireItem = new ItemStack(Material.FLINT_AND_STEEL,1);
        ItemMeta meta = fireItem.getItemMeta();
        if(meta!= null){
            meta.addEnchant(Enchantment.MENDING,1,true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setDisplayName(ChatColor.GOLD+"Lit Fire");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Represents fire in the shrine recipe.");
            meta.setLore(lore);

            fireItem.setItemMeta(meta);
        }

        return fireItem;
    }

    /**
     * Ритуал призыва: что нужно держать в руке, в каком мире и биоме и какая структура стоит под ногами.
     * <p>
     * Раньше условия были разбросаны по длинной цепочке {@code if/else if}, а названия предметов призыва
     * вписаны прямо в код — например {@code "§5§lBook of Spells"} или {@code "§f&lЯдро Льда"}. Они разошлись
     * с lang.yml, из-за чего восемь боссов перестали призываться, а ошибка при этом молча глоталась пустым
     * {@code catch}. Теперь все ритуалы лежат в одной таблице {@link #rituals}, имя предмета берётся из
     * lang.yml через {@link BossLandItems}, а структура блоков — из recipes.yml, то есть из той же записи,
     * которую игрок видит в руководстве.
     */
    public record Ritual(String boss, String recipe, String activatorId, boolean customActivator,
                         Material activatorMaterial, String activatorName, int activatorAmount,
                         World.Environment environment, List<String> biomes, Integer minY, Integer maxY,
                         int explosion, boolean explosionFire, int boom, boolean boomFire, int lightning,
                         boolean realStrike, Sound sound, int riseY, int delay, boolean consumeBlocks,
                         boolean clearCenter, boolean needsDeadGods, String[] blocks) {

        /** Как ритуал подписывается в самопроверке и в логах. */
        public String label() {
            return boss + " (" + recipe + ")";
        }
    }

    /** Заготовка ритуала: цепочка вызовов описывает условие призыва и его эффект. */
    private final class Shrine {
        private final String boss;
        private final String recipe;
        private String activatorId;
        private boolean customActivator;
        private int activatorAmount = 1;
        private World.Environment environment;
        private List<String> biomes = List.of();
        private Integer minY;
        private Integer maxY;
        private int explosion;
        private boolean explosionFire;
        private int boom;
        private boolean boomFire;
        private int lightning;
        private boolean realStrike;
        private Sound sound;
        private int riseY;
        private int delay = 40;
        private boolean consumeBlocks = true;
        private boolean clearCenter;
        private boolean needsDeadGods;
        private String[] blocks = {};

        private Shrine(String boss, String recipe) {
            this.boss = boss;
            this.recipe = recipe;
        }

        /** Обычный (ванильный) предмет в руке: имя не проверяется, важен только материал. */
        private Shrine hand(Material material) {
            this.activatorId = material.name();
            this.customActivator = false;
            return this;
        }

        /** Предмет призыва BossLand: имя берётся из lang.yml, поэтому сверяем и материал, и имя. */
        private Shrine hand(String itemId) {
            this.activatorId = itemId;
            this.customActivator = true;
            return this;
        }

        /** Сколько предметов требуется в руке (и сколько забираем). */
        private Shrine amount(int amount) {
            this.activatorAmount = amount;
            return this;
        }

        private Shrine world(World.Environment env) {
            this.environment = env;
            return this;
        }

        /** Части названий биомов: подходит любой из перечисленных. */
        private Shrine biomes(String... names) {
            this.biomes = List.of(names);
            return this;
        }

        /** Ритуал работает только на этой высоте и ниже (например, глубоко под землёй). */
        private Shrine below(int y) {
            this.maxY = y;
            return this;
        }

        private Shrine explosion(int power, boolean fire) {
            this.explosion = power;
            this.explosionFire = fire;
            return this;
        }

        /** Серия взрывов вокруг святилища. */
        private Shrine boom(int power, boolean fire) {
            this.boom = power;
            this.boomFire = fire;
            return this;
        }

        /** Декоративные молнии вокруг святилища (огня и урона нет). */
        private Shrine lightning(int size) {
            this.lightning = size;
            return this;
        }

        /** Настоящий удар молнии в центр святилища. */
        private Shrine strike() {
            this.realStrike = true;
            return this;
        }

        private Shrine sound(Sound value) {
            this.sound = value;
            return this;
        }

        /** На сколько блоков поднять точку появления босса. */
        private Shrine rise(int blocks) {
            this.riseY = blocks;
            return this;
        }

        private Shrine delay(int ticks) {
            this.delay = ticks;
            return this;
        }

        /** Структуру не трогаем (босс появляется на ней). */
        private Shrine keepBlocks() {
            this.consumeBlocks = false;
            return this;
        }

        /** Убрать центральный блок, чтобы босс не задохнулся в собственной структуре. */
        private Shrine clearCenter() {
            this.clearCenter = true;
            return this;
        }

        /** Ритуал срабатывает только когда боги уже побеждены. */
        private Shrine needsDeadGods() {
            this.needsDeadGods = true;
            return this;
        }

        /** Структура «кольцо и центр» на случай, если в recipes.yml нет своей записи. */
        private Shrine ring(String ring, String center) {
            this.blocks = new String[] { ring + ":" + ring + ":" + ring, ring + ":" + center + ":" + ring,
                    ring + ":" + ring + ":" + ring };
            return this;
        }

        /** Структура «крестом» на случай, если в recipes.yml нет своей записи. */
        private Shrine cross(String corner, String edge, String center) {
            this.blocks = new String[] { corner + ":" + edge + ":" + corner, edge + ":" + center + ":" + edge,
                    corner + ":" + edge + ":" + corner };
            return this;
        }
    }

    /** Таблица ритуалов: единственное место, где описано, какого босса чем и где призвать. */
    private List<Ritual> buildRituals() {
        List<Shrine> shrines = List.of(
                ritual("KingSlime", "slime_king")
                        .hand("SLIME_CORE").world(World.Environment.NORMAL).biomes("SWAMP")
                        .ring("SLIME_BLOCK", "DIAMOND_BLOCK").explosion(3, false).rise(2).delay(40),
                ritual("WitherSkeletonKing", "wither_skeleton_king")
                        .hand("WITHER_SIGIL").world(World.Environment.NETHER)
                        .ring("FIRE", "WITHER_SKELETON_SKULL").explosion(2, true).delay(20),
                ritual("PapaPanda", "papa_panda")
                        .hand("PANDA_CHARM").world(World.Environment.NORMAL).biomes("BAMBOO")
                        .ring("MOSSY_COBBLESTONE", "CHISELED_STONE_BRICKS").explosion(2, false).delay(20),
                ritual("Anger", "anger")
                        .hand("ANGER_SEAL").world(World.Environment.NORMAL).below(-40)
                        .ring("COPPER_BLOCK", "IRON_BLOCK").sound(Sound.ENTITY_IRON_GOLEM_REPAIR).delay(20),
                ritual("ZombieKing", "zombie_king")
                        .hand("UNDEAD_SIGIL").world(World.Environment.NORMAL).biomes("PLAINS")
                        .ring("SOUL_SAND", "EMERALD_BLOCK").explosion(2, false).delay(20),
                ritual("EvilWizard", "evil_wizard")
                        .hand("BOOK_OF_SPELLS").world(World.Environment.NORMAL)
                        .biomes("SNOWY", "FROZEN", "JAGGED", "GROVE", "ICE")
                        .cross("REDSTONE_TORCH", "REDSTONE_WIRE", "CAMPFIRE").lightning(2).delay(20),
                ritual("IllagerKing", "illager_king")
                        .hand("BELL_OF_DOOM").world(World.Environment.NORMAL).biomes("SAVANNA")
                        .cross("REDSTONE_TORCH", "REDSTONE_WIRE", "CAMPFIRE").lightning(3).delay(20),
                ritual("Giant", "giant")
                        .hand("POTION_OF_GIANT_GROWTH").world(World.Environment.NORMAL).biomes("PLAINS")
                        .cross("REDSTONE_TORCH", "REDSTONE_WIRE", "CAMPFIRE").lightning(5).delay(20),
                ritual("Demon", "demon")
                        .hand("DEMON_SEAL")
                        .cross("REDSTONE_TORCH", "REDSTONE_WIRE", "CAMPFIRE")
                        .explosion(4, false).needsDeadGods().delay(10),
                ritual("GhastLord", "ghast_lord")
                        .hand("GHAST_SIGIL").world(World.Environment.NETHER)
                        .ring("REDSTONE_WIRE", "MAGMA_BLOCK")
                        .keepBlocks().clearCenter().boom(5, true).delay(40),
                ritual("KillerBunny", "killer_bunny")
                        .hand("BUNNY_FOOT").world(World.Environment.NORMAL)
                        .biomes("DESERT").ring("CARROTS", "MAGMA_BLOCK")
                        .keepBlocks().clearCenter().strike().delay(40),
                ritual("ForestGuardian", "forest_guardian")
                        .hand("NATURES_ESSENCE").world(World.Environment.NORMAL)
                        .biomes("FOREST", "TAIGA", "BIRCH", "JUNGLE")
                        .ring("OAK_LOG", "MOSS_BLOCK").explosion(2, false).delay(40),
                ritual("FrostTitan", "frost_titan")
                        .hand("FROST_CORE").world(World.Environment.NORMAL)
                        .biomes("SNOWY", "ICE", "FROZEN", "TAIGA")
                        .ring("BLUE_ICE", "PACKED_ICE").explosion(3, false).delay(40),
                ritual("OceanLeviathan", "ocean_leviathan")
                        .hand("ABYSSAL_SCALE").world(World.Environment.NORMAL).biomes("OCEAN", "BEACH")
                        .ring("PRISMARINE_BRICKS", "PRISMARINE").explosion(3, false).delay(40),
                ritual("NetherOverlord", "nether_overlord")
                        .hand("INFERNAL_CORE").world(World.Environment.NETHER)
                        .ring("MAGMA_BLOCK", "NETHERRACK").explosion(4, true).delay(40),
                ritual("EndColossus", "end_colossus")
                        .hand("ENDER_CRYSTAL_SHARD").world(World.Environment.THE_END)
                        .ring("END_STONE", "OBSIDIAN").explosion(4, false).delay(40)
        );

        List<Ritual> built = new ArrayList<>();
        for (Shrine shrine : shrines) {
            Material material;
            String displayName = null;
            try {
                if (shrine.customActivator) {
                    ItemStack activator = getMentionedItem(shrine.activatorId, 1);
                    material = activator == null ? null : activator.getType();
                    ItemMeta meta = activator == null ? null : activator.getItemMeta();
                    if (meta != null && meta.hasDisplayName())
                        displayName = meta.getDisplayName();
                } else {
                    material = CompatibilityResolver.resolveMaterial(shrine.activatorId);
                }
            } catch (Exception x) {
                // один битый предмет не должен ронять весь плагин: ритуал просто не соберётся,
                // а самопроверка (/bl-admin selftest) покажет его отдельной строкой
                plugin.getLogger().log(Level.WARNING,
                        "Ритуал «" + shrine.boss + "» отключён: не удалось получить предмет «" + shrine.activatorId
                                + "» — " + x, x);
                material = null;
            }
            built.add(new Ritual(shrine.boss, shrine.recipe, shrine.activatorId, shrine.customActivator,
                    material, displayName, shrine.activatorAmount, shrine.environment, shrine.biomes,
                    shrine.minY, shrine.maxY, shrine.explosion, shrine.explosionFire, shrine.boom,
                    shrine.boomFire, shrine.lightning, shrine.realStrike, shrine.sound, shrine.riseY,
                    shrine.delay, shrine.consumeBlocks, shrine.clearCenter, shrine.needsDeadGods,
                    resolveBlocks(shrine.recipe, shrine.blocks)));
        }
        return List.copyOf(built);
    }

    private Shrine ritual(String boss, String recipe) {
        return new Shrine(boss, recipe);
    }

    /**
     * Структура святилища берётся из recipes.yml — из той же записи, которую показывает руководство.
     * Если записи нет или материал неизвестен этой версии сервера, работает запасная структура из кода.
     */
    private String[] resolveBlocks(String recipeKey, String[] fallback) {
        if (recipesSection == null)
            return fallback;
        ConfigurationSection section = recipesSection.getConfigurationSection(recipeKey);
        if (section == null)
            return fallback;
        List<String> shape = section.getStringList("shape");
        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (shape.size() != 3 || ingredients == null)
            return fallback;

        String[] rows = new String[3];
        for (int row = 0; row < 3; row++) {
            String line = shape.get(row);
            if (line.length() != 3)
                return fallback;
            StringBuilder built = new StringBuilder();
            for (int col = 0; col < 3; col++) {
                String key = String.valueOf(line.charAt(col));
                String id = ingredients.getString(key, ingredients.getString(key.toLowerCase(Locale.ROOT)));
                Material material = blockMaterial(id);
                if (material == null)
                    return fallback;
                if (col > 0)
                    built.append(':');
                built.append(material.name());
            }
            rows[row] = built.toString();
        }
        return rows;
    }

    /** В книге ингредиенты записаны предметами, а в мире стоят блоками: REDSTONE → REDSTONE_WIRE. */
    private Material blockMaterial(String id) {
        if (id == null)
            return null;
        String name = id.toUpperCase(Locale.ROOT);
        return switch (name) {
            case "REDSTONE" -> CompatibilityResolver.resolveMaterial("REDSTONE_WIRE", "REDSTONE");
            case "CARROT" -> CompatibilityResolver.resolveMaterial("CARROTS", "CARROT");
            default -> CompatibilityResolver.resolveMaterial(name);
        };
    }

    /** Подходит ли предмет в руке под ритуал: для предметов призыва сверяем ещё и имя из lang.yml. */
    private boolean holdsActivator(ItemStack hand, Ritual ritual) {
        if (ritual.activatorMaterial() == null || hand.getType() != ritual.activatorMaterial())
            return false;
        if (hand.getAmount() < ritual.activatorAmount())
            return false;
        if (!ritual.customActivator())
            return true;
        ItemMeta meta = hand.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().equals(ritual.activatorName());
    }

    /**
     * Призыв босса предметом. По умолчанию (настройки {@code rituals.*} в config.yml) строить святилище
     * не нужно и место не важно: скрафтил предмет призыва — нажал правую кнопку — босс появился.
     */
    public void detectShrineRecipe(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR)
            return;
        final Player p = e.getPlayer();
        final ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir())
            return;
        // кликнули по блоку — босс встанет на него, кликнули в воздух — прямо на месте игрока
        Block clicked = e.getClickedBlock();
        final Location l = clicked != null ? clicked.getLocation().add(0, 1, 0) : p.getLocation();
        final World w = l.getWorld();
        if (w == null)
            return;
        final String biome = w.getBiome(l).toString().toUpperCase(Locale.ROOT);
        final boolean requireShrine = plugin.getConfig().getBoolean("rituals.requireShrine", false);
        final boolean requireBiome = plugin.getConfig().getBoolean("rituals.requireBiome", false);
        try {
            boolean almostThere = false;
            for (Ritual ritual : rituals) {
                if (!holdsActivator(hand, ritual))
                    continue;
                if (requireBiome) {
                    if (ritual.environment() != null && w.getEnvironment() != ritual.environment())
                        continue;
                    if (!ritual.biomes().isEmpty() && ritual.biomes().stream().noneMatch(biome::contains))
                        continue;
                    if (ritual.maxY() != null && l.getY() > ritual.maxY())
                        continue;
                    if (ritual.minY() != null && l.getY() < ritual.minY())
                        continue;
                }
                // предмет подходит, не хватает только структуры — подскажем игроку
                almostThere = true;
                if (requireShrine && !checkBlockRecipe(l, ritual.blocks()[0], ritual.blocks()[1],
                        ritual.blocks()[2], ritual.consumeBlocks()))
                    continue;
                performRitual(p, e, l, w, ritual, requireShrine);
                return;
            }
            if (almostThere && requireShrine)
                hintRitualFailed(p);
        } catch (Exception x) {
            warnOnce("Не удалось выполнить ритуал призыва: " + x, x);
        }
    }

    private void performRitual(Player p, PlayerInteractEvent e, Location l, World w, Ritual ritual,
                               boolean requireShrine) {
        // PlayerInteractEvent успевает прилететь дважды (по руке и по второй руке) — призываем один раз
        long now = System.currentTimeMillis();
        Long last = lastSummon.get(p.getUniqueId());
        if (last != null && now - last < 1000)
            return;
        lastSummon.put(p.getUniqueId(), now);
        e.setCancelled(true);
        if (ritual.needsDeadGods() && !config.godsDead()) {
            p.sendMessage(config.getLang("noPower"));
            return;
        }
        // центр святилища убираем, только когда он действительно построен
        if (requireShrine && ritual.clearCenter() && !l.getBlock().getType().equals(Material.BEDROCK))
            l.getBlock().setType(Material.AIR);
        if (ritual.explosion() > 0)
            w.createExplosion(l, ritual.explosion(), ritual.explosionFire());
        if (ritual.boom() > 0)
            MCUtility.boom(plugin, l, ritual.boom(), ritual.boomFire());
        if (ritual.lightning() > 0)
            MCUtility.lightningShow(plugin, l, ritual.lightning());
        if (ritual.realStrike())
            w.strikeLightning(l);
        if (ritual.sound() != null)
            w.playSound(l, ritual.sound(), 1, 1);
        MCUtility.takeItem(p, ritual.activatorAmount());
        if (ritual.needsDeadGods())
            p.sendMessage(config.getLang("demonSpawn"));
        final Location bs = l.clone();
        bs.setY(bs.getY() + ritual.riseY());
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin,
                () -> bossManager.spawnBoss(p, bs, ritual.boss()), ritual.delay());
    }

    /** Игрок держит предмет призыва и стоит где надо, но структура не та — скажем об этом, а не промолчим. */
    private void hintRitualFailed(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastHint.get(p.getUniqueId());
        if (last != null && now - last < 5000)
            return;
        if (lastHint.size() > 128)
            lastHint.clear();
        lastHint.put(p.getUniqueId(), now);
        p.sendMessage(config.getLang("ritualFail"));
    }

    /** Ошибки ритуалов раньше проглатывались молча — теперь пишем в консоль, но по одному разу. */
    private void warnOnce(String message, Exception x) {
        if (!warnedRituals.add(message))
            return;
        plugin.getLogger().log(Level.WARNING, message, x);
    }

    private boolean checkBlockRecipe(Location l, String line1, String line2, String line3, boolean remove) {
        // String[] s1 = line1.split(":");
        // String[] s2 = line2.split(":");
        // String[] s3 = line3.split(":");
        // Row One
        Location l1 = l.clone();
        l1.setX(l1.getX() + 1);
        l1.setZ(l1.getZ() + 1);
        Location l2 = l.clone();
        l2.setX(l2.getX() + 1);
        Location l3 = l.clone();
        l3.setX(l3.getX() + 1);
        l3.setZ(l3.getZ() - 1);
        // System.out.println(l1.getBlock().getType() + ":" + l2.getBlock().getType() +
        // ":" + l3.getBlock().getType());
        // l1.getBlock().setType(Material.DIRT); l2.getBlock().setType(Material.DIRT);
        // l3.getBlock().setType(Material.DIRT);
        // if((l1.getBlock().getType().toString().equals(s1[0])) &&
        // (l2.getBlock().getType().toString().equals(s1[1])) &&
        // (l3.getBlock().getType().toString().equals(s1[2]))) {
        if (line1.equals(l1.getBlock().getType() + ":" + l2.getBlock().getType() + ":" + l3.getBlock().getType())) {
            // Row Two
            Location l4 = l.clone();
            l4.setZ(l4.getZ() + 1);
            Location l5 = l.clone();
            Location l6 = l.clone();
            l6.setZ(l6.getZ() - 1);
            // System.out.println(l4.getBlock().getType() + ":" + l5.getBlock().getType() +
            // ":" + l6.getBlock().getType());
            // if((l4.getBlock().getType().toString().equals(s2[0])) &&
            // (l5.getBlock().getType().toString().equals(s2[1])) &&
            // (l6.getBlock().getType().toString().equals(s2[2]))) {
            if (line2.equals(l4.getBlock().getType() + ":" + l5.getBlock().getType() + ":" + l6.getBlock().getType())) {
                // Row Three
                Location l7 = l.clone();
                l7.setX(l7.getX() - 1);
                l7.setZ(l7.getZ() + 1);
                Location l8 = l.clone();
                l8.setX(l8.getX() - 1);
                Location l9 = l.clone();
                l9.setX(l9.getX() - 1);
                l9.setZ(l9.getZ() - 1);
                // System.out.println(l7.getBlock().getType() + ":" + l8.getBlock().getType() +
                // ":" + l9.getBlock().getType());
                // if((l7.getBlock().getType().toString().equals(s3[0])) &&
                // (l8.getBlock().getType().toString().equals(s3[1])) &&
                // (l9.getBlock().getType().toString().equals(s3[2]))) {
                if (line3.equals(
                        l7.getBlock().getType() + ":" + l8.getBlock().getType() + ":" + l9.getBlock().getType())) {
                    // Recipe Correct
                    // System.out.println("Recipe Correct");
                    if (remove)
                        for (Location bl : Arrays.asList(l1, l2, l3, l4, l5, l6, l7, l8, l9))
                            if (!bl.getBlock().getType().equals(Material.BEDROCK))
                                bl.getBlock().setType(Material.AIR);
                    return true;
                }
            }
        }
        return false;
    }
}
