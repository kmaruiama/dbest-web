package ibd.query.unaryop;

import ibd.query.ColumnDescriptor;
import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.Tuple;
import ibd.query.UnpagedOperationIterator;
import ibd.query.binaryop.join.Join;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.Prototype;
import ibd.table.prototype.column.Column;
import ibd.table.prototype.column.StringColumn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Reference extends Operation {

    private final List<ColumnDescriptor> columns = new ArrayList<>();

    public Reference(String[] columns) throws Exception {
        this.alias = "ref";
        for (String column : columns) {
            this.columns.add(new ColumnDescriptor(column));
        }
    }

    @Override
    public void prepare() throws Exception {
        super.prepare();
        for (ColumnDescriptor column : columns) {
            if (!setColumnLocationFromProcessedOperations(column)) {
                throw new Exception("Error in operation " + this + ".\nColumn " + column + " not found.");
            }
        }
    }

    @Override
    public void setConnectedDataSources() throws Exception {
        List<Operation> providers = providers();
        Prototype prototype = new Prototype();
        for (ColumnDescriptor column : columns) {
            String name = column.getTableName() + "_" + column.getColumnName();
            Column referenced = referencedColumn(providers, column);
            prototype.addColumn(referenced == null ? new StringColumn(name) : Column.copyColumn(referenced, name));
        }
        connectedDataSources = new ReferedDataSource[1];
        connectedDataSources[0] = new ReferedDataSource();
        connectedDataSources[0].alias = alias;
        connectedDataSources[0].prototype = prototype;
    }

    private List<Operation> providers() {
        List<Operation> providers = new ArrayList<>();
        Operation child = this;
        Operation parent = getParentOperation();
        while (parent != null) {
            if (parent instanceof Join join && join.useLeftSideLookups() && join.getRightOperation() == child) {
                providers.add(join.getLeftOperation());
            }
            child = parent;
            parent = parent.getParentOperation();
        }
        Collections.reverse(providers);
        return providers;
    }

    private static Column referencedColumn(List<Operation> providers, ColumnDescriptor column) throws Exception {
        for (Operation provider : providers) {
            for (ReferedDataSource source : provider.getExposedDataSources()) {
                if (source.alias.equals(column.getTableName())) {
                    return source.prototype.getColumn(column.getColumnName());
                }
            }
        }
        return null;
    }

    @Override
    public Map<String, List<String>> getContentInfo() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (ReferedDataSource source : dataSources) {
            List<String> names = new ArrayList<>();
            for (Column column : source.prototype.getColumns()) {
                names.add(column.getName());
            }
            map.put(source.alias, names);
        }
        return map;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new ReferenceIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public String toString() {
        return "Reference";
    }

    private class ReferenceIterator extends UnpagedOperationIterator {

        private Tuple tuple;

        ReferenceIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            if (processedTuples.isEmpty()) {
                return;
            }
            LinkedDataRow row = new LinkedDataRow(dataSources[0].prototype, false);
            for (int i = 0; i < columns.size(); i++) {
                ColumnDescriptor column = columns.get(i);
                Tuple processed = processedTuples.get(column.getColumnLocation().tupleIndex);
                row.setValue(i, processed.rows[column.getColumnLocation().rowIndex].getValue(column.getColumnLocation().colIndex));
            }
            tuple = new Tuple();
            tuple.setSourceRows(new LinkedDataRow[]{row});
        }

        @Override
        protected Tuple findNextTuple() {
            Tuple next = tuple;
            tuple = null;
            return next;
        }
    }
}
