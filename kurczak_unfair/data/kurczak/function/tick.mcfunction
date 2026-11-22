execute as @a[gamemode=!adventure,tag=!k_dev] run gamemode adventure @s
execute as @a[tag=!k_init] run function kurczak:first_join
execute as @a[scores={k_deaths=1..}] run function kurczak:on_death
execute as @a[scores={k_leave=1..}] run function kurczak:on_rejoin
execute as @a[tag=!k_dev,y=-100,dy=156] run function kurczak:kill_player
execute if score #g k_state matches 0..1 if block 8 66 12 minecraft:stone_button[powered=true] run function kurczak:button_by_player
execute if score #g k_state matches 1 if score #g k_timer matches 1.. run scoreboard players remove #g k_timer 1
execute if score #g k_state matches 1 if score #g k_timer matches 0 run function kurczak:spit
execute as @e[type=item_display,tag=k_corn] at @s run function kurczak:corn_step
execute as @e[type=chicken,tag=k_pecker] at @s run function kurczak:pecker_step
execute if score #g k_state matches 0..2 unless block 14 69 8 minecraft:lightning_rod run function kurczak:rod_a
execute if score #g k_state matches 0..2 unless block 9 68 5 minecraft:lightning_rod run function kurczak:rod_b
execute if score #g k_state matches 0..2 unless block 13 65 12 minecraft:lightning_rod run function kurczak:rod_c
execute if score #g k_state matches 10 as @e[type=villager,tag=k_farmer] at @s run function kurczak:farmer_step
execute if score #g k_state matches 11 as @e[type=villager,tag=k_farmer] at @s run function kurczak:stab_tick
execute if score #g k_state matches 20 run function kurczak:flight_tick
execute if score #g k_state matches 20 as @a[x=20,y=68,z=3,dx=2,dy=3,dz=4] run function kurczak:exit_reached
