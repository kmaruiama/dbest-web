package ibd.query.binaryop.set;

import ibd.query.Operation;
import ibd.query.QueryStats;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.query.fields.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class HashIntersection extends Set {

    HashMap<String, Tuple> tuples;

    public HashIntersection(Operation leftOperation, Operation rightOperation) throws Exception {
        super(leftOperation, rightOperation);
    }

    @Override
    public String toString() {
        return "Hash Intersection";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new UnionIterator(processedTuples, withFilterDelegation);
    }

    private class UnionIterator extends UnpagedOperationIterator {

        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;
        
        int tupleSize = 0;

        public UnionIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            tuples = new HashMap<>();
            leftTuples = leftOperation.lookUp(processedTuples, false);
            rightTuples = rightOperation.lookUp(processedTuples, false);
            try {
                tupleSize = rightOperation.getTupleSize();
            } catch (Exception ex) {
            }
        }

        private String getKey(Tuple tuple) {
            String key = "";
            for (LinkedDataRow row : tuple.rows) {
                for (int i = 0; i < row.getFieldsSize(); i++) {
                    Comparable value = row.getValue(i);
                    key += value;
                }

            }
            return key;
        }

        @Override
        protected Tuple findNextTuple() {

            while (rightTuples.hasNext()) {
                Tuple rightTuple = rightTuples.next();
                String key = getKey(rightTuple);
                tuples.put(key, rightTuple);
                QueryStats.MEMORY_USED += tupleSize;
            }
            

            while (leftTuples.hasNext()) {
                Tuple leftTuple = leftTuples.next();
                String key = getKey(leftTuple);
                if ((tuples.containsKey(key))) {
                    return leftTuple;
                }
            }

            return null;

        }

    }

}
