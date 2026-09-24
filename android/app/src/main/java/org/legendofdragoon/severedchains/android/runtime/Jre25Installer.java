package org.legendofdragoon.severedchains.android.runtime;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Downloads and safely extracts the public ARM64 JRE 25 runtime used by the compatibility host. */
public final class Jre25Installer {
  private static final String ARCHIVE_URL =
      "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/"
          + "download_jre25/jre25-android-arm64.tar.xz";
  private static final String ARCHIVE_SHA256 =
      "d3eb7afe2240c26728a1bb440502c5f18ac3883e932d202dd7f0c9bcbbce4c37";
  private static final int BUFFER_SIZE = 1024 * 1024;

  public interface ProgressListener {
    void onProgress(String message);
  }

  private Jre25Installer() { }

  public static boolean isInstalled(final GamePaths paths) {
    final File jre = new File(paths.runtime(), "jre25");
    return new File(jre, "bin/java").isFile() && new File(jre, "lib/server/libjvm.so").isFile();
  }

  public static void install(final GamePaths paths, final ProgressListener progress) throws IOException {
    RuntimeLog.info(paths, "JRE 25 installation requested; runtime=" + paths.runtime());
    if (isInstalled(paths)) {
      RuntimeLog.info(paths, "JRE 25 was already installed.");
      progress.onProgress("ARM64 JRE 25 is already installed.");
      return;
    }

    final File archive = new File(paths.runtime(), "jre25-android-arm64.tar.xz");
    try {
      download(archive, progress);
      RuntimeLog.info(paths, "Downloaded archive bytes=" + archive.length());
      verifySha256(archive);
      RuntimeLog.info(paths, "Archive SHA-256 verified.");
      extract(paths, archive, new File(paths.runtime(), "jre25"), progress);
    } catch (final IOException e) {
      RuntimeLog.error(paths, "JRE 25 installation failed: " + e.getMessage(), e);
      throw e;
    }
    if (!isInstalled(paths)) {
      throw new IOException("Runtime archive did not contain a usable ARM64 JRE 25");
    }
    if (!archive.delete()) {
      progress.onProgress("JRE 25 installed; the temporary archive can be removed later.");
    } else {
      progress.onProgress("ARM64 JRE 25 installed.");
    }
  }

  private static void download(final File destination, final ProgressListener progress) throws IOException {
    final File partial = new File(destination.getParentFile(), destination.getName() + ".part");
    final HttpURLConnection connection = (HttpURLConnection) new URL(ARCHIVE_URL).openConnection();
    connection.setConnectTimeout(20_000);
    connection.setReadTimeout(30_000);
    connection.setInstanceFollowRedirects(true);
    connection.connect();
    if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
      throw new IOException("Runtime download returned HTTP " + connection.getResponseCode());
    }

    final long total = connection.getContentLengthLong();
    long copied = 0;
    try (InputStream input = new BufferedInputStream(connection.getInputStream());
         OutputStream output = new FileOutputStream(partial)) {
      final byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
        copied += read;
        if (total > 0) {
          progress.onProgress("Downloading ARM64 JRE 25: " + (copied * 100 / total) + "%");
        }
      }
    } catch (final IOException e) {
      partial.delete();
      throw e;
    } finally {
      connection.disconnect();
    }

    if (!partial.renameTo(destination)) {
      partial.delete();
      throw new IOException("Could not finalize runtime download");
    }
  }

  private static void verifySha256(final File archive) throws IOException {
    try (InputStream input = new FileInputStream(archive)) {
      final MessageDigest digest = MessageDigest.getInstance("SHA-256");
      final byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = input.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
      }
      final String actual = toHex(digest.digest());
      if (!ARCHIVE_SHA256.equals(actual)) {
        throw new IOException("Downloaded JRE 25 checksum did not match");
      }
    } catch (final NoSuchAlgorithmException e) {
      throw new IOException("SHA-256 is unavailable", e);
    }
  }

  private static void extract(final GamePaths paths, final File archive, final File target, final ProgressListener progress) throws IOException {
    final File staging = new File(target.getParentFile(), target.getName() + ".staging");
    deleteRecursively(staging);
    if (!staging.mkdirs()) {
      throw new IOException("Could not create runtime staging directory");
    }
    final String stagingPath = staging.getCanonicalPath();
    final String rootPath = stagingPath + File.separator;
    int entryCount = 0;

    try (InputStream fileInput = new BufferedInputStream(new FileInputStream(archive));
         XZCompressorInputStream xz = new XZCompressorInputStream(fileInput);
         TarArchiveInputStream tar = new TarArchiveInputStream(xz, StandardCharsets.UTF_8.name())) {
      TarArchiveEntry entry;
      while ((entry = tar.getNextTarEntry()) != null) {
        entryCount++;
        RuntimeLog.info(paths, "Archive entry " + entryCount + ": " + entry.getName());
        if (entry.isSymbolicLink() || entry.isLink()) {
          throw new IOException("Runtime archive links are not supported");
        }
        final File output = new File(staging, entry.getName());
        final String outputPath = output.getCanonicalPath();
        // A tar root entry such as "./" canonically resolves to staging itself. It is safe;
        // everything else must be a child, which still rejects absolute paths and ../ traversal.
        if (!outputPath.equals(stagingPath) && !outputPath.startsWith(rootPath)) {
          throw new IOException("Unsafe runtime archive path: " + entry.getName());
        }
        if (entry.isDirectory()) {
          if (!output.exists() && !output.mkdirs()) {
            throw new IOException("Could not create runtime directory");
          }
          continue;
        }
        final File parent = output.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
          throw new IOException("Could not create runtime parent directory");
        }
        try (OutputStream stream = new FileOutputStream(output)) {
          copy(tar, stream);
        }
      }
    } catch (final IOException e) {
      deleteRecursively(staging);
      throw e;
    }
    RuntimeLog.info(paths, "Extracted " + entryCount + " runtime archive entries.");

    deleteRecursively(target);
    if (!staging.renameTo(target)) {
      deleteRecursively(staging);
      throw new IOException("Could not activate installed JRE 25");
    }
    progress.onProgress("Verifying ARM64 JRE 25…");
  }

  private static void copy(final InputStream input, final OutputStream output) throws IOException {
    final byte[] buffer = new byte[BUFFER_SIZE];
    int read;
    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
  }

  private static String toHex(final byte[] bytes) {
    final StringBuilder result = new StringBuilder(bytes.length * 2);
    for (final byte value : bytes) {
      result.append(String.format("%02x", value));
    }
    return result.toString();
  }

  private static void deleteRecursively(final File target) throws IOException {
    if (!target.exists()) {
      return;
    }
    final File[] children = target.listFiles();
    if (children != null) {
      for (final File child : children) {
        deleteRecursively(child);
      }
    }
    if (!target.delete()) {
      throw new IOException("Could not remove " + target);
    }
  }
}
