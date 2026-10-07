package ibd.query;

import ibd.query.binaryop.BinaryOperation;
import ibd.query.binaryop.join.Join;
import ibd.query.lookup.ColumnElement;
import ibd.query.lookup.CompositeLookupFilter;
import ibd.query.lookup.LookupFilter;
import ibd.query.lookup.NoLookupFilter;
import ibd.query.lookup.ReferencedElement;
import ibd.query.lookup.SingleColumnLookupFilter;
import ibd.table.prototype.column.Column;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public abstract class Operation {

    protected ReferedDataSource[] dataSources;
    
    protected ReferedDataSource[] connectedDataSources;
    
    protected List<Operation> processedOperations = null;

    protected Operation parentOperation;

    protected boolean isReady = false;

    protected int startingTuple = -1;

    protected int tuplesToRead = -1;

    protected boolean hasDelegatedFilters = true;

    private boolean runFromHere = false;
    
    protected String alias = "";
    
    public void setDataSourceAlias(String alias){
        this.alias = alias;
    }
    public String getDataSourceAlias(){
        return alias;
    }

    public void setPageInfo(int startingTuple, int tuplesToRead) {
        this.startingTuple = startingTuple;
        this.tuplesToRead = tuplesToRead;
    }

    public int getStartingTuple() {
        return startingTuple;
    }

    public int getTuplesToRead() {
        return tuplesToRead;
    }

    public ReferedDataSource[] getExposedDataSources() throws Exception {
        return dataSources;
    }
    
    public ReferedDataSource[] getConnectedDataSources() throws Exception {
        return connectedDataSources;
    }

    public void prepare() throws Exception {
        if (!runFromHere) {
            checkFilters();
        }
        prepareIndexes();
       
    }

    public boolean hasDelegatedfilters() {
        return hasDelegatedFilters;
    }

    private void checkFilters() throws Exception {

        hasDelegatedFilters = false;
        if (parentOperation == null) {
            return;
        }

        if (!(parentOperation.getFilters() instanceof NoLookupFilter)) {
            hasDelegatedFilters = true;
        }
        if (parentOperation instanceof BinaryOperation) {
            BinaryOperation bop = (BinaryOperation) parentOperation;
            if (bop.useLeftSideLookups()) {
                if (bop.getLeftOperation().equals(this)) {
                    hasDelegatedFilters = false;
                }
            } else {
                hasDelegatedFilters = false;
            }

        }

    }

    private void setTupleIndex(LookupFilter filter) throws Exception {
        if (filter instanceof CompositeLookupFilter) {
            setTupleIndex((CompositeLookupFilter) filter);

        } else if (filter instanceof SingleColumnLookupFilter) {
            setTupleIndex((SingleColumnLookupFilter) filter);
        } 
    }

    private void setTupleIndex(CompositeLookupFilter filter) throws Exception {
        for (LookupFilter filter1 : filter.getFilters()) {
            setTupleIndex(filter1);
        }
    }

    private ColumnElement setTupleIndex(ColumnElement elem) throws Exception{
        try {
        setColumnLocation(elem.getColumnDescriptor());
        }
        catch(Exception e){
            boolean found = setColumnLocationFromProcessedOperations(elem.getColumnDescriptor());
            if (found){
                return new ReferencedElement(elem.getColumnDescriptor());
            }
            else throw new Exception("Error in operation "+this+".\nColumn " + elem.getColumnDescriptor().getColumnName()+" not found.");
        
        }
        return null;
    }
    
    private void setTupleIndex(SingleColumnLookupFilter filter) throws Exception{

            if (filter.getFirstElement() instanceof ColumnElement){
            ColumnElement colElem = (ColumnElement)filter.getFirstElement();
            ColumnElement newElem = setTupleIndex(colElem);
            if (newElem!=null)
                filter.setFirstElement(newElem);
            }
            
            if (filter.getSecondElement() instanceof ColumnElement){
            ColumnElement colElem = (ColumnElement) filter.getSecondElement();
            ColumnElement newElem = setTupleIndex(colElem);
            if (newElem!=null)
                filter.setSecondElement(newElem);
            }
        
    }

    

    
    public boolean canProcessDelegatedFilters() {
        return false;
    }
    
    public LookupFilter getDelegatedFilters() {
        if (hasDelegatedFilters) {
            return parentOperation.getFilters();
        }
        return new NoLookupFilter();
    }

    public LookupFilter getFilters() {
        return new NoLookupFilter();
    }

    ;
    
    

    public void setParentOperation(Operation op) {
        parentOperation = op;
    }

    public Operation getParentOperation() {
        return parentOperation;
    }

    public int getRowIndex(String tableName, ReferedDataSource[] dataSources_) throws Exception {
        if (tableName == null) {
            return 0;
        } else {
            for (int i = 0; i < dataSources_.length; i++) {
                if (dataSources_[i].alias.equals(tableName)) {
                    return i;
                }

            }
        }
        return -1;
    }

    public ReferedDataSource getExposedDataSource(String tableName) throws Exception {
        if (tableName == null) {
            return null;
        } else {
            ReferedDataSource[] dataSources_ = getExposedDataSources();
            for (int i = 0; i < dataSources_.length; i++) {
                if (dataSources_[i].alias.equals(tableName)) {
                    return dataSources_[i];
                }

            }
        }
        return null;
    }
    
    public ReferedDataSource getConnectedDataSource(String tableName) throws Exception {
        if (tableName == null) {
            return null;
        } else {
            ReferedDataSource[] dataSources_ = getConnectedDataSources();
            for (int i = 0; i < dataSources_.length; i++) {
                if (dataSources_[i].alias.equals(tableName)) {
                    return dataSources_[i];
                }

            }
        }
        return null;
    }

    public int getTupleSize() throws Exception {
        int size = 0;
        for (ReferedDataSource referedDataSource : getExposedDataSources()) {
            size += referedDataSource.prototype.getSizeInBytes();
        }
        return size;
    }

    public boolean setColumnLocationFromProcessedOperations(ColumnDescriptor columnDescriptor) throws Exception {

        for (int i = 0; i < processedOperations.size(); i++) {
            Operation processedOperation = processedOperations.get(i);
            try{
            processedOperation.setColumnLocation(columnDescriptor);
            ColumnLocation auxColumnLocation = columnDescriptor.getColumnLocation();
            if (auxColumnLocation != null) {
                auxColumnLocation.tupleIndex = i;
                return true;
            }
            }catch(Exception e){}
        }
        return false;
    }

    public void setColumnLocation(ColumnDescriptor columnDescriptor) throws Exception {

        ReferedDataSource[] dataSources_ = getExposedDataSources();
        ColumnLocation colLoc = getColumnLocation(columnDescriptor, dataSources_);
        columnDescriptor.setColumnLocation(colLoc);
    }
    
    protected ColumnLocation getColumnLocation(ColumnDescriptor columnDescriptor, ReferedDataSource[] dataSources_) throws Exception {

        ColumnLocation colLoc = new ColumnLocation();
        if (columnDescriptor.getTableName() == null) {
            colLoc.tupleIndex = 0;
            colLoc.rowIndex = 0;
            Column col = dataSources_[0].prototype.getColumn(columnDescriptor.getColumnName());
            if (col==null)
                throw new Exception("Error in operation "+this+".\nColumn " + columnDescriptor.getColumnName()+" not found.");
            colLoc.colIndex = col.index;
        } else {
            int rowIndex = getRowIndex(columnDescriptor.getTableName(), dataSources_);
            if (rowIndex != -1) {
                Column col = dataSources_[rowIndex].prototype.getColumn(columnDescriptor.getColumnName());
                if (col==null)
                    throw new Exception("Error in operation "+this+".\nColumn " + columnDescriptor.getColumnName()+" not found.");
                colLoc.rowIndex = rowIndex;
                colLoc.colIndex = col.index;
            }
            else throw new Exception("Error in operation "+this+".\nTable " + columnDescriptor.getTableName()+" not found.");
        }
        return colLoc;
    }

    public void setDataSources() throws Exception{
        setConnectedDataSources();
        setExposedDataSources();
        for (Operation childOperation : childOperations) {
            childOperation.setDataSources();
        }
    }
    
    public abstract void setConnectedDataSources() throws Exception;
    
    public void setExposedDataSources() throws Exception {
        
         dataSources = connectedDataSources;

    }

    public void setProcessedOperations() {

        processedOperations = new ArrayList();

        Operation parent = getParentOperation();
        if (parent != null && parent.processedOperations != null && !runFromHere) {
            List<Operation> processedOperations_ = parent.processedOperations;
            processedOperations.addAll(processedOperations_);
            if (parent instanceof Join) {
                Join join = (Join) parent;

                if (join.useLeftSideLookups() && join.getRightOperation().equals(this)) {
                    processedOperations.add(join.getLeftOperation());
                }
            }
        }
    }

    protected abstract Iterator<Tuple> lookUp_(List<Tuple> processedTuples, boolean withFilterDelegation);

    public Iterator<Tuple> lookUp(List<Tuple> processedTuples, boolean withFilterDelegation) {
        if (tuplesToRead != -1 && startingTuple != -1) {
            return pagedLookUp(processedTuples, withFilterDelegation, startingTuple, tuplesToRead);
        } else {
            return lookUp_(processedTuples, withFilterDelegation);
        }
    }

    protected Iterator<Tuple> pagedLookUp(List<Tuple> processedTuples, boolean withFilterDelegation, int startingTuple, int tuplesToRead) {
        return new PagedOperationIterator(lookUp_(processedTuples, withFilterDelegation), startingTuple, tuplesToRead);
    }

    public boolean exists(List<Tuple> processedTuples, boolean withFilterDelegation) {
        Iterator<Tuple> iterator = pagedLookUp(processedTuples, withFilterDelegation, 0, 1);
        return iterator.hasNext();
    }

    Iterator<Tuple> currentRun;

    public void open() throws Exception {
        currentRun = run();
    }

    public Tuple next() {
        return currentRun.next();
    }

    public boolean hasNext() {
        return currentRun.hasNext();
    }

    protected List<Operation> childOperations = new ArrayList();
    public List<Operation> getChildOperations(){
        return childOperations;
    }
    
    protected void prepareIndexes() throws Exception{
         setTupleIndex(getFilters());
    }
    
    private void prepareAllFromTopDown2() throws Exception{
    Stack<Operation> stack = new Stack<>();
        Operation current = this;

        while (current != null || !stack.isEmpty()) {
            while (current != null) {
                stack.push(current);
                List<Operation> children = current.getChildOperations();
                current = (children != null && !children.isEmpty()) ? children.get(0) : null;
            }

            current = stack.pop();
            current.prepare();

            List<Operation> children = current.getChildOperations();
            current = (children != null && children.size() > 1) ? children.get(1) : null;
        }
    }
    
    private void prepareAllFromTopDown() throws Exception {
        
        Stack<Operation> stack = new Stack<>();
        stack.push(this);
        
        while (!stack.isEmpty()) {
            Operation current = stack.pop();
            current.prepare();
            
            List<Operation> children = current.getChildOperations();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
    }
    
    public void prepareAllDataSources() throws Exception {

        Stack<Operation> stack = new Stack<>();
        Stack<Operation> processedStack = new Stack<>();
        stack.push(this);

        while (!stack.isEmpty()) {
            Operation current = stack.pop();
            processedStack.push(current);

            List<Operation> children = current.getChildOperations();
            if (children != null) {
                for (Operation child : children) {
                    stack.push(child);
                }
            }
        }

        while (!processedStack.isEmpty()) {
            Operation op = processedStack.pop();            
            op.setConnectedDataSources();
            op.setExposedDataSources();
        }
    }
    
    public abstract Map<String, List<String>> getContentInfo();

    public Iterator<Tuple> run() throws Exception {
        try{
        runFromHere = true;
        hasDelegatedFilters = false;
        
        prepareAllDataSources();
        
        setProcessedOperations();

        prepareAllFromTopDown();
        }
        catch(Exception ex){
            runFromHere = false;
            throw ex;
        }
        
        runFromHere = false;

        return lookUp(new ArrayList(), false);
    }
    
    public abstract void close() throws Exception;

}
