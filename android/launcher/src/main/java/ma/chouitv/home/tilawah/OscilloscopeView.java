package ma.chouitv.home.tilawah;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.view.View;

/**
 * A transparent oscilloscope in the app's gold and green. It draws the real waveform through the
 * Visualizer when the microphone permission was granted (the Visualizer needs it), otherwise a soft
 * synthetic wave that moves only while the sound plays.
 */
public class OscilloscopeView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final byte[] wave = new byte[512];
    private Visualizer visualizer;
    private int session = -1;
    private boolean live, haveWave;
    private float phase, level;
    private long last;

    public OscilloscopeView(Context c, AttributeSet a) {
        super(c, a);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3.5f * getResources().getDisplayMetrics().density);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(10f * getResources().getDisplayMetrics().density);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        int gold = 0xFFDCB86F, green = 0xFF57D294;
        float[] pos = {0f, 0.5f, 1f};
        line.setShader(new LinearGradient(0, 0, w, 0, new int[] {green, gold, green}, pos, Shader.TileMode.CLAMP));
        glow.setShader(new LinearGradient(0, 0, w, 0, new int[] {0x3357D294, 0x33DCB86F, 0x3357D294}, pos, Shader.TileMode.CLAMP));
    }

    /** The playing state and the audio session of the MediaPlayer (-1 when there is none). */
    void setSource(boolean playing, int audioSession) {
        live = playing;
        if (audioSession != session) {
            session = audioSession;
            stopVisualizer();
            if (session > 0) startVisualizer();
        }
    }

    private void startVisualizer() {
        try {
            if (getContext().checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            visualizer = new Visualizer(session);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[0] >= wave.length
                    ? Visualizer.getCaptureSizeRange()[0] : wave.length);
            visualizer.setEnabled(true);
        } catch (Throwable e) {
            stopVisualizer();
        }
    }

    private void stopVisualizer() {
        haveWave = false;
        if (visualizer != null) {
            try { visualizer.setEnabled(false); } catch (Throwable ignored) {}
            try { visualizer.release(); } catch (Throwable ignored) {}
            visualizer = null;
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopVisualizer();
        session = -1;
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float mid = h / 2f;

        haveWave = false;
        if (live && visualizer != null) {
            try {
                if (visualizer.getWaveForm(wave) == Visualizer.SUCCESS) haveWave = true;
            } catch (Throwable ignored) {}
        }
        level += ((live ? 1f : 0.06f) - level) * Math.min(1f, dt * 3f);
        phase += dt * 2.4f;

        path.reset();
        int steps = Math.max(2, w / 6);
        float peak = 0f;
        for (int i = 0; i <= steps; i++) {
            float x = w * i / (float) steps;
            float u = i / (float) steps;
            float y;
            if (haveWave) {
                int idx = Math.min(wave.length - 1, (int) (u * (wave.length - 1)));
                y = ((wave[idx] & 0xFF) - 128) / 128f;
                y *= 2.2f;
            } else {
                float env = (float) Math.sin(Math.PI * u);   // the wave fades at both ends
                y = (float) (Math.sin(u * 13 + phase) * 0.45 + Math.sin(u * 27 - phase * 1.6) * 0.3
                        + Math.sin(u * 5 + phase * 0.7) * 0.25) * env * (0.8f + 0.2f * (float) Math.sin(phase * 0.9));
            }
            y = Math.max(-1f, Math.min(1f, y)) * level;
            peak = Math.max(peak, Math.abs(y));
            float py = mid - y * mid * 0.85f;
            if (i == 0) path.moveTo(x, py); else path.lineTo(x, py);
        }
        canvas.drawPath(path, glow);
        canvas.drawPath(path, line);
        postInvalidateOnAnimation();
    }
}
