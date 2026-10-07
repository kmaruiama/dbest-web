package ibd.query.binaryop.conditional;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.ReferedDataSource;
import ibd.query.SingleSource;
import ibd.query.Tuple;
import ibd.query.binaryop.BinaryOperation;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.Prototype;
import ibd.table.prototype.column.BooleanColumn;
import ibd.table.prototype.column.Column;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LogicalXor extends BinaryOperation implements SingleSource {

    String colName = "EVAL";
    boolean leftSideIsCondition = false;
    boolean rightSideIsCondition = false;
    Tuple fixedTrueTuple;
    Tuple fixedFalseTuple;

    public LogicalXor(Operation leftOperation, Operation rightOperation) throws Exception {
        super(leftOperation, rightOperation);
        alias = "condition";
    }

    public LogicalXor(Operation leftOperation, Operation rightOperation, String alias) throws Exception {
        this(leftOperation, rightOperation);
        if (!alias.isBlank()) {
            this.alias = alias;
        }
    }

    protected Prototype setPrototype() throws Exception {
        Prototype prototype = new Prototype();
        prototype.addColumn(new BooleanColumn(colName));

        fixedTrueTuple = new Tuple();
        LinkedDataRow row = new LinkedDataRow(prototype, false);
        row.setValue(0, true);
        fixedTrueTuple.setSourceRows(new LinkedDataRow[]{row});

        fixedFalseTuple = new Tuple();
        row = new LinkedDataRow(prototype, false);
        row.setValue(0, false);
        fixedFalseTuple.setSourceRows(new LinkedDataRow[]{row});
        
        return prototype;
    }

    @Override
    public Map<String, List<String>> getContentInfo() {
        HashMap<String, List<String>> map = new LinkedHashMap<>();
        List list = new ArrayList<String>();
        list.add(colName);
        map.put(alias, list);
        return map;
    }

    private boolean isBooleanCondition(Operation op) throws Exception {
        if (op.getExposedDataSources().length > 1) {
            return false;
        }

        ReferedDataSource source = op.getExposedDataSources()[0];
        if (source.prototype.size() > 1) {
            return false;
        }

        Column col = source.prototype.getColumn(0);
        if (col.isBoolean()) {
            return true;
        }

        return false;

    }

    @Override
    public void setExposedDataSources() throws Exception {

        dataSources = new ReferedDataSource[1];
        dataSources[0] = new ReferedDataSource();
        dataSources[0].alias = alias;

        dataSources[0].prototype = setPrototype();

        leftSideIsCondition = isBooleanCondition(getLeftOperation());
        rightSideIsCondition = isBooleanCondition(getRightOperation());
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
        return new LogicalOrIterator(processedTuples, withFilterDelegation);
    }

    private class LogicalOrIterator extends UnpagedOperationIterator {

        boolean finished = false;

        public LogicalOrIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
        }

        @Override
        protected Tuple findNextTuple() {

            if (finished) {
                return null;
            }
            finished = true;

            boolean leftSatisfied = true;
            Iterator<Tuple> tuples = leftOperation.lookUp(processedTuples, true);
            if (tuples.hasNext()) {
                Tuple tuple = tuples.next();
                if (!leftSideIsCondition) {
                    leftSatisfied = true;
                } else {
                    leftSatisfied = ((Boolean) tuple.rows[0].getValue(0));
                }
            }

            boolean rightSatisfied = true;
            tuples = rightOperation.lookUp(processedTuples, true);
            if (tuples.hasNext()) {
                Tuple tuple = tuples.next();
                if (!rightSideIsCondition) {
                    rightSatisfied = true;
                } else {
                    rightSatisfied = ((Boolean) tuple.rows[0].getValue(0));
                }
            }
            if (leftSatisfied ^ rightSatisfied) {
                return fixedTrueTuple;
            } else {
                return fixedFalseTuple;
            }
        }

    }

}
