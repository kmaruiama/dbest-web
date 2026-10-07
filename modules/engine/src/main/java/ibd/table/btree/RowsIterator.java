package ibd.table.btree;

import ibd.index.btree.AllKeysIterator;
import ibd.index.btree.table.BPlusTreeFileTable;
import ibd.table.prototype.LinkedDataRow;
import java.util.Iterator;

public abstract class RowsIterator implements Iterator<LinkedDataRow> {

    LinkedDataRow nextValue = null;
    
    BPlusTreeFileTable btree;

    public RowsIterator(BPlusTreeFileTable btree) {
        this.btree = btree;
    }
    

    @Override
    public LinkedDataRow next() {

        
        if (nextValue != null) {
            LinkedDataRow next_ = nextValue;
            nextValue = null;
            return next_;
        }

        return findNextTuple();

    }

    @Override
    public boolean hasNext() {

        if (nextValue != null) {
            return true;
        }
        nextValue = findNextTuple();

        return (nextValue != null);
    }

    protected abstract LinkedDataRow findNextTuple();
}
