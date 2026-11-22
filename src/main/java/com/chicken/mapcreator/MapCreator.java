package com.chicken.mapcreator;

import com.chicken.mapcreator.command.MapCreatorCommand;
import com.chicken.mapcreator.menu.ChatPrompt;
import com.chicken.mapcreator.net.CameraEffectPayload;
import com.chicken.mapcreator.rules.TriggerListener;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapCreator implements ModInitializer {
	public static final String MOD_ID = "mapcreator";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.register();
		CameraEffectPayload.register();
		ChatPrompt.register();
		MapCreatorCommand.register();
		TriggerListener.register();
		LOGGER.info("Map Creator loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
