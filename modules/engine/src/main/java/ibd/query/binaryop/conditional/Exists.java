package ibd.query.binaryop.conditional;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.ReferedDataSource;
import ibd.query.Tuple;
import ibd.query.binaryop.BinaryOperation;
import ibd.table.prototype.LinkedDataRow;
import java.util.Iterator;
import java.util.List;

public class Exists extends BinaryOperation {

    boolean conjunctive = true;

    Tuple nullLeftTuple;

    Tuple nullRightTuple;

    public Exists(Operation leftOperation, Operation rightOperation, boolean conjunctive) throws Exception {
        super(leftOperation, rightOperation);
        this.conjunctive = conjunctive;
    }

    @Override
    public void prepare() throws Exception {
        super.prepare();
        setNullLeftTuple();
        setNullRightTuple();
    }

    @Override
    public String toString() {
        return "Exists";
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
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new ExistsIterator(processedTuples, withFilterDelegation);
    }

    private class ExistsIterator extends UnpagedOperationIterator {

        Tuple leftTuple;

        Tuple rightTuple;
        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;

        boolean finished = false;

        public ExistsIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            leftTuple = null;

        }

        @Override
        protected Tuple findNextTuple() {

            if (finished) {
                return null;
            }

            finished = true;
            leftTuples = leftOperation.lookUp(processedTuples, false);

            if (leftTuple == null) {
                if (leftTuples.hasNext()) {
                    while (leftTuples.hasNext()) {
                        leftTuple = leftTuples.next();
                        if (!conjunctive) {
                            Tuple tuple = new Tuple();
                            tuple.setSourceRows(leftTuple, nullRightTuple);
                            return tuple;
                        }
                        break;
                    }
                }
            }
            rightTuples = rightOperation.lookUp(processedTuples, false);

            if (rightTuple == null) {
                while (rightTuples.hasNext()) {
                    rightTuple = rightTuples.next();
                    if (!conjunctive) {
                        Tuple tuple = new Tuple();
                        tuple.setSourceRows(nullLeftTuple, rightTuple);
                        return tuple;
                    }
                    break;
                }
            }

            if (leftTuple == null && rightTuple == null) {
                return null;
            }

            if ((leftTuple == null || rightTuple == null) && conjunctive) {
                return null;
            }

            Tuple tuple = new Tuple();
            tuple.setSourceRows(leftTuple, rightTuple);
            return tuple;
        }

    }

}
