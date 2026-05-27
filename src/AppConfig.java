import java.io.*;
import java.util.Properties;

public final class AppConfig {

    private static final String CONFIG_FILE   = "config.properties";
    private static final String DEFAULT_BASE  = "resources";

    public static final String RESOURCE_BASE;

    static {
        Properties p = new Properties();
        File f = new File(CONFIG_FILE);
        if (f.exists()) {
            try (InputStream is = new FileInputStream(f)) {
                p.load(is);
            } catch (IOException e) {
                System.err.println("[AppConfig] Could not read " + CONFIG_FILE + ": " + e.getMessage());
            }
        }
        RESOURCE_BASE = p.getProperty("resourceBase", DEFAULT_BASE);
    }

    public static String music(String filename) {
        return RESOURCE_BASE + File.separator + "background_music" + File.separator + filename;
    }

    public static String sound(String filename) {
        return RESOURCE_BASE + File.separator + "sounds" + File.separator + filename;
    }

    public static String image(String filename) {
        return RESOURCE_BASE + File.separator + "images" + File.separator + filename;
    }

    private AppConfig() {}
}