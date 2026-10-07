package ibd.query.unaryop.sort;

import ibd.query.ColumnDescriptor;
import ibd.query.Operation;
import ibd.query.QueryStats;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.unaryop.UnaryOperation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Sort extends UnaryOperation {

    ArrayList<Tuple> tuples;

    List <ColumnDescriptor> sortColumns;
    
    boolean ascending = true;

    
    public Sort(Operation childOperation, String col, boolean ascending) throws Exception {
        super(childOperation);
        this.ascending = ascending;
        sortColumns = new ArrayList();
            ColumnDescriptor sortColumn = new ColumnDescriptor(col);
            sortColumns.add(sortColumn);
        
    }
    
    public Sort(Operation childOperation, String[] columns, boolean ascending) throws Exception {
        super(childOperation);
        this.ascending = ascending;
        sortColumns = new ArrayList();
        for (String col : columns) {
            ColumnDescriptor sortColumn = new ColumnDescriptor(col);
            sortColumns.add(sortColumn);
        }
        
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
                    QueryStats.MEMORY_USED+=tuples.size()*tupleSize;
                    Comparator comparator = createComparator();
                    if (!ascending)
                        comparator = Collections.reverseOrder(comparator);
                    Collections.sort(tuples, comparator);
                    
                    ibd.query.QueryStats.SORT_TUPLES+=tuples.size();

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
            for (ColumnDescriptor sortColumn : sortColumns) {
                Comparable value1 = tt1.rows[sortColumn.getColumnLocation().rowIndex].getValue(sortColumn.getColumnLocation().colIndex);
                Comparable value2 = tt2.rows[sortColumn.getColumnLocation().rowIndex].getValue(sortColumn.getColumnLocation().colIndex);
                int comp = value1.compareTo(value2);
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
