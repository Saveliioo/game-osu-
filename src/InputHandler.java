import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

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
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                panel.repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                mouseX = e.getX();
                mouseY = e.getY();
                if (engine.settingsOpen) {
                    engine.settings.handleDrag(e.getX());
                    panel.repaint();
                }
            }
        });

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                panel.requestFocusInWindow();
                Point p = e.getPoint();

                if (engine.inSongMenu) {
                    if (renderer.menuExitBounds != null && renderer.menuExitBounds.contains(p)) {
                        System.exit(0);
                    }
                    if (renderer.songBounds != null) {
                        for (int i = 0; i < renderer.songBounds.length; i++) {
                            if (renderer.songBounds[i] != null && renderer.songBounds[i].contains(p)) {
                                engine.inSongMenu = false;
                                String path = engine.songList.get(i).audioPath;
                                engine.analyzeAudioAndGenerateMap(path);
                                engine.playMusic(path);
                                panel.repaint();
                                break;
                            }
                        }
                    }
                    return;
                }

                if (engine.gameOver) {
                    if (renderer.gameOverMenuBounds.contains(p)) {
                        engine.inSongMenu = true;
                        engine.resetGame();
                        panel.repaint();
                    } else if (renderer.exitBounds.contains(p)) {
                        System.exit(0);
                    }
                    return;
                }

                if (engine.isPaused) {
                    if (engine.settingsOpen) {
                        if (engine.settings.handleClick(p)) {
                            engine.settingsOpen = false;
                        }
                        panel.repaint();
                        return;
                    }
                    if (renderer.resumeBounds.contains(p)) {
                        engine.triggerResume();
                    } else if (renderer.mainMenuBounds.contains(p)) {
                        engine.inSongMenu = true;
                        engine.resetGame();
                    } else if (renderer.exitBounds.contains(p)) {
                        System.exit(0);
                    }
                    panel.repaint();
                    return;
                }

                int dmg = e.getButton() == MouseEvent.BUTTON3 ? 2 : 1;
                handleClick(e.getX(), e.getY(), dmg);
                panel.repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (engine.settingsOpen) {
                    engine.settings.releaseSlider();
                    panel.repaint();
                }
            }
        });

        panel.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && !engine.inSongMenu && !engine.gameOver && !engine.restarting && engine.resumeCountdown <= 0) {
                    if (engine.settingsOpen) {
                        engine.settingsOpen = false;
                    } else {
                        engine.isPaused = !engine.isPaused;
                    }
                    panel.repaint();
                }
                if (!engine.inSongMenu && !engine.gameOver && !engine.isPaused && !engine.restarting && engine.resumeCountdown <= 0) {
                    int dmg = 0;
                    if (e.getKeyCode() == KeyEvent.VK_Z) dmg = 1;
                    if (e.getKeyCode() == KeyEvent.VK_X) dmg = 2;
                    if (dmg > 0) {
                        handleClick(mouseX, mouseY, dmg);
                        panel.repaint();
                    }
                }
            }
        });
    }

    private void handleClick(int mx, int my, int dmg) {
        int[] o = renderer.getGridOrigin(engine, panel);
        int tx = (mx - o[0]) / GameEngine.TILE_SIZE;
        int ty = (my - o[1]) / GameEngine.TILE_SIZE;
        boolean hit = false;

        for (Target t : engine.targets) {
            if (t.x == tx && t.y == ty && !t.isFailing) {
                if (t.isReady) {
                    if (dmg > t.hp) {
                        engine.score -= t.maxHp * 10;
                        engine.totalFails++;
                        t.triggerFail();
                        engine.shakeIntensity = 10;
                        hit = true;
                        break;
                    }
                    engine.playSound("high_hit.wav");
                    t.hp -= dmg;
                    if (t.hp <= 0) {
                        engine.score += t.maxHp * 2;
                        engine.actualEarnedScore += t.maxHp * 2;
                        engine.totalHits++;
                        engine.colorHits[Math.min(t.maxHp - 1, 4)]++;
                        engine.createExplosion(mx, my, t.baseColor, t.maxHp);
                        t.shouldRemove = true;
                    } else {
                        engine.shakeIntensity = (dmg == 2) ? 16 : 12;
                        engine.createExplosion(mx, my, java.awt.Color.WHITE, 0);
                    }
                } else {
                    engine.score -= t.maxHp * 10;
                    engine.totalFails++;
                    t.triggerFail();
                    engine.shakeIntensity = 10;
                }
                hit = true;
                break;
            }
        }
        if (!hit && tx >= 0 && tx < GameEngine.SIZE && ty >= 0 && ty < GameEngine.SIZE) {
            engine.score -= 5;
            engine.shakeIntensity = 5;
            engine.createExplosion(mx, my, java.awt.Color.WHITE, 0);
        }
    }
}