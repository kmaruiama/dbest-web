package ibd.persistent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

public class PageHeader {

    private static final int SIZE = 4 * Integer.BYTES;

    private static final int FILE_VERSION = 841150978;

    private int pageSize = -1;

    private int emptyPagesSize = 0;

    private int largestPageID = 0;

    public PageHeader(int pageSize) {
        this.pageSize = pageSize;
    }

    public void readHeader(FileChannel file) throws IOException {
        readHeader(file.map(MapMode.READ_ONLY, 0, size()));
    }

    public void writeHeader(FileChannel file) throws IOException {
        writeHeader(file.map(MapMode.READ_WRITE, 0, size()));
    }

    public int size() {
        return SIZE;
    }

    public int getEmptyPagesSize() {
        return emptyPagesSize;
    }

    public void setEmptyPagesSize(int emptyPagesSize) {
        this.emptyPagesSize = emptyPagesSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getLargestPageID() {
        return largestPageID;
    }

    public void setLargestPageID(int largestPageID) {
        this.largestPageID = largestPageID;
    }

    public void readHeader(ByteBuffer data) {
        if (data.getInt() != FILE_VERSION) {
            throw new RuntimeException("PersistentPageFile version does not match!");
        }
        this.pageSize = data.getInt();
        this.emptyPagesSize = data.getInt();
        this.largestPageID = data.getInt();
    }

    public void writeHeader(ByteBuffer buffer) {
        buffer.putInt(FILE_VERSION)
                .putInt(pageSize)
                .putInt(this.emptyPagesSize)
                .putInt(this.largestPageID);
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getReservedPages() {
        return size() / getPageSize() + 1;
    }

    public void writeEmptyPages(IntegerArray emptyPages, FileChannel file) throws IOException {
        if (emptyPages.isEmpty()) {
            this.emptyPagesSize = 0;
            return;
        }
        emptyPages.sort();
        this.emptyPagesSize = emptyPages.size * Integer.BYTES;
        ByteBuffer buf = ByteBuffer.allocateDirect(this.emptyPagesSize);
        buf.asIntBuffer().put(emptyPages.data, 0, emptyPages.size);
        file.write(buf, file.size());
    }

    public IntegerArray readEmptyPages(FileChannel file) throws IOException {
        IntegerArray emptyPages = new IntegerArray();
        if (emptyPagesSize > 0) {
            int n = emptyPagesSize / Integer.BYTES;
            if (n > emptyPages.data.length) {
                emptyPages.data = new int[n];
            }
            ByteBuffer buf = ByteBuffer.allocateDirect(emptyPagesSize);
            file.read(buf, file.size() - emptyPagesSize);
            buf.position(0);

            for (int i = 0; i < n; i++) {
                emptyPages.data[i] = buf.getInt();
            }
        }
        return emptyPages;
    }

}
