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

public class SourceRename1 extends UnaryOperation {

    List<ColumnDescriptor> columns;

    public SourceRename1(Operation op, String alias) throws Exception {
        super(op);
        this.alias = alias;

    }

    private void setColumns() throws Exception {
        ReferedDataSource childSources[] = getChildOperation().getExposedDataSources();
        columns = new ArrayList();
        for (ReferedDataSource dataSource : childSources) {
            for (Column col : dataSource.prototype.getColumns()) {
                ColumnDescriptor colDesc = new ColumnDescriptor(dataSource.alias + "." + col.getName());
                columns.add(colDesc);
            }

        }

    }

    @Override
    public void prepare() throws Exception {

        super.prepare();

        setColumnsIndexes();
    }

    private void setColumnsIndexes() throws Exception {
        for (ColumnDescriptor col : columns) {
            childOperation.setColumnLocation(col);
        }
    }

    protected Prototype setPrototype() throws Exception {
        Prototype prototype = new Prototype();
        for (ColumnDescriptor col : columns) {
            childOperation.setColumnLocation(col);
            Column originalCol = childOperation.getExposedDataSources()[col.getColumnLocation().rowIndex].prototype.getColumn(col.getColumnName());
            Column newCol = Prototype.cloneColumn(originalCol);
            prototype.addColumn(newCol);
        }
        return prototype;
        
    }

    @Override
    public Map<String, List<String>> getContentInfo() {
        HashMap<String, List<String>> map = new LinkedHashMap<>();

        try {
            if (columns == null || columns.size() == 0) {
                setConnectedDataSources();
                setColumns();
            }
        } catch (Exception ex) {
        }
        List list = new ArrayList<String>();
        for (ColumnDescriptor colDescriptor : columns) {
            list.add(colDescriptor.getColumnName());
        }
        map.put(alias, list);
        return map;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new SourceRenameIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public void setConnectedDataSources() throws Exception {
        
        connectedDataSources = new ReferedDataSource[1];
        connectedDataSources[0] = new ReferedDataSource();
        connectedDataSources[0].alias = alias;

        setColumns();
        connectedDataSources[0].prototype = setPrototype();
    }
    
    @Override
    public void setExposedDataSources() throws Exception {

        dataSources = connectedDataSources;

    }

    @Override
    public String toString() {
        return "Source Rename:" + columns;
    }

    private class SourceRenameIterator extends UnpagedOperationIterator {

        Iterator<Tuple> tuples;

        public SourceRenameIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            tuples = childOperation.lookUp(processedTuples, false);
        }

        @Override
        protected Tuple findNextTuple() {
            while (tuples.hasNext()) {
                Tuple tp = tuples.next();
                LinkedDataRow row = new LinkedDataRow(dataSources[0].prototype, false);
                for (int i = 0; i < columns.size(); i++) {
                    ColumnDescriptor col = columns.get(i);
                    Comparable value = tp.rows[col.getColumnLocation().rowIndex].getValue(col.getColumnLocation().colIndex);
                    row.setValue(i, value);
                }

                Tuple returnTp = new Tuple();
                returnTp.rows = new LinkedDataRow[1];
                returnTp.rows[0] = new LinkedDataRow();
                returnTp.rows[0] = row;

                return returnTp;

            }
            return null;
        }

    }
}
