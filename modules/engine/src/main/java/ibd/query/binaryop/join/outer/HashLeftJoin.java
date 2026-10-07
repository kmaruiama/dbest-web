package ibd.query.binaryop.join.outer;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.binaryop.join.HashJoin;
import ibd.query.binaryop.join.JoinPredicate;
import ibd.table.prototype.LinkedDataRow;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HashLeftJoin extends HashJoin {


    Tuple nullRightTuple;

    
    public HashLeftJoin(Operation leftOperation, Operation rightOperation, JoinPredicate joinPredicate) throws Exception {
        super(leftOperation, rightOperation, joinPredicate);
    }

    @Override
    public void prepare() throws Exception {

        super.prepare();

        setNullRightTuple();

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
        return "Hash Left Join";
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
                    foundJoin = false;
                    String key = fillKey(currentLeftTuple);
                    List<Tuple> result = tuples.get(key);
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

            return null;
        }
    }

}
