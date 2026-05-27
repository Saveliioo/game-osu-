public final class Song {

    public final String title;
    public final String audioPath;

    public Song(String title, String audioPath) {
        this.title     = title;
        this.audioPath = audioPath;
    }

    @Override
    public String toString() {
        return "Song{title='" + title + "', audioPath='" + audioPath + "'}";
    }
}