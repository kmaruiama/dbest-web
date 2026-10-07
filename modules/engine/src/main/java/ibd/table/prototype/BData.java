package ibd.table.prototype;

import ibd.table.util.UtilConversor;



public abstract class BData {
    private int offset;
    private int length;

    public BData(){
    }
    public BData(int offset, int length){
        this.offset = offset;
        this.length = length;
    }

        public abstract byte[] getData();
    
    public Integer getInt() {
        byte[] data = this.getData();
        if(data==null)return null;
        return UtilConversor.byteArrayToInt(data, offset);
    }
    
    public Long getLong() {
       byte[] data = this.getData();
        if(data==null)return null;
        return UtilConversor.byteArrayToLong(data, offset);
    }
    public Float getFloat() {
        byte[] data = this.getData();
        if(data==null)return null;
        return UtilConversor.byteArrayToFloat(data, offset);
    }
    public Double getDouble() {
        byte[] data = this.getData();
        if(data==null)return null;
        return UtilConversor.byteArrayToDouble(data, offset);
    }
    public String getString() {
        byte[] data = this.getData();
        if(data==null)return null;
        
        return UtilConversor.byteArrayToString(data, offset, length);
    }
    public Boolean getBoolean() {
        byte[] data = this.getData();
        if(data==null)return null;
        return data[0]!=0;
    }

    public int length(){
        return this.getData().length;
    }

}
