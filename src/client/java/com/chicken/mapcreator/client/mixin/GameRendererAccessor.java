package com.chicken.mapcreator.client.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Dostep do prywatnego setPostEffect, zeby wlaczyc wlasny shader pelnoekranowy. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Invoker("setPostEffect")
	void mapcreator$setPostEffect(Identifier id);
}
