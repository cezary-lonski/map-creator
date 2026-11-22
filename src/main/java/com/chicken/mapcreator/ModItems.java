package com.chicken.mapcreator;

import com.chicken.mapcreator.item.WandItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final ResourceKey<Item> WAND_KEY = ResourceKey.create(Registries.ITEM, MapCreator.id("wand"));
	public static Item WAND;

	private ModItems() {}

	public static void register() {
		WAND = Registry.register(BuiltInRegistries.ITEM, WAND_KEY,
				new WandItem(new Item.Properties().setId(WAND_KEY).stacksTo(1)));
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> entries.accept(WAND));
	}
}
