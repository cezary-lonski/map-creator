scoreboard players set #g k_state 10
summon minecraft:lightning_bolt 14.5 69 8.5
execute as @a run function kurczak:freeze
title @a title {"text":"Wieśniak: A CO TY ROBISZ?!","color":"red"}
schedule function kurczak:spawn_farmer 30t
