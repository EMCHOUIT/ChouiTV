package ma.chouitv.installer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;

/** The Moroccan touch, drawn in code: an eight-point star (khatam), the zellige lattice, a horseshoe arch, a star-ended line. */
final class Orn {
    static final int GOLD = 0xFFD6AF6B;

    /** An eight-point star centred at cx,cy with outer radius r. */
    static Path star(float cx, float cy, float r) {
        Path p = new Path();
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5 - 90);
            float rad = i % 2 == 0 ? r : r * 0.72f;
            float x = cx + (float) Math.cos(a) * rad, y = cy + (float) Math.sin(a) * rad;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    static float dp(Context c, float v) { return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()); }

    static Paint line(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        p.setStrokeWidth(width);
        return p;
    }

    /** The whole screen's faint lattice of stars. */
    static final class Lattice extends View {
        private final Paint p;
        private final float step;

        Lattice(Context c) {
            super(c);
            p = line(0x1CD6AF6B, dp(c, 1.2f));
            step = dp(c, 58);
        }

        @Override protected void onDraw(Canvas cv) {
            int row = 0;
            for (float y = 0; y < getHeight() + step; y += step * 0.5f, row++) {
                for (float x = (row % 2) * step * 0.5f; x < getWidth() + step; x += step) cv.drawPath(star(x, y, step * 0.30f), p);
            }
        }
    }

    /** A narrow band of small stars along the top edge. */
    static final class Strip extends View {
        private final Paint p;

        Strip(Context c) { super(c); p = line(0x66D6AF6B, dp(c, 1.2f)); }

        @Override protected void onDraw(Canvas cv) {
            float step = dp(getContext(), 34), r = dp(getContext(), 6);
            for (float x = step / 2; x < getWidth(); x += step) cv.drawPath(star(x, getHeight() / 2f, r), p);
        }
    }

    /** A thin gold line that ends in an eight-point star, under a title. It starts on the reading side of the screen. */
    static final class Rule extends View {
        private final Paint l, f;

        Rule(Context c) {
            super(c);
            l = line(0x99D6AF6B, dp(c, 1.3f));
            f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setColor(0x40D6AF6B);
        }

        @Override protected void onDraw(Canvas cv) {
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float w = getWidth(), cy = getHeight() / 2f, r = dp(getContext(), 7), len = dp(getContext(), 150);
            float x0 = rtl ? w - r : r, dir = rtl ? -1 : 1;
            Path s = star(x0, cy, r);
            cv.drawPath(s, f);
            Paint o = line(0xFFD6AF6B, dp(getContext(), 1.3f));
            cv.drawPath(s, o);
            cv.drawLine(x0 + dir * r * 1.6f, cy, x0 + dir * len, cy, l);
        }
    }

    /** A gold star with a number inside: the steps of the preparation. */
    static final class StarNum extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String n = "";

        StarNum(Context c, String n, android.graphics.Typeface tf) {
            super(c);
            this.n = n;
            fill.setColor(GOLD);
            txt.setColor(0xFF040E09);
            txt.setTextAlign(Paint.Align.CENTER);
            txt.setTypeface(tf);
            txt.setTextSize(dp(c, 16));
        }

        @Override protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            cv.drawPath(star(cx, cy, Math.min(cx, cy)), fill);
            cv.drawText(n, cx, cy + dp(getContext(), 5.5f), txt);
        }
    }

    /** A horseshoe arch (the door of a medina): two gold outlines, the inner one fainter, a star at the crown. Its children sit inside. */
    static final class Arch extends FrameLayout {
        private final Paint outer, inner, fill, crownFill;

        Arch(Context c) {
            super(c);
            setWillNotDraw(false);
            outer = line(0xE6D6AF6B, dp(c, 2.2f));
            inner = line(0x70D6AF6B, dp(c, 1.4f));
            fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(0xD20E2218);
            crownFill = new Paint(Paint.ANTI_ALIAS_FLAG);
            crownFill.setColor(0xFF142C1F);
        }

        private Path shape(float inset) {
            float w = getWidth(), top = dp(getContext(), 14) + inset, b = getHeight() - inset, x1 = inset + dp(getContext(), 2), x2 = w - inset - dp(getContext(), 2);
            float r = (x2 - x1) / 2f;
            Path p = new Path();
            p.moveTo(x1, b);
            p.lineTo(x1, top + r);
            p.arcTo(new RectF(x1, top, x2, top + 2 * r), 180, 180, false);
            p.lineTo(x2, b);
            return p;
        }

        @Override protected void onDraw(Canvas cv) {
            Path a = shape(0);
            Path closed = new Path(a);
            closed.close();
            cv.drawPath(closed, fill);
            cv.drawPath(a, outer);
            cv.drawPath(shape(dp(getContext(), 9)), inner);
            float cx = getWidth() / 2f, cy = dp(getContext(), 14), r = dp(getContext(), 10);
            Path s = star(cx, cy, r);
            cv.drawPath(s, crownFill);
            cv.drawPath(s, outer);
        }
    }

    /** The global progress: a wide gold bar that fills from the reading side, a tick per step, a star at the tip. */
    static final class Bar extends View {
        private final Paint track, fill, tick, ring, dot;
        private float p;
        static final int STEPS = 8;

        Bar(Context c) {
            super(c);
            track = new Paint(Paint.ANTI_ALIAS_FLAG); track.setColor(0xFF343F38);
            fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            tick = new Paint(Paint.ANTI_ALIAS_FLAG); tick.setColor(0xFF0A1811); tick.setStrokeWidth(dp(c, 2));
            ring = line(0xFFD6AF6B, dp(c, 1.6f));
            dot = new Paint(Paint.ANTI_ALIAS_FLAG); dot.setColor(0xFF142C1F);
        }

        void set(float v) { p = Math.max(0f, Math.min(1f, v)); invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float h = dp(getContext(), 14), pad = dp(getContext(), 12), w = getWidth() - 2 * pad, cy = getHeight() / 2f, top = cy - h / 2, bot = cy + h / 2;
            RectF tr = new RectF(pad, top, pad + w, bot);
            cv.drawRoundRect(tr, h / 2, h / 2, track);
            float fw = Math.max(h, w * p);
            RectF fr = rtl ? new RectF(pad + w - fw, top, pad + w, bot) : new RectF(pad, top, pad + fw, bot);
            fill.setShader(new android.graphics.LinearGradient(0, top, 0, bot, 0xFFFFE296, 0xFFC48E3A, android.graphics.Shader.TileMode.CLAMP));
            cv.drawRoundRect(fr, h / 2, h / 2, fill);
            for (int i = 1; i < STEPS; i++) {
                float x = rtl ? pad + w - w * i / STEPS : pad + w * i / STEPS;
                cv.drawLine(x, top - dp(getContext(), 3), x, bot + dp(getContext(), 3), tick);
            }
            float tip = rtl ? fr.left : fr.right;
            Path s = star(tip, cy, dp(getContext(), 11));
            cv.drawPath(s, dot);
            cv.drawPath(s, ring);
        }
    }

    /** One node of the timeline: the rail through it, and a dot that is empty, running, done or failed. */
    static final class Node extends View {
        static final int WAIT = 0, RUN = 1, DONE = 2, FAIL = 3;
        private int state = WAIT;
        private final boolean first, last;
        private final Paint rail, fill, ring, mark;

        Node(Context c, boolean first, boolean last) {
            super(c);
            this.first = first; this.last = last;
            rail = line(0xFF50645A, dp(c, 2));
            fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring = line(0xFF50645A, dp(c, 2));
            mark = line(0xFFF6EFE0, dp(c, 2.4f));
            mark.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(int s) { state = s; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f, cy = Math.min(getHeight() / 2f, dp(getContext(), 20)), r = dp(getContext(), 10);
            if (!first) cv.drawLine(cx, 0, cx, cy - r, rail);
            if (!last) cv.drawLine(cx, cy + r, cx, getHeight(), rail);
            fill.setColor(state == DONE ? 0xFF3F7A5C : state == FAIL ? 0xFF7A3F3F : state == RUN ? 0xFF142C1F : 0xFF0A1811);
            cv.drawCircle(cx, cy, r, fill);
            ring.setColor(state == RUN ? GOLD : state == DONE ? 0xFF3F7A5C : state == FAIL ? 0xFF7A3F3F : 0xFF50645A);
            ring.setStrokeWidth(dp(getContext(), state == RUN ? 2.6f : 2));
            cv.drawCircle(cx, cy, r, ring);
            if (state == DONE) {
                Path c = new Path();
                c.moveTo(cx - r * 0.45f, cy + r * 0.05f); c.lineTo(cx - r * 0.1f, cy + r * 0.4f); c.lineTo(cx + r * 0.5f, cy - r * 0.35f);
                cv.drawPath(c, mark);
            } else if (state == RUN) {
                Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(GOLD);
                cv.drawCircle(cx, cy, r * 0.32f, d);
            } else if (state == FAIL) {
                cv.drawLine(cx, cy - r * 0.45f, cx, cy + r * 0.1f, mark);
                cv.drawPoint(cx, cy + r * 0.45f, mark);
            }
        }
    }
}
