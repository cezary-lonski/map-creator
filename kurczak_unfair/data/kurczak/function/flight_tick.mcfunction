execute as @a[x=5,y=67,z=2,dx=6,dy=7,dz=6] run effect give @s minecraft:resistance 1 4 true
execute if score #g k_timer matches 1.. run scoreboard players remove #g k_timer 1
execute if score #g k_timer matches 0 run function kurczak:flight_end
