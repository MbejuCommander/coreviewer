package dev.coreviewer.view;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.*;

/** Vanilla's asynchronous profile/texture cache supplies current skins, never historical skins. */
public final class PlayerAppearance {
    private static final Map<String, ResolvableProfile> profiles = new LinkedHashMap<>();

    public static ResolvableProfile profile(String name, UUID uuid) {
        var mc = Minecraft.getInstance();
        var connection = mc.getConnection();
        var info =
                connection == null
                        ? null
                        : uuid == null
                                ? connection.getPlayerInfo(name)
                                : connection.getPlayerInfo(uuid);
        if (info != null) return ResolvableProfile.createResolved(info.getProfile());
        String key = uuid == null ? name : uuid.toString();
        var existing = profiles.get(key);
        if (existing != null) return existing;
        var value =
                uuid != null
                        ? ResolvableProfile.createUnresolved(uuid)
                        : name != null && name.matches("[A-Za-z0-9_]{1,16}")
                                ? ResolvableProfile.createUnresolved(name)
                                : ResolvableProfile.createResolved(
                                        new GameProfile(new UUID(0, 0), "Unknown"));
        if (profiles.size() >= 256) profiles.remove(profiles.keySet().iterator().next());
        profiles.put(key, value);
        return value;
    }

    public static RemotePlayer hologram(String name, UUID uuid) {
        String display = name == null || name.isBlank() ? "Unknown" : name;
        var profile = profile(display, uuid);
        return new RemotePlayer(
                Minecraft.getInstance().level,
                new GameProfile(uuid == null ? new UUID(0, 0) : uuid, display)) {
            @Override
            public PlayerSkin getSkin() {
                return Minecraft.getInstance()
                        .playerSkinRenderCache()
                        .getOrDefault(profile)
                        .playerSkin();
            }
        };
    }
}
