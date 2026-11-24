package ibd.query.unaryop.sort;

import ibd.query.ColumnDescriptor;
import ibd.query.Operation;
import ibd.query.QueryStats;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.unaryop.UnaryOperation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Sort extends UnaryOperation {

    ArrayList<Tuple> tuples;

    List<ColumnDescriptor> sortColumns;

    List<Boolean> ascendingOrders;

    boolean ascending = true;

    public Sort(Operation childOperation, String col, boolean ascending) throws Exception {
        super(childOperation);
        setSortCriteria(new String[]{col}, new boolean[]{ascending});
    }

    public Sort(Operation childOperation, String[] columns, boolean ascending) throws Exception {
        super(childOperation);
        boolean[] ascendingOrders = new boolean[columns.length];
        Arrays.fill(ascendingOrders, ascending);
        setSortCriteria(columns, ascendingOrders);
    }

    public Sort(Operation childOperation, String[] columns, boolean[] ascendingOrders) throws Exception {
        super(childOperation);
        setSortCriteria(columns, ascendingOrders);
    }

    private void setSortCriteria(String[] columns, boolean[] ascendingOrders) throws Exception {
        if (columns == null || ascendingOrders == null || columns.length == 0) {
            throw new Exception("At least one sort column must be provided");
        }

        if (columns.length != ascendingOrders.length) {
            throw new Exception("Sort columns and sort directions must have the same size");
        }

        sortColumns = new ArrayList();
        this.ascendingOrders = new ArrayList<>();
        for (int i = 0; i < columns.length; i++) {
            String col = columns[i];
            ColumnDescriptor sortColumn = new ColumnDescriptor(col);
            sortColumns.add(sortColumn);
            this.ascendingOrders.add(ascendingOrders[i]);
        }

        this.ascending = this.ascendingOrders.get(0);
    }

    @Override
    public void prepare() throws Exception {

        setSortColumnsIndexes();

        tuples = null;

        super.prepare();

    }

    private void setSortColumnsIndexes() throws Exception{
        for (ColumnDescriptor sortColumn : sortColumns) {

            childOperation.setColumnLocation(sortColumn);
        }
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples,  boolean withFilterDelegation) {
        return new SortIterator(processedTuples,  withFilterDelegation);
    }

    public Comparator<Tuple> createComparator() {
        return new TupleComparator();
    }

    private class SortIterator extends UnpagedOperationIterator {

        Iterator<Tuple> it;

        public SortIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
        super(processedTuples, withFilterDelegation, getDelegatedFilters());

            if (tuples == null) {
                tuples = new ArrayList<>();
                try {

                    int tupleSize = childOperation.getTupleSize();
                    it = childOperation.lookUp(processedTuples,  false);
                    while (it.hasNext()) {
                        Tuple tuple = (Tuple) it.next();
                        tuples.add(tuple);

                    }
                    QueryStats.MEMORY_USED.add(tuples.size()*tupleSize);

                    Comparator<Tuple> comparator = createComparator();
                    Collections.sort(tuples, comparator);

                    ibd.query.QueryStats.SORT_TUPLES.add(tuples.size());

                } catch (Exception ex) {
                    Logger.getLogger(Sort.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

            it = tuples.iterator();

        }

        @Override
        protected Tuple findNextTuple() {

            while (it.hasNext()) {
                Tuple tp = it.next();
                return tp;
            }
            return null;
        }

    }

    public class TupleComparator implements Comparator<Tuple> {

        @Override
        public int compare(Tuple tt1, Tuple tt2) {
            for (int i = 0; i < sortColumns.size(); i++) {
                ColumnDescriptor sortColumn = sortColumns.get(i);
                Comparable value1 = tt1.rows[sortColumn.getColumnLocation().rowIndex].getValue(sortColumn.getColumnLocation().colIndex);
                Comparable value2 = tt2.rows[sortColumn.getColumnLocation().rowIndex].getValue(sortColumn.getColumnLocation().colIndex);
                int comp = value1.compareTo(value2);
                if (!ascendingOrders.get(i)) {
                    comp = -comp;
                }
                if (comp!=0) return comp;
            }

            return 0;
        }
    }

    @Override
    public String toString() {
        return "Sort(" + sortColumns+")";
    }

}
