package dev.randomised.dispensertest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON config at config/randomised_dispenser_testing.json (created with defaults on first run). */
public final class RdtConfig {

    public enum ValueMode {
        /** value = slot index + 1 (1..9, larger towards the bottom of the grid). */
        SLOT,
        /** value = item stack count. */
        COUNT
    }

    public boolean enabled = true;
    /** Single-player only: push the computed slots into the integrated server's dispenser logic. */
    public boolean applyToIntegratedServer = true;
    public boolean chatFeedback = true;
    public ValueMode valueMode = ValueMode.SLOT;
    /** false: recent >= other. true: recent > other. */
    public boolean strictGreater = false;
    /** 0 = uniform over all valid slot pairs; higher = prefer bigger margins. */
    public double spread = 0.0;
    /** Abstain unless both containers have all 9 slots filled. */
    public boolean requireFullContainers = false;
    /** How long a right-click on a container may wait for its screen to open. */
    public int pendingOpenTimeoutTicks = 40;
    /** How long a computed slot stays armed after the lever is pulled. */
    public long armTtlMillis = 3000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RdtConfig instance = new RdtConfig();

    public static RdtConfig get() {
        return instance;
    }

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("randomised_dispenser_testing.json");
        try {
            if (Files.exists(path)) {
                RdtConfig loaded = GSON.fromJson(Files.readString(path), RdtConfig.class);
                if (loaded != null) instance = loaded;
            }
            instance.sanitise();
            Files.writeString(path, GSON.toJson(instance)); // writes defaults / adds new keys
        } catch (IOException | RuntimeException e) {
            RandomisedDispenserClient.LOGGER.warn("Could not read config, using defaults", e);
            instance = new RdtConfig();
        }
    }

    private void sanitise() {
        if (valueMode == null) valueMode = ValueMode.SLOT;
        spread = Math.max(0.0, Math.min(4.0, spread));
        pendingOpenTimeoutTicks = Math.max(5, pendingOpenTimeoutTicks);
        armTtlMillis = Math.max(500, armTtlMillis);
    }
}
