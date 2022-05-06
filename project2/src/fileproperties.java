import java.io.File;

public class fileproperties {
    private volatile File file; 
    private volatile boolean readingCurrently;
    private volatile boolean readingInWaiting;
    private volatile int readCount;

    public fileproperties (File file) {
        this.file = file;
        this.readingCurrently = false;
        this.readingInWaiting = false;
        this.readCount = 0;
    }

    public void setWaiting(boolean value) {
        this.readingInWaiting = value;
    }

    public void setReadCur(boolean value) {
        this.readingCurrently = value;
    }

    public void addCount() {
        this.readCount++;
    }

    public void removeCount() {
        this.readCount--;
    }

    public int getCount() {
        return this.readCount;
    }

    public boolean getWait() {
        return this.readingInWaiting;
    }

    public boolean getCur() {
        return this.readingCurrently;
    }

    public File getFile() {
        return this.file;
    }

    public File remove() {
        File f = this.file;
        this.file = null;
        return f;
    }

    public void addFile(File f) {
        this.file = f;
    }
}
