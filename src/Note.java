public final class Note {

    public final long timeMs;
    public final int x;
    public final int y;
    public final int hp;

    public Note(long timeMs, int x, int y, int hp) {
        this.timeMs = timeMs;
        this.x      = x;
        this.y      = y;
        this.hp     = hp;
    }

    @Override
    public String toString() {
        return "Note{timeMs=" + timeMs + ", x=" + x + ", y=" + y + ", hp=" + hp + "}";
    }
}