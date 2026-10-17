package net.unfamily.repae2bridge.ae2.matter;

/**
 * Marks Import/Export Bus transfers so {@code MatterItemsStorage} does not
 * virtual-extract (which would leave cells full and duplicate into the adjacent tank).
 * Crafting CPU extracts stay virtual and must not set this flag.
 */
public final class MatterIoGuard {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private MatterIoGuard() {
    }

    public static boolean isRealIo() {
        return DEPTH.get() > 0;
    }

    public static Scope open() {
        DEPTH.set(DEPTH.get() + 1);
        return Scope.INSTANCE;
    }

    public enum Scope implements AutoCloseable {
        INSTANCE;

        @Override
        public void close() {
            int d = DEPTH.get() - 1;
            if (d <= 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(d);
            }
        }
    }
}
