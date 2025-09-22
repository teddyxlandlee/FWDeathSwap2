package xland.mcplugin.fwds;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

public record IntRange(int from, int to) {
    public IntRange {
        if (from > to) {
            int tmp = from;
            from = to;
            to = tmp;
        }
    }

    public IntRange(int exact) {
        this(exact, exact);
    }

    Object asObject() {
        int from = from(), to = to();
        if (from == to) return from;
        return new ArrayList<>(List.of(from, to));
    }

    @Override
    public @NotNull String toString() {
        return asObject().toString();
    }

    public int select(RandomGenerator random) {
        int from = from(), to = to();
        if (from == to) return from;
        return random.nextInt(from, to);
    }
}
