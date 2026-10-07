package ibd.query.unaryop;

import ibd.query.ColumnDescriptor;
import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;
import java.util.Iterator;
import java.util.List;

public class Explode extends UnaryOperation {

    ColumnDescriptor columnToExplode;
    String delimiter;
    int colIndex;
    int rowIndex;

    public Explode(Operation op, String columnToExplode, String delimiter) throws Exception {
        super(op);
        this.columnToExplode = new ColumnDescriptor(columnToExplode);
        this.delimiter = delimiter;
    }

    @Override
    public void prepare() throws Exception {

        super.prepare();

        setColumnIndexes();
    }

    private void setColumnIndexes() throws Exception {
        childOperation.setColumnLocation(columnToExplode);
        colIndex = columnToExplode.getColumnLocation().colIndex;
        rowIndex = columnToExplode.getColumnLocation().rowIndex;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new ExplodeColumnIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public String toString() {
        return "Explode(" + columnToExplode + ")";
    }

    private class ExplodeColumnIterator extends UnpagedOperationIterator {

        Iterator<Tuple> tuples;

        LinkedDataRow currentRow;
        Tuple currentTuple;
        String[] explodedValues;
        int explodedValuesIndex = -1;

        public ExplodeColumnIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            tuples = childOperation.lookUp(processedTuples, false);
        }

        private Tuple createNewTuple() {
            LinkedDataRow newRow = new LinkedDataRow(currentRow);
            newRow.setValue(colIndex, explodedValues[explodedValuesIndex].trim());
            explodedValuesIndex++;
            
            Tuple newTuple = new Tuple();
            newTuple.setSourceRows(currentTuple);
            newTuple.rows[rowIndex] = newRow;
            return newTuple;
        }

        @Override
        protected Tuple findNextTuple() {

            while (tuples.hasNext() || (explodedValuesIndex > -1 && explodedValuesIndex < explodedValues.length)) {
                
                if (explodedValuesIndex > -1 && explodedValuesIndex < explodedValues.length) {
                    Tuple newTuple = createNewTuple();
                    return newTuple;
                }

                currentTuple = tuples.next();
                
                currentRow = currentTuple.rows[rowIndex];
                String value = currentRow.getValue(colIndex).toString();
                explodedValues = value.split(delimiter);
                if (explodedValues.length == 0) {
                    explodedValues = new String[]{""};
                }
                explodedValuesIndex = 0;
                Tuple newTuple = createNewTuple();
                return newTuple;
            }

            return null;
        }

    }
}
