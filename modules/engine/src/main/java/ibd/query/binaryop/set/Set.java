package ibd.query.binaryop.set;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.binaryop.BinaryOperation;
import java.util.List;
import java.util.Map;

public abstract class Set extends BinaryOperation {

    public Set(Operation leftOperation, Operation rightOperation) throws Exception {
        super(leftOperation, rightOperation);
    }

    
    
    @Override
    public void setConnectedDataSources() throws Exception {
        
        ReferedDataSource left[] = getLeftOperation().getExposedDataSources();
        connectedDataSources = new ReferedDataSource[left.length];
        System.arraycopy(left, 0, connectedDataSources, 0, left.length);

    }
    
    @Override
    public void setExposedDataSources() throws Exception {

        dataSources = connectedDataSources;

    }
    
    @Override
    public Map<String, List<String>> getContentInfo() {
        return leftOperation.getContentInfo();
    }

}
