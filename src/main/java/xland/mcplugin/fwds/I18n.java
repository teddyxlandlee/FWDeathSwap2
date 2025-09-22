package xland.mcplugin.fwds;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.Objects;

public final class I18n {
    private @NotNull Configuration config = new MemoryConfiguration();
    private @Nullable Configuration defaultConfig;

    public void loadFrom(Reader r1, @Nullable Reader r2) {
        try (r1; r2) {
            config = YamlConfiguration.loadConfiguration(r1);
            if (r2 != null)
                defaultConfig = YamlConfiguration.loadConfiguration(r2);
        } catch (Exception ignore) {
        }
    }

    public @NotNull String getRaw(@NotNull String key) {
        Objects.requireNonNull(key, "key");

        String s = config.getString(key);
        if (s == null && defaultConfig != null) {
            s = defaultConfig.getString(key);
        }
        if (s == null) {
            return key;
        }

        return s;
    }

    public @NotNull String getRaw(@NotNull String key, Object @NotNull... args) {
        Objects.requireNonNull(args, "args");

        if (args.length == 0) return getRaw(key);
        return String.format(getRaw(key), args);
    }

    public @NotNull Component get(@NotNull String key) {
        return MiniMessage.miniMessage().deserialize(getRaw(key));
    }

    public @NotNull Component get(@NotNull String key, Object @NotNull... args) {
        return MiniMessage.miniMessage().deserialize(getRaw(key, args));
    }


}
