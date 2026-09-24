package org.legendofdragoon.severedchains.android.runtime;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Streams user-provided disc images into private app storage without large allocations. */
public final class GameDataImporter {
  private static final int BUFFER_SIZE = 1024 * 1024;

  private GameDataImporter() { }

  public static void importDiscImage(final Context context, final Uri source, final GamePaths paths) throws IOException {
    final File destination = paths.nextDiscDestination();
    final File temporary = new File(destination.getParentFile(), destination.getName() + ".part");
    final ContentResolver resolver = context.getContentResolver();

    try (InputStream input = resolver.openInputStream(source);
         OutputStream output = new FileOutputStream(temporary)) {
      if (input == null) {
        throw new IOException("Android could not open the selected file");
      }
      final byte[] buffer = new byte[BUFFER_SIZE];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
    } catch (final IOException e) {
      temporary.delete();
      throw e;
    }

    if (!temporary.renameTo(destination)) {
      temporary.delete();
      throw new IOException("Could not finalize imported disc image");
    }
  }
}
