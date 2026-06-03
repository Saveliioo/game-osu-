import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

public class Renderer {

    // ── Hit-test bounds (populated each frame, consumed by InputHandler) ──────

    public Rectangle resumeBounds    = new Rectangle();
    public Rectangle settingsBounds  = new Rectangle();   // was declared but never set — now wired up
    public Rectangle mainMenuBounds  = new Rectangle();
    public Rectangle exitBounds      = new Rectangle();
    public Rectangle gameOverMenuBounds = new Rectangle();
    public Rectangle menuExitBounds  = new Rectangle();
    public Rectangle[] songBounds;

    private BufferedImage memeImage;

    public Renderer() {
        try {
            File memeFile = new File("C:\\Users\\nesto\\Desktop\\OSU_Question\\Gemini_Generated_Image_.png");
            if (!memeFile.exists()) {
                memeFile = new File(AppConfig.image("meme.png"));
            }
            memeImage = ImageIO.read(memeFile);
        } catch (IOException e) {
            System.err.println("[Renderer] Could not load meme image: " + e.getMessage());
        }
    }

    public int[] getGridOrigin(GameEngine engine, JPanel panel) {
        return new int[]{
                (panel.getWidth()  - GameEngine.GRID_PX) / 2 + engine.shakeX,
                (panel.getHeight() - GameEngine.GRID_PX) / 2 + 30 + engine.shakeY
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Main draw entry point
    // ══════════════════════════════════════════════════════════════════════════

    public void draw(Graphics2D g, GameEngine e, JPanel p, int mx, int my) {
        int w  = p.getWidth();
        int h  = p.getHeight();
        int cx = w / 2;
        int cy = h / 2;

        // Clear
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Consolas", Font.BOLD, 20));

        if (e.inSongMenu) {
            drawSongMenu(g, e, w, h, cx, mx, my);
            return;
        }

        drawGameplay(g, e, p, cx, cy, w, h, mx, my);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Song-selection menu
    // ══════════════════════════════════════════════════════════════════════════

    private void drawSongMenu(Graphics2D g, GameEngine e, int w, int h, int cx, int mx, int my) {
        // Ambient particles
        for (Particle pt : e.particles) pt.draw(g);

        g.setFont(new Font("Consolas", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        g.drawString("SELECT TRACK", cx - 70, 50);

        songBounds = new Rectangle[e.songList.size()];
        for (int i = 0; i < e.songList.size(); i++) {
            songBounds[i] = new Rectangle(cx - 150, 100 + i * 40, 300, 30);
            btn(g, songBounds[i], e.songList.get(i).title, mx, my);
        }

        menuExitBounds.setBounds(cx - 100, h - 50, 200, 30);
        btn(g, menuExitBounds, "EXIT", mx, my);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Gameplay (grid, HUD, overlays)
    // ══════════════════════════════════════════════════════════════════════════

    private void drawGameplay(Graphics2D g, GameEngine e, JPanel p,
                              int cx, int cy, int w, int h, int mx, int my) {
        int[] o  = getGridOrigin(e, p);
        int   ox = o[0];
        int   oy = o[1];

        // Grid lines
        g.setColor(Color.DARK_GRAY);
        for (int i = 0; i < GameEngine.SIZE * GameEngine.SIZE; i++) {
            g.drawRect(ox + (i % GameEngine.SIZE) * GameEngine.TILE_SIZE,
                    oy + (i / GameEngine.SIZE) * GameEngine.TILE_SIZE,
                    GameEngine.TILE_SIZE, GameEngine.TILE_SIZE);
        }

        // Targets & particles
        for (Target t : e.targets)    t.draw(g, ox, oy, GameEngine.TILE_SIZE);
        for (Particle pt : e.particles) pt.draw(g);

        g.setFont(new Font("Consolas", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        g.drawString("SCORE: " + e.score, cx - 50, 30);
        g.fillRect(cx - 150, 40, (int) (300 * e.getAccuracy()), 5);

        if (e.restarting || e.resumeCountdown > 0) {
            g.drawString("WAIT...", cx - 35, cy);

        } else if (e.settingsOpen) {
            e.settings.draw(g, mx, my, w, h);

        } else if (e.isPaused) {
            drawPauseMenu(g, cx, cy, mx, my);

        } else if (e.gameOver) {
            drawGameOverScreen(g, e, w, h, cx, cy, mx, my);
        }
    }

    // ── Pause menu ────────────────────────────────────────────────────────────

    private void drawPauseMenu(Graphics2D g, int cx, int cy, int mx, int my) {
        // Semi-transparent backdrop
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, cx * 2, cy * 2);

        resumeBounds  .setBounds(cx - 100, cy - 60, 200, 30);
        settingsBounds.setBounds(cx - 100, cy - 20, 200, 30);
        mainMenuBounds.setBounds(cx - 100, cy + 20, 200, 30);
        exitBounds    .setBounds(cx - 100, cy + 60, 200, 30);

        btn(g, resumeBounds,   "RESUME",   mx, my);
        btn(g, settingsBounds, "SETTINGS", mx, my);
        btn(g, mainMenuBounds, "MENU",     mx, my);
        btn(g, exitBounds,     "EXIT",     mx, my);
    }

    private void drawGameOverScreen(Graphics2D g, GameEngine e,
                                    int w, int h, int cx, int cy, int mx, int my) {
        if (e.score < 0) {
            g.setColor(new Color(10, 10, 10, 200));
            g.fillRect(0, 0, w, h);

            if (!e.showMeme) {
                float flash = Math.max(0f, 1.0f - (e.gameOverTicks / 90.0f));
                g.setColor(new Color(255, 255, 255, (int) (flash * 255)));
                g.fillRect(0, 0, w, h);
            } else if (memeImage != null) {
                float     alpha    = Math.min(1.0f, e.memeTicks / 120.0f);
                Composite original = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.drawImage(memeImage, 0, 0, w, h, null);
                g.setComposite(original);

                g.setColor(new Color(0, 0, 0, 120));
                g.fillRect(0, 0, w, h);
            }
        }

        g.setFont(new Font("Consolas", Font.BOLD, 20));
        g.setColor(e.score < 0 ? Color.RED : Color.GREEN);
        g.drawString(e.score < 0 ? "GAME OVER" : "PASSED", cx - 50, cy - 50);

        gameOverMenuBounds.setBounds(cx - 100, cy,      200, 30);
        exitBounds        .setBounds(cx - 100, cy + 40, 200, 30);

        btn(g, gameOverMenuBounds, "MENU", mx, my);
        btn(g, exitBounds,         "EXIT", mx, my);
    }
    private void btn(Graphics2D g, Rectangle r, String s, int mx, int my) {
        g.setColor(r.contains(mx, my) ? Color.GRAY : Color.DARK_GRAY);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Consolas", Font.BOLD, 20));
        g.drawString(s, r.x + 10, r.y + 22);
    }
}