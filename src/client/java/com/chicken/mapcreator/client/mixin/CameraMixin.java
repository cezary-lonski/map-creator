package com.chicken.mapcreator.client.mixin;

import com.chicken.mapcreator.client.CameraEffects;
import net.minecraft.client.Camera;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Po ustawieniu obrotu kamery dokleja efekt (odwrocona, bokiem, falowanie). */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	@Final
	private Quaternionf rotation;

	@Inject(method = "setRotation", at = @At("RETURN"))
	private void mapcreator$applyEffect(float yRot, float xRot, CallbackInfo ci) {
		CameraEffects.applyRotation(this.rotation);
	}
}
