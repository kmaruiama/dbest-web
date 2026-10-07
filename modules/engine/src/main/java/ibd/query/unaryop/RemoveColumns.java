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

public class RemoveColumns extends UnaryOperation {

    String alias;
    List<ColumnDescriptor> columnsToProject;
    List<ColumnDescriptor> columnsToRemove;

    public RemoveColumns(Operation op, String alias, String[] columnsToRemove_) throws Exception {
        super(op);
        this.alias = alias;
        columnsToRemove = new ArrayList();
        for (String col : columnsToRemove_) {
            ColumnDescriptor colToRemove = new ColumnDescriptor(col);
            columnsToRemove.add(colToRemove);
        }

    }

    public RemoveColumns(Operation op, String alias, List<String> columnsToRemove_) throws Exception {
        super(op);
        this.alias = alias;
        columnsToRemove = new ArrayList();
        for (String col : columnsToRemove_) {
            ColumnDescriptor colToRemove = new ColumnDescriptor(col);
            columnsToRemove.add(colToRemove);
        }

    }

    @Override
    public void prepare() throws Exception {

        super.prepare();

        setColumnsIndexes();
    }

    private void setColumnsIndexes() throws Exception {
        for (ColumnDescriptor col : columnsToProject) {
            childOperation.setColumnLocation(col);
        }
    }

    protected Prototype setPrototype() throws Exception {
        Prototype prototype = new Prototype();
        for (ColumnDescriptor col : columnsToProject) {
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
        if (columnsToProject == null || columnsToProject.isEmpty()) {
            try {
                setConnectedDataSources();
                setProjectedColumns();
            } catch (Exception ex) {
            }

        }
        List list = new ArrayList<String>();
        for (ColumnDescriptor colDescriptor : columnsToProject) {
            list.add(colDescriptor.getColumnName());
        }
        map.put(alias, list);
        return map;
    }

    private void setProjectedColumns() throws Exception {
        ReferedDataSource childSources[] = getChildOperation().getExposedDataSources();
        columnsToProject = new ArrayList();
        for (ReferedDataSource dataSource : childSources) {

            for (Column col : dataSource.prototype.getColumns()) {
                boolean toRemove = false;
                for (ColumnDescriptor colToRemove : columnsToRemove) {
                    if (colToRemove.getTableName().equals(dataSource.alias)) {
                        if (colToRemove.getColumnName().equals(col.getName())) {
                            toRemove = true;
                        }
                    }
                }
                if (!toRemove) {
                    ColumnDescriptor colDesc = new ColumnDescriptor(dataSource.alias + "." + col.getName());
                    columnsToProject.add(colDesc);
                }
            }
        }

    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new RemoveColumnsIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public void setConnectedDataSources() throws Exception {
        
        connectedDataSources = new ReferedDataSource[1];
        connectedDataSources[0] = new ReferedDataSource();
        connectedDataSources[0].alias = alias;

        setProjectedColumns();
        connectedDataSources[0].prototype = setPrototype();
    }
    
    @Override
    public void setExposedDataSources() throws Exception {
        
        dataSources = connectedDataSources;
    }

    @Override
    public String toString() {
        return "Remove Columns:" + columnsToRemove;
    }

    private class RemoveColumnsIterator extends UnpagedOperationIterator {

        Iterator<Tuple> tuples;

        public RemoveColumnsIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            tuples = childOperation.lookUp(processedTuples, false);
        }

        @Override
        protected Tuple findNextTuple() {
            while (tuples.hasNext()) {
                Tuple tp = tuples.next();

                LinkedDataRow row = new LinkedDataRow(dataSources[0].prototype, false);
                for (int i = 0; i < columnsToProject.size(); i++) {
                    ColumnDescriptor col = columnsToProject.get(i);
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
