package xland.mcplugin.fwds;

import org.bukkit.configuration.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class Config implements Cloneable {
    private @NotNull String language = "en";
    private @NotNull IntRange safeTime = new IntRange(30, 120);
    private @NotNull IntRange unsafeTime = new IntRange(20, 80);
    private @NotNull IntRange countdownTime = new IntRange(10);
    private boolean allowPortal = true;
    private boolean allowPvp = true;
    private boolean loseOnQuit = true;
    private @NotNull DealEnderPearl dealEnderPearl = DealEnderPearl.DEFAULT;

    public void saveTo(Configuration conf) {
        conf.set("language", getLanguage());
        conf.set("safe-time", getSafeTime().asObject());
        conf.set("unsafe-time", getUnsafeTime().asObject());
        conf.set("countdown-time", getCountdownTime().asObject());
        conf.set("allow-portal", isAllowPortal());
        conf.set("allow-pvp", isAllowPvp());
        conf.set("lose-on-quit", isLoseOnQuit());
        conf.set("deal-ender-pearl", getDealEnderPearl().toString());
    }

    public void loadFrom(Configuration conf) {
        setLanguage(conf.getString("language", getLanguage()));
        setSafeTime(getIntRange(conf, "safe-time", getSafeTime()));
        setUnsafeTime(getIntRange(conf, "unsafe-time", getUnsafeTime()));
        setCountdownTime(getIntRange(conf, "countdown-time", getCountdownTime()));
        setAllowPortal(conf.getBoolean("allow-portal", isAllowPortal()));
        setAllowPvp(conf.getBoolean("allow-pvp", isAllowPvp()));
        setLoseOnQuit(conf.getBoolean("lose-on-quit", isLoseOnQuit()));
        setDealEnderPearl(DealEnderPearl.fromString(conf.getString("deal-ender-pearl", getDealEnderPearl().toString())));
    }

    public enum DealEnderPearl {
        NO_OP("no-op", "0", "noop", "none"),
        CHANGE_OWNER("change-owner", "1", "swap"),   // to upstream player
        KILL_EXISTING_PEARLS("kill-existing-pearls", "-1", "kill")
        ;
        private final String id;
        private final Collection<String> aliases;
        static final DealEnderPearl DEFAULT = KILL_EXISTING_PEARLS;

        DealEnderPearl(String id, String... aliases) {
            this.id = id;
            this.aliases = Arrays.asList(aliases);
        }

        public static @NotNull DealEnderPearl fromString(@Nullable String value) {
            if (value == null) return DEFAULT;
            for (DealEnderPearl each : DealEnderPearl.values()) {
                if (value.equals(each.id) || each.aliases.contains(value))
                    return each;
            }
            return DEFAULT;
        }

        @Override
        public String toString() {
            return id;
        }
    }

    private static @NotNull IntRange getIntRange(Configuration conf, String key, @NotNull IntRange defaultValue) {
        return Objects.requireNonNullElse(getIntRange(conf, key), defaultValue);
    }

    private static @Nullable IntRange getIntRange(Configuration conf, String key) {
        if (conf.isInt(key)) {
            return new IntRange(conf.getInt(key));
        }

        List<Integer> list = conf.getIntegerList(key);
        return switch (list.size()) {
            case 1 -> new IntRange(list.getFirst());
            case 2 -> new IntRange(list.getFirst(), list.getLast());
            default -> null;
        };
    }

    public @NotNull String getLanguage() {
        return language;
    }

    public void setLanguage(@NotNull String language) {
        Objects.requireNonNull(language, "language");
        this.language = language;
    }

    public @NotNull IntRange getSafeTime() {
        return safeTime;
    }

    public void setSafeTime(@NotNull IntRange safeTime) {
        Objects.requireNonNull(safeTime, "safeTime");
        this.safeTime = safeTime;
    }

    public @NotNull IntRange getUnsafeTime() {
        return unsafeTime;
    }

    public void setUnsafeTime(@NotNull IntRange unsafeTime) {
        Objects.requireNonNull(unsafeTime, "unsafeTime");
        this.unsafeTime = unsafeTime;
    }

    public @NotNull IntRange getCountdownTime() {
        return countdownTime;
    }

    public void setCountdownTime(@NotNull IntRange countdownTime) {
        Objects.requireNonNull(countdownTime, "countdownTime");
        this.countdownTime = countdownTime;
    }

    public boolean isAllowPortal() {
        return allowPortal;
    }

    public void setAllowPortal(boolean allowPortal) {
        this.allowPortal = allowPortal;
    }

    public boolean isAllowPvp() {
        return allowPvp;
    }

    public void setAllowPvp(boolean allowPvp) {
        this.allowPvp = allowPvp;
    }

    public boolean isLoseOnQuit() {
        return loseOnQuit;
    }

    public void setLoseOnQuit(boolean loseOnQuit) {
        this.loseOnQuit = loseOnQuit;
    }

    public @NotNull DealEnderPearl getDealEnderPearl() {
        return dealEnderPearl;
    }

    public void setDealEnderPearl(@NotNull DealEnderPearl dealEnderPearl) {
        Objects.requireNonNull(dealEnderPearl, "dealEnderPearl");
        this.dealEnderPearl = dealEnderPearl;
    }

    @Override
    public Config clone() {
        try {
            return (Config) super.clone();
        } catch (Exception e) {
            throw new AssertionError("Shall never happen", e);
        }
    }
}
