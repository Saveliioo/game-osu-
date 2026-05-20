import java.awt.*;
import java.awt.geom.Path2D;
import java.util.Random;

public class Particle {
    double x, y, vx, vy, size;
    float alpha;
    Color color;
    int type, shapeType;
    private float cr, cg, cb;
    private static final Random random = new Random();

    public Particle() {}

    public void init(int x, int y, Color c, int type, int shapeType) {
        this.x = x; this.y = y; this.color = c; this.type = type; this.shapeType = shapeType;
        this.alpha = 1.0f;
        this.cr = c.getRed() / 255f;
        this.cg = c.getGreen() / 255f;
        this.cb = c.getBlue() / 255f;
        if (type == 0) {
            this.size = 3;
            double speed = 6 + random.nextDouble() * 8;
            double angle = random.nextDouble() * Math.PI * 2;
            this.vx = Math.cos(angle) * speed;
            this.vy = Math.sin(angle) * speed;
        } else {
            this.size = 10;
        }
    }

    public void update() {
        if (type == 0) {
            alpha -= 0.035f; x += vx; y += vy; vy += 0.25;
        } else {
            alpha -= 0.04f; size += 8;
        }
    }

    public void draw(Graphics2D g2) {
        float a = Math.max(0, alpha);
        g2.setColor(new Color(cr, cg, cb, a));
        if (type == 0) {
            g2.fillRect((int) x, (int) y, (int) size, (int) size);
            return;
        }
        g2.setStroke(new BasicStroke(3f));
        int r = (int) size / 2;
        switch (shapeType) {
            case 1 -> g2.drawOval((int) x - r, (int) y - r, (int) size, (int) size);
            case 2 -> g2.drawRect((int) x - r, (int) y - r, (int) size, (int) size);
            case 3 -> {
                Path2D hex = new Path2D.Double();
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI / 3;
                    double hx = x + Math.cos(ang) * r;
                    double hy = y + Math.sin(ang) * r;
                    if (i == 0) hex.moveTo(hx, hy); else hex.lineTo(hx, hy);
                }
                hex.closePath();
                g2.draw(hex);
            }
            case 4 -> {
                Path2D tri = new Path2D.Double();
                for (int i = 0; i < 3; i++) {
                    double ang = i * (2 * Math.PI / 3) - Math.PI / 2;
                    double tx = x + Math.cos(ang) * r;
                    double ty = y + Math.sin(ang) * r;
                    if (i == 0) tri.moveTo(tx, ty); else tri.lineTo(tx, ty);
                }
                tri.closePath();
                g2.draw(tri);
            }
            case 5 -> {
                Path2D star = new Path2D.Double();
                double innerR = r * 0.4;
                for (int i = 0; i < 10; i++) {
                    double ang = i * Math.PI / 5 - Math.PI / 2;
                    double rad = (i % 2 == 0) ? r : innerR;
                    double sx = x + Math.cos(ang) * rad;
                    double sy = y + Math.sin(ang) * rad;
                    if (i == 0) star.moveTo(sx, sy); else star.lineTo(sx, sy);
                }
                star.closePath();
                g2.draw(star);
            }
            default -> g2.drawOval((int) x - r, (int) y - r, (int) size, (int) size);
        }
    }
}