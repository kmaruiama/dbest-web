package ibd.table.prototype;

import ibd.table.block.Block;
import ibd.table.prototype.column.Column;
import ibd.table.prototype.metadata.BooleanMetadata;
import ibd.table.prototype.metadata.DoubleMetadata;
import ibd.table.prototype.metadata.FloatMetadata;
import ibd.table.prototype.metadata.IntegerMetadata;
import ibd.table.prototype.metadata.LongMetadata;
import ibd.table.prototype.metadata.Metadata;
import ibd.table.prototype.metadata.StringMetadata;
import ibd.table.prototype.query.fields.BinaryField;
import ibd.table.prototype.query.fields.Field;

public abstract class DataRow implements Comparable<DataRow> {

    

    long len = 0;
    Block block;

    protected int fieldsSet = 0;

    public DataRow() {

    }

    public abstract int getFieldsSize();

    public abstract void setField(String column, Field field);
    
    public abstract Field unset(String column);

    public abstract Comparable getValue(String column);
    
    public abstract Field getField(String column);
    
    public abstract Field getField(int index);

    public abstract Integer getInt(String column);

    public abstract Long getLong(String column);

    public abstract Float getFloat(String column);

    public abstract Double getDouble(String column);

    public abstract String getString(String column);

    public abstract Boolean getBoolean(String column);
    
    public abstract int partialMatch(DataRow r);

    

    public int fieldsSet() {
        return fieldsSet;
    }

    public void setInt(String column, int data) {
        this.setField(column, Field.createField(IntegerMetadata.generic, data));
    }

    public void setLong(String column, long data) {
        this.setField(column, Field.createField(LongMetadata.generic, data));
    }

    public void setString(String column, String data) {
        this.setField(column, Field.createField(new StringMetadata((short) (data.length() + 1)), data));
    }

    public void setFloat(String column, float data) {
        this.setField(column, Field.createField(FloatMetadata.generic, data));
    }

    public void setDouble(String column, double data) {
        this.setField(column, Field.createField(DoubleMetadata.generic, data));
    }

    public void setBoolean(String column, boolean data) {
        this.setField(column, Field.createField(BooleanMetadata.generic, data));
    }
    
    public abstract boolean hasValue(String column);
    

    

    public int len() {
        return (int) this.len;

    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public Integer getBlockId() {
        return block.getPageID();
    }

}
