package com.chicken.mapcreator.item;

import com.chicken.mapcreator.Perms;
import com.chicken.mapcreator.rules.Rule;
import com.chicken.mapcreator.selection.SelectionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Rozdzka tworcy: PPM na blok ustawia kotwice albo wybiera rog obszaru. Menu GUI dojdzie w etapie 8. */
public class WandItem extends Item {
	public WandItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(context.getPlayer() instanceof ServerPlayer player) || !Perms.canEdit(player)) {
			return InteractionResult.PASS;
		}
		Rule.Anchor anchor = new Rule.Anchor(context.getLevel().dimension().identifier().toString(), context.getClickedPos());
		SelectionManager.onWandClick(player, anchor);
		return InteractionResult.SUCCESS_SERVER;
	}
}
