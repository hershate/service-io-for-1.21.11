package net.thenextlvl.service.plugin.wrapper.service.model;

import net.milkbowl.vault2.chat.Chat;
import net.thenextlvl.service.chat.ChatProfile;
import net.thenextlvl.service.model.MetadataHolder;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class VaultUnlockedChatProfile implements ChatProfile, MetadataHolder {
    private final @Nullable World world;
    private final Chat chat;
    private final OfflinePlayer holder;

    public VaultUnlockedChatProfile(@Nullable final World world, final Chat chat, final OfflinePlayer holder) {
        this.world = world;
        this.chat = chat;
        this.holder = holder;
    }

    @Override
    public Optional<String> getDisplayName() {
        return Optional.empty();
    }

    @Override
    public Optional<String> getName() {
        return Optional.ofNullable(holder.getName());
    }

    @Override
    public Optional<String> getPrefix(final int priority) {
        return Optional.ofNullable(chat.getPlayerPrefix(world != null ? world.getName() : null, holder));
    }

    @Override
    public @Unmodifiable Map<Integer, String> getPrefixes() {
        return getPrefix().map(prefix -> Map.of(0, prefix)).orElseGet(Map::of);
    }

    @Override
    public Optional<String> getPrimaryGroup() {
        return Optional.ofNullable(chat.getPrimaryGroup(world != null ? world.getName() : null, holder));
    }

    @Override
    public Optional<World> getWorld() {
        return Optional.ofNullable(world);
    }

    @Override
    public Optional<String> getSuffix(final int priority) {
        return Optional.ofNullable(chat.getPlayerSuffix(world != null ? world.getName() : null, holder));
    }

    @Override
    public @Unmodifiable Map<Integer, String> getSuffixes() {
        return getSuffix().map(suffix -> Map.of(0, suffix)).orElseGet(Map::of);
    }

    @Override
    public @Unmodifiable Set<String> getGroups() {
        // getPlayerGroups may report a group more than once (e.g. diamond inheritance); Set.of
        // throws on duplicates, so collect into a set that tolerates them instead.
        return Arrays.stream(chat.getPlayerGroups(world != null ? world.getName() : null, holder))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public boolean setDisplayName(@Nullable final String displayName) {
        return false;
    }

    @Override
    public boolean setPrefix(@Nullable final String prefix, final int priority) {
        chat.setPlayerPrefix(world != null ? world.getName() : null, holder, prefix);
        return true;
    }

    @Override
    public boolean setSuffix(@Nullable final String suffix, final int priority) {
        chat.setPlayerSuffix(world != null ? world.getName() : null, holder, suffix);
        return true;
    }

    @Override
    public <T> Optional<T> getInfoNode(final String key, final Function<@Nullable String, @Nullable T> mapper) {
        return Optional.ofNullable(chat.getPlayerInfoString(
                world != null ? world.getName() : null, holder, key, null
        )).map(mapper);
    }

    @Override
    public boolean removeInfoNode(final String key) {
        chat.setPlayerInfoString(world != null ? world.getName() : null, holder, key, null);
        return true;
    }

    @Override
    public boolean setInfoNode(final String key, final String value) {
        chat.setPlayerInfoString(world != null ? world.getName() : null, holder, key, value);
        return true;
    }
}
