package com.pancake.surviving_the_aftermath.common.mixin;

import com.pancake.surviving_the_aftermath.common.init.ModMobEffects;
import com.pancake.surviving_the_aftermath.common.util.LegacyTrades;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin {
    @org.spongepowered.asm.mixin.Shadow
    protected abstract void updateSpecialPrices(Player player);

    @Inject(method = "updateSpecialPrices", at = @At("HEAD"))
    private void aftermath$refreshRelicCatalogue(Player player, CallbackInfo ci) {
        com.pancake.surviving_the_aftermath.common.util.RelicDealerTrades.synchronize((Villager) (Object) this);
    }

    @Inject(method = "customServerAiStep", at = @At("RETURN"))
    private void aftermath$restockRelicDealer(CallbackInfo ci) {
        var villager = (Villager) (Object) this;
        // This generated profession has no workstation. Keep vanilla's saved restock limits.
        if (villager.tickCount % 200 == 0 && !villager.isTrading()
                && com.pancake.surviving_the_aftermath.common.util.RelicDealerTrades.isDealer(villager)
                && villager.shouldRestock((net.minecraft.server.level.ServerLevel) villager.level())) villager.restock();
    }

    // In 26.3 vanilla sends prices inside this method; apply Cowardice before that packet.
    @Inject(method = "updateSpecialPrices", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/npc/villager/Villager;resetSpecialPrices()V", shift = At.Shift.AFTER))
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
        var villager = (Villager) (Object) this;
        LegacyTrades.addOffers(villager);
        // 26.3 upgrades while the menu is open. Our profession has no vanilla TradeSet,
        // so vanilla skips its price/menu refresh; send the complete new catalogue here.
        if (com.pancake.surviving_the_aftermath.common.util.RelicDealerTrades.isDealer(villager)
                && villager.getTradingPlayer() != null) updateSpecialPrices(villager.getTradingPlayer());
    }
}
