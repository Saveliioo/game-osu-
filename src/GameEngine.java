import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.SwingUtilities;

public class GameEngine {

    // ── Grid constants ────────────────────────────────────────────────────────

    public static final int SIZE      = 7;
    public static final int TILE_SIZE = 75;
    public static final int GRID_PX   = SIZE * TILE_SIZE;

    // ── Target colours (indexed by hp 1–5) ───────────────────────────────────

    public static final Color C1 = new Color(  0, 255, 220);
    public static final Color C2 = new Color(255, 200,  50);
    public static final Color C3 = new Color(200, 100, 255);
    public static final Color C4 = new Color(255, 140,   0);
    public static final Color C5 = new Color(255,  55,  90);

    // ── Scoring ───────────────────────────────────────────────────────────────

    public int   score              = 50;
    public int   totalPossibleScore = 0;
    public int   actualEarnedScore  = 0;

    // ── Game-over state ───────────────────────────────────────────────────────

    public boolean gameOver      = false;
    public int     gameOverTicks = 0;
    public boolean showMeme      = false;
    public int     memeTicks     = 0;

    // ── Wave / flow ───────────────────────────────────────────────────────────

    public int    waveNumber      = 0;
    public boolean inSongMenu     = true;
    public boolean restarting     = false;
    public int     restartTimer   = 0;
    public boolean isPaused       = false;
    public boolean settingsOpen   = false;
    public int     resumeCountdown = 0;
    public String  pendingAudioPath = null;

    // ── Statistics ────────────────────────────────────────────────────────────

    public int   totalHits  = 0;
    public int   totalFails = 0;
    public int[] colorHits  = new int[5];

    // ── Screen shake ──────────────────────────────────────────────────────────

    public int shakeIntensity = 0;
    public int shakeX         = 0;
    public int shakeY         = 0;

    // ── Collections ───────────────────────────────────────────────────────────

    public final List<Song>          songList      = new ArrayList<>();
    public final List<Note>          activeBeatmap = new ArrayList<>();
    public final ArrayList<Target>   targets       = new ArrayList<>();
    public final ArrayList<Particle> particles     = new ArrayList<>();
    public       List<int[][]>       patterns;

    // ── Sub-systems ───────────────────────────────────────────────────────────

    public final Settings        settings;
    public final AudioManager    audio;
    public final BeatmapAnalyzer analyzer;

    // ── Shared random + object pools ─────────────────────────────────────────

    public final Random random = new Random();

    private final ArrayDeque<Target>   targetPool   = new ArrayDeque<>();
    private final ArrayDeque<Particle> particlePool = new ArrayDeque<>();

    // ══════════════════════════════════════════════════════════════════════════
    // Construction
    // ══════════════════════════════════════════════════════════════════════════

    public GameEngine() {
        settings = new Settings();
        audio    = new AudioManager(settings);
        analyzer = new BeatmapAnalyzer(audio, random);

        try {
            patterns = PatternLoader.load("patterns.txt");
        } catch (Exception ignored) {}

        loadSongList();
    }
    private void loadSongList() {
        songList.add(new Song("Recall",     AppConfig.music("gabriawll - Recall [NCS Release].wav")));
        songList.add(new Song("Old School", AppConfig.music("More Plastic - Old School [NCS Release].wav")));
        songList.add(new Song("LOOP",       AppConfig.music("SXYGX, ACIGODE, LANCELOT - LOOP [NCS Release].wav")));
        songList.add(new Song("Faster",     AppConfig.music("Zambolino - Faster.wav")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Main update loop  (called every ~16 ms from the Swing Timer on the EDT)
    // ══════════════════════════════════════════════════════════════════════════

    public void update() {
        audio.updateMusicFade(isPaused);

        if (inSongMenu) {
            tickAmbientParticles();
            return;
        }

        if (gameOver) {
            gameOverTicks++;
            if (showMeme) memeTicks++;
            tickParticles();
            return;
        }

        if (isPaused || resumeCountdown > 0) {
            if (resumeCountdown > 0) resumeCountdown--;
            return;
        }

        if (restarting) {
            if (--restartTimer <= 0) {
                restarting = false;
                if (pendingAudioPath != null) {
                    audio.playMusic(pendingAudioPath);
                    pendingAudioPath = null;
                }
            }
            return;
        }

        tickBeatmap();
        updateShake();
        tickTargets();
        tickParticles();
        checkGameOver();
    }

    // ── Update helpers ────────────────────────────────────────────────────────

    private void tickBeatmap() {
        long ms = audio.getPositionMs();
        if (audio.isMusicActive()) {
            audio.songStarted = true;
            while (!activeBeatmap.isEmpty() && activeBeatmap.get(0).timeMs - 1200 <= ms) {
                Note n = activeBeatmap.remove(0);
                targets.add(createTarget(n.x, n.y, n.hp, hpColor(n.hp), n.timeMs));
            }
        } else if (audio.songStarted && activeBeatmap.isEmpty() && targets.isEmpty()) {
            triggerGameOver(false);
        }
    }

    private void tickTargets() {
        long ms = audio.getPositionMs();
        targets.removeIf(t -> {
            t.update(ms);
            if (t.shouldRemove && !t.isFailing && t.brightness <= 0) {
                score -= 10;  // missed note penalty
            }
            if (t.shouldRemove) recycleTarget(t);
            return t.shouldRemove;
        });
    }

    private void tickParticles() {
        particles.removeIf(p -> {
            p.update();
            boolean done = p.alpha <= 0;
            if (done) recycleParticle(p);
            return done;
        });
    }

    private void tickAmbientParticles() {
        tickParticles();
        if (random.nextInt(100) < 4) {
            particles.add(createParticle(
                    random.nextInt(1600), random.nextInt(900),
                    hpColor(random.nextInt(5) + 1), 1, random.nextInt(5) + 1));
        }
    }

    private void checkGameOver() {
        if (score < 0 && !gameOver) triggerGameOver(true);
    }

    // ── Game-over flow ────────────────────────────────────────────────────────

    private void triggerGameOver(boolean isFailure) {
        gameOver      = true;
        gameOverTicks = 0;
        showMeme      = false;
        memeTicks     = 0;

        audio.stopMusic();
        targets.clear();
        activeBeatmap.clear();

        if (isFailure) {
            spawnGameOverParticles();
            scheduleGameOverMeme();
        }
    }

    private void spawnGameOverParticles() {
        for (int i = 0; i < 150; i++) {
            int gray = 100 + random.nextInt(100);
            particles.add(createParticle(
                    random.nextInt(1920), random.nextInt(1080),
                    new Color(gray, gray, gray), 0, 0));
        }
    }

    /**
     * Schedules the flashbang sound and meme image on a daemon thread.
     * Mutations to game state (showMeme) are posted back to the EDT.
     */
    private void scheduleGameOverMeme() {
        Thread t = new Thread(() -> {
            audio.playSound(AppConfig.sound("flashbang-full-out.wav"));
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            if (gameOver && !restarting) {
                SwingUtilities.invokeLater(() -> showMeme = true);
                audio.playSound(AppConfig.sound("sssr-mem.wav"));
            }
        }, "gameover-meme");
        t.setDaemon(true);
        t.start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Public API called by InputHandler / Renderer
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Starts async beat-map analysis for the given audio file.
     * The resulting {@link Note} list is merged into {@link #activeBeatmap}
     * on the EDT so game-loop access remains single-threaded.
     */
    public void analyzeAudioAndGenerateMap(String audioPath) {
        activeBeatmap.clear();
        totalPossibleScore = 0;
        analyzer.analyzeAsync(audioPath, notes -> SwingUtilities.invokeLater(() -> {
            activeBeatmap.addAll(notes);
            for (Note n : notes) totalPossibleScore += n.hp * 2;
        }));
    }

    /** Delegates to {@link AudioManager#playMusic(String)}. */
    public void playMusic(String path) { audio.playMusic(path); }

    /** Delegates to {@link AudioManager#playSound(String)}. */
    public void playSound(String name) { audio.playSound(name); }

    /** Resets all game state back to its initial values. */
    public void resetGame() {
        restarting        = true;
        restartTimer      = 180;
        pendingAudioPath  = null;
        audio.songStarted = false;
        targets.clear();
        particles.clear();
        activeBeatmap.clear();

        score             = 50;
        actualEarnedScore = 0;
        totalPossibleScore = 0;
        gameOver          = false;
        gameOverTicks     = 0;
        showMeme          = false;
        memeTicks         = 0;
        totalHits         = 0;
        totalFails        = 0;
        colorHits         = new int[5];   // bug-fix: was missing in original

        audio.stopAllSfx();
    }

    /** Exits pause mode after a brief countdown (resume animation). */
    public void triggerResume() {
        isPaused        = false;
        resumeCountdown = 186;
    }

    /** Accuracy as a value in [0, 1] based on earned vs. possible score. */
    public float getAccuracy() {
        if (totalPossibleScore == 0) return 1.0f;
        return Math.max(0f, Math.min(1f, (float) actualEarnedScore / totalPossibleScore));
    }

    // ── Visual helpers ────────────────────────────────────────────────────────

    /** Spawns a burst of particles at the given screen position. */
    public void createExplosion(int x, int y, Color c, int hp) {
        int count = (hp == 0) ? 8 : 15 + hp * 5;
        for (int i = 0; i < count; i++) particles.add(createParticle(x, y, c, 0, 0));
        if (hp > 0) particles.add(createParticle(x, y, c, 1, hp));
    }

    /** Maps an hp value (1–5) to its corresponding display colour. */
    public Color hpColor(int hp) {
        return switch (hp) {
            case 5  -> C5;
            case 4  -> C4;
            case 3  -> C3;
            case 2  -> C2;
            default -> C1;
        };
    }

    // ── Screen shake ──────────────────────────────────────────────────────────

    public void updateShake() {
        if (shakeIntensity > 0) {
            shakeX = random.nextInt(shakeIntensity * 2 + 1) - shakeIntensity;
            shakeY = random.nextInt(shakeIntensity * 2 + 1) - shakeIntensity;
            shakeIntensity--;
        } else {
            shakeX = shakeY = 0;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Object pools
    // ══════════════════════════════════════════════════════════════════════════

    public Target createTarget(int x, int y, int hp, Color c, long hitTime) {
        Target t = targetPool.poll();
        if (t == null) t = new Target();
        t.init(x, y, hp, c, hitTime);
        return t;
    }

    public void recycleTarget(Target t) { targetPool.offer(t); }

    public Particle createParticle(int x, int y, Color c, int type, int shapeType) {
        Particle p = particlePool.poll();
        if (p == null) p = new Particle();
        p.init(x, y, c, type, shapeType);
        return p;
    }

    public void recycleParticle(Particle p) { particlePool.offer(p); }
}