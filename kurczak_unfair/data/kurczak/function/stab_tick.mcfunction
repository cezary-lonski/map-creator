tp @e[type=item_display,tag=k_fork,limit=1] @s
scoreboard players remove #g k_timer 1
execute if score #g k_timer matches 8 run data merge entity @e[type=item_display,tag=k_fork,limit=1] {start_interpolation:0,interpolation_duration:3,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0.7071f,0f,0f,0.7071f],translation:[0.35f,1.1f,1.7f],scale:[1.3f,1.3f,1.3f]}}
execute if score #g k_timer matches 0 run function kurczak:farmer_kill
