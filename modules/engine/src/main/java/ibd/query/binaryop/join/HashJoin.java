package ibd.query.binaryop.join;

import ibd.query.ColumnDescriptor;
import ibd.query.Operation;
import ibd.query.QueryStats;
import ibd.query.Tuple;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public abstract class HashJoin extends Join {


    protected HashMap<String, List<Tuple>> tuples;

    public HashJoin(Operation leftOperation, Operation rightOperation, JoinPredicate joinPredicate) throws Exception {
        super(leftOperation, rightOperation, joinPredicate);
    }

    @Override
    public void prepare() throws Exception {

        super.prepare();

        tuples = null;

    }

    protected void buildHash(List<Tuple> processedTuples) {
        if (tuples == null) {
            tuples = new HashMap();
            long memoryUsed = 0;
            try {
                Iterator<Tuple> it = rightOperation.lookUp(processedTuples, false);
                int tupleSize = rightOperation.getTupleSize();
                while (it.hasNext()) {
                    Tuple tuple = (Tuple) it.next();
                    String key = "";
                    for (JoinTerm term : joinPredicate.getTerms()) 
                    {
                        ColumnDescriptor col = term.getRightColumnDescriptor();
                        key += tuple.rows[col.getColumnLocation().rowIndex].getValue(col.getColumnLocation().colIndex).toString();
                    }

                    List tupleList = tuples.get(key);
                    if (tupleList == null) {
                        tupleList = new ArrayList();
                        tuples.put(key, tupleList);
                    }
                    tupleList.add(tuple);
                    memoryUsed += tupleSize;
                }

            } catch (Exception ex) {
            }
            QueryStats.MEMORY_USED += memoryUsed;
        }

    }

    protected String fillKey(Tuple currentLeftTuple) {
        String key = "";
        List<JoinTerm> joinTerms = joinPredicate.getTerms();
        for (int i = 0; i < joinTerms.size(); i++) {
            JoinTerm joinTerm = joinTerms.get(i);
            Comparable value = currentLeftTuple.rows[joinTerm.getLeftColumnDescriptor().getColumnLocation().rowIndex].getValue(joinTerm.getLeftColumnDescriptor().getColumnLocation().colIndex);
            key += value;

        }
        return key;
    }

}
