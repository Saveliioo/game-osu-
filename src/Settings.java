import java.awt.*;
import java.io.*;
import java.util.Properties;

/**
 * Self-contained Settings panel.
 * Holds all configurable values, handles its own rendering + input,
 * and persists values to / loads from  {@value #SETTINGS_FILE}.
 */
public class Settings {

    // ── File ─────────────────────────────────────────────────────────────────
    private static final String SETTINGS_FILE = "settings.properties";

    // ── Defaults ─────────────────────────────────────────────────────────────
    public static final float DEFAULT_MUSIC_VOLUME = 0.7f;
    public static final float DEFAULT_HIT_VOLUME   = 0.7f;
    public static final int   DEFAULT_MAX_TARGETS  = 7;

    // ── Configurable values (read by Main) ───────────────────────────────────
    public float musicVolume = DEFAULT_MUSIC_VOLUME;
    public float hitVolume   = DEFAULT_HIT_VOLUME;
    public int   maxTargets  = DEFAULT_MAX_TARGETS;

    public static final int MIN_TARGETS = 3;
    public static final int MAX_TARGETS = 10;

    // ── Internal slider state ─────────────────────────────────────────────────
    /** 0 = none, 1 = music, 2 = hit, 3 = maxTargets */
    private int draggingSlider = 0;

    private static final int SLIDER_W = 320;

    // Hit-areas (populated by draw(), consumed by handleClick/handleDrag)
    private Rectangle musicTrack, hitTrack, maxTrack;
    public  Rectangle backBounds;
    private Rectangle resetBounds;

    // ── "Saved" flash feedback ────────────────────────────────────────────────
    private int savedFlashTimer = 0;   // counts down from 90 ticks (~1.5 s)

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final Color COLOR_TILE  = new Color(40,  35,  60);
    private static final Color COLOR_TEXT  = new Color(255, 245, 230);
    private static final Color C1          = new Color(0,   255, 220);
    private static final Color C2          = new Color(255, 200,  50);
    private static final Color C3          = new Color(200, 100, 255);
    private static final Color C_RESET     = new Color(255, 100,  80);

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructor  –  load on creation
    // ═══════════════════════════════════════════════════════════════════════════

    public Settings() {
        load();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Persistence
    // ═══════════════════════════════════════════════════════════════════════════

    /** Write current values to {@value #SETTINGS_FILE}. */
    public void save() {
        Properties p = new Properties();
        p.setProperty("musicVolume", String.valueOf(musicVolume));
        p.setProperty("hitVolume",   String.valueOf(hitVolume));
        p.setProperty("maxTargets",  String.valueOf(maxTargets));
        try (OutputStream os = new FileOutputStream(SETTINGS_FILE)) {
            p.store(os, "Game Settings – edit carefully or use the in-game panel");
        } catch (IOException e) {
            System.err.println("[Settings] Could not save: " + e.getMessage());
        }
    }

    /** Read values from {@value #SETTINGS_FILE}; silently uses defaults if absent. */
    public void load() {
        File f = new File(SETTINGS_FILE);
        if (!f.exists()) return;          // first run – keep defaults
        Properties p = new Properties();
        try (InputStream is = new FileInputStream(f)) {
            p.load(is);
            musicVolume = clamp01(Float.parseFloat(p.getProperty("musicVolume",
                    String.valueOf(DEFAULT_MUSIC_VOLUME))));
            hitVolume   = clamp01(Float.parseFloat(p.getProperty("hitVolume",
                    String.valueOf(DEFAULT_HIT_VOLUME))));
            maxTargets  = clampTargets(Integer.parseInt(p.getProperty("maxTargets",
                    String.valueOf(DEFAULT_MAX_TARGETS))));
        } catch (Exception e) {
            System.err.println("[Settings] Could not load (using defaults): " + e.getMessage());
            resetToDefaults();
        }
    }

    /** Restore all values to their defaults and save immediately. */
    public void resetToDefaults() {
        musicVolume = DEFAULT_MUSIC_VOLUME;
        hitVolume   = DEFAULT_HIT_VOLUME;
        maxTargets  = DEFAULT_MAX_TARGETS;
        save();
        savedFlashTimer = 90;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Input
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Call on mouse-press while the settings screen is visible.
     * @return {@code true} when the BACK button was clicked.
     */
    public boolean handleClick(Point p) {
        if (backBounds  != null && backBounds.contains(p))  return true;
        if (resetBounds != null && resetBounds.contains(p)) { resetToDefaults(); return false; }

        if      (musicTrack != null && musicTrack.contains(p)) { draggingSlider = 1; slideTo(p.x); }
        else if (hitTrack   != null && hitTrack.contains(p))   { draggingSlider = 2; slideTo(p.x); }
        else if (maxTrack   != null && maxTrack.contains(p))   { draggingSlider = 3; slideTo(p.x); }

        return false;
    }

    /** Call on mouse-drag. */
    public void handleDrag(int mx) { if (draggingSlider > 0) slideTo(mx); }

    /** Call on mouse-release – persists to file. */
    public void releaseSlider() {
        if (draggingSlider > 0) {
            save();
            savedFlashTimer = 90;
        }
        draggingSlider = 0;
    }

    private void slideTo(int mx) {
        Rectangle track = switch (draggingSlider) {
            case 1 -> musicTrack;
            case 2 -> hitTrack;
            case 3 -> maxTrack;
            default -> null;
        };
        if (track == null) return;
        float t = Math.max(0f, Math.min(1f, (float)(mx - track.x) / track.width));
        switch (draggingSlider) {
            case 1 -> musicVolume = t;
            case 2 -> hitVolume   = t;
            case 3 -> maxTargets  = MIN_TARGETS + Math.round(t * (MAX_TARGETS - MIN_TARGETS));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Rendering
    // ═══════════════════════════════════════════════════════════════════════════

    public void draw(Graphics2D g2, int mouseX, int mouseY, int W, int H) {
        if (savedFlashTimer > 0) savedFlashTimer--;

        // Semi-transparent overlay
        g2.setColor(new Color(0, 0, 0, 215));
        g2.fillRect(0, 0, W, H);

        int cx = W / 2, cy = H / 2;
        int sx = cx - SLIDER_W / 2;

        // Title
        g2.setFont(new Font("SansSerif", Font.BOLD, 56));
        g2.setColor(COLOR_TEXT);
        String title = "SETTINGS";
        g2.drawString(title, cx - g2.getFontMetrics().stringWidth(title) / 2, cy - 180);

        // ── File path hint (small, subtle) ────────────────────────────────────
        g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g2.setColor(new Color(130, 130, 160, 160));
        String filePath = "Saved to: " + new File(SETTINGS_FILE).getAbsolutePath();
        g2.drawString(filePath, cx - g2.getFontMetrics().stringWidth(filePath) / 2, cy - 148);

        // ── Three sliders ──────────────────────────────────────────────────────
        musicTrack = drawSlider(g2, "MUSIC VOLUME",     sx, cy - 100, musicVolume, false, C1);
        hitTrack   = drawSlider(g2, "HIT SOUND VOLUME", sx, cy -   5, hitVolume,   false, C2);
        float maxNorm = (float)(maxTargets - MIN_TARGETS) / (MAX_TARGETS - MIN_TARGETS);
        maxTrack   = drawSlider(g2, "MAX TARGETS  [" + maxTargets + "]",
                sx, cy + 90, maxNorm, true, C3);

        // ── Buttons row: BACK (left) + RESET (right) ──────────────────────────
        int btnY  = cy + 185;
        int btnH  = 55;
        backBounds  = new Rectangle(cx - 270, btnY, 240, btnH);
        resetBounds = new Rectangle(cx +  30, btnY, 240, btnH);

        drawBtn(g2, backBounds,  "BACK",              mouseX, mouseY, COLOR_TILE, new Color(200, 200, 200));
        drawBtn(g2, resetBounds, "RESET TO DEFAULTS", mouseX, mouseY,
                new Color(70, 25, 25), C_RESET);

        // ── "Saved!" flash ────────────────────────────────────────────────────
        if (savedFlashTimer > 0) {
            float fAlpha = Math.min(1f, savedFlashTimer / 20f);
            g2.setFont(new Font("SansSerif", Font.BOLD, 18));
            g2.setColor(new Color(80 / 255f, 255 / 255f, 180 / 255f, fAlpha));
            String saved = "✓  Saved";
            g2.drawString(saved, cx - g2.getFontMetrics().stringWidth(saved) / 2, btnY - 12);
        }

        // ── Hint ──────────────────────────────────────────────────────────────
        g2.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2.setColor(new Color(180, 180, 180, 140));
        String hint = "[ESC] to go back  •  settings auto-save when you release a slider";
        g2.drawString(hint, cx - g2.getFontMetrics().stringWidth(hint) / 2, cy + 262);
    }

    private Rectangle drawSlider(Graphics2D g2, String label,
                                 int x, int y, float value,
                                 boolean ticks, Color accent) {
        // Label
        g2.setFont(new Font("SansSerif", Font.BOLD, 19));
        g2.setColor(new Color(200, 200, 255, 215));
        g2.drawString(label, x, y + 14);

        // Right-side percentage hint (continuous sliders only)
        if (!ticks) {
            String pct = (int)(value * 100) + "%";
            g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g2.setColor(new Color(180, 180, 180, 180));
            g2.drawString(pct, x + SLIDER_W - g2.getFontMetrics().stringWidth(pct), y + 14);
        }

        int trackY = y + 26, trackH = 8;
        int fillW  = Math.max(0, (int)(SLIDER_W * value));
        int knobX  = x + fillW, knobR = 9;

        // Track background
        g2.setColor(new Color(55, 50, 80));
        g2.fillRoundRect(x, trackY, SLIDER_W, trackH, trackH, trackH);
        // Filled portion
        if (fillW > 0) {
            g2.setColor(accent);
            g2.fillRoundRect(x, trackY, fillW, trackH, trackH, trackH);
        }
        // Knob
        g2.setColor(Color.WHITE);
        g2.fillOval(knobX - knobR, trackY + trackH / 2 - knobR, knobR * 2, knobR * 2);
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(accent.darker());
        g2.drawOval(knobX - knobR, trackY + trackH / 2 - knobR, knobR * 2, knobR * 2);

        // Tick marks + labels for integer slider (3–10)
        if (ticks) {
            int steps = MAX_TARGETS - MIN_TARGETS;
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            for (int i = 0; i <= steps; i++) {
                int tx = x + (int)((float) i / steps * SLIDER_W);
                g2.setColor(new Color(180, 180, 180, 130));
                g2.fillRect(tx, trackY - 5, 1, 5);
                String num = String.valueOf(MIN_TARGETS + i);
                g2.setColor(new Color(180, 180, 180, 170));
                g2.drawString(num, tx - g2.getFontMetrics().stringWidth(num) / 2, trackY - 7);
            }
        }

        return new Rectangle(x, trackY - knobR, SLIDER_W, trackH + knobR * 2);
    }

    /** Button renderer – supports custom background and border/text colours. */
    private void drawBtn(Graphics2D g2, Rectangle r, String s, int mx, int my,
                         Color bg, Color accent) {
        boolean hovered = r.contains(mx, my);
        g2.setColor(hovered ? bg.brighter() : bg);
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 20, 20);
        g2.setStroke(new BasicStroke(2));
        g2.setColor(hovered ? Color.WHITE : accent);
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 20, 20);
        g2.setFont(new Font("SansSerif", Font.BOLD, 19));
        g2.setColor(hovered ? Color.WHITE : accent);
        g2.drawString(s, r.x + (r.width - g2.getFontMetrics().stringWidth(s)) / 2, r.y + 36);
    }

    /** Legacy single-accent overload – keeps Main/other callers compatible. */
    void drawBtn(Graphics2D g2, Rectangle r, String s, int mx, int my) {
        drawBtn(g2, r, s, mx, my, COLOR_TILE, new Color(200, 200, 200));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private float clamp01(float v)       { return Math.max(0f, Math.min(1f, v)); }
    private int   clampTargets(int v)    { return Math.max(MIN_TARGETS, Math.min(MAX_TARGETS, v)); }
}