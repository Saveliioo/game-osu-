import java.io.*;
import java.util.ArrayList;
import java.util.List;

class PatternLoader {
    static List<int[][]> load(String filename) {
        try (InputStream is = PatternLoader.class.getResourceAsStream("/" + filename)) {
            if (is != null) return parse(is);
        } catch (IOException ignored) {}
        File file = new File(filename);
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) { return parse(is); } catch (IOException ignored) {}
        }
        return defaults();
    }

    private static List<int[][]> parse(InputStream is) throws IOException {
        List<int[][]> out = new ArrayList<>();
        List<int[]> current = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.equals("[PATTERN_START]")) current = new ArrayList<>();
                else if (line.equals("[PATTERN_END]")) { if (current != null) out.add(current.toArray(new int[0][])); current = null; }
                else if (current != null) {
                    int[] row = new int[line.length()];
                    for (int i = 0; i < line.length(); i++) {
                        char ch = line.charAt(i);
                        row[i] = (ch >= '0' && ch <= '3') ? (ch - '0') : 0;
                    }
                    current.add(row);
                }
            }
        }
        return out;
    }

    private static List<int[][]> defaults() {
        List<int[][]> p = new ArrayList<>();
        p.add(new int[][]{{0,0,1,0,0},{0,1,2,1,0},{1,2,3,2,1},{0,1,2,1,0},{0,0,1,0,0}});
        return p;
    }
}