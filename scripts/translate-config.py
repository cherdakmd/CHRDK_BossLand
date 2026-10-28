#!/usr/bin/env python3
"""Перевод имён и лора предметов/боссов в config.yml (RU)."""
import re
import sys
from pathlib import Path

CONFIG = Path("src/main/resources/config.yml")

NAMES = {
    # --- боссы ---
    "§f§lGhast Lord": "§f§lГерцог Гастов",
    "§a§lKing Slime": "§a§lКороль Слизней",
    "§7§lKiller Bunny": "§7§lКролик-Убийца",
    "§6§lPapa Panda": "§6§lПапа Панда",
    "§8§lWither Skeleton King": "§8§lКороль Скелетов-Иссохших",
    "§2§lZombie King": "§2§lКороль Зомби",
    "§9§lEvil Wizard": "§9§lЗлой Колдун",
    "§6§lKing of the Illagers": "§6§lКороль Иллов",
    "§2§lGiant": "§2§lГигант",
    "§b§lThe Drowned God": "§b§lУтонувший Бог",
    "§0§lThe Pharaoh God": "§0§lБог-Фараон",
    "§b§lThe Aether God": "§b§lЭфирный Бог",
    "§c§lDemon": "§c§lДемон",
    "§c§lThe Devil": "§c§lДьявол",
    "§5§lDeath": "§5§lСмерть",
    "§c§lEmbodiment of Anger": "§c§lВоплощение Гнева",
    "§3§lFrost Titan": "§3§lЛедяной Титан",
    "§b§lOcean Leviathan": "§b§lОкеанский Левиафан",
    "§4§lNether Overlord": "§4§lВладыка Нижнего Мира",
    "§5§lEnd Colossus": "§5§lКолосс Края",
    "§a§lForest Guardian": "§a§lЛесной Страж",
    # --- осколки (строго как в lang.yml!) ---
    "&lWhite Boss Shard": "&lБелый осколок босса",
    "&a&lGreen Boss Shard": "&a&lЗеленый осколок босса",
    "&7&lGrey Boss Shard": "&7&lСерый осколок босса",
    "&4&lBrown Boss Shard": "&4&lКоричневый осколок босса",
    "&8&lBlack Boss Shard": "&8&lЧерный осколок босса",
    "&c&lRed Boss Shard": "&c&lКрасный осколок босса",
    "&9&lBlue Boss Shard": "&9&lСиний осколок босса",
    "&6&lGold Boss Shard": "&6&lЗолотой осколок босса",
    "&a&lEmerald Boss Shard": "&a&lИзумрудный осколок босса",
    "&c&lDemonic Shard": "&c&lДемонический Осколок",
    "&d&lEnder Crystal Shard": "&d&lОсколок Кристалла Края",
    "&f&lFrost Core": "&f&lЯдро Льда",
    "&b&lAbyssal Scale": "&b&lЧешуя Бездны",
    "&c&lInfernal Core": "&c&lИнфернальное Ядро",
    "&c&lBook of Knowledge": "&c&lКнига Знаний",
    "&f&lЯдро Льда": "&f&lЯдро Льда",
    "&d&lОсколок Кристалла Края": "&d&lОсколок Кристалла Края",
    "&c&lИнфернальное Ядро": "&c&lИнфернальное Ядро",
    "&b&lЧешуя Бездны": "&b&lЧешуя Бездны",
    "&a&lЗеленый осколок босса": "&a&lЗеленый осколок босса",
    # --- оружие и броня ---
    "&c&lFlame Sword": "&c&lПламенный Меч",
    "&c&lFlame &6&lBow": "&c&lПламенный &6&lЛук",
    "&a&lSlime Boots": "&a&lСлизистые Ботинки",
    "&7&lBunny Pants": "&7&lКроличьи Штаны",
    "&lPanda Pants": "&lШтаны Панды",
    "&f&lDeath Mask": "&f&lМаска Смерти",
    "&2&lPick of the Undead": "&2&lКирка Нежити",
    "&2&lGrave Digger": "&2&lМогильщик",
    "&2&lThe Headmans Axe": "&2&lТопор Палача",
    "&5&lMagic Book": "&5&lКнига Магии",
    "&f&lPlate of Death": "&f&lЛаты Смерти",
    "&1&lWitch Hunter": "&1&lОхотник на Ведьм",
    "&d&lGiant's Pick": "&d&lКирка Гиганта",
    "&d&lGiant's Spade": "&d&lЛопата Гиганта",
    "&3&lTrident of Ultimate Power": "&3&lТрезубец Абсолютной Силы",
    "&3&lTrident of Ultimate Speed": "&3&lТрезубец Абсолютной Скорости",
    "&3&lDrowned Helm": "&3&lШлем Утонувшего",
    "&e&lThe Cursed Skull": "&e&lПроклятый Череп",
    "&e&lStaff of Brimstone": "&e&lПосох Серы",
    "&6&lSwiftfoot Boots": "&6&lБотинки Быстронога",
    "&lZephyr Wings": "&lКрылья Зефира",
    "&lStaff of Control": "&lПосох Контроля",
    "&lZephyr Bow": "&lЛук Зефира",
    "&kxxx &r&c&lDemonic Axe &r&kxxx": "&kxxx &r&c&lДемонический Топор &r&kxxx",
    "&c&lDevil's Greaves": "&c&lНаголенники Дьявола",
    "&5&lEnd Blade": "&5&lКлинок Края",
    "&5&lScythe of Death": "&5&lКоса Смерти",
    "&6&lCopper Sword": "&6&lМедный Меч",
    "&c&lTablet of Anger": "&c&lСкрижаль Гнева",
    "&f&lFrostbrand": "&f&lМорозный Клинок",
    "&b&lTide Caller": "&b&lЗов Прилива",
    "&c&lInferno Cleaver": "&c&lТесак Инферно",
    "&d&lVoid Blade": "&d&lКлинок Пустоты",
    "&2&lGreenwarden": "&2&lЗелёный Страж",
}

LORE = {
    "&eOne of 6 &lTier 1 &r&eshards": "&eОдин из 6 осколков &lТира 1",
    "&eOne of 4 &lTier 2 &r&eshards": "&eОдин из 4 осколков &lТира 2",
    "&6Used to craft &lTier 2 &r&6summon item:": "&6Нужен для крафта предмета призыва &lТира 2&r&6:",
    "&6Used to craft &lTier 2 &r&6summon items:": "&6Нужен для крафта предметов призыва &lТира 2&r&6:",
    "&6Used to craft &lTier 3 &r&6summon item:": "&6Нужен для крафта предмета призыва &lТира 3&r&6:",
    "&eUsed to craft &lTier 3 &r&esummon item:": "&eНужен для крафта предмета призыва &lТира 3&r&e:",
    "&eUsed to craft &lTier 4 &r&esummon item:": "&eНужен для крафта предмета призыва &lТира 4&r&e:",
    "&6&lBell of Doom": "&6&lКолокол Рока",
    "&6&lPotion of Giant Growth": "&6&lЗелье Роста Гиганта",
    "&6&lBook of Spells": "&6&lКнига Заклинаний",
    "&6&lForbidden Fruit": "&6&lЗапретный Плод",
    "&6&lAbhorrent Fruit": "&6&lОтвратительный Плод",
    "&6&lFrost Titan Summoning Item": "&6&lПредмет призыва Ледяного Титана",
    "&6&lOcean Leviathan Summoning Item": "&6&lПредмет призыва Океанского Левиафана",
    "&6&lNether Overlord Summoning Item": "&6&lПредмет призыва Владыки Нижнего Мира",
    "&6&lEnd Colossus Summoning Item": "&6&lПредмет призыва Колосса Края",
    "&6&lPotion of Giant Growth&r&6, &lBell of Doom": "&6&lЗелье Роста Гиганта&r&6, &lКолокол Рока",
    "&6&lPotion of Giant Growth&r&6, &lBook of Spells": "&6&lЗелье Роста Гиганта&r&6, &lКнига Заклинаний",
    # дроп с боссов
    "&8Dropped by Ghast Lord": "&8Выпадает с: Герцог Гастов",
    "&8Dropped by Slime King": "&8Выпадает с: Король Слизней",
    "&8Dropped by Killer Bunny": "&8Выпадает с: Кролик-Убийца",
    "&8Dropped by Papa Panda": "&8Выпадает с: Папа Панда",
    "&8Dropped by Wither Skeleton King": "&8Выпадает с: Король Скелетов-Иссохших",
    "&8Dropped by Zombie King": "&8Выпадает с: Король Зомби",
    "&8Dropped by Evil Wizard": "&8Выпадает с: Злой Колдун",
    "&8Dropped by Illager King": "&8Выпадает с: Король Иллов",
    "&8Dropped by the Giant": "&8Выпадает с: Гигант",
    "&4Dropped by Drowned God": "&4Выпадает с: Утонувший Бог",
    "&8Dropped by Drowned God": "&8Выпадает с: Утонувший Бог",
    "&4Dropped by Pharaoh God": "&4Выпадает с: Бог-Фараон",
    "&4Dropped by Aether God": "&4Выпадает с: Эфирный Бог",
    "&8Dropped by Aether God": "&8Выпадает с: Эфирный Бог",
    "&8Dropped by Demon": "&8Выпадает с: Демон",
    "&4Dropped by the &lDEVIL": "&4Выпадает с: &lДьявол",
    "&4Dropped by the &lDEATH": "&4Выпадает с: &lСмерть",
    "&4Dropped by the angry one": "&4Выпадает с: Воплощение Гнева",
    "&8Dropped by Frost Titan": "&8Выпадает с: Ледяной Титан",
    "&8Dropped by Ocean Leviathan": "&8Выпадает с: Океанский Левиафан",
    "&8Dropped by Nether Overlord": "&8Выпадает с: Владыка Нижнего Мира",
    "&8Dropped by End Colossus": "&8Выпадает с: Колосс Края",
    "&8Dropped by Forest Guardian": "&8Выпадает с: Лесной Страж",
    # прочее
    "&6Hey, at least it has looting": "&6Зато с добычей",
    "&6Shoots fire to the heavens!": "&6Мечет огонь в небеса!",
    "&2Bouncy as heck!": "&2Прыгучий до жути!",
    "&eGrants ability to &lbounce off everything": "&eДаёт умение &lотскакивать от всего",
    "&6I'm the king of parkour!": "&6Я король паркура!",
    "&eGrants &lJump Boost IV": "&eДаёт &lПрыгучесть IV",
    "&bI'M FAST AS FLIP BOI!": "&bЯ БЫСТРЫЙ, КАК ВЕТЕР!",
    "&eGrants &lSpeed IV": "&eДаёт &lСкорость IV",
    "&cLet them feel the PAIN": "&cПусть почувствуют БОЛЬ",
    "&fBreath death itself!": "&fДыхание самой смерти!",
    "&2Pretty dang good at digging holes": "&2Копает ямы лучше всех",
    "&2TREES! FEAR ME!": "&2ДЕРЕВЬЯ! БОЙТЕСЬ МЕНЯ!",
    "&5Use when all hope is lost": "&5Используй, когда надежды не осталось",
    "&eConsumes 5 regular books from inventory to teleport back to your bed.": "&eТратит 5 обычных книг из инвентаря, чтобы телепортировать вас к кровати.",
    "&bHeart of the eternal winter": "&bСердце вечной зимы",
    "&fBecome immune to death": "&fСтань неуязвим к смерти",
    "&13 witches 1 shot": "&1Три ведьмы — один выстрел",
    "&dD dwarve's dream": "&dМечта гнома",
    "&eDigs &l3x3 &r&ehole": "&eКопает яму &l3x3",
    "&dThe holes are dang huge!": "&dЯмы просто огромные!",
    "&3His lightnings lit up the world": "&3Его молнии озарили мир",
    "&3The earth saw and trembled": "&3Земля увидела и задрожала",
    "&3The rains fall on the": "&3Дождь идёт равно",
    "&3wicked and the good alike": "&3на злых и на добрых",
    "&3What is dead may never die": "&3Что мертво, умереть не может",
    "&3but rises again, harder and stronger": "&3но восстаёт вновь, сильнее прежнего",
    "&eGrants &lNight Vision, Conduit Power, Dolphin's Grace": "&eДаёт &lНочное зрение, Силу проводника, Грацию дельфина",
    "&dFragment of the end dimension": "&dФрагмент измерения Края",
    "&6Immortality, a fate worse than death": "&6Бессмертие — участь хуже смерти",
    "&eWorks like a totem (only in offhand) *15min cooldown*": "&eРаботает как тотем (только во второй руке) *откат 15 мин*",
    "&6Rain upon them fire and brimstone": "&6Пролей на них огонь и серу",
    "&6Their ruin will be great": "&6Их погибель будет велика",
    "&eSummons &lDESTRUCTIVE &r&efireballs": "&eПризывает &lРАЗРУШИТЕЛЬНЫЕ &r&eфаерболы",
    "&6Swift like a wind": "&6Быстрый, как ветер",
    "&6deadly like a lightning": "&6смертоносный, как молния",
    "&eGrants &lJump Boost III, Speed III, Haste III": "&eДаёт &lПрыгучесть III, Скорость III, Спешку III",
    "&bSoft the zephyr blows, While ": "&bНежно веет зефир, пока ",
    "&bproudly riding o'er the azure realm": "&bгордо несусь над лазурным краем",
    "&eGrants &lStrength II, Slow Falling IV": "&eДаёт &lСилу II, Медленное падение IV",
    "&bMode: Shard": "&bРежим: осколок",
    "&b(Sneak/Click to Change)": "&b(Shift/клик — сменить)",
    "&eAllows for &lgrabbing &r&eentities": "&eПозволяет &lзахватывать &r&eсущности",
    "&e&lloot/shard &r&emode is only for &lboss &r&eloot": "&e&lРежим лута/осколков &r&eтолько для &lлута боссов",
    "&bHushed in grim repose,": "&bВ мрачном покое,",
    "&bexpects his evening prey": "&bждёт своей вечерней добычи",
    "&cCore of the nether ruler": "&cЯдро владыки Нижнего мира",
    "&cHERE'S JOHNNY!": "&cА ВОТ И Я!",
    "&bScale from the deep ocean beast": "&bЧешуя глубинного морского зверя",
    "&cProwl like a roaring lion": "&cРыскаю, как рычащий лев",
    "&cseeking who you may devour": "&cища, кого поглотить",
    "&eGrants &lAbsorbtion II, Fire Resistance IV": "&eДаёт &lПоглощение II, Огнестойкость IV",
    "&cWhen you read thereof": "&cКогда прочтёшь сие",
    "&cyour eyes will be opened": "&cоткроются глаза твои",
    "&cyou will be like God": "&cи будешь как Бог",
    "&eAllows to add various &lenchantments &r&eto items": "&eПозволяет добавлять разные &lзачарования &r&eна предметы",
    "&6Used to craft the ultimate summon item: &lDeath Note": "&6Нужна для крафта финального предмета призыва: &lСмертная Тетрадь",
    "The blade to end them all": "Клинок, что положит конец всему",
    "Life and death are one": "Жизнь и смерть едины",
    "just as the river and the sea become one": "как река и море становятся одним",
    "&eDeals a shit ton of &lDAMAGE": "&eНаносит чудовищный &lУРОН",
    "&eexept to players and &lDeath &eboss": "&eкроме игроков и босса &lСмерть",
    "&4One of the 7 tablets of sin": "&4Одна из 7 скрижалей грехов",
    "&eUsed to craft summoning items": "&eИспользуется для крафта предметов призыва",
    "&bFreezes enemies on hit": "&bЗамораживает врагов при ударе",
    "&bCommands the ocean itself": "&bПовелевает самим океаном",
    "&cBurns everything it touches": "&cСжигает всё, к чему прикоснётся",
    "&dCuts through dimensions": "&dРассекает измерения",
    "&aBlessed by the forest itself": "&aБлагословлён самим лесом",
}


def main() -> int:
    text = CONFIG.read_text(encoding="utf-8")
    original = text
    applied_names = applied_lore = 0

    def repl(match: re.Match) -> str:
        indent, field, quote, value = match.group(1), match.group(2), match.group(3), match.group(4)
        table = NAMES if field == "name" else LORE
        new = table.get(value)
        if new is None or new == value:
            return match.group(0)
        nonlocal applied_names, applied_lore
        if field == "name":
            applied_names += 1
        else:
            applied_lore += 1
        return f"{indent}{field}: {quote}{new}{quote}"

    text = re.sub(r"^(\s+)(name|lore\d+):\s*([\"']?)(.*?)\3\s*$", repl, text, flags=re.M)

    # проверяем, что не осталось английских значений
    leftover = []
    for line in text.split("\n"):
        m = re.match(r"^\s+(name|lore\d+):\s*[\"']?(.*?)[\"']?\s*$", line)
        if not m:
            continue
        value = re.sub(r"^(&.|§.)+", "", m.group(2)).strip()
        if not value:
            continue
        if not re.search(r"[А-Яа-яЁё]", value):
            leftover.append(line.strip())

    CONFIG.write_text(text, encoding="utf-8")
    print(f"переведено названий: {applied_names}, строк лора: {applied_lore}")
    if leftover:
        print("\nОСТАЛИСЬ БЕЗ ПЕРЕВОДА:")
        for line in leftover:
            print("   ", line)
        return 1
    print("английских названий/лора не осталось")
    return 0


if __name__ == "__main__":
    sys.exit(main())
