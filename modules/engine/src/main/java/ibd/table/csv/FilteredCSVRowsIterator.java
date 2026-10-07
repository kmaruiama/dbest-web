package ibd.table.csv;

import ibd.query.lookup.LookupFilter;
import ibd.table.prototype.LinkedDataRow;
import sources.csv.InvalidCsvException;

public class FilteredCSVRowsIterator extends CSVRowsIterator {

    LookupFilter filter;

    public FilteredCSVRowsIterator(CSVTable csvTable, LookupFilter filter) throws InvalidCsvException {
        super(csvTable);
        this.filter = filter;
    }

    
    @Override
    protected boolean match(LinkedDataRow dataRow){
        return filter.match(dataRow);
    }
    
}
