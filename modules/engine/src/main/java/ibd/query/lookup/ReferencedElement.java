package ibd.query.lookup;

import ibd.query.ColumnDescriptor;
import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;
import java.util.List;

public class ReferencedElement extends ColumnElement{
    protected Comparable value;
    
    public ReferencedElement(String valueCol) throws Exception{
        super(valueCol);
    }
    
    public ReferencedElement(ColumnDescriptor columnDescriptor) throws Exception{
        super(columnDescriptor);
    }
    
    public void setValue(List<Tuple> processedTuples) {
        value = processedTuples.get(columnDescriptor.getColumnLocation().tupleIndex).rows[columnDescriptor.getColumnLocation().rowIndex].getValue(columnDescriptor.getColumnLocation().colIndex);
    }
    
    
    
    @Override
    public Comparable getValue(Tuple tuple) {
        return value;
    }
    public Comparable getValueFromRow(LinkedDataRow row){
        return value;
    }
    
}
