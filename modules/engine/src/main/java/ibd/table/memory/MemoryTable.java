package ibd.table.memory;

import engine.exceptions.DataBaseException;
import ibd.index.btree.Key;
import ibd.index.btree.Value;
import ibd.index.btree.table.BinaryKey;
import ibd.index.btree.table.BinaryValue;
import ibd.query.lookup.LookupFilter;
import ibd.table.ComparisonTypes;
import ibd.table.Table;
import ibd.table.prototype.BasicDataRow;
import ibd.table.prototype.Header;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.Prototype;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MemoryTable extends Table {

    String name;
    Prototype prototype;
    List<LinkedDataRow> rows = new ArrayList();

    public MemoryTable(Header header) throws Exception {

        super(header);
        this.name = header.get(Header.TABLE_NAME);
        create(header.getPrototype(), 0);
        prototype.validateColumns();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getHeaderName() {
        return header.getFileName();
    }

    @Override
    public final void create(Prototype prototype, int pageSize) throws Exception {
        this.prototype = prototype;
    }

    @Override
    public void open() throws Exception {

    }

    public Key createKey() {
        return new BinaryKey(prototype);
    }

    public Value createValue() {
        return new BinaryValue(prototype);
    }

    @Override
    public void close() {
    }

    @Override
    public void flushDB() throws Exception {
    }

    @Override
    public LinkedDataRow addRecord(BasicDataRow dataRow, boolean unique) {
        LinkedDataRow linkedDataRow = dataRow.getLinkedDataRow(prototype);

        rows.add(linkedDataRow);

        return linkedDataRow;
    }

    @Override
    public LinkedDataRow updateRecord(BasicDataRow dataRow) {
        throw new DataBaseException("CSVTable", "This type of table (CSVTable) is not writable");
    }

    @Override
    public LinkedDataRow updateRecord(LinkedDataRow linkedDataRow) {
        throw new DataBaseException("CSVTable", "This type of table (CSVTable) is not writable");
    }

    @Override
    public LinkedDataRow removeRecord(BasicDataRow dataRow) {
        throw new DataBaseException("CSVTable", "This type of table (CSVTable) is not writable");
    }

    @Override
    public List<LinkedDataRow> getAllRecords() throws Exception {
        return rows;
    }

    @Override
    public Iterator getAllRecordsIterator() throws Exception {
        return getAllRecords().iterator();
    }

    @Override
    public LinkedDataRow getRecord(BasicDataRow dataRow) throws Exception {
        LinkedDataRow linkedDataRow = dataRow.getLinkedDataRow(prototype);

        LinkedDataRow pkRow = prototype.createPKRow(linkedDataRow);

        return getRecord(pkRow);

    }

    @Override
    public LinkedDataRow getRecord(LinkedDataRow pkRow) throws Exception {

        Iterator<LinkedDataRow> it = getAllRecordsIterator();
        while (it.hasNext()){
            LinkedDataRow dataRow = it.next();
            if (dataRow.compareTo(pkRow)==0) return dataRow;
        }
        
        return null;
    }

    @Override
    public List<LinkedDataRow> getRecords(BasicDataRow dataRow) throws Exception{

        LinkedDataRow linkedDataRow = dataRow.getLinkedDataRow(prototype);

        LinkedDataRow pkRow = prototype.createPKRow(linkedDataRow);

        return getRecords(pkRow);

    }

    @Override
    public List<LinkedDataRow> getRecords(LinkedDataRow pkRow) throws Exception{

        List<LinkedDataRow> rows = new ArrayList();
        Iterator<LinkedDataRow> it = getAllRecordsIterator();
        while (it.hasNext()){
            LinkedDataRow dataRow = it.next();
            if (dataRow.compareTo(pkRow)==0) rows.add(dataRow);
        }
            
        return rows;

    }

    @Override
    public List<LinkedDataRow> getRecords(LinkedDataRow pkRow, LookupFilter rowFilter) throws Exception{

        List<LinkedDataRow> rows = new ArrayList();
        Iterator<LinkedDataRow> it = getAllRecordsIterator();
        while (it.hasNext()){
            LinkedDataRow dataRow = it.next();
            if (dataRow.compareTo(pkRow)==0) {
                if (rowFilter.match(dataRow)) {
                rows.add(dataRow);
            }
            }
        }
            
        return rows;


    }

    @Override
    public boolean contains(LinkedDataRow pkRow) {

        Iterator<LinkedDataRow> it;
        try {
            it = getAllRecordsIterator();
        } catch (Exception ex) {
            return false;
        }
        while (it.hasNext()){
            LinkedDataRow dataRow = it.next();
            if (dataRow.compareTo(pkRow)==0) return true;
        }
            
    return false;
    }

    @Override
    public List<LinkedDataRow> getRecords(String col, Comparable comparable, int comparisonType) throws Exception {
        
        List<LinkedDataRow> rows = new ArrayList();
        Iterator<LinkedDataRow> it = getAllRecordsIterator();
        while (it.hasNext()){
            LinkedDataRow dataRow = it.next();
            if (ComparisonTypes.match(dataRow.getValue(col), comparable, comparisonType)) {
                rows.add(dataRow);
            }
        }
            
        return rows;
        

    }

    @Override
    public List<LinkedDataRow> getFilteredRecords(LookupFilter filter) throws Exception {
        
        List<LinkedDataRow> rows = new ArrayList();
        Iterator<LinkedDataRow> it = getAllRecordsIterator();
        while (it.hasNext()){
            LinkedDataRow dataRow = it.next();
            if (filter.match(dataRow)) {
                rows.add(dataRow);
            }
        }
            
        return rows;
    }

    @Override
    public Iterator getFilteredRecordsIterator(LookupFilter filter) throws Exception {
        List<LinkedDataRow> list = getFilteredRecords(filter);
        return list.iterator();
    }

    

    @Override
    public void printStats() throws Exception {
    }

    @Override
    public int getRecordsAmount() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Prototype getPrototype() {
        return prototype;
    }
    
    @Override
    public Iterator getPKFilteredRecordsIterator(LinkedDataRow pkRow, LookupFilter rowFilter, int compType) throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

}
