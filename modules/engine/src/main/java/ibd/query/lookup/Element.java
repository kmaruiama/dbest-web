package ibd.query.lookup;

import ibd.query.Tuple;
import ibd.table.prototype.LinkedDataRow;

public abstract class Element {
    public abstract Comparable getValue(Tuple tuple);
    public abstract Comparable getValueFromRow(LinkedDataRow row);
}
