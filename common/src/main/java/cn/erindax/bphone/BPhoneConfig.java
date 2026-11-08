package cn.erindax.bphone;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.GsonHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BPhoneConfig {
    private static final String FILE_NAME = "bphone.json";
    private static final String DEFAULT_CONTENT = """
            {
              "economy": {
                "builtin": true,
                "starting_balance": 0
              }
            }
            """;

    private static volatile boolean builtinEconomy = true;
    private static volatile long startingBalance;

    private BPhoneConfig() {
    }

    public static synchronized void load() {
        Path path = BPhone.dataDir().toPath().resolve(FILE_NAME);
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, DEFAULT_CONTENT, StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject economy = GsonHelper.getAsJsonObject(root, "economy", new JsonObject());
            builtinEconomy = GsonHelper.getAsBoolean(economy, "builtin", true);
            startingBalance = Math.max(0L, GsonHelper.getAsLong(economy, "starting_balance", 0L));
        } catch (Exception exception) {
            BPhone.LOGGER.error("[BPhone] Failed to read {}, using defaults", path, exception);
        }
    }

    public static boolean builtinEconomy() {
        return builtinEconomy;
    }

    public static long startingBalance() {
        return startingBalance;
    }
}
