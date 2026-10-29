package com.pineapple.abstickmen.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.pineapple.abstickmen.AlanBeckerStickmen;
import com.pineapple.abstickmen.reseau.MessageEcran;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Un bonhomme-bâton d'Alan Becker. Il suit le joueur qui l'a dessiné, se bat contre les monstres qui
 * l'approchent, mine les minerais visibles autour de lui et lui rapporte ce qu'il trouve, et fait des
 * acrobaties quand tout est calme. Jaune a en plus son bâton de commande, et l'orange écrit sur l'écran.
 *
 * Le serveur décide de tout ; le client ne reçoit que le geste en cours (voir {@link Geste}), sa
 * durée, la commande que Jaune tape, l'avancée du coup de crayon qui le dessine, et s'il est assis.
 */
public class Stickman extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_GESTE = SynchedEntityData.defineId(Stickman.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DUREE = SynchedEntityData.defineId(Stickman.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_COMMANDE = SynchedEntityData.defineId(Stickman.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_DESSIN = SynchedEntityData.defineId(Stickman.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ASSIS = SynchedEntityData.defineId(Stickman.class, EntityDataSerializers.BOOLEAN);

    /** Durée du coup de crayon qui le dessine, et de la gomme qui l'efface (en ticks). */
    public static final int DUREE_DESSIN = 30;
    public static final int DUREE_GOMME = 16;
    /** Commande tapée : temps pour lever le bâton, lettres par tick, temps pendant lequel elle reste affichée. */
    public static final int LEVER = 8, LEVER_URGENT = 4;
    public static final float FRAPPE = 1.25f, FRAPPE_URGENTE = 4f;
    private static final int TENUE = 24, TENUE_URGENTE = 14;
    /** Une commande urgente commence par ce caractère dans la donnée synchronisée (il n'est pas affiché). */
    public static final String MARQUE_URGENTE = "!";
    /** Lettres écrites sur l'écran par seconde. */
    public static final float LETTRES_PAR_SECONDE = 16f;
    /** Tir à l'arc : la flèche part à ce tick du geste. */
    public static final int LACHER = 22;
    /**
     * Portées de l'arc, en blocs : il le sort pour une cible plus loin que TIR_MIN, repasse au corps à
     * corps en deçà de TIR_PROCHE, et laisse filer un creeper arrivé à moins de CREEPER_TROP_PRES.
     */
    private static final double TIR_MIN = 9, TIR_MAX = 28, TIR_PROCHE = 5.5, CREEPER_TROP_PRES = 4;
    private static final float VITESSE_FLECHE = 2.4f;

    private static final Map<EntityType<?>, Personnage> PERSONNAGES = new HashMap<>();
    /** Minerais déjà visés : deux stickmen ne piochent jamais le même bloc. */
    private static final Set<GlobalPos> RESERVES = ConcurrentHashMap.newKeySet();
    /** Créée à la première utilisation : les objets ne peuvent pas être fabriqués pendant le chargement du mod. */
    private static @Nullable ItemStack pioche;

    /** Pour les tests en jeu : plus aucune décision, ils restent où on les pose et font ce qu'on leur dit. */
    public static volatile boolean galerie;

    public final Personnage personnage;
    private @Nullable UUID proprietaire;
    private Geste geste = Geste.AUCUN;
    private int finGeste;
    private int numeroGeste;
    private int debutGesteClient;
    private final SimpleContainer sac = new SimpleContainer(9);
    private boolean mine;
    /** Il a sorti son arc (une cible au loin). */
    private boolean arc;
    private int prochainAmusement = 160;
    private int prochainSalut;
    private float angleSuivi = Float.NaN;
    private int loinDepuis;
    private int ailleursDepuis;
    // Jaune seulement : son bâton de commande
    private final Map<String, Long> dernieresCommandes = new HashMap<>();
    private long derniereCommande = -100000;
    private @Nullable String commandeEnAttente;
    private int momentCommande;
    // L'orange seulement : ses messages
    private long prochaineParole = -1;
    private long derniereVictoire;
    private int accueilA = -1;
    private boolean nuitAnnoncee = true;
    private boolean dangerAnnonce;

    public Stickman(EntityType<? extends Stickman> type, Level level) {
        super(type, level);
        personnage = PERSONNAGES.getOrDefault(type, Personnage.ROUGE);
        setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0f);
        }
        if (getNavigation() instanceof GroundPathNavigation sol) {
            sol.setCanOpenDoors(true);
        }
    }

    public static void enregistrer(EntityType<? extends Stickman> type, Personnage personnage) {
        PERSONNAGES.put(type, personnage);
    }

    public static AttributeSupplier.Builder attributs() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GESTE, 0);
        builder.define(DATA_DUREE, 0);
        builder.define(DATA_COMMANDE, "");
        builder.define(DATA_DESSIN, 0);
        builder.define(DATA_ASSIS, false);
    }

    // Les objectifs sont créés avant que `personnage` soit connu : ils ne le lisent qu'en jeu.
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AssisGoal());
        goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        goalSelector.addGoal(2, new BondirGoal());
        goalSelector.addGoal(2, new TirGoal());
        goalSelector.addGoal(3, new CombatGoal());
        goalSelector.addGoal(4, new MinerGoal());
        goalSelector.addGoal(5, new RapporterGoal());
        goalSelector.addGoal(6, new SuivreGoal());
        goalSelector.addGoal(7, new AmuserGoal());
        // Pendant les tests en jeu, ils gardent la tête là où on la leur met
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8f) {
            @Override
            public boolean canUse() {
                return !galerie && super.canUse();
            }
        });
        goalSelector.addGoal(9, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return !galerie && super.canUse();
            }
        });
        targetSelector.addGoal(1, new DefendreGoal());
        targetSelector.addGoal(2, new AiderGoal());
        targetSelector.addGoal(3, new ViserGoal());
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, true,
                (cible, niveau) -> aCombattre(cible)));
    }

    // ------------------------------------------------------------------ lu par le client

    public Geste gesteVisible() {
        return Geste.parNumero(entityData.get(DATA_GESTE) & 0xFF);
    }

    public int dureeGeste() {
        return entityData.get(DATA_DUREE);
    }

    /** Ticks écoulés depuis le début du geste visible (côté client). */
    public float tempsGeste(float partialTick) {
        return tickCount - debutGesteClient + partialTick;
    }

    public String commandeVisible() {
        return entityData.get(DATA_COMMANDE);
    }

    /** &gt; 0 : en train d'être dessiné (ticks restants) ; &lt; 0 : en train d'être effacé. */
    public int dessin() {
        return entityData.get(DATA_DESSIN);
    }

    public boolean estAssis() {
        return entityData.get(DATA_ASSIS);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> cle) {
        super.onSyncedDataUpdated(cle);
        if (DATA_GESTE.equals(cle)) {
            debutGesteClient = tickCount;
        }
    }

    // ------------------------------------------------------------------ gestes

    public Geste gesteServeur() {
        return geste;
    }

    public void jouer(Geste g) {
        jouer(g, g.duree);
    }

    public void jouer(Geste g, int duree) {
        geste = g;
        finGeste = g == Geste.ASSIS ? Integer.MAX_VALUE : tickCount + Math.max(1, duree);
        numeroGeste = (numeroGeste + 1) & 0x7FFFFF;
        entityData.set(DATA_DUREE, duree);
        entityData.set(DATA_GESTE, (numeroGeste << 8) | g.ordinal());
        if (g.famille == Geste.Famille.AMUSEMENT && proprietaire != null) {
            Souvenirs s = Souvenirs.de(proprietaire);
            s.dernierGeste = g;
            s.auteurDernierGeste = personnage;
        }
    }

    private void finirGeste() {
        if (geste == Geste.COMMANDE) {
            entityData.set(DATA_COMMANDE, "");
        }
        jouer(estAssis() ? Geste.ASSIS : Geste.AUCUN, 0);
    }

    public void asseoir(boolean assis) {
        entityData.set(DATA_ASSIS, assis);
        if (assis) {
            getNavigation().stop();
            setTarget(null);
            jouer(Geste.ASSIS, 0);
        } else if (geste == Geste.ASSIS) {
            jouer(Geste.AUCUN, 0);
        }
    }

    /** Coup de crayon : il apparaît trait après trait. */
    public void dessiner() {
        entityData.set(DATA_DESSIN, DUREE_DESSIN);
        if (personnage.estOrange()) {
            accueilA = tickCount + DUREE_DESSIN + 12;
        }
    }

    /** Coup de gomme : il disparaît trait après trait, puis s'en va pour de bon. */
    public void effacer() {
        entityData.set(DATA_DESSIN, -DUREE_GOMME);
        getNavigation().stop();
        if (level() instanceof ServerLevel niveau) {
            rendreLeSac(niveau);
        }
    }

    /** Ni assis, ni en train d'être dessiné ou effacé : il peut décider de ce qu'il fait. */
    private boolean libre() {
        return !galerie && !estAssis() && dessin() == 0;
    }

    // ------------------------------------------------------------------ propriétaire

    public @Nullable UUID proprietaire() {
        return proprietaire;
    }

    public void adopterPar(Player joueur) {
        proprietaire = joueur.getUUID();
    }

    public @Nullable ServerPlayer proprio() {
        if (proprietaire == null || !(level() instanceof ServerLevel niveau)) {
            return null;
        }
        ServerPlayer p = niveau.getServer().getPlayerList().getPlayer(proprietaire);
        return p != null && p.isAlive() && !p.isSpectator() ? p : null;
    }

    private boolean memeBande(Entity e) {
        return e instanceof Stickman s && Objects.equals(s.proprietaire, proprietaire);
    }

    // ------------------------------------------------------------------ chaque tick

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel niveau)) {
            return;
        }
        int dessin = dessin();
        if (dessin > 0) {
            entityData.set(DATA_DESSIN, dessin - 1);
        } else if (dessin < 0) {
            if (dessin == -1) {
                niveau.sendParticles(new DustParticleOptions(personnage.couleur, 1.2f), getX(), getY() + 0.9, getZ(), 12, 0.25, 0.5, 0.25, 0);
                discard();
                return;
            }
            entityData.set(DATA_DESSIN, dessin + 1);
        }
        if (geste != Geste.AUCUN && geste != Geste.ASSIS && tickCount >= finGeste) {
            finirGeste();
        }
        // La commande tapée part quand la dernière lettre est tapée, quoi qu'il fasse d'autre
        if (commandeEnAttente != null && tickCount >= momentCommande) {
            ServerPlayer lecteur = proprio();
            if (lecteur != null && isAlive()) {
                executer(niveau, lecteur, commandeEnAttente);
            }
            commandeEnAttente = null;
        }
        if (galerie || !isAlive()) {
            return;
        }
        if (proprietaire == null) {
            if (tickCount % 20 == 0) {
                Player proche = niveau.getNearestPlayer(this, 16);
                if (proche != null && !proche.isSpectator()) {
                    adopterPar(proche);
                }
            }
            return;
        }
        // Il se remet de ses blessures, doucement, loin des combats
        if (tickCount % 40 == 0 && getHealth() < getMaxHealth() && tickCount - getLastHurtByMobTimestamp() > 200) {
            heal(1);
        }
        equiper();
        ServerPlayer p = proprio();
        if (p == null) {
            return;
        }
        if (p.level() != niveau) {
            suivreDansUneAutreDimension(p);
            return;
        }
        ailleursDepuis = 0;
        if (tickCount % 5 == 0) {
            saluerSiRegarde(p);
        }
        if (personnage.aLeBaton()) {
            tickBaton(niveau, p);
        }
        if (personnage.estOrange()) {
            tickOrange(niveau, p);
        }
    }

    /** Le joueur le regarde droit dans les yeux (enfin, dans le cercle) : il lui fait coucou. */
    private void saluerSiRegarde(ServerPlayer p) {
        if (!libre() || geste != Geste.AUCUN || getTarget() != null || tickCount < prochainSalut || distanceToSqr(p) > 12 * 12) {
            return;
        }
        Vec3 vers = position().add(0, 1.4, 0).subtract(p.getEyePosition()).normalize();
        if (p.getViewVector(1f).dot(vers) > 0.985 && p.hasLineOfSight(this)) {
            getLookControl().setLookAt(p);
            jouer(Geste.SALUT);
            prochainSalut = tickCount + 600;
        }
    }

    /** Ce qu'il tient en main : son arc ou son arme pendant un combat, la pioche pendant qu'il mine, rien sinon. */
    private void equiper() {
        Item voulu = Items.AIR;
        if (mine) {
            voulu = Items.IRON_PICKAXE;
        } else if (arc) {
            voulu = Items.BOW;
        } else if (getTarget() != null) {
            if (personnage.style == Personnage.Style.EPEE) {
                voulu = Items.IRON_SWORD;
            } else if (personnage.style == Personnage.Style.HACHE) {
                voulu = Items.IRON_AXE;
            }
        }
        if (!getMainHandItem().is(voulu)) {
            setItemSlot(EquipmentSlot.MAINHAND, voulu == Items.AIR ? ItemStack.EMPTY : new ItemStack(voulu));
        }
    }

    // ------------------------------------------------------------------ suivre

    /** Sa place dans la bande, derrière le joueur. L'angle ne suit le joueur que quand il avance. */
    private Vec3 place(ServerPlayer p) {
        Vec3 v = p.getDeltaMovement();
        if (Float.isNaN(angleSuivi) || v.horizontalDistanceSqr() > 0.004) {
            angleSuivi = p.yBodyRot;
        }
        double a = Math.toRadians(angleSuivi + 180 + personnage.angleFormation());
        double d = personnage.distanceFormation();
        Vec3 place = new Vec3(p.getX() - Math.sin(a) * d, p.getY(), p.getZ() + Math.cos(a) * d);
        BlockPos pos = BlockPos.containing(place);
        Level niveau = level();
        boolean libre = niveau.getBlockState(pos).getCollisionShape(niveau, pos).isEmpty()
                && niveau.getBlockState(pos.above()).getCollisionShape(niveau, pos.above()).isEmpty()
                && !niveau.getBlockState(pos.below()).getCollisionShape(niveau, pos.below()).isEmpty();
        return libre ? place : p.position();
    }

    /** Trop loin : une perle de l'End (ou, pour Jaune, la commande /tp s'il en a le droit). */
    public void rejoindre(ServerPlayer p) {
        if (!(level() instanceof ServerLevel niveau) || p.level() != niveau) {
            return;
        }
        if (personnage.aLeBaton() && Commandes.permises(p) && commandeEnAttente == null && geste != Geste.COMMANDE) {
            taperCommande("/tp @s " + p.getName().getString(), true);
            return;
        }
        BlockPos place = placeSure(niveau, p.blockPosition());
        if (place == null) {
            return;
        }
        niveau.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 24, 0.3, 0.6, 0.3, 0.3);
        niveau.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_PEARL_THROW, getSoundSource(), 0.6f, 1f);
        getNavigation().stop();
        snapTo(place.getX() + 0.5, place.getY(), place.getZ() + 0.5, getYRot(), getXRot());
        resetFallDistance();
        niveau.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 24, 0.3, 0.6, 0.3, 0.3);
        niveau.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_TELEPORT, getSoundSource(), 0.6f, 1.2f);
        jouer(Geste.PERLE);
    }

    private void suivreDansUneAutreDimension(ServerPlayer p) {
        if (estAssis() || dessin() != 0 || ++ailleursDepuis < 60 || !(p.level() instanceof ServerLevel autre)) {
            return;
        }
        ailleursDepuis = 0;
        BlockPos place = placeSure(autre, p.blockPosition());
        if (place != null) {
            teleport(new TeleportTransition(autre, Vec3.atBottomCenterOf(place), Vec3.ZERO, p.getYRot(), 0,
                    TeleportTransition.PLAY_PORTAL_SOUND));
        }
    }

    /** Une case où il tient debout, à côté de {@code centre} (sol solide, deux blocs d'air, pas de lave). */
    public static @Nullable BlockPos placeSure(ServerLevel niveau, BlockPos centre) {
        for (int essai = 0; essai < 24; essai++) {
            int dx = niveau.getRandom().nextIntBetweenInclusive(-3, 3);
            int dz = niveau.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos pos = centre.offset(dx, dy, dz);
                if (niveau.getBlockState(pos).getCollisionShape(niveau, pos).isEmpty()
                        && niveau.getBlockState(pos.above()).getCollisionShape(niveau, pos.above()).isEmpty()
                        && niveau.getBlockState(pos.below()).isFaceSturdy(niveau, pos.below(), Direction.UP)
                        && niveau.getFluidState(pos).isEmpty() && niveau.getFluidState(pos.below()).isEmpty()) {
                    return pos;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ combat

    private boolean aCombattre(LivingEntity cible) {
        ServerPlayer p = proprio();
        if (p == null || !libre() || !(cible instanceof Enemy) || cible instanceof Creeper || cible.distanceToSqr(p) > 18 * 18) {
            return false;
        }
        if (cible instanceof NeutralMob || cible instanceof AbstractPiglin) {
            // Ceux-là ne se battent que si on les cherche : on ne s'en occupe que s'ils attaquent
            LivingEntity visee = cible instanceof Mob m ? m.getTarget() : null;
            return visee == p || visee instanceof Stickman;
        }
        return true;
    }

    private Geste coupAuHasard() {
        float r = random.nextFloat();
        return switch (personnage.style) {
            case BATON -> r < 0.6f ? Geste.COUP_DE_BATON : r < 0.8f ? Geste.COUP_DE_PIED : Geste.COUP_DE_PIED_RETOURNE;
            case POINGS -> r < 0.4f ? Geste.DIRECT : r < 0.7f ? Geste.CROCHET : r < 0.9f ? Geste.UPPERCUT : Geste.COUP_DE_PIED;
            case PIEDS -> r < 0.45f ? Geste.COUP_DE_PIED : r < 0.8f ? Geste.COUP_DE_PIED_RETOURNE : Geste.DIRECT;
            case EPEE -> r < 0.8f ? Geste.COUP_D_EPEE : Geste.COUP_DE_PIED;
            case HACHE -> r < 0.7f ? Geste.COUP_D_EPEE : r < 0.85f ? Geste.DIRECT : Geste.CROCHET;
        };
    }

    /** Un monstre qui vient vers nous : il vise le joueur ou quelqu'un de la bande, ou marche droit sur le joueur. */
    private boolean approche(Mob m, ServerPlayer p) {
        LivingEntity visee = m.getTarget();
        if (visee == p || visee != null && memeBande(visee)) {
            return true;
        }
        if (m instanceof NeutralMob || m instanceof AbstractPiglin) {
            return false;   // ceux-là ne sont des ennemis que s'ils attaquent
        }
        Vec3 vers = p.position().subtract(m.position());
        Vec3 v = m.getDeltaMovement();
        double aPlat = Math.sqrt(vers.x * vers.x + vers.z * vers.z);
        return aPlat > 0 && (v.x * vers.x + v.z * vers.z) / aPlat > 0.04;
    }

    /**
     * La flèche part de l'arc, devant lui à hauteur du cou. La visée tient compte de la gravité et du
     * frottement de l'air, et prend un peu d'avance sur une cible qui marche.
     */
    private void tirer(ServerLevel niveau, LivingEntity cible) {
        stopUsingItem();
        Vec3 devant = Vec3.directionFromRotation(0, getYRot());
        Vec3 depart = position().add(devant.x * 0.55, 1.3, devant.z * 0.55);
        Vec3 visee = cible.getBoundingBox().getCenter();
        double vol = depart.distanceTo(visee) / VITESSE_FLECHE * 1.1;
        // L'avance se fait sur sa marche, pas sur un recul (une flèche reçue le repousse un instant)
        Vec3 v = cible.getDeltaMovement().multiply(1, 0, 1);
        if (v.lengthSqr() > 0.25 * 0.25) {
            v = v.normalize().scale(0.25);
        }
        visee = visee.add(v.x * vol, 0, v.z * vol);
        double dx = visee.x - depart.x, dz = visee.z - depart.z;
        double aPlat = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
        double angle = angleDeTir(aPlat, visee.y - depart.y);
        double c = Math.cos(angle);
        Fleche fleche = new Fleche(AlanBeckerStickmen.FLECHE, niveau);
        fleche.setOwner(this);
        fleche.setPos(depart);
        fleche.setBaseDamage(1.6);
        fleche.setCritArrow(true);
        Projectile.spawnProjectileUsingShoot(fleche, niveau, new ItemStack(Items.ARROW), dx / aPlat * c, Math.sin(angle), dz / aPlat * c,
                VITESSE_FLECHE, 1f);
        playSound(SoundEvents.ARROW_SHOOT, 1f, 1f / (random.nextFloat() * 0.4f + 1.2f) + 0.5f);
    }

    /** L'angle (vers le haut) qui amène la flèche {@code dy} blocs plus haut au bout de {@code aPlat} blocs. */
    private static double angleDeTir(double aPlat, double dy) {
        double bas = -Math.PI / 4, haut = Math.PI / 4;
        for (int i = 0; i < 24; i++) {
            double milieu = (bas + haut) / 2;
            if (hauteurAuBout(aPlat, milieu) < dy) {
                bas = milieu;
            } else {
                haut = milieu;
            }
        }
        return (bas + haut) / 2;
    }

    /** Hauteur de la flèche au bout de {@code aPlat} blocs, comme le jeu la fait voler (frottement 0,99 et gravité 0,05 par tick). */
    private static double hauteurAuBout(double aPlat, double angle) {
        double vx = VITESSE_FLECHE * Math.cos(angle), vy = VITESSE_FLECHE * Math.sin(angle), x = 0, y = 0;
        for (int t = 0; t < 200 && x < aPlat; t++) {
            double part = Math.min(1, (aPlat - x) / vx);
            x += vx * part;
            y += vy * part;
            vx *= 0.99;
            vy = vy * 0.99 - 0.05;
        }
        return y;
    }

    @Override
    public boolean doHurtTarget(ServerLevel niveau, Entity cible) {
        if (geste != Geste.COUP_DE_PIED_SAUTE || tickCount >= finGeste - 4) {
            jouer(coupAuHasard());
        }
        boolean touche = super.doHurtTarget(niveau, cible);
        if (touche) {
            playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 1.1f + random.nextFloat() * 0.3f);
        }
        return touche;
    }

    @Override
    public boolean killedEntity(ServerLevel niveau, LivingEntity victime, DamageSource source) {
        if (proprietaire != null) {
            Souvenirs.de(proprietaire).victime(niveau.getGameTime(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(victime.getType()).getPath(), personnage);
        }
        return super.killedEntity(niveau, victime, source);
    }

    @Override
    public boolean hurtServer(ServerLevel niveau, DamageSource source, float degats) {
        Entity auteur = source.getEntity();
        if (auteur != null && (auteur.getUUID().equals(proprietaire) || memeBande(auteur))) {
            return false;
        }
        if (source.is(DamageTypes.LIGHTNING_BOLT) || source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.hurtServer(niveau, source, degats);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity e) {
        return memeBande(e) || (proprietaire != null && e.getUUID().equals(proprietaire)) || super.considersEntityAsAlly(e);
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplicateur, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.5f + random.nextFloat() * 0.2f;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel niveau) {
            ServerPlayer p = proprio();
            if (p != null) {
                p.sendSystemMessage(Component.translatable("message.abstickmen.efface", getDisplayName()).withStyle(ChatFormatting.GRAY));
            }
            rendreLeSac(niveau);
            niveau.sendParticles(new DustParticleOptions(personnage.couleur, 1.5f), getX(), getY() + 0.9, getZ(), 30, 0.3, 0.6, 0.3, 0);
        }
        super.die(source);
    }

    // ------------------------------------------------------------------ clic droit

    @Override
    protected InteractionResult mobInteract(Player joueur, InteractionHand main) {
        if (joueur.getItemInHand(main).is(AlanBeckerStickmen.CRAYON) || main != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(level() instanceof ServerLevel) || !(joueur instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        getLookControl().setLookAt(joueur);
        if (proprietaire == null) {
            adopterPar(joueur);
            sp.sendOverlayMessage(Component.translatable("message.abstickmen.ami", getDisplayName()));
            jouer(Geste.SALUT);
            return InteractionResult.SUCCESS;
        }
        if (joueur.isSecondaryUseActive()) {
            if (!joueur.getUUID().equals(proprietaire)) {
                return InteractionResult.PASS;
            }
            boolean assis = !estAssis();
            asseoir(assis);
            sp.sendOverlayMessage(Component.translatable(assis ? "message.abstickmen.reste" : "message.abstickmen.suit", getDisplayName()));
            return InteractionResult.SUCCESS;
        }
        if (personnage.estOrange() && Reglages.get().ecrire) {
            ecrire(sp, "clic");
        } else if (!estAssis() && geste.famille != Geste.Famille.COMBAT) {
            jouer(amusementAuHasard());
        } else {
            jouer(Geste.SALUT);
        }
        return InteractionResult.SUCCESS;
    }

    private Geste amusementAuHasard() {
        Geste[] choix = switch (personnage) {
            case ORANGE -> new Geste[]{Geste.SALTO_ARRIERE, Geste.SALTO_AVANT, Geste.ROUE, Geste.POIRIER, Geste.KATA,
                    Geste.TOUPIE, Geste.MAINS_HANCHES, Geste.ETIREMENT, Geste.DANSE_DISCO, Geste.BRAS_CROISES};
            case ROUGE -> new Geste[]{Geste.KATA, Geste.POMPES, Geste.SALTO_ARRIERE, Geste.BRAS_CROISES, Geste.MAINS_HANCHES,
                    Geste.ETIREMENT, Geste.DANSE_ROBOT, Geste.VICTOIRE};
            case BLEU -> new Geste[]{Geste.KATA, Geste.ROUE, Geste.SALTO_AVANT, Geste.TOUPIE, Geste.POIRIER,
                    Geste.SAUTS_ECART, Geste.ETIREMENT, Geste.SALTO_ARRIERE};
            case VERT -> new Geste[]{Geste.POMPES, Geste.ETIREMENT, Geste.GRATTE_TETE, Geste.SAUTS_ECART, Geste.BRAS_CROISES,
                    Geste.POIRIER, Geste.DANSE_DISCO, Geste.MAINS_HANCHES};
            case JAUNE -> new Geste[]{Geste.DANSE_DISCO, Geste.DANSE_ROBOT, Geste.TOUPIE, Geste.ROUE, Geste.SAUTS_ECART,
                    Geste.GRATTE_TETE, Geste.SALTO_AVANT, Geste.DANSE_DISCO};
        };
        return choix[random.nextInt(choix.length)];
    }

    // ------------------------------------------------------------------ Jaune et son bâton de commande

    private void tickBaton(ServerLevel niveau, ServerPlayer p) {
        if (!libre() || commandeEnAttente != null || geste == Geste.COMMANDE || tickCount % 10 != 0 || !Commandes.permises(p)) {
            return;
        }
        long t = niveau.getGameTime();
        Commandes.Choix choix = Commandes.choisir(this, p, niveau, dernieresCommandes, derniereCommande, t);
        if (choix != null) {
            dernieresCommandes.put(choix.cle(), t);
            derniereCommande = t;
            taperCommande(choix.texte(), choix.urgent());
        }
    }

    /** Jaune lève son bâton et tape la commande ; elle part quand la dernière lettre est tapée. */
    public void taperCommande(String texte, boolean urgent) {
        int lever = urgent ? LEVER_URGENT : LEVER;
        int frappe = Mth.ceil(texte.length() / (urgent ? FRAPPE_URGENTE : FRAPPE));
        entityData.set(DATA_COMMANDE, (urgent ? MARQUE_URGENTE : "") + texte);
        jouer(Geste.COMMANDE, lever + frappe + (urgent ? TENUE_URGENTE : TENUE));
        commandeEnAttente = texte;
        momentCommande = tickCount + lever + frappe;
        playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.3f);
    }

    private void executer(ServerLevel niveau, ServerPlayer p, String texte) {
        if (!Commandes.permises(p) || !isAlive()) {
            return;     // les commandes ont été coupées entre-temps
        }
        CommandSourceStack source = createCommandSourceStackForNameResolution(niveau)
                .withPermission(LevelBasedPermissionSet.GAMEMASTER)
                .withSuppressedOutput();
        niveau.getServer().getCommands().performPrefixedCommand(source, texte);
        niveau.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.2, getZ(), 24, 0.25, 0.25, 0.25, 0.4);
        niveau.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + 2.2, getZ(), 30, 0.4, 0.4, 0.4, 0.8);
        niveau.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, getSoundSource(), 1f, 1.4f);
        p.sendSystemMessage(Component.translatable("message.abstickmen.commande", getDisplayName(), texte)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    // ------------------------------------------------------------------ l'orange

    private void tickOrange(ServerLevel niveau, ServerPlayer p) {
        long t = niveau.getGameTime();
        if (prochaineParole < 0) {
            prochaineParole = t + 2400 + random.nextInt(3600);
        }
        if (!libre() || geste == Geste.ECRIRE || !Reglages.get().ecrire || tickCount % 20 != 0 || getTarget() != null) {
            return;
        }
        // Des messages de circonstance
        if (accueilA > 0 && tickCount >= accueilA) {
            accueilA = -1;
            ecrire(p, "arrivee");
        } else if (p.getHealth() <= 6 && !p.isCreative() && !dangerAnnonce) {
            dangerAnnonce = true;
            ecrire(p, "danger");
        } else if (niveau.dimension() == Level.OVERWORLD && niveau.isDarkOutside() && niveau.canSeeSky(p.blockPosition()) && !nuitAnnoncee) {
            nuitAnnoncee = true;
            ecrire(p, "nuit");
        } else if (Souvenirs.de(p.getUUID()).victimesDepuis(t - 800).values().stream().mapToInt(Integer::intValue).sum() >= 3
                && t - derniereVictoire > 1200 && calmeAutourDe(niveau, p)) {
            derniereVictoire = t;
            ecrire(p, "victoire");
        } else if (Reglages.get().messagesSpontanes && t >= prochaineParole && calmeAutourDe(niveau, p) && distanceToSqr(p) < 14 * 14) {
            ecrire(p, "spontane");
        }
        if (p.getHealth() > 12) {
            dangerAnnonce = false;
        }
        if (!niveau.isDarkOutside()) {
            nuitAnnoncee = false;
        }
    }

    private boolean calmeAutourDe(ServerLevel niveau, ServerPlayer p) {
        return niveau.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(14), m -> m instanceof Enemy && m.isAlive()).isEmpty();
    }

    /** L'orange écrit une phrase (toujours nouvelle) sur l'écran du joueur, avec son doigt. */
    public boolean ecrire(ServerPlayer lecteur, String evenement) {
        if (!personnage.estOrange() || !(level() instanceof ServerLevel niveau)) {
            return false;
        }
        String texte = Paroles.composer(contexte(niveau, lecteur, evenement), random);
        jouer(Geste.ECRIRE, Mth.ceil(texte.length() / LETTRES_PAR_SECONDE * 20f) + 16);
        getLookControl().setLookAt(lecteur);
        if (ServerPlayNetworking.canSend(lecteur, MessageEcran.TYPE)) {
            ServerPlayNetworking.send(lecteur, new MessageEcran(getId(), texte, personnage.couleur));
        } else {
            lecteur.sendSystemMessage(Component.literal("✎ " + texte).withColor(personnage.couleur));
        }
        prochaineParole = niveau.getGameTime() + 2400 + random.nextInt(4800);
        return true;
    }

    private Paroles.Contexte contexte(ServerLevel niveau, ServerPlayer p, String evenement) {
        long t = niveau.getGameTime();
        Souvenirs s = Souvenirs.de(p.getUUID());
        long depuis = t - (evenement.equals("victoire") ? 900 : 3600);
        Map<String, Integer> victimes = s.victimesDepuis(depuis);
        Map<String, Integer> minerais = s.mineraisDepuis(t - 3600);
        String mob = victimes.isEmpty() ? "" : victimes.keySet().iterator().next();
        String minerai = minerais.isEmpty() ? "" : minerais.keySet().iterator().next();
        BlockPos pos = p.blockPosition();
        String biome = niveau.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
        int amis = bande(niveau, proprietaire).size() - 1;
        boolean fr = p.clientInformation().language().toLowerCase(Locale.ROOT).startsWith("fr");
        return new Paroles.Contexte(fr, p.getName().getString(), evenement, niveau.getOverworldClockTime() % 24000,
                niveau.isRaining(), niveau.isThundering(), niveau.dimension().identifier().getPath(),
                !niveau.canSeeSky(pos) && pos.getY() < 50, pos.getY(), p.getHealth() / 2f, p.getFoodData().getFoodLevel(), biome,
                victimes, minerais, s.champion(false, mob, depuis), s.champion(true, minerai, t - 3600),
                s.dernierGeste, s.auteurDernierGeste, Math.max(0, amis), Commandes.permises(p));
    }

    /** Tous les stickmen de ce joueur dans ce monde. */
    public static List<Stickman> bande(ServerLevel niveau, @Nullable UUID joueur) {
        List<Stickman> bande = new ArrayList<>();
        if (joueur != null) {
            niveau.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Stickman.class),
                    s -> joueur.equals(s.proprietaire) && s.isAlive() && s.dessin() >= 0, bande);
        }
        return bande;
    }

    // ------------------------------------------------------------------ apparition et sauvegarde

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor niveau, DifficultyInstance difficulte, EntitySpawnReason raison,
                                                  @Nullable SpawnGroupData donnees) {
        dessiner();
        return super.finalizeSpawn(niveau, difficulte, raison, donnees);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput sortie) {
        super.addAdditionalSaveData(sortie);
        if (proprietaire != null) {
            sortie.putString("Proprietaire", proprietaire.toString());
        }
        sortie.putBoolean("Assis", estAssis());
        List<ItemStack> contenu = new ArrayList<>();
        for (int i = 0; i < sac.getContainerSize(); i++) {
            if (!sac.getItem(i).isEmpty()) {
                contenu.add(sac.getItem(i));
            }
        }
        if (!contenu.isEmpty()) {
            sortie.store("Sac", ItemStack.CODEC.listOf(), contenu);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput entree) {
        super.readAdditionalSaveData(entree);
        entree.getString("Proprietaire").ifPresent(texte -> {
            try {
                proprietaire = UUID.fromString(texte);
            } catch (IllegalArgumentException ignore) {
                proprietaire = null;
            }
        });
        asseoir(entree.getBooleanOr("Assis", false));
        sac.clearContent();
        entree.read("Sac", ItemStack.CODEC.listOf()).ifPresent(liste -> liste.forEach(sac::addItem));
    }

    @Override
    public boolean removeWhenFarAway(double distanceCarree) {
        return false;
    }

    /** Les membres de la bande se croisent sans se bousculer. */
    @Override
    protected void doPush(Entity entite) {
        if (!memeBande(entite)) {
            super.doPush(entite);
        }
    }

    private void rendreLeSac(ServerLevel niveau) {
        for (int i = 0; i < sac.getContainerSize(); i++) {
            ItemStack objet = sac.removeItemNoUpdate(i);
            if (!objet.isEmpty()) {
                spawnAtLocation(niveau, objet);
            }
        }
    }

    // ------------------------------------------------------------------ objectifs

    /** Assis : il ne bouge plus (mais il fait coucou si on le regarde). */
    private final class AssisGoal extends Goal {
        AssisGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return estAssis() || dessin() != 0;
        }

        @Override
        public void start() {
            getNavigation().stop();
        }
    }

    /** Le coup de pied sauté : il bondit sur un monstre encore à quelques pas. */
    private final class BondirGoal extends LeapAtTargetGoal {
        BondirGoal() {
            super(Stickman.this, 0.42f);
        }

        @Override
        public boolean canUse() {
            return libre() && personnage.style != Personnage.Style.EPEE && random.nextInt(3) == 0 && super.canUse();
        }

        @Override
        public void start() {
            super.start();
            jouer(Geste.COUP_DE_PIED_SAUTE);
        }
    }

    private final class CombatGoal extends MeleeAttackGoal {
        CombatGoal() {
            super(Stickman.this, 1.3, true);
        }

        @Override
        public boolean canUse() {
            return libre() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return libre() && super.canContinueToUse();
        }
    }

    /** De loin, à l'arc : il s'arrête, bande son arc, vise et tire. Trop près, il laisse la place au corps à corps. */
    private final class TirGoal extends Goal {
        private int lacher = -1;
        private int prochainTir;

        TirGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean aPortee(double min) {
            LivingEntity c = getTarget();
            ServerPlayer p = proprio();
            if (!libre() || !Reglages.get().arc || c == null || !c.isAlive() || p == null || p.level() != level()
                    || distanceToSqr(p) > 24 * 24) {
                return false;
            }
            double d2 = distanceToSqr(c);
            return d2 >= min * min && d2 <= TIR_MAX * TIR_MAX;
        }

        @Override
        public boolean canUse() {
            return aPortee(TIR_MIN) && getSensing().hasLineOfSight(getTarget());
        }

        @Override
        public boolean canContinueToUse() {
            // Une flèche déjà bandée part quand même ; sinon il faut que la cible soit encore assez loin
            return aPortee(lacher >= 0 ? 0 : getTarget() instanceof Creeper ? CREEPER_TROP_PRES : TIR_PROCHE);
        }

        @Override
        public void start() {
            arc = true;
            equiper();
            getNavigation().stop();
            prochainTir = tickCount + 3;
        }

        @Override
        public void stop() {
            arc = false;
            lacher = -1;
            stopUsingItem();
            if (geste == Geste.TIR_A_L_ARC) {
                finirGeste();
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity c = getTarget();
            if (c == null || !(level() instanceof ServerLevel niveau)) {
                return;
            }
            getNavigation().stop();
            getLookControl().setLookAt(c, 30f, 30f);
            float vers = (float) Math.toDegrees(Math.atan2(c.getZ() - getZ(), c.getX() - getX())) - 90f;
            setYRot(vers);
            setYBodyRot(vers);
            if (lacher >= 0) {
                if (geste != Geste.TIR_A_L_ARC) {
                    lacher = -1;        // interrompu (Jaune tape une commande urgente, par exemple)
                    stopUsingItem();
                } else if (tickCount >= lacher) {
                    tirer(niveau, c);
                    lacher = -1;
                    prochainTir = tickCount + 12 + random.nextInt(16);
                }
            } else if (tickCount >= prochainTir && geste != Geste.COMMANDE && geste != Geste.ECRIRE && getSensing().hasLineOfSight(c)) {
                equiper();
                startUsingItem(InteractionHand.MAIN_HAND);
                jouer(Geste.TIR_A_L_ARC);
                lacher = tickCount + LACHER;
            }
        }
    }

    /** Rejoint sa place derrière le joueur : en marchant, en courant, ou d'une perle de l'End s'il est trop loin. */
    private final class SuivreGoal extends Goal {
        private int chemin;

        SuivreGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private @Nullable ServerPlayer cible() {
            ServerPlayer p = proprio();
            return p != null && p.level() == level() && libre() && getTarget() == null ? p : null;
        }

        @Override
        public boolean canUse() {
            ServerPlayer p = cible();
            return p != null && distanceToSqr(place(p)) > 3.2 * 3.2;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer p = cible();
            return p != null && distanceToSqr(place(p)) > 1.0;
        }

        @Override
        public void start() {
            chemin = 0;
            loinDepuis = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            ServerPlayer p = cible();
            if (p == null) {
                return;
            }
            double d2 = distanceToSqr(p);
            loinDepuis = d2 > 10 * 10 ? loinDepuis + 1 : 0;
            if (d2 > 26 * 26 || loinDepuis > 120 || (isInLava() && d2 > 4)) {
                loinDepuis = 0;
                rejoindre(p);
                return;
            }
            if (geste.corpsEntier && geste.famille == Geste.Famille.AMUSEMENT) {
                finirGeste();       // une acrobatie s'arrête quand le joueur s'en va
            }
            Vec3 place = place(p);
            if (--chemin <= 0) {
                chemin = 10;
                double vitesse = d2 > 9 * 9 ? 1.45 : d2 > 5 * 5 ? 1.15 : 0.9;
                if (!getNavigation().moveTo(place.x, place.y, place.z, vitesse)) {
                    getNavigation().moveTo(p, vitesse);
                }
            }
            if (getNavigation().isDone() && distanceToSqr(place) > 1.0) {
                getMoveControl().setWantedPosition(place.x, place.y, place.z, 0.8);
            }
        }
    }

    /** Quand tout est calme : salto, roue, danse, poirier… */
    private final class AmuserGoal extends Goal {
        private Geste choisi = Geste.AUCUN;

        AmuserGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!libre() || !Reglages.get().acrobaties || tickCount < prochainAmusement || geste != Geste.AUCUN
                    || getTarget() != null || mine || !sac.isEmpty() || !onGround()) {
                return false;
            }
            ServerPlayer p = proprio();
            return p != null && p.level() == level() && distanceToSqr(p) < 8 * 8;
        }

        @Override
        public boolean canContinueToUse() {
            return libre() && geste == choisi && tickCount < finGeste && getTarget() == null;
        }

        @Override
        public void start() {
            getNavigation().stop();
            choisi = amusementAuHasard();
            ServerPlayer p = proprio();
            if (p != null) {
                getLookControl().setLookAt(p);
                float vers = (float) Math.toDegrees(Math.atan2(p.getZ() - getZ(), p.getX() - getX())) - 90f;
                setYRot(vers);
                setYBodyRot(vers);
                setYHeadRot(vers);
            }
            jouer(choisi);
        }

        @Override
        public void stop() {
            if (geste == choisi) {
                finirGeste();
            }
            prochainAmusement = tickCount + 140 + random.nextInt(260);
        }
    }

    /** Mine les minerais visibles autour du joueur, un par un, et garde le butin dans son sac. */
    private final class MinerGoal extends Goal {
        private @Nullable BlockPos minerai;
        private @Nullable GlobalPos reserve;
        private int progres;
        private int total;
        private int etape = -1;
        private int prochainScan;
        private int bloque;

        MinerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!libre() || !Reglages.get().miner || getTarget() != null || tickCount < prochainScan || sacPlein()) {
                return false;
            }
            prochainScan = tickCount + 30 + random.nextInt(20);
            ServerPlayer p = proprio();
            if (p == null || p.level() != level() || p.isCreative() || distanceToSqr(p) > 14 * 14) {
                return false;
            }
            minerai = chercher((ServerLevel) level(), p.blockPosition(), 7, 4, p);
            return minerai != null;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer p = proprio();
            return minerai != null && libre() && getTarget() == null && p != null && p.level() == level() && distanceToSqr(p) < 20 * 20;
        }

        @Override
        public void start() {
            mine = true;
            viser(minerai);
        }

        @Override
        public void stop() {
            mine = false;
            if (minerai != null) {
                level().destroyBlockProgress(getId(), minerai, -1);
            }
            liberer();
            minerai = null;
            prochainScan = tickCount + 40;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private void viser(@Nullable BlockPos pos) {
            liberer();
            minerai = pos;
            progres = 0;
            etape = -1;
            bloque = 0;
            if (pos == null) {
                return;
            }
            reserve = GlobalPos.of(level().dimension(), pos.immutable());
            RESERVES.add(reserve);
            BlockState etat = level().getBlockState(pos);
            float durete = Math.max(0.5f, etat.getDestroySpeed(level(), pos));
            total = (int) ((8 + durete * 7) * (personnage == Personnage.VERT ? 0.7f : 1f));
            Path chemin = getNavigation().createPath(pos, 1);
            if (chemin != null) {
                getNavigation().moveTo(chemin, 1.1);
            }
        }

        private void liberer() {
            if (reserve != null) {
                RESERVES.remove(reserve);
                reserve = null;
            }
        }

        @Override
        public void tick() {
            if (minerai == null || !(level() instanceof ServerLevel niveau)) {
                return;
            }
            BlockState etat = niveau.getBlockState(minerai);
            if (!estMinerai(etat)) {
                viser(chercher(niveau, minerai, 3, 3, proprio()));      // quelqu'un l'a cassé : le suivant
                return;
            }
            Vec3 centre = Vec3.atCenterOf(minerai);
            getLookControl().setLookAt(centre.x, centre.y, centre.z);
            if (getEyePosition().distanceToSqr(centre) > 3.4 * 3.4) {
                if (getNavigation().isDone() && ++bloque % 20 == 0) {
                    Path chemin = getNavigation().createPath(minerai, 1);
                    if (chemin == null || bloque > 100) {
                        viser(null);        // inaccessible : on laisse tomber
                        return;
                    }
                    getNavigation().moveTo(chemin, 1.1);
                }
                return;
            }
            getNavigation().stop();
            if (progres % 10 == 0) {
                jouer(Geste.PIOCHE);
                niveau.playSound(null, minerai, etat.getSoundType().getHitSound(), getSoundSource(), 0.6f, 0.9f);
            }
            progres++;
            int nouvelle = Math.min(9, progres * 10 / Math.max(1, total));
            if (nouvelle != etape) {
                etape = nouvelle;
                niveau.destroyBlockProgress(getId(), minerai, etape);
            }
            if (progres >= total) {
                casser(niveau, minerai, etat);
                viser(chercher(niveau, minerai, 3, 3, proprio()));
            }
        }

        private void casser(ServerLevel niveau, BlockPos pos, BlockState etat) {
            niveau.destroyBlockProgress(getId(), pos, -1);
            List<ItemStack> butin = Block.getDrops(etat, niveau, pos, niveau.getBlockEntity(pos), Stickman.this, pioche());
            etat.spawnAfterBreak(niveau, pos, pioche(), true);
            niveau.destroyBlock(pos, false, Stickman.this, 512);
            for (ItemStack objet : butin) {
                ItemStack reste = sac.addItem(objet);
                if (!reste.isEmpty()) {
                    spawnAtLocation(niveau, reste);
                }
            }
            String id = BuiltInRegistries.BLOCK.getKey(etat.getBlock()).getPath();
            if (proprietaire != null) {
                Souvenirs.de(proprietaire).minerai(niveau.getGameTime(), id, personnage);
                if (id.contains("diamond")) {
                    ServerPlayer p = proprio();
                    for (Stickman s : bande(niveau, proprietaire)) {
                        if (s.personnage.estOrange() && p != null && s.libre() && s.geste == Geste.AUCUN) {
                            s.ecrire(p, "diamants");
                        }
                    }
                }
            }
        }

        private boolean sacPlein() {
            int total = 0;
            for (int i = 0; i < sac.getContainerSize(); i++) {
                total += sac.getItem(i).isEmpty() ? 0 : 1;
            }
            return total >= sac.getContainerSize() - 1;
        }
    }

    private static ItemStack pioche() {
        if (pioche == null) {
            pioche = new ItemStack(Items.IRON_PICKAXE);
        }
        return pioche;
    }

    private static boolean estMinerai(BlockState etat) {
        return etat.is(ConventionalBlockTags.ORES) && pioche().isCorrectToolForDrops(etat);
    }

    /** Le minerai visible le plus proche (au moins une face à l'air libre), que personne d'autre ne vise. */
    private @Nullable BlockPos chercher(ServerLevel niveau, BlockPos centre, int rayon, int hauteur, @Nullable ServerPlayer p) {
        BlockPos meilleur = null;
        double meilleure = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-rayon, -hauteur, -rayon), centre.offset(rayon, hauteur, rayon))) {
            BlockState etat = niveau.getBlockState(pos);
            if (!estMinerai(etat) || RESERVES.contains(GlobalPos.of(niveau.dimension(), pos))) {
                continue;
            }
            // Celui que le joueur est sans doute en train de miner, on le lui laisse
            if (p != null && p.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < 2.4 * 2.4) {
                continue;
            }
            boolean visible = false;
            for (Direction d : Direction.values()) {
                if (niveau.getBlockState(pos.relative(d)).isAir()) {
                    visible = true;
                    break;
                }
            }
            double d2 = pos.distSqr(blockPosition());
            if (visible && d2 < meilleure) {
                meilleur = pos.immutable();
                meilleure = d2;
            }
        }
        if (meilleur != null && getNavigation().createPath(meilleur, 1) == null) {
            return null;
        }
        return meilleur;
    }

    /** Rapporte au joueur ce qu'il a miné, et le lui lance. */
    private final class RapporterGoal extends Goal {
        private int lancer;
        private int chemin;

        RapporterGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            ServerPlayer p = proprio();
            return !sac.isEmpty() && libre() && !mine && getTarget() == null && p != null && p.level() == level();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() || lancer > 0;
        }

        @Override
        public void start() {
            lancer = 0;
            chemin = 0;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            ServerPlayer p = proprio();
            if (p == null || !(level() instanceof ServerLevel niveau)) {
                lancer = 0;
                return;
            }
            getLookControl().setLookAt(p);
            if (lancer > 0) {
                if (++lancer == 5) {
                    lancerLeSac(niveau, p);
                }
                if (lancer > 12) {
                    lancer = 0;
                }
                return;
            }
            if (distanceToSqr(p) > 2.8 * 2.8) {
                if (--chemin <= 0) {
                    chemin = 10;
                    getNavigation().moveTo(p, 1.15);
                }
                return;
            }
            getNavigation().stop();
            jouer(Geste.LANCER);
            lancer = 1;
        }
    }

    private void lancerLeSac(ServerLevel niveau, ServerPlayer p) {
        Vec3 depart = new Vec3(getX(), getEyeY() - 0.3, getZ());
        Vec3 vers = p.getEyePosition().subtract(depart);
        Vec3 elan = new Vec3(vers.x, 0, vers.z).normalize().scale(Math.min(0.45, 0.1 + vers.horizontalDistance() * 0.09)).add(0, 0.22, 0);
        for (int i = 0; i < sac.getContainerSize(); i++) {
            ItemStack objet = sac.removeItemNoUpdate(i);
            if (!objet.isEmpty()) {
                ItemEntity lance = new ItemEntity(niveau, depart.x, depart.y, depart.z, objet);
                lance.setDeltaMovement(elan.add(random.nextGaussian() * 0.02, 0, random.nextGaussian() * 0.02));
                lance.setPickUpDelay(4);
                niveau.addFreshEntity(lance);
            }
        }
        niveau.playSound(null, getX(), getY(), getZ(), SoundEvents.SNOWBALL_THROW, getSoundSource(), 0.6f, 1.2f);
    }

    /** Fonce sur ce qui vient de frapper le joueur. */
    private final class DefendreGoal extends TargetGoal {
        private int dernier;
        private @Nullable LivingEntity agresseur;

        DefendreGoal() {
            super(Stickman.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer p = proprio();
            if (p == null || !libre()) {
                return false;
            }
            agresseur = p.getLastHurtByMob();
            return agresseur != null && p.getLastHurtByMobTimestamp() != dernier && !(agresseur instanceof Player)
                    && !memeBande(agresseur) && canAttack(agresseur, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(agresseur);
            ServerPlayer p = proprio();
            if (p != null) {
                dernier = p.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    /** Attaque ce que le joueur attaque (sauf les autres joueurs et les animaux de compagnie). */
    private final class AiderGoal extends TargetGoal {
        private int dernier;
        private @Nullable LivingEntity cible;

        AiderGoal() {
            super(Stickman.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer p = proprio();
            if (p == null || !libre()) {
                return false;
            }
            cible = p.getLastHurtMob();
            return cible != null && p.getLastHurtMobTimestamp() != dernier && !(cible instanceof Player) && !memeBande(cible)
                    && !(cible instanceof net.minecraft.world.entity.OwnableEntity) && cible instanceof Enemy
                    && canAttack(cible, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(cible);
            ServerPlayer p = proprio();
            if (p != null) {
                dernier = p.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }

    /** Repère de loin les monstres qui s'approchent, creepers compris : de quoi sortir l'arc. */
    private final class ViserGoal extends TargetGoal {
        private @Nullable LivingEntity cible;

        ViserGoal() {
            super(Stickman.this, true);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer p = proprio();
            if (p == null || !libre() || !Reglages.get().arc || tickCount % 10 != 0 || p.level() != level()) {
                return false;
            }
            cible = null;
            double mieux = Double.MAX_VALUE;
            List<Stickman> autres = level().getEntitiesOfClass(Stickman.class, getBoundingBox().inflate(32), s -> s != Stickman.this && memeBande(s));
            for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(TIR_MAX), e -> e instanceof Enemy && e.isAlive())) {
                double d2 = distanceToSqr(m);
                if (d2 < TIR_MIN * TIR_MIN || d2 > TIR_MAX * TIR_MAX || m.distanceToSqr(p) >= 32 * 32 || !approche(m, p)
                        || !getSensing().hasLineOfSight(m) || !canAttack(m, TargetingConditions.DEFAULT)) {
                    continue;
                }
                // Chacun le sien : un monstre touché revient invincible un instant, les flèches suivantes rebondiraient.
                // Celui que d'autres visent déjà compte comme 15 blocs plus loin.
                double note = d2 + 15 * 15 * autres.stream().filter(s -> s.getTarget() == m).count();
                if (note < mieux) {
                    mieux = note;
                    cible = m;
                }
            }
            return cible != null;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity c = mob.getTarget();
            // Un creeper arrivé trop près : on le laisse (à Jaune et à ses commandes)
            if (c == null || c instanceof Creeper && distanceToSqr(c) < CREEPER_TROP_PRES * CREEPER_TROP_PRES) {
                return false;
            }
            return super.canContinueToUse();
        }

        @Override
        public void start() {
            mob.setTarget(cible);
            super.start();
        }
    }
}
