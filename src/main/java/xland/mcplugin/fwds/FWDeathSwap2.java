package xland.mcplugin.fwds;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

public final class FWDeathSwap2 extends JavaPlugin {
    private final Config config = new Config();
    private final I18n i18n = new I18n();
    private @Nullable DSGameManager gameManager;
    private final @NotNull LinkedHashSet<Player> candidatePlayers = new LinkedHashSet<>();

    private final ThreadLocal<RandomGenerator> random = ThreadLocal.withInitial(Random::new);

    private void initConfig() {
        config.loadFrom(getConfig());
//        config.saveTo(getConfig());
        saveDefaultConfig();

        String fnLanguage = "language/" + config.getLanguage() + ".yml";
        saveResource(fnLanguage, false);
        if (!"language/en.yml".equals(fnLanguage))
            saveResource("language/en.yml", false);

        i18n.loadFrom(
                getTextResource(fnLanguage),
                "language/en.yml".equals(fnLanguage) ? null : getTextResource("language/en.yml")
        );
    }

    private void removeManager() {
        this.gameManager = null;
    }

    private final class TheListener implements org.bukkit.event.Listener {
        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            DSGameManager manager = gameManager;
            if (manager != null) {
                if (manager.isPlayerLoseOnQuit()) {
                    manager.onPlayerGameOver(event.getPlayer(), i18n, FWDeathSwap2.this::removeManager);
                }
            }
        }

        @EventHandler
        public void onPlayerDeath(PlayerDeathEvent event) {
            DSGameManager manager = gameManager;
            if (manager != null) {
                manager.onPlayerGameOver(event.getPlayer(), i18n, FWDeathSwap2.this::removeManager);
            }
        }

        @EventHandler
        public void onAttack(PrePlayerAttackEntityEvent event) {
            DSGameManager manager = gameManager;
            if (manager != null && manager.cancelAttack(event.getPlayer(), event.getAttacked())) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onPlayerPortal(PlayerPortalEvent event) {
            DSGameManager manager = gameManager;
            if (manager != null && manager.disallowsPortal(event.getPlayer())) {
                event.setCancelled(true);
            }
        }
    }

    private void registerEvents() {
        this.getServer().getPluginManager().registerEvents(this.new TheListener(), this);

        this.getServer().getScheduler().runTaskTimer(this, task -> {
            DSGameManager manager = this.gameManager;
            if (manager != null) {
                manager.tick(i18n);
            }
        }, 1L, 20L);
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        initConfig();
        registerEvents();
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);
    }

    private void registerCommands(ReloadableRegistrarEvent<@NotNull Commands> event) {
        var builder = Commands.literal("deathswap")
                .then(Commands.literal("players")
                        .then(Commands.literal("add")
                                .then(Commands.argument("players", ArgumentTypes.players())
                                        .requires(requirePermission("fwds.command.players"))
                                        .executes(context -> {
                                            final int sizeBefore = this.candidatePlayers.size();
                                            List<Player> players = context.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(context.getSource());
                                            this.candidatePlayers.addAll(players);
                                            final int sizeAfter = this.candidatePlayers.size();

                                            context.getSource().getSender().sendMessage(i18n.get("commands.players.add", sizeAfter - sizeBefore));
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("players", ArgumentTypes.players())
                                        .requires(requirePermission("fwds.command.players"))
                                        .executes(context -> {
                                            final int sizeBefore = this.candidatePlayers.size();
                                            List<Player> players = context.getArgument("players", PlayerSelectorArgumentResolver.class).resolve(context.getSource());
                                            players.forEach(this.candidatePlayers::remove);
                                            final int sizeAfter = this.candidatePlayers.size();

                                            context.getSource().getSender().sendMessage(i18n.get("commands.players.remove", sizeBefore - sizeAfter));
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(Commands.literal("clear")
                                .requires(requirePermission("fwds.command.players"))
                                .executes(context -> {
                                    if (this.candidatePlayers.isEmpty()) {
                                        context.getSource().getSender().sendMessage(i18n.get("commands.players.clear.nought"));
                                        return 0;
                                    } else {
                                        this.candidatePlayers.clear();
                                        context.getSource().getSender().sendMessage(i18n.get("commands.players.clear.success"));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                })
                        )
                        .then(Commands.literal("query")
                                .requires(requirePermission("fwds.command.players.query"))
                                .executes(context -> {
                                    // Depend on whether the game is running
                                    return this.gameManager == null ? queryUpcoming(context) : queryOngoing(context);
                                })
                                .then(Commands.literal("upcoming")
                                        .requires(requirePermission("fwds.command.players.query"))
                                        .executes(this::queryUpcoming)
                                )
                                .then(Commands.literal("ongoing")
                                        .requires(requirePermission("fwds.command.players.query"))
                                        .executes(this::queryOngoing)
                                )
                        )
                )
                .then(Commands.literal("start")
                        .requires(requirePermission("fwds.command.start"))
                        .executes(context -> {
                            if (this.gameManager != null) {
                                context.getSource().getSender().sendMessage(i18n.get("commands.start.fail.started"));
                                return 0;
                            }

                            Collection<Player> candidatePlayers = this.candidatePlayers.stream()
                                    .filter(Player::isOnline)
                                    .toList();

                            if (candidatePlayers.size() < 2) {
                                context.getSource().getSender().sendMessage(i18n.get("commands.start.fail.not-enough-players", candidatePlayers.size()));
                                return 0;
                            }

                            this.gameManager = new DSGameManager(
                                    this.config,
                                    candidatePlayers,
                                    this.random.get()
                            );
                            context.getSource().getSender().sendMessage(i18n.get("commands.start.success"));
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(Commands.literal("stop")
                        .requires(requirePermission("fwds.command.stop"))
                        .executes(context -> {
                            DSGameManager manager = this.gameManager;
                            if (manager == null) {
                                context.getSource().getSender().sendMessage(i18n.get("commands.stop.fail.unstarted"));
                                return 0;
                            }
                            manager.onGameAbort(i18n, this::removeManager);
                            return Command.SINGLE_SUCCESS;
                        })
                );
        event.registrar().register(builder.build(), "Death Swap Game", List.of("fwds", "ds"));
    }

    private static Predicate<CommandSourceStack> requirePermission(String perm) {
        return stack -> stack.getSender().hasPermission(perm);
    }

    private int queryUpcoming(CommandContext<CommandSourceStack> context) {
        int candidatePlayerCount = this.candidatePlayers.size();
        TextColor textColor = switch (candidatePlayerCount) {
            case 0, 1 -> NamedTextColor.RED;
            case 2 -> NamedTextColor.YELLOW;
            default -> NamedTextColor.GREEN;
        };
        Component component = Component.text(builder -> {
            builder.append(MiniMessage.miniMessage().deserialize(
                    i18n.getRaw("commands.players.query.upcoming.header"),
                    Placeholder.component("count", Component.text(candidatePlayerCount, textColor))
            ));
            for (Player player : this.candidatePlayers) {
                builder.appendNewline().append(MiniMessage.miniMessage().deserialize(
                        i18n.getRaw(player.isOnline() ? "commands.players.query.upcoming.online" : "commands.player.query.upcoming.offline"),
                        Placeholder.component("player", player.displayName())
                ));
            }
        });
        context.getSource().getSender().sendMessage(component);
        return Command.SINGLE_SUCCESS;
    }

    private int queryOngoing(CommandContext<CommandSourceStack> context) {
        DSGameManager manager = this.gameManager;
        if (manager == null) {
            context.getSource().getSender().sendMessage(i18n.get("commands.stop.fail.unstarted"));
            return 0;
        }
        Component queried = manager.query(i18n);
        context.getSource().getSender().sendMessage(queried);
        return Command.SINGLE_SUCCESS;
    }
}
