package ibd.query.unaryop.filter;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.Tuple;
import ibd.query.UnpagedOperationIterator;
import ibd.query.lookup.CompositeLookupFilter;
import ibd.query.lookup.Element;
import ibd.query.lookup.LookupFilter;
import ibd.query.lookup.ReferencedElement;
import ibd.query.lookup.SingleColumnLookupFilter;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.Prototype;
import ibd.table.prototype.column.BooleanColumn;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Condition extends Operation {

    private static final String COLUMN = "EVAL";

    private final LookupFilter filter;
    private Tuple trueTuple;
    private Tuple falseTuple;

    public Condition(LookupFilter filter) throws Exception {
        this.filter = filter;
        this.alias = "condition";
    }

    @Override
    public LookupFilter getFilters() {
        return filter;
    }

    @Override
    public void prepare() throws Exception {
        super.prepare();
        trueTuple = fixedTuple(true);
        falseTuple = fixedTuple(false);
    }

    private Tuple fixedTuple(boolean value) {
        LinkedDataRow row = new LinkedDataRow(dataSources[0].prototype, false);
        row.setValue(0, value);
        Tuple tuple = new Tuple();
        tuple.setSourceRows(new LinkedDataRow[]{row});
        return tuple;
    }

    @Override
    public void setConnectedDataSources() throws Exception {
        Prototype prototype = new Prototype();
        prototype.addColumn(new BooleanColumn(COLUMN));
        connectedDataSources = new ReferedDataSource[1];
        connectedDataSources[0] = new ReferedDataSource();
        connectedDataSources[0].alias = alias;
        connectedDataSources[0].prototype = prototype;
    }

    @Override
    public Map<String, List<String>> getContentInfo() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> columns = new ArrayList<>();
        columns.add(COLUMN);
        map.put(alias, columns);
        return map;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new ConditionIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public boolean exists(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return evaluate(processedTuples);
    }

    private boolean evaluate(List<Tuple> processedTuples) {
        bindReferences(filter, processedTuples);
        return filter.match((Tuple) null);
    }

    private static void bindReferences(LookupFilter filter, List<Tuple> processedTuples) {
        if (filter instanceof CompositeLookupFilter composite) {
            for (LookupFilter inner : composite.getFilters()) {
                bindReferences(inner, processedTuples);
            }
        } else if (filter instanceof SingleColumnLookupFilter single) {
            bindReference(single.getFirstElement(), processedTuples);
            bindReference(single.getSecondElement(), processedTuples);
        }
    }

    private static void bindReference(Element element, List<Tuple> processedTuples) {
        if (element instanceof ReferencedElement referenced) {
            referenced.setValue(processedTuples);
        }
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public String toString() {
        return "Condition(" + filter + ")";
    }

    private class ConditionIterator extends UnpagedOperationIterator {

        private final boolean satisfied;
        private boolean finished = false;

        ConditionIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            satisfied = evaluate(processedTuples);
        }

        @Override
        protected Tuple findNextTuple() {
            if (finished) {
                return null;
            }
            finished = true;
            return satisfied ? trueTuple : falseTuple;
        }
    }
}
