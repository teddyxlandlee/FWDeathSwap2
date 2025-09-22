package xland.mcplugin.fwds;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.random.RandomGenerator;

public interface Shuffling {
    // Use SatolloShuffle algorithm
    static void shuffle(@NotNull Object[] arr, @NotNull RandomGenerator random) {
        Objects.requireNonNull(arr, "The array must not be null");
        Objects.requireNonNull(random, "The random generator must not be null");

        switch (arr.length) {
            case 0, 1 -> {}    // not shufflable
            case 2 -> swap(arr, 0, 1);
            default -> {
                for (int i = arr.length - 1; i > 0; i--) {
                    swap(arr, i, random.nextInt(i));
                }
            }
        }
    }

    private static void swap(Object[] arr, int i, int j) {
        Object obj = arr[i];
        arr[i] = arr[j];
        arr[j] = obj;
    }
}
