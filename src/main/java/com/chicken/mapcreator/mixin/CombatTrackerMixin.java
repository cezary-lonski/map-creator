package com.chicken.mapcreator.mixin;

import com.chicken.mapcreator.rules.KillText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Podmienia komunikat o smierci gracza zabitego przez regule z wlasnym tekstem. */
@Mixin(CombatTracker.class)
public abstract class CombatTrackerMixin {
	@Shadow
	@Final
	private LivingEntity mob;

	@Inject(method = "getDeathMessage", at = @At("HEAD"), cancellable = true)
	private void mapcreator$customDeathMessage(CallbackInfoReturnable<Component> cir) {
		String text = KillText.take(this.mob.getUUID());
		if (text != null) {
			cir.setReturnValue(Component.translatable("mapcreator.death.custom", this.mob.getDisplayName(), text));
		}
	}
}
