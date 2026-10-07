package ibd.query.binaryop.set;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import java.util.Iterator;
import java.util.List;

public class Difference extends Set { 

    public Difference(Operation leftOperation, Operation rightOperation) throws Exception {
        super(leftOperation, rightOperation);
    }

    @Override
    public String toString() {
        return "Difference";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new DifferenceIterator(processedTuples, withFilterDelegation);
    }

    private class DifferenceIterator extends UnpagedOperationIterator {

        Tuple rightTuple;
        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;

        public DifferenceIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            leftTuples = leftOperation.lookUp(processedTuples, false);
            rightTuples = rightOperation.lookUp(processedTuples, false);
        }

        @Override
        protected Tuple findNextTuple() {

            while (leftTuples.hasNext()) {
                Tuple leftTuple = leftTuples.next();
                if (!hasEqual(leftTuple)) {
                    Tuple tuple = new Tuple();
                    tuple.setSourceRows(leftTuple);
                    return tuple;
                }
            }
            return null;
        }

        private boolean hasEqual(Tuple leftTuple) {
            while (rightTuple != null || rightTuples.hasNext()) {
                if (rightTuple == null) {
                    rightTuple = rightTuples.next();
                }
                int compare = leftTuple.compareTo(rightTuple);
                if (compare < 0) {
                    return false;
                } else if (compare == 0) {
                    return true;
                }
                rightTuple = null;
            }
            return false;
        }

    }

}
