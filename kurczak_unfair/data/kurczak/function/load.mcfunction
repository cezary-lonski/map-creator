scoreboard objectives add k_state dummy
scoreboard objectives add k_timer dummy
scoreboard objectives add k_deaths deathCount
scoreboard objectives add k_leave custom:leave_game
gamerule immediate_respawn false
gamerule mob_drops false
gamerule fire_spread_radius_around_player 0
gamerule block_drops false
gamerule mob_griefing false
gamerule spawn_mobs false
gamerule advance_time false
gamerule advance_weather false
gamerule send_command_feedback false
gamerule command_block_output false
gamerule log_admin_commands false
gamerule keep_inventory false
time set day
weather clear
forceload add -2 -2 27 15
setworldspawn 8 65 10
execute unless score #g k_state matches 0.. run scoreboard players set #g k_state 0
