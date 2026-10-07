package ibd.query.binaryop.join.anti;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.binaryop.join.HashJoin;
import ibd.query.binaryop.join.JoinPredicate;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HashLeftAntiJoin extends HashJoin {


    public HashLeftAntiJoin(Operation leftOperation, Operation rightOperation, JoinPredicate joinPredicate) throws Exception {
        super(leftOperation, rightOperation, joinPredicate);
    }

    @Override
    public String getJoinAlgorithm() {
        return "Hash Left Anti Join";
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
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new HashJoinIterator(processedTuples, withFilterDelegation);
    }

    private class HashJoinIterator extends UnpagedOperationIterator {

        Iterator<Tuple> leftTuples;

        public HashJoinIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            leftTuples = leftOperation.lookUp(processedTuples, false);

            buildHash(processedTuples);

        }

        

        @Override
        protected Tuple findNextTuple() {

            while (leftTuples.hasNext()) {
                Tuple currentLeftTuple = leftTuples.next();

                String key = fillKey(currentLeftTuple);
                
                boolean exists = tuples.containsKey(key);

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
