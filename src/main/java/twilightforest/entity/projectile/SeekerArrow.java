package twilightforest.entity.projectile;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import twilightforest.entity.TFEntities;

public class SeekerArrow extends TFArrow {

	private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(SeekerArrow.class, EntityDataSerializers.INT);

	private static final double seekDistance = 5.0;
	private static final double seekFactor = 0.8;
	private static final double seekAngle = Math.PI / 6.0;
	private static final double seekThreshold = 0.5;

	public SeekerArrow(EntityType<? extends SeekerArrow> type, Level world) {
		super(type, world);
		setBaseDamage(1.0D);
	}

	public SeekerArrow(Level world, Entity shooter) {
		super(TFEntities.SEEKER_ARROW.get(), world, shooter);
		setBaseDamage(1.0D);
	}

	@Override
	protected void defineSynchedData() {
		super.defineSynchedData();
		entityData.define(TARGET, -1);
	}

	@Override
	public void tick() {
		if (isThisArrowFlying()) {
			if (!level.isClientSide) {
				updateTarget();
			}

			if (level.isClientSide && !inGround) {
				for (int i = 0; i < 4; ++i) {
					this.level.addParticle(ParticleTypes.WITCH, this.getX() + this.getDeltaMovement().x() * i / 4.0D, this.getY() + this.getDeltaMovement().y() * i / 4.0D, this.getZ() + this.getDeltaMovement().z() * i / 4.0D, -this.getDeltaMovement().x(), -this.getDeltaMovement().y() + 0.2D, -this.getDeltaMovement().z());
				}
			}

			Entity target = getTarget();
			if (target != null) {

				Vec3 targetVec = getVectorToTarget(target).scale(seekFactor);
				Vec3 courseVec = getMotionVec();

				// vector lengths
				double courseLen = courseVec.length();
				double targetLen = targetVec.length();
				double totalLen = Math.sqrt(courseLen*courseLen + targetLen*targetLen);

				double dotProduct = courseVec.dot(targetVec) / (courseLen * targetLen); // cosine similarity

				if (dotProduct > seekThreshold) {

					// add vector to target, scale to match current velocity
					Vec3 newMotion = courseVec.scale(courseLen / totalLen).add(targetVec.scale(courseLen / totalLen));

					this.setDeltaMovement(newMotion.add(0, 0.045F, 0));

				} else if (!level.isClientSide) {
					// too inaccurate for our intended target, give up on it
					setTarget(null);
				}
			}
		}

		super.tick();
	}

	private void updateTarget() {

		Entity target = getTarget();

		if (target != null && !target.isAlive()) {
			target = null;
			setTarget(null);
		}

		if (target == null) {
			AABB positionBB = new AABB(getX(), getY(), getZ(), getX(), getY(), getZ());
			AABB targetBB = positionBB;

			// add two possible courses to our selection box
			Vec3 courseVec = getMotionVec().scale(seekDistance).yRot((float) seekAngle);
			targetBB = targetBB.minmax(positionBB.move(courseVec));

			courseVec = getMotionVec().scale(seekDistance).yRot((float) -seekAngle);
			targetBB = targetBB.minmax(positionBB.move(courseVec));

			targetBB = targetBB.inflate(0, seekDistance * 0.5, 0);

			List<LivingEntity> entityList = this.level.getEntitiesOfClass(LivingEntity.class, targetBB);
			List<LivingEntity> monsters = entityList.stream().filter(l -> l instanceof Monster).collect(Collectors.toList());

			if(!monsters.isEmpty()) {
				for (LivingEntity monster : monsters) {
					if (((Monster) monster).getTarget() == this.getOwner()) {
						if (!ChangeTargetEvent.postOnChange(this, getTarget(), monster))
						{
							setTarget(monster);
							return;
						}
					}
				}
				for (LivingEntity monster : monsters) {
					if(monster instanceof NeutralMob) continue;

					if (monster.hasLineOfSight(this)) {
						if (!ChangeTargetEvent.postOnChange(this, getTarget(), monster))
						{
							setTarget(monster);
							return;
						}
					}
				}
			}

			Vec3 motionVec = getMotionVec().normalize();
			
			entityList = entityList.stream()
			// Remove monsters as we already checked above
			.filter(e -> !(e instanceof Monster))
			// Primary checks
			.filter(living -> {
				if(!living.hasLineOfSight(this)) 
					return false;
				if (living == this.getOwner()) 
					return false;
				if (getOwner() != null && living instanceof TamableAnimal animal && animal.getOwner() == this.getOwner()) 
					return false;
				if (motionVec.dot(getVectorToTarget(living).normalize()) <= seekThreshold) 
					return false;
				return true;
			})
			// Sort by angle cosine, descending
			.sorted(Comparator.comparingDouble((LivingEntity entity) -> 
				motionVec.dot(getVectorToTarget(entity).normalize())
			).reversed())
			.collect(Collectors.toList());

			// Find and set the first target that isn't canceled by event
			for (int i = 0; i < entityList.size(); ++i)
			{
				if (!ChangeTargetEvent.postOnChange(this, getTarget(), entityList.get(i)))
				{
					setTarget(entityList.get(i));
					break;
				}
			}
		}
	}

	private Vec3 getMotionVec() {
		return new Vec3(this.getDeltaMovement().x(), this.getDeltaMovement().y(), this.getDeltaMovement().z());
	}

	private Vec3 getVectorToTarget(Entity target) {
		return new Vec3(target.getX() - this.getX(), (target.getY() + target.getEyeHeight()) - this.getY(), target.getZ() - this.getZ());
	}

	@Nullable
	private Entity getTarget() {
		return level.getEntity(entityData.get(TARGET));
	}

	private void setTarget(@Nullable Entity e) {
		entityData.set(TARGET, e == null ? -1 : e.getId());
	}

	private boolean isThisArrowFlying() {
		return !inGround && getDeltaMovement().lengthSqr() > 1.0;
	}

	@Override
	protected void onHitEntity(EntityHitResult pResult) {
		this.setCritArrow(false);
		super.onHitEntity(pResult);
	}
	
	/**
	 * Fired on seeker arrow changing target.
	 * <p> If canceled, the seeker arrow will discard this target and seek for next one. 
	 * Specially, if the new target is null, cancellation means the previous target won't be removed.
	 */
	@Cancelable
	public static class ChangeTargetEvent extends Event
	{
		public final SeekerArrow arrow;
		@Nullable
		public final Entity oldTarget;
		@Nullable
		public final Entity newTarget;
		protected ChangeTargetEvent(SeekerArrow arrow, @Nullable Entity oldTarget, @Nullable Entity newTarget)
		{
			this.arrow = arrow;
			this.oldTarget = oldTarget;
			this.newTarget = newTarget;
		}
		
		/**
		 * Post event on target changes.
		 * @param arrow Seeker Arrow entity.
		 * @param oldTarget Target before setting.
		 * @param newTarget Target after setting.
		 * @return Whether the event is canceled, or false if not posted.
		 */
		public static boolean postOnChange(SeekerArrow arrow, Entity oldTarget, Entity newTarget)
		{
			// Prevent from unexpectly posting on invalid entities
			if (!oldTarget.isAlive())
				oldTarget = null;
			if (!newTarget.isAlive())
				newTarget = null;
			// Don't post if not changed
			if (oldTarget == newTarget)
				return false;
			return MinecraftForge.EVENT_BUS.post(new ChangeTargetEvent(arrow, oldTarget, newTarget));
		}
	}
}
