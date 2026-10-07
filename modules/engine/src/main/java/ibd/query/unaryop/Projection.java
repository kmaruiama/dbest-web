package ibd.query.unaryop;

import ibd.query.ColumnDescriptor;
import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.ReferedDataSource;
import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.Prototype;
import ibd.table.prototype.column.Column;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Projection extends UnaryOperation {

    List<ColumnDescriptor> projectionColumns;
    TreeMap<Integer, List<ColumnDescriptor>> columnsByRow = new TreeMap();

    public Projection(Operation op,  String[] columns) throws Exception {
        super(op);
        projectionColumns = new ArrayList();
        for (String col : columns) {
            ColumnDescriptor sortColumn = new ColumnDescriptor(col);
            projectionColumns.add(sortColumn);
        }

    }

    @Override
    public void prepare() throws Exception {
        
        super.prepare();
        
        setColumnsIndexes();
    }

    private void setColumnsIndexes() throws Exception {
        for (ColumnDescriptor col : projectionColumns) {
            childOperation.setColumnLocation(col);
        }
    }

    protected void setConnectedPrototypes() throws Exception {
        TreeMap<Integer, Prototype> prototypes = new TreeMap();
        TreeMap<Integer, String> aliases = new TreeMap();
        TreeMap<Integer, List<ColumnDescriptor>> columnsByRow_ = new TreeMap();
        for (ColumnDescriptor col : projectionColumns) {
            childOperation.setColumnLocation(col);
            int rowIndex = col.getColumnLocation().rowIndex;
            Column originalCol = childOperation.getExposedDataSources()[rowIndex].prototype.getColumn(col.getColumnName());
            Column newCol = Prototype.cloneColumn(originalCol);
            Prototype prototype = prototypes.get(rowIndex);
            List<ColumnDescriptor> rowCols = columnsByRow_.get(rowIndex);
            if (prototype==null){
                prototype = new Prototype();
                prototypes.put(rowIndex, prototype);
                aliases.put(rowIndex, childOperation.getExposedDataSources()[rowIndex].alias);
                rowCols = new ArrayList();
                columnsByRow_.put(rowIndex, rowCols);
            }
            prototype.addColumn(newCol);
            rowCols.add(col);
        }
        connectedDataSources = new ReferedDataSource[prototypes.size()];
        
        int x = 0;
        for (Map.Entry<Integer, Prototype> entry : prototypes.entrySet()) {
            connectedDataSources[x] = new ReferedDataSource();
            connectedDataSources[x].prototype = entry.getValue();
            String alias = aliases.get(entry.getKey());
            connectedDataSources[x].alias = alias;
            List<ColumnDescriptor> cols = columnsByRow_.get(entry.getKey());
            columnsByRow.put(x, cols);
            x++;
        }

    }
    
    protected void setExposedPrototypes() throws Exception {
        TreeMap<Integer, Prototype> prototypes = new TreeMap();
        TreeMap<Integer, String> aliases = new TreeMap();
        TreeMap<Integer, List<ColumnDescriptor>> columnsByRow_ = new TreeMap();
        for (ColumnDescriptor col : projectionColumns) {
            childOperation.setColumnLocation(col);
            int rowIndex = col.getColumnLocation().rowIndex;
            Column originalCol = childOperation.getExposedDataSources()[rowIndex].prototype.getColumn(col.getColumnName());
            Column newCol = Prototype.cloneColumn(originalCol);
            Prototype prototype = prototypes.get(rowIndex);
            List<ColumnDescriptor> rowCols = columnsByRow_.get(rowIndex);
            if (prototype==null){
                prototype = new Prototype();
                prototypes.put(rowIndex, prototype);
                aliases.put(rowIndex, childOperation.getExposedDataSources()[rowIndex].alias);
                rowCols = new ArrayList();
                columnsByRow_.put(rowIndex, rowCols);
            }
            prototype.addColumn(newCol);
            rowCols.add(col);
        }
        dataSources = new ReferedDataSource[prototypes.size()];
        
        int x = 0;
        for (Map.Entry<Integer, Prototype> entry : prototypes.entrySet()) {
            dataSources[x] = new ReferedDataSource();
            dataSources[x].prototype = entry.getValue();
            String alias = aliases.get(entry.getKey());
            dataSources[x].alias = alias;
            List<ColumnDescriptor> cols = columnsByRow_.get(entry.getKey());
            columnsByRow.put(x, cols);
            x++;
        }

    }
    
    @Override
    public Map<String, List<String>> getContentInfo() {
        HashMap<String,List<String>> map = new LinkedHashMap<>();
        for (ReferedDataSource dataSource : dataSources) {
            List list = new ArrayList<String>();
            for (Column column : dataSource.prototype.getColumns()) {
                list.add(column.getName());
            }
            map.put(dataSource.alias,list);
        }
        
        return map;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new ProjectionIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public void setConnectedDataSources() throws Exception {

        setConnectedPrototypes();
    }
    
    @Override
    public void setExposedDataSources() throws Exception {

        setExposedPrototypes();
    }

    @Override
    public String toString() {
        return "projection(" + projectionColumns+")";
    }

    private class ProjectionIterator extends UnpagedOperationIterator {

        Iterator<Tuple> tuples;

        
        public ProjectionIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            tuples = childOperation.lookUp(processedTuples, false);
        }

        @Override
        protected Tuple findNextTuple() {
            while (tuples.hasNext()) {
                Tuple tp = tuples.next();
                
                Tuple returnTp = new Tuple();
                returnTp.rows = new LinkedDataRow[dataSources.length];
                
                int x = 0;
                for (List<ColumnDescriptor> cols : columnsByRow.values()) {
                    LinkedDataRow row = new LinkedDataRow(dataSources[x].prototype, false);
                    returnTp.rows[x] = row;
                    int i = 0;
                    for (ColumnDescriptor col : cols) {
                        Comparable value = tp.rows[col.getColumnLocation().rowIndex].getValue(col.getColumnLocation().colIndex);
                        row.setValue(i, value);
                        i++;
                    }
                    x++;
                }
                
                
                
                return returnTp;

            }
            return null;
        }


    }
}
