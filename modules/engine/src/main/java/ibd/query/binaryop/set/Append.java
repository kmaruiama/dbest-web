package ibd.query.binaryop.set;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.SingleSource;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.Prototype;
import ibd.table.prototype.column.Column;
import java.util.Iterator;
import java.util.List;

public class Append extends Set implements SingleSource{

    public Append(Operation leftOperation, Operation rightOperation) throws Exception {
        super(leftOperation, rightOperation);
    }

    @Override
    public String toString() {
        return "Append";
    }

    protected Prototype setPrototype() throws Exception {
        Prototype prototype = new Prototype();
        int leftSize = getColumnsSize(leftOperation);
        int rightSize = getColumnsSize(rightOperation);
        int minSize = Math.min(leftSize, rightSize);

        int currentColumn = 0;

        for (ReferedDataSource dataSource : getLeftOperation().getExposedDataSources()) {
            List<Column> columns = dataSource.prototype.getColumns();
            int columnsToCopy = Math.min(columns.size(), minSize - currentColumn);

            for (int i = 0; i < columnsToCopy; i++) {
                Column originalCol = columns.get(i);
                Column newCol = Prototype.cloneColumn(originalCol);
                prototype.addColumn(newCol);
                currentColumn++;
            }

            if (currentColumn >= minSize) {
                break;
            }
        }
        return prototype;
        
    }

    protected int getColumnsSize(Operation op) throws Exception {
        int colSize = 0;
        for (ReferedDataSource dataSource : op.getExposedDataSources()) {
            colSize += dataSource.prototype.getColumns().size();
        }
        return colSize;
    }

    @Override
    public void setExposedDataSources() throws Exception {

        dataSources = new ReferedDataSource[1];
        dataSources[0] = new ReferedDataSource();
        dataSources[0].alias = alias;

        dataSources[0].prototype = setPrototype();
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new UnionAllIterator(processedTuples, withFilterDelegation);
    }

    private class UnionAllIterator extends UnpagedOperationIterator {

        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;

        public UnionAllIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            leftTuples = leftOperation.lookUp(processedTuples, false);
            rightTuples = rightOperation.lookUp(processedTuples, false);
        }

        @Override
        protected Tuple findNextTuple() {

            while (leftTuples.hasNext()) {
                Tuple leftTuple = leftTuples.next();

                Tuple returnTp = new Tuple();
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(leftTuple);

                return returnTp;
            }
            while (rightTuples.hasNext()) {
                Tuple rightTuple = rightTuples.next();
                Tuple returnTp = new Tuple();
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(rightTuple);

                return returnTp;
            }

            return null;
        }

    }

    protected LinkedDataRow buildRow(Tuple tuple) {
        List<Column> columns = dataSources[0].prototype.getColumns();
        LinkedDataRow row = new LinkedDataRow(dataSources[0].prototype, false);
        int totalColumns = columns.size();
        int currentIndex = 0;

        outerLoop:
        for (LinkedDataRow row1 : tuple.rows) {
            int fieldSize = row1.getFieldsSize();
            for (int i = 0; i < fieldSize; i++) {
                Comparable value = row1.getValue(i);
                row.setValue(currentIndex, value);
                currentIndex++;
                if (currentIndex == totalColumns) {
                    break outerLoop;
                }
            }
        }

        return row;
    }

}
