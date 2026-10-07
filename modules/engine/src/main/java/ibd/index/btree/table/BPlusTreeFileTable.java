package ibd.index.btree.table;

import ibd.index.btree.BPlusTreeFile;
import ibd.index.btree.Key;
import ibd.index.btree.TreeIndexHeader;
import ibd.index.btree.Value;
import ibd.persistent.PageFile;
import java.util.List;
import ibd.table.prototype.Prototype;
import ibd.table.prototype.column.Column;

public class BPlusTreeFileTable extends BPlusTreeFile {

    public Prototype prototype;

    public BPlusTreeFileTable(PageFile pagefile, Prototype prototype) throws InstantiationException, IllegalAccessException {
        super(pagefile);
        this.prototype = prototype;
    }

    @Override
    public Key createKey() {
        return new BinaryKey(prototype);
    }
    
    @Override
    public Value createValue() {
        return new BinaryValue(prototype);
    }

    @Override
    protected TreeIndexHeader loadHeader() {
        return new TreeIndexHeaderTable(file.getPageSize(),  rootID, firstLeafID, prototype);
    }
    
    @Override
    protected void readFromHeader(TreeIndexHeader header){
        TreeIndexHeaderTable headerTable = (TreeIndexHeaderTable)header;
        prototype = headerTable.prototype;
        prototype.validateColumns();
    }
    

}
