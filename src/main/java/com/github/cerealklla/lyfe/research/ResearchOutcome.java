package com.github.cerealklla.lyfe.research;

/**
 * The result of a single research attempt (design doc Section 19.5): how many Research Points to
 * grant, whether the item's durability should be saved/double-damaged, and the Research XP earned.
 */
public record ResearchOutcome(int pointsGranted, boolean criticalFailure, boolean criticalSuccess, boolean durabilitySaved) {
}
