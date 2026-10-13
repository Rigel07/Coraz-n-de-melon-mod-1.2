package com.corazondemelon.entity;

import com.corazondemelon.MelonConfig;
import com.corazondemelon.ai.MaoActions;
import com.corazondemelon.ai.MaoMods;
import com.corazondemelon.innocence.Innocence;
import com.corazondemelon.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
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
    private static final EntityDataAccessor<Integer> MAO_COLOR = SynchedEntityData.defineId(MaoEntity.class, EntityDataSerializers.INT);
    private static final String DATA_REPRESENTATIVE = "cdm_representative";
    private static final String DATA_PATROL = "cdm_patrol";
    private static final String DATA_PATROL_X = "cdm_patrol_x";
    private static final String DATA_PATROL_Y = "cdm_patrol_y";
    private static final String DATA_PATROL_Z = "cdm_patrol_z";
    private static final String DATA_PATROL_RADIUS = "cdm_patrol_radius";
    private static final String DATA_MINING_TARGET = "cdm_mining_target";

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
    private long nextSocial = 0L;
    private long nextHugXp = 0L;

    /** Amigo (de otro mod o mascota del mismo dueño) al que Mao va a abrazar. */
    private LivingEntity hugTarget;
    private long hugUntil = 0L;

    /** Estructuras ya comentadas por este Mao (para no repetirse). */
    public final Set<String> announced = new HashSet<>();

    public MaoEntity(EntityType<? extends MaoEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MAO_COLOR, 0);
    }

    public int getMaoColor() { return this.entityData.get(MAO_COLOR); }
    public void setMaoColor(String color) {
        int value = switch (color.toLowerCase(java.util.Locale.ROOT)) {
            case "verde", "green" -> 1;
            case "azul", "blue" -> 2;
            case "violeta", "purple", "morado" -> 3;
            default -> 0;
        };
        this.entityData.set(MAO_COLOR, value);
        this.getPersistentData().putInt("cdm_color", value);
    }
    public String getMaoColorName() { return switch (getMaoColor()) { case 1 -> "Verde"; case 2 -> "Azul"; case 3 -> "Violeta"; default -> "Rosa"; }; }
    public boolean isRepresentative() { return this.getPersistentData().getBoolean(DATA_REPRESENTATIVE); }
    public void setRepresentative(boolean value) { this.getPersistentData().putBoolean(DATA_REPRESENTATIVE, value); }
    public boolean isPatrolling() { return this.getPersistentData().getBoolean(DATA_PATROL); }
    public void startPatrol(BlockPos center, int radius) {
        CompoundTag data = this.getPersistentData();
        data.putBoolean(DATA_PATROL, true); data.putInt(DATA_PATROL_X, center.getX());
        data.putInt(DATA_PATROL_Y, center.getY()); data.putInt(DATA_PATROL_Z, center.getZ());
        data.putInt(DATA_PATROL_RADIUS, Math.max(4, Math.min(64, radius)));
        this.setOrderedToSit(false); this.setInSittingPose(false); this.getNavigation().stop();
    }
    public void stopPatrol() { this.getPersistentData().putBoolean(DATA_PATROL, false); this.getNavigation().stop(); }
    public BlockPos getPatrolCenter() { CompoundTag d=this.getPersistentData(); return new BlockPos(d.getInt(DATA_PATROL_X),d.getInt(DATA_PATROL_Y),d.getInt(DATA_PATROL_Z)); }
    public int getPatrolRadius() { return Math.max(4, this.getPersistentData().getInt(DATA_PATROL_RADIUS)); }
    public void setMiningTarget(String target) { this.getPersistentData().putString(DATA_MINING_TARGET, target == null ? "" : target); }
    public String getMiningTarget() { return this.getPersistentData().getString(DATA_MINING_TARGET); }

    /** First tamed Mao becomes the permanent representative; subsequent Maos remain independently manageable. */
    public void initializeOwnerRole(Player owner) {
        boolean found = false; int count = 1;
        if (owner instanceof ServerPlayer sp) {
            for (ServerLevel dimension : sp.server.getAllLevels()) {
                for (Entity e : dimension.getAllEntities()) {
                    if (e instanceof MaoEntity other && other != this && other.isAlive() && other.isOwnedByUuid(owner)) {
                        count++;
                        if (other.isRepresentative()) found = true;
                    }
                }
            }
        }
        this.setRepresentative(!found);
        String assigned = this.isRepresentative() ? "Mao" : "Mao " + count;
        this.setCustomName(Component.literal(assigned));
        this.setCustomNameVisible(true);
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
        MaoEntity representative = list.stream().filter(MaoEntity::isRepresentative).findFirst().orElse(null);
        if (representative != null) return representative;
        MaoEntity near = list.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(player))).orElse(null);
        if (near != null) return near;
        MaoEntity fallback = null;
        for (ServerLevel lv : player.server.getAllLevels()) {
            for (Entity e : lv.getAllEntities()) {
                if (e instanceof MaoEntity m && m.isAlive() && m.isOwnedByUuid(player)) {
                    if (m.isRepresentative()) return m;
                    if (fallback == null) fallback = m;
                }
            }
        }
        return fallback;
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
        this.goalSelector.addGoal(3, new HugFriendGoal());
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0D, 5.0F, 2.0F, true) {
            @Override public boolean canUse() { return !MaoEntity.this.isPatrolling() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !MaoEntity.this.isPatrolling() && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(5, new PatrolGoal());
        this.goalSelector.addGoal(6, new ApproachInnocentGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

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
        this.targetSelector.addGoal(5, new DefendFriendsGoal());
    }

    // ------------------------------------------------------------------ convivencia con otros mods

    /** Mao nunca ataca a sus amigos (criaturas amigas de otros mods, mascotas de su dueño y otros Maos). */
    @Override
    public boolean canAttack(LivingEntity target) {
        if (MaoMods.isFriend(this, target)) return false;
        return super.canAttack(target);
    }

    /** Pide a Mao que vaya a abrazar a un amigo (dura como mucho 20 s). */
    public void startHug(LivingEntity friend) {
        this.hugTarget = friend;
        this.hugUntil = this.level().getGameTime() + 400L;
    }

    private void doHug(LivingEntity friend) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.HEART, friend.getX(), friend.getY() + friend.getBbHeight() + 0.2, friend.getZ(), 6, 0.3, 0.2, 0.3, 0.0);
        sl.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.0, this.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        sl.playSound(null, this.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1.0F, 1.6F);
        if (friend.getHealth() < friend.getMaxHealth()) friend.heal(4.0F);
        if (this.getOwner() instanceof ServerPlayer owner) {
            owner.displayClientMessage(Component.translatable("message.corazondemelon.mao_hug", friend.getDisplayName()), true);
            long now = sl.getGameTime();
            if (now >= nextHugXp) {                       // ser cariñoso sube la Inocencia, pero solo cada ~3 minutos
                Innocence.addXp(owner, 0.5F);
                nextHugXp = now + 3600L;
            }
        }
    }

    private class HugFriendGoal extends Goal {
        HugFriendGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return MaoEntity.this.isTame() && !MaoEntity.this.isOrderedToSit() && hugTarget != null && hugTarget.isAlive()
                    && MaoEntity.this.level().getGameTime() < hugUntil && MaoEntity.this.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            MaoEntity.this.getLookControl().setLookAt(hugTarget, 30.0F, 30.0F);
            if (MaoEntity.this.distanceToSqr(hugTarget) > 4.0D) {
                if (MaoEntity.this.tickCount % 10 == 0) MaoEntity.this.getNavigation().moveTo(hugTarget, 1.2D);
            } else {
                doHug(hugTarget);
                hugTarget = null;
            }
        }

        @Override
        public void stop() {
            MaoEntity.this.getNavigation().stop();
        }
    }

    /** Si algo ataca a un amigo de Mao, Mao va a defenderlo. */
    private class DefendFriendsGoal extends Goal {
        private LivingEntity attacker;

        DefendFriendsGoal() {
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!MaoEntity.this.isTame() || MaoEntity.this.isOrderedToSit()) return false;
            if (!MelonConfig.MOD_INTERACTIONS.get() || !MelonConfig.PROTECT_FRIENDS.get()) return false;
            if (MaoEntity.this.tickCount % 10 != 0) return false;
            for (Mob m : MaoEntity.this.level().getEntitiesOfClass(Mob.class, MaoEntity.this.getBoundingBox().inflate(16.0D),
                    e -> e.isAlive() && e != MaoEntity.this)) {
                LivingEntity victim = m.getTarget();
                if (victim == null || victim == m || m instanceof Creeper) continue;
                if (m instanceof TamableAnimal ta && ta.isTame()) continue;
                if (!MaoMods.isFriend(MaoEntity.this, victim) || MaoMods.isFriend(MaoEntity.this, m)) continue;
                if (!MaoEntity.this.canAttack(m)) continue;
                attacker = m;
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return attacker != null && attacker.isAlive() && MaoEntity.this.getTarget() == attacker && !MaoEntity.this.isOrderedToSit();
        }

        @Override
        public void start() {
            MaoEntity.this.setTarget(attacker);
        }

        @Override
        public void stop() {
            if (MaoEntity.this.getTarget() == attacker) MaoEntity.this.setTarget(null);
            attacker = null;
        }
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

    private final class PatrolGoal extends Goal {
        private int cooldown;
        PatrolGoal() { setFlags(EnumSet.of(Flag.MOVE)); }
        @Override public boolean canUse() { return MaoEntity.this.isTame() && MaoEntity.this.isPatrolling() && !MaoEntity.this.isOrderedToSit(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void tick() {
            if (--cooldown > 0 && !MaoEntity.this.getNavigation().isDone()) return;
            cooldown = 80 + MaoEntity.this.random.nextInt(100);
            BlockPos c = MaoEntity.this.getPatrolCenter(); int r = MaoEntity.this.getPatrolRadius();
            double x = c.getX() + 0.5 + (MaoEntity.this.random.nextDouble() * 2 - 1) * r;
            double z = c.getZ() + 0.5 + (MaoEntity.this.random.nextDouble() * 2 - 1) * r;
            int y = MaoEntity.this.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int)x, (int)z);
            if (MaoEntity.this.distanceToSqr(x, y, z) > 9) MaoEntity.this.getNavigation().moveTo(x, y + 1.0, z, 0.85D);
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
            if (MelonConfig.MOD_INTERACTIONS.get() && MelonConfig.MOD_HUGS.get() && hugTarget == null
                    && !this.isOrderedToSit() && now >= nextSocial) {
                nextSocial = now + 1200L + this.random.nextInt(1200);        // se pasa a saludar cada 1-2 minutos
                LivingEntity friend = MaoMods.nearestFriend(this, 8.0D);
                if (friend != null) startHug(friend);
            }
            if (this.getOwner() instanceof ServerPlayer owner) {
                if (!this.isRepresentative()) {
                    boolean hasRepresentative = false;
                    for (ServerLevel dimension : owner.server.getAllLevels()) {
                        for (Entity entity : dimension.getAllEntities()) {
                            if (entity instanceof MaoEntity other && other != this && other.isAlive() && other.isOwnedByUuid(owner) && other.isRepresentative()) { hasRepresentative = true; break; }
                        }
                        if (hasRepresentative) break;
                    }
                    if (!hasRepresentative) this.initializeOwnerRole(owner); // migration for worlds created before v1.4.0
                }
                // The representative never stays behind: long-distance and cross-dimension recovery.
                if (this.isRepresentative() && !this.isOrderedToSit() && (this.level() != owner.level() || this.distanceToSqr(owner) > 4096.0D)) {
                    this.warpTo(owner.serverLevel(), owner.getX() + 1.0, owner.getY() + 1.0, owner.getZ());
                    return;
                }
                int innocenceLevel = Innocence.getLevel(owner);
                this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(3.0D + Math.max(0, innocenceLevel - 1) * 0.75D);
                this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D + innocenceLevel * 2.0D);
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3D + innocenceLevel * 0.012D);
                this.getAttribute(Attributes.FLYING_SPEED).setBaseValue(0.6D + innocenceLevel * 0.025D);
                if (innocenceLevel >= 8 && now % 100L == 0L) owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, innocenceLevel >= 10 ? 1 : 0, true, false));
                if (innocenceLevel >= 9) owner.removeEffect(MobEffects.POISON);
                if (innocenceLevel >= 10 && now % 40L == 0L) {
                    for (MobEffectInstance effect : new ArrayList<>(owner.getActiveEffects())) {
                        if (!effect.getEffect().isBeneficial()) owner.removeEffect(effect.getEffect());
                    }
                }
                if (innocenceLevel >= 10 && now % 2400L == 0L) owner.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 1, true, false));
                if (!this.getMiningTarget().isBlank() && now % 100L == 0L) miningStep(owner, level);
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

    private void miningStep(ServerPlayer owner, ServerLevel level) {
        String wanted = this.getMiningTarget().toLowerCase(java.util.Locale.ROOT).replace(" ", "_");
        wanted = switch (wanted) {
            case "diamantes", "diamond" -> "diamante";
            case "iron" -> "hierro";
            case "gold" -> "oro";
            case "coal" -> "carbon";
            case "esmeraldas" -> "esmeralda";
            case "lapislazuli", "lapis" -> "lapislazuli";
            case "cuarzo" -> "quartz";
            case "netherita", "escombros" -> "ancient_debris";
            default -> wanted;
        };
        BlockPos origin = this.blockPosition(); BlockPos best = null; double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(); int radius = 16;
        int minY = Math.max(level.getMinBuildHeight(), origin.getY() - 12), maxY = Math.min(level.getMaxBuildHeight()-1, origin.getY()+12);
        for(int x=origin.getX()-radius;x<=origin.getX()+radius;x++) for(int z=origin.getZ()-radius;z<=origin.getZ()+radius;z++) {
            cursor.set(x,origin.getY(),z); if(!level.hasChunkAt(cursor)) continue;
            for(int y=minY;y<=maxY;y++) {
                cursor.set(x,y,z); BlockState state=level.getBlockState(cursor); if(state.isAir()||level.getBlockEntity(cursor)!=null)continue;
                String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                boolean ore = id.contains("_ore") || id.equals("ancient_debris");
                if(!ore || !(id.contains(wanted) || (wanted.equals("diamante")&&id.contains("diamond")) || (wanted.equals("hierro")&&id.contains("iron")) || (wanted.equals("oro")&&id.contains("gold")) || (wanted.equals("carbon")&&id.contains("coal")) || (wanted.equals("esmeralda")&&id.contains("emerald")) || (wanted.equals("cobre")&&id.contains("copper")) || (wanted.equals("redstone")&&id.contains("redstone")) || (wanted.equals("lapislazuli")&&id.contains("lapis")))) continue;
                double d=origin.distSqr(cursor); if(d<bestDist){bestDist=d;best=cursor.immutable();}
            }
        }
        if(best==null){
            this.getPersistentData().putInt("cdm_mining_progress",0);
            // No matching ore in the immediate area: wander through already loaded terrain and keep searching.
            if(this.getNavigation().isDone() || this.getPersistentData().getInt("cdm_roam_cooldown")<=0) {
                int x=origin.getX()+this.random.nextInt(49)-24, z=origin.getZ()+this.random.nextInt(49)-24;
                BlockPos probe=new BlockPos(x,origin.getY(),z);
                if(level.hasChunkAt(probe)) {
                    int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                    this.getNavigation().moveTo(x+0.5,y+1.0,z+0.5,0.7D);
                    this.getPersistentData().putInt("cdm_roam_cooldown",3);
                }
            } else this.getPersistentData().putInt("cdm_roam_cooldown",this.getPersistentData().getInt("cdm_roam_cooldown")-1);
            return;
        }
        if(bestDist>9.0D){this.getNavigation().moveTo(best.getX()+0.5,best.getY()+0.5,best.getZ()+0.5,0.75D);return;}
        BlockState state=level.getBlockState(best);
        if(!level.mayInteract(owner,best) || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,best,state,owner))) { this.getPersistentData().putInt("cdm_mining_progress",0);return; }
        int progress=this.getPersistentData().getInt("cdm_mining_progress")+1;
        this.getPersistentData().putInt("cdm_mining_progress",progress);
        level.sendParticles(ParticleTypes.CRIT,best.getX()+0.5,best.getY()+0.5,best.getZ()+0.5,2,0.2,0.2,0.2,0.01);
        if(progress>=8) {
            List<ItemStack> drops=Block.getDrops(state,level,best,level.getBlockEntity(best),this,ItemStack.EMPTY);
            level.destroyBlock(best,false,this);
            for(ItemStack stack:drops) MaoStorage.add(this,stack);
            this.getPersistentData().putInt("cdm_mining_progress",0);
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
        // Con criaturas amigas cerca (de otros mods), a veces regala flores (de Minecraft o de cualquier mod).
        if (MelonConfig.MOD_INTERACTIONS.get() && this.random.nextInt(100) < 35 && MaoMods.nearestFriend(this, 16.0D) != null) {
            Item flower = MaoMods.randomFlower(this.random);
            if (flower != null) stack = new ItemStack(flower, 1 + this.random.nextInt(3));
        }
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("MaoColor", this.getMaoColor());
        tag.putBoolean("MaoRepresentative", this.isRepresentative());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(MAO_COLOR, tag.getInt("MaoColor"));
        this.setRepresentative(tag.getBoolean("MaoRepresentative"));
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
                this.initializeOwnerRole(player);
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
                boolean sit = this.isRepresentative() ? false : !this.isOrderedToSit();
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
