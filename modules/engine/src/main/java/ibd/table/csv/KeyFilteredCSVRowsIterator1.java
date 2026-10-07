package ibd.table.csv;

import ibd.query.lookup.LookupFilter;
import ibd.table.prototype.LinkedDataRow;
import sources.csv.InvalidCsvException;

public class KeyFilteredCSVRowsIterator1 extends CSVRowsIterator {

    LookupFilter filter;
    LinkedDataRow pkRow;

    public KeyFilteredCSVRowsIterator1(CSVTable csvTable, LinkedDataRow pkRow, LookupFilter filter) throws InvalidCsvException {
        super(csvTable);
        this.filter = filter;
        this.pkRow = pkRow;
    }

    
    @Override
    protected boolean match(LinkedDataRow dataRow){
        return (filter.match(dataRow) && dataRow.compareTo(pkRow)==0);
    }
}
