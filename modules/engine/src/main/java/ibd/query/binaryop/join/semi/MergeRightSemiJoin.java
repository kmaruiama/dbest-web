package ibd.query.binaryop.join.semi;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.binaryop.join.Join;
import ibd.query.binaryop.join.JoinPredicate;
import ibd.query.binaryop.join.JoinTerm;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MergeRightSemiJoin extends Join {

    Comparable leftTupleArray[];
    Comparable rightTupleArray[];

    public MergeRightSemiJoin(Operation leftOperation, Operation rightOperation, JoinPredicate terms) throws Exception {
        super(leftOperation, rightOperation, terms);
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
    public void prepare() throws Exception {

        super.prepare();

        leftTupleArray = new Comparable[joinPredicate.size()];
        rightTupleArray = new Comparable[joinPredicate.size()];
    }

    @Override
    public String getJoinAlgorithm() {
        return "Merge Right Semi Join";
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new MergeJoinIterator(processedTuples, withFilterDelegation);
    }

    private class MergeJoinIterator extends UnpagedOperationIterator {

        Tuple leftTuple;

        Tuple rightTuple;
        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;

        public MergeJoinIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            leftTuple = null;

            leftTuples = leftOperation.lookUp(processedTuples, false);
            rightTuples = rightOperation.lookUp(processedTuples, false);

        }

        private void fillLeftTupleArray(Tuple currentLeftTuple) {
            int i = 0;
            for (JoinTerm term : joinPredicate.getTerms()) {
                Comparable value = currentLeftTuple.rows[term.getLeftColumnDescriptor().getColumnLocation().rowIndex].getValue(term.getLeftColumnIndex());
                leftTupleArray[i] = value;
                i++;
            }
        }

        private void fillRightTupleArray(Tuple currentRightTuple) {
            int i = 0;
            for (JoinTerm term : joinPredicate.getTerms()) {
                Comparable value = currentRightTuple.rows[term.getRightColumnDescriptor().getColumnLocation().rowIndex].getValue(term.getRightColumnIndex());
                rightTupleArray[i] = value;
                i++;
            }
        }

        @Override
        protected Tuple findNextTuple() {

            while (true) {
                if (leftTuple == null) {
                    if (leftTuples.hasNext()) {
                        leftTuple = leftTuples.next();
                        fillLeftTupleArray(leftTuple);
                    }
                }

                if (rightTuple == null) {
                    if (rightTuples.hasNext()) {
                        rightTuple = rightTuples.next();
                        fillRightTupleArray(rightTuple);
                    }
                }
                if (leftTuple == null || rightTuple == null) {
                    return null;
                }

                int comp = Arrays.compare(leftTupleArray, rightTupleArray);
                if (comp == 0) {
                    Tuple tuple = new Tuple();
                    tuple.setSourceRows(rightTuple);
                    rightTuple = null;
                    leftTuple = null;
                    return tuple;
                } else if (comp < 0) {
                    leftTuple = null;
                } else {
                    rightTuple = null;
                }

            }
        }

    }

}
