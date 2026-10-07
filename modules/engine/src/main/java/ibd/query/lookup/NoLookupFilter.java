package ibd.query.lookup;

import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;


public class NoLookupFilter implements LookupFilter{

    @Override
    public boolean match(Tuple tuple) {
        return true; 
    }
    
    @Override
    public boolean match(LinkedDataRow row) {
        return true; 
    }

    
}
