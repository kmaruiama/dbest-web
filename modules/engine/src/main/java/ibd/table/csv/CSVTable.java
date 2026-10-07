package ibd.table.csv;

import engine.exceptions.DataBaseException;
import ibd.query.lookup.LookupFilter;
import ibd.table.ComparisonTypes;
import ibd.table.Table;
import ibd.table.prototype.BasicDataRow;
import ibd.table.prototype.Header;
import ibd.table.prototype.LinkedDataRow;

import java.util.ArrayList;
import java.util.List;
import ibd.table.prototype.Prototype;
import ibd.table.prototype.column.Column;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import sources.csv.CSVRecognizer;
import sources.csv.InvalidCsvException;

public class CSVTable extends Table {

    public CSVRecognizer recognizer;
    public char separator, stringDelimiter;
    public int beginIndex;
    
    private String name;


    private static Header prepareStuff(Header header){
        return header;
    }

public CSVTable(Header header) {
        super(prepareStuff(header));
        this.beginIndex = Integer.parseInt(header.get("beginIndex"));
        this.separator = header.get("separator").charAt(0);
        this.stringDelimiter = header.get("delimiter").charAt(0);
        
        String path = header.getTablePath();

            Path filePath = Paths.get(path);
            this.name = filePath.getFileName().toString();
    }
    public CSVTable(Header header,char separator, char stringDelimiter, int beginIndex) {
        super(prepareStuff(header));
        header.set(Header.TABLE_TYPE,"CSVTable");
        header.set("separator",separator+"");
        header.set("delimiter",stringDelimiter+"");
        header.set("beginIndex",beginIndex+"");
        this.beginIndex = beginIndex;
        this.separator = separator;
        this.stringDelimiter = stringDelimiter;
    }

    @Override
    public void create(Prototype prototype, int pageSize) throws Exception {
    }

    @Override
    public void open() throws Exception {
        try {
            this.recognizer = new CSVRecognizer(header.getTablePath(), separator, stringDelimiter, beginIndex);
        } catch (InvalidCsvException e) {
            throw new DataBaseException("CSVTable->Constructor", e.getMessage());
        }
    }

    @Override
    public void close() {
        this.recognizer = null;
    }

    @Override
    public void flushDB() throws Exception {
    }

    @Override
    public LinkedDataRow addRecord(BasicDataRow dataRow, boolean unique) {
        throw new DataBaseException("CSVTable", "This type of table (CSVTable) is not writable");
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
        List<LinkedDataRow> rows = new ArrayList();
        Iterator<String[]> csvLines = recognizer.iterator();
        long pk = 0L;
        while (csvLines.hasNext()) {
            String[] data = csvLines.next();
            if (data == null) {
                return null;
            }
            pk++;

            String[] columns = recognizer.getColumnNames();
            LinkedDataRow dataRow = convertRow(columns, data, pk);

            rows.add(dataRow);
        }

        return rows;
    }

    public LinkedDataRow convertRow(String[] columns, String[] data, long pk) {
        LinkedDataRow dataRow = new LinkedDataRow(prototype, false);
        for (Column c
                : prototype.getColumns()) {
            for (int x = 0; x < columns.length; x++) {
                String columnName = columns[x];
                if (c.getName().compareToIgnoreCase(columnName) != 0) {
                    continue;
                }
                String val = data[x];
                boolean isNull = (val == null || val.compareToIgnoreCase("null") == 0 || val.isEmpty() || val.strip().isEmpty());

                switch (c.getType()) {
                    case Column.STRING_TYPE -> {
                        if (!isNull) 
                        {
                            dataRow.setString(c.getName(), val);
                        }
                        else dataRow.setString(c.getName(), "");
                    }
                    case Column.INTEGER_TYPE -> {
                        if (!isNull) {
                            dataRow.setInt(c.getName(), Integer.parseInt(val));
                        }
                        else dataRow.setInt(c.getName(),Integer.MIN_VALUE);
                    }
                    case Column.LONG_TYPE -> {
                        if (!isNull) {
                            dataRow.setLong(c.getName(), Long.parseLong(val));
                        }
                        else dataRow.setLong(c.getName(),Long.MIN_VALUE);
                    }
                    case Column.DOUBLE_TYPE -> {
                        if (!isNull) {
                            dataRow.setDouble(c.getName(), Double.parseDouble(val));
                        }
                        else dataRow.setDouble(c.getName(),Double.MIN_VALUE);
                    }
                    case Column.FLOAT_TYPE -> {
                        if (!isNull) {
                            dataRow.setFloat(c.getName(), Float.parseFloat(val));
                        }
                        else dataRow.setFloat(c.getName(),Float.MIN_VALUE);
                    }
                    case Column.BOOLEAN_TYPE -> {
                        if (!isNull) {
                            dataRow.setBoolean(c.getName(), Boolean.parseBoolean(val));
                        }
                        else dataRow.setBoolean(c.getName(), false);
                    }
                }

            }
        }
        return dataRow;
    }

    @Override
    public Iterator getAllRecordsIterator() throws Exception {
        return new AllCSVRowsIterator(this);
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
        return new FilteredCSVRowsIterator(this, filter);
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

    public Iterator getPKFilteredRecordsIterator(LinkedDataRow pkRow, LookupFilter rowFilter) throws Exception {
        return new KeyFilteredCSVRowsIterator1(this, pkRow, rowFilter);
    }

    @Override
    public String getName() {
        return getName();
    }

    @Override
    public String getHeaderName() {
        return header.getFileName();
    }
    @Override
    public Iterator getPKFilteredRecordsIterator(LinkedDataRow pkRow, LookupFilter rowFilter, int compType) throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

}
