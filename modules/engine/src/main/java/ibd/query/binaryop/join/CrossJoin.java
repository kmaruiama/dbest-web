package ibd.query.binaryop.join;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import java.util.Iterator;
import java.util.List;

public class CrossJoin extends Join {

    public CrossJoin(Operation leftOperation, Operation rightOperation) throws Exception {
        super(leftOperation, rightOperation, new JoinPredicate());
    }
    
    
    @Override
    public boolean useLeftSideLookups() {
        return true;
    }

    @Override
    public String getJoinAlgorithm() {
        return "Cross Join";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples,  boolean withFilterDelegation) {
        return new CrossJoinIterator(processedTuples,  withFilterDelegation);
    }

    private class CrossJoinIterator extends UnpagedOperationIterator {

        Tuple currentLeftTuple;
        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;
        
        
        public CrossJoinIterator(List<Tuple> processedTuples,  boolean withFilterDelegation) {
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

                    rightTuples = rightOperation.lookUp(processedTuples, false);

                    processedTuples.remove(processedTuples.size() - 1);
                    
                }
                
                processedTuples.add(currentLeftTuple);

                while (rightTuples.hasNext()) {
                    Tuple curTuple2 = (Tuple) rightTuples.next();
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
