package com.herocraftonline.heroes.characters.skill.reborn.pathfinder;

import com.herocraftonline.heroes.Heroes;
import com.herocraftonline.heroes.characters.CharacterTemplate;
import com.herocraftonline.heroes.characters.Hero;
import com.herocraftonline.heroes.characters.effects.ExpirableEffect;
import com.herocraftonline.heroes.characters.skill.*;
import com.herocraftonline.heroes.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;

public class SkillSerratedArrows extends PassiveSkill {

    public SkillSerratedArrows(Heroes plugin) {
        super(plugin, "SerratedArrows");
        setDescription("The third arrow hit against the same target within $1s will deal $2 bonus damage and pierce through your targets Armor");
        setUsage("/skill serratedarrows");
        setArgumentRange(0, 0);
        setIdentifiers("skill serratedarrows");
        setTypes(SkillType.DAMAGING);
        Bukkit.getServer().getPluginManager().registerEvents(new SkillDamageListener(this), plugin);
    }

    @Override
    public String getDescription(Hero hero) {
        double damage = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DAMAGE, 90, false);
        long duration = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DURATION, 5000, false);

        return getDescription()
                .replace("$1", Util.decFormat.format(duration))
                .replace("$2", Util.decFormat.format(damage));
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        ConfigurationSection config = super.getDefaultConfig();
        config.set(SkillSetting.DURATION.node(), 5000);
        config.set(SkillSetting.DAMAGE.node(), 90);
        return config;
    }

    public class SkillDamageListener implements Listener {
        private Skill skill;
        SkillDamageListener(Skill skill) {
            this.skill = skill;
        }

        public void onEntityShootBow(EntityShootBowEvent event) {
        }

        private String getMultiHitEffectName(Player player) {
            return player.getName() + "-SerratedArrows";
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onEntityDamage(EntityDamageByEntityEvent event) {
            if (!(event.getDamager() instanceof Arrow)) {
                return;
            }

            Arrow arrow = (Arrow) event.getDamager();
            if (!(arrow.getShooter() instanceof Player)) {
                return;
            }

            final Player player = (Player) arrow.getShooter();
            final Hero hero = plugin.getCharacterManager().getHero(player);
            double damage = SkillConfigManager.getUseSetting(hero, this.skill, SkillSetting.DAMAGE, 90, false);
            long duration = SkillConfigManager.getUseSetting(hero, this.skill, SkillSetting.DURATION, 30000, false);

            if (!hero.hasEffect("SerratedArrows")) {
                return;
            }


            LivingEntity target = (LivingEntity) event.getEntity();
            CharacterTemplate targetCT = plugin.getCharacterManager().getCharacter(target);
            String effectName = getMultiHitEffectName(player);

            if (targetCT.hasEffect(effectName)) {
               SerratedArrowsHitEffect serratedEffect = (SerratedArrowsHitEffect) targetCT.getEffect(effectName);
               serratedEffect.addHit();
               if (serratedEffect.getHitCount() == 3) {
                   addSpellTarget(target, hero);
                   damageEntity(target, player, damage, EntityDamageEvent.DamageCause.MAGIC, true);
                   targetCT.removeEffect(serratedEffect);
                   VisualEffect.playInstantFirework(FireworkEffect.builder()
                           .flicker(false)
                           .trail(false)
                           .with(FireworkEffect.Type.BURST)
                           .withColor(Color.WHITE)
                           .withFade(Color.GREEN)
                           .build(), target.getLocation().add(0, 1.0, 0));
               }

            } else {
                targetCT.addEffect(new SerratedArrowsHitEffect(skill, effectName, player, duration));
            }

            /*
            if (hero.hasEffect("SerratedArrows")) {
                hitCount++;
                player.sendMessage("hit");
            }

            if (hitCount == 3) {
                final LivingEntity target = (LivingEntity) event.getEntity();
                addSpellTarget(target, hero);
                damageEntity(target, player, damage, EntityDamageEvent.DamageCause.MAGIC, true);
                target.sendMessage("fuckyou");
                player.sendMessage("hi cutie");
                VisualEffect.playInstantFirework(FireworkEffect.builder()
                        .flicker(false)
                        .trail(false)
                        .with(FireworkEffect.Type.BURST)
                        .withColor(Color.WHITE)
                        .withFade(Color.GREEN)
                        .build(), player.getLocation().add(0, 2.0, 0));
                hitCount = 0;
            }
             */
        }
    }

    private class SerratedArrowsHitEffect extends ExpirableEffect {
        private int hitCount = 1;

        SerratedArrowsHitEffect(Skill skill, String name, Player applier, long duration) {
            super(skill, name, applier, duration);
        }

        private int getHitCount() {
            return this.hitCount;
        }

        private void addHit() {
            this.hitCount++;
            this.setDuration(getDuration());

        }
    }
}
