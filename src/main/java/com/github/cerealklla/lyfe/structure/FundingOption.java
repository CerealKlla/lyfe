package com.github.cerealklla.lyfe.structure;

/** The three upgrade-funding options (design doc Section 19.9, explicit user spec, 2026-10-05). */
public enum FundingOption {
    /** Use only resources already sitting in a box on the structure's own plot -- fails outright if that's not enough, no purchasing. */
    ON_HAND,
    /** Use on-hand resources first, then buy the rest (local settlement markup, or a neighboring settlement's price + transport fee if nobody local sells it). */
    MIX,
    /** Skip the plot's own boxes entirely -- buy the full cost, same pricing as {@link #MIX}'s purchase step. */
    GOLD_ONLY
}
