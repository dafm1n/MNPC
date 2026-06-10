package com.meedix.mnpc.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meedix.mnpc.api.skin.Skin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves player skins (texture + signature) from the Mojang API, fully
 * asynchronously via {@link HttpClient} — no Bukkit scheduler involvement
 * and never any main-thread I/O.
 *
 * <p>Results are cached forever (skin changes of source accounts are rarely
 * relevant for NPCs); in-flight requests are de-duplicated so concurrent
 * callers share one HTTP round-trip.</p>
 */
public final class SkinService {

    private static final String UUID_URL = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_URL =
            "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Cache of lowercase account name -> resolved skin future. */
    private final Map<String, CompletableFuture<Skin>> cache = new ConcurrentHashMap<>();

    /**
     * Resolves the skin of the given Minecraft account.
     *
     * @param playerName the account name
     * @return a cached or fresh future; completes exceptionally with
     *         {@link SkinFetchException} if the account or skin is missing
     */
    public CompletableFuture<Skin> fetchByName(String playerName) {
        return cache.compute(playerName.toLowerCase(), (key, existing) -> {
            if (existing != null && !existing.isCompletedExceptionally()) {
                return existing;
            }
            return requestUuid(playerName).thenCompose(this::requestSkin);
        });
    }

    private CompletableFuture<String> requestUuid(String playerName) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(UUID_URL + playerName))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new SkinFetchException(
                                "Unknown Minecraft account '" + playerName + "' (HTTP " + response.statusCode() + ")");
                    }
                    return JsonParser.parseString(response.body())
                            .getAsJsonObject().get("id").getAsString();
                });
    }

    private CompletableFuture<Skin> requestSkin(String uuid) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_URL.formatted(uuid)))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new SkinFetchException(
                                "Mojang session server returned HTTP " + response.statusCode());
                    }
                    JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonArray properties = profile.getAsJsonArray("properties");
                    for (var element : properties) {
                        JsonObject property = element.getAsJsonObject();
                        if (property.get("name").getAsString().equals("textures")) {
                            return new Skin(
                                    property.get("value").getAsString(),
                                    property.get("signature").getAsString());
                        }
                    }
                    throw new SkinFetchException("Profile " + uuid + " has no textures property");
                });
    }

    /** Thrown when a skin cannot be resolved from the Mojang API. */
    public static final class SkinFetchException extends RuntimeException {

        /** @param message a human-readable failure description */
        public SkinFetchException(String message) {
            super(message);
        }
    }
}
