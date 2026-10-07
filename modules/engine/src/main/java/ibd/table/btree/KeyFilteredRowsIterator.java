package ibd.table.btree;

import ibd.index.btree.DictionaryPair;
import ibd.index.btree.Key;
import ibd.index.btree.table.BPlusTreeFileTable;
import ibd.index.btree.table.BinaryValue;
import ibd.query.lookup.LookupFilter;
import ibd.table.ComparisonTypes;
import ibd.table.lookup.RowLookupFilter;
import ibd.table.prototype.LinkedDataRow;
import java.util.Collections;
import java.util.Iterator;

public class KeyFilteredRowsIterator extends RowsIterator {

    LookupFilter filter;
    Iterator<DictionaryPair> valueIterator = null;

    public KeyFilteredRowsIterator(BPlusTreeFileTable btree, Key key, LookupFilter filter, int compType) {
        super(btree);
        this.filter = filter;
        switch (compType) {
            case ComparisonTypes.EQUAL -> valueIterator = btree.equalKeyIterator(key);
            case ComparisonTypes.GREATER_EQUAL_THAN -> valueIterator = btree.largerKeyIterator(key, true);
            case ComparisonTypes.GREATER_THAN -> valueIterator = btree.largerKeyIterator(key, false);
            case ComparisonTypes.LOWER_EQUAL_THAN -> valueIterator = btree.smallerKeyIterator(key, true);
            case ComparisonTypes.LOWER_THAN -> valueIterator = btree.smallerKeyIterator(key, false);
            case ComparisonTypes.DIFF -> valueIterator = btree.differentKeyIterator(key);
            case ComparisonTypes.IS_NULL -> valueIterator = Collections.emptyIterator();
            case ComparisonTypes.IS_NOT_NULL -> valueIterator = btree.searchAllIterator();
            default -> {
            }
        }
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
