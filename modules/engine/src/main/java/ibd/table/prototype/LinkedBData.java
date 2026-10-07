package ibd.table.prototype;

public class LinkedBData extends BData{
    LinkedDataRow row;
    
    public LinkedBData(LinkedDataRow row, int offset, int length){
        super(offset, length);
        this.row = row;
    }

    @Override
    public byte[] getData() {
        return row.data;
    }
}
