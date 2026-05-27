import javax.sound.sampled.*;
import java.io.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Handles all audio: background music playback, sound-effect playback,
 * volume control, and the pause-fade effect.
 *
 * <p>Sound effects are played on short-lived daemon threads.
 * {@link CopyOnWriteArrayList} is used for {@code activeSfx} so that the
 * game-loop thread can iterate the list safely while SFX threads add/remove
 * entries concurrently — eliminating the original
 * {@link java.util.ConcurrentModificationException} risk.
 */
public class AudioManager {

    // ── State ─────────────────────────────────────────────────────────────────

    private Clip bgMusicClip;

    /** Set to {@code true} once the current song has started playing. */
    public boolean songStarted = false;

    /** Current fade multiplier applied on top of {@code settings.musicVolume}. */
    public float musicFadeMult = 1.0f;

    /** All currently-playing SFX clips (thread-safe for concurrent add/remove). */
    private final List<Clip> activeSfx = new CopyOnWriteArrayList<>();

    private final Settings settings;

    // ══════════════════════════════════════════════════════════════════════════

    public AudioManager(Settings settings) {
        this.settings = settings;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Current playback position of the background music in milliseconds. */
    public long getPositionMs() {
        return bgMusicClip != null ? bgMusicClip.getMicrosecondPosition() / 1000 : 0;
    }

    /** {@code true} when background music is currently playing (not stopped/paused). */
    public boolean isMusicActive() {
        return bgMusicClip != null && bgMusicClip.isActive();
    }

    /** Direct access to the underlying Clip, needed by the beatmap tick. */
    public Clip getBgMusicClip() {
        return bgMusicClip;
    }

    // ── Music ─────────────────────────────────────────────────────────────────

    /**
     * Stops any currently-playing music, loads the file at {@code path},
     * and starts playback immediately.
     */
    public void playMusic(String path) {
        stopMusic();
        try (InputStream is = openStream(path)) {
            if (is == null) {
                System.err.println("[AudioManager] Music file not found: " + path);
                return;
            }
            AudioInputStream decoded = toDecodedStream(
                    AudioSystem.getAudioInputStream(new BufferedInputStream(is)));
            bgMusicClip = AudioSystem.getClip();
            bgMusicClip.open(decoded);
            applyVolume(bgMusicClip, settings.musicVolume);
            bgMusicClip.start();
        } catch (Exception e) {
            System.err.println("[AudioManager] playMusic failed (" + path + "): " + e.getMessage());
        }
    }

    public void stopMusic() {
        if (bgMusicClip != null) {
            bgMusicClip.stop();
            bgMusicClip.close();
            bgMusicClip = null;
        }
    }

    // ── SFX ──────────────────────────────────────────────────────────────────

    public void playSound(String name) {
        Thread t = new Thread(() -> {
            try {
                File file = resolveSound(name);
                if (file == null) {
                    System.err.println("[AudioManager] Sound not found: " + name);
                    return;
                }
                AudioInputStream ais  = AudioSystem.getAudioInputStream(file);
                Clip              clip = AudioSystem.getClip();
                clip.open(ais);
                applyVolume(clip, settings.hitVolume);

                activeSfx.add(clip);
                clip.start();
                Thread.sleep(clip.getMicrosecondLength() / 1000);
                clip.close();
                activeSfx.remove(clip);
            } catch (Exception e) {
                System.err.println("[AudioManager] playSound failed (" + name + "): " + e.getMessage());
            }
        }, "sfx-thread");
        t.setDaemon(true);
        t.start();
    }

    public void stopAllSfx() {
        for (Clip c : activeSfx) {
            if (c != null && c.isOpen()) {
                c.stop();
                c.close();
            }
        }
        activeSfx.clear();
    }

    // ── Volume / fade ─────────────────────────────────────────────────────────


    public void applyVolume(Clip clip, float volume) {
        if (clip == null) return;
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (volume <= 0.0001f)
                    ? gain.getMinimum()
                    : (float) (Math.log10(volume) * 20.0);
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB)));
        } catch (IllegalArgumentException | IllegalStateException ignored) {

        }
    }

    public void updateMusicFade(boolean isPaused) {
        float target = isPaused ? 0.28f : 1.0f;
        if (musicFadeMult < target) {
            musicFadeMult = Math.min(musicFadeMult + 0.03f, target);
        } else {
            musicFadeMult = Math.max(musicFadeMult - 0.03f, target);
        }
        applyVolume(bgMusicClip, settings.musicVolume * musicFadeMult);
    }

    // ── Package-level helpers (used by BeatmapAnalyzer) ──────────────────────

    InputStream openStream(String path) {
        try {
            File f = new File(path);
            if (f.exists()) return new FileInputStream(f);
        } catch (Exception ignored) {}

        // Classpath fallback (for resources bundled inside a JAR)
        String cp = path.startsWith("/") ? path : "/" + path;
        return getClass().getResourceAsStream(cp);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static AudioInputStream toDecodedStream(AudioInputStream raw) {
        AudioFormat base = raw.getFormat();
        AudioFormat decoded = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(), 16,
                base.getChannels(), base.getChannels() * 2,
                base.getSampleRate(), false);
        return AudioSystem.getAudioInputStream(decoded, raw);
    }
    private static File resolveSound(String name) {
        File f = new File(name);
        if (f.exists()) return f;
        f = new File(AppConfig.sound(name));
        if (f.exists()) return f;
        return null;
    }
}