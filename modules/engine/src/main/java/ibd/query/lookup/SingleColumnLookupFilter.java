package ibd.query.lookup;

import ibd.query.ColumnDescriptor;
import ibd.query.Tuple;
import ibd.table.ComparisonTypes;
import ibd.table.prototype.LinkedDataRow;


public class SingleColumnLookupFilter implements LookupFilter{

   Element elem1;
    
    protected int comparisonType;
    
    Element elem2;
    
    public SingleColumnLookupFilter(Element element1, int comparisonType, Element element2) throws Exception{
        this.comparisonType = comparisonType;
        this.elem1 = element1;
        this.elem2 = element2;
    }
    
    
    
    public ColumnDescriptor getColumnDescriptor(){
        return ((ColumnElement)elem1).getColumnDescriptor();
    }
    
    public Element getFirstElement(){
        return elem1;
    }
    
    public Element getSecondElement(){
        return elem2;
    }
         
    
    public void setFirstElement(Element elem){
        this.elem1 = elem;
    }
    
    public void setSecondElement(Element elem){
        this.elem2 = elem;
    }
    
    public int getComparisonType() {
        return comparisonType;
    }
    
    public Element getComparedElement(){
        return elem2;
    }
    
    public Comparable getValue(Tuple tuple){
        return elem2.getValue(tuple);
    }
    
    public Comparable getValueFromRow(LinkedDataRow row){
        return elem2.getValueFromRow(row);
    }
    
    @Override
    public boolean match(Tuple tuple) {
        return ComparisonTypes.match(elem1.getValue(tuple), elem2.getValue(tuple), comparisonType);
    }
    
   @Override
    public boolean match(LinkedDataRow row){
        return ComparisonTypes.match(elem1.getValueFromRow(row), elem2.getValueFromRow(row), comparisonType);
    }
    
    @Override
    public String toString() {
        String compType = ComparisonTypes.getComparisonOperation(comparisonType);
        return elem1+compType+elem2;
    }
}
