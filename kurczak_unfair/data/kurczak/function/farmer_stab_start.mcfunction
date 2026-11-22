scoreboard players set #g k_state 11
scoreboard players set #g k_timer 14
tp @s @s
data merge entity @e[type=item_display,tag=k_fork,limit=1] {start_interpolation:0,interpolation_duration:6,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0.7071f,0f,0f,0.7071f],translation:[0.35f,1.1f,-0.2f],scale:[1.3f,1.3f,1.3f]}}
playsound minecraft:entity.villager.no master @a ~ ~ ~ 1 0.7
