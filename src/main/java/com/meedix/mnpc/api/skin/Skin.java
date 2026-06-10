package com.meedix.mnpc.api.skin;

import java.util.Objects;

/**
 * An immutable Mojang skin: the Base64 texture payload and its Yggdrasil
 * signature. The signature is required for skins to render on offline
 * (texture-signed) clients, so MNPC always stores both.
 *
 * @param texture   Base64-encoded texture property value
 * @param signature Base64-encoded signature of the texture value
 */
public record Skin(String texture, String signature) {

    /**
     * @throws NullPointerException if texture or signature is null
     */
    public Skin {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(signature, "signature");
    }
}
