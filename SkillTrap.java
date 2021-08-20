package com.herocraftonline.heroes.characters.skill.reborn.pathfinder;

import com.herocraftonline.heroes.Heroes;
import com.herocraftonline.heroes.api.SkillResult;
import com.herocraftonline.heroes.characters.CharacterTemplate;
import com.herocraftonline.heroes.characters.Hero;
import com.herocraftonline.heroes.characters.effects.common.RootEffect;
import com.herocraftonline.heroes.characters.skill.SkillConfigManager;
import com.herocraftonline.heroes.characters.skill.SkillSetting;
import com.herocraftonline.heroes.characters.skill.SkillType;
import com.herocraftonline.heroes.characters.skill.skills.SkillBaseGroundEffect;
import com.herocraftonline.heroes.util.Util;
import de.slikey.effectlib.Effect;
import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.EffectType;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class SkillTrap extends SkillBaseGroundEffect {

    public SkillTrap(Heroes plugin) {
        super(plugin, "Trap");
        setDescription("You set a trap underneath that lasts for $1s. The first player who sets of the trap will be rooted for $2s");
        setUsage("/skill trap");
        setIdentifiers("skill trap");
        setArgumentRange(0, 0);
        setTypes(SkillType.ABILITY_PROPERTY_MAGICAL, SkillType.MULTI_GRESSIVE, SkillType.AREA_OF_EFFECT, SkillType.NO_SELF_TARGETTING, SkillType.SILENCEABLE);
    }

    @Override
    public String getDescription(Hero hero) {
        long warmUp = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DELAY, 3000, false);
        long duration = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DURATION, 30000, false);
        return getDescription()
                .replace("$1", Util.decFormat.format((double) duration / 1000));
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        ConfigurationSection node = super.getDefaultConfig();
        node.set(SkillSetting.RADIUS.node(), 3d);
        node.set(HEIGHT_NODE, 2d);
        node.set(SkillSetting.DELAY.node(), 5000);
        node.set("root-duration", 2000);
        node.set(SkillSetting.DURATION.node(), 5000);
        node.set(SkillSetting.PERIOD.node(), 500);
        return node;
    }

    @Override public SkillResult use(Hero hero, String[] strings) {
        final Player player = hero.getPlayer();
        Location playerLoc = player.getLocation();

        // place on ground only
        Material belowBlockType = playerLoc.getBlock().getRelative(BlockFace.DOWN).getType();
        if (!belowBlockType.isSolid()) {
            player.sendMessage("You must be standing on something hard to place the trap");
            return SkillResult.FAIL;
        }

        broadcastExecuteText(hero);

        final double radius = SkillConfigManager.getUseSetting(hero, this, SkillSetting.RADIUS, 3d, false);
        double height = SkillConfigManager.getUseSetting(hero, this, HEIGHT_NODE, 2d, false);
        long duration = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DURATION, 30000, false);
        final long period = SkillConfigManager.getUseSetting(hero, this, SkillSetting.PERIOD, 500, false);
        long rootDuration = SkillConfigManager.getUseSetting(hero, this, "root-duration", 2000, false);
        applyAreaGroundEffectEffect(hero, period, duration, player.getLocation(), radius, height, new GroundEffectActions() {

            @Override
            public void groundEffectTickAction(Hero hero, AreaGroundEffectEffect effect) {
                EffectManager em = new EffectManager(plugin);
                Effect e = new Effect(em) {
                    int particlesPerRadius = 3;
                    Particle particle = Particle.SMOKE_LARGE;

                    @Override
                    public void onRun() {
                        double inc = 1 / (particlesPerRadius * radius);

                        for (double angle = 0; angle <= 2 * Math.PI; angle += inc) {
                            Vector v = new Vector(Math.cos(angle), 0, Math.sin(angle)).multiply(radius);
                            display(particle, getLocation().add(v));
                            getLocation().subtract(v);
                        }
                    }
                };

                Location location = effect.getLocation().clone();
                e.setLocation(location);
                e.asynchronous = true;
                e.iterations = 1;
                e.type = EffectType.INSTANT;
                e.color = Color.WHITE;

                e.start();
                em.disposeOnTermination();
            }

            @Override
            public void groundEffectTargetAction(Hero hero, final LivingEntity target, final AreaGroundEffectEffect groundEffect) {
                Player player = hero.getPlayer();
                if (!damageCheck(player, target))
                    return;

                SkillTrap skill = SkillTrap.this;
                final CharacterTemplate targetCT = plugin.getCharacterManager().getCharacter(target);
                final RootEffect effect = new RootEffect(skill, player, 100, rootDuration);
                targetCT.addEffect(effect);

                Location targetLocation = target.getLocation();
                targetLocation.getWorld().playSound(targetLocation, Sound.BLOCK_WOODEN_PRESSURE_PLATE_CLICK_ON, 0.8F, 0.5F);
                targetLocation.getWorld().playSound(targetLocation, Sound.BLOCK_WOODEN_PRESSURE_PLATE_CLICK_OFF, 0.8F, 0.5F);
                hero.removeEffect(hero.getEffect(skill.getName()));
            }
        });
        return SkillResult.NORMAL;
    }
