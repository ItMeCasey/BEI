package com.itmecasey.bei.api;

import net.minecraft.world.entity.player.Player;

/**
 * A place the Master Blueprint counts items from, besides the player's inventory and (if the setting is on) ender
 * chest: a backpack, a networked storage system. Register one from {@link IBeiPlugin#registerStorageSources}.
 *
 * <p>{@link #count} runs on the client, often several times a second while the planner or recipe popup is open, so
 * read data the client already has and keep it cheap. A source that throws is skipped and its partial counts are
 * dropped; it never breaks the planner or the other sources.
 */
public interface IStorageSource {
    /**
     * A stable id such as {@code mymod:network}. Registering a second source with the same id replaces the first.
     */
    String id();

    /** Reports the items this source holds for {@code player}. */
    void count(Player player, IItemCounter counter);
}
