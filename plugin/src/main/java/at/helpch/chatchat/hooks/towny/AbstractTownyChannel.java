package at.helpch.chatchat.hooks.towny;

import at.helpch.chatchat.ChatChatPlugin;
import at.helpch.chatchat.api.holder.FormatsHolder;
import at.helpch.chatchat.api.user.ChatUser;
import at.helpch.chatchat.api.user.User;
import at.helpch.chatchat.channel.AbstractChannel;
import at.helpch.chatchat.user.UsersHolderImpl;
import at.helpch.chatchat.util.ChannelUtils;
import com.palmergames.bukkit.towny.TownyUniverse;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.ResidentList;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractTownyChannel extends AbstractChannel {

    protected AbstractTownyChannel(@NotNull final String name,
                                   @NotNull final String messagePrefix,
                                   @NotNull final List<String> toggleCommands,
                                   @NotNull final String channelPrefix,
                                   @NotNull final FormatsHolder formats,
                                   final int radius) {
        super(name, messagePrefix, toggleCommands, channelPrefix, formats, radius);
        if (Bukkit.getPluginManager().getPlugin("Towny") == null) {
            throw new RuntimeException("Attempting to use a Towny channel but Towny is not installed.");
        }
    }

    private Optional<ResidentList> residentList(@NotNull final UUID uuid) {
        return TownyUniverse.getInstance().getResidentOpt(uuid).map(this::residentList);
    }

    @Override
    public boolean isUsableBy(@NotNull final ChatUser user) {
        return super.isUsableBy(user) && residentList(user.uuid()).isPresent();
    }

    protected abstract @Nullable ResidentList residentList(@NotNull final Resident resident);

    private final ChatChatPlugin plugin = ChatChatPlugin.getPlugin(ChatChatPlugin.class);

    @Override
    public Set<User> targets(final @NotNull User source) {
        Optional<ResidentList> residentList = residentList(source.uuid());
        if (residentList.isEmpty()) return Set.of();

        // Start to construct the target list
        Set<User> set = new HashSet<>();
        UsersHolderImpl users = plugin.usersHolder();
        for (Resident resident : residentList.get().getResidents()) {

            // Caution: towns/nations may contain NPC residents whose UUID does not map to a real player.
            // This leads to ChatChat creating broken User instances when calling UserHolder#getUser(UUID).
            if (resident.isNPC() || !resident.isOnline()) continue;

            UUID uuid = resident.getUUID();
            User target = users.getUser(uuid);
            if (ChannelUtils.isTargetWithinRadius(source, target, radius())) {
                set.add(target);
            }
        }

        return set;
    }

}
