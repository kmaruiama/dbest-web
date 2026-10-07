package ibd.table.btree;

import ibd.index.btree.DictionaryPair;
import ibd.index.btree.table.BPlusTreeFileTable;
import ibd.index.btree.table.BinaryValue;
import ibd.query.lookup.LookupFilter;
import ibd.table.lookup.RowLookupFilter;
import ibd.table.prototype.LinkedDataRow;
import java.util.Iterator;

public class FilteredRowsIterator extends RowsIterator {

    LookupFilter filter;
    Iterator<DictionaryPair> valueIterator = null;

    public FilteredRowsIterator(BPlusTreeFileTable btree, LookupFilter filter) {
        super(btree);
        this.filter = filter;
        valueIterator = btree.searchAllIterator();
    }

    @Override
    protected LinkedDataRow findNextTuple() {

        while (valueIterator.hasNext()) {
            DictionaryPair dp = valueIterator.next();
            BinaryValue v = (BinaryValue) dp.getValue();
            LinkedDataRow dataRow = v.rowData;
            if (filter.match(dataRow)) {
                nextValue = dataRow;
                return nextValue;
            }
        }

        return null;

    }
}
