package net.unfamily.repae2bridge.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.unfamily.repae2bridge.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Soft Sable detection: plot / sub-level bounds live inside the host {@link Level}.
 * Titanium {@code NetworkManager} also keys by that Level, so Bridge must not register
 * matter elements or fire neighbor updates at plot coordinates (exit/save hard-crash).
 */
public final class SableCompat {

    public static final String MOD_ID = "sable";

    private static final Logger LOGGER = LogManager.getLogger("RepAE2Bridge/SableCompat");

    private static final Object LOCK = new Object();
    private static boolean resolved;
    private static Method getContainer;
    private static Method inBoundsBlockPos;
    private static boolean unavailable;

    /** Throttle plot-hit logs so purge/scan does not flood debug.log. */
    private static long lastPlotHitLogMs;
    private static int suppressedPlotHitLogs;

    private SableCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Flushable breadcrumb for silent-exit diagnosis. Gated by {@link Config#enableSableLogging}.
     */
    public static void breadcrumb(String message) {
        if (!Config.enableSableLogging) {
            return;
        }
        LOGGER.info(message);
        System.out.flush();
        System.err.flush();
    }

    /**
     * @return true when Sable is present and {@code pos} falls inside a sub-level plot region
     *         of {@code level}; false if Sable is absent, API missing, or outside plots.
     */
    public static boolean isInSubLevelPlot(Level level, BlockPos pos) {
        if (level == null || pos == null || !isLoaded()) {
            return false;
        }
        resolve();
        if (unavailable || getContainer == null || inBoundsBlockPos == null) {
            return false;
        }
        try {
            Object container = getContainer.invoke(null, level);
            if (container == null) {
                return false;
            }
            Object result = inBoundsBlockPos.invoke(container, pos);
            boolean inPlot = result instanceof Boolean b && b;
            if (inPlot) {
                logPlotHitThrottled(level, pos);
            }
            return inPlot;
        } catch (Throwable t) {
            if (Config.enableSableLogging) {
                LOGGER.warn("Sable inBounds check failed at {}: {}", pos, t.toString());
            }
            return false;
        }
    }

    private static void logPlotHitThrottled(Level level, BlockPos pos) {
        if (!Config.enableSableLogging) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPlotHitLogMs < 1000L) {
            suppressedPlotHitLogs++;
            return;
        }
        if (suppressedPlotHitLogs > 0) {
            LOGGER.debug("Sable plot hit at {} in {} (+ {} similar hits suppressed)",
                    pos, level.dimension().location(), suppressedPlotHitLogs);
            suppressedPlotHitLogs = 0;
        } else {
            LOGGER.debug("Sable plot hit at {} in {}", pos, level.dimension().location());
        }
        lastPlotHitLogMs = now;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (LOCK) {
            if (resolved) {
                return;
            }
            try {
                Class<?> containerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                getContainer = containerClass.getMethod("getContainer", Level.class);
                inBoundsBlockPos = containerClass.getMethod("inBounds", BlockPos.class);
                unavailable = false;
                if (Config.enableSableLogging) {
                    LOGGER.info("Sable SubLevelContainer API resolved");
                }
            } catch (Throwable t) {
                unavailable = true;
                getContainer = null;
                inBoundsBlockPos = null;
                LOGGER.warn("Sable SubLevelContainer API unavailable: {}", t.toString());
            }
            resolved = true;
        }
    }
}
