package ibd.query;

public class ColumnDescriptor {
    private String columnName;
    private String tableName;
    
    ColumnLocation columnLocation;
    
    public ColumnDescriptor(String columnName) throws Exception{
        setInfo(columnName);
    }
    
    public ColumnDescriptor(String tableName, String columnName){
        this.tableName = tableName;
        this.columnName = columnName;
    }
    
    public String getColumnName(){
        return columnName;
    }
    
    public String getTableName(){
        return tableName;
    }
    
    private void setInfo(String column) throws Exception{
    if (column.length()==0) throw new Exception("Column name was not provided");
    int index = column.indexOf('.');
    if (index==-1) {
        columnName = column;
        return;
    }
    if (index==0) throw new Exception("Table name was not provided");
    if (index==column.length()-1) throw new Exception("Column name was not provided");
    
    tableName = column.substring(0, index);
    columnName = column.substring(index+1, column.length());
    
    }
    
    public ColumnLocation getColumnLocation(){
        return columnLocation;
    }
    
    public void setColumnLocation(ColumnLocation cl){
        this.columnLocation = cl;
    }
   
    @Override
    public String toString(){
        if (tableName == null)
            return columnName;
        return tableName+"."+columnName;
    }
}
