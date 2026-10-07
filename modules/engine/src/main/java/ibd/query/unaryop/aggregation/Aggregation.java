package ibd.query.unaryop.aggregation;

import ibd.query.ColumnDescriptor;
import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.ReferedDataSource;
import ibd.query.SingleSource;
import ibd.query.Tuple;
import ibd.query.unaryop.UnaryOperation;
import ibd.query.unaryop.sort.Sort;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import ibd.table.prototype.Prototype;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.column.Column;
import ibd.table.prototype.column.DoubleColumn;
import ibd.table.prototype.column.FloatColumn;
import ibd.table.prototype.column.IntegerColumn;
import ibd.table.prototype.column.LongColumn;
import ibd.table.prototype.column.StringColumn;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Aggregation extends UnaryOperation implements SingleSource{

    ColumnDescriptor groupByColumn;
    List<AggregationType> aggregationTypes;

    Prototype prototype = null;

    public Aggregation(Operation op, String alias, String groupByCol, String aggregateCol, int type, boolean isOrdered) throws Exception {
        super(op);
        this.alias = alias;
        if (this.alias.isBlank())
            this.alias = "aggregate";
        groupByColumn = new ColumnDescriptor(groupByCol);
        aggregationTypes = new ArrayList();
        AggregationType aggType = new AggregationType(aggregateCol, type);
        aggregationTypes.add(aggType);

        if (!isOrdered) {
            Sort cs = new Sort(childOperation, new String[]{groupByColumn.getColumnName()}, true);
            childOperation = cs;
        }
    }

    public Aggregation(Operation op, String alias, String groupByTable, String groupByCol, String groupedTable, String groupedCol, int type, boolean isOrdered) throws Exception {
        super(op);
        this.alias = alias;
        if (this.alias.isBlank())
            this.alias = "aggregate";
        groupByColumn = new ColumnDescriptor(groupByTable, groupByCol);

        aggregationTypes = new ArrayList();
        AggregationType aggType = new AggregationType(groupedTable, groupedCol, type);
        aggregationTypes.add(aggType);

        if (!isOrdered) {
            Sort cs = new Sort(childOperation, new String[]{groupByColumn.getColumnName()}, true);
            childOperation = cs;
        }
    }

    public Aggregation(Operation op, String alias, String groupByTable, String groupByCol, List<AggregationType> aggregationOperations, boolean isOrdered) throws Exception {
        super(op);
        this.alias = alias;
        if (this.alias.isBlank())
            this.alias = "aggregate";
        groupByColumn = new ColumnDescriptor(groupByTable, groupByCol);
        this.aggregationTypes = aggregationOperations;

        if (!isOrdered) {
            Sort cs = new Sort(childOperation, new String[]{groupByColumn.getColumnName()}, true);
            childOperation = cs;
        }

    }

    public Aggregation(Operation op, String alias, String groupByCol, List<AggregationType> aggregationOperations, boolean isOrdered) throws Exception {
        super(op);
        this.alias = alias;
        if (this.alias.isBlank())
            this.alias = "aggregate";
        groupByColumn = new ColumnDescriptor(groupByCol);
        this.aggregationTypes = aggregationOperations;

        if (!isOrdered) {
            Sort cs = new Sort(childOperation, new String[]{groupByColumn.getColumnName()}, true);
            childOperation = cs;
        }

    }

    private Column createColumn(ColumnDescriptor colDesc, String colName) throws Exception {
        if (colName==null)
            colName = colDesc.getColumnName();
        
        ReferedDataSource dataSource = childOperation.getExposedDataSource(colDesc.getTableName());
        
        if (dataSource==null){
            return new StringColumn(colName);
        }
        Column col = dataSource.prototype.getColumn(colDesc.getColumnName());

        return Column.copyColumn(col, colName);
    }
    
    private void createPrototype() throws Exception {

        prototype = new Prototype();
        prototype.addColumn(createColumn(groupByColumn, null));
        for (AggregationType aggregationType : aggregationTypes) {
            if (aggregationType.type == AggregationType.AVG) {
                prototype.addColumn(new DoubleColumn(aggregationType.aggregateColumnName));
            } else if (aggregationType.type != AggregationType.FIRST && aggregationType.type != AggregationType.LAST){
                prototype.addColumn(new IntegerColumn(aggregationType.aggregateColumnName));
            }
            else prototype.addColumn(createColumn(aggregationType.aggregateColumn,aggregationType.aggregateColumnName));
        }

        prototype.validateColumns();
    }
    
    @Override
    public void prepare() throws Exception {

        super.prepare();

        childOperation.setColumnLocation(groupByColumn);
        for (AggregationType aggregationType : aggregationTypes) {
            childOperation.setColumnLocation(aggregationType.aggregateColumn);
        }

    }

    @Override
    public void setConnectedDataSources() throws Exception {
        
        connectedDataSources = new ReferedDataSource[1];
        connectedDataSources[0] = new ReferedDataSource();
        connectedDataSources[0].alias = alias;

        createPrototype();
        connectedDataSources[0].prototype = prototype;
    }

    @Override
    public void setExposedDataSources() throws Exception {

        dataSources = connectedDataSources;

    }
    
    @Override
    public Map<String, List<String>> getContentInfo() {
        HashMap<String, List<String>> map = new LinkedHashMap<>();

        List<String> list = new ArrayList<>();
        list.add(groupByColumn.getColumnName());
        for (AggregationType aggregationType : aggregationTypes) {
            list.add(aggregationType.aggregateColumnName);
        }
        map.put(alias, list);
        return map;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new AggregationIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public String toString() {
        String aggregation = "";
        for (AggregationType aggregationType : aggregationTypes) {
            aggregation += ",";
            aggregation += aggregationType.toString();
        }
        return "Group by(" + groupByColumn.toString() + ")" + aggregation;
    }

    @Override
    public void setDataSourceAlias(String sourceAlias) {
        this.alias = sourceAlias;
    }

    @Override
    public String getDataSourceAlias() {
        return alias;
    }

    private class AggregationIterator extends UnpagedOperationIterator {

        Iterator<Tuple> tuples;
        Comparable prevGroupByValue;
        List<Comparable> groupedValues[];
        int countNulls[];
        int countAll;

        public AggregationIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            groupedValues = new List[aggregationTypes.size()];
            countNulls = new int[aggregationTypes.size()];
            countAll = 0;
            
            for (int i = 0; i < groupedValues.length; i++) {
                groupedValues[i] = new ArrayList();
                countNulls[i] = 0;
            }
            tuples = childOperation.lookUp(processedTuples, false);
        }

        private Comparable getValue(Tuple tp, ColumnDescriptor col) {
            return tp.rows[col.getColumnLocation().rowIndex].getValue(col.getColumnName());
        }

        @Override
        protected Tuple findNextTuple() {
            while (tuples.hasNext()) {
                Tuple tp = tuples.next();
                Comparable groupByValue = getValue(tp, groupByColumn);

                if (prevGroupByValue == null) {
                    prevGroupByValue = groupByValue;
                    for (int i = 0; i < aggregationTypes.size(); i++) {
                        AggregationType aggregationType = aggregationTypes.get(i);
                        Comparable aggregatedValue = getValue(tp, aggregationType.aggregateColumn);
                        if (aggregatedValue!=null)
                            groupedValues[i].add(aggregatedValue);
                        else {
                            countNulls[i]++;
                        }
                    }
                    countAll++;
                } else if ((prevGroupByValue.equals(groupByValue))) {
                    for (int i = 0; i < aggregationTypes.size(); i++) {
                        AggregationType aggregationType = aggregationTypes.get(i);
                        Comparable aggregatedValue = getValue(tp, aggregationType.aggregateColumn);
                        if (aggregatedValue!=null)
                            groupedValues[i].add(aggregatedValue);
                        else {
                            countNulls[i]++;
                        }
                    }
                    countAll++;
                } else
                {
                    Tuple tuple = new Tuple();
                    LinkedDataRow dataRow = new LinkedDataRow(prototype, false);
                    dataRow.setValue(0, prevGroupByValue);
                    aggregate(dataRow, aggregationTypes, groupedValues, countNulls, countAll);
                    tuple.setSingleSourceRow(alias, dataRow);

                    prevGroupByValue = groupByValue;

                    for (int i = 0; i < aggregationTypes.size(); i++) {
                        AggregationType aggregationType = aggregationTypes.get(i);
                        Comparable aggregatedValue = getValue(tp, aggregationType.aggregateColumn);
                        groupedValues[i].clear();
                        countNulls[i] = 0;
                        if (aggregatedValue!=null)
                            groupedValues[i].add(aggregatedValue);
                        else countNulls[i]++;
                    }
                    countAll = 1;
                    
                    return tuple;
                }

            }
            if (prevGroupByValue != null) {
                Tuple tuple = new Tuple();
                LinkedDataRow dataRow = new LinkedDataRow(prototype, false);
                dataRow.setValue(0, prevGroupByValue);
                aggregate(dataRow, aggregationTypes, groupedValues, countNulls, countAll);
                tuple.setSingleSourceRow(alias, dataRow);
                prevGroupByValue = null;
                return tuple;
            }

            return null;
        }
        
        
        private void aggregate(LinkedDataRow row, List<AggregationType> aggregationTypes, List<Comparable> list[], int[] countNulls, int countAll) {
            for (int i = 0; i < list.length; i++) {

                switch (aggregationTypes.get(i).type) {
                    case AggregationType.AVG -> {
                        row.setValue(i + 1, avg(list[i]));
                    }
                    case AggregationType.MAX -> {
                        row.setValue(i + 1, max(list[i]));
                    }
                    case AggregationType.MIN -> {
                        row.setValue(i + 1, min(list[i]));
                    }
                    case AggregationType.COUNT -> {
                        row.setValue(i + 1, count(list[i]));
                    }
                    case AggregationType.SUM -> {
                        row.setValue(i + 1, sum(list[i]));
                    }
                    case AggregationType.FIRST -> {
                        row.setValue(i + 1, first(list[i]));
                    }
                    case AggregationType.LAST -> {
                        row.setValue(i + 1, last(list[i]));
                    }
                    case AggregationType.COUNT_ALL -> {
                        row.setValue(i + 1, countAll);
                    }
                    case AggregationType.COUNT_NULL -> {
                        row.setValue(i + 1, countNulls[i]);
                    }
                }
            }

        }

        private Double avg(List<Comparable> list) {

            if (list.isEmpty()) {
                return 0.;
            }
            int sum = 0;
            for (Comparable integer : list) {
                sum += (Integer)integer;
            }
            return Double.valueOf(sum / list.size());
        }

        private Integer sum(List<Comparable> list) {

            int sum = 0;
            for (Comparable integer : list) {
                sum += (Integer)integer;
            }
            return sum;
        }

        private Integer min(List<Comparable> list) {

            if (list.isEmpty()) {
                return 0;
            }
            int min = Integer.MAX_VALUE;
            for (Comparable integer : list) {
                if ((Integer)integer < min) {
                    min = (Integer)integer;
                }
            }
            return min;
        }

        private Integer max(List<Comparable> list) {

            if (list.isEmpty()) {
                return 0;
            }
            int max = Integer.MIN_VALUE;
            for (Comparable integer : list) {
                if ((Integer)integer > max) {
                    max = (Integer)integer;
                }
            }
            return max;
        }

        private Integer count(List<Comparable> list) {
            return list.size();
        }


        private Comparable first(List<Comparable> list) {

            if (list.isEmpty()) {
                return 0;
            }
            return list.get(0);
        }

        private Comparable last(List<Comparable> list) {

            if (list.isEmpty()) {
                return 0;
            }
            return list.get(list.size() - 1);
        }

    }

}
