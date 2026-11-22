package com.chicken.mapcreator.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Pocisk rzucany przez moba: wyglada jak wybrany przedmiot. Trafiony gracz dostaje ustawione Damage,
 * trafiony przycisk zostaje wcisniety, a pocisk znika przy kazdym uderzeniu (Snowball usuwa sie po trafieniu).
 */
public class ThrownRuleItem extends Snowball {
	private final float damage;

	public ThrownRuleItem(Level level, LivingEntity owner, ItemStack stack, float damage) {
		super(level, owner, stack);
		this.damage = damage;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		if (this.level() instanceof ServerLevel serverLevel && hit.getEntity() instanceof ServerPlayer player) {
			LivingEntity owner = this.getOwner() instanceof LivingEntity living ? living : null;
			player.hurtServer(serverLevel, serverLevel.damageSources().mobProjectile(this, owner), damage);
		}
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		if (this.level() instanceof ServerLevel serverLevel) {
			BlockPos pos = hit.getBlockPos();
			BlockState state = serverLevel.getBlockState(pos);
			if (state.getBlock() instanceof ButtonBlock button && !state.getValue(ButtonBlock.POWERED)) {
				button.press(state, serverLevel, pos, null);
			}
		}
		super.onHitBlock(hit);
	}
}
