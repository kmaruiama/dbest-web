package ibd.query.unaryop;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import java.util.Iterator;
import java.util.List;

public class DuplicateRemoval extends UnaryOperation {


    public DuplicateRemoval(Operation op) throws Exception {
        super(op);
    }

    @Override
    public String toString() {
        return "Sorted Duplicate Removal";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new DuplicateRemovalIterator(processedTuples, withFilterDelegation);
    }

    private class DuplicateRemovalIterator extends UnpagedOperationIterator {

        Iterator<Tuple> tuples;
        Tuple prevTuple = null;

        public DuplicateRemovalIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            tuples = childOperation.lookUp(processedTuples, false);
        }

        @Override
        protected Tuple findNextTuple() {
            while (tuples.hasNext()) {
                Tuple tp = tuples.next();
                
                if (prevTuple == null || (!tp.equals(prevTuple))) {
                    prevTuple = tp;
                    return tp;
                }

            }
            return null;
        }

    }
}
