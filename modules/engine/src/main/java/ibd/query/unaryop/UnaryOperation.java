package ibd.query.unaryop;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import java.util.List;
import java.util.Map;

public abstract class UnaryOperation extends Operation {

    protected Operation childOperation;

    public UnaryOperation(Operation childOperation) throws Exception {
        setChildOperation(childOperation);
    }

    public final void setChildOperation(Operation childOperation) {
        this.childOperation = childOperation;
        childOperations.add(childOperation);
        childOperation.setParentOperation(this);
    }

    public Operation getChildOperation() {
        return childOperation;
    }

    @Override
    public void prepare() throws Exception {
        childOperation.prepare();
        
        super.prepare();
    }

    @Override
    public void close() throws Exception {
        childOperation.close();
    }

    
    @Override
    public void setConnectedDataSources() throws Exception {
        ReferedDataSource s[] = childOperation.getExposedDataSources();
        connectedDataSources = new ReferedDataSource[s.length];
        System.arraycopy(s, 0, connectedDataSources, 0, s.length);

    }
    
    @Override
    public void setExposedDataSources() throws Exception {
        ReferedDataSource s[] = childOperation.getExposedDataSources();
        dataSources = new ReferedDataSource[s.length];
        System.arraycopy(s, 0, dataSources, 0, s.length);

    }
    
    @Override
    public final void setProcessedOperations() {
        super.setProcessedOperations();
        childOperation.setProcessedOperations();
    }
    
    @Override
    public Map<String, List<String>> getContentInfo() {
        return childOperation.getContentInfo();
    }

}
