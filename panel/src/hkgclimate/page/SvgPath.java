package hkgclimate.page;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * Turns SVG path data (the "d" attribute) into an android.graphics.Path, so the page's icons can
 * be drawn from the same path data as the owner's mockup. Handles M L H V C S Q T A Z, absolute
 * and relative, with SVG's compact number forms ("-.49.42", ".5.5"). Arcs must have an x-axis
 * rotation of 0 (all of ours do); anything else is refused with IllegalArgumentException.
 */
final class SvgPath {
    private final String d;
    private int pos;

    private SvgPath(String d) {
        this.d = d;
    }

    static Path parse(String d) {
        return new SvgPath(d).build();
    }

    private Path build() {
        Path p = new Path();
        float x = 0, y = 0;             // current point
        float startX = 0, startY = 0;   // start of the current subpath
        float ctrlX = 0, ctrlY = 0;     // last control point, for S and T
        char last = 0;
        char cmd = 0;
        while (true) {
            skipSeparators();
            if (pos >= d.length()) break;
            char c = d.charAt(pos);
            if (isCommand(c)) {
                cmd = c;
                pos++;
            } else if (cmd == 0) {
                throw new IllegalArgumentException("path data must start with a command: " + d);
            } else if (cmd == 'M') {
                cmd = 'L';              // further pairs after a moveto are linetos
            } else if (cmd == 'm') {
                cmd = 'l';
            } else if (cmd == 'Z' || cmd == 'z') {
                throw new IllegalArgumentException("numbers after Z: " + d);
            }
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? x : 0, oy = rel ? y : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M':
                    x = ox + number();
                    y = oy + number();
                    p.moveTo(x, y);
                    startX = x;
                    startY = y;
                    break;
                case 'L':
                    x = ox + number();
                    y = oy + number();
                    p.lineTo(x, y);
                    break;
                case 'H':
                    x = ox + number();
                    p.lineTo(x, y);
                    break;
                case 'V':
                    y = oy + number();
                    p.lineTo(x, y);
                    break;
                case 'C': {
                    float x1 = ox + number(), y1 = oy + number();
                    float x2 = ox + number(), y2 = oy + number();
                    x = ox + number();
                    y = oy + number();
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    ctrlX = x2;
                    ctrlY = y2;
                    break;
                }
                case 'S': {
                    boolean after = last == 'C' || last == 'S';
                    float x1 = after ? 2 * x - ctrlX : x, y1 = after ? 2 * y - ctrlY : y;
                    float x2 = ox + number(), y2 = oy + number();
                    x = ox + number();
                    y = oy + number();
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    ctrlX = x2;
                    ctrlY = y2;
                    break;
                }
                case 'Q': {
                    float x1 = ox + number(), y1 = oy + number();
                    x = ox + number();
                    y = oy + number();
                    p.quadTo(x1, y1, x, y);
                    ctrlX = x1;
                    ctrlY = y1;
                    break;
                }
                case 'T': {
                    boolean after = last == 'Q' || last == 'T';
                    float x1 = after ? 2 * x - ctrlX : x, y1 = after ? 2 * y - ctrlY : y;
                    x = ox + number();
                    y = oy + number();
                    p.quadTo(x1, y1, x, y);
                    ctrlX = x1;
                    ctrlY = y1;
                    break;
                }
                case 'A': {
                    float rx = Math.abs(number()), ry = Math.abs(number());
                    float rotation = number();
                    boolean large = flag(), sweep = flag();
                    float x2 = ox + number(), y2 = oy + number();
                    if (rotation != 0) throw new IllegalArgumentException("rotated arc: " + d);
                    arc(p, x, y, rx, ry, large, sweep, x2, y2);
                    x = x2;
                    y = y2;
                    break;
                }
                case 'Z':
                    p.close();
                    x = startX;
                    y = startY;
                    break;
                default:
                    throw new IllegalArgumentException("unknown command " + cmd + ": " + d);
            }
            last = Character.toUpperCase(cmd);
        }
        return p;
    }

    /** An SVG elliptical arc with no rotation, from (x1, y1) to (x2, y2) (SVG 1.1 appendix F.6.5). */
    private static void arc(Path p, float x1, float y1, float rx, float ry,
            boolean large, boolean sweep, float x2, float y2) {
        if (x1 == x2 && y1 == y2) return;
        if (rx == 0 || ry == 0) {
            p.lineTo(x2, y2);
            return;
        }
        double hx = (x1 - x2) / 2.0, hy = (y1 - y2) / 2.0;
        double rx2 = (double) rx * rx, ry2 = (double) ry * ry;
        double lambda = hx * hx / rx2 + hy * hy / ry2;
        double a = rx, b = ry;
        if (lambda > 1) {
            // radii too small to reach: scale them up just enough
            double s = Math.sqrt(lambda);
            a *= s;
            b *= s;
            rx2 = a * a;
            ry2 = b * b;
        }
        double num = rx2 * ry2 - rx2 * hy * hy - ry2 * hx * hx;
        double den = rx2 * hy * hy + ry2 * hx * hx;
        double coef = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
        double cxp = coef * a * hy / b;
        double cyp = -coef * b * hx / a;
        double cx = cxp + (x1 + x2) / 2.0, cy = cyp + (y1 + y2) / 2.0;
        double start = Math.toDegrees(Math.atan2((hy - cyp) / b, (hx - cxp) / a));
        double end = Math.toDegrees(Math.atan2((-hy - cyp) / b, (-hx - cxp) / a));
        double extent = end - start;
        if (sweep && extent < 0) extent += 360;
        if (!sweep && extent > 0) extent -= 360;
        RectF oval = new RectF((float) (cx - a), (float) (cy - b), (float) (cx + a), (float) (cy + b));
        p.arcTo(oval, (float) start, (float) extent, false);
    }

    private static boolean isCommand(char c) {
        return "MmLlHhVvCcSsQqTtAaZz".indexOf(c) >= 0;
    }

    private void skipSeparators() {
        while (pos < d.length()) {
            char c = d.charAt(pos);
            if (c != ' ' && c != ',' && c != '\t' && c != '\n' && c != '\r') break;
            pos++;
        }
    }

    /** An arc flag: a single 0 or 1, which SVG lets run into the next number. */
    private boolean flag() {
        skipSeparators();
        if (pos >= d.length()) throw new IllegalArgumentException("missing arc flag: " + d);
        char c = d.charAt(pos++);
        if (c == '0') return false;
        if (c == '1') return true;
        throw new IllegalArgumentException("bad arc flag " + c + ": " + d);
    }

    private float number() {
        skipSeparators();
        int begin = pos;
        int n = d.length();
        if (pos < n && (d.charAt(pos) == '-' || d.charAt(pos) == '+')) pos++;
        boolean digits = false;
        while (pos < n && Character.isDigit(d.charAt(pos))) {
            pos++;
            digits = true;
        }
        if (pos < n && d.charAt(pos) == '.') {
            pos++;
            while (pos < n && Character.isDigit(d.charAt(pos))) {
                pos++;
                digits = true;
            }
        }
        if (!digits) throw new IllegalArgumentException("expected a number at " + begin + ": " + d);
        if (pos < n && (d.charAt(pos) == 'e' || d.charAt(pos) == 'E')) {
            int mark = pos;
            pos++;
            if (pos < n && (d.charAt(pos) == '-' || d.charAt(pos) == '+')) pos++;
            if (pos < n && Character.isDigit(d.charAt(pos))) {
                while (pos < n && Character.isDigit(d.charAt(pos))) pos++;
            } else {
                pos = mark;             // not an exponent after all
            }
        }
        return Float.parseFloat(d.substring(begin, pos));
    }
}
