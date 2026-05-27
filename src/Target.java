import java.awt.*;

public class Target {
    int x, y, hp, maxHp;
    double growth;
    float brightness, failAlpha;
    boolean isReady, isFailing, shouldRemove;
    Color baseColor;
    long hitTime;
    boolean isLoop;

    public Target() {
    }

    public void init(int x, int y, int hp, Color c, long hitTime, boolean isLoop) {
        this.x = x;
        this.y = y;
        this.hp = hp;
        this.maxHp = hp;
        this.baseColor = c;
        this.hitTime = hitTime;
        this.growth = 0;
        this.brightness = 1.0f;
        this.failAlpha = 1.0f;
        this.isReady = false;
        this.isFailing = false;
        this.shouldRemove = false;
        this.isLoop = isLoop;
    }

    public void update(long currentMs) {
        if (isFailing) {
            failAlpha -= (isLoop ? 0.05f : 0.1f);
            if (failAlpha <= 0) {
                shouldRemove = true;
            }
        } else {
            long diff = hitTime - currentMs;
            if (diff > 0) {
                growth = Math.max(0, 1.0 - (diff / 1200.0));
                isReady = false;
                brightness = 1.0f;
            } else {
                growth = 1.0;
                isReady = true;
                long overTime = currentMs - hitTime;
                long maxLifeTime = (hp + 1) * (isLoop ? 2000L : 1000L);
                brightness = 1.0f - ((float) overTime / maxLifeTime);
                if (brightness <= 0) {
                    shouldRemove = true;
                }
            }
        }
    }

    public void triggerFail() {
        isFailing = true;
        growth = 1.0;
    }

    public void draw(Graphics2D g2, int ox, int oy, int tileSize) {
        int s = (int) ((tileSize - 10) * Math.min(growth, 1.0));
        if (s <= 0) {
            return;
        }

        int px = ox + x * tileSize + (tileSize - s) / 2;
        int py = oy + y * tileSize + (tileSize - s) / 2;

        float alphaValue = isReady ? brightness : (float) growth;
        float alpha = Math.max(0f, Math.min(1.0f, isFailing ? failAlpha : alphaValue));

        Color c = isFailing ? Color.RED : baseColor;

        if (isReady) {
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (alpha * 255)));
            g2.fillRect(px, py, s, s);
            g2.setColor(new Color(255, 255, 255, (int) (alpha * 255)));
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(px, py, s, s);
        } else {
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (alpha * 255)));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(px, py, s, s);
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (alpha * 80)));
            g2.fillRect(px, py, s, s);
        }

        if (!isFailing && hp > 1 && !isLoop) {
            g2.setFont(new Font("Consolas", Font.BOLD, s / 2));
            g2.setColor(new Color(255, 255, 255, (int) (alpha * 255)));
            String hText = String.valueOf(hp);
            int tw = g2.getFontMetrics().stringWidth(hText);
            int th = g2.getFontMetrics().getAscent();
            g2.drawString(hText, px + (s - tw) / 2, py + (s + th) / 2 - th / 4);
        }
    }
}