execute if entity @p[distance=..2.2] run function kurczak:farmer_stab_start
execute unless entity @p[distance=..2.2] facing entity @p feet run tp @s ^ ^ ^0.12 facing entity @p feet
tp @e[type=item_display,tag=k_fork,limit=1] @s
