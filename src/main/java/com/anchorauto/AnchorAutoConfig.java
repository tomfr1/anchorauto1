package com.anchorauto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AnchorAutoConfig {
    public static final int MAX_DELAY_TICKS = 40;

    private static final Logger LOGGER = LoggerFactory.getLogger(AnchorAutoMod.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("anchorauto.json");

    private static AnchorAutoConfig instance = new AnchorAutoConfig();

    public int delayPlaceTicks = 2;
    public int delayChargeTicks = 2;
    public int delayExplodeTicks = 2;
    public int doubleGlowstoneChancePercent = 25;

    public static AnchorAutoConfig get() {
        return instance;
    }

    public static void load() {
        if (Files.exists(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE)) {
                AnchorAutoConfig loaded = GSON.fromJson(reader, AnchorAutoConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("Config konnte nicht gelesen werden, nutze Standardwerte", e);
                instance = new AnchorAutoConfig();
            }
        }
        instance.validate();
        save();
    }

    public static void save() {
        instance.validate();
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            LOGGER.error("Config konnte nicht gespeichert werden", e);
        }
    }

    public static void resetToDefaults() {
        instance = new AnchorAutoConfig();
    }

    private void validate() {
        delayPlaceTicks = clamp(delayPlaceTicks, 0, MAX_DELAY_TICKS);
        delayChargeTicks = clamp(delayChargeTicks, 0, MAX_DELAY_TICKS);
        delayExplodeTicks = clamp(delayExplodeTicks, 0, MAX_DELAY_TICKS);
        doubleGlowstoneChancePercent = clamp(doubleGlowstoneChancePercent, 0, 100);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
