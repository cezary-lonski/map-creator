scoreboard players set #g k_state 1
scoreboard players set #g k_timer 30
summon minecraft:chicken 8.5 65 8.5 {NoAI:1b,Invulnerable:1b,DeathLootTable:"minecraft:empty",EggLayTime:1000000,Rotation:[0.0f,0.0f],CustomName:{text:"Kurczak"},Tags:["k_ent","k_chicken"]}
playsound minecraft:entity.chicken.ambient master @a 8 65 8 1 0.6
tellraw @a {"text":"Kurczak: ...","color":"yellow"}
