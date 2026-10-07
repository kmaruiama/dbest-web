package ibd.table.prototype;

import ibd.table.prototype.column.Column;
import ibd.table.prototype.query.fields.Field;
import java.util.Map;

import java.util.Map.Entry;
import java.util.TreeMap;

public class BasicDataRow extends DataRow {

    protected Map<String, Field> data;

    public BasicDataRow() {
        data = new TreeMap<>();
    }
    
    @Override
    public int getFieldsSize() {
        return data.size();
    }

    @Override
    public Comparable getValue(String column) {
        if (!this.data.containsKey(column)) {
            return null;
        }
        return (Comparable) this.data.get(column).getValue();
    }
    
    

    @Override
    public Integer getInt(String column) {
        if (!this.data.containsKey(column)) {
            return null;
        }
        return this.data.get(column).getInt();
    }

    @Override
    public Long getLong(String column) {
        if (!this.data.containsKey(column)) {
            return null;
        }
        return this.data.get(column).getLong();
    }

    @Override
    public Float getFloat(String column) {
        if (!this.data.containsKey(column)) {
            return null;
        }
        return this.data.get(column).getFloat();
    }

    @Override
    public Double getDouble(String column) {
        if (!this.data.containsKey(column)) {
            return null;
        }
        return this.data.get(column).getDouble();
    }

    @Override
    public String getString(String column) {
        if (!this.data.containsKey(column)) {
            return null;
        }
        return this.data.get(column).getString();
    }

    @Override
    public Boolean getBoolean(String column) {
        if (!this.data.containsKey(column)) {
            return null;
        }
        return this.data.get(column).getBoolean();
    }

    @Override
    public Field getField(String column) {
        return this.data.get(column);
    }

    @Override
    public Field getField(int index) {
        return null;
    }

    @Override
    public void setField(String column, Field field) {
        if (field == null) {
            return;
        }
        if (!this.data.containsKey(column)) {
            fieldsSet++;
        }

        this.data.put(column, field);

    }
   
    @Override
    public boolean hasValue(String column) {
        Field f = this.data.get(column);
        return (f != null);
    }

    @Override
    public Field unset(String column) {
        Field f = this.data.get(column);
        setField(column, null);
        this.data.remove(column);
        return f;
    }

    public LinkedDataRow getLinkedDataRow(Prototype prototype) {
        LinkedDataRow ldr = new LinkedDataRow(prototype, false);

        for (String colName : data.keySet()) {

            Column col = prototype.getColumn(colName);
            if (col == null) {
                continue;
            }
            Field f = data.get(colName);
            addField(ldr, col, f);
        }
        return ldr;

    }
    private void addField(LinkedDataRow ldr, Column col, Field value) {
        switch (col.getType()) {
            case Column.BOOLEAN_TYPE -> {
                ldr.setBoolean(col.getName(), value.getBoolean());
                break;
            }
            case Column.DOUBLE_TYPE -> {
                ldr.setDouble(col.getName(), value.getDouble());
                break;
            }
            case Column.FLOAT_TYPE -> {
                ldr.setFloat(col.getName(), value.getFloat());
                break;
            }
            case Column.INTEGER_TYPE -> {
                ldr.setInt(col.getName(), value.getInt());
                break;
            }
            case Column.LONG_TYPE -> {
                ldr.setLong(col.getName(), value.getLong());
                break;
            }
            case Column.STRING_TYPE -> {
                ldr.setString(col.getName(), value.getString());
                break;
            }

        }
    }

    @Override
    public int compareTo(DataRow r) {
        int val;
        for (Map.Entry<String, Field> entry
                : data.entrySet()) {
            Field f = r.getField(entry.getKey());
            if (f == null) {
                continue;
            }
            val = entry.getValue().compareTo(f);
            if (val != 0) {
                return val;
            }
        }
        return fieldsSet() - r.fieldsSet();
    }

    @Override
    public int partialMatch(DataRow r) {
        int val = 0;
        for (Map.Entry<String, Field> entry
                : data.entrySet()) {
            Field f = r.getField(entry.getKey());
            if (f == null) {
                continue;
            }
            val = entry.getValue().compareTo(f);
            if (val != 0) {
                return val;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        String str = "row(";
        for (Entry<String, Field> entry : data.entrySet()) {
            str += entry.getKey() + ":" + entry.getValue().getComparable();
            str += ", ";
        }
      
        str += ")";
        return str;
    }

}
