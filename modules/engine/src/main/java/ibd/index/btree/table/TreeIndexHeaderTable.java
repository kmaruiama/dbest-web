package ibd.index.btree.table;

import ibd.index.btree.TreeIndexHeader;
import java.nio.ByteBuffer;

import ibd.table.prototype.Prototype;
import ibd.table.prototype.column.BooleanColumn;
import ibd.table.prototype.column.Column;
import ibd.table.prototype.column.DoubleColumn;
import ibd.table.prototype.column.FloatColumn;
import ibd.table.prototype.column.IntegerColumn;
import ibd.table.prototype.column.LongColumn;
import ibd.table.prototype.column.StringColumn;

public class TreeIndexHeaderTable extends TreeIndexHeader {

    Prototype prototype;

    public TreeIndexHeaderTable(int pageSize, int rootID, int firstLeafID, Prototype prototype) {
        super(pageSize, rootID, firstLeafID);
        this.prototype = prototype;
    }

    @Override
    public void readHeader(ByteBuffer buffer) {
        super.readHeader(buffer);

        prototype = new Prototype();

        int numberOfColumns = buffer.getShort();
        for (int i = 0; i < numberOfColumns; i++) {
            String colType = readString(buffer);
            String colName = readString(buffer);
            int colSize = buffer.getInt();
            short colFlags = buffer.getShort();
            Column col = null;
            switch (colType) {
                case Column.STRING_TYPE:
                    col = new StringColumn(colName, colSize, colFlags);
                    break;
                case Column.INTEGER_TYPE:
                    col = new IntegerColumn(colName, colSize, colFlags);
                    break;
                case Column.LONG_TYPE:
                    col = new LongColumn(colName, colSize, colFlags);
                    break;
                case Column.FLOAT_TYPE:
                    col = new FloatColumn(colName, colSize, colFlags);
                    break;
                case Column.DOUBLE_TYPE:
                    col = new DoubleColumn(colName, colSize, colFlags);
                    break;
                case Column.BOOLEAN_TYPE:
                    col = new BooleanColumn(colName, colSize, colFlags);
                    break;
            }
            prototype.addColumn(col);
        }

    }

    private String readString(ByteBuffer buffer) {
        int stringLength = buffer.getInt();
        byte[] bytes = new byte[stringLength];
        buffer.get(bytes);
        return new String(bytes);
    }

    private void writeString(ByteBuffer buffer, String str) {

        buffer.putInt(str.length());
        buffer.put(str.getBytes());
    }

    @Override
    public void writeHeader(ByteBuffer buffer) {
        super.writeHeader(buffer);

        buffer.putShort(prototype.size());
        for (int i = 0; i < prototype.size(); i++) {
            Column col = prototype.getColumn(i);
            writeString(buffer, col.getType());
            writeString(buffer, col.getName());
            buffer.putInt(col.getSize());
            buffer.putShort(col.getFlags());
        }

    }

    @Override
    public int size() {
        return super.size() + 2000;
    }

}
