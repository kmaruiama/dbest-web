package ibd.query.unaryop;

import ibd.query.Operation;
import ibd.query.UnpagedOperationIterator;
import ibd.query.ReferedDataSource;
import ibd.query.Tuple;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SourceRename extends UnaryOperation {

    String newAlias;
    HashMap<String, String> old2NewPairs = new HashMap();

    public SourceRename(Operation op, String oldAlias, String newAlias) throws Exception {
        super(op);
        this.newAlias = newAlias;
        old2NewPairs.put(oldAlias, newAlias);
    }
    
    
    
    @Override
    public void setConnectedDataSources() throws Exception {
        
        
        ReferedDataSource s[] = childOperation.getExposedDataSources();
        connectedDataSources = new ReferedDataSource[s.length];
        
        int i = 0;
        for (ReferedDataSource referedDataSource : s) {
            ReferedDataSource newDataSource = new ReferedDataSource();
            newDataSource.prototype = referedDataSource.prototype;
            String newAlias_ = old2NewPairs.get(referedDataSource.alias);
            if (newAlias_==null)
                newAlias_ = referedDataSource.alias;
            
            newDataSource.alias = newAlias_;
            connectedDataSources[i] = newDataSource;
            i++;
        }
        
    }
    
    @Override
    public void setExposedDataSources() throws Exception {

        dataSources = connectedDataSources;

    }
    
    @Override
    public Map<String, List<String>> getContentInfo() {
        Map<String,List<String>> newMap = new HashMap<String,List<String>>(); 
        Map<String,List<String>> oldMap = this.getChildOperation().getContentInfo();
        for (Map.Entry<String, List<String>> entry : oldMap.entrySet()) {
            String oldAlias_ = entry.getKey();
            String newAlias_ = old2NewPairs.get(oldAlias_);
            if (newAlias_==null)
                newAlias_ = oldAlias_;
            newMap.put(newAlias_, entry.getValue());
            
        }
        return newMap;
    }

    @Override
    public Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation) {
        return new SourceRenameIterator(processedTuples, withFilterDelegation);
    }

    

    @Override
    public String toString() {
        return "Source Rename:" + newAlias;
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
                return tp;

            }
            return null;
        }


    }
}
