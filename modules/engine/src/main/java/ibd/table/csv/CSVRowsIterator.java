package ibd.table.csv;

import engine.info.Parameters;
import ibd.table.prototype.LinkedDataRow;
import java.util.Iterator;
import sources.csv.InvalidCsvException;

public abstract class CSVRowsIterator implements Iterator<LinkedDataRow> {

    LinkedDataRow nextValue = null;
    Iterator<String[]> iterator;
    CSVTable csvTable;
    long pk = 0L;

    public CSVRowsIterator(CSVTable csvTable) throws InvalidCsvException {
        this.csvTable = csvTable;
        iterator = csvTable.recognizer.iterator();
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
    protected LinkedDataRow findNextTuple(){
    while (iterator.hasNext()) {
            String[] data = iterator.next();
            if (data == null) {
                return null;
            }
            pk++;

            String[] columns = csvTable.recognizer.getColumnNames();
            LinkedDataRow dataRow = csvTable.convertRow(columns, data, pk);
            Parameters.RECORDS_READ++;
           if (match(dataRow)) {
                nextValue = dataRow;
                return nextValue;
            } 

        }

        return null;
    
    
    }
    
    protected abstract boolean match(LinkedDataRow dataRow);
}
