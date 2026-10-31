package hkgclimate.page;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;

/**
 * The page's icons, drawn from path data in one colour (the page has no resources of its own):
 * the gear, the side-view seat (plain, with three rising heat arrows, or with a four-blade fan),
 * swap (two arrows chasing round a circle) and × (the sub-button's circle is its own outline).
 * The path data is the owner's mockup (docs/superpowers/specs/assets/2026-10-31-panel-v1-2.html),
 * in its viewBox units; the paths are scaled to the bounds once per size, not on the canvas, so
 * they stay sharp under hardware drawing. Main thread only.
 */
final class Icon extends Drawable {
    private static final String TAG = ClimatePage.TAG;

    static final int GEAR = 0;
    static final int SEAT = 1;
    static final int SEAT_HEAT = 2;
    static final int SEAT_COOL = 3;
    static final int SWAP = 4;
    static final int OFF = 5;
    private static final int SHAPES = 6;

    // the seat, cushion and backrest slightly bulged; the same outline under every seat icon
    private static final String SEAT_BODY = "M5 38.5 Q5 32.6 10.6 31.9 Q20.5 28.6 30.4 31.1"
            + " Q33.6 31.3 34.4 27.8 Q36.4 19.5 37.3 9.2 Q38 3.9 42 4.6 Q46.6 5.6 45.7 10.6"
            + " Q45.2 26.5 40.9 39.2 Q39.3 45 32.5 45 L10.2 45 Q5 45 5 40.6 Z";

    /** One part of a shape: a path in viewBox units, filled, or stroked with round caps. */
    private static final class Part {
        final Path path;
        final float stroke;     // 0: filled

        Part(Path path, float stroke) {
            this.path = path;
            this.stroke = stroke;
        }
    }

    private static final class Shape {
        final float width;
        final float height;
        final Part[] parts;

        Shape(float width, float height, Part... parts) {
            this.width = width;
            this.height = height;
            this.parts = parts;
        }
    }

    // built on first use; never changed afterwards (drawing works on transformed copies)
    private static final Shape[] SHAPE_CACHE = new Shape[SHAPES];

    private final int intrinsicWidth;
    private final int intrinsicHeight;
    private final boolean mirrored;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();

    private Shape shape;
    private int shapeId = -1;
    private Path[] drawn = new Path[0];
    private float scale = 1f;
    private int color;
    private int alpha = 255;

    /**
     * An icon drawn at the given size in pixels (its viewBox scaled to fit, centred), in the given
     * colour; mirrored left to right when asked (the passenger's seat faces the other way).
     */
    Icon(int shapeId, int widthPx, int heightPx, boolean mirrored, int color) {
        this.intrinsicWidth = widthPx;
        this.intrinsicHeight = heightPx;
        this.mirrored = mirrored;
        this.color = color;
        fill.setStyle(Paint.Style.FILL);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.MITER);   // as SVG draws the mockup
        updatePaints();
        setShape(shapeId);
    }

    void setShape(int id) {
        if (id == shapeId) return;
        shapeId = id;
        shape = shape(id);
        layout(getBounds());
        invalidateSelf();
    }

    void setColor(int c) {
        if (c == color) return;
        color = c;
        updatePaints();
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        try {
            Shape s = shape;
            if (s == null) return;
            for (int i = 0; i < s.parts.length && i < drawn.length; i++) {
                Part part = s.parts[i];
                if (part.stroke > 0) {
                    line.setStrokeWidth(part.stroke * scale);
                    canvas.drawPath(drawn[i], line);
                } else {
                    canvas.drawPath(drawn[i], fill);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "drawing an icon failed", t);
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        try {
            layout(bounds);
        } catch (Throwable t) {
            Log.e(TAG, "sizing an icon failed", t);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
        updatePaints();
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
        line.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private void updatePaints() {
        int a = (color >>> 24) * alpha / 255;
        int c = (a << 24) | (color & 0x00FFFFFF);
        fill.setColor(c);
        line.setColor(c);
    }

    /** Scale the shape's paths to the bounds: fit the viewBox, centred, mirrored if asked. */
    private void layout(Rect b) {
        Shape s = shape;
        if (s == null || b == null || b.isEmpty()) {
            drawn = new Path[0];
            return;
        }
        scale = Math.min(b.width() / s.width, b.height() / s.height);
        float dx = b.left + (b.width() - s.width * scale) / 2f;
        float dy = b.top + (b.height() - s.height * scale) / 2f;
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        if (mirrored) matrix.postScale(-1f, 1f, b.exactCenterX(), b.exactCenterY());
        Path[] out = new Path[s.parts.length];
        for (int i = 0; i < out.length; i++) {
            Path src = s.parts[i].path;
            Path dst = new Path();
            src.transform(matrix, dst);
            dst.setFillType(src.getFillType());
            out[i] = dst;
        }
        drawn = out;
    }

    private static Shape shape(int id) {
        if (id < 0 || id >= SHAPES) return null;
        Shape s = SHAPE_CACHE[id];
        if (s == null) {
            s = build(id);
            SHAPE_CACHE[id] = s;
        }
        return s;
    }

    private static Shape build(int id) {
        switch (id) {
            case GEAR: {
                Path gear = SvgPath.parse("M19.43 12.98c.04-.32.07-.64.07-.98s-.03-.66-.07-.98"
                        + "l2.11-1.65a.5.5 0 0 0 .12-.64l-2-3.46a.5.5 0 0 0-.61-.22l-2.49 1"
                        + "a7.3 7.3 0 0 0-1.69-.98l-.38-2.65A.49.49 0 0 0 14 2h-4a.49.49 0 0 0-.49.42"
                        + "l-.38 2.65c-.61.25-1.17.59-1.69.98l-2.49-1a.5.5 0 0 0-.61.22l-2 3.46"
                        + "a.49.49 0 0 0 .12.64l2.11 1.65c-.04.32-.07.65-.07.98s.03.66.07.98"
                        + "l-2.11 1.65a.5.5 0 0 0-.12.64l2 3.46c.12.22.39.3.61.22l2.49-1"
                        + "c.52.4 1.08.73 1.69.98l.38 2.65c.03.24.24.42.49.42h4"
                        + "c.25 0 .46-.18.49-.42l.38-2.65c.61-.25 1.17-.59 1.69-.98l2.49 1"
                        + "c.23.09.49 0 .61-.22l2-3.46a.5.5 0 0 0-.12-.64l-2.11-1.65z"
                        + "M12 15.5A3.5 3.5 0 1 1 12 8.5a3.5 3.5 0 0 1 0 7z");
                // the hub is a hole whichever way its contour runs
                gear.setFillType(Path.FillType.EVEN_ODD);
                return new Shape(24, 24, new Part(gear, 0));
            }
            case SEAT:
                return new Shape(48, 52, new Part(SvgPath.parse(SEAT_BODY), 0));
            case SEAT_HEAT: {
                // three wavy arrows rising from the cushion
                Part[] parts = new Part[7];
                parts[0] = new Part(SvgPath.parse(SEAT_BODY), 0);
                int[] xs = {11, 18, 25};
                for (int i = 0; i < xs.length; i++) {
                    int x = xs[i];
                    parts[1 + 2 * i] = new Part(SvgPath.parse("M" + x + " 28.5 q-2.6 -3.2 0 -6.4"
                            + " q2.6 -3.2 0 -6.4 L" + x + " 12.5"), 2.3f);
                    parts[2 + 2 * i] = new Part(SvgPath.parse("M" + (x - 3) + " 13.5 L" + x
                            + " 8.5 L" + (x + 3) + " 13.5 Z"), 0);
                }
                return new Shape(48, 52, parts);
            }
            case SEAT_COOL:
                return new Shape(48, 52, new Part(SvgPath.parse(SEAT_BODY), 0), new Part(fan(), 0));
            case SWAP:
                return new Shape(24, 24,
                        new Part(SvgPath.parse("M19.5 9.5 A8 8 0 0 0 5.2 7.6"), 2.4f),
                        new Part(SvgPath.parse("M4.5 14.5 A8 8 0 0 0 18.8 16.4"), 2.4f),
                        new Part(SvgPath.parse("M2.6 4.6 L5 8.4 L8.9 6.2 Z"), 0),
                        new Part(SvgPath.parse("M21.4 19.4 L19 15.6 L15.1 17.8 Z"), 0));
            case OFF:
                return new Shape(24, 24, new Part(SvgPath.parse("M7 7 L17 17 M17 7 L7 17"), 2.8f));
            default:
                return null;
        }
    }

    /**
     * Four elliptical blades round (17, 17), turned 18 degrees, with a round hub hole of radius 2
     * (the mockup draws the hub in the background colour; here it is cut out).
     */
    private static Path fan() {
        Path blades = new Path();
        Path blade = new Path();
        Matrix m = new Matrix();
        for (int k = 0; k < 4; k++) {
            blade.reset();
            // ellipse centre (0, -5), radii 3.1 x 5: from the hub outwards
            blade.addOval(new RectF(-3.1f, -10f, 3.1f, 0f), Path.Direction.CW);
            m.setRotate(90f * k + 18f);
            m.postTranslate(17f, 17f);
            blade.transform(m);
            blades.addPath(blade);
        }
        Path hub = new Path();
        hub.addCircle(17f, 17f, 2f, Path.Direction.CW);
        try {
            Path cut = new Path();
            if (cut.op(blades, hub, Path.Op.DIFFERENCE)) return cut;
            Log.w(TAG, "cutting the fan hub failed; drawing the fan without its hole");
        } catch (Throwable t) {
            Log.e(TAG, "cutting the fan hub failed", t);
        }
        return blades;
    }
}
