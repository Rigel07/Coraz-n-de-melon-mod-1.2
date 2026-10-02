package com.corazondemelon.entity;

import com.corazondemelon.MelonConfig;
import com.corazondemelon.ai.MaoActions;
import com.corazondemelon.innocence.Innocence;
import com.corazondemelon.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.List;

/**
 * Mao: un pequeño ángel volador.
 *  - Salvaje: se acerca a jugadores con Inocencia 6+ y les regala objetos. Huye de jugadores "Brutos" (nv 1-3).
 *  - Domesticable (nv 3+) con zanahorias doradas. Una vez domesticado es INMORTAL, te sigue (u obedece "quédate"),
 *    te defiende, te cura y es tu asistente por el chat (ver MaoBrain / MaoActions).
 */
public class MaoEntity extends TamableAnimal {
    public static final int GIFT_MIN_LEVEL = 6;
    public static final int TAME_MIN_LEVEL = 3;

    private record GiftEntry(Item item, int minLevel, int weight, int max) {}

    private static final List<GiftEntry> GIFTS = List.of(
            new GiftEntry(Items.APPLE, 6, 30, 3),
            new GiftEntry(Items.BREAD, 6, 25, 2),
            new GiftEntry(Items.COOKIE, 6, 25, 4),
            new GiftEntry(Items.GOLDEN_CARROT, 6, 20, 2),
            new GiftEntry(Items.EXPERIENCE_BOTTLE, 7, 10, 2),
            new GiftEntry(Items.EMERALD, 8, 6, 2),
            new GiftEntry(Items.GOLDEN_APPLE, 9, 4, 1),
            new GiftEntry(Items.DIAMOND, 10, 2, 1)
    );

    private long nextGift = 0L;
    private long nextHeal = 0L;
    private long nextAnnounce = 0L;

    /** Estructuras ya comentadas por este Mao (para no repetirse). */
    public final Set<String> announced = new HashSet<>();

    public MaoEntity(EntityType<? extends MaoEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.FLYING_SPEED, 0.6D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    public boolean isOwnedByUuid(Player player) {
        UUID id = this.getOwnerUUID();
        return this.isTame() && id != null && id.equals(player.getUUID());
    }

    /** El Mao domesticado más cercano al jugador; si no hay ninguno cerca, cualquiera suyo que esté cargado en cualquier dimensión. */
    @Nullable
    public static MaoEntity findOwnedMao(ServerPlayer player) {
        List<MaoEntity> list = player.level().getEntitiesOfClass(MaoEntity.class,
                player.getBoundingBox().inflate(64), m -> m.isAlive() && m.isOwnedByUuid(player));
        MaoEntity near = list.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(player))).orElse(null);
        if (near != null) return near;
        for (ServerLevel lv : player.server.getAllLevels()) {
            for (Entity e : lv.getAllEntities()) {
                if (e instanceof MaoEntity m && m.isAlive() && m.isOwnedByUuid(player)) return m;
            }
        }
        return null;
    }

    /**
     * Mueve a Mao a otro punto, también entre dimensiones (copiando sus datos).
     * @return el Mao resultante (en otra dimensión es una copia con los mismos datos y dueño).
     */
    public MaoEntity warpTo(ServerLevel target, double x, double y, double z) {
        this.getNavigation().stop();
        if (target == this.level()) {
            this.moveTo(x, y, z, this.getYRot(), this.getXRot());
            this.setDeltaMovement(Vec3.ZERO);
            return this;
        }
        CompoundTag tag = new CompoundTag();
        this.saveWithoutId(tag);
        MaoEntity copy = ModEntities.MAO.get().create(target);
        if (copy == null) return this;
        copy.load(tag);
        copy.moveTo(x, y, z, this.getYRot(), this.getXRot());
        copy.setDeltaMovement(Vec3.ZERO);
        target.addFreshEntity(copy);
        this.discard();
        return copy;
    }

    /** Si cae al vacío lo devuelve junto a su dueño (o al spawn del mundo). */
    private void rescue() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        UUID id = this.getOwnerUUID();
        ServerPlayer owner = id == null ? null : sl.getServer().getPlayerList().getPlayer(id);
        if (owner != null) {
            this.warpTo(owner.serverLevel(), owner.getX(), owner.getY() + 1.0, owner.getZ());
        } else {
            BlockPos sp = sl.getSharedSpawnPos();
            this.warpTo(sl, sp.getX() + 0.5, sp.getY() + 1.0, sp.getZ() + 0.5);
        }
    }

    // ------------------------------------------------------------------ IA / movimiento

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.4D) {
            @Override
            public boolean canUse() {
                return !MaoEntity.this.isTame() && super.canUse();
            }
        });
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 10.0F, 1.2D, 1.5D,
                e -> !this.isTame() && e instanceof Player pl && Innocence.getLevel(pl) < TAME_MIN_LEVEL));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0D, 5.0F, 2.0F, true));
        this.goalSelector.addGoal(5, new ApproachInnocentGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return MaoEntity.this.isTame() && super.canUse();
            }
        });
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                e -> this.isTame() && !this.isOrderedToSit() && !(e instanceof Creeper)));
    }

    /** Un Mao salvaje se acerca a quien tenga suficiente Inocencia. */
    private static class ApproachInnocentGoal extends Goal {
        private final MaoEntity mao;
        private Player target;
        private int recalc;

        ApproachInnocentGoal(MaoEntity mao) {
            this.mao = mao;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mao.isTame()) return false;
            Player p = mao.level().getNearestPlayer(mao, 16.0D);
            if (p == null || p.isSpectator() || Innocence.getLevel(p) < TAME_MIN_LEVEL || mao.distanceToSqr(p) < 9.0D) return false;
            target = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return target != null && target.isAlive() && !mao.isTame() && mao.distanceToSqr(target) > 6.0D;
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void tick() {
            if (--recalc <= 0) {
                recalc = 10;
                mao.getNavigation().moveTo(target, 1.0D);
            }
            mao.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        @Override
        public void stop() {
            target = null;
            mao.getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------ comportamiento

    @Override
    public void aiStep() {
        super.aiStep();
        if (!this.level().isClientSide && this.isAlive() && this.tickCount % 20 == 0) {
            serverTick();
        }
    }

    private void serverTick() {
        long now = this.level().getGameTime();
        ServerLevel level = (ServerLevel) this.level();

        if (!this.isTame()) {
            Player near = level.getNearestPlayer(this, 5.0D);
            if (near instanceof ServerPlayer sp && !sp.isSpectator() && now >= nextGift) {
                int lvl = Innocence.getLevel(sp);
                if (lvl >= GIFT_MIN_LEVEL) {
                    giveGift(level, sp, lvl);
                    nextGift = now + 4800 + this.random.nextInt(4800);
                }
            }
        } else {
            if (this.getHealth() < this.getMaxHealth()) this.heal(1.0F);
            if (this.getOwner() instanceof ServerPlayer owner) {
                if (now >= nextHeal && owner.getHealth() < owner.getMaxHealth() * 0.5F && this.distanceToSqr(owner) < 256.0D) {
                    owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                    level.sendParticles(ParticleTypes.HEART, owner.getX(), owner.getY() + 1.8, owner.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
                    level.playSound(null, this.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1.0F, 1.4F);
                    nextHeal = now + 600;
                }
                if (MelonConfig.ANNOUNCE_STRUCTURES.get() && this.tickCount % 200 == 0 && now >= nextAnnounce
                        && this.level() == owner.level() && this.distanceToSqr(owner) < 1600.0D) {
                    if (MaoActions.announceNearby(owner, this)) nextAnnounce = now + 1200;
                }
            }
        }
    }

    private void giveGift(ServerLevel level, ServerPlayer player, int lvl) {
        List<GiftEntry> pool = new ArrayList<>();
        int total = 0;
        for (GiftEntry g : GIFTS) {
            if (lvl >= g.minLevel()) {
                pool.add(g);
                total += g.weight();
            }
        }
        if (pool.isEmpty()) return;
        int roll = this.random.nextInt(total);
        GiftEntry chosen = pool.get(0);
        for (GiftEntry g : pool) {
            roll -= g.weight();
            if (roll < 0) {
                chosen = g;
                break;
            }
        }
        ItemStack stack = new ItemStack(chosen.item(), 1 + this.random.nextInt(chosen.max()));
        ItemEntity item = new ItemEntity(level, this.getX(), this.getY() + 0.4, this.getZ(), stack);
        Vec3 dir = player.position().subtract(this.position());
        dir = new Vec3(dir.x, 0, dir.z).normalize().scale(0.2);
        item.setDeltaMovement(dir.x, 0.2, dir.z);
        item.setPickUpDelay(15);
        level.addFreshEntity(item);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
        level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.0, this.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
        level.playSound(null, this.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1.0F, 1.3F);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!this.isTame()) {
            if (!stack.is(Items.GOLDEN_CARROT)) return InteractionResult.PASS;
            if (this.level().isClientSide) return InteractionResult.CONSUME;
            int lvl = Innocence.getLevel(player);
            if (lvl < TAME_MIN_LEVEL) {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("message.corazondemelon.mao_distrust"), true);
                }
                return InteractionResult.SUCCESS;
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (this.random.nextInt(lvl >= 9 ? 2 : 3) == 0) {
                this.tame(player);
                this.setPersistenceRequired();
                this.navigation.stop();
                this.setTarget(null);
                this.setOrderedToSit(false);
                this.level().broadcastEntityEvent(this, (byte) 7);
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable("message.corazondemelon.mao_tamed"), true);
                    Innocence.addXp(sp, 2.0F);
                }
            } else {
                this.level().broadcastEntityEvent(this, (byte) 6);
            }
            return InteractionResult.SUCCESS;
        }

        if (this.isOwnedBy(player)) {
            if (stack.is(Items.GOLDEN_CARROT) && this.getHealth() < this.getMaxHealth()) {
                if (!this.level().isClientSide) {
                    this.heal(8.0F);
                    if (!player.getAbilities().instabuild) stack.shrink(1);
                    this.level().broadcastEntityEvent(this, (byte) 7);
                }
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            if (!this.level().isClientSide) {
                boolean sit = !this.isOrderedToSit();
                this.setOrderedToSit(sit);
                this.setInSittingPose(sit);
                this.navigation.stop();
                this.setTarget(null);
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(Component.translatable(sit
                            ? "message.corazondemelon.mao_sit" : "message.corazondemelon.mao_follow"), true);
                }
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Un Mao domesticado es inmortal: ignora todo daño (salvo /kill de un admin). Si cae al vacío, vuelve con su dueño. */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (this.isTame() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean fireImmune() {
        return this.isTame() || super.fireImmune();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isTame() && source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            if (!this.level().isClientSide) this.rescue();
            return false;
        }
        if (this.isInvulnerableTo(source)) return false;
        if (!this.level().isClientSide) {
            this.setOrderedToSit(false);
            this.setInSittingPose(false);
        }
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ misc

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canMate(Animal other) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 2;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }
}
