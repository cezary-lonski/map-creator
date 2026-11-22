package com.chicken.mapcreator;

import net.minecraft.world.entity.player.Player;

/** Jedno miejsce na ewentualne ograniczenie uzywania rozdzki i GUI. Na razie kazdy moze edytowac. */
public final class Perms {
	private Perms() {}

	public static boolean canEdit(Player player) {
		return true;
	}
}
