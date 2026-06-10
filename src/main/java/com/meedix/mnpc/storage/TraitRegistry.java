package com.meedix.mnpc.storage;

import com.meedix.mnpc.api.trait.Trait;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps persistent trait ids to factories so traits survive server restarts.
 * Third-party plugins can register their own factories to make custom traits
 * persistent.
 */
public final class TraitRegistry {

    /**
     * Creates and serializes one trait type for persistence.
     */
    public interface TraitFactory {

        /** @return the stable storage id, e.g. {@code "look_at_player"}. */
        String id();

        /** @return the trait class this factory handles. */
        Class<? extends Trait> type();

        /**
         * Recreates a trait from persisted data.
         *
         * @param data the data previously returned by {@link #serialize(Trait)}
         * @return a fresh trait instance
         */
        Trait create(Map<String, Object> data);

        /**
         * Extracts persistable data from a live trait.
         *
         * @param trait the trait to serialize
         * @return arbitrary YAML-friendly key/value data
         */
        Map<String, Object> serialize(Trait trait);
    }

    private final Map<String, TraitFactory> byId = new ConcurrentHashMap<>();
    private final Map<Class<? extends Trait>, TraitFactory> byType = new ConcurrentHashMap<>();

    /**
     * Registers a factory.
     *
     * @param factory the factory to register
     * @throws IllegalArgumentException if the id is already taken
     */
    public void register(TraitFactory factory) {
        if (byId.putIfAbsent(factory.id(), factory) != null) {
            throw new IllegalArgumentException("Duplicate trait factory id: " + factory.id());
        }
        byType.put(factory.type(), factory);
    }

    /** @return the factory with the given storage id. */
    public Optional<TraitFactory> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** @return the factory handling the given trait's class. */
    public Optional<TraitFactory> byTrait(Trait trait) {
        return Optional.ofNullable(byType.get(trait.getClass()));
    }
}
