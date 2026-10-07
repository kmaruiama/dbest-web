package ibd.query.binaryop.join;

import ibd.query.ColumnDescriptor;

public class JoinTerm {

    private final ColumnDescriptor leftColumn;
    private final ColumnDescriptor rightColumn;

    public JoinTerm(String leftTableAlias, String leftColumn, String rightTableAlias, String rightColumn) {
        this.leftColumn = new ColumnDescriptor(leftTableAlias, leftColumn);
        this.rightColumn = new ColumnDescriptor(rightTableAlias, rightColumn);
    }

    public JoinTerm(String leftColumn, String rightColumn) throws Exception {
        this.leftColumn = new ColumnDescriptor(leftColumn);
        this.rightColumn = new ColumnDescriptor(rightColumn);

    }

    public String getLeftTableAlias() {
        return leftColumn.getTableName();
    }

    public String getLeftColumn() {
        return leftColumn.getColumnName();
    }

    public int getLeftColumnIndex(){
        return leftColumn.getColumnLocation().colIndex; 
    }
    
    
    
    public int getRightColumnIndex(){
        return rightColumn.getColumnLocation().colIndex;
    }
    
    
    
    public ColumnDescriptor getLeftColumnDescriptor(){
        return leftColumn;
    }
    
    public ColumnDescriptor getRightColumnDescriptor(){
        return rightColumn;
    }
    
    
    public String getRightTableAlias() {
        return rightColumn.getTableName();
    }

    public String getRightColumn() {
        return rightColumn.getColumnName();
    }

    @Override
    public String toString(){
        return getLeftTableAlias()+"."+getLeftColumn()+"="+getRightTableAlias()+"."+getRightColumn();
    }

}
