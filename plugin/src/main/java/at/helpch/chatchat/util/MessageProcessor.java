package at.helpch.chatchat.util;

import at.helpch.chatchat.ChatChatPlugin;
import at.helpch.chatchat.api.channel.Channel;
import at.helpch.chatchat.api.event.ChatChatEvent;
import at.helpch.chatchat.api.user.ChatUser;
import at.helpch.chatchat.api.user.User;
import at.helpch.chatchat.user.ConsoleUser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class MessageProcessor {

    private static final MiniMessage USER_MESSAGE_MINI_MESSAGE = MiniMessage.builder().tags(TagResolver.empty()).build();
    private static final Pattern DEFAULT_URL_PATTERN = Pattern.compile("(?:(https?)://)?([-\\w_.]+\\.\\w{2,})(/\\S*)?");
    private static final Pattern URL_SCHEME_PATTERN = Pattern.compile("^[a-z][a-z\\d+\\-.]*:");

    private static final TextReplacementConfig URL_REPLACER_CONFIG = TextReplacementConfig.builder()
        .match(DEFAULT_URL_PATTERN)
        .replacement(builder -> {
            String clickUrl = builder.content();
            if (!URL_SCHEME_PATTERN.matcher(clickUrl).find()) {
                clickUrl = "https://" + clickUrl;
            }
            return builder.clickEvent(ClickEvent.openUrl(clickUrl));
        })
        .build();

    private static final String URL_PERMISSION = "chatchat.url";
    public static final String TAG_BASE_PERMISSION = "chatchat.tag.";
    private static final String ITEM_TAG_PERMISSION = TAG_BASE_PERMISSION + "item";

    private static final Map<String, TagResolver> PERMISSION_TAGS = Map.ofEntries(
        Map.entry("click", StandardTags.clickEvent()),
        Map.entry("color", StandardTags.color()),
        Map.entry("font", StandardTags.font()),
        Map.entry("gradient", StandardTags.gradient()),
        Map.entry("hover", StandardTags.hoverEvent()),
        Map.entry("insertion", StandardTags.insertion()),
        Map.entry("keybind", StandardTags.keybind()),
        Map.entry("newline", StandardTags.newline()),
        Map.entry("rainbow", StandardTags.rainbow()),
        Map.entry("reset", StandardTags.reset()),
        Map.entry("translatable", StandardTags.translatable())
    );

    private MessageProcessor() {
        throw new AssertionError("Util classes are not to be instantiated!");
    }

    /**
     * Processes the message, including:
     * <ul>
     *     <li>fire an {@link ChatChatEvent} and check cancel state</li>
     *     <li>decorate the message if it contains mentions</li>
     *     <li>broadcast the message to the channel</li>
     *     <li>......</li>
     * </ul>
     * <p>
     * This method will not process (i.e. send) the message to the Console.
     *
     * @param plugin     the ChatChat plugin instance
     * @param sourceUser the user who sends the message
     * @param channel    the channel to which the message being sent
     * @param message    the message (which is cleansed)
     * @param async      whether it's async or not
     *
     * @return true if this is allowed to be sent; otherwise false
     */
    public static boolean process(
        @NotNull final ChatChatPlugin plugin,
        @NotNull final ChatUser sourceUser,
        @NotNull final Channel channel,
        @NotNull final String message,
        final boolean async
    ) {
        final var isMuted = plugin.hookManager()
            .muteHooks()
            .stream()
            .filter(Objects::nonNull)
            .anyMatch(hook -> hook.isMuted(sourceUser));

        if (isMuted) {
            return false;
        }

        final var rulesResult = plugin.ruleManager().isAllowedPublicChat(sourceUser, message);
        if (rulesResult.isPresent()) {
            sourceUser.sendMessage(rulesResult.get());
            return false;
        }

        final var chatEvent = new ChatChatEvent(
            async,
            sourceUser,
            FormatUtils.findFormat(
                sourceUser.player(),
                channel,
                plugin.configManager().formats(),
                plugin.configManager().extensions().addons().deluxeChatInversePriorities()),
            // TODO: 9/2/22 Process message for each recipient to add rel support inside the message itself.
            //  Possibly even pass the minimessage string here instead of the processed component.
            MessageProcessor.processMessage(plugin, sourceUser, ConsoleUser.INSTANCE, message),
            channel,
            channel.targets(sourceUser)
        );

        plugin.getServer().getPluginManager().callEvent(chatEvent);

        if (chatEvent.isCancelled()) {
            return false;
        }

        final var oldChannel = sourceUser.channel();
        sourceUser.channel(channel);

        final var parsedMessage = chatEvent.message().compact();
        final var mentions = plugin.configManager().settings().mentions();

        var userMessage = parsedMessage;
        var userIsTarget = false;

        for (final var targetUser : chatEvent.recipients()) {
            if (targetUser.uuid() == sourceUser.uuid()) {
                userIsTarget = true;
                continue;
            }

            // Console Users have their own format we set in ChatListener.java
            if (targetUser instanceof ConsoleUser) continue;

            // Process mentions and get the result.
            final var mentionResult = plugin.mentionsManager().processMentions(
                async,
                sourceUser,
                targetUser,
                chatEvent.channel(),
                parsedMessage,
                true
            );

            if (targetUser instanceof final ChatUser chatTarget) {

                final var component = FormatUtils.parseFormat(
                    chatEvent.format(),
                    sourceUser.player(),
                    chatTarget.player(),
                    mentionResult.message(),
                    plugin.miniPlaceholdersManager().compileTags(false, sourceUser, targetUser)
                );

                targetUser.sendMessage(component); // Send message to this recipient
                if (mentionResult.playSound()) {
                    targetUser.playSound(mentions.sound());
                }
                if (sourceUser.canSee(chatTarget)) {
                    userMessage = plugin.mentionsManager().processMentions(
                        async,
                        sourceUser,
                        chatTarget,
                        chatEvent.channel(),
                        userMessage,
                        false
                    ).message();
                }
                continue;
            }

            final var component = FormatUtils.parseFormat(
                chatEvent.format(),
                sourceUser.player(),
                mentionResult.message(),
                plugin.miniPlaceholdersManager().compileTags(false, sourceUser, targetUser)
            );

            targetUser.sendMessage(component); // Send message to this recipient
            if (mentionResult.playSound()) {
                targetUser.playSound(mentions.sound());
            }
        }

        if (!userIsTarget) {
            sourceUser.channel(oldChannel);
            return true;
        }

        final var mentionResult = plugin.mentionsManager().processMentions(
            async,
            sourceUser,
            sourceUser,
            chatEvent.channel(),
            parsedMessage,
            true
        );

        final var component = FormatUtils.parseFormat(
            chatEvent.format(),
            sourceUser.player(),
            sourceUser.player(),
            mentionResult.message(),
            plugin.miniPlaceholdersManager().compileTags(false, sourceUser, sourceUser)
        );

        sourceUser.sendMessage(component); // Send message to the sender itself
        if (mentionResult.playSound()) {
            sourceUser.playSound(mentions.sound());
        }

        sourceUser.channel(oldChannel);
        return true;
    }

    /**
     * Deserializes the raw message into {@link Component}.
     * <p>
     * The given message string is typically in MiniMessage representation.
     * <p>
     * This method will parse the tags depending on what permissions the user has.
     *
     * @param plugin    the ChatChat plugin instance
     * @param user      the user who sends the message
     * @param recipient the user who receives the message
     * @param message   the message
     *
     * @return a component deserialized from the given params
     */
    public static @NotNull Component processMessage(
        @NotNull final ChatChatPlugin plugin,
        @NotNull final ChatUser user,
        @NotNull final User recipient,
        @NotNull final String message
    ) {
        final var resolver = TagResolver.builder();

        for (final var entry : PERMISSION_TAGS.entrySet()) {
            if (!user.hasPermission(TAG_BASE_PERMISSION + entry.getKey())) {
                continue;
            }

            resolver.resolver(entry.getValue());
        }

        for (final var tag : TextDecoration.values()) {
            if (!user.hasPermission(TAG_BASE_PERMISSION + tag.toString())) {
                continue;
            }

            resolver.resolver(StandardTags.decorations(tag));
        }

        if (user.hasPermission(ITEM_TAG_PERMISSION)) {
            resolver.resolver(Placeholder.component("item", user.player().getInventory().getItemInMainHand().displayName()));
        }

        resolver.resolvers(plugin.miniPlaceholdersManager().compileTags(true, user, recipient));

        return !user.hasPermission(URL_PERMISSION)
            ? USER_MESSAGE_MINI_MESSAGE.deserialize(message, resolver.build())
            : USER_MESSAGE_MINI_MESSAGE.deserialize(message, resolver.build()).replaceText(URL_REPLACER_CONFIG);
    }

}
