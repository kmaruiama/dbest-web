package ibd.query.binaryop;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.binaryop.conditional.Exists;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BinaryOperation extends Operation {

    protected Operation leftOperation;

    protected Operation rightOperation;

    public BinaryOperation(Operation leftOperation, Operation rightOperation) throws Exception {
        super();
        setLeftOperation(leftOperation);
        setRightOperation(rightOperation);
        
        
    }
    
    public boolean useLeftSideLookups(){
        return false;
    }
    
   @Override
    public void prepare() throws Exception {
        
        getLeftOperation().prepare();
        getRightOperation().prepare();
        
        super.prepare();
    }
    
    @Override
    public final void setProcessedOperations() {
        super.setProcessedOperations();
        getLeftOperation().setProcessedOperations();
        getRightOperation().setProcessedOperations();
    }

    public final void setLeftOperation(Operation op) {
        leftOperation = op;
        op.setParentOperation(this);
        refreshChildOperations();
    }
    
    public final void setRightOperation(Operation op) {
        rightOperation = op;
        op.setParentOperation(this);
        refreshChildOperations();
    }
    
    private void refreshChildOperations(){
        childOperations.clear();
        if (leftOperation!=null)
            childOperations.add(leftOperation);
        if (rightOperation!=null)
            childOperations.add(rightOperation);
    }

    public Operation getLeftOperation() {
        return leftOperation;
    }

    public Operation getRightOperation() {
        return rightOperation;
    }

    @Override
    public void close() throws Exception {
        leftOperation.close();
        rightOperation.close();
    }
    
    @Override
    public Map<String, List<String>> getContentInfo() {
        return getStringListMap(leftOperation, rightOperation);
    }

    protected static Map<String, List<String>> getStringListMap(Operation left, Operation right) {
        Map<String, List<String>> lTable = left.getContentInfo();
        Map<String, List<String>> rTable = right.getContentInfo();
        for(Map.Entry<String,List<String>> a: lTable.entrySet()){
            if(rTable.containsKey(a.getKey())){
                a.setValue(Stream.concat(a.getValue().stream(),rTable.get(a.getKey()).stream()).collect(Collectors.toList()));
                rTable.remove(a.getKey());
            }
        }
        return Stream.concat(lTable.entrySet().stream(), rTable.entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    @Override
    public void setConnectedDataSources() throws Exception {
        
        try {
        ReferedDataSource left[] = getLeftOperation().getExposedDataSources();
        ReferedDataSource right[] = getRightOperation().getExposedDataSources();
        connectedDataSources = new ReferedDataSource[left.length + right.length];
        int count = 0;
        for (int i = 0; i < left.length; i++) {
            connectedDataSources[count] = left[i];
            count++;
        }
        for (int i = 0; i < right.length; i++) {
            connectedDataSources[count] = right[i];
            count++;
        }
        } catch (Exception ex) {
            Logger.getLogger(Exists.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    

}
