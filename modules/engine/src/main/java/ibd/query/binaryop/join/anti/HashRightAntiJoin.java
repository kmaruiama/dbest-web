package ibd.query.binaryop.join.anti;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.binaryop.join.HashJoin;
import ibd.query.binaryop.join.JoinPredicate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HashRightAntiJoin extends HashJoin {


    
    public HashRightAntiJoin(Operation leftOperation, Operation rightOperation, JoinPredicate joinPredicate) throws Exception {
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
        return "Hash Right Anti Join";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new HashJoinIterator(processedTuples, withFilterDelegation);
    }

    private class HashJoinIterator extends UnpagedOperationIterator {

        Iterator<Tuple> leftTuples;

        TupleTraverser tupleTraverser = null;

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
                tuples.remove(key);
            }

            if (tupleTraverser == null) {
                tupleTraverser = new TupleTraverser(tuples);
            }
            
            while (tupleTraverser.hasNext()) {
                Tuple curTuple2 = (Tuple) tupleTraverser.nextTuple();
                Tuple tuple = new Tuple();
                tuple.setSourceRows(curTuple2);
                return tuple;

            }

            return null;
        }
    }

    class TupleTraverser {

        private final HashMap<String, List<Tuple>> tuples;
        private Iterator<Map.Entry<String, List<Tuple>>> mapIterator;
        private List<Tuple> currentList;
        private int currentIndex;

        public TupleTraverser(HashMap<String, List<Tuple>> tuples) {
            this.tuples = tuples;
            this.mapIterator = tuples.entrySet().iterator();
            this.currentList = null;
            this.currentIndex = 0;
        }

        public Tuple nextTuple() {
            if (currentList == null || currentIndex >= currentList.size()) {
                if (mapIterator.hasNext()) {
                    Map.Entry<String, List<Tuple>> entry = mapIterator.next();
                    currentList = entry.getValue();
                    currentIndex = 0;
                } else {
                    return null;
                }
            }

            Tuple nextTuple = currentList.get(currentIndex);
            currentIndex++;
            return nextTuple;
        }

        public boolean hasNext() {
            return (currentList != null && currentIndex < currentList.size()) || mapIterator.hasNext();
        }
    }

}
