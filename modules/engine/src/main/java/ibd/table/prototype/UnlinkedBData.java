package ibd.table.prototype;

public class UnlinkedBData extends BData{
    byte[] data;
    
    public UnlinkedBData(byte[] data){
        super();
        this.data = data;
    }
    
    public UnlinkedBData(byte[] data, int offset, int length){
        super(offset, length);
        this.data = data;
    }

    @Override
    public byte[] getData() {
        return data;
    }
}
