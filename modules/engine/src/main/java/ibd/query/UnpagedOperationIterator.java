package ibd.query;

import ibd.query.lookup.LookupFilter;
import ibd.query.lookup.NoLookupFilter;
import java.util.List;

public abstract class UnpagedOperationIterator extends OperationIterator {


    protected LookupFilter lookup;
    
    protected List<Tuple> processedTuples;

    public UnpagedOperationIterator(List<Tuple> processedTuples, boolean withFilterDelegation, LookupFilter parentFilters) {
        this.processedTuples = processedTuples;
        if (withFilterDelegation) {
            this.lookup = parentFilters;
        } else {
            this.lookup = new NoLookupFilter();
        }
    }

}
