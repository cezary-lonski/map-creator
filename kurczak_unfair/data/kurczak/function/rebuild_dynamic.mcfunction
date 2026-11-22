setblock 8 66 12 minecraft:stone_button[face=wall,facing=north,powered=false] replace
setblock 8 65 5 minecraft:oak_log[axis=y] replace
setblock 8 65 7 minecraft:chest[facing=south] replace
data modify block 8 65 7 Items set value []
setblock 8 66 6 minecraft:oak_wall_sign[facing=south]{is_waxed:1b,allow_op_features:1b,front_text:{messages:[{text:"Otwórz mnie",click_event:{action:"run_command",command:"function kurczak:sign_click"}},"","",""]}} replace
setblock 14 69 8 minecraft:lightning_rod[facing=up] replace
setblock 9 68 5 minecraft:lightning_rod[facing=up] replace
setblock 13 65 12 minecraft:lightning_rod[facing=up] replace
