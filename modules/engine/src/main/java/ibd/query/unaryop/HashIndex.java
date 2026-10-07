package ibd.query.unaryop;

import ibd.query.ColumnDescriptor;
import ibd.query.Operation;
import ibd.query.QueryStats;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.lookup.ColumnElement;
import ibd.query.lookup.CompositeLookupFilter;
import ibd.query.lookup.Element;
import ibd.query.lookup.LiteralElement;
import ibd.query.lookup.LookupFilter;
import ibd.query.lookup.NoLookupFilter;
import ibd.query.lookup.ReferencedElement;
import ibd.query.lookup.SingleColumnLookupFilter;
import ibd.table.ComparisonTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class HashIndex extends UnaryOperation {

    HashMap<String, List<Tuple>> tuples;

    List<Tuple> constantTuples;

    List<SingleColumnLookupFilter> hashedFilters;

    LookupFilter unhashedFilters;

    List<SingleColumnLookupFilter> hashedFilters2;

    public HashIndex(Operation childOperation) throws Exception {
        super(childOperation);
    }

    @Override
    public boolean canProcessDelegatedFilters() {
        return true;
    }

    @Override
    public void prepare() throws Exception {
        super.prepare();

        prepareHashColumns();

        tuples = null;
    }
    
    @Override
    public void close() throws Exception {
        if (tuples != null) {
            try {
                int tupleSize = getTupleSize();
                long memoryFreed = 0;
                
                for (List<Tuple> tupleList : tuples.values()) {
                    if (tupleList != null) {
                        memoryFreed += tupleList.size() * tupleSize;
                    }
                }
                if (constantTuples != null) {
                    memoryFreed += constantTuples.size() * tupleSize;
                }
                
                for (List<Tuple> tupleList : tuples.values()) {
                    if (tupleList != null) {
                        tupleList.clear();
                    }
                }
                tuples.clear();
                tuples = null;
                
                if (constantTuples != null) {
                    constantTuples.clear();
                    constantTuples = null;
                }
                
                QueryStats.MEMORY_USED = Math.max(0, QueryStats.MEMORY_USED - memoryFreed);
                
                System.gc();
                
            } catch (Exception e) {
                try {
                    if (tuples != null) {
                        for (List<Tuple> tupleList : tuples.values()) {
                            if (tupleList != null) {
                                tupleList.clear();
                            }
                        }
                        tuples.clear();
                        tuples = null;
                    }
                    if (constantTuples != null) {
                        constantTuples.clear();
                        constantTuples = null;
                    }
                    System.gc();
                } catch (Exception ignored) {
                    tuples = null;
                    constantTuples = null;
                }
            }
        }
        
        super.close();
    }
    
    private void prepareHashColumns() throws Exception {

        hashedFilters = new ArrayList();
        hashedFilters2 = new ArrayList();
        unhashedFilters = new NoLookupFilter();
        if (hasDelegatedfilters()) {
            LookupFilter joinFilter = parentOperation.getFilters();

            if (hasSingleDisjunction(joinFilter)) {
                CompositeLookupFilter rowFilter = transformDisjunctiveFilter((CompositeLookupFilter) joinFilter);
                if (rowFilter != null) {
                    joinFilter = rowFilter;
                }

            }

            prepareHashColumns(joinFilter);

            unhashedFilters = copyUnhashedFilters(joinFilter);
        }
    }

    private void prepareHashColumns(LookupFilter filter) throws Exception {
        if (filter instanceof CompositeLookupFilter compositeLookupFilter) {
            if (compositeLookupFilter.getBooleanConnector() == CompositeLookupFilter.OR) {
                return;
            }
            for (LookupFilter f : compositeLookupFilter.getFilters()) {
                prepareHashColumns(f);
            }
        } else if (filter instanceof SingleColumnLookupFilter singleColumnLookupFilter) {
            if (singleColumnLookupFilter.getComparisonType() == ComparisonTypes.EQUAL) {
                singleColumnLookupFilter = createFilter(singleColumnLookupFilter);
                if (singleColumnLookupFilter != null) {
                    hashedFilters.add(singleColumnLookupFilter);
                }
            }

        }

    }

    private SingleColumnLookupFilter createFilter(SingleColumnLookupFilter singleColumnLookupFilter) throws Exception {
        Element elem1 = singleColumnLookupFilter.getFirstElement();
        Element elem2 = singleColumnLookupFilter.getSecondElement();
        if (elem1 instanceof ColumnElement && (elem2 instanceof LiteralElement || elem2 instanceof ReferencedElement)) {
            return singleColumnLookupFilter;
        } else if (elem2 instanceof ColumnElement && (elem1 instanceof LiteralElement || elem1 instanceof ReferencedElement)) {
            SingleColumnLookupFilter invertedFilter = new SingleColumnLookupFilter(
                    singleColumnLookupFilter.getSecondElement(),
                    ComparisonTypes.EQUAL,
                    singleColumnLookupFilter.getFirstElement());
            hashedFilters2.add(singleColumnLookupFilter);
            return invertedFilter;
        }
        return null;
    }

    private LookupFilter copyUnhashedFilters(LookupFilter filter) throws Exception {

        if (filter instanceof CompositeLookupFilter clf) {

            CompositeLookupFilter rowFilter = new CompositeLookupFilter(clf.getBooleanConnector());
            for (LookupFilter f : clf.getFilters()) {
                LookupFilter rlf = copyUnhashedFilters(f);
                if (!(rlf instanceof NoLookupFilter)) {
                    rowFilter.addFilter(rlf);
                }
            }
            if (rowFilter.getFilters().isEmpty()) {
                return new NoLookupFilter();
            }
            return rowFilter;
        } else if (filter instanceof SingleColumnLookupFilter f) {
            if (hashedFilters2.contains(f)) {
                return new NoLookupFilter();
            }
            return f;
        } else {
            return new NoLookupFilter();
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

    List<LookupFilter> nullFilters = new ArrayList();

    private CompositeLookupFilter transformDisjunctiveFilter(CompositeLookupFilter compositeLookupFilter) throws Exception {

        boolean alreadyFoundNonConstant = false;
        CompositeLookupFilter rowFilter = new CompositeLookupFilter(CompositeLookupFilter.AND);
        for (LookupFilter f : compositeLookupFilter.getFilters()) {
            if (hasConstantFilter(f)) {
                nullFilters.add(f);
                continue;
            }
            if (alreadyFoundNonConstant) {
                nullFilters.clear();
                return null;
            }

            rowFilter.addFilter(f);
            alreadyFoundNonConstant = true;

        }

        return rowFilter;
    }

    private boolean hasValidNullFilter(ColumnElement colElem, int compType, LiteralElement litElem) throws Exception {
        Comparable value = litElem.getValue(null);
        return true;
    }

    private boolean hasConstantFilter(LookupFilter f) {
        SingleColumnLookupFilter filter = (SingleColumnLookupFilter) f;
        Element elem1 = filter.getFirstElement();
        Element elem2 = filter.getSecondElement();
        if (elem1 instanceof ColumnElement && elem2 instanceof LiteralElement) {
            return true;
        }

        if (elem2 instanceof ColumnElement && elem1 instanceof LiteralElement) {
            return true;
        }

        return false;
    }

    private LookupFilter createConstantFilter(LookupFilter f) throws Exception {
        SingleColumnLookupFilter filter = (SingleColumnLookupFilter) f;
        Element elem1 = filter.getFirstElement();
        Element elem2 = filter.getSecondElement();
        if (elem1 instanceof ColumnElement && elem2 instanceof LiteralElement) {
            return f;
        }

        if (elem2 instanceof ColumnElement && elem1 instanceof LiteralElement) {
            return new SingleColumnLookupFilter(elem2, ComparisonTypes.getSwitchedComparisonType(filter.getComparisonType()), elem1);
        }

        return null;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new MaterializedIndexIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public String toString() {
        return "Hash";
    }

    public class MaterializedIndexIterator extends UnpagedOperationIterator {

        Iterator<Tuple> it = null;

        public MaterializedIndexIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {

            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            this.lookup = unhashedFilters;
            buildHash();
            queryHash();
        }

        private void queryHash() {
            if (tuples == null) {
                it = new ArrayList<Tuple>().iterator();
                return;
            }
            
            String key = "";
            for (SingleColumnLookupFilter lookupFilter : hashedFilters) {
                Element elem2 = lookupFilter.getSecondElement();
                key += elem2.getValue(null).toString();
            }

            List<Tuple> result = tuples.get(key);
            if (result != null) {
                if (constantTuples != null) {
                    it = new TwoListsIterator(constantTuples.iterator(), result.iterator());
                } else {
                    it = result.iterator();
                }
            } else {
                if (constantTuples != null) {
                    it = constantTuples.iterator();
                } else {
                    it = new ArrayList<Tuple>().iterator();
                }
            }
        }

        private void buildHash() {
            if (tuples == null) {
                Thread.currentThread().interrupted();
                
                tuples = new HashMap();
                constantTuples = new ArrayList();
                long memoryUsed = 0;
                
                try {
                    it = childOperation.lookUp(processedTuples, false);
                    int tupleSize = childOperation.getTupleSize();
                    int processedCount = 0;
                    while (it.hasNext()) {
                        if (processedCount % 1000 == 0 && Thread.currentThread().isInterrupted()) {
                            tuples.clear();
                            constantTuples.clear();
                            tuples = null;
                            constantTuples = null;
                            return;
                        }
                        
                        Tuple tuple = (Tuple) it.next();
                        processedCount++;

                        boolean foundNullTuple = false;
                        for (LookupFilter lookupFilter : nullFilters) {
                            if (lookupFilter.match(tuple))
                            {
                                constantTuples.add(tuple);
                                memoryUsed += tupleSize;
                                foundNullTuple = true;
                                break;
                            }
                        }
                        if (foundNullTuple) {
                            continue;
                        }

                        String key = "";
                        for (SingleColumnLookupFilter lookupFilter : hashedFilters) {
                            ColumnElement colElem = (ColumnElement) lookupFilter.getFirstElement();
                            ColumnDescriptor col = colElem.getColumnDescriptor();
                            key += tuple.rows[col.getColumnLocation().rowIndex].getValue(col.getColumnLocation().colIndex).toString();
                        }

                        List tupleList = tuples.get(key);
                        if (tupleList == null) {
                            tupleList = new ArrayList();
                            tuples.put(key, tupleList);
                        }
                        tupleList.add(tuple);
                        memoryUsed += tupleSize;
                    }

                } catch (Exception ex) {
                    
                }
                
                if (tuples != null && !Thread.currentThread().isInterrupted()) {
                    QueryStats.MEMORY_USED += memoryUsed;
                }
            }
        }

        @Override
        protected Tuple findNextTuple() {
            while (it.hasNext()) {
                Tuple tp = it.next();
                if (lookup.match(tp)) {
                    return tp;
                }

            }
            return null;
        }

    }

    private class TwoListsIterator implements Iterator {

        private final Iterator<Tuple> it1;
        private final Iterator<Tuple> it2;

        public TwoListsIterator(Iterator<Tuple> it1, Iterator<Tuple> it2) {
            this.it1 = it1;
            this.it2 = it2;
        }

        @Override
        public boolean hasNext() {
            return it1.hasNext() || it2.hasNext();
        }

        @Override
        public Tuple next() {
            return it1.hasNext() ? it1.next() : it2.next();
        }

    }

}
