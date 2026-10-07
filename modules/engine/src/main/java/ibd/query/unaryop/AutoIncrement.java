package ibd.query.unaryop;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.ReferedDataSource;
import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.Prototype;
import ibd.table.prototype.column.IntegerColumn;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AutoIncrement extends UnaryOperation {

    String col;
    int increment = 0;
    
    public AutoIncrement(Operation op, String alias, String col, int increment) throws Exception {
        super(op);
        this.alias = alias;
        this.col = col;
        this.increment = increment;
        }



    protected Prototype createPrototype() throws Exception {
        Prototype pt = new Prototype();
        pt.addColumn(new IntegerColumn(col));
        return pt;
        
    }
    
    @Override
    public Map<String, List<String>> getContentInfo() {
        Map<String,List<String>> map = childOperation.getContentInfo();
        map.put(alias,Arrays.asList(col));
        return map;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new AutoIncrementIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public void setConnectedDataSources() throws Exception {
        
        ReferedDataSource s[] = childOperation.getExposedDataSources();
        connectedDataSources = new ReferedDataSource[s.length+1];
        System.arraycopy(s, 0, connectedDataSources, 1, s.length);
   
        connectedDataSources[0] = new ReferedDataSource();
        connectedDataSources[0].alias = alias;
        connectedDataSources[0].prototype = createPrototype();
    }
    
    @Override
    public void setExposedDataSources() throws Exception {
        
        ReferedDataSource s[] = childOperation.getExposedDataSources();
        dataSources = new ReferedDataSource[s.length+1];
        System.arraycopy(s, 0, dataSources, 1, s.length);
   
        dataSources[0] = new ReferedDataSource();
        dataSources[0].alias = alias;
        dataSources[0].prototype = createPrototype();
    }

    @Override
    public String toString() {
        return "Auto Increment(" + col+")";
    }

    private class AutoIncrementIterator extends UnpagedOperationIterator {

        Iterator<Tuple> tuples;
        
        int autoInc = 1;

        
        public AutoIncrementIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            autoInc = increment;
            tuples = childOperation.lookUp(processedTuples, false);
        }

        @Override
        protected Tuple findNextTuple() {
            while (tuples.hasNext()) {
                Tuple tp = tuples.next();
                
                LinkedDataRow row = new LinkedDataRow(dataSources[0].prototype, false);
                row.setValue(0, autoInc);
                autoInc++;
                
                LinkedDataRow rows[] = new LinkedDataRow[tp.rows.length+1];
                System.arraycopy(tp.rows, 0, rows, 1, tp.rows.length);
                rows[0] = row;
                Tuple returnTp = new Tuple();
                returnTp.setSourceRows(rows);
                
                return returnTp;

            }
            return null;
        }


    }
}
