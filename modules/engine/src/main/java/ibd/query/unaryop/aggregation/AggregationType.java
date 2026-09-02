/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ibd.query.unaryop.aggregation;

import ibd.query.ColumnDescriptor;

/**
 *
 * @author ferna
 */
public class AggregationType {

    //defines the five types of aggregation
    public final static int MAX = 1;
    public final static int MIN = 2;
    public final static int COUNT = 3;
    public final static int AVG = 4;
    public final static int SUM = 5;
    public final static int FIRST = 6;
    public final static int LAST = 7;
    public final static int COUNT_ALL = 8;
    public final static int COUNT_NULL = 9;

    public ColumnDescriptor aggregateColumn;

    public String aggregateColumnName;

    //the aggregation type
    public int type;

    public AggregationType(String col, int type) throws Exception {
        this.type = type;
        aggregateColumn = new ColumnDescriptor(col);
        aggregateColumnName = getStringType() + "_" + aggregateColumn.getColumnName();
    }

    public AggregationType(String table, String col, int type) {
        this.type = type;
        aggregateColumn = new ColumnDescriptor(table, col);
        aggregateColumnName = getStringType() + "_" + aggregateColumn.getColumnName();
    }

    public int getAgregationType() {
        return type;
    }

    private String getStringType() {
        return switch (type) {
            case AVG ->
                "AVG";
            case MIN ->
                "MIN";
            case MAX ->
                "MAX";
            case COUNT ->
                "COUNT";
            case SUM ->
                "SUM";
            case FIRST ->
                "FIRST";
            case LAST ->
                "LAST";
            case COUNT_ALL ->
                "COUNT_ALL";
            case COUNT_NULL ->
                "COUNT_NULL";
            
            default ->
                "";
        };
    }

    @Override
    public String toString() {
        return getStringType() + "_" + aggregateColumn;
    }
}
