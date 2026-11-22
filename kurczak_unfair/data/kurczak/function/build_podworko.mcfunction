forceload add -2 -2 27 15
fill -2 63 -2 27 80 15 minecraft:air
fill -1 64 -1 19 64 13 minecraft:grass_block
fill -1 65 -1 -1 67 13 minecraft:cobblestone_wall
fill -1 65 -1 19 67 -1 minecraft:cobblestone_wall
fill 19 65 -1 19 67 13 minecraft:cobblestone_wall
fill -1 65 13 19 68 13 minecraft:stone_bricks
place feature minecraft:oak 8 65 5
place template minecraft:village/plains/houses/plains_small_house_1 12 64 8 counterclockwise_90
fill 12 69 2 18 70 8 minecraft:air
fill 14 68 4 16 68 6 minecraft:oak_planks
setblock 15 64 8 minecraft:grass_block
setblock 15 64 5 minecraft:oak_planks
setblock 9 66 5 minecraft:oak_log[axis=x]
fill 9 67 6 9 69 7 minecraft:air
setblock 9 67 5 minecraft:lantern[hanging=false]
fill 20 64 3 22 68 7 minecraft:hay_block
function kurczak:rebuild_dynamic
