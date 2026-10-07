package ibd.query.binaryop.join.outer;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.binaryop.join.HashJoin;
import ibd.query.binaryop.join.JoinPredicate;
import ibd.table.prototype.LinkedDataRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HashFullOuterJoin extends HashJoin {


    HashMap<String, List<Tuple>> existingTuples;

    Tuple nullRightTuple;

    Tuple nullLeftTuple;

    public HashFullOuterJoin(Operation leftOperation, Operation rightOperation, JoinPredicate joinPredicate) throws Exception {
        super(leftOperation, rightOperation, joinPredicate);
    }

    @Override
    public void prepare() throws Exception {

        super.prepare();

        setNullLeftTuple();
        setNullRightTuple();

    }

    protected void setNullLeftTuple() throws Exception {
        ReferedDataSource left[] = getLeftOperation().getExposedDataSources();
        nullLeftTuple = new Tuple();
        nullLeftTuple.rows = new LinkedDataRow[left.length];
        for (int i = 0; i < left.length; i++) {
            LinkedDataRow row = new LinkedDataRow(left[i].prototype, false);
            nullLeftTuple.rows[i] = row;
        }

    }

    protected void setNullRightTuple() throws Exception {
        ReferedDataSource right[] = getRightOperation().getExposedDataSources();
        nullRightTuple = new Tuple();
        nullRightTuple.rows = new LinkedDataRow[right.length];
        for (int i = 0; i < right.length; i++) {
            LinkedDataRow row = new LinkedDataRow(right[i].prototype, false);
            nullRightTuple.rows[i] = row;
        }

    }

    @Override
    public String getJoinAlgorithm() {
        return "Hash Full Outer Join";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new HashJoinIterator(processedTuples, withFilterDelegation);
    }

    private class HashJoinIterator extends UnpagedOperationIterator {

        Tuple currentLeftTuple;
        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;

        boolean foundJoin = false;

        TupleTraverser tupleTraverser = null;

        public HashJoinIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            currentLeftTuple = null;
            
            leftTuples = leftOperation.lookUp(processedTuples, false);
            
            if (tuples == null) {
                existingTuples = new HashMap();
            }
            
            buildHash(processedTuples);

        }

       

        @Override
        protected Tuple findNextTuple() {

            while (currentLeftTuple != null || leftTuples.hasNext()) {
                if (currentLeftTuple == null) {
                    currentLeftTuple = leftTuples.next();
                    foundJoin = false;
                    String key = fillKey(currentLeftTuple);
                    
                    List<Tuple> result = existingTuples.get(key);
                    if (result == null) {
                        result = tuples.get(key);
                        if (result != null) {
                            existingTuples.put(key, result);
                            tuples.remove(key);
                        }
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
                    tuple.setSourceRows(currentLeftTuple, curTuple2);

                    foundJoin = true;
                    return tuple;

                }

                if (!foundJoin) {
                    Tuple tuple = new Tuple();
                    tuple.setSourceRows(currentLeftTuple, nullRightTuple);
                    currentLeftTuple = null;
                    return tuple;
                }

                currentLeftTuple = null;

            }
            
            if (tupleTraverser == null) {
                tupleTraverser = new TupleTraverser(tuples);
            }

            while (tupleTraverser.hasNext()) {
                Tuple rightTuple = tupleTraverser.nextTuple();
                Tuple tuple = new Tuple();
                tuple.setSourceRows(nullLeftTuple, rightTuple);
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
