tag @s add k_peck
summon minecraft:chicken 8.5 65.9 7.5 {NoAI:1b,Invulnerable:1b,DeathLootTable:"minecraft:empty",EggLayTime:1000000,CustomName:{text:"Kurczak z dziupli"},Tags:["k_ent","k_chicken","k_pecker"]}
playsound minecraft:entity.chicken.hurt master @a 8 65 6 1 0.5
particle minecraft:poof 8.5 66 7.5 0.2 0.2 0.2 0.05 10
schedule function kurczak:peck_hit_all 12t
