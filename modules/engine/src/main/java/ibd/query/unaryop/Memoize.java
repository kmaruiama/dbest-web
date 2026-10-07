package ibd.query.unaryop;

import ibd.query.Operation;
import ibd.query.QueryStats;
import ibd.query.UnpagedOperationIterator;
import ibd.query.Tuple;
import ibd.query.lookup.CompositeLookupFilter;
import ibd.query.lookup.LookupFilter;
import ibd.query.lookup.NoLookupFilter;
import ibd.query.lookup.SingleColumnLookupFilter;
import ibd.table.ComparisonTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class Memoize extends UnaryOperation {

    HashMap<String, List<Tuple>> tuples;

    List<SingleColumnLookupFilter> hashedFilters;

    LookupFilter unhashedFilters;

    public Memoize(Operation childOperation) throws Exception {
        super(childOperation);
    }

    @Override
    public void prepare() throws Exception {
        super.prepare();

        prepareHashColumns();

        tuples = new HashMap();
    }

    @Override
    public LookupFilter getFilters() {
        if (parentOperation != null) {
            return parentOperation.getFilters();
        }
        return new NoLookupFilter();
    }

    @Override
    public boolean canProcessDelegatedFilters() {
        return true;
    }

    private void prepareHashColumns() throws Exception {

        hashedFilters = new ArrayList();
        unhashedFilters = new NoLookupFilter();
        if (hasDelegatedfilters()) {
            LookupFilter joinFilter = parentOperation.getFilters();

            prepareHashColumns(joinFilter);

            unhashedFilters = copyUnhashedFilters(joinFilter);
        }
    }

    private void prepareHashColumns(LookupFilter filter) {
        if (filter instanceof CompositeLookupFilter compositeLookupFilter) {
            if (compositeLookupFilter.getBooleanConnector() == CompositeLookupFilter.OR) {
                return;
            }
            for (LookupFilter f : compositeLookupFilter.getFilters()) {
                prepareHashColumns(f);
            }
        } else if (filter instanceof SingleColumnLookupFilter singleColumnLookupFilter) {
            if (singleColumnLookupFilter.getComparisonType() == ComparisonTypes.EQUAL) {
                hashedFilters.add(singleColumnLookupFilter);
            }
        }

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
            if (hashedFilters.contains(f)) {
                return new NoLookupFilter();
            }
            return f;
        } else {
            return new NoLookupFilter();
        }
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new MemoizeIterator(processedTuples, withFilterDelegation);
    }

    @Override
    public String toString() {
        return "Memoize";
    }

    public class MemoizeIterator extends UnpagedOperationIterator {

        Iterator<Tuple> it = null;
        int tupleSize = 0;

        public MemoizeIterator(List<Tuple> processedTuples, boolean withFilterDelegation) {

            super(processedTuples, withFilterDelegation, getDelegatedFilters());
            this.lookup = unhashedFilters;

            try {
                tupleSize = childOperation.getTupleSize();

            } catch (Exception ex) {
            }

            queryHash(processedTuples);
        }

        private void queryHash(List<Tuple> processedTuples) {
            String key = "";
            for (SingleColumnLookupFilter lookupFilter : hashedFilters) {
                key += lookupFilter.getValue(null).toString();
            }

            List<Tuple> result = tuples.get(key);
            if (result != null) {
                it = result.iterator();
            } else {
                it = childOperation.lookUp(processedTuples, true);
                it = feedHash(key);
            }
        }

        private Iterator<Tuple> feedHash(String key) {
            List tupleList = new ArrayList();

            while (it.hasNext()) {
                Tuple tuple = (Tuple) it.next();
                tupleList.add(tuple);

            }
            QueryStats.MEMORY_USED += tupleList.size() * tupleSize;
            tuples.put(key, tupleList);
            return tupleList.iterator();

        }

        @Override
        protected Tuple findNextTuple() {
            while (it.hasNext()) {
                QueryStats.NEXT_CALLS++;
                Tuple tp = it.next();
                if (lookup.match(tp)) {
                    return tp;
                }

            }
            return null;
        }

    }

}
