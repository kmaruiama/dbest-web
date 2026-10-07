package ibd.table.btree;

import ibd.table.btree.RowsIterator;
import ibd.index.btree.DictionaryPair;
import ibd.index.btree.AllKeysIterator;
import ibd.index.btree.table.BPlusTreeFileTable;
import ibd.index.btree.table.BinaryValue;
import ibd.table.prototype.LinkedDataRow;
import java.util.Iterator;

public class AllRowsIterator extends RowsIterator {

    Iterator<DictionaryPair> valueIterator = null;
    public AllRowsIterator(BPlusTreeFileTable btree) {
        super(btree);
        valueIterator = btree.searchAllIterator();
    }
    
    

    @Override
    protected LinkedDataRow findNextTuple() {

        while (valueIterator.hasNext()) {
            DictionaryPair dp = valueIterator.next();
            BinaryValue v = (BinaryValue) dp.getValue();

            nextValue = v.rowData;
            return nextValue;

        }

        return null;

    }
}
