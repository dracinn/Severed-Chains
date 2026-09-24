package org.legendofdragoon.severedchains.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.MotionEvent;
import android.widget.TextView;

import org.legendofdragoon.severedchains.android.runtime.NativeEglSurface;

/** Native Android rendering-surface smoke test for the future game backend. */
public final class RuntimeSurfaceActivity extends Activity implements SurfaceHolder.Callback {
  private TextView status;

  @Override
  protected void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final SurfaceView surface = new SurfaceView(this);
    surface.getHolder().addCallback(this);
    surface.setOnTouchListener((view, event) -> {
      NativeEglSurface.touch(event.getActionMasked(), event.getX(), event.getY(), view.getWidth(), view.getHeight());
      return true;
    });
    status = new TextView(this);
    status.setText("Preparing Android OpenGL ES surface…");
    setContentView(surface);
  }

  @Override
  public void surfaceCreated(final SurfaceHolder holder) {
    final String error = NativeEglSurface.start(holder.getSurface());
    if (error != null) {
      status.setText("Renderer initialization failed: " + error);
      setContentView(status);
    }
  }

  @Override
  public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
    NativeEglSurface.resize(width, height);
  }

  @Override
  public void surfaceDestroyed(final SurfaceHolder holder) {
    NativeEglSurface.stop();
  }
}
