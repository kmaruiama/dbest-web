package ibd.query.unaryop;

import ibd.query.Operation;
import ibd.query.QueryStats;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Materialization extends UnaryOperation {

    List<Tuple> tuples;

    public Materialization(Operation childOperation) throws Exception {
        super(childOperation);
    }

    @Override
    public void prepare() throws Exception {
        super.prepare();

        tuples = null;
    }
    
    @Override
    public boolean canProcessDelegatedFilters() {
        return true;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new MaterializedIndexIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public String toString() {
        return "Materialized";
    }

    public class MaterializedIndexIterator extends UnpagedOperationIterator {

        Iterator<Tuple> it = null;

        public MaterializedIndexIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {

            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            buildList();
            it = tuples.iterator();
        }

        private void buildList() {
            if (tuples == null) {
                tuples = new ArrayList<>();
                int tupleSize = 0;
                try {
                    tupleSize = childOperation.getTupleSize();
                } catch (Exception ex) {
                }
                it = childOperation.lookUp(processedTuples, false);

                while (it.hasNext()) {
                    Tuple tuple = (Tuple) it.next();
                    tuples.add(tuple);
                }

                QueryStats.MEMORY_USED += tuples.size() * tupleSize;
            }

        }

        @Override
        protected Tuple findNextTuple() {
            while (it.hasNext()) {
                Tuple tp = it.next();
                if (lookup.match(tp)) {
                    return tp;
                }

            }
            return null;
        }

    }

}
