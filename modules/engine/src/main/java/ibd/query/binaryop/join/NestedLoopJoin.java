package ibd.query.binaryop.join;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import java.util.Iterator;
import java.util.List;

public class NestedLoopJoin extends LookupJoin {

    public NestedLoopJoin(Operation leftOperation, Operation rightOperation, JoinPredicate joinPredicate) throws Exception {
        super(leftOperation, rightOperation, joinPredicate);
    }


    @Override
    public String getJoinAlgorithm() {
        return "Nested Loop Join";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new NestedLoopJoinIterator(processedTuples, withFilterDelegation);
    }

    private class NestedLoopJoinIterator extends UnpagedOperationIterator {

        Tuple currentLeftTuple;
        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;

        public NestedLoopJoinIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            currentLeftTuple = null;
            leftTuples = leftOperation.lookUp(processedTuples, false);
        }

        
        @Override
        protected Tuple findNextTuple() {

            while (currentLeftTuple != null || leftTuples.hasNext()) {
                if (currentLeftTuple == null) {
                    currentLeftTuple = leftTuples.next();

                    processedTuples.add(currentLeftTuple);

                    fillFilter(currentLeftTuple);
                    rightTuples = rightOperation.lookUp(processedTuples, true);

                    processedTuples.remove(processedTuples.size() - 1);
                }
                
                processedTuples.add(currentLeftTuple);

                while (rightTuples.hasNext()) {
                    Tuple curTuple2 = (Tuple) rightTuples.next();
                    
                    if (!(rightOperation.canProcessDelegatedFilters() || joinFilter.match(curTuple2)))
                        continue;
                    
                    Tuple tuple = new Tuple();
                    tuple.setSourceRows(currentLeftTuple, curTuple2);
                    
                    processedTuples.remove(processedTuples.size() - 1);
                    return tuple;

                }
                currentLeftTuple = null;
                
                processedTuples.remove(processedTuples.size() - 1);
            }

            return null;
        }
    }

}
