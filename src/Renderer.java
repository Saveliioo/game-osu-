import java.awt.*;
import javax.swing.JPanel;
import javax.imageio.ImageIO;
import java.io.File;

public class Renderer {
    public Rectangle resumeBounds = new Rectangle();
    public Rectangle mainMenuBounds = new Rectangle();
    public Rectangle settingsBounds = new Rectangle();
    public Rectangle exitBounds = new Rectangle();
    public Rectangle gameOverMenuBounds = new Rectangle();
    public Rectangle menuExitBounds = new Rectangle();
    public Rectangle[] songBounds;
    private Image img;

    public Renderer() {
        try {
            img = ImageIO.read(new File("E:\\OSU_Question\\Gemini_Generated_Image_.png"));
        } catch (Exception e) {
        }
    }

    public int[] getGridOrigin(GameEngine engine, JPanel panel) {
        return new int[]{
                (panel.getWidth() - GameEngine.GRID_PX) / 2 + engine.shakeX,
                (panel.getHeight() - GameEngine.GRID_PX) / 2 + 30 + engine.shakeY
        };
    }

    public void draw(Graphics2D g, GameEngine e, JPanel p, int mx, int my) {
        int w = p.getWidth();
        int h = p.getHeight();
        int cx = w / 2;
        int cy = h / 2;

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Consolas", Font.BOLD, 20));

        if (e.inSongMenu) {
            g.drawString("SELECT TRACK", cx - 70, 50);
            songBounds = new Rectangle[e.songList.size()];
            for (int i = 0; i < e.songList.size(); i++) {
                songBounds[i] = new Rectangle(cx - 150, 100 + i * 40, 300, 30);
                btn(g, songBounds[i], e.songList.get(i).title, mx, my);
            }
            menuExitBounds.setBounds(cx - 100, h - 50, 200, 30);
            btn(g, menuExitBounds, "EXIT", mx, my);
            return;
        }

        int[] o = getGridOrigin(e, p);
        int ox = o[0];
        int oy = o[1];

        g.setColor(Color.DARK_GRAY);
        for (int i = 0; i < 49; i++) {
            g.drawRect(ox + (i % 7) * 75, oy + (i / 7) * 75, 75, 75);
        }

        for (Target t : e.targets) {
            t.draw(g, ox, oy, 75);
        }
        for (Particle pt : e.particles) {
            pt.draw(g);
        }

        g.setColor(Color.WHITE);
        g.drawString("SCORE: " + e.score, cx - 50, 30);
        g.fillRect(cx - 150, 40, (int) (300 * e.getAccuracy()), 5);

        if (e.restarting || e.resumeCountdown > 0) {
            g.drawString("WAIT...", cx - 35, cy);
        } else if (e.isPaused) {
            resumeBounds.setBounds(cx - 100, cy - 40, 200, 30);
            mainMenuBounds.setBounds(cx - 100, cy, 200, 30);
            exitBounds.setBounds(cx - 100, cy + 40, 200, 30);

            btn(g, resumeBounds, "RESUME", mx, my);
            btn(g, mainMenuBounds, "MENU", mx, my);
            btn(g, exitBounds, "EXIT", mx, my);
        } else if (e.gameOver) {
            if (e.score < 0) {
                g.setColor(new Color(10, 10, 10, 200));
                g.fillRect(0, 0, w, h);

                if (!e.showMeme) {
                    float flash = Math.max(0f, 1.0f - (e.gameOverTicks / 90.0f));
                    g.setColor(new Color(255, 255, 255, (int) (flash * 255)));
                    g.fillRect(0, 0, w, h);
                } else if (img != null) {
                    float alpha = Math.min(1.0f, e.memeTicks / 120.0f);
                    Composite originalComposite = g.getComposite();

                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g.drawImage(img, 0, 0, w, h, null);
                    g.setComposite(originalComposite);

                    g.setColor(new Color(0, 0, 0, 120));
                    g.fillRect(0, 0, w, h);
                }
            }

            g.setColor(e.score < 0 ? Color.RED : Color.GREEN);
            g.drawString(e.score < 0 ? "GAME OVER" : "PASSED", cx - 50, cy - 50);

            gameOverMenuBounds.setBounds(cx - 100, cy, 200, 30);
            exitBounds.setBounds(cx - 100, cy + 40, 200, 30);

            btn(g, gameOverMenuBounds, "MENU", mx, my);
            btn(g, exitBounds, "EXIT", mx, my);
        }
    }

    private void btn(Graphics2D g, Rectangle r, String s, int mx, int my) {
        g.setColor(r.contains(mx, my) ? Color.GRAY : Color.DARK_GRAY);
        g.fillRect(r.x, r.y, r.width, r.height);
        g.setColor(Color.WHITE);
        g.drawString(s, r.x + 10, r.y + 22);
    }
}