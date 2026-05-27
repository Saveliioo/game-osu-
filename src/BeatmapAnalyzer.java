import javax.sound.sampled.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public class BeatmapAnalyzer {

    // ── Algorithm constants ───────────────────────────────────────────────────

    private static final float BEAT_RATIO_THRESHOLD = 1.2f;
    private static final float MIN_RMS_FOR_BEAT     = 200f;
    private static final int   HISTORY_WINDOW       = 60;  // frames (~1.4 s at 44 kHz / 1024 frame)

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final AudioManager audio;
    private final Random       random;

    // ══════════════════════════════════════════════════════════════════════════

    public BeatmapAnalyzer(AudioManager audio, Random random) {
        this.audio  = audio;
        this.random = random;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void analyzeAsync(String audioPath, Consumer<List<Note>> onComplete) {
        Thread t = new Thread(() -> {
            List<Note> notes = analyze(audioPath);
            onComplete.accept(notes);
        }, "beatmap-analyzer");
        t.setDaemon(true);
        t.start();
    }

    // ── Core algorithm ────────────────────────────────────────────────────────

    private List<Note> analyze(String audioPath) {
        List<Note> notes = new ArrayList<>();

        try (InputStream is = audio.openStream(audioPath)) {
            if (is == null) {
                System.err.println("[BeatmapAnalyzer] Audio file not found: " + audioPath);
                return notes;
            }

            AudioInputStream raw     = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
            AudioFormat      base    = raw.getFormat();
            AudioFormat      decoded = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16,
                    base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
            AudioInputStream ais = AudioSystem.getAudioInputStream(decoded, raw);

            byte[]      buf         = new byte[1024 * decoded.getFrameSize()];
            int         bytesRead;
            long        totalFrames = 0;
            long        lastBeatMs  = 0;
            List<Float> energyHist  = new ArrayList<>();

            while ((bytesRead = ais.read(buf)) != -1) {
                float rms      = computeRms(buf, bytesRead);
                energyHist.add(rms);

                float localAvg = rollingAverage(energyHist, HISTORY_WINDOW);
                long  timeMs   = framesToMs(totalFrames, decoded.getSampleRate());

                if (isBeat(rms, localAvg, timeMs, lastBeatMs)) {
                    int hp = classifyHp(rms, localAvg, timeMs);
                    notes.add(new Note(
                            timeMs,
                            random.nextInt(GameEngine.SIZE),
                            random.nextInt(GameEngine.SIZE),
                            hp));
                    lastBeatMs = timeMs;
                }
                totalFrames += bytesRead / decoded.getFrameSize();
            }

        } catch (Exception e) {
            System.err.println("[BeatmapAnalyzer] Analysis failed: " + e.getMessage());
        }

        return notes;
    }

    // ── Signal helpers ────────────────────────────────────────────────────────

    private static float computeRms(byte[] buf, int bytesRead) {
        long sum = 0;
        for (int i = 0; i < bytesRead; i += 2) {
            int sample = (buf[i + 1] << 8) | (buf[i] & 0xFF);
            sum += (long) sample * sample;
        }
        return (float) Math.sqrt((double) sum / (bytesRead / 2));
    }

    private static float rollingAverage(List<Float> history, int window) {
        int end   = history.size() - 1;
        int start = Math.max(0, end - window);
        if (start >= end) return 0f;
        float total = 0;
        for (int i = start; i < end; i++) total += history.get(i);
        return total / (end - start);
    }

    private static long framesToMs(long frames, float sampleRate) {
        return (long) ((frames / sampleRate) * 1000L);
    }

    // ── Beat / HP classification ──────────────────────────────────────────────

    private static boolean isBeat(float rms, float localAvg, long timeMs, long lastBeatMs) {
        if (rms <= BEAT_RATIO_THRESHOLD * localAvg) return false;
        if (rms <= MIN_RMS_FOR_BEAT)               return false;
        return (timeMs - lastBeatMs) > minimumGap(timeMs);
    }

    private static int minimumGap(long timeMs) {
        if (timeMs <  10_000) return 600;
        if (timeMs <  30_000) return 250;
        return 120;
    }
    private static int classifyHp(float rms, float localAvg, long timeMs) {
        int   phaseMax = Math.min(5, 2 + (int) (timeMs / 30_000L));
        float ratio    = (localAvg > 0f) ? rms / localAvg : 1f;

        int hp;
        if      (ratio > 2.2f && rms > 1200) hp = 5;
        else if (ratio > 2.0f && rms > 1000) hp = 4;
        else if (ratio > 1.8f && rms >  800) hp = 3;
        else if (ratio > 1.5f && rms >  600) hp = 2;
        else                                  hp = 1;

        return Math.min(hp, phaseMax);
    }
}