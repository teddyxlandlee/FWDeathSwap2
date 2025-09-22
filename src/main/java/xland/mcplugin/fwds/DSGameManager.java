package xland.mcplugin.fwds;

import com.google.common.base.Preconditions;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.random.RandomGenerator;

public final class DSGameManager implements Tickable {
    private final Config config;

    private final SequencedSet<Player> players;
    private final RandomGenerator random;

    private @NotNull StageRunner stageRunner;

    private final Audience forwardingAudience;
    private final Player[] allPlayers;

    private static final Player[] EMPTY_PLAYER_ARRAY = {};

    public DSGameManager(@NotNull Config config, @NotNull Collection<Player> players, @NotNull RandomGenerator random) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(random, "random");

        Preconditions.checkArgument(players.size() >= 2, "The game requires at least 2 players");

        this.config = config.clone();

        this.players = new LinkedHashSet<>(players);
        this.random = random;

        this.stageRunner = this.new SafeStage();

        // toArray to make sure immutability of ForwardingAudience#audiences()
        this.allPlayers = this.players.toArray(EMPTY_PLAYER_ARRAY);
        this.forwardingAudience = Audience.audience(this.allPlayers);
    }

    void deathSwap() {
        SequencedSet<Player> downstreamPlayers = this.players;
        List<@NotNull Location> downstreamLocations = downstreamPlayers.stream().map(Player::getLocation).toList();

        Player[] upstreamPlayers = downstreamPlayers.toArray(EMPTY_PLAYER_ARRAY);
        Shuffling.shuffle(upstreamPlayers, this.random);

        // Teleport
        for (int i = upstreamPlayers.length - 1; i >= 0; i--) {
            upstreamPlayers[i].teleport(downstreamLocations.get(i));
        }

        // DealEnderPearl
        switch (config.getDealEnderPearl()) {
            case NO_OP -> {}    // Do nothing
            case CHANGE_OWNER -> {
                // downstream.pearls foreach: setOwner(upstream)
                int idx = 0;
                for (Player downstreamPlayer : downstreamPlayers) {
                    Player upstreamPlayer = upstreamPlayers[idx++];
                    for (EnderPearl pearl : downstreamPlayer.getEnderPearls()) {
                        pearl.setShooter(upstreamPlayer);
                    }
                }
            }
            case KILL_EXISTING_PEARLS -> {
                for (Player player : downstreamPlayers) {
                    for (EnderPearl pearl : player.getEnderPearls()) {
                        pearl.remove();
                    }
                }
            }
        }
    }

    public void onPlayerGameOver(@NotNull Player player, @NotNull I18n i18n, @NotNull Runnable removeCallback) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(i18n, "i18n");

        if (!this.players.remove(player)) return;

        switch (this.players.size()) {
            case 0 -> forwardingAudience.sendMessage(i18n.get("end.unexpected"));
            case 1 -> forwardingAudience.sendMessage(MiniMessage.miniMessage().deserialize(
                    i18n.getRaw("end.winner"),
                    Placeholder.component("player", this.players.getFirst().displayName())
            ));
            default -> { return; }
        }
        removeCallback.run();
    }

    public Component query(@NotNull I18n i18n) {
        return Component.text(builder -> {
            builder.append(i18n.get("commands.players.query.ongoing.header", players.size(), allPlayers.length));
            for (Player player : allPlayers) {
                builder.appendNewline().append(MiniMessage.miniMessage().deserialize(
                        i18n.getRaw(players.contains(player) ? "commands.players.query.ongoing.alive" : "commands.players.query.ongoing.lose"),
                        Placeholder.component("player", player.displayName())
                ));
            }
        });
    }

    public boolean isPlayerLoseOnQuit() {
        return this.config.isLoseOnQuit();
    }

    private boolean isAlivePlayer(Entity entity) {
        return entity instanceof Player && this.players.contains(entity);
    }

    public boolean cancelAttack(Entity attacker, Entity attacked) {
        if (this.config.isAllowPvp()) return false;
        return isAlivePlayer(attacker) && isAlivePlayer(attacked);
    }

    public boolean disallowsPortal(Player player) {
        if (!isAlivePlayer(player)) return false;    // do not block them
        return !this.config.isAllowPortal();    // for alive player: disallow when disallowed
    }

    public void onGameAbort(@NotNull I18n i18n, @NotNull Runnable removeCallback) {
        Objects.requireNonNull(i18n, "i18n");
        forwardingAudience.sendMessage(i18n.get("end.aborted"));
        removeCallback.run();
    }

    private final class SafeStage extends ReportableStageRunner {
        SafeStage() {
            super("stages.safe", DSGameManager.this.config.getSafeTime().select(DSGameManager.this.random));
        }

        @Override
        void onTickEnds(I18n i18n) {
            DSGameManager.this.stageRunner = DSGameManager.this.new UnsafeStage();
        }
    }

    private final class UnsafeStage extends ReportableStageRunner {
        UnsafeStage() {
            super("stages.unsafe", DSGameManager.this.config.getUnsafeTime().select(DSGameManager.this.random));
        }

        @Override
        void onTickEnds(I18n i18n) {
            DSGameManager.this.stageRunner = DSGameManager.this.new CountdownStage(maxTick);
        }
    }

    private final class CountdownStage extends ReportableStageRunner {
        private final int prevMaxTicks;

        CountdownStage(int prevMaxTicks) {
            super("stages.unsafe", DSGameManager.this.config.getCountdownTime().select(DSGameManager.this.random));
            this.prevMaxTicks = prevMaxTicks;
        }

        @Override
        void reportTick(int tick, I18n i18n) {
            super.reportTick(prevMaxTicks + tick, i18n);
            int remainingTicks = maxTick - tick;
            if (remainingTicks > 0) {
                final Component component = i18n.get("swap.countdown", remainingTicks);
                DSGameManager.this.players.forEach(p -> p.sendMessage(component));
            }
        }

        @Override
        void onTickEnds(I18n i18n) {
            final Component component = i18n.get("swap.now");
            DSGameManager.this.players.forEach(p -> p.sendMessage(component));

            DSGameManager.this.deathSwap();
            // Resume
            DSGameManager.this.stageRunner = DSGameManager.this.new SafeStage();
        }
    }

    private abstract class ReportableStageRunner extends StageRunner {
        private final String reportKey;

        ReportableStageRunner(String reportKey, int maxTick) {
            super(maxTick);
            this.reportKey = reportKey;
        }

        @Override
        void reportTick(int tick, I18n i18n) {
            final Component component = i18n.get(reportKey, tick);

            DSGameManager.this.players.forEach(p -> p.sendActionBar(component));
        }
    }

    @Override
    public void tick(I18n i18n) {
        this.stageRunner.tick(i18n);
    }
}
