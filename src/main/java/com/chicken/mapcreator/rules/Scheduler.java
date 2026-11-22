package com.chicken.mapcreator.rules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Proste opoznione zadania serwera (liczone w tickach). Znikaja przy restarcie serwera. */
public final class Scheduler {
	private static final class Task {
		int ticksLeft;
		final Runnable action;

		Task(int ticksLeft, Runnable action) {
			this.ticksLeft = ticksLeft;
			this.action = action;
		}
	}

	private static final List<Task> TASKS = new ArrayList<>();

	private Scheduler() {}

	public static void runLater(int ticks, Runnable action) {
		TASKS.add(new Task(Math.max(1, ticks), action));
	}

	public static void clear() {
		TASKS.clear();
	}

	/** Wolane raz na tick serwera. */
	public static void tick() {
		if (TASKS.isEmpty()) {
			return;
		}
		List<Runnable> due = new ArrayList<>();
		Iterator<Task> it = TASKS.iterator();
		while (it.hasNext()) {
			Task t = it.next();
			if (--t.ticksLeft <= 0) {
				due.add(t.action);
				it.remove();
			}
		}
		due.forEach(Runnable::run);
	}
}
