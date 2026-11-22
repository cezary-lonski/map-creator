scoreboard players set #g k_state 12
tellraw @p {"text":"Wieśniak dźgnął cię widłami.","color":"red"}
playsound minecraft:entity.player.hurt master @a ~ ~ ~ 1 0.8
damage @p 1000 minecraft:mob_attack by @s
kill @p[gamemode=adventure]
