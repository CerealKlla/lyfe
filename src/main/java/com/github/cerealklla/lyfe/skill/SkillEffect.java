package com.github.cerealklla.lyfe.skill;

/**
 * A single (level threshold -> effect) pair (design doc Section 4). Effects stack from every
 * threshold at or below the player's current level, not just the highest one reached.
 */
public record SkillEffect(int levelThreshold, EffectType type, double magnitude) {
}
