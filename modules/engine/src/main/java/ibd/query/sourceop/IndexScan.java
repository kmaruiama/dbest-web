package ibd.query.sourceop;

import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.lookup.ColumnElement;
import ibd.query.lookup.LookupFilter;
import ibd.query.lookup.CompositeLookupFilter;
import ibd.query.lookup.Element;
import ibd.query.lookup.LiteralElement;
import ibd.query.lookup.NoLookupFilter;
import ibd.query.lookup.ReferencedElement;
import ibd.query.lookup.SingleColumnLookupFilter;
import ibd.table.ComparisonTypes;
import ibd.table.Table;
import ibd.table.lookup.NoRowLookupFilter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.column.Column;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class IndexScan extends SourceOperation {

    private List<String> columns;

    public Table table;

    boolean canLookup = false;

    List<SingleColumnLookupFilter> fastFilters;

    List<SingleColumnLookupFilter> fastDisjunctiveFilters;

    HashMap<SingleColumnLookupFilter, SingleColumnLookupFilter> modifiedFilters;

    LinkedDataRow fastLookupRow;

    LookupFilter slowLookupFilter;

    int compType;

    public IndexScan(String tableAlias, Table table) {
        super(tableAlias);
        this.table = table;
        columns = new ArrayList<>();

        for (Column c : table.getPrototype().getColumns()) {
            columns.add(c.getName());
        }
    }

    @Override
    public void prepare() throws Exception {

        table.open();
        
        super.prepare();

        separateFilters();

        fastLookupRow = new LinkedDataRow(table.getPrototype(), true);
    }

    @Override
    public void setExposedDataSources() throws Exception {
        super.setExposedDataSources();
        dataSources[0].prototype = table.getPrototype();
    }

    @Override
    public void setConnectedDataSources() throws Exception {
        super.setConnectedDataSources();
        connectedDataSources[0].prototype = table.getPrototype();
    }

    @Override
    public String getDataSourceName() {
        return table.getName();
    }

    @Override
    public boolean canProcessDelegatedFilters() {
        return true;
    }

    private void separateFilters() throws Exception {

        slowLookupFilter = new NoLookupFilter();

        canLookup = false;

        if (hasDelegatedFilters) {

            LookupFilter joinFilter = parentOperation.getFilters();

            fastFilters = new ArrayList();
            modifiedFilters = new HashMap();

            fillFastFilters(joinFilter);

            List<String> columns = new ArrayList<>();
            for (SingleColumnLookupFilter f : fastFilters) {
                columns.add(f.getColumnDescriptor().getColumnName());
            }

            List<String> pkColumns = table.getPrototype().pKPrefix(columns);
            if (!pkColumns.isEmpty()) {
                canLookup = true;
            }

            List<SingleColumnLookupFilter> reorganizedFilters = new ArrayList<>();
            for (String pkColumn : pkColumns) {
                for (int i = 0; i < fastFilters.size(); i++) {
                    SingleColumnLookupFilter filter = fastFilters.get(i);
                    String colName = filter.getColumnDescriptor().getColumnName();

                    if (colName.equals(pkColumn)) {
                        reorganizedFilters.add(filter);
                        compType = filter.getComparisonType();
                        fastFilters.remove(i);
                        if (filter.getComparisonType() != ComparisonTypes.EQUAL) {
                            fastFilters.clear();
                            break;
                        }
                        break;
                    }
                }
            }
            fastFilters.clear();
            fastFilters.addAll(reorganizedFilters);

            for (SingleColumnLookupFilter fastFilter : fastFilters) {
                try {
                    setColumnLocation(fastFilter.getColumnDescriptor());
                } catch (Exception e) {
                    System.err.println("Warning: Could not set column location for filter: " + fastFilter);
                }
            }

            List<SingleColumnLookupFilter> unnModifiedFastFilters = new ArrayList();
            for (SingleColumnLookupFilter fastFilter : fastFilters) {
                SingleColumnLookupFilter originalFilter = modifiedFilters.get(fastFilter);
                if (originalFilter != null) {
                    unnModifiedFastFilters.add(originalFilter);
                } else {
                    unnModifiedFastFilters.add(fastFilter);
                }
            }

            slowLookupFilter = fillSlowFilter(parentOperation.getFilters(), unnModifiedFastFilters);
        }

    }

    private void fillFastFilters(LookupFilter filter) throws Exception {
        if (filter instanceof CompositeLookupFilter compositeLookupFilter) {
            if (compositeLookupFilter.getBooleanConnector() == CompositeLookupFilter.OR) {
                return;
            }
            for (LookupFilter f : compositeLookupFilter.getFilters()) {
                fillFastFilters(f);
            }
        } else if (filter instanceof SingleColumnLookupFilter singleColumnLookupFilter) {
            {
                Element elem1 = singleColumnLookupFilter.getFirstElement();
                Element elem2 = singleColumnLookupFilter.getSecondElement();
                if (elem1 instanceof ColumnElement && (elem2 instanceof LiteralElement || elem2 instanceof ReferencedElement)) {
                    compType = singleColumnLookupFilter.getComparisonType();
                    fastFilters.add(singleColumnLookupFilter);
                } else if (elem2 instanceof ColumnElement && (elem1 instanceof LiteralElement || elem1 instanceof ReferencedElement)) {
                    int compType_ = ComparisonTypes.getSwitchedComparisonType(singleColumnLookupFilter.getComparisonType());
                    SingleColumnLookupFilter invertedFilter = new SingleColumnLookupFilter(
                            singleColumnLookupFilter.getSecondElement(),
                            compType_,
                            singleColumnLookupFilter.getFirstElement());
                    fastFilters.add(invertedFilter);
                    modifiedFilters.put(invertedFilter, singleColumnLookupFilter);
                }

            }
        }

    }

    private boolean hasSingleDisjunction(LookupFilter filter) throws Exception {
        if (!(filter instanceof CompositeLookupFilter compositeLookupFilter)) {
            return false;
        }

        if (compositeLookupFilter.getBooleanConnector() == CompositeLookupFilter.AND) {
            return false;
        }

        for (LookupFilter f : compositeLookupFilter.getFilters()) {
            if (f instanceof CompositeLookupFilter) {
                return false;
            }
        }
        return true;
    }

    SingleColumnLookupFilter nullFilter;

    private CompositeLookupFilter transformDisjunctiveFilter(CompositeLookupFilter compositeLookupFilter) throws Exception {

        boolean foundNonNullTerm = false;
        CompositeLookupFilter rowFilter = new CompositeLookupFilter(CompositeLookupFilter.AND);
        for (LookupFilter f : compositeLookupFilter.getFilters()) {
            SingleColumnLookupFilter filter = (SingleColumnLookupFilter) f;
            Element elem1 = filter.getFirstElement();
            Element elem2 = filter.getSecondElement();
            int compType = filter.getComparisonType();
            if (elem1 instanceof ColumnElement colElem && elem2 instanceof LiteralElement litElem) {
                nullFilter = createNullFilter(colElem, compType, litElem);
                if (nullFilter == null) {
                    return null;
                } else {
                    if (foundNonNullTerm) {
                        return null;
                    }
                    rowFilter.addFilter(f);
                    foundNonNullTerm = true;
                }
            } else if (elem2 instanceof ColumnElement colElem && elem1 instanceof LiteralElement litElem) {
                nullFilter = createNullFilter(colElem, compType, litElem);
                nullFilter = new SingleColumnLookupFilter(colElem, compType, litElem);
                if (nullFilter == null) {
                    return null;
                } else {
                    if (foundNonNullTerm) {
                        return null;
                    }
                    rowFilter.addFilter(f);
                    foundNonNullTerm = true;
                }
            }

        }
        return rowFilter;
    }

    private SingleColumnLookupFilter createNullFilter(ColumnElement colElem, int compType, LiteralElement litElem) throws Exception {
        Comparable value = litElem.getValue(null);
        if (value == null) {
            List<String> list = new ArrayList();
            list.add(colElem.getColumnDescriptor().getColumnName());
            List result = table.getPrototype().pKPrefix(list);
            if (result.isEmpty()) {
                return null;
            }
            return new SingleColumnLookupFilter(colElem, compType, litElem);
        }
        return null;
    }

    private LookupFilter fillSlowFilter(LookupFilter filter, List<SingleColumnLookupFilter> unnModifiedFastFilters) throws Exception {

        if (filter instanceof CompositeLookupFilter clf) {

            CompositeLookupFilter rowFilter = new CompositeLookupFilter(clf.getBooleanConnector());
            for (LookupFilter f : clf.getFilters()) {
                LookupFilter rlf = fillSlowFilter(f, unnModifiedFastFilters);
                if (!(rlf instanceof NoLookupFilter)) {
                    rowFilter.addFilter(rlf);
                }
            }
            if (rowFilter.getFilters().isEmpty()) {
                return new NoLookupFilter();
            }
            return rowFilter;
        } else if (filter instanceof SingleColumnLookupFilter f) {
            if (unnModifiedFastFilters.contains(f)) {
                return new NoLookupFilter();
            }
            return f;
        } else {
            return new NoLookupFilter();
        }
    }

@Override
public String toString() {
        return "[" + alias + "] Index Scan";
    }

    @Override
public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new IndexScanIterator(processedTuples, withFilterDelegation);
    }

    @Override
public boolean exists(List<Tuple> processedTuples, boolean withFilterDelegation) {
        if (canLookup) {
            for (SingleColumnLookupFilter filter_ : fastFilters) {
                fastLookupRow.setValue(filter_.getColumnDescriptor().getColumnLocation().colIndex, filter_.getSecondElement().getValue(null));
            }
            ibd.query.QueryStats.PK_SEARCH++;
            return table.contains(fastLookupRow);
        } else {
            if (slowLookupFilter instanceof NoRowLookupFilter) {
                Iterator iterator;
                try {
                    iterator = table.getAllRecordsIterator();
                    return iterator.hasNext();
                } catch (Exception ex) {
                    return false;
                }

            } else {
                try {
                    Iterator iterator = table.getFilteredRecordsIterator(slowLookupFilter);
                    return iterator.hasNext();
                } catch (Exception ex) {
                    return false;
                }
            }
        }
    }

    @Override
public void close() throws Exception {
    }

    @Override
public Map<String, List<String>> getContentInfo() {
        HashMap<String, List<String>> map = new LinkedHashMap<>();
        map.put(alias, new ArrayList<>(columns));
        return map;

}

    public class IndexScanIterator extends UnpagedOperationIterator {

    Iterator<LinkedDataRow> iterator;

    public IndexScanIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {
        super(processedTuples, withFilterDelegation, getDelegatedFilters());

        try {

            if (canLookup) {
                ibd.query.QueryStats.PK_SEARCH++;
                for (SingleColumnLookupFilter filter : fastFilters) {
                    fastLookupRow.setValue(filter.getColumnDescriptor().getColumnLocation().colIndex, filter.getSecondElement().getValue(null));
                }

                iterator = table.getPKFilteredRecordsIterator(fastLookupRow, slowLookupFilter, compType);
                return;

            }

            if (slowLookupFilter instanceof NoLookupFilter) {
                iterator = table.getAllRecordsIterator();
                return;
            }
            iterator = table.getFilteredRecordsIterator(slowLookupFilter);

        } catch (Exception ex) {
        }

    }

    @Override
    protected Tuple findNextTuple() {

        while (iterator.hasNext()) {
            LinkedDataRow row = iterator.next();
            Tuple tuple = new Tuple();
            tuple.setSingleSourceRow(dataSources[0].alias, row);
            return tuple;

        }

        return null;
    }

}
}
