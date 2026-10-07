package ibd.persistent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface PageSerialization {
    
    void writePage(DataOutputStream oos, Page page) throws IOException;
    Page readPage(DataInputStream ois) throws IOException;
    
}
