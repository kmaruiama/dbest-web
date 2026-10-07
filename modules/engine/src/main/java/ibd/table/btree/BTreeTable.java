package ibd.table.btree;

import ibd.index.btree.table.BPlusTreeFileTable;
import ibd.index.btree.DictionaryPair;
import ibd.index.btree.Key;
import ibd.index.btree.Value;
import ibd.index.btree.table.BinaryValue;
import ibd.persistent.PersistentPageFile;
import ibd.persistent.cache.Cache;
import ibd.query.lookup.LookupFilter;
import ibd.table.ComparisonTypes;
import ibd.table.Table;
import ibd.table.prototype.BasicDataRow;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import ibd.table.prototype.DataRow;
import ibd.table.prototype.Header;
import ibd.table.prototype.LinkedDataRow;

import java.util.ArrayList;
import java.util.List;
import ibd.table.prototype.Prototype;

public class BTreeTable extends Table {

    BPlusTreeFileTable tree = null;

    protected Cache cache = null;

    String folder;

    String name;

    int cacheSize;

    boolean loaded = false;

    public BTreeTable(Header header, String folder, String fileName, int cacheSize) throws Exception {

        super(header);
        if (header != null) {
            String path = header.getTablePath();

            Path filePath = Paths.get(path);
            Path folder_ = filePath.getParent();
            this.folder = ".";

            if (folder_ != null) {
                this.folder = folder_.toString();
            }

            this.name = filePath.getFileName().toString();
        } else {
            this.folder = folder;
            this.name = fileName;
        }

        this.cacheSize = cacheSize;

    }

    public BTreeTable(String path, int cacheSize) throws Exception {

        
        super(null);
        
        this.header = new Header(path);

        Path filePath = Paths.get(path);
        Path folder_ = filePath.getParent();
        this.folder = ".";

        if (folder_ != null) {
            this.folder = folder_.toString();
        }

        this.name = filePath.getFileName().toString();

        this.cacheSize = cacheSize;

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
    public void create(Prototype prototype, int pageSize) throws Exception {
        if (loaded) {
            return;
        }
        PersistentPageFile p = new PersistentPageFile(pageSize, Paths.get(folder + File.separator + name), true);
        open(p, prototype);
    }

    @Override
    public void open() throws Exception {
        if (loaded) {
            tree.reopen();
            return;
        }
        

        Path fileName = Paths.get(folder + File.separator + name);
        boolean exists = Files.exists(fileName);
        if (!exists) {
            throw new Exception("The file " + fileName + " does not exists");
        }
        PersistentPageFile p = new PersistentPageFile(-1, fileName, false);
        open(p, null);
    }

    private void open(PersistentPageFile p, Prototype prototype) throws Exception {

        if (cacheSize > 0) {
            cache = new ibd.persistent.cache.LRUCache(cacheSize);
            cache.setPageFile(p);
        }

        if (cache != null) {
            tree = new BPlusTreeFileTable(cache, prototype);
        } else {
            tree = new BPlusTreeFileTable(p, prototype);
        }
        tree.open();
        
        loaded = true;
    }

    @Override
    public void close() {
        tree.flush();
        tree.close();
    }

    @Override
    public void flushDB() throws Exception {
        tree.flush();
        if (cache != null) {
            cache.flush();
        }
    }

    @Override
    public LinkedDataRow addRecord(BasicDataRow dataRow, boolean unique) {
        LinkedDataRow linkedDataRow = dataRow.getLinkedDataRow(tree.prototype);

        DataRow pkRow = tree.prototype.createPKRow(linkedDataRow);
        Key key = tree.createKey();
        key.setKeys(new DataRow[]{pkRow});

        Value value = tree.createValue();
        byte bytes[] = tree.prototype.convertToArray(linkedDataRow);
        value.set(0, bytes);
        ((BinaryValue) value).rowData = linkedDataRow;

        boolean ok = tree.insert(key, value, unique);
        if (!ok) {
            return null;
        }

        return linkedDataRow;
    }

    @Override
    public LinkedDataRow updateRecord(BasicDataRow dataRow) {

        LinkedDataRow linkedDataRow = dataRow.getLinkedDataRow(tree.prototype);
        return updateRecord(linkedDataRow);
    }

    @Override
    public LinkedDataRow updateRecord(LinkedDataRow linkedDataRow) {

        DataRow pkRow = tree.prototype.createPKRow(linkedDataRow);
        Key key = tree.createKey();
        key.setKeys(new DataRow[]{pkRow});

        Value value = tree.createValue();
        byte bytes[] = tree.prototype.convertToArray(linkedDataRow);
        value.set(0, bytes);
        ((BinaryValue) value).rowData = linkedDataRow;

        Value v = tree.update(key, value);
        if (v == null) {
            return null;
        }

        return linkedDataRow;
    }

    @Override
    public LinkedDataRow removeRecord(BasicDataRow dataRow) {
        LinkedDataRow linkedDataRow = dataRow.getLinkedDataRow(tree.prototype);

        DataRow pkRow = tree.prototype.createPKRow(linkedDataRow);
        Key key = tree.createKey();
        key.setKeys(new DataRow[]{pkRow});

        Value value = tree.delete(key);
        if (value == null) {
            return null;
        }

        byte bytes_[] = (byte[]) value.get(0);
        LinkedDataRow dataRow1 = tree.prototype.convertBinaryToRowData(bytes_);

        return dataRow1;
    }

    @Override
    public List<LinkedDataRow> getAllRecords() throws Exception {
        List<DictionaryPair> values = tree.searchAll();
        List<LinkedDataRow> rows = new ArrayList();
        for (DictionaryPair value : values) {
            BinaryValue v = (BinaryValue) value.getValue();
            LinkedDataRow rowData = v.rowData;
            rows.add(rowData);
        }
        return rows;
    }

    @Override
    public AllRowsIterator getAllRecordsIterator() throws Exception {
        return new AllRowsIterator(tree);
    }

    @Override
    public LinkedDataRow getRecord(BasicDataRow dataRow) {
        LinkedDataRow linkedDataRow = dataRow.getLinkedDataRow(tree.prototype);

        LinkedDataRow pkRow = tree.prototype.createPKRow(linkedDataRow);

        return getRecord(pkRow);

    }

    @Override
    public LinkedDataRow getRecord(LinkedDataRow pkRow) {

        Key key = tree.createKey();
        key.setKeys(new DataRow[]{pkRow});

        Value v = tree.search(key);
        if (v == null) {
            return null;
        }

        LinkedDataRow dataRow = ((BinaryValue) v).rowData;

        return dataRow;
    }

    @Override
    public List<LinkedDataRow> getRecords(BasicDataRow dataRow) {

        LinkedDataRow linkedDataRow = dataRow.getLinkedDataRow(tree.prototype);

        LinkedDataRow pkRow = tree.prototype.createPKRow(linkedDataRow);

        return getRecords(pkRow);

    }

    @Override
    public List<LinkedDataRow> getRecords(LinkedDataRow pkRow) {

        Key key = tree.createKey();
        key.setKeys(new DataRow[]{pkRow});

        List<Value> values = tree.partialSearch(key);
        List<LinkedDataRow> rows = new ArrayList();
        for (Value value : values) {
            LinkedDataRow dataRow = ((BinaryValue) value).rowData;
            rows.add(dataRow);
        }
        return rows;

    }

    @Override
    public List<LinkedDataRow> getRecords(LinkedDataRow pkRow, LookupFilter rowFilter) {

        Key key = tree.createKey();
        key.setKeys(new DataRow[]{pkRow});

        List<Value> values = tree.partialSearch(key);
        List<LinkedDataRow> rows = new ArrayList();
        for (Value value : values) {
            LinkedDataRow dataRow = ((BinaryValue) value).rowData;
            if (rowFilter.match(dataRow)) {
                rows.add(dataRow);
            }
        }
        return rows;

    }

    @Override
    public boolean contains(LinkedDataRow pkRow) {

        Key key = tree.createKey();
        key.setKeys(new DataRow[]{pkRow});

        return tree.contains(key);

    }

    @Override
    public List<LinkedDataRow> getRecords(String col, Comparable comparable, int comparisonType) throws Exception {
        List<LinkedDataRow> rows = new ArrayList();
        List<LinkedDataRow> allRows = getAllRecords();
        for (LinkedDataRow row : allRows) {
            if (ComparisonTypes.match(row.getValue(col), comparable, comparisonType)) {
                rows.add(row);
            }
        }
        return rows;

    }

    @Override
    public List<LinkedDataRow> getFilteredRecords(LookupFilter filter) throws Exception {
        List<DictionaryPair> values = tree.searchAll();
        List<LinkedDataRow> rows = new ArrayList();
        for (DictionaryPair value : values) {
            BinaryValue v = (BinaryValue) value.getValue();
            LinkedDataRow dataRow = v.rowData;
            if (filter.match(dataRow)) {
                rows.add(dataRow);
            }
        }
        return rows;
    }

    @Override
    public FilteredRowsIterator getFilteredRecordsIterator(LookupFilter filter) throws Exception {
        return new FilteredRowsIterator(tree, filter);
    }

    @Override
    public RowsIterator getPKFilteredRecordsIterator(LinkedDataRow pkRow, LookupFilter rowFilter, int compType) throws Exception {
        Key key = tree.createKey();
        key.setKeys(new DataRow[]{pkRow});
        return new KeyFilteredRowsIterator(tree, key, rowFilter, compType);
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
        return tree.prototype;
    }

}
