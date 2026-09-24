package org.legendofdragoon.severedchains.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Android-owned entry point. RuntimeHost will initially launch a bundled ARM64 JVM
 * compatibility host, and can later be replaced by a native engine host.
 */
public final class LauncherActivity extends Activity {
  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    final int padding = (int) (24 * getResources().getDisplayMetrics().density);
    content.setPadding(padding, padding, padding, padding);

    final TextView title = new TextView(this);
    title.setText("Severed Chains Android\n\nLauncher bootstrap complete.");
    title.setTextSize(22);
    content.addView(title);

    final TextView status = new TextView(this);
    status.setText(
        "Next: add the ARM64 compatibility runtime, disc-image import, controls, and logs.\n\n"
            + "The game and disc images are not bundled with this app.");
    status.setTextSize(16);
    content.addView(status);

    setContentView(content);
  }
}
