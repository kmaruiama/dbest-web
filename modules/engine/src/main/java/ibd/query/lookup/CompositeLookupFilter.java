package ibd.query.lookup;

import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;
import java.util.ArrayList;
import java.util.List;

public class CompositeLookupFilter implements LookupFilter {

    public static final int AND = 0;
    public static final int OR = 1; 

    List<LookupFilter> filters = new ArrayList();

    int boolean_connector;

    public CompositeLookupFilter(int connector) {
        this.boolean_connector = connector;
    }

    public void addFilter(LookupFilter filter) {
        filters.add(filter);
    }

    public List<LookupFilter> getFilters() {
        return filters;
    }
    
    public int getBooleanConnector(){
        return boolean_connector;
    }

    @Override
    public boolean match(Tuple tuple) {

        if (boolean_connector == CompositeLookupFilter.AND) {
            return matchAnd(tuple);
        }

        return matchOr(tuple);

    }

    private boolean matchAnd(Tuple tuple) {

        for (LookupFilter filter : filters) {
            if (!filter.match(tuple)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchOr(Tuple tuple) {

        for (LookupFilter filter : filters) {
            if (filter.match(tuple)) {
                return true;
            }
        }

        return false;
    }
    
    @Override
    public boolean match(LinkedDataRow row) {

        if (boolean_connector == CompositeLookupFilter.AND) {
            return matchAnd(row);
        }

        return matchOr(row);

    }

    private boolean matchAnd(LinkedDataRow row) {

        for (LookupFilter filter : filters) {
            if (!filter.match(row)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchOr(LinkedDataRow row) {

        for (LookupFilter filter : filters) {
            if (filter.match(row)) {
                return true;
            }
        }

        return false;
    }

    
    @Override
    public String toString() {

        String output = "";
        String connector = " OR ";
        if (boolean_connector==AND)
            connector = " AND ";
        boolean first = true;
        for (LookupFilter filter : filters) {
            if (first)
                output +=filter.toString();
            else output +=connector+filter.toString();
            first = false;
        }

        return output;
    }

}
