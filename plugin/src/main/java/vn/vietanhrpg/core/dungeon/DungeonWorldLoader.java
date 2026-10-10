package vn.vietanhrpg.core.dungeon;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

/**
 * Paper 26.1+ stores API-created custom worlds as dimensions inside the
 * primary level folder. Dungeon maps downloaded as standalone vanilla worlds
 * cannot reliably be loaded with the legacy WorldCreator(String) path because
 * that path triggers Paper's legacy migration and can collide with partially
 * migrated files.
 *
 * This loader imports only the map's overworld dimension into a dedicated
 * VanillaRPG namespace and then loads it using the modern keyed WorldCreator.
 */
public final class DungeonWorldLoader {
    private static final String IMPORT_MARKER = ".vanillarpg-import-v2";

    private final JavaPlugin plugin;

    public DungeonWorldLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public World load(String sourceFolderName) {
        if (sourceFolderName == null || sourceFolderName.isBlank()) return null;

        String keyValue = sanitizeKey(sourceFolderName);
        NamespacedKey key = new NamespacedKey(plugin, keyValue);

        World loaded = Bukkit.getWorld(key);
        if (loaded != null) return loaded;

        Path sourceRoot = Bukkit.getWorldContainer().toPath().resolve(sourceFolderName).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) return null;

        try {
            Path targetDimension = targetDimensionPath(key);
            importMapIfNeeded(sourceRoot, targetDimension);

            WorldCreator creator = WorldCreator.ofKey(key)
                    .environment(World.Environment.NORMAL)
                    .generateStructures(false);

            World world = creator.createWorld();
            if (world == null) {
                plugin.getLogger().warning("Paper returned null while loading dungeon dimension '" + key + "'.");
            }
            return world;
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not import/load dungeon world '" + sourceFolderName + "': "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return null;
        }
    }

    public NamespacedKey keyFor(String sourceFolderName) {
        return new NamespacedKey(plugin, sanitizeKey(sourceFolderName));
    }

    private void importMapIfNeeded(Path sourceRoot, Path targetDimension) throws IOException {
        Path marker = targetDimension.resolve(IMPORT_MARKER);
        if (Files.isRegularFile(marker)) return;

        Path modernOverworld = sourceRoot.resolve("dimensions").resolve("minecraft").resolve("overworld");
        if (Files.isDirectory(modernOverworld)) {
            plugin.getLogger().info("Importing dungeon map dimension from " + modernOverworld + " to " + targetDimension);
            copyTree(modernOverworld, targetDimension);
        } else if (Files.isDirectory(sourceRoot.resolve("region"))) {
            plugin.getLogger().info("Importing legacy dungeon map from " + sourceRoot + " to " + targetDimension);
            for (String child : new String[]{"region", "entities", "poi", "data"}) {
                Path sourceChild = sourceRoot.resolve(child);
                if (Files.exists(sourceChild)) {
                    copyTree(sourceChild, targetDimension.resolve(child));
                }
            }
        } else {
            throw new IOException("No overworld chunk data found. Expected dimensions/minecraft/overworld or region in " + sourceRoot);
        }

        Files.createDirectories(targetDimension);
        Files.writeString(marker,
                "Imported by VanillaRPG from " + sourceRoot.getFileName() + System.lineSeparator());
    }

    private Path targetDimensionPath(NamespacedKey key) throws IOException {
        World primary = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        if (primary == null && !Bukkit.getWorlds().isEmpty()) {
            primary = Bukkit.getWorlds().get(0);
        }
        if (primary == null) throw new IOException("Primary overworld is not loaded");

        Path worldPath = primary.getWorldPath().toAbsolutePath().normalize();
        Path levelRoot = findLevelRoot(worldPath);
        return levelRoot.resolve("dimensions")
                .resolve(key.getNamespace())
                .resolve(key.getKey());
    }

    private Path findLevelRoot(Path worldPath) {
        Path cursor = worldPath;
        while (cursor != null) {
            Path fileName = cursor.getFileName();
            if (fileName != null && fileName.toString().equalsIgnoreCase("dimensions")) {
                Path parent = cursor.getParent();
                if (parent != null) return parent;
                break;
            }
            cursor = cursor.getParent();
        }
        // Fallback for implementations that report the primary level root directly.
        return worldPath;
    }

    private void copyTree(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                Files.createDirectories(target.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(file);
                Path destination = target.resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String sanitizeKey(String input) {
        String normalized = input.toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replaceAll("[^a-z0-9._/-]", "_");
        while (normalized.contains("__")) normalized = normalized.replace("__", "_");
        return normalized;
    }
}
