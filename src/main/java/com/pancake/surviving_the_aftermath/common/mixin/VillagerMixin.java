package com.pancake.surviving_the_aftermath.common.mixin;

import com.pancake.surviving_the_aftermath.common.init.ModMobEffects;
import com.pancake.surviving_the_aftermath.common.util.LegacyTrades;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin {
    @Inject(method = "updateSpecialPrices", at = @At("RETURN"))
    private void aftermath$cowardicePrices(Player player, CallbackInfo ci) {
        if (player.hasEffect(ModMobEffects.COWARDICE)) {
            var villager = (Villager) (Object) this;
            for (var offer : villager.getOffers()) {
                // Preserve the historical 36.25% penalty (both effect levels), minimum one item.
                offer.addToSpecialPriceDiff(Math.max(1, (int) Math.floor(.3625D * offer.getBaseCostA().getCount())));
            }
        }
    }

    @Inject(method = "updateTrades", at = @At("RETURN"))
    private void aftermath$legacyTrades(CallbackInfo ci) {
        LegacyTrades.addOffers((Villager) (Object) this);
    }
}
