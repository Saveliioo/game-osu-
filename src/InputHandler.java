import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class InputHandler {
    private final GameEngine engine;
    private final Renderer renderer;
    private final Main panel;
    public int mouseX = 0;
    public int mouseY = 0;

    public InputHandler(GameEngine engine, Renderer renderer, Main panel) {
        this.engine = engine;
        this.renderer = renderer;
        this.panel = panel;
    }

    public void attach() {
        panel.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                mouseX = e.getX(); mouseY = e.getY(); panel.repaint();
            }
            @Override public void mouseDragged(MouseEvent e) {
                mouseX = e.getX(); mouseY = e.getY();
                if (engine.isPaused && engine.settingsOpen) {
                    engine.settings.handleDrag(e.getX()); panel.repaint();
                }
            }
        });

        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                panel.requestFocusInWindow(); Point p = e.getPoint();

                if (engine.inSongMenu) {
                    if (renderer.menuExitBounds != null && renderer.menuExitBounds.contains(p)) System.exit(0);
                    if (renderer.songBounds != null) {
                        for (int i = 0; i < engine.songList.size(); i++) {
                            if (renderer.songBounds[i] != null && renderer.songBounds[i].contains(p)) {
                                engine.inSongMenu = false; engine.resetGame();
                                String path = engine.songList.get(i).audioPath;
                                engine.analyzeAudioAndGenerateMap(path);
                                engine.pendingAudioPath = path; break;
                            }
                        }
                    } return;
                }

                if (engine.gameOver) {
                    if (renderer.gameOverMenuBounds != null && renderer.gameOverMenuBounds.contains(p)) {
                        engine.inSongMenu = true; if (engine.bgMusicClip != null) engine.bgMusicClip.stop(); engine.resetGame();
                    } else if (renderer.exitBounds != null && renderer.exitBounds.contains(p)) {
                        System.exit(0);
                    }
                    panel.repaint(); return;
                }

                if (engine.isPaused) {
                    if (engine.settingsOpen) {
                        if (engine.settings.handleClick(p)) engine.settingsOpen = false;
                    } else {
                        if (renderer.resumeBounds != null && renderer.resumeBounds.contains(p)) engine.triggerResume();
                        else if (renderer.mainMenuBounds != null && renderer.mainMenuBounds.contains(p)) {
                            engine.isPaused = false; engine.inSongMenu = true;
                            if (engine.bgMusicClip != null) engine.bgMusicClip.stop(); engine.resetGame();
                        }
                        else if (renderer.settingsBounds != null && renderer.settingsBounds.contains(p)) engine.settingsOpen = true;
                        else if (renderer.exitBounds != null && renderer.exitBounds.contains(p)) System.exit(0);
                    }
                    panel.repaint(); return;
                }

                if (engine.resumeCountdown > 0 || engine.restarting) return;

                int dmg = (e.getButton() == MouseEvent.BUTTON3) ? 2 : 1;
                handleClick(e.getX(), e.getY(), dmg);
            }
            @Override public void mouseReleased(MouseEvent e) { engine.settings.releaseSlider(); }
        });

        panel.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (engine.inSongMenu || engine.gameOver) return;
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    if (engine.isPaused && engine.settingsOpen) engine.settingsOpen = false;
                    else if (engine.isPaused) engine.triggerResume();
                    else if (engine.resumeCountdown == 0) engine.isPaused = true;
                    panel.repaint();
                } else if (!engine.isPaused && engine.resumeCountdown == 0 && !engine.restarting) {
                    if (e.getKeyChar() == '1') handleClick(mouseX, mouseY, 1);
                    else if (e.getKeyChar() == '2') handleClick(mouseX, mouseY, 2);
                }
            }
        });
    }

    private void handleClick(int mx, int my, int dmg) {
        int[] o = renderer.getGridOrigin(engine, panel);
        int tx = (mx - o[0]) / GameEngine.TILE_SIZE, ty = (my - o[1]) / GameEngine.TILE_SIZE;
        boolean hit = false;
        for (Target t : engine.targets) {
            if (t.x == tx && t.y == ty && !t.isFailing) {
                if (t.isReady) {
                    if (dmg > t.hp) {
                        engine.score -= t.maxHp * 10;
                        engine.totalFails++; t.triggerFail(); engine.shakeIntensity = 10; hit = true; break;
                    }
                    engine.playSound("high_hit.wav");
                    t.hp -= dmg;
                    if (t.hp <= 0) {
                        engine.score += t.maxHp * 2; engine.actualEarnedScore += t.maxHp * 2; engine.totalHits++;
                        engine.colorHits[Math.min(t.maxHp - 1, 4)]++;
                        engine.createExplosion(mx, my, t.baseColor, t.maxHp); t.shouldRemove = true;
                    } else {
                        engine.shakeIntensity = (dmg == 2) ? 16 : 12;
                        engine.createExplosion(mx, my, java.awt.Color.WHITE, 0);
                    }
                } else {
                    engine.score -= t.maxHp * 10; engine.totalFails++; t.triggerFail();
                }
                hit = true; break;
            }
        }
        if (!hit) { engine.score -= 5; engine.shakeIntensity = 6; }
    }
}