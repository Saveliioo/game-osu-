import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.File;

public class GameEngine {
    public static final int SIZE = 7;
    public static final int TILE_SIZE = 75;
    public static final int GRID_PX = SIZE * TILE_SIZE;

    public int score = 50;
    public int totalPossibleScore = 0;
    public int actualEarnedScore = 0;
    public boolean gameOver = false;
    public int gameOverTicks = 0;

    public boolean showMeme = false;
    public int memeTicks = 0;

    public int waveNumber = 0;
    public int shakeIntensity = 0;
    public int shakeX = 0;
    public int shakeY = 0;

    public boolean inSongMenu = true;
    public final List<Song> songList = new ArrayList<>();
    public final List<Note> activeBeatmap = new ArrayList<>();
    public String pendingAudioPath = null;

    public boolean restarting = false;
    public int restartTimer = 0;
    public boolean isPaused = false;
    public boolean settingsOpen = false;
    public int totalHits = 0;
    public int totalFails = 0;
    public int[] colorHits = new int[5];
    public int resumeCountdown = 0;
    public float musicFadeMult = 1.0f;
    public Clip bgMusicClip = null;
    public boolean songStarted = false;

    public final List<Clip> activeSfx = new ArrayList<>();

    public final Settings settings = new Settings();
    public final ArrayList<Target> targets = new ArrayList<>();
    public final ArrayList<Particle> particles = new ArrayList<>();
    public List<int[][]> patterns;

    public final Random random = new Random();
    public final ArrayDeque<Target> targetPool = new ArrayDeque<>();
    public final ArrayDeque<Particle> particlePool = new ArrayDeque<>();

    public final Color C1 = new Color(0, 255, 220);
    public final Color C2 = new Color(255, 200, 50);
    public final Color C3 = new Color(200, 100, 255);
    public final Color C4 = new Color(255, 140, 0);
    public final Color C5 = new Color(255, 55, 90);

    public static class Song {
        public String title;
        public String audioPath;

        public Song(String title, String audioPath) {
            this.title = title;
            this.audioPath = audioPath;
        }
    }

    public static class Note {
        public long timeMs;
        public int x, y, hp;

        public Note(long timeMs, int x, int y, int hp) {
            this.timeMs = timeMs;
            this.x = x;
            this.y = y;
            this.hp = hp;
        }
    }

    public GameEngine() {
        try {
            patterns = PatternLoader.load("patterns.txt");
        } catch (Exception ignored) {
        }

        // ИСПОЛЬЗУЕМ ОТНОСИТЕЛЬНЫЕ ПУТИ
        // Папка "resources" должна лежать в корне твоего проекта
        songList.add(new Song("Recall", "resources/background_music/gabriawll - Recall [NCS Release].wav"));
        songList.add(new Song("Old School", "resources/background_music/More Plastic - Old School [NCS Release].wav"));
        songList.add(new Song("LOOP", "resources/background_music/SXYGX, ACIGODE, LANCELOT - LOOP [NCS Release].wav"));
        songList.add(new Song("Faster", "resources/background_music/Zambolino - Faster.wav"));
    }

    private InputStream getResourceStream(String path) {
        try {
            // 1. Проверяем как обычный файл в папке проекта
            File file = new File(path);
            if (file.exists()) {
                return new java.io.FileInputStream(file);
            }

            // 2. Проверяем внутри сборки (если скомпилировано в JAR)
            String p = path.startsWith("/") ? path.substring(1) : path;
            InputStream is = getClass().getClassLoader().getResourceAsStream(p);
            if (is == null) {
                is = getClass().getResourceAsStream("/" + p);
            }
            return is;
        } catch (Exception e) {
            return null;
        }
    }

    public void update() {
        updateMusicFade();

        if (inSongMenu) {
            if (random.nextInt(100) < 4) {
                particles.add(createParticle(random.nextInt(1600), random.nextInt(900), hpColor(random.nextInt(5) + 1), 1, random.nextInt(5) + 1));
            }
            particles.removeIf(p -> {
                p.update();
                boolean rem = p.alpha <= 0;
                if (rem) {
                    recycleParticle(p);
                }
                return rem;
            });
            return;
        }

        if (gameOver) {
            gameOverTicks++;
            if (showMeme) {
                memeTicks++;
            }
            particles.removeIf(p -> {
                p.update();
                boolean rem = p.alpha <= 0;
                if (rem) {
                    recycleParticle(p);
                }
                return rem;
            });
            return;
        }

        if (isPaused || resumeCountdown > 0) {
            if (resumeCountdown > 0) {
                resumeCountdown--;
            }
            return;
        }

        if (restarting) {
            restartTimer--;
            if (restartTimer <= 0) {
                restarting = false;
                if (pendingAudioPath != null) {
                    playMusic(pendingAudioPath);
                    pendingAudioPath = null;
                }
            }
            return;
        }

        long currentMs = bgMusicClip != null ? bgMusicClip.getMicrosecondPosition() / 1000 : 0;

        if (bgMusicClip != null && bgMusicClip.isActive()) {
            songStarted = true;
            while (!activeBeatmap.isEmpty() && activeBeatmap.get(0).timeMs - 1200 <= currentMs) {
                Note n = activeBeatmap.remove(0);
                targets.add(createTarget(n.x, n.y, n.hp, hpColor(n.hp), n.timeMs));
            }
        } else if (songStarted && activeBeatmap.isEmpty() && targets.isEmpty() && !gameOver) {
            gameOver = true;
            if (bgMusicClip != null) {
                bgMusicClip.stop();
            }
        }

        updateShake();

        targets.removeIf(t -> {
            t.update(currentMs);
            if (t.shouldRemove && !t.isFailing && t.brightness <= 0) {
                score -= 10;
            }
            if (t.shouldRemove) {
                recycleTarget(t);
            }
            return t.shouldRemove;
        });

        particles.removeIf(p -> {
            p.update();
            boolean rem = p.alpha <= 0;
            if (rem) {
                recycleParticle(p);
            }
            return rem;
        });

        if (score < 0 && !gameOver) {
            gameOver = true;
            gameOverTicks = 0;
            showMeme = false;
            memeTicks = 0;

            if (bgMusicClip != null) {
                bgMusicClip.stop();
            }
            targets.clear();
            activeBeatmap.clear();

            for (int i = 0; i < 150; i++) {
                int px = random.nextInt(1920);
                int py = random.nextInt(1080);
                int gray = 100 + random.nextInt(100);
                particles.add(createParticle(px, py, new Color(gray, gray, gray), 0, 0));
            }

            new Thread(() -> {
                playSound("flashbang-full-out.wav");
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                }

                if (gameOver && !restarting) {
                    showMeme = true;
                    playSound("sssr-mem.wav");
                }
            }).start();
        }
    }

    public void analyzeAudioAndGenerateMap(String audioPath) {
        activeBeatmap.clear();
        totalPossibleScore = 0;

        try (InputStream is = getResourceStream(audioPath)) {
            if (is == null) return;

            AudioInputStream rawAis = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
            AudioFormat baseFormat = rawAis.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, baseFormat.getSampleRate(), 16,
                    baseFormat.getChannels(), baseFormat.getChannels() * 2, baseFormat.getSampleRate(), false
            );
            AudioInputStream ais = AudioSystem.getAudioInputStream(decodedFormat, rawAis);

            byte[] buffer = new byte[1024 * decodedFormat.getFrameSize()];
            int bytesRead;
            long totalFramesRead = 0;
            long lastBeatTime = 0;
            List<Float> energyHistory = new ArrayList<>();

            while ((bytesRead = ais.read(buffer)) != -1) {
                long sum = 0;
                for (int i = 0; i < bytesRead; i += 2) {
                    int sample = (buffer[i + 1] << 8) | (buffer[i] & 0xFF);
                    sum += sample * sample;
                }
                float rms = (float) Math.sqrt((double) sum / (bytesRead / 2));
                energyHistory.add(rms);

                float localAvg = 0;
                int count = 0;
                for (int i = Math.max(0, energyHistory.size() - 60); i < energyHistory.size() - 1; i++) {
                    localAvg += energyHistory.get(i);
                    count++;
                }
                if (count > 0) {
                    localAvg /= count;
                }

                if (rms > 1.2f * localAvg && rms > 200) {
                    long actualTimeMs = (long) ((totalFramesRead / decodedFormat.getSampleRate()) * 1000);

                    int gap = 200;
                    if (actualTimeMs < 10000) {
                        gap = 600;
                    } else if (actualTimeMs < 30000) {
                        gap = 250;
                    } else {
                        gap = 120;
                    }

                    if (actualTimeMs - lastBeatTime > gap) {
                        int phaseMaxHp = Math.min(5, 2 + (int) (actualTimeMs / 30000));
                        float ratio = rms / localAvg;
                        int hp = 1;

                        if (ratio > 2.2f && rms > 1200) {
                            hp = 5;
                        } else if (ratio > 2.0f && rms > 1000) {
                            hp = 4;
                        } else if (ratio > 1.8f && rms > 800) {
                            hp = 3;
                        } else if (ratio > 1.5f && rms > 600) {
                            hp = 2;
                        }

                        hp = Math.min(hp, phaseMaxHp);

                        activeBeatmap.add(new Note(actualTimeMs, random.nextInt(SIZE), random.nextInt(SIZE), hp));
                        totalPossibleScore += hp * 2;
                        lastBeatTime = actualTimeMs;
                    }
                }
                totalFramesRead += bytesRead / decodedFormat.getFrameSize();
            }
        } catch (Exception e) {
            System.out.println("ОШИБКА ПРИ ГЕНЕРАЦИИ КАРТЫ: " + audioPath);
            e.printStackTrace();
        }
    }

    public void playMusic(String path) {
        if (bgMusicClip != null) {
            bgMusicClip.stop();
            bgMusicClip.close();
        }

        try (InputStream is = getResourceStream(path)) {
            if (is == null) {
                System.out.println("КРИТИЧЕСКАЯ ОШИБКА: Файл музыки не найден -> " + path);
                return;
            }
            AudioInputStream rawAis = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
            AudioFormat baseFormat = rawAis.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED, baseFormat.getSampleRate(), 16,
                    baseFormat.getChannels(), baseFormat.getChannels() * 2, baseFormat.getSampleRate(), false
            );
            AudioInputStream ais = AudioSystem.getAudioInputStream(decodedFormat, rawAis);

            bgMusicClip = AudioSystem.getClip();
            bgMusicClip.open(ais);
            applyVolume(bgMusicClip, settings.musicVolume);
            bgMusicClip.start();
            System.out.println("Успешно запущен трек: " + path);
        } catch (Exception e) {
            System.out.println("ОШИБКА ПРИ ЗАПУСКЕ ТРЕКА: " + path);
            e.printStackTrace();
        }
    }

    public float getAccuracy() {
        if (totalPossibleScore == 0) {
            return 1.0f;
        }
        return Math.max(0f, Math.min(1.0f, (float) actualEarnedScore / totalPossibleScore));
    }

    public Target createTarget(int x, int y, int hp, Color c, long hitTime) {
        Target t = targetPool.poll();
        if (t == null) {
            t = new Target();
        }
        t.init(x, y, hp, c, hitTime);
        return t;
    }

    public void recycleTarget(Target t) {
        targetPool.offer(t);
    }

    public Particle createParticle(int x, int y, Color c, int type, int shapeType) {
        Particle p = particlePool.poll();
        if (p == null) {
            p = new Particle();
        }
        p.init(x, y, c, type, shapeType);
        return p;
    }

    public void recycleParticle(Particle p) {
        particlePool.offer(p);
    }

    public void resetGame() {
        restarting = true;
        restartTimer = 180;
        pendingAudioPath = null;
        songStarted = false;
        targets.clear();
        score = 50;
        actualEarnedScore = 0;
        gameOver = false;
        gameOverTicks = 0;
        showMeme = false;
        memeTicks = 0;
        totalHits = 0;
        totalFails = 0;

        for (Clip c : activeSfx) {
            if (c != null && c.isOpen()) {
                c.stop();
                c.close();
            }
        }
        activeSfx.clear();
    }

    public Color hpColor(int hp) {
        if (hp == 5) return C5;
        if (hp == 4) return C4;
        if (hp == 3) return C3;
        if (hp == 2) return C2;
        return C1;
    }

    public void updateShake() {
        if (shakeIntensity > 0) {
            shakeX = random.nextInt(shakeIntensity * 2 + 1) - shakeIntensity;
            shakeY = random.nextInt(shakeIntensity * 2 + 1) - shakeIntensity;
            shakeIntensity--;
        } else {
            shakeX = 0;
            shakeY = 0;
        }
    }

    public void createExplosion(int x, int y, Color c, int hp) {
        int count = (hp == 0) ? 8 : 15 + hp * 5;
        for (int i = 0; i < count; i++) {
            particles.add(createParticle(x, y, c, 0, 0));
        }
        if (hp > 0) {
            particles.add(createParticle(x, y, c, 1, hp));
        }
    }

    public void applyVolume(Clip clip, float volume) {
        if (clip == null) return;
        try {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float dB = (volume <= 0.0001f) ? gain.getMinimum() : (float) (Math.log10(volume) * 20.0);
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB)));
        } catch (Exception ignored) {
        }
    }

    public void updateMusicFade() {
        float target = isPaused ? 0.28f : 1.0f;
        if (musicFadeMult < target) {
            musicFadeMult = Math.min(musicFadeMult + 0.03f, target);
        } else if (musicFadeMult > target) {
            musicFadeMult = Math.max(musicFadeMult - 0.03f, target);
        }
        applyVolume(bgMusicClip, settings.musicVolume * musicFadeMult);
    }

    public void triggerResume() {
        isPaused = false;
        resumeCountdown = 186;
    }

    public void playSound(String name) {
        new Thread(() -> {
            try {
                // Пытаемся найти звук в папке resources/sounds/
                File soundFile = new File("resources/sounds/" + name);
                if (!soundFile.exists()) {
                    // Если не нашли, ищем просто в корне проекта
                    soundFile = new File(name);
                }

                if (!soundFile.exists()) {
                    System.out.println("ОШИБКА: Звуковой эффект не найден -> " + name);
                    return;
                }

                AudioInputStream ais = AudioSystem.getAudioInputStream(soundFile);
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                applyVolume(clip, settings.hitVolume); // Используем hitVolume для эффектов

                activeSfx.add(clip);
                clip.start();
                Thread.sleep(clip.getMicrosecondLength() / 1000);

                clip.close();
                activeSfx.remove(clip);
            } catch (Exception e) {
                System.out.println("ОШИБКА ПРИ ПРОИГРЫВАНИИ ЭФФЕКТА: " + name);
                e.printStackTrace();
            }
        }).start();
    }
}