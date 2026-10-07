package ibd.query.unaryop;

import ibd.query.Operation;
import ibd.query.QueryStats;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class HashDuplicateRemoval extends UnaryOperation {


    HashMap<String, Tuple> allTuples;
    int tupleSize = 0;
    
    public HashDuplicateRemoval(Operation op) throws Exception {
        super(op);
    }

    @Override
    public void prepare() throws Exception {
        super.prepare();
        tupleSize = childOperation.getTupleSize();
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new HashDuplicateRemovalIterator(processedTuples, withFilterDelegation);
    }
    
    @Override
    public String toString() {
        return "Hash Duplicate Removal";
    }

    private class HashDuplicateRemovalIterator extends UnpagedOperationIterator {

        Iterator<Tuple> tuples;
        
        Iterator<Tuple> allTuplesIterator;
        
        public HashDuplicateRemovalIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            tuples = childOperation.lookUp(processedTuples, false);
            allTuples = new HashMap<>();
        }
        
        private String getKey(Tuple tuple){
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
            while (tuples.hasNext()) {
                Tuple tp = tuples.next();
                
                String key = getKey(tp);
                allTuples.put(key, tp);
            }
            
            if (allTuplesIterator==null){
                allTuplesIterator = allTuples.values().iterator();
                QueryStats.MEMORY_USED += allTuples.size()*tupleSize;
            }
            
            while(allTuplesIterator.hasNext()){
                Tuple tp = allTuplesIterator.next();
                return tp;
            }
            
            
            return null;
        }

    }
}
