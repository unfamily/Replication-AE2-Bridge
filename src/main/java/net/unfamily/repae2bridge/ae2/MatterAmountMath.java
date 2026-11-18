package net.unfamily.repae2bridge.ae2;

/**
 * Saturating helpers for matter cost × craft count (avoids long wrap / double precision loss).
 */
public final class MatterAmountMath {

    /** Safe integer precision limit for double matter amounts. */
    public static final long MAX_SAFE_AMOUNT = 1L << 53;

    private MatterAmountMath() {
    }

    /** {@code ceil(perItem) * count}, saturated to {@link Long#MAX_VALUE}. */
    public static long ceilTimesCount(double perItem, long count) {
        if (count <= 0 || !(perItem > 0) || Double.isNaN(perItem)) {
            return 0L;
        }
        long per = (long) Math.ceil(perItem);
        if (per <= 0) {
            return 0L;
        }
        if (count >= Long.MAX_VALUE / per) {
            return Long.MAX_VALUE;
        }
        return per * count;
    }

    /** Same as {@link #ceilTimesCount(double, long)} but capped for double drain APIs. */
    public static double ceilTimesCountAsDouble(double perItem, long count) {
        long needed = ceilTimesCount(perItem, count);
        return needed >= MAX_SAFE_AMOUNT ? (double) MAX_SAFE_AMOUNT : (double) needed;
    }
}
