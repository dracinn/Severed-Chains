package legend.game.unpacker;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import legend.core.Async;
import legend.core.DebugHelper;
import legend.game.modding.ModAssetResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public final class Loader {
  private Loader() { }

  private static final Logger LOGGER = LogManager.getFormatterLogger(Loader.class);

  private static final Pattern MRG_ENTRY = Pattern.compile("[=;]");

  private static final AtomicInteger LOADING_COUNT = new AtomicInteger();

  public static Path resolve(final String name) {
    return resolve(Path.of(fixPath(name)));
  }

  public static Path resolve(final Path name) {
    if(name.isAbsolute()) {
      return name;
    }

    return ModAssetResolver.resolve(name);
  }

  public static FileData loadFileSync(final Path path) {
    final Path resolved = ModAssetResolver.resolveGamePath(path);
    LOGGER.info("Loading file %s", resolved);

    try {
      return new FileData(Files.readAllBytes(resolved));
    } catch(final IOException e) {
      throw new RuntimeException("Failed to load file " + resolved, e);
    }
  }

  public static FileData loadFileSync(final String name) {
    return loadFileSync(resolve(name));
  }

  public static CompletableFuture<FileData> loadFile(final Path path) {
    final int total = LOADING_COUNT.incrementAndGet();
    final StackWalker.StackFrame frame = DebugHelper.getCallerFrame();
    LOGGER.info("Queueing file %s (total queued: %d) from %s.%s(%s:%d)", path, total, frame.getClassName(), frame.getMethodName(), frame.getFileName(), frame.getLineNumber());

    return Async
      .run(() -> loadFileSync(path))
      .exceptionally(t -> onFileLoadingException(t, path.toString()))
      .whenComplete((result, exception) -> {
        final int remaining = LOADING_COUNT.decrementAndGet();
        LOGGER.info("File %s loaded (remaining queued: %d)", path, remaining);
      })
    ;
  }

  public static CompletableFuture<FileData> loadFile(final String name) {
    final int total = LOADING_COUNT.incrementAndGet();
    LOGGER.info("Queueing file %s (total queued: %d)", name, total);

    return Async
      .run(() -> loadFileSync(name))
      .exceptionally(t -> onFileLoadingException(t, name))
      .whenComplete((result, exception) -> {
        final int remaining = LOADING_COUNT.decrementAndGet();
        LOGGER.info("File %s loaded (remaining queued: %d)", name, remaining);
      })
    ;
  }

  public static CompletableFuture<List<FileData>> loadFiles(final String... files) {
    final int total = LOADING_COUNT.updateAndGet(i -> i + files.length);
    LOGGER.info("Queueing files %s (total queued: %d)", Arrays.toString(files), total);

    final FileData[] data = new FileData[files.length];
    final CompletableFuture<FileData>[] futures = new CompletableFuture[files.length];
    for(int i = 0; i < files.length; i++) {
      final int finalI = i;
      futures[i] = Async.run(() -> data[finalI] = loadFileSync(files[finalI]));
    }

    return CompletableFuture.allOf(futures)
      .thenApply(v -> List.of(data))
      .exceptionally(t -> onFileLoadingException(t, Arrays.toString(files)))
      .whenComplete((result, exception) -> {
        final int remaining = LOADING_COUNT.updateAndGet(i -> i - files.length);
        LOGGER.info("Files %s loaded (remaining queued: %d)", Arrays.toString(files), remaining);
      })
    ;
  }

  public static CompletableFuture<List<FileData>> loadFiles(final Path... files) {
    final int total = LOADING_COUNT.updateAndGet(i -> i + files.length);
    LOGGER.info("Queueing files %s (total queued: %d)", Arrays.toString(files), total);

    final FileData[] data = new FileData[files.length];
    final CompletableFuture<FileData>[] futures = new CompletableFuture[files.length];
    for(int i = 0; i < files.length; i++) {
      final int finalI = i;
      futures[i] = Async.run(() -> data[finalI] = loadFileSync(files[finalI]));
    }

    return CompletableFuture.allOf(futures)
      .thenApply(v -> List.of(data))
      .exceptionally(t -> onFileLoadingException(t, Arrays.toString(files)))
      .whenComplete((result, exception) -> {
        final int remaining = LOADING_COUNT.updateAndGet(i -> i - files.length);
        LOGGER.info("Files %s loaded (remaining queued: %d)", Arrays.toString(files), remaining);
      })
    ;
  }

  public static CompletableFuture<List<FileData>> loadDirectory(final String name) {
    final int total = LOADING_COUNT.incrementAndGet();
    LOGGER.info("Queueing directory %s (total queued: %d)", name, total);

    return Async
      .run(() -> loadDirectorySync(name))
      .exceptionally(t -> onFileLoadingException(t, name))
      .whenComplete((result, exception) -> {
        final int remaining = LOADING_COUNT.decrementAndGet();
        LOGGER.info("Directory %s loaded (remaining queued: %d)", name, remaining);
      })
    ;
  }

  public static CompletableFuture<List<FileData>> loadDirectory(final Path dir) {
    final int total = LOADING_COUNT.incrementAndGet();

    final StackWalker.StackFrame frame = DebugHelper.getCallerFrame();
    LOGGER.info("Queueing directory %s (total queued: %d) from %s.%s(%s:%d)", dir, total, frame.getClassName(), frame.getMethodName(), frame.getFileName(), frame.getLineNumber());

    return Async.run(() -> loadDirectorySync(dir))
      .exceptionally(t -> onFileLoadingException(t, dir.toString()))
      .whenComplete((result, exception) -> {
        final int remaining = LOADING_COUNT.decrementAndGet();
        LOGGER.info("Directory %s loaded (remaining queued: %d)", dir, remaining);
      })
    ;
  }

  private static <T> T onFileLoadingException(final Throwable t, final String path) {
    LOGGER.error("Failed to load " + path, t);
    return null;
  }

  public static List<FileData> loadDirectorySync(final String name) {
    return loadDirectorySync(Unpacker.ROOT.resolve(fixPath(name)));
  }

  public static List<FileData> loadDirectorySync(final Path dir) {
    LOGGER.info("Loading directory %s", dir);

    final Path relative = ModAssetResolver.relativeToGameRoot(dir);
    if(relative != null) {
      return loadMergedGameDirectory(relative);
    }

    return loadDirectDirectory(dir);
  }

  private static List<FileData> loadMergedGameDirectory(final Path relativeDir) {
    final Path mrg = ModAssetResolver.resolve(relativeDir.resolve("mrg"));

    if(Files.isRegularFile(mrg)) {
      return loadMrgDirectory(relativeDir, mrg);
    }

    final List<Path> children = new ArrayList<>(ModAssetResolver.listMergedFiles(relativeDir));
    children.sort(Loader::compareFilenames);

    final List<FileData> files = new ArrayList<>();
    for(final Path child : children) {
      try {
        files.add(new FileData(Files.readAllBytes(child)));
      } catch(final IOException e) {
        throw new RuntimeException("Failed to load directory " + relativeDir, e);
      }
    }

    return files;
  }

  private static List<FileData> loadMrgDirectory(final Path relativeDir, final Path mrg) {
    try(final BufferedReader reader = Files.newBufferedReader(mrg)) {
      final Int2IntMap fileMap = new Int2IntArrayMap();
      final Int2IntMap virtualSizeMap = new Int2IntArrayMap();

      reader.lines().forEach(line -> {
        final String[] parts = MRG_ENTRY.split(line);

        if(parts.length != 3) {
          throw new RuntimeException("Invalid MRG entry! " + line);
        }

        final int virtual = Integer.parseInt(parts[0]);

        if(parts[1].isBlank()) {
          fileMap.put(virtual, -1);
          virtualSizeMap.put(virtual, 0);
          return;
        }

        final int real = Integer.parseInt(parts[1]);
        fileMap.put(virtual, real);
        virtualSizeMap.put(virtual, Integer.parseInt(parts[2]));
      });

      final List<FileData> files = new ArrayList<>();

      for(final var entry : fileMap.int2IntEntrySet()) {
        final int virtual = entry.getIntKey();
        final int real = entry.getIntValue();

        if(real == -1) {
          files.add(null);
          continue;
        }

        try {
          final Path file = ModAssetResolver.resolve(relativeDir.resolve(String.valueOf(real)));
          if(Files.isRegularFile(file)) {
            if(virtual == real) {
              files.add(new FileData(Files.readAllBytes(file)));
            } else {
              files.add(null);
            }
          } else if(ModAssetResolver.isDirectory(relativeDir.resolve(String.valueOf(real)))) {
            files.add(new FileData(new byte[0]));
          }
        } catch(final IOException e) {
          throw new RuntimeException("Failed to load directory " + relativeDir, e);
        }
      }

      for(final var entry : fileMap.int2IntEntrySet()) {
        final int virtual = entry.getIntKey();
        int real = entry.getIntValue();

        if(virtual == real || real == -1) {
          continue;
        }

        while(fileMap.get(real) != real) {
          real = fileMap.get(real);
        }

        final Path file = ModAssetResolver.resolve(relativeDir.resolve(String.valueOf(real)));
        if(Files.isRegularFile(file)) {
          files.set(virtual, FileData.virtual(files.get(real), virtualSizeMap.get(virtual), real));
        }
      }

      return files;
    } catch(final IOException e) {
      throw new RuntimeException("Failed to load directory " + relativeDir, e);
    }
  }

  private static List<FileData> loadDirectDirectory(final Path dir) {
    try(final DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
      final List<Path> children = new ArrayList<>();
      for(final Path child : ds) {
        if(Files.isRegularFile(child)) {
          children.add(child);
        }
      }

      children.sort(Loader::compareFilenames);

      final List<FileData> files = new ArrayList<>();
      for(final Path child : children) {
        try {
          files.add(new FileData(Files.readAllBytes(child)));
        } catch(final IOException e) {
          throw new RuntimeException("Failed to load directory " + dir, e);
        }
      }

      return files;
    } catch(final IOException e) {
      throw new RuntimeException("Failed to load directory " + dir, e);
    }
  }

  private static int compareFilenames(final Path path1, final Path path2) {
    final String filename1 = path1.getFileName().toString();
    final String filename2 = path2.getFileName().toString();

    try {
      return Integer.compare(Integer.parseInt(filename1), Integer.parseInt(filename2));
    } catch(final NumberFormatException ignored) {
    }

    return String.CASE_INSENSITIVE_ORDER.compare(filename1, filename2);
  }

  public static int getLoadingFileCount() {
    return LOADING_COUNT.get();
  }

  public static boolean exists(final String name) {
    return ModAssetResolver.exists(Path.of(fixPath(name)));
  }

  public static boolean isDirectory(final String name) {
    return ModAssetResolver.isDirectory(Path.of(fixPath(name)));
  }

  private static String fixPath(String name) {
    if(name.contains(";")) {
      name = name.substring(0, name.lastIndexOf(';'));
    }

    if(name.startsWith("\\") || name.startsWith("/")) {
      name = name.substring(1);
    }

    return name.replace('\\', '/');
  }
}
