package ibd.query.lookup;

import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;

public interface LookupFilter {
    
    public boolean match(Tuple tuple) ;
    
    public boolean match(LinkedDataRow row) ;
}
