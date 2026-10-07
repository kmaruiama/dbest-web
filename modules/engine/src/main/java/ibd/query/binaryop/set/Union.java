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

public class Union extends Set implements SingleSource{

    public Union(Operation leftOperation, Operation rightOperation) throws Exception {
        super(leftOperation, rightOperation);
    }
    
    public Union(Operation leftOperation, Operation rightOperation, String alias) throws Exception {
        this(leftOperation, rightOperation);
        this.alias = alias;
        if (this.alias.isBlank())
            this.alias = "union";
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
    public String toString() {
        return "Union";
    }
    
    @Override
    public void setDataSourceAlias(String sourceAlias) {
        this.alias = sourceAlias;
    }

    @Override
    public String getDataSourceAlias() {
        return alias;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new UnionIterator(processedTuples, withFilterDelegation);
    }

    private class UnionIterator extends UnpagedOperationIterator {

        Tuple leftTuple;

        Tuple rightTuple;
        Iterator<Tuple> leftTuples;
        Iterator<Tuple> rightTuples;

        public UnionIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());

            leftTuple = null;
            leftTuples = leftOperation.lookUp(processedTuples, false);
            rightTuples = rightOperation.lookUp(processedTuples, false);
        }

        @Override
        protected Tuple findNextTuple() {

            if (leftTuple == null) {
                if (leftTuples.hasNext()) {
                    while (leftTuples.hasNext()) {
                        leftTuple = leftTuples.next();
                        break;
                    }
                }
            }

            if (rightTuple == null) {
                while (rightTuples.hasNext()) {
                    rightTuple = rightTuples.next();
                    break;
                }
            }

            if (leftTuple == null && rightTuple == null) {
                return null;
            }

            if (leftTuple != null && rightTuple != null
                    && (leftTuple.compareTo(rightTuple) == 0)) {
                Tuple returnTp = new Tuple();
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(leftTuple);

                while (returnTp.compareTo(leftTuple)==0){
                    if (!leftTuples.hasNext()){
                        leftTuple = null;
                        break;
                    }
                    leftTuple = leftTuples.next();
                }

                while (returnTp.compareTo(rightTuple)==0){
                    if (!rightTuples.hasNext()){
                        rightTuple = null;
                        break;
                    }
                    rightTuple = rightTuples.next();
                }

                return returnTp;

            } else if (leftTuple != null && (rightTuple == null
                    || (leftTuple.compareTo(rightTuple) < 0))) {
                Tuple returnTp = new Tuple();
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(leftTuple);

                while (returnTp.compareTo(leftTuple)==0){
                    if (!leftTuples.hasNext()){
                        leftTuple = null;
                        return returnTp;
                    }
                    leftTuple = leftTuples.next();
                }
                return returnTp;
            } else {
                Tuple returnTp = new Tuple();
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(rightTuple);

                while (returnTp.compareTo(rightTuple) == 0){
                    if (!rightTuples.hasNext()){
                        rightTuple = null;
                        return returnTp;
                    }
                    rightTuple = rightTuples.next();
                }
                return returnTp;

            }
        }


        
        
        protected Tuple findNextTupleOld() {

            if (leftTuple == null) {
                if (leftTuples.hasNext()) {
                    while (leftTuples.hasNext()) {
                        leftTuple = leftTuples.next();
                        break;
                    }
                }
            }

            if (rightTuple == null) {
                while (rightTuples.hasNext()) {
                    rightTuple = rightTuples.next();
                    break;
                }
            }

            if (leftTuple == null && rightTuple == null) {
                return null;
            }

            if (leftTuple != null && rightTuple != null
                    && (leftTuple.compareTo(rightTuple) == 0)) {
                Tuple returnTp = new Tuple();
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(leftTuple);
                leftTuple = null;
                rightTuple = null;
                return returnTp;
            } else if (leftTuple != null && (rightTuple == null
                    || (leftTuple.compareTo(rightTuple) < 0))) {
                Tuple returnTp = new Tuple();
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(leftTuple);
                leftTuple = null;
                return returnTp;
            } else {
                Tuple returnTp = new Tuple();
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = buildRow(leftTuple);
                rightTuple = null;
                return returnTp;
            }
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
