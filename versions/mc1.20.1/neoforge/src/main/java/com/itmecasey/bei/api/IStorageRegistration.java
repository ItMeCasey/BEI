package com.itmecasey.bei.api;

import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;

/** Where a plugin registers its storage sources. */
public interface IStorageRegistration {
    /** Adds a storage source; one with the same id replaces it. */
    void register(IStorageSource source);

    /** Adds a storage source made of an id and a counting function. */
    default void register(String id, BiConsumer<Player, IItemCounter> counter) {
        register(new IStorageSource() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void count(Player player, IItemCounter out) {
                counter.accept(player, out);
            }
        });
    }
}
