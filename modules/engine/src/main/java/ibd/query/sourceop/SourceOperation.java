package ibd.query.sourceop;

import ibd.query.Operation;
import ibd.query.ReferedDataSource;
import ibd.query.SingleSource;


public abstract class SourceOperation extends Operation implements SingleSource{
    
    public SourceOperation(String dataSourceAlias){
        this.alias = dataSourceAlias;
        
    }
    
    public abstract String getDataSourceName();

    
    @Override
    public void setExposedDataSources() throws Exception {
        dataSources = new ReferedDataSource[1];
        dataSources[0] = new ReferedDataSource();
        dataSources[0].alias = alias;
    }
    
    @Override
    public void setConnectedDataSources() throws Exception {
        connectedDataSources = new ReferedDataSource[1];
        connectedDataSources[0] = new ReferedDataSource();
        connectedDataSources[0].alias = alias;
    }
    
    
    
    @Override
     public String toString(){
         return "["+alias+"]";
     }
    
}
