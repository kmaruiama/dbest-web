package ibd.query.binaryop.join.semi;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.binaryop.join.HashJoin;
import ibd.query.binaryop.join.JoinPredicate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HashRightSemiJoin extends HashJoin {


    public HashRightSemiJoin(Operation leftOperation, Operation rightOperation, JoinPredicate joinPredicate) throws Exception {
        super(leftOperation, rightOperation, joinPredicate);
    }

    @Override
    public void setExposedDataSources() throws Exception {

        ReferedDataSource right[] = getRightOperation().getExposedDataSources();
        dataSources = new ReferedDataSource[right.length];
        System.arraycopy(right, 0, dataSources, 0, right.length);

    }
    
    @Override
    public Map<String, List<String>> getContentInfo() {
        return getRightOperation().getContentInfo();
    }

    @Override
    public String getJoinAlgorithm() {
        return "Hash Right Semi Join";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new HashJoinIterator(processedTuples, withFilterDelegation);
    }

    private class HashJoinIterator extends UnpagedOperationIterator {

        Tuple currentLeftTuple;
        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;

        public HashJoinIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            currentLeftTuple = null;
            leftTuples = leftOperation.lookUp(processedTuples, false);

            buildHash(processedTuples);

        }

        

        @Override
        protected Tuple findNextTuple() {

            while (currentLeftTuple != null || leftTuples.hasNext()) {
                if (currentLeftTuple == null) {
                    currentLeftTuple = leftTuples.next();

                    String key = fillKey(currentLeftTuple);
                    
                    List<Tuple> result = tuples.get(key);
                    
                    if (result != null) {
                        tuples.remove(key);
                    }
                    
                    if (result != null) {
                        rightTuples = result.iterator();
                    } else {
                        rightTuples = new ArrayList<Tuple>().iterator();
                    }

                }

                while (rightTuples.hasNext()) {
                    Tuple curTuple2 = (Tuple) rightTuples.next();
                    Tuple tuple = new Tuple();
                    tuple.setSourceRows(curTuple2);
                    return tuple;

                }
                currentLeftTuple = null;

            }

            return null;
        }
    }

}
