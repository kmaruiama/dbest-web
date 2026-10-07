package ibd.query.binaryop.join.outer;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.ReferedDataSource;
import ibd.query.Tuple;
import ibd.query.binaryop.join.JoinPredicate;
import ibd.query.binaryop.join.JoinTerm;
import ibd.query.binaryop.join.LookupJoin;
import ibd.query.lookup.LiteralElement;
import ibd.query.lookup.LookupFilter;
import ibd.query.lookup.SingleColumnLookupFilter;
import ibd.table.prototype.LinkedDataRow;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class NestedLoopLeftJoin extends LookupJoin {

    Tuple nullRightTuple;

    public NestedLoopLeftJoin(Operation leftOperation, Operation rightOperation, JoinPredicate terms) throws Exception {
        super(leftOperation, rightOperation, terms);
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
            nullRightTuple.rows[i] = new LinkedDataRow();
            nullRightTuple.rows[i] = row;
        }

    }

    @Override
    public String getJoinAlgorithm() {
        return "Nested Loop Left Join";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new LeftNestedLoopJoinIterator(processedTuples, withFilterDelegation);
    }

    private class LeftNestedLoopJoinIterator extends UnpagedOperationIterator {

        Tuple currentLeftTuple;

        boolean foundJoin = false;
        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;

        public LeftNestedLoopJoinIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            currentLeftTuple = null;

            leftTuples = leftOperation.lookUp(processedTuples, false);
        }

        private boolean fillFilter() {
            List<JoinTerm> joinTerms = joinPredicate.getTerms();
            int x = 0;
            for (LookupFilter filter : joinFilter.getFilters()) {
                JoinTerm joinTerm = joinTerms.get(x);
                SingleColumnLookupFilter f = (SingleColumnLookupFilter) filter;
                Comparable value = currentLeftTuple.rows[joinTerm.getLeftColumnDescriptor().getColumnLocation().rowIndex].getValue(joinTerm.getLeftColumnDescriptor().getColumnLocation().colIndex);
                if (value==null) 
                    return false;
                LiteralElement elem = (LiteralElement)f.getComparedElement();
                elem.setValue(value);
                x++;

            }
            return true;
        }
        
        

        @Override
        protected Tuple findNextTuple() {

            while (currentLeftTuple != null || leftTuples.hasNext()) {
                if (currentLeftTuple == null) {
                    currentLeftTuple = leftTuples.next();
                    foundJoin = false;

                    processedTuples.add(currentLeftTuple);

                    boolean hasAllJoinTerms = fillFilter();
                    
                    if (hasAllJoinTerms){
                        rightTuples = rightOperation.lookUp(processedTuples, true);
                    }
                    else rightTuples = Collections.emptyIterator();
                    
                    processedTuples.remove(processedTuples.size() - 1);
                }

                processedTuples.add(currentLeftTuple);
                
                while (rightTuples.hasNext()) {
                    Tuple curTuple2 = (Tuple) rightTuples.next();
                    
                    if (!(rightOperation.canProcessDelegatedFilters() || joinFilter.match(curTuple2)))
                        continue;
                    
                    Tuple tuple = new Tuple();
                    tuple.setSourceRows(currentLeftTuple, curTuple2);

                    foundJoin = true;
                    processedTuples.remove(processedTuples.size() - 1);
                    return tuple;

                }
                
                if (!foundJoin) {
                    Tuple tuple = new Tuple();
                    tuple.setSourceRows(currentLeftTuple, nullRightTuple);
                    currentLeftTuple = null;
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
