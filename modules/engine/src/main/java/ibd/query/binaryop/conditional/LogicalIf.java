package ibd.query.binaryop.conditional;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.ReferedDataSource;
import ibd.query.Tuple;
import ibd.query.binaryop.BinaryOperation;
import ibd.query.lookup.CompositeLookupFilter;
import ibd.query.lookup.Element;
import ibd.query.lookup.LookupFilter;
import ibd.query.lookup.ReferencedElement;
import ibd.query.lookup.SingleColumnLookupFilter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class LogicalIf extends BinaryOperation {

    
    LookupFilter filter = null;
    
    public LogicalIf(Operation leftOperation, Operation rightOperation, LookupFilter filter) throws Exception {
        super(leftOperation, rightOperation);
        this.filter = filter;
    }

    
    @Override
    public LookupFilter getFilters() {
        return filter;
    }
    
    

    @Override
    public void setExposedDataSources() throws Exception {
        
        ReferedDataSource left[] = getLeftOperation().getExposedDataSources();
        dataSources = new ReferedDataSource[left.length];
        System.arraycopy(left, 0, dataSources, 0, left.length);
        
    }
    
    @Override
    public Map<String, List<String>> getContentInfo() {
        return leftOperation.getContentInfo();
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
        return new LogicalIfIterator(processedTuples, withFilterDelegation);
    }

    private class LogicalIfIterator extends UnpagedOperationIterator {

        Tuple leftTuple = null;
        Tuple rightTuple = null;
        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;
        boolean done = false;

        public LogicalIfIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            leftTuples = leftOperation.lookUp(processedTuples, false);
            rightTuples = rightOperation.lookUp(processedTuples, false);
        }

        @Override
        protected Tuple findNextTuple() {

            if (done) return null;
            done = true;
            setFilterValue(filter, processedTuples);
            
            if (filter.match((Tuple) null)) {
                if (leftTuple==null) leftTuple = leftTuples.next();
                return leftTuple;
            }
            else {
            if (rightTuple==null) rightTuple = rightTuples.next();
                return rightTuple;
            }
        }

    }

}
