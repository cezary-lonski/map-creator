package com.chicken.mapcreator.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Jedna regula: wywolywacz -> zdarzenia -> efekty dla gracza (+ opcjonalny NPC).
 * Klucz reguly w swiecie to kotwica + rodzaj (RULE albo NPC).
 */
public record Rule(
		UUID id,
		Kind kind,
		Anchor anchor,
		Trigger trigger,
		List<Event> events,
		List<MobSpec> mobs,
		PlayerEffects effects,
		Optional<NpcSpec> npc
) {
	public enum Kind { RULE, NPC }

	public enum TriggerType { NONE, CLICK, STAND, REDSTONE, OPEN, BREAK, AFTER_EVENT, IF_HAPPENED, NPC_STEP_ON, NPC_PICKUP, NPC_HURT }

	public enum EventType { NONE, BROKE, CHANGE_BLOCK }

	static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type, E fallback) {
		return Codec.STRING.xmap(name -> {
			try {
				return Enum.valueOf(type, name);
			} catch (IllegalArgumentException e) {
				return fallback;
			}
		}, Enum::name);
	}

	public record Anchor(String dimension, BlockPos pos) {
		public static final Codec<Anchor> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dimension").forGetter(Anchor::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Anchor::pos)
		).apply(i, Anchor::new));
	}

	public record Area(BlockPos a, BlockPos b) {
		public static final Codec<Area> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("a").forGetter(Area::a),
				BlockPos.CODEC.fieldOf("b").forGetter(Area::b)
		).apply(i, Area::new));

		public boolean contains(BlockPos p) {
			return p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
					&& p.getY() >= Math.min(a.getY(), b.getY()) && p.getY() <= Math.max(a.getY(), b.getY())
					&& p.getZ() >= Math.min(a.getZ(), b.getZ()) && p.getZ() <= Math.max(a.getZ(), b.getZ());
		}
	}

	public record Trigger(TriggerType type, Optional<Area> area, List<UUID> ifIds, String filter) {
		public static final Codec<Trigger> CODEC = RecordCodecBuilder.create(i -> i.group(
				enumCodec(TriggerType.class, TriggerType.CLICK).fieldOf("type").forGetter(Trigger::type),
				Area.CODEC.optionalFieldOf("area").forGetter(Trigger::area),
				UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("if_ids", List.of()).forGetter(Trigger::ifIds),
				Codec.STRING.optionalFieldOf("filter", "").forGetter(Trigger::filter)
		).apply(i, Trigger::new));

		public static Trigger of(TriggerType type) {
			return new Trigger(type, Optional.empty(), List.of(), "");
		}
	}

	public record Event(EventType type, Optional<Area> area, String block) {
		public static final Codec<Event> CODEC = RecordCodecBuilder.create(i -> i.group(
				enumCodec(EventType.class, EventType.NONE).fieldOf("type").forGetter(Event::type),
				Area.CODEC.optionalFieldOf("area").forGetter(Event::area),
				Codec.STRING.optionalFieldOf("block", "").forGetter(Event::block)
		).apply(i, Event::new));
	}

	/** Dodatkowe ustawienia moba: nieśmiertelnosc na gracza, opoznienie (s) przed rzutem/atakiem, grawitacja pocisku. */
	public record MobExtras(boolean immune, int delay, boolean gravity) {
		public static final MobExtras DEFAULT = new MobExtras(false, 0, true);
		public static final Codec<MobExtras> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("immune", false).forGetter(MobExtras::immune),
				Codec.INT.optionalFieldOf("delay", 0).forGetter(MobExtras::delay),
				Codec.BOOL.optionalFieldOf("gravity", true).forGetter(MobExtras::gravity)
		).apply(i, MobExtras::new));
	}

	/**
	 * Mob wywolywany przez regule. action: NONE (tylko stoi), THROW (rzuca w gracza), ATTACK (atakuje) albo GO (idzie do goTo).
	 * item = przedmiot w rece i do rzutu; drop = przedmiot wyrzucany po wywolaniu; deathText = wlasny napis smierci gracza
	 * zabitego przez tego moba; extras = nieśmiertelnosc, opoznienie i grawitacja rzutu.
	 */
	public record MobSpec(String mob, String nick, String action, String item, double damage,
			String offhand, String head, String chest, String legs, String feet, String drop, Optional<BlockPos> spawn,
			String deathText, Optional<BlockPos> goTo, MobExtras extras) {
		public static final Codec<MobSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("mob").forGetter(MobSpec::mob),
				Codec.STRING.optionalFieldOf("nick", "").forGetter(MobSpec::nick),
				Codec.STRING.optionalFieldOf("action", "NONE").forGetter(MobSpec::action),
				Codec.STRING.optionalFieldOf("item", "").forGetter(MobSpec::item),
				Codec.DOUBLE.optionalFieldOf("damage", 1.0).forGetter(MobSpec::damage),
				Codec.STRING.optionalFieldOf("offhand", "").forGetter(MobSpec::offhand),
				Codec.STRING.optionalFieldOf("head", "").forGetter(MobSpec::head),
				Codec.STRING.optionalFieldOf("chest", "").forGetter(MobSpec::chest),
				Codec.STRING.optionalFieldOf("legs", "").forGetter(MobSpec::legs),
				Codec.STRING.optionalFieldOf("feet", "").forGetter(MobSpec::feet),
				Codec.STRING.optionalFieldOf("drop", "").forGetter(MobSpec::drop),
				BlockPos.CODEC.optionalFieldOf("spawn").forGetter(MobSpec::spawn),
				Codec.STRING.optionalFieldOf("death_text", "").forGetter(MobSpec::deathText),
				BlockPos.CODEC.optionalFieldOf("go_to").forGetter(MobSpec::goTo),
				MobExtras.CODEC.optionalFieldOf("extras", MobExtras.DEFAULT).forGetter(MobSpec::extras)
		).apply(i, MobSpec::new));

		public static MobSpec create(String mob, String nick) {
			return new MobSpec(mob, nick, "NONE", "", 1.0, "", "", "", "", "", "", Optional.empty(), "", Optional.empty(), MobExtras.DEFAULT);
		}

		public boolean immune() {
			return extras.immune();
		}

		public int delay() {
			return extras.delay();
		}

		public boolean gravity() {
			return extras.gravity();
		}

		public MobSpec withAction(String v) {
			return new MobSpec(mob, nick, v, item, damage, offhand, head, chest, legs, feet, drop, spawn, deathText, goTo, extras);
		}

		public MobSpec withDamage(double v) {
			return new MobSpec(mob, nick, action, item, v, offhand, head, chest, legs, feet, drop, spawn, deathText, goTo, extras);
		}

		public MobSpec withSpawn(Optional<BlockPos> v) {
			return new MobSpec(mob, nick, action, item, damage, offhand, head, chest, legs, feet, drop, v, deathText, goTo, extras);
		}

		public MobSpec withDeathText(String v) {
			return new MobSpec(mob, nick, action, item, damage, offhand, head, chest, legs, feet, drop, spawn, v, goTo, extras);
		}

		public MobSpec withGoTo(Optional<BlockPos> v) {
			return new MobSpec(mob, nick, action, item, damage, offhand, head, chest, legs, feet, drop, spawn, deathText, v, extras);
		}

		public MobSpec withImmune(boolean v) {
			return new MobSpec(mob, nick, action, item, damage, offhand, head, chest, legs, feet, drop, spawn, deathText, goTo,
					new MobExtras(v, extras.delay(), extras.gravity()));
		}

		public MobSpec withDelay(int v) {
			return new MobSpec(mob, nick, action, item, damage, offhand, head, chest, legs, feet, drop, spawn, deathText, goTo,
					new MobExtras(extras.immune(), v, extras.gravity()));
		}

		public MobSpec withGravity(boolean v) {
			return new MobSpec(mob, nick, action, item, damage, offhand, head, chest, legs, feet, drop, spawn, deathText, goTo,
					new MobExtras(extras.immune(), extras.delay(), v));
		}

		/** slot: item (reka), offhand, head, chest, legs, feet, drop. */
		public MobSpec withEquip(String slot, String id) {
			return new MobSpec(mob, nick, action, slot.equals("item") ? id : item, damage,
					slot.equals("offhand") ? id : offhand, slot.equals("head") ? id : head, slot.equals("chest") ? id : chest,
					slot.equals("legs") ? id : legs, slot.equals("feet") ? id : feet, slot.equals("drop") ? id : drop,
					spawn, deathText, goTo, extras);
		}
	}

	public record ItemSpec(String item, int count, String name) {
		public static final Codec<ItemSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("item").forGetter(ItemSpec::item),
				Codec.INT.optionalFieldOf("count", 1).forGetter(ItemSpec::count),
				Codec.STRING.optionalFieldOf("name", "").forGetter(ItemSpec::name)
		).apply(i, ItemSpec::new));
	}

	public record EffectSpec(String effect, int amplifier, int seconds) {
		public static final Codec<EffectSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("effect").forGetter(EffectSpec::effect),
				Codec.INT.optionalFieldOf("amplifier", 0).forGetter(EffectSpec::amplifier),
				Codec.INT.optionalFieldOf("seconds", 10).forGetter(EffectSpec::seconds)
		).apply(i, EffectSpec::new));
	}

	public record PlayerEffects(boolean kill, String killText, List<ItemSpec> give, Optional<BlockPos> tp,
			List<EffectSpec> effects, String screenText) {
		public static final PlayerEffects NONE = new PlayerEffects(false, "", List.of(), Optional.empty(), List.of(), "");
		public static final Codec<PlayerEffects> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("kill", false).forGetter(PlayerEffects::kill),
				Codec.STRING.optionalFieldOf("kill_text", "").forGetter(PlayerEffects::killText),
				ItemSpec.CODEC.listOf().optionalFieldOf("give", List.of()).forGetter(PlayerEffects::give),
				BlockPos.CODEC.optionalFieldOf("tp").forGetter(PlayerEffects::tp),
				EffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(PlayerEffects::effects),
				Codec.STRING.optionalFieldOf("screen_text", "").forGetter(PlayerEffects::screenText)
		).apply(i, PlayerEffects::new));
	}

	public record NpcSpec(String mob, String skin, String action, String tradeIn, String tradeOut, double damage,
			Optional<BlockPos> goTo, boolean deleteOnTrigger, Optional<UUID> uuid, boolean playerImmune,
			Optional<BlockPos> pos, boolean appear) {
		public static final Codec<NpcSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("mob").forGetter(NpcSpec::mob),
				Codec.STRING.optionalFieldOf("skin", "").forGetter(NpcSpec::skin),
				Codec.STRING.optionalFieldOf("action", "NONE").forGetter(NpcSpec::action),
				Codec.STRING.optionalFieldOf("trade_in", "").forGetter(NpcSpec::tradeIn),
				Codec.STRING.optionalFieldOf("trade_out", "").forGetter(NpcSpec::tradeOut),
				Codec.DOUBLE.optionalFieldOf("damage", 1.0).forGetter(NpcSpec::damage),
				BlockPos.CODEC.optionalFieldOf("go_to").forGetter(NpcSpec::goTo),
				Codec.BOOL.optionalFieldOf("delete_on_trigger", true).forGetter(NpcSpec::deleteOnTrigger),
				UUIDUtil.STRING_CODEC.optionalFieldOf("uuid").forGetter(NpcSpec::uuid),
				Codec.BOOL.optionalFieldOf("player_immune", true).forGetter(NpcSpec::playerImmune),
				BlockPos.CODEC.optionalFieldOf("pos").forGetter(NpcSpec::pos),
				Codec.BOOL.optionalFieldOf("appear", false).forGetter(NpcSpec::appear)
		).apply(i, NpcSpec::new));
	}

	public static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Rule::id),
			enumCodec(Kind.class, Kind.RULE).optionalFieldOf("kind", Kind.RULE).forGetter(Rule::kind),
			Anchor.CODEC.fieldOf("anchor").forGetter(Rule::anchor),
			Trigger.CODEC.fieldOf("trigger").forGetter(Rule::trigger),
			Event.CODEC.listOf().optionalFieldOf("events", List.of()).forGetter(Rule::events),
			MobSpec.CODEC.listOf().optionalFieldOf("mobs", List.of()).forGetter(Rule::mobs),
			PlayerEffects.CODEC.optionalFieldOf("effects", PlayerEffects.NONE).forGetter(Rule::effects),
			NpcSpec.CODEC.optionalFieldOf("npc").forGetter(Rule::npc)
	).apply(i, Rule::new));

	/** Krotki identyfikator do GUI, np. a1b2c3. */
	public String shortId() {
		return id.toString().substring(0, 6);
	}

	public String key() {
		return keyOf(anchor, kind);
	}

	public static String keyOf(Anchor anchor, Kind kind) {
		return kind + "|" + anchor.dimension() + "|" + anchor.pos().getX() + "," + anchor.pos().getY() + "," + anchor.pos().getZ();
	}
}
