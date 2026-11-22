# Ledger - plan: docs/superpowers/plans/2026-11-21-podworko-plan.md
Pre-flight: tasks share state only through scoreboard `#g k_state`; values documented in tick.mcfunction.
Task 1-6: files written; NOT run in game (no Minecraft available to the executor).
Ruling: Minecraft 1.21.4, pack_format 61 - user did not state the version - cost: syntax changes needed for other versions (see README).
Ruling: house moved to x13..21 and exit platform to x23..25 - plan had no 1-block gaps so jumps would not be jumps - cost: none, coordinates in plan for A/B/C unchanged.
Ruling: fall damage in tree crown prevented with Resistance V pulses, not fall_distance NBT - data modify cannot edit players - cost: 1 s of leniency after landing.
Ruling: chest holds the pickaxe only after the chicken dies (state 2) - cost: none.
Ruling: no git/TDD - folder is not a repository and the game cannot be run here; verification replaced by syntax lint + in-game checklist in README.
Ruling: retargeted to 1.21.11 (user: 1.21.11 or 26.3) - 26.3 syntax unverified, only allow_op_features added to the sign - cost: rework if 26.3 is the real target.
Ruling: pack.mcmeta min_format/max_format 94 - wiki lists 94.1 but not how minor versions are written - cost: possible incompatibility warning.
Ruling: doFireTick has no 1.21.11 equivalent, replaced by fire_spread_radius_around_player 0 - cost: none.
Ruling: v2 - hollow = barrel in oak trunk (8,65,5); oak via place feature minecraft:oak (random shape, landing zone x5..11 y67..74); house = village template plains_small_house_1 rotated counterclockwise_90 at (12,64,8); platform moved to x20..22 - cost: oak shape varies per build.
Ruling: rod A on roof eave (14,69,8); user wrote 'na rdzewie' (unclear) - interpreted as roof - cost: relocate A if meant otherwise. Rod B on lantern (9,67,5) in tree at (9,68,5).
Ruling: v3 - roof lowered by removing template layers y69-70 and capping 14..16,68,4..6; farmer is a villager with a separate trident display (stab animation, damage by mob_attack) - cost: fork orientation not verified in game.
Ruling: v4 - found in latest.log: lightning turned the farmer villager into a witch; farmer now spawns 30t after the strike (schedule spawn_farmer). Chest (8,65,7) in front of the tree replaces the hollow barrel - cost: none.
Ruling: v5 - rods react in states 0..2 (user broke rods without chicken solved / with k_dev); 1x3x2 viewing window carved in the oak crown at x9 y67..69 z6..7 so rod B is findable - cost: none.
