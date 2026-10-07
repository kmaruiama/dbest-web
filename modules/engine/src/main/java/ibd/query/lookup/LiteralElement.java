package ibd.query.lookup;

import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;

public class LiteralElement extends Element{
    protected Comparable value;

    public LiteralElement(Comparable value){
        this.value = value;
    }
    
    @Override
    public Comparable getValue(Tuple tuple) {
        return value;
    }
    
    @Override
    public Comparable getValueFromRow(LinkedDataRow row){
        return value;
    }
    
    public void setValue(Comparable value) {
        this.value = value;
    }
    
    @Override
    public String toString(){
        return value.toString();
    }
}
