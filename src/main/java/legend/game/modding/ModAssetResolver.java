package legend.game.modding;

import legend.game.unpacker.Unpacker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.legendofdragoon.modloader.ModContainer;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static legend.core.GameEngine.MODS;

/**
 * Resolves loose asset overrides supplied by enabled mods.
 *
 * <p>Each loaded mod may provide a sidecar directory using the layout
 * {@code mods/<mod-id>/files/...}. Files in that tree mirror paths under the
 * normal Severed Chains {@code files/...} tree. This keeps large replacement
 * assets out of mod JARs and, importantly, allows a mod to replace one file in
 * a directory without copying all of the retail files beside it.</p>
 *
 * <p>When more than one loaded mod replaces the same path, mods are applied in
 * ascending mod-id order, so the lexicographically later mod wins. The choice
 * is deterministic and a warning is logged for collisions.</p>
 */
public final class ModAssetResolver {
  private ModAssetResolver() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(ModAssetResolver.class);

  public static final Path MODS_ROOT = Path.of(".", "mods");
  public static final String FILES_DIR = "files";

  /**
   * Resolve a logical path under {@link Unpacker#ROOT} to its highest-priority
   * loose mod replacement, or to the normal extracted file if no replacement
   * exists.
   */
  public static Path resolve(final Path relativePath) {
    final Path relative = normalizeRelative(relativePath);
    Path resolved = Unpacker.ROOT.resolve(relative);
    String previousOwner = Files.isRegularFile(resolved) ? "retail" : null;

    for(final ModContainer mod : loadedModsInApplyOrder()) {
      final Path candidate = modFilesRoot(mod).resolve(relative);
      if(Files.isRegularFile(candidate)) {
        if(previousOwner != null && !"retail".equals(previousOwner)) {
          LOGGER.warn("Asset override collision for %s: %s overridden by %s", relative, previousOwner, mod.modId);
        }

        resolved = candidate;
        previousOwner = mod.modId;
      }
    }

    if(previousOwner != null && !"retail".equals(previousOwner)) {
      LOGGER.debug("Using mod asset %s from %s", relative, previousOwner);
    }

    return resolved;
  }

  /** Resolve a path only if it is located under the extracted game root. */
  public static Path resolveGamePath(final Path path) {
    final Path relative = relativeToGameRoot(path);
    return relative != null ? resolve(relative) : path;
  }

  /**
   * Convert a path under {@link Unpacker#ROOT} into a logical game path.
   * Returns {@code null} for paths outside the extracted game tree.
   */
  public static Path relativeToGameRoot(final Path path) {
    final Path root = Unpacker.ROOT.toAbsolutePath().normalize();
    final Path absolute = path.toAbsolutePath().normalize();

    if(!absolute.startsWith(root)) {
      return null;
    }

    return root.relativize(absolute);
  }

  /** Return whether a logical game path exists as either retail or mod data. */
  public static boolean exists(final Path relativePath) {
    final Path relative = normalizeRelative(relativePath);

    if(Files.exists(Unpacker.ROOT.resolve(relative))) {
      return true;
    }

    for(final ModContainer mod : loadedModsInApplyOrder()) {
      if(Files.exists(modFilesRoot(mod).resolve(relative))) {
        return true;
      }
    }

    return false;
  }

  /** Return whether a logical game path is a directory in retail or mod data. */
  public static boolean isDirectory(final Path relativePath) {
    final Path relative = normalizeRelative(relativePath);

    if(Files.isDirectory(Unpacker.ROOT.resolve(relative))) {
      return true;
    }

    for(final ModContainer mod : loadedModsInApplyOrder()) {
      if(Files.isDirectory(modFilesRoot(mod).resolve(relative))) {
        return true;
      }
    }

    return false;
  }

  /**
   * Build a merged directory view. Retail files are added first; files from
   * enabled mods then replace entries with the same filename. This is what
   * allows an HD character pack to replace only model file {@code 32} while
   * retaining the retail animation files in the same directory.
   */
  public static List<Path> listMergedFiles(final Path relativeDirectory) {
    final Path relative = normalizeRelative(relativeDirectory);
    final Map<String, Path> merged = new LinkedHashMap<>();

    mergeDirectory(Unpacker.ROOT.resolve(relative), merged);
    for(final ModContainer mod : loadedModsInApplyOrder()) {
      mergeDirectory(modFilesRoot(mod).resolve(relative), merged);
    }

    return new ArrayList<>(merged.values());
  }

  private static void mergeDirectory(final Path directory, final Map<String, Path> merged) {
    if(!Files.isDirectory(directory)) {
      return;
    }

    try(final DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
      for(final Path child : stream) {
        if(Files.isRegularFile(child)) {
          merged.put(child.getFileName().toString(), child);
        }
      }
    } catch(final IOException e) {
      throw new RuntimeException("Failed to enumerate asset directory " + directory, e);
    }
  }

  private static Path modFilesRoot(final ModContainer mod) {
    return MODS_ROOT.resolve(mod.modId).resolve(FILES_DIR);
  }

  private static List<ModContainer> loadedModsInApplyOrder() {
    return MODS.getLoadedMods().stream()
      .sorted(Comparator.comparing(mod -> mod.modId))
      .toList();
  }

  private static Path normalizeRelative(final Path path) {
    if(path.isAbsolute()) {
      throw new IllegalArgumentException("Asset path must be relative: " + path);
    }

    final Path normalized = path.normalize();
    if(normalized.startsWith("..")) {
      throw new IllegalArgumentException("Asset path escapes game root: " + path);
    }

    return normalized;
  }
}
