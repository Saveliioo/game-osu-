import java.awt.*;
import java.awt.geom.Path2D;

public class DragEvent {

    // ── State machine ─────────────────────────────────────────────────────────
    public enum Phase { ACTIVE, SUCCESS, FAIL }
    public Phase phase = Phase.ACTIVE;

    // ── Layout ────────────────────────────────────────────────────────────────
    private final int[] tileX, tileY;
    private final int length;

    // ── Interaction state ─────────────────────────────────────────────────────
    private boolean dragging     = false;
    private float   dragProgress = 0f;
    private int     dragPixelX   = 0;
    private int     dragPixelY   = 0;

    // ── Animation timers ──────────────────────────────────────────────────────
    private int resultTimer = 55;

    // public flag: Main polls this to know when to clean up
    public boolean finished = false;

    // ── Visual constants ──────────────────────────────────────────────────────
    private static final Color C_BORDER  = Color.WHITE;
    private static final Color C_START   = new Color(40, 210, 80);
    private static final Color C_DEST    = Color.WHITE;
    private static final Color C_ARROW   = Color.WHITE;
    private static final Color C_DRAG    = new Color(100, 255, 160);
    private static final Color C_FAIL    = new Color(255, 60, 60);
    private static final Color C_SUCCESS = new Color(80, 255, 180);
    private static final int   SCORE_BONUS    = 150;
    private static final int   SCORE_PENALTY  = 60;

    // ── Direction (0=right, 1=left, 2=down, 3=up) ────────────────────────────
    private final int dir;

    // ── Cached tile half-size (set on first draw call) ────────────────────────
    private int halfTile = 0;

    public DragEvent(int startGX, int startGY, int dir, int len) {
        this.dir    = dir;
        this.length = len;
        tileX = new int[len];
        tileY = new int[len];
        int dx = (dir == 0) ? 1 : (dir == 1) ? -1 : 0;
        int dy = (dir == 2) ? 1 : (dir == 3) ? -1 : 0;
        for (int i = 0; i < len; i++) {
            tileX[i] = startGX + dx * i;
            tileY[i] = startGY + dy * i;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update
    // ─────────────────────────────────────────────────────────────────────────

    public int update() {
        if (phase == Phase.SUCCESS || phase == Phase.FAIL) {
            if (--resultTimer <= 0) finished = true;
        }
        return 0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input handlers  – called by Main
    // ─────────────────────────────────────────────────────────────────────────

    public int handlePress(int mx, int my, int ox, int oy, int tileSize) {
        if (phase != Phase.ACTIVE) return 0;
        int sx = ox + tileX[0] * tileSize;
        int sy = oy + tileY[0] * tileSize;
        if (mx >= sx && mx < sx + tileSize && my >= sy && my < sy + tileSize) {
            dragging     = true;
            dragProgress = 0f;
            dragPixelX   = mx;
            dragPixelY   = my;
        }
        return 0;
    }

    public int handleDrag(int mx, int my, int ox, int oy, int tileSize) {
        if (!dragging || phase != Phase.ACTIVE) return 0;

        int startCX = ox + tileX[0] * tileSize + tileSize / 2;
        int startCY = oy + tileY[0] * tileSize + tileSize / 2;
        int endCX   = ox + tileX[length - 1] * tileSize + tileSize / 2;
        int endCY   = oy + tileY[length - 1] * tileSize + tileSize / 2;

        float totalLen = dist(startCX, startCY, endCX, endCY);
        if (totalLen < 1) return 0;

        // Project mouse onto the rail axis and clamp 0..1
        boolean horizontal = (dir == 0 || dir == 1);
        float raw = horizontal ? (mx - startCX) : (my - startCY);
        float span = horizontal ? (endCX - startCX) : (endCY - startCY);
        dragProgress = Math.max(0f, Math.min(1f, raw / span));

        // Derive pixel position from progress — cube is always on the rail
        dragPixelX = startCX + Math.round((endCX - startCX) * dragProgress);
        dragPixelY = startCY + Math.round((endCY - startCY) * dragProgress);

        // Snap to exactly 1 once within half a tile of the dest centre
        if (dist(dragPixelX, dragPixelY, endCX, endCY) < tileSize * 0.5f) dragProgress = 1f;

        return 0;
    }

    public int handleRelease() {
        if (!dragging || phase != Phase.ACTIVE) return 0;
        dragging = false;
        // Success only if the player dragged all the way to the destination
        return resolve(dragProgress >= 1f);
    }

    private int resolve(boolean success) {
        dragging     = false;
        dragProgress = success ? 1f : dragProgress;
        phase        = success ? Phase.SUCCESS : Phase.FAIL;
        resultTimer  = 55;
        return success ? SCORE_BONUS : -SCORE_PENALTY;
    }

    private float dist(float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────────────────

    public void draw(Graphics2D g2, int ox, int oy, int tileSize) {
        halfTile = tileSize / 2;

        float a = (phase == Phase.SUCCESS || phase == Phase.FAIL)
                ? Math.min(1f, resultTimer / 20f)
                : 1f;

        Color mainColor = (phase == Phase.SUCCESS) ? C_SUCCESS
                : (phase == Phase.FAIL)    ? C_FAIL
                : C_BORDER;

        // ── Outer border around the whole sequence ────────────────────────────
        int minX = tileX[0], maxX = tileX[0], minY = tileY[0], maxY = tileY[0];
        for (int i = 1; i < length; i++) {
            if (tileX[i] < minX) minX = tileX[i]; else if (tileX[i] > maxX) maxX = tileX[i];
            if (tileY[i] < minY) minY = tileY[i]; else if (tileY[i] > maxY) maxY = tileY[i];
        }
        int pad = 6;
        int bx  = ox + minX * tileSize - pad;
        int by  = oy + minY * tileSize - pad;
        int bw  = (maxX - minX + 1) * tileSize + pad * 2;
        int bh  = (maxY - minY + 1) * tileSize + pad * 2;

        g2.setStroke(new BasicStroke(2f));
        g2.setColor(withAlpha(mainColor, a));
        g2.drawRoundRect(bx, by, bw, bh, 16, 16);

        // ── Draw each tile ────────────────────────────────────────────────────
        for (int i = 0; i < length; i++) {
            int tx = ox + tileX[i] * tileSize;
            int ty = oy + tileY[i] * tileSize;
            int cx = tx + halfTile;
            int cy = ty + halfTile;

            float tileFrac = (float) i / (length - 1);
            if (dragging && tileFrac <= dragProgress && i > 0 && i < length - 1) {
                g2.setColor(withAlpha(C_DRAG, a * 0.25f));
                g2.fillRoundRect(tx + 4, ty + 4, tileSize - 8, tileSize - 8, 14, 14);
            }

            if (i == 0) {
                if (dragging) {
                    g2.setStroke(new BasicStroke(2f));
                    g2.setColor(withAlpha(C_START, a * 0.30f));
                    g2.drawRoundRect(tx + 8, ty + 8, tileSize - 16, tileSize - 16, 12, 12);
                } else {
                    g2.setColor(withAlpha(C_START, a));
                    g2.fillRoundRect(tx + 8, ty + 8, tileSize - 16, tileSize - 16, 12, 12);
                    drawPlayArrow(g2, cx, cy, halfTile - 10, dir, withAlpha(Color.WHITE, a));
                }
            } else if (i == length - 1) {
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(withAlpha(C_DEST, a));
                int hs = halfTile - 10;
                g2.drawRoundRect(cx - hs, cy - hs, hs * 2, hs * 2, 10, 10);
                g2.fillRect(cx - 1, cy - 1, 3, 3);
            } else {
                g2.setFont(new Font("SansSerif", Font.BOLD, 20));
                FontMetrics fm = g2.getFontMetrics();
                String arrow = dirChar(dir);
                g2.setColor(withAlpha(C_ARROW, a));
                g2.drawString(arrow, cx - fm.stringWidth(arrow) / 2, cy + fm.getAscent() / 2 - 2);
            }
        }

        // ── Dragging cube ─────────────────────────────────────────────────────
        if (dragging) {
            int cubeSize = tileSize - 16;
            int cubeX    = dragPixelX - cubeSize / 2;
            int cubeY    = dragPixelY - cubeSize / 2;
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRoundRect(cubeX + 3, cubeY + 4, cubeSize, cubeSize, 12, 12);
            g2.setColor(withAlpha(C_START, a));
            g2.fillRoundRect(cubeX, cubeY, cubeSize, cubeSize, 12, 12);
            drawPlayArrow(g2, dragPixelX, dragPixelY, halfTile - 10, dir, withAlpha(Color.WHITE, a));
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(withAlpha(C_DRAG, a * 0.85f));
            g2.drawRoundRect(cubeX, cubeY, cubeSize, cubeSize, 12, 12);
        }

        // ── Result label ──────────────────────────────────────────────────────
        if (phase == Phase.SUCCESS || phase == Phase.FAIL) {
            String label = (phase == Phase.SUCCESS) ? "+" + SCORE_BONUS : "-" + SCORE_PENALTY;
            g2.setFont(new Font("SansSerif", Font.BOLD, 36));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(withAlpha(mainColor, a));
            g2.drawString(label, bx + bw / 2 - fm.stringWidth(label) / 2, by - 14);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void drawPlayArrow(Graphics2D g2, int cx, int cy, int r, int direction, Color c) {
        double tipAngle = switch (direction) {
            case 0  ->  0;
            case 1  ->  Math.PI;
            case 2  ->  Math.PI / 2;
            default -> -Math.PI / 2;
        };
        Path2D tri = new Path2D.Float();
        tri.moveTo(cx + Math.cos(tipAngle)         * r,        cy + Math.sin(tipAngle)         * r);
        tri.lineTo(cx + Math.cos(tipAngle + 2.4)   * (r * 0.75), cy + Math.sin(tipAngle + 2.4)   * (r * 0.75));
        tri.lineTo(cx + Math.cos(tipAngle - 2.4)   * (r * 0.75), cy + Math.sin(tipAngle - 2.4)   * (r * 0.75));
        tri.closePath();
        g2.setColor(c);
        g2.fill(tri);
    }

    private String dirChar(int d) {
        return switch (d) { case 0 -> ">"; case 1 -> "<"; case 2 -> "v"; default -> "^"; };
    }

    private Color withAlpha(Color c, float a) {
        return new Color(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f,
                Math.max(0f, Math.min(1f, a)));
    }

    // ── Public query helpers ──────────────────────────────────────────────────
    public boolean isActive() { return phase == Phase.ACTIVE; }
    public int     getScoreBonus()    { return SCORE_BONUS;   }
    public int     getScorePenalty()  { return SCORE_PENALTY; }

    public boolean occupiesTile(int gx, int gy) {
        for (int i = 0; i < length; i++)
            if (tileX[i] == gx && tileY[i] == gy) return true;
        return false;
    }
}