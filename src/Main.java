import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;

public class Main extends JPanel {
    private final GameEngine engine;
    private final Renderer renderer;
    private final InputHandler inputHandler;

    public Main() {
        setBackground(Color.BLACK);

        engine = new GameEngine();
        renderer = new Renderer();
        inputHandler = new InputHandler(engine, renderer, this);
        setFocusable(true);
        inputHandler.attach();

        new Timer(16, e -> {
            engine.update();
            repaint();
        }).start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderer.draw((Graphics2D) g, engine, this, inputHandler.mouseX, inputHandler.mouseY);
    }

    public static void main(String[] args) {
        JFrame f = new JFrame("GT-R BEAT // REDLINE");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setUndecorated(true);
        f.setExtendedState(JFrame.MAXIMIZED_BOTH);
        f.add(new Main());
        f.setVisible(true);
    }
}