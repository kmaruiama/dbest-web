package ibd.persistent;

import ibd.table.Params;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class PersistentPageFile<P extends Page> extends AbstractStoringPageFile {

    protected  FileChannel file;

    

    private boolean existed;

    Path fileName;
        
  
    
    public PersistentPageFile(int pageSize, Path filename, boolean recreate) throws IOException {
        super(pageSize);
        this.fileName = filename;
        
        if (recreate) {
            File f = filename.toFile();
            f.delete();
        }

        existed = Files.exists(filename);

        file = FileChannel.open(filename, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);

    }

    @Override
    public void reopen() throws Exception {
        file = FileChannel.open(fileName, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }
    
    @Override
    public Page readPage(int pageID) {
        try {
            engine.info.Parameters.BLOCKS_LOADED++;
            long offset = ((long) (header.getReservedPages() + pageID)) * (long) pageSize;
            byte[] buffer = new byte[pageSize];
            int read = file.read(ByteBuffer.wrap(buffer), offset);
            if (read != pageSize) {
                throw new IOException("Incomplete read at offset " + offset + " read " + read + " bytes, expected " + pageSize);
            }
            return byteArrayToPage(buffer);
        } catch (IOException e) {
            throw new RuntimeException("IOException occurred during reading of page " + pageID + "\n", e);
        }
    }

    @Override
    public void deletePage(int pageID) {
        try {
            super.deletePage(pageID);

            byte[] array = pageToByteArray(null);
            long offset = (header.getReservedPages() + pageID) * (long) pageSize;
            int written = file.write(ByteBuffer.wrap(array), offset);
            if (written != pageSize) {
                throw new IOException("Incomplete write at offset " + offset + " wrote " + written + " bytes, expected " + array.length);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writePage(int pageID, Page page) {
        try {
            Params.BLOCKS_SAVED++;
            
            byte[] array = pageToByteArray(page);
            long offset = ((long) (header.getReservedPages() + pageID)) * (long) pageSize;
            assert offset >= 0 : header.getReservedPages() + " " + pageID + " " + pageSize + " " + offset;
            int written = file.write(ByteBuffer.wrap(array), offset);
            if (written != pageSize) {
                throw new IOException("Incomplete write at offset " + offset + " wrote " + written + " bytes, expected " + array.length);
            }
            page.setDirty(false);
        } catch (IOException e) {
            throw new RuntimeException("Error writing to page file.", e);
        }
    }

    @Override
    public void close() {
        try {
            flush();
            super.close();
            file.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clear() {
    }

    @Override
    public void reset() {
        super.reset();
        existed = false;

    }

    private Page byteArrayToPage(byte[] array) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(array);
        DataInputStream ois = new DataInputStream(bais);
        return pageSerialization.readPage(ois);

    }

    private byte[] pageToByteArray(Page page) {
        try {

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream oos = new DataOutputStream(baos);
            pageSerialization.writePage(oos, page);
            oos.close();
            baos.close();
            byte[] array = baos.toByteArray();

            if (array.length > this.pageSize) {
                throw new IllegalArgumentException("Size of page " + page + " is greater than specified" + " pagesize: " + array.length + " > " + pageSize);
            } else if (array.length == this.pageSize) {
                return array;
            } else {
                byte[] result = new byte[pageSize];
                System.arraycopy(array, 0, result, 0, array.length);
                return result;
            }
        } catch (IOException e) {
            throw new RuntimeException("IOException occurred! ", e);
        }
    }

    public FileChannel getFile() {
        return file;
    }

    

    @Override
    public void setNextPageID(int next_page_id) {
        this.nextPageID = next_page_id;
        while (!emptyPages.isEmpty() && emptyPages.get(emptyPages.size - 1) >= this.nextPageID) {
            --emptyPages.size;
        }
    }

    @Override
    public boolean initialize(PageHeader header) {
        try {
            if (existed) {
                this.header = header;

                header.readHeader(file);

                nextPageID = header.getLargestPageID();
                emptyPages = header.readEmptyPages(file);

            }
            else {
                this.header = header;
                header.writeHeader(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("IOException occurred.", e);
        }
        pageSize = header.getPageSize();
        return existed;
    }

    @Override
    public void flush() {
        try {
            {
                header.writeEmptyPages(emptyPages, file);
            }
            header.setLargestPageID(nextPageID);
            header.writeHeader(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
