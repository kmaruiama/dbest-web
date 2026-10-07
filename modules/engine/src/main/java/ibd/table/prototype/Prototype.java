package ibd.table.prototype;

import ibd.exceptions.DataBaseException;
import ibd.query.Stats;
import ibd.table.prototype.column.BooleanColumn;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import ibd.table.prototype.column.Column;
import ibd.table.prototype.column.DoubleColumn;
import ibd.table.prototype.column.FloatColumn;
import ibd.table.prototype.column.IntegerColumn;
import ibd.table.prototype.column.LongColumn;
import ibd.table.prototype.column.StringColumn;
import ibd.table.prototype.query.fields.Field;
import ibd.table.util.UtilConversor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Prototype implements Iterable<Column> {

    public static final Long KB = (long) 1024;
    public static final Long MB = 1024 * KB;
    public static final Long GB = 1024 * MB;
    public static final Long TB = 1024 * GB;

    private final ArrayList<Column> columns;

    private final HashMap<String, Column> columnsDic;

    private boolean stat;

    private int headerSize = 0;

    private int primaryKeySize;

    private HashMap<Integer, Integer> headerPosition;

    public Prototype() {
        columns = new ArrayList<>();
        columnsDic = new HashMap<>();
        stat = true;
    }
    
    public Prototype(Prototype prot) {
		columns = new ArrayList<Column>();
                columnsDic = new HashMap<>();
		stat =prot.stat;
		for (Column c:
			 prot) {
			addColumn(c.getType(), c.getName(),(short)c.getSize(),c.getFlags());
		}
	}

    public void addColumn(Column c) {
        if (c == null) {
            throw new DataBaseException("Prototype->addColumn", "Column passada é nula");
        }
        columns.add(c);
        c.index = columns.size()-1;

        if (c.isDinamicSize()) {
            stat = false;
        }
        columnsDic.put(c.getName(), c);
    }

    public void replaceColumn(Column c) {
        if(!columnsDic.containsKey(c.getName())) {
            this.addColumn(c);
            return;
        }
        Column ori = columnsDic.get(c.getName());
        
        columns.set(ori.index, c);
        
        c.index = ori.index;
        columnsDic.put(c.getName(), c);
    }

    public void addColumn(String name, short size, short flags) {
        Column c = new Column(name, size, flags);
        addColumn(c);
    }
    
    public void addColumn(String type, String name, short size, short flags) {
        
        Column c;
        switch (type) {
            case Column.BOOLEAN_TYPE:
                c = new BooleanColumn(name, size, flags);
                break;
            case Column.DOUBLE_TYPE:
                c = new DoubleColumn(name, size, flags);
                break;
           case Column.FLOAT_TYPE:
                c = new FloatColumn(name, size, flags);
                break;
           case Column.STRING_TYPE:
                c = new StringColumn(name, size, flags);
                break;
           case Column.INTEGER_TYPE:
                c = new IntegerColumn(name, size, flags);
                break;
            case Column.LONG_TYPE:
                c = new LongColumn(name, size, flags);
                break;
            default:
                throw new AssertionError();
        }
        addColumn(c);
    }

    public boolean isStatic() {
        return stat;
    }

    public Column getColumn(int i) {
        try {
            return columns.get(i);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    public Column getColumn(String name) {
        return columnsDic.get(name);
    }

    public int getColumnIndex(String name) {
        return columnsDic.get(name).index;
    }

    public short size() {
        return (short) columns.size();
    }

    public void validateColumns() {
        DataBaseException ex = null;
        if (size() == 0) {
            String error = "Não é valido uma tabela com nenhuma coluna!";
            ex = new DataBaseException("Prototype->ValidateColumns", error);
            ex.addValidation("Min:1");
            throw ex;
        }
        for (short x = 0; x < size(); x++) {
            Column col = getColumn(x);
            int namelen = col.getName().length();
            if (namelen > 240 || namelen < 1) {
                String error = "Coluna " + x + " tem nome de tamanhos inválido!";
                ex = new DataBaseException("Prototype->ValidateColumns", error);
                ex.addValidation("Max:240");
                ex.addValidation("Min:1");
                throw ex;
            }
            if (col.isPrimaryKey() && col.canBeNull()) {
                String error = "Coluna " + col.getName() + " não pode ser nula e ao mesmo tempo ser primary key!";
                ex = new DataBaseException("Prototype->ValidateColumns", error);
                ex.addValidation("NULL not in PRIMARY KEY");
                throw ex;
            }
            if (col.isPrimaryKey() && col.isDinamicSize()) {
                String error = "Coluna " + col.getName() + " não pode ser dinamica ao mesmo tempo que é primary key!";
                ex = new DataBaseException("Prototype->ValidateColumns", error);
                ex.addValidation("DINAMIC not in PRIMARY KEY");
                throw ex;
            }
            for (short y = (short) (x + 1); y < size(); y++) {
                Column col2 = getColumn(y);
                if (col.getName().equalsIgnoreCase(col2.getName())) {
                    String error = "Coluna " + x + " e a coluna " + y + " tem nomes iguais!";
                    ex = new DataBaseException("Prototype->ValidateColumns", error);
                    throw ex;
                }
            }
        }

        columns.sort(new Comparator<Column>() {
            @Override
            public int compare(Column o1, Column o2) {
                if (o1.isPrimaryKey() && !o2.isPrimaryKey()) {
                    return -1;
                }
                if (!o1.isPrimaryKey() && o2.isPrimaryKey()) {
                    return 1;
                }
                if (o1.isPrimaryKey() && o2.isPrimaryKey()) {
                    return Integer.compare(columns.indexOf(o1), columns.indexOf(o2));
                } else {
                    return (o1.index - o2.index);
                }
            }
        });
        calculateSizes();

        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).index = i;
        }
    }

    @Override
    public Iterator<Column> iterator() {
        return columns.iterator();
    }

    public List<Column> getColumns() {
        return columns;
    }

    public int getSizeInBytes() {
        int size = Integer.BYTES;
        for (int i = 0; i < columns.size(); i++) {
            size += columns.get(i).getSizeinBytes();
        }
        return size;
    }

    public List<String> getPKColumns() {
        List<String> cols = new ArrayList();
        for (Column column : columns) {
            if (column.isPrimaryKey()) {
                cols.add(column.getName());
            }

        }
        return cols;
    }

    public List<String> pKPrefix(List<String> cols) {
        List<String> pkCols = new ArrayList();
        for (Column column : columns) {
            if (column.isPrimaryKey()) {
                if (!cols.contains(column.getName())) {
                    break;
                } else {
                    pkCols.add(column.getName());
                }
            } else {
                break;
            }

        }
        return pkCols;
    }

    private void calculateSizes() {
        int headerSizeAux = 1;
        this.headerPosition = new HashMap<>();
        int aux = 1;
        int sizePk = 0;
        for (Column c : columns) {
            if (c.canBeNull()) {
                this.headerPosition.put(columns.indexOf(c), (headerSizeAux - 1) * 8 + aux);
                aux++;
            }
            if (aux >= 8) {
                headerSizeAux++;
                aux = 0;
            }
            if (c.isPrimaryKey()) {
                sizePk += c.getSize();
            }
        }
        this.headerSize = headerSizeAux;
        primaryKeySize = sizePk;
    }

    public int maxRecordSize() {
        int size = headerSize;
        for (Column c : columns) {
            size += c.getSize();
            if (c.isDinamicSize()) {
                size += 4;
            }
        }
        return size;
    }

    public int getPrimaryKeySize() {
        return primaryKeySize;
    }

    private void validateRowData(DataRow dataRow) {
    }

    public synchronized DataRow convertBinaryToRowData(byte[] data, Map<String, Column> meta, boolean hasHeader, boolean onlyPrimaryKey) {
        LinkedDataRow row = new LinkedDataRow();
        row.data = data;

        int selecteds = 0;
        int offset = (hasHeader) ? this.headerSize : 0;

        byte[] header = new byte[headerSize];
        if (hasHeader) {
            System.arraycopy(data, 0, header, 0, this.headerSize);
        }

        int headerPointer = 1;

        for (Column c : columns) {
            if (meta != null && selecteds >= meta.size()) {
                break;
            }
            if (onlyPrimaryKey && !c.isPrimaryKey()) {
                break;
            }
            boolean checkColumn = meta == null || meta.containsKey(c.getName());
            if (checkColumn) {
                selecteds++;
            }
            if (c.canBeNull() && hasHeader) {
                try {
                    if ((header[headerPointer / 8] & (1 << headerPointer % 8)) != 0) {
                        continue;
                    }
                } finally {
                    headerPointer++;
                }
            }
            int size = c.getSize();
            if (c.isDinamicSize()) {
                size = UtilConversor.byteArrayToInt(Arrays.copyOfRange(data, offset, offset + 4));
                offset += 4;
                if (checkColumn) {
                    byte[] arr = Arrays.copyOfRange(data, offset, offset + size);
                    row.setField(c.getName(), Field.createField(c, new LinkedBData(row, offset, size)));
                }

            } else {
                if (checkColumn) {
                    byte[] arr = Arrays.copyOfRange(data, offset, offset + c.getSize());
                    row.setField(c.getName(), Field.createField(c, new LinkedBData(row, offset, c.getSize())));
                }
            }
            offset += size;
        }
        return row;
    }

    public synchronized LinkedDataRow convertBinaryToPKRowData_(byte[] data, Map<String, Column> meta, boolean hasHeader, boolean onlyPrimaryKey) {
        LinkedDataRow row = new LinkedDataRow(this, true);
        
        int selecteds = 0;
        int offset = (hasHeader) ? this.headerSize : 0;

        byte[] header = new byte[headerSize];
        if (hasHeader) {
            System.arraycopy(data, 0, header, 0, this.headerSize);
        }

        int headerPointer = 1;

        for (Column c : columns) {
            if (meta != null && selecteds >= meta.size()) {
                break;
            }
            if (onlyPrimaryKey && !c.isPrimaryKey()) {
                break;
            }
            boolean checkColumn = meta == null || meta.containsKey(c.getName());
            if (checkColumn) {
                selecteds++;
            }
            if (c.canBeNull() && hasHeader) {
                try {
                    if ((header[headerPointer / 8] & (1 << headerPointer % 8)) != 0) {
                        continue;
                    }
                } finally {
                    headerPointer++;
                }
            }
            int size = c.getSize();
            if (c.isDinamicSize()) {
                size = UtilConversor.byteArrayToInt(Arrays.copyOfRange(data, offset, offset + 4));
                offset += 4;
                if (checkColumn) {
                    byte[] arr = Arrays.copyOfRange(data, offset, offset + size);
                    row.setField(c.getName(), Field.createField2(c, new UnlinkedBData(arr)));
                }

            } else {
                if (checkColumn) {
                    byte[] arr = Arrays.copyOfRange(data, offset, offset + c.getSize());
                    row.setField(c.getName(), Field.createField2(c, new UnlinkedBData(arr)));
                }
            }
            offset += size;
        }
        return row;
    }
    
    public synchronized LinkedDataRow convertBinaryToPKRowData(byte[] data, Map<String, Column> meta, boolean hasHeader, boolean onlyPrimaryKey) {
        LinkedDataRow row = new LinkedDataRow(this, true);
        row.data = data;
        int selecteds = 0;
        int offset = (hasHeader) ? this.headerSize : 0;

        byte[] header = new byte[headerSize];
        if (hasHeader) {
            System.arraycopy(data, 0, header, 0, this.headerSize);
        }

        int headerPointer = 1;

        for (Column c : columns) {
            if (meta != null && selecteds >= meta.size()) {
                break;
            }
            if (onlyPrimaryKey && !c.isPrimaryKey()) {
                break;
            }
            boolean checkColumn = meta == null || meta.containsKey(c.getName());
            if (checkColumn) {
                selecteds++;
            }
            if (c.canBeNull() && hasHeader) {
                try {
                    if ((header[headerPointer / 8] & (1 << headerPointer % 8)) != 0) {
                        continue;
                    }
                } finally {
                    headerPointer++;
                }
            }
            int size = c.getSize();
            if (c.isDinamicSize()) {
                size = UtilConversor.byteArrayToInt(Arrays.copyOfRange(data, offset, offset + 4));
                offset += 4;
                if (checkColumn) {
                    row.setField(c.getName(), Field.createField(c, new LinkedBData(row, offset, size)));
                }

            } else {
                if (checkColumn) {
                    row.setField(c.getName(), Field.createField(c, new LinkedBData(row, offset, c.getSize())));
                }
            }
            offset += size;
        }
        return row;
    }

    public synchronized LinkedDataRow convertBinaryToRowDataX(byte[] data) {
        LinkedDataRow row = new LinkedDataRow(this, false);

        int offset = this.headerSize;


        for (Column c : columns) {
            if (!c.isPrimaryKey()) break;
            
            int newOffset = offset + c.getSize();
            {
                row.setField(c.index, Field.createField(c, new LinkedBData(row, offset, c.getSize())));
            }
            offset  = newOffset;
        }
        return row;
    }
    
    public synchronized LinkedDataRow convertBinaryToRowData(byte[] data) {
        LinkedDataRow row = new LinkedDataRow(this, false);
        row.setData(data);
        int offset = this.headerSize;

        for (Column c : columns) {
            if (c.canBeNull()) {
            }
            int size = c.getSize();
            if (c.isDinamicSize()) {
                size = UtilConversor.byteArrayToInt(data, offset);
                offset += 4;
                row.setField(c.index, Field.createField(c, new LinkedBData(row, offset, size)));

            } else {
                row.setField(c.index, Field.createField(c, new LinkedBData(row, offset, c.getSize())));
            }
            offset += size;
        }
        return row;
    }
    
    
    public synchronized LinkedDataRow convertBinaryToRowData_(byte[] data) {
        LinkedDataRow row = new LinkedDataRow(this, false);
        
        int offset = this.headerSize;

        byte[] header = new byte[headerSize];

        System.arraycopy(data, 0, header, 0, this.headerSize);

        int headerPointer = 1;

        for (Column c : columns) {
            if (c.canBeNull()) {
                try {
                    if ((header[headerPointer / 8] & (1 << headerPointer % 8)) != 0) {
                        continue;
                    }
                } finally {
                    headerPointer++;
                }
            }
            int size = c.getSize();
            if (c.isDinamicSize()) {
                size = UtilConversor.byteArrayToInt(Arrays.copyOfRange(data, offset, offset + 4));
                offset += 4;
                byte[] arr = Arrays.copyOfRange(data, offset, offset + size);
                row.setField(c.index, Field.createField2(c, new UnlinkedBData(arr)));

            } else {
                byte[] arr = Arrays.copyOfRange(data, offset, offset + c.getSize());
                row.setField(c.index, Field.createField2(c, new UnlinkedBData(arr)));
            }
            offset += size;
        }
        return row;
    }

    public byte[] convertToArray(LinkedDataRow dataRow) {
        this.validateRowData(dataRow);
        byte[] header = new byte[this.headerSize];
        ArrayList<byte[]> dados = new ArrayList<>();
        int size = this.headerSize;
        header[0] |= 1;
        for (Column c : columns) {
            byte[] data = dataRow.getData(c.getName());
            if (data == null) {
                if (c.canBeNull()) {
                    int posHeader = headerPosition.get(columns.indexOf(c));
                    header[posHeader / 8] |= 1 << (posHeader % 8);
                }
            } else {
                if (c.isDinamicSize()) {
                    ByteBuffer buff = ByteBuffer.allocate(4);
                    buff.order(ByteOrder.LITTLE_ENDIAN);
                    buff.putInt(data.length);
                    byte[] indice = buff.array();
                    dados.add(indice);
                    dados.add(data);
                    size += indice.length;
                    size += data.length;
                } else {
                    dados.add(data);
                    size += c.getSize();
                    if (data.length < c.getSize()) {
                        dados.add(new byte[c.getSize() - data.length]);
                    }
                }
            }
        }
        byte[] bufferRecord = new byte[size];
        int offset = this.headerSize;
        System.arraycopy(header, 0, bufferRecord, 0, header.length);
        for (byte[] data : dados) {
            System.arraycopy(data, 0, bufferRecord, offset, data.length);
            offset += data.length;
        }
        return bufferRecord;
    }

    public byte[] convertPrimaryKeyToByteArray(LinkedDataRow dataRow) {
        ArrayList<byte[]> dados = new ArrayList<>();
        int size = 0;
        for (Column c : columns) {
            if (!c.isPrimaryKey()) {
                continue;
            }
            byte[] data = dataRow.getData(c.getName());

            {
                dados.add(data);
                size += c.getSize();
                if (data.length < c.getSize()) {
                    dados.add(new byte[c.getSize() - data.length]);
                }
            }
        }
        byte[] bufferRecord = new byte[size];
        int offset = 0;
        for (byte[] data : dados) {
            System.arraycopy(data, 0, bufferRecord, offset, data.length);
            offset += data.length;
        }
        return bufferRecord;
    }

    public LinkedDataRow createPKRow(DataRow dataRow) {
        LinkedDataRow keyData = new LinkedDataRow(this, true);

        for (Column column : columns) {
            if (!dataRow.hasValue(column.getName())) {
                continue;
            }
            if (column.isPrimaryKey()) {
                switch (column.getType()) {
                    case "STRING":
                        keyData.setString(column.getName(), dataRow.getString(column.getName()));
                        break;
                    case "INTEGER":
                        keyData.setInt(column.getName(), dataRow.getInt(column.getName()));
                        break;
                    case "LONG":
                        keyData.setLong(column.getName(), dataRow.getLong(column.getName()));
                        break;
                    case "FLOAT":
                        keyData.setFloat(column.getName(), dataRow.getFloat(column.getName()));
                        break;
                    case "DOUBLE":
                        keyData.setDouble(column.getName(), dataRow.getDouble(column.getName()));
                        break;
                    case "BOOLEAN":
                        keyData.setBoolean(column.getName(), dataRow.getBoolean(column.getName()));
                        break;
                    default:
                        throw new AssertionError();
                }

            }
        }

        return keyData;
    }

    public static Column cloneColumn(Column col) {
        Column newCol;

        switch (col.getType()) {
            case "STRING":
                newCol = new StringColumn(col.getName(), col.getSize(), col.getFlags());
                break;
            case "INTEGER":
                newCol = new IntegerColumn(col.getName(), col.getSize(), col.getFlags());
                break;
            case "LONG":
                newCol = new LongColumn(col.getName(), col.getSize(), col.getFlags());
                break;
            case "FLOAT":
                newCol = new FloatColumn(col.getName(), col.getSize(), col.getFlags());
                break;
            case "DOUBLE":
                newCol = new DoubleColumn(col.getName(), col.getSize(), col.getFlags());
                break;
            case "BOOLEAN":
                newCol = new BooleanColumn(col.getName(), col.getSize(), col.getFlags());
                break;
            default:
                throw new AssertionError();
        }

        return newCol;
    }
}
