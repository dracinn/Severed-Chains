package org.legendofdragoon.severedchains.android;

import android.app.Activity;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.legendofdragoon.severedchains.android.runtime.CompatibilityRuntimeHost;
import org.legendofdragoon.severedchains.android.runtime.GameDataImporter;
import org.legendofdragoon.severedchains.android.runtime.GamePaths;
import org.legendofdragoon.severedchains.android.runtime.Jre25Installer;
import org.legendofdragoon.severedchains.android.runtime.RuntimeHost;
import org.legendofdragoon.severedchains.android.runtime.RuntimeLog;

import java.io.IOException;

/**
 * Android-owned entry point. RuntimeHost will initially launch a bundled ARM64 JVM
 * compatibility host, and can later be replaced by a native engine host.
 */
public final class LauncherActivity extends Activity {
  private static final int PICK_DISC_IMAGE = 1001;

  private GamePaths gamePaths;
  private RuntimeHost runtimeHost;
  private TextView status;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    gamePaths = GamePaths.create(this);
    runtimeHost = new CompatibilityRuntimeHost();

    final LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    final int padding = (int) (24 * getResources().getDisplayMetrics().density);
    content.setPadding(padding, padding, padding, padding);

    final TextView title = new TextView(this);
    title.setText("Severed Chains Android 0.1.2-debug");
    title.setTextSize(22);
    content.addView(title);

    status = new TextView(this);
    status.setTextSize(16);
    content.addView(status);

    final Button importDisc = new Button(this);
    importDisc.setText("Import disc image");
    importDisc.setOnClickListener(ignored -> selectDiscImage());
    content.addView(importDisc);

    final Button launch = new Button(this);
    launch.setText("Launch Severed Chains");
    launch.setOnClickListener(ignored -> launchGame());
    content.addView(launch);

    final Button rendererTest = new Button(this);
    rendererTest.setText("Test Android renderer surface");
    rendererTest.setOnClickListener(ignored -> startActivity(new Intent(this, RuntimeSurfaceActivity.class)));
    content.addView(rendererTest);

    final Button installRuntime = new Button(this);
    installRuntime.setText("Install ARM64 JRE 25");
    installRuntime.setOnClickListener(ignored -> installRuntime());
    content.addView(installRuntime);

    final Button copyLog = new Button(this);
    copyLog.setText("Copy runtime log");
    copyLog.setOnClickListener(ignored -> copyRuntimeLog());
    content.addView(copyLog);

    final TextView legal = new TextView(this);
    legal.setText("The game and disc images are not bundled. Import your own legally obtained disc images.");
    legal.setTextSize(14);
    content.addView(legal);

    refreshStatus();
    setContentView(content);
  }

  private void selectDiscImage() {
    final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("application/octet-stream");
    startActivityForResult(intent, PICK_DISC_IMAGE);
  }

  @Override
  @SuppressWarnings("deprecation")
  protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != PICK_DISC_IMAGE || resultCode != RESULT_OK || data == null) {
      return;
    }
    final Uri discImage = data.getData();
    if (discImage == null) {
      return;
    }
    try {
      GameDataImporter.importDiscImage(this, discImage, gamePaths);
      refreshStatus();
    } catch (final IOException e) {
      status.setText("Import failed: " + e.getMessage());
    }
  }

  private void launchGame() {
    status.setText(runtimeHost.start(this, gamePaths).message());
  }

  private void installRuntime() {
    status.setText("Downloading and installing ARM64 JRE 25…");
    new Thread(() -> {
      try {
        RuntimeLog.info(gamePaths, "Install button pressed.");
        Jre25Installer.install(gamePaths, message -> runOnUiThread(() -> status.setText(message)));
        runOnUiThread(this::refreshStatus);
      } catch (final IOException e) {
        RuntimeLog.error(gamePaths, "Installer surfaced an error", e);
        runOnUiThread(() -> status.setText("Runtime installation failed: " + e.getMessage()));
      }
    }, "jre25-installer").start();
  }

  private void copyRuntimeLog() {
    try {
      final String log = RuntimeLog.read(gamePaths);
      final ClipboardManager clipboard = getSystemService(ClipboardManager.class);
      clipboard.setPrimaryClip(ClipData.newPlainText("Severed Chains runtime log", log));
      status.setText("Runtime log copied to clipboard.");
    } catch (final IOException e) {
      status.setText("No runtime log is available yet.");
    }
  }

  private void refreshStatus() {
    status.setText("Game files: " + gamePaths.importedDiscCount() + "/4 discs\n" + runtimeHost.describe(gamePaths));
  }
}
