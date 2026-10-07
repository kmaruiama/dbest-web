package ibd.index.btree.table;

import ibd.index.btree.Key;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import ibd.table.prototype.DataRow;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.Prototype;

public class BinaryKey extends Key {

    Prototype prototype;

    public BinaryKey(Prototype prototype) {
        this.prototype = prototype;
        keys = new LinkedDataRow[1];
    }
    
    @Override
    public int getSizeInBytes() {
        return prototype.getPrimaryKeySize();
    }
    
    @Override
    public boolean partialMatch(Key otherKey) {
        DataRow otherRow = (DataRow)otherKey.get(0);
        DataRow thisRow = (DataRow)keys[0];
        return (thisRow.partialMatch(otherRow)==0);
    }
    
    

    @Override
    public void writeExternal(DataOutput out) throws IOException {
        byte bytes[] = prototype.convertPrimaryKeyToByteArray((LinkedDataRow) keys[0]);
        out.write(bytes);
    }

    @Override
    public void readExternal(DataInput in) throws IOException {
        byte b[] = new byte[prototype.getPrimaryKeySize()];
        in.readFully(b);
        DataRow rowData = prototype.convertBinaryToPKRowData(b, null, false, true);
        keys[0] = rowData;
    }

}
