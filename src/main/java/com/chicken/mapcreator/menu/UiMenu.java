package com.chicken.mapcreator.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Menu w stylu skrzyni (jak na serwerach), w calosci po stronie serwera: ikony to przyciski,
 * a sloty "duchy" to ustawienia (wkladasz przedmiot z kursora, ale NIE jest on zabierany z ekwipunku).
 */
public class UiMenu extends ChestMenu {
	public interface Action {
		void run(ServerPlayer player, ClickType type, int button);
	}

	public interface GhostChange {
		void changed(ServerPlayer player, ItemStack value);
	}

	private final int size;
	private final Container container;
	private final Map<Integer, Action> buttons = new HashMap<>();
	private final Map<Integer, GhostChange> ghosts = new HashMap<>();
	private final Map<Integer, ItemStack> ghostValues = new HashMap<>();
	private final Map<Integer, ItemStack> ghostMarkers = new HashMap<>();
	private String key = "";

	private UiMenu(MenuType<?> type, int id, Inventory inventory, SimpleContainer container, int rows) {
		super(type, id, inventory, container, rows);
		this.container = container;
		this.size = rows * 9;
	}

	private static MenuType<?> typeFor(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	/** Otwiera menu (rzedy 1-6); init wypelnia je przyciskami i slotami. */
	public static void open(ServerPlayer player, Component title, int rows, Consumer<UiMenu> init) {
		int r = Math.max(1, Math.min(6, rows));
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
			UiMenu menu = new UiMenu(typeFor(r), id, inv, new SimpleContainer(r * 9), r);
			init.accept(menu);
			return menu;
		}, title));
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String key() {
		return key;
	}

	public void button(int slot, ItemStack icon, Action action) {
		container.setItem(slot, icon);
		buttons.put(slot, action);
	}

	public void decor(int slot, ItemStack icon) {
		container.setItem(slot, icon);
	}

	/** Wypelnia wszystkie puste sloty ozdobnym przedmiotem. */
	public void fillEmpty(ItemStack filler) {
		for (int i = 0; i < size; i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, filler.copy());
			}
		}
	}

	/**
	 * Slot-duch: pusty pokazuje marker (z opisem), z przedmiotem pokazuje kopie wartosci.
	 * Klik z przedmiotem na kursorze ustawia wartosc, klik pustym kursorem albo shift-klik czyści.
	 */
	public void ghost(int slot, ItemStack marker, ItemStack current, GhostChange onChange) {
		ghostMarkers.put(slot, marker);
		ghostValues.put(slot, current == null ? ItemStack.EMPTY : current.copy());
		ghosts.put(slot, onChange);
		refreshGhost(slot);
	}

	private void refreshGhost(int slot) {
		ItemStack value = ghostValues.getOrDefault(slot, ItemStack.EMPTY);
		container.setItem(slot, value.isEmpty() ? ghostMarkers.get(slot).copy() : value.copy());
	}

	@Override
	public void clicked(int slotId, int button, ClickType clickType, Player player) {
		if (!(player instanceof ServerPlayer sp)) {
			return;
		}
		if (clickType == ClickType.QUICK_CRAFT) {
			return;
		}
		if (slotId < 0 || slotId >= size) {
			// ekwipunek gracza (dolna czesc) dziala normalnie, ale bez przenoszenia shiftem do menu
			if (clickType == ClickType.QUICK_MOVE || clickType == ClickType.PICKUP_ALL) {
				return;
			}
			super.clicked(slotId, button, clickType, player);
			return;
		}
		Action action = buttons.get(slotId);
		if (action != null) {
			action.run(sp, clickType, button);
		} else if (ghosts.containsKey(slotId)
				&& (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE || clickType == ClickType.CLONE)) {
			ItemStack carried = getCarried();
			ItemStack value;
			if (clickType == ClickType.QUICK_MOVE || carried.isEmpty()) {
				value = ItemStack.EMPTY;
			} else {
				value = carried.copy();
				if (button == 1) {
					value.setCount(1);
				}
			}
			ghostValues.put(slotId, value);
			refreshGhost(slotId);
			ghosts.get(slotId).changed(sp, value);
		}
		sendAllDataToRemote();
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
