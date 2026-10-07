package ibd.query.unaryop.filter;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.lookup.CompositeLookupFilter;
import ibd.query.lookup.Element;
import ibd.query.lookup.LookupFilter;
import ibd.query.lookup.ReferencedElement;
import ibd.query.lookup.SingleColumnLookupFilter;
import ibd.query.unaryop.UnaryOperation;
import java.util.Iterator;
import java.util.List;

public class Filter extends UnaryOperation {

    LookupFilter filter = null;

    public Filter(Operation childOperation, LookupFilter filter) throws Exception {
        super(childOperation);
        this.filter = filter;
    }


    @Override
    public LookupFilter getFilters() {
        return filter;
    }

    

    
    private void setFilterValue(LookupFilter filter, List<Tuple> processedTuples) {
        if (filter instanceof CompositeLookupFilter) {
            setFilterValue((CompositeLookupFilter) filter, processedTuples);

        } else if (filter instanceof SingleColumnLookupFilter) {
            setFilterValue((SingleColumnLookupFilter) filter, processedTuples);
        }
    }

    private void setFilterValue(CompositeLookupFilter filter, List<Tuple> processedTuples) {
        for (LookupFilter filter1 : filter.getFilters()) {
            setFilterValue(filter1, processedTuples);
        }
    }

    private void setFilterValue(SingleColumnLookupFilter filter, List<Tuple> processedTuples) {
        Element elem = filter.getFirstElement();
        if (elem instanceof ReferencedElement) {
            ((ReferencedElement) elem).setValue(processedTuples);
        }

        elem = filter.getSecondElement();
        if (elem instanceof ReferencedElement) {
            ((ReferencedElement) elem).setValue(processedTuples);
        }
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new FilterOperationIterator(processedTuples, withFilterDelegation);
    }

    public class FilterOperationIterator extends UnpagedOperationIterator {

        Iterator<Tuple> tuples;

        public FilterOperationIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            setFilterValue(filter, processedTuples);

            tuples = childOperation.lookUp(processedTuples, true);
        }

        @Override
        protected Tuple findNextTuple() {
            while (tuples.hasNext()) {
                Tuple tp = tuples.next();
                if (childOperation.canProcessDelegatedFilters() || filter.match(tp)) {
                    return tp;
                }

            }
            return null;
        }

    }

    @Override
    public String toString() {
        return "Filter (" + filter + ")";
    }
}
