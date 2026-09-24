package com.pancake.surviving_the_aftermath.common.data.datagen;

import com.pancake.surviving_the_aftermath.SurvivingTheAftermath;
import com.pancake.surviving_the_aftermath.common.event.subscriber.RaidEventSubscriber;
import com.pancake.surviving_the_aftermath.common.event.tracker.RaidPlayerBattleTracker;
import com.pancake.surviving_the_aftermath.common.init.ModItems;
import com.pancake.surviving_the_aftermath.common.item.DiamondFlintAndSteelItem;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModLanguageCNProvider extends LanguageProvider {

    public ModLanguageCNProvider(PackOutput output) {
        super(output, SurvivingTheAftermath.MOD_ID, "zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("item.surviving_the_aftermath.nether_core.tooltip", "右键抛出，寻找城市与遗物商人。必定完整掉落，可无限次使用。");
        add("message.surviving_the_aftermath.nether_core.overworld", "请在主世界使用下界核心寻找遗物商人。");
        add("message.surviving_the_aftermath.nether_core.searching", "正在寻找城市……请继续手持下界核心。");
        add("message.surviving_the_aftermath.nether_core.not_found", "附近暂未找到可引导的城市，请换个区域重试。");
        add("message.surviving_the_aftermath.nether_core.city", "下界核心正指向城市；接近后会寻找存活的遗物商人。");
        add("message.surviving_the_aftermath.nether_core.dealer", "下界核心正指向附近的遗物商人。");
        add("item.surviving_the_aftermath.raw_falukorv", "生的法伦香肠");
        add("item.surviving_the_aftermath.cooked_falukorv", "熟的法伦香肠");
        add("item.surviving_the_aftermath.egg_tart", "蛋挞");
        add("item.surviving_the_aftermath.stack_of_egg_tarts", "蛋挞堆");
        add("item.surviving_the_aftermath.hamburger", "汉堡");
        add("item.surviving_the_aftermath.tianjin_pancake", "煎饼果子");
        add("item.surviving_the_aftermath.nether_core", "下界核心");
        add("item.surviving_the_aftermath.music_disk_orchelias_vox", "音乐唱片");
        add("enchantment.surviving_the_aftermath.counter_attack", "反击");
        add("enchantment.surviving_the_aftermath.bloodthirsty", "渴血");
        add("enchantment.surviving_the_aftermath.clean_water", "净水");
        add("enchantment.surviving_the_aftermath.life_tree", "树灵");
        add("enchantment.surviving_the_aftermath.devoured", "吞噬");
        add("enchantment.surviving_the_aftermath.frantic", "癫狂");
        add("enchantment.surviving_the_aftermath.execute", "处决");
        add("enchantment.surviving_the_aftermath.ranger", "游侠");
        add("enchantment.surviving_the_aftermath.moon", "皎月");
        add("enchantment.surviving_the_aftermath.sun", "烈阳");
        add("effect.surviving_the_aftermath.cowardice", "懦弱");
        add("entity.minecraft.villager.surviving_the_aftermath.relic_dealer", "遗物商人");
        add("entity.minecraft.villager.relic_dealer", "遗物商人");
        add("item.surviving_the_aftermath.music_disk_orchelias_vox.desc", "Hagali - Orchelia's vox (offvocal ver_)");

        add(ModItems.DIAMOND_FLINT_AND_STEEL.get(), "钻石打火石");
        add(DiamondFlintAndSteelItem.TOOLTIP, "用于激活副本门，每次激活消耗 1 点耐久。");
        add(DiamondFlintAndSteelItem.REQUIRED, "此副本需要使用钻石打火石激活。");
        add(DiamondFlintAndSteelItem.UNAVAILABLE, "无法激活：请检查门框、挑战条件，或等待当前挑战发奖结束。");
        add("itemGroup." + SurvivingTheAftermath.MOD_ID, "劫后余生");
        add(RaidEventSubscriber.NETHER_RAID_START, "你感受到空气愈发炎热......");
        add(RaidEventSubscriber.NETHER_RAID_VICTORY, "望着最后一颗火星熄灭，你感觉它们不会再回来了，暂时......");
        add(RaidPlayerBattleTracker.PLAYER_BATTLE_ESCAPE, "请勿战斗过程逃跑，否则你将付出代价，倒计时开始......%s");
        add(RaidPlayerBattleTracker.PLAYER_BATTLE_PERSONAL_FAIL, "你虽然失败了但是可以继续相信你的队友");
    }

}
