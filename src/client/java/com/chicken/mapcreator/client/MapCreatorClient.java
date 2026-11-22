package com.chicken.mapcreator.client;

import net.fabricmc.api.ClientModInitializer;

/** Menu dzialaja po stronie serwera (skrzynie); klient ma tylko efekty kamery. */
public class MapCreatorClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CameraEffects.register();
		FlyingWindows.register();
	}
}
