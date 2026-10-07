package ibd.query.binaryop.join.anti;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.ReferedDataSource;
import ibd.query.Tuple;
import ibd.query.binaryop.join.JoinPredicate;
import ibd.query.binaryop.join.LookupJoin;
import ibd.query.lookup.CompositeLookupFilter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class NestedLoopLeftAntiJoin extends LookupJoin {

    public NestedLoopLeftAntiJoin(Operation leftOperation, Operation rightOperation, JoinPredicate joinPredicate) throws Exception {
        super(leftOperation, rightOperation, joinPredicate);
    }

    
    
    @Override
    public void setExposedDataSources() throws Exception {

        ReferedDataSource left[] = getLeftOperation().getExposedDataSources();
        dataSources = new ReferedDataSource[left.length];
        System.arraycopy(left, 0, dataSources, 0, left.length);

    }

    @Override
    public Map<String, List<String>> getContentInfo() {
        return getLeftOperation().getContentInfo();
    }

    @Override
    public String getJoinAlgorithm() {
        return "Nested Loop Left Anti Join";
    }

    
    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new NestedLoopLeftAntiJoinIterator(processedTuples, withFilterDelegation);
    }

    private class NestedLoopLeftAntiJoinIterator extends UnpagedOperationIterator {

        Iterator<Tuple> leftTuples;
        
        public NestedLoopLeftAntiJoinIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            leftTuples = leftOperation.lookUp(processedTuples, false);
        }

        private boolean exists() {
            if (rightOperation.canProcessDelegatedFilters() || hasNoFilters) {
                return rightOperation.exists(processedTuples, true);
            }

            Iterator tuples = rightOperation.lookUp(processedTuples, false);
            while (tuples.hasNext()) {
                Tuple tuple = (Tuple) tuples.next();
                if (rightOperation.canProcessDelegatedFilters() || joinFilter.match(tuple)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        protected Tuple findNextTuple() {

            while (leftTuples.hasNext()) {
                Tuple currentLeftTuple = leftTuples.next();

                processedTuples.add(currentLeftTuple);

                fillFilter(currentLeftTuple);

                boolean exists = exists();

                processedTuples.remove(processedTuples.size() - 1);

                if (!exists) {
                    Tuple tuple = new Tuple();
                    tuple.setSourceRows(currentLeftTuple);
                    return tuple;
                }

            }

            return null;
        }
    }

}
