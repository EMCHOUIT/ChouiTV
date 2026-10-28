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
}
