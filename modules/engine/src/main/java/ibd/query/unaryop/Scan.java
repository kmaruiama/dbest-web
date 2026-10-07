package ibd.query.unaryop;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import java.util.Iterator;
import java.util.List;

public class Scan extends UnaryOperation {

    public Scan(Operation childOperation) throws Exception {
        super(childOperation);
    }

    @Override
    public String toString() {
        return "Scan";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new ScanIterator(processedTuples, withFilterDelegation);
    }

    public class ScanIterator extends UnpagedOperationIterator {

        Iterator<Tuple> tuples;

        public ScanIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            tuples = childOperation.lookUp(processedTuples, true);
        }

        @Override
        protected Tuple findNextTuple() {
            while (tuples.hasNext()) {
                Tuple tp = tuples.next();
                return tp;

            }
            return null;
        }

    }

}
