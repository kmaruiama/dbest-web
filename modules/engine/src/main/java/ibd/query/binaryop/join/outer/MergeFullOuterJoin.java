package ibd.query.binaryop.join.outer;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.binaryop.join.Join;
import ibd.query.binaryop.join.JoinPredicate;
import ibd.query.binaryop.join.JoinTerm;
import ibd.table.prototype.LinkedDataRow;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class MergeFullOuterJoin extends Join {

    Comparable leftTupleArray[];
    Comparable rightTupleArray[];

    Comparable nextLeftTupleArray[];
    Comparable nextRightTupleArray[];

    Tuple nullRightTuple;

    Tuple nullLeftTuple;

    public MergeFullOuterJoin(Operation leftOperation, Operation rightOperation, JoinPredicate terms) throws Exception {
        super(leftOperation, rightOperation, terms);
    }

    @Override
    public void prepare() throws Exception {

        super.prepare();

        leftTupleArray = new Comparable[joinPredicate.size()];
        rightTupleArray = new Comparable[joinPredicate.size()];

        nextLeftTupleArray = new Comparable[joinPredicate.size()];
        nextRightTupleArray = new Comparable[joinPredicate.size()];

        setNullLeftTuple();
        setNullRightTuple();
    }

    protected void setNullLeftTuple() throws Exception {
        ReferedDataSource left[] = getLeftOperation().getExposedDataSources();
        nullLeftTuple = new Tuple();
        nullLeftTuple.rows = new LinkedDataRow[left.length];
        for (int i = 0; i < left.length; i++) {
            LinkedDataRow row = new LinkedDataRow(left[i].prototype, false);
            nullLeftTuple.rows[i] = new LinkedDataRow();
            nullLeftTuple.rows[i] = row;
        }

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
        return "Merge Full Outer Join";
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

        List<Tuple> leftDuplicates = new ArrayList<>();
        List<Tuple> rightDuplicates = new ArrayList<>();
        boolean returningDuplicates = false;
        int rightIndex = 0;
        int leftIndex = 0;

        public MergeJoinIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            leftTuple = null;

            leftTuples = leftOperation.lookUp(processedTuples, false);
            rightTuples = rightOperation.lookUp(processedTuples, false);

        }

        private void fillLeftTupleArray(Comparable tupleArray[], Tuple currentLeftTuple) {
            int i = 0;
            for (JoinTerm term : joinPredicate.getTerms()) {
                Comparable value = currentLeftTuple.rows[term.getLeftColumnDescriptor().getColumnLocation().rowIndex].getValue(term.getLeftColumnIndex());
                tupleArray[i] = value;
                i++;
            }
        }

        private void fillRightTupleArray(Comparable tupleArray[], Tuple currentRightTuple) {
            int i = 0;
            for (JoinTerm term : joinPredicate.getTerms()) {
                Comparable value = currentRightTuple.rows[term.getRightColumnDescriptor().getColumnLocation().rowIndex].getValue(term.getRightColumnIndex());
                tupleArray[i] = value;
                i++;
            }
        }

        @Override
        protected Tuple findNextTuple() {

            while (true) {
                if (returningDuplicates) {
                    if (leftIndex < leftDuplicates.size()) {
                        if (rightIndex < rightDuplicates.size()) {
                            Tuple tuple = new Tuple();
                            tuple.setSourceRows(leftDuplicates.get(leftIndex), rightDuplicates.get(rightIndex));
                            rightIndex++;
                            if (rightIndex >= rightDuplicates.size()) {
                                rightIndex = 0;
                                leftIndex++;
                            }
                            return tuple;
                        }
                    } else {
                        returningDuplicates = false;
                        leftDuplicates.clear();
                        rightDuplicates.clear();
                    }
                }

                if (leftTuple == null && leftTuples.hasNext()) {
                    leftTuple = leftTuples.next();
                    fillLeftTupleArray(leftTupleArray, leftTuple);
                }

                if (rightTuple == null && rightTuples.hasNext()) {
                    rightTuple = rightTuples.next();
                    fillLeftTupleArray(rightTupleArray, rightTuple);
                }

                if (leftTuple == null || rightTuple == null) {
                    return null;
                }

                int comp = Arrays.compare(leftTupleArray, rightTupleArray);

                if (comp == 0) {
                    if (leftDuplicates.isEmpty()) {
                        leftDuplicates.add(leftTuple);
                        rightDuplicates.add(rightTuple);
                    }

                    leftTuple = null;
                    while (leftTuples.hasNext()) {
                        Tuple nextLeft = leftTuples.next();
                        fillLeftTupleArray(nextLeftTupleArray, nextLeft);
                        if (Arrays.compare(nextLeftTupleArray, leftTupleArray) == 0) {
                            leftDuplicates.add(nextLeft);
                        } else {
                            leftTuple = nextLeft;
                            System.arraycopy(nextLeftTupleArray, 0, leftTupleArray, 0, leftTupleArray.length);
                            break;
                        }
                    }

                    rightTuple = null;
                    while (rightTuples.hasNext()) {
                        Tuple nextRight = rightTuples.next();
                        fillRightTupleArray(nextRightTupleArray, nextRight);
                        if (Arrays.compare(nextRightTupleArray, rightTupleArray) == 0) {
                            rightDuplicates.add(nextRight);
                        } else {
                            rightTuple = nextRight;
                            System.arraycopy(nextRightTupleArray, 0, rightTupleArray, 0, rightTupleArray.length);
                            break;
                        }
                    }

                    returningDuplicates = true;
                    leftIndex = 0;
                    rightIndex = 0;

                } else if (comp < 0) {
                    Tuple tuple = new Tuple();
                    tuple.setSourceRows(leftTuple, nullRightTuple);
                    leftTuple = null;
                    return tuple;

                } else {
                    Tuple tuple = new Tuple();
                    tuple.setSourceRows(nullLeftTuple, rightTuple);
                    rightTuple = null;
                    return tuple;
                }
            }

        }

    }

}
