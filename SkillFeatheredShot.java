package com.herocraftonline.heroes.characters.skill.reborn.pathfinder;

import com.herocraftonline.heroes.Heroes;
import com.herocraftonline.heroes.api.SkillResult;
import com.herocraftonline.heroes.characters.CharacterTemplate;
import com.herocraftonline.heroes.characters.Hero;
import com.herocraftonline.heroes.characters.effects.EffectType;
import com.herocraftonline.heroes.characters.effects.ExpirableEffect;
import com.herocraftonline.heroes.characters.skill.*;
import com.herocraftonline.heroes.chat.ChatComponents;
import com.herocraftonline.heroes.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SkillFeatheredShot extends ActiveSkill {

    private String applyText;
    private String expireText;
    private String shotEffect = "HasFeatheredArrows";

    public SkillFeatheredShot(Heroes plugin) {
        super(plugin, "FeatheredShot");
        setDescription("For the next $1 second(s), your arrows will apply a jump boost and slow fall to slow down your target.");
        setUsage("/skill featheredshot");
        setIdentifiers("skill featheredshot");
        setArgumentRange(0, 0);
        setTypes(SkillType.DEBUFFING, SkillType.BUFFING);

        Bukkit.getServer().getPluginManager().registerEvents(new SkillDamageListener(this), plugin);
    }

    @Override
    public String getDescription(Hero hero) {
        int duration = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DURATION, 10000, false);
        double damage = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DAMAGE, 5, false);

        String formattedDamage = Util.decFormat.format(damage);
        String formattedDuration = Util.decFormat.format(duration / 1000.0);

        return getDescription()
                .replace("$1", formattedDuration)
                .replace("$2", formattedDamage);
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        ConfigurationSection config = super.getDefaultConfig();
        config.set(SkillSetting.DURATION.node(), 10000);
        config.set(SkillSetting.DAMAGE.node(), 15.0);
        config.set(SkillSetting.DAMAGE_INCREASE_PER_INTELLECT.node(), 0.0);
        config.set("shot-duration", 4000);
        config.set("slow-fall-amplifier", 3);
        config.set("jump-boost-amplifier", 4);
        config.set(SkillSetting.APPLY_TEXT.node(), ChatComponents.GENERIC_SKILL + "%hero% has deadly feathers attached to their bow.");
        config.set(SkillSetting.EXPIRE_TEXT.node(), ChatComponents.GENERIC_SKILL + "%hero%'s bow no longer has deadly feathers attached to their bow.");
        return config;
    }

    public void init() {
        super.init();

        applyText = SkillConfigManager.getRaw(this, SkillSetting.APPLY_TEXT,
                ChatComponents.GENERIC_SKILL + "%hero% has deadly feathers attached to their bow.")
                .replace("%hero%", "$1");

        expireText = SkillConfigManager.getRaw(this, SkillSetting.EXPIRE_TEXT,
                ChatComponents.GENERIC_SKILL + "%hero%'s bow no longer has deadly feathers attached to their bow.")
                .replace("%hero%", "$1");
    }

    @Override
    public SkillResult use(Hero hero, String[] args) {
        broadcastExecuteText(hero);

        int duration = SkillConfigManager.getUseSetting(hero, this, SkillSetting.DURATION, 10000, false);
        hero.addEffect(new FeatherArrowsEffect(this, hero.getPlayer(), duration));

        return SkillResult.NORMAL;
    }

    public class FeatherArrowsEffect extends ExpirableEffect {

        FeatherArrowsEffect(Skill skill, Player applier, long duration) {
            super(skill, shotEffect, applier, duration, applyText, expireText);
            types.add(EffectType.IMBUE);
            types.add(EffectType.BENEFICIAL);
        }

        @Override
        public void applyToHero(Hero hero) {
            super.applyToHero(hero);

            for (final com.herocraftonline.heroes.characters.effects.Effect effect : hero.getEffects()) {
                if (effect.equals(this)) {
                    continue;
                }

                if (effect.isType(EffectType.IMBUE)) {
                    hero.removeEffect(effect);
                }
            }
        }

        @Override
        public void removeFromHero(Hero hero) {
            super.removeFromHero(hero);
            VisualEffect effect = new VisualEffect();
        }
    }

    public class SkillDamageListener implements Listener {
        private final Skill skill;

        public SkillDamageListener(Skill skill) {
            this.skill = skill;
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onEntityDamage(EntityDamageByEntityEvent event) {
            if (!(event.getEntity() instanceof LivingEntity))
                return;

            if (!(event.getDamager() instanceof Arrow))
                return;

            final Arrow arrow = (Arrow) event.getDamager();
            if (!(arrow.getShooter() instanceof Player))
                return;

            final Player player = (Player) arrow.getShooter();
            final Hero hero = plugin.getCharacterManager().getHero(player);
            if (!hero.hasEffect(shotEffect))
                return;

            CharacterTemplate targetCT = plugin.getCharacterManager().getCharacter((LivingEntity) event.getEntity());
            int duration = SkillConfigManager.getUseSetting(hero, skill, "shot-duration", 4000, false);
            int slowFall = SkillConfigManager.getUseSetting(hero, skill, "slow-fall-amplifier", 3, false);
            int jumpBoost = SkillConfigManager.getUseSetting(hero, skill, "jump-boost-amplifier", 4, false);
            targetCT.addEffect(new FeatheredEffect(skill, player, duration, slowFall, jumpBoost));
        }
    }

    public class FeatheredEffect extends ExpirableEffect {

        public FeatheredEffect(Skill skill, Player applier, long duration, int slowFallAmplifier, int jumpBoostAmplifier) {
            super(skill, "FeatherShotted", applier, duration);

            types.add(EffectType.DISPELLABLE);
            types.add(EffectType.HARMFUL);

            addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, (int) (duration / 1000 * 20), slowFallAmplifier));
            addPotionEffect(new PotionEffect(PotionEffectType.JUMP, (int) (duration / 1000 * 20), jumpBoostAmplifier));
        }
    }
}
