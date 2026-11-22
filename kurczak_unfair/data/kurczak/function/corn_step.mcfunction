tp @s ~ ~ ~0.5
execute positioned ~ ~-1 ~ as @a[distance=..1.1] run function kurczak:corn_kill
execute positioned ~ ~ ~0.6 if block ~ ~ ~ minecraft:stone_button run function kurczak:corn_hits_button
