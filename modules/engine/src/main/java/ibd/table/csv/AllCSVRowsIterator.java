package ibd.table.csv;

import ibd.table.prototype.LinkedDataRow;
import sources.csv.InvalidCsvException;

public class AllCSVRowsIterator extends CSVRowsIterator {


    public AllCSVRowsIterator(CSVTable csvTable) throws InvalidCsvException {
        super(csvTable);
    }

    
    @Override
    protected boolean match(LinkedDataRow dataRow){
    return true;
    }
}
