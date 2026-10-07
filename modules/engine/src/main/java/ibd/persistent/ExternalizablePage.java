package ibd.persistent;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
public interface ExternalizablePage  {
    void readExternal(DataInput out) throws IOException;
    void writeExternal(DataOutput out) throws IOException;
    int getSizeInBytes();
}
