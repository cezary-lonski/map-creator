kill @e[tag=k_ent]
scoreboard players set #g k_state 0
scoreboard players set #g k_timer 0
execute as @a run function kurczak:unfreeze
effect clear @a
clear @a
function kurczak:rebuild_dynamic
