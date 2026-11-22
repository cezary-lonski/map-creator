kill @s
scoreboard players set #g k_state 2
kill @e[tag=k_chicken]
particle minecraft:poof 8.5 65.5 8.5 0.3 0.3 0.3 0.1 20
setblock 8 66 12 minecraft:stone_button[face=wall,facing=north,powered=true] replace
playsound minecraft:block.stone_button.click_on master @a 8 66 12 1 1
data modify block 8 65 7 Items set value [{Slot:13b,id:"minecraft:iron_pickaxe",count:1,components:{"minecraft:can_break":{blocks:"minecraft:lightning_rod"},"minecraft:unbreakable":{},"minecraft:custom_name":{text:"Kilof farmera",italic:false}}}]
tellraw @a {"text":"Kurczak dostał kukurydzą we własny guzik. Skrzynka jest bezpieczna.","color":"green"}
