ackage com.herocraftonline.heroes.characters.skill.reborn.pathfinder;

import com.herocraftonline.heroes.Heroes;
import com.herocraftonline.heroes.api.SkillResult;
import com.herocraftonline.heroes.attributes.AttributeType;
import com.herocraftonline.heroes.characters.CharacterTemplate;
import com.herocraftonline.heroes.characters.Hero;
import com.herocraftonline.heroes.characters.effects.EffectType;
import com.herocraftonline.heroes.characters.effects.ExpirableEffect;
import com.herocraftonline.heroes.characters.effects.common.SlowEffect;
import com.herocraftonline.heroes.characters.skill.*;
import com.herocraftonline.heroes.chat.ChatComponents;
import com.herocraftonline.heroes.util.Util;
import de.slikey.effectlib.Effect;
import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.effect.CloudEffect;
import de.slikey.effectlib.util.RandomUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.Map.Entry;

public class SkillArrowStorm extends ActiveSkill {

    private Map<Arrow, Long> stormArrows = new LinkedHashMap<Arrow, Long>(100) {
        private static final long serialVersionUID = 4632858378318784263L;

        @Override
        protected boolean removeEldestEntry(Entry<Arrow, Long> eldest) {
            return (size() > 7000 || eldest.getValue() + 5000 <= System.currentTimeMillis());
        }
    };

    private String applyText;
    private String expireText;

    public SkillArrowStorm(Heroes plugin) {
        super(plugin, "ArrowStorm");
        setDescription("Summon a powerful storm of arrows at your target location. Arrows storm down from the sky, dealing $1 damage and slowing any targets hit for $2 seconds.");
        setUsage("/skill ArrowStorm");
        setArgumentRange(0, 0);
        setIdentifiers("skill ArrowStorm");
        setTypes(SkillType.ABILITY_PROPERTY_MAGICAL, SkillType.ABILITY_PROPERTY_PROJECTILE, SkillType.SILENCEABLE, SkillType.DAMAGING, SkillType.AREA_OF_EFFECT, SkillType.AGGRESSIVE);

        Bukkit.getServer().getPluginManager().registerEvents(new SkillEntityListener(this), plugin);

    }

    @Override
    public String getDescription(Hero hero) {
        double damage = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DAMAGE, 50, false);
        long duration = SkillConfigManager.getUseSetting(hero, this, "slow-duration", 2000, false);

        String formattedDuration = Util.decFormat.format(duration / 1000.0);
        String formattedDamage = Util.decFormat.format(damage);

        return getDescription().replace("$1", formattedDamage).replace("$2", formattedDuration);
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        ConfigurationSection node = super.getDefaultConfig();
        node.set(SkillSetting.MAX_DISTANCE.node(), 12);
        node.set(SkillSetting.RADIUS.node(), 5);
        node.set(SkillSetting.DAMAGE.node(), 15);
        node.set("max-storm-height", 10);
        node.set("downward-velocity", 1.0);
        node.set("velocity-deviation", 0.0);
        node.set("delay-between-firing", 0.1);
        node.set("storm-arrows-launched", 100);
        node.set("slow duration", 1000);
        node.set("slow-multiplier", 1);
        node.set(SkillSetting.APPLY_TEXT.node(), "");
        node.set(SkillSetting.EXPIRE_TEXT.node(), "");

        return node;
    }

    @Override
    public void init() {
        super.init();

        applyText = SkillConfigManager.getRaw(this, SkillSetting.APPLY_TEXT, ChatComponents.GENERIC_SKILL + "%target% has been slowed by %hero%'s ArrowStorm!").replace("%target%", "$1").replace("%hero%", "$2");
        expireText = SkillConfigManager.getRaw(this, SkillSetting.EXPIRE_TEXT, ChatComponents.GENERIC_SKILL + "%target% is no longer slowed!").replace("%target%", "$1");
    }

    @Override
    public SkillResult use(Hero hero, String[] args) {
        final Player player = hero.getPlayer();

        final int radius = SkillConfigManager.getUseSetting(hero, this, SkillSetting.RADIUS, 10, false);

        int numstormArrows = SkillConfigManager.getUseSetting(hero, this, "storm-arrows-launched", 12, false);


        double delayBetween = SkillConfigManager.getUseSetting(hero, this, "delay-between-firing", 0.2, false);
        final double velocityDeviation = SkillConfigManager.getUseSetting(hero, this, "velocity-deviation", 0.2, false);
        final double yVelocity = SkillConfigManager.getUseSetting(hero, this, "downward-velocity", 0.5, false);

        int stormHeight = SkillConfigManager.getUseSetting(hero, this, "max-storm-height", 10, false);

        int maxDist = SkillConfigManager.getUseSetting(hero, this, SkillSetting.MAX_DISTANCE, 12, false);

        Block tBlock = player.getTargetBlock((HashSet<Material>) null, maxDist);
        // Block tBlock = player.getTargetBlock(null, maxDist);
        if (tBlock == null)
            return SkillResult.INVALID_TARGET;

        broadcastExecuteText(hero);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.2F, 1.0F);

        // Create a cicle of stormArrow launch locations, based on skill radius.
        Location stormCenter = tBlock.getLocation().add(new Vector(.5, stormHeight + 0.5d, .5));
        List<Location> possibleLaunchLocations = Util.getCircleLocationList(stormCenter, radius, 1, true, true, 0);
        int numPossibleLaunchLocations = possibleLaunchLocations.size();

        Collections.shuffle(possibleLaunchLocations);

        long time = System.currentTimeMillis();
        final Random ranGen = new Random((int) ((time / 2.0) * 12));

        // Play the firework effects in a sequence
        final World world = tBlock.getLocation().getWorld();
        int k = 0;
        for (int i = 0; i < numstormArrows; i++) {
            if (k >= numPossibleLaunchLocations) {
                Collections.shuffle(possibleLaunchLocations);
                k = 0;
            }

            final Location fLoc = possibleLaunchLocations.get(k);
            k++;

            final int j = i;
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {

                    double randomX = ranGen.nextGaussian() * velocityDeviation;
                    double randomZ = ranGen.nextGaussian() * velocityDeviation;

                    Vector vel = new Vector(randomX, -yVelocity, randomZ);

                    Arrow stormArrow = world.spawn(fLoc, Arrow.class);
                    //stormArrow.getWorld().spigot().playEffect(stormArrow.getLocation(), Effect.EXPLOSION_LARGE, 0, 0, 0.4F, 0.4F, 0.4F, 0.0F, 2, 32);
                    //stormArrow.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, stormArrow.getLocation(), 2, 0.4, 0.4, 0.4, 0);

                    cloudEffect(stormArrow.getLocation());
                    stormArrow.setShooter(player);
                    stormArrow.setVelocity(vel);
                    stormArrows.put(stormArrow, System.currentTimeMillis());

                }
            }, (long) ((delayBetween * i) * 20));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_BURN, 0.5F, 1.0F);
        return SkillResult.NORMAL;
    }

    public void cloudEffect(Location location) {
        //Cloud Effect
        EffectManager em = new EffectManager(plugin);
        final VisualEffect firework = new VisualEffect();
        Effect visualEffect = new Effect(em) {
            public Particle cloudParticle = Particle.CLOUD;
            public Color cloudColor = Color.WHITE;
            public Particle mainParticle = Particle.REDSTONE;
            public Color mainParticleColor = Color.GREEN;
            public float cloudSize = .7f;
            public float particleRadius = cloudSize - .1f;
            public double yOffset = .8;

            @Override
            public void onRun() {
                Location location = getLocation();
                location.add(0, yOffset, 0);
                for (int i = 0; i < 50; i++) {
                    Vector v = RandomUtils.getRandomCircleVector().multiply(RandomUtils.random.nextDouble() * cloudSize);
                    display(cloudParticle, location.add(v), cloudColor, 0, 7);
                    location.subtract(v);
                }
                Location l = location.add(0, .2, 0);
                for (int i = 0; i < 15; i++) {
                    int r = RandomUtils.random.nextInt(2);
                    double x = RandomUtils.random.nextDouble() * particleRadius;
                    double z = RandomUtils.random.nextDouble() * particleRadius;
                    l.add(x, 0, z);
                    if (r != 1) {
                        display(mainParticle, l, mainParticleColor);
                    }
                    l.subtract(x, 0, z);
                    l.subtract(x, 0, z);
                    if (r != 1) {
                        display(mainParticle, l, mainParticleColor);
                    }
                    l.add(x, 0, z);
                }
            }
        };

        visualEffect.type = de.slikey.effectlib.EffectType.INSTANT;
//        visualEffect.period = 5;
//        visualEffect.iterations = 50;
        visualEffect.asynchronous = true;
        visualEffect.setLocation(location);

        visualEffect.start();
        em.disposeOnTermination();
    }


    public class SkillEntityListener implements Listener {

        private final Skill skill;

        public SkillEntityListener(Skill skill) {
            this.skill = skill;
        }

        @EventHandler()
        public void onEntityDamage(EntityDamageEvent event) {
            if (event.isCancelled() || !(event instanceof EntityDamageByEntityEvent) || !(event.getEntity() instanceof LivingEntity)) {
                return;
            }

            EntityDamageByEntityEvent subEvent = (EntityDamageByEntityEvent) event;
            Entity projectile = subEvent.getDamager();
            if (!(projectile instanceof Arrow) || !stormArrows.containsKey(projectile)) {
                return;
            }

            event.setDamage(0.0);
            event.setCancelled(true);
            stormArrows.remove(projectile);

            ProjectileSource source = ((Projectile) subEvent.getDamager()).getShooter();
            if (!(source instanceof LivingEntity))
                return;
            Entity dmger = (LivingEntity) source;
            if (dmger instanceof Player) {
                Hero hero = plugin.getCharacterManager().getHero((Player) dmger);

                if (!damageCheck((Player) dmger, (LivingEntity) event.getEntity()))
                    return;

                LivingEntity target = (LivingEntity) event.getEntity();
                CharacterTemplate targetCT = plugin.getCharacterManager().getCharacter(target);
                // Check if entity is immune to further firewave hits
                if (targetCT.hasEffect("ArrowStormAntiMultiEffect")) {
                    event.setCancelled(true);
                    return;
                }

                double damage = SkillConfigManager.getUseSetting(hero, skill, SkillSetting.DAMAGE, 50, false);
                double damageIncrease = SkillConfigManager.getUseSetting(hero, skill, SkillSetting.DAMAGE_INCREASE_PER_INTELLECT, 1.0, false);
                damage += (damageIncrease * hero.getAttributeValue(AttributeType.INTELLECT));

                long duration = SkillConfigManager.getUseSetting(hero, skill, "slow-duration", 2000, false);
                int amplifier = SkillConfigManager.getUseSetting(hero, skill, "slow-multiplier", 1, false);

                SlowEffect arrowSlowEffect = new SlowEffect(skill, (Player) dmger, duration, amplifier, applyText, expireText);
                arrowSlowEffect.types.add(EffectType.DISPELLABLE);
                arrowSlowEffect.types.add(EffectType.AREA_OF_EFFECT);

                targetCT.addEffect(arrowSlowEffect);
                targetCT.addEffect(new ExpirableEffect(skill, "ArrowStormAntiMultiEffect", (Player) dmger, 500));

                //addSpellTarget((LivingEntity) event.getEntity(), hero);
                addSpellTarget(event.getEntity(), hero);
                damageEntity(target, hero.getPlayer(), damage, EntityDamageEvent.DamageCause.MAGIC);
                event.setCancelled(true);
            }
        }
    }
}
