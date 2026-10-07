package ibd.table.lookup;

import ibd.table.prototype.LinkedDataRow;


public class NoRowLookupFilter implements RowLookupFilter{

    @Override
    public boolean match(LinkedDataRow row) {
        return true; 
    }

    
}
