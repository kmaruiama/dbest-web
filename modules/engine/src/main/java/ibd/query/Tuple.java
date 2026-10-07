package ibd.query;

import java.util.Arrays;
import ibd.table.prototype.DataRow;
import ibd.table.prototype.LinkedDataRow;
import ibd.table.prototype.query.fields.Field;
import java.util.ArrayList;
import java.util.List;

public class Tuple implements Comparable<Tuple> {

    public LinkedDataRow rows[];

    public void setSingleSourceRow(String dataSourceAlias, LinkedDataRow row) {
        rows = new LinkedDataRow[1];
        rows[0] = row;
    }

    public void setSourceRows(LinkedDataRow rows[]) {
        this.rows = rows;
    }

    public void setSourceRows(Tuple t) {
        rows = new LinkedDataRow[t.rows.length];
        int count = 0;
        for (LinkedDataRow sourceTuple : t.rows) {
            rows[count] = sourceTuple;
            count++;
        }

    }
    
    
    public void setSourceRows(List<Tuple> tuples) {
        List<LinkedDataRow> rowList = new ArrayList();
        for (Tuple tuple : tuples) {
            for (LinkedDataRow row : tuple.rows) {
                rowList.add(row);
            }
        }
        
        rows = new LinkedDataRow[rowList.size()];
        int count = 0;
        for (LinkedDataRow sourceTuple : rowList) {
            rows[count] = sourceTuple;
            count++;
        }

    }

    public void setSourceRows(Tuple t1, Tuple t2) {
        rows = new LinkedDataRow[t1.rows.length + t2.rows.length];
        int count = 0;
        for (LinkedDataRow sourceTuple : t1.rows) {
            rows[count] = sourceTuple;
            count++;
        }
        for (LinkedDataRow sourceTuple : t2.rows) {
            rows[count] = sourceTuple;
            count++;
        }

    }

    @Override
    public boolean equals(Object otherObject) {
        Tuple otherTuple = (Tuple) otherObject;
        if (rows.length != otherTuple.rows.length) {
            return false;
        }
        for (int i = 0; i < rows.length; i++) {
            if (rows[i].compareTo(otherTuple.rows[i]) != 0) {
                return false;
            }

        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Arrays.deepHashCode(this.rows);
        return hash;
    }

    public int size() {
        int size = 0;
        for (LinkedDataRow row : rows) {
            size+=row.getPrototype().getSizeInBytes();
        }
        return size;
    }

    @Override
    public String toString() {
        String str = new String();
        for (DataRow st : rows) {
            str += st.toString() + ", ";
        }
        return str;
    }

    @Override
    public int compareTo(Tuple t2) {
        for (int i = 0; i < rows.length; i++) {
            LinkedDataRow st1 = rows[i];
            LinkedDataRow st2 = t2.rows[i];
            for (int j = 0; j < st1.getFieldsSize(); j++) {
                Field f1 = st1.getField(j);
                Field f2 = st2.getField(j);
                int comp = f1.compareTo(f2);
                if (comp != 0) {
                    return comp;
                }
            }
        }
        return 0;
    }

}
