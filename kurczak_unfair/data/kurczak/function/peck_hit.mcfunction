tellraw @s {"text":"Ze skrzynki wyskoczył kurczak i dziobnął cię na śmierć.","color":"red"}
execute unless entity @e[type=chicken,tag=k_pecker] run kill @s
damage @s 1000 minecraft:mob_attack by @e[type=chicken,tag=k_pecker,limit=1]
