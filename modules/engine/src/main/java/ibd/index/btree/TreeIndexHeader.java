package ibd.index.btree;

import java.nio.ByteBuffer;

import ibd.persistent.PageHeader;

public abstract class TreeIndexHeader extends PageHeader {

    private static int SIZE = 2 * Integer.BYTES;


    private int rootID = 0;

    private int firstLeafID = 0;

    public TreeIndexHeader(int pageSize) {
        super(pageSize);
    }

    public TreeIndexHeader(int pageSize, int rootID, int firstLeafID) {
        super(pageSize);
        this.rootID = rootID;
        this.firstLeafID = firstLeafID;
    }

    @Override
    public void readHeader(ByteBuffer buffer) {
        super.readHeader(buffer);
        this.rootID = buffer.getInt();
        this.firstLeafID = buffer.getInt();

    }

    @Override
    public void writeHeader(ByteBuffer buffer) {
        super.writeHeader(buffer);
        buffer.putInt(this.rootID);
        buffer.putInt(this.firstLeafID);
    }
   

    public int getRootID() {
        return rootID;
    }

    public int getFirstLeafID() {
        return firstLeafID;
    }

    public void setRootID(int rootID) {
        this.rootID = rootID;
    }

    public void setFirstLeafID(int firstLeafID) {
        this.firstLeafID = firstLeafID;
    }

    @Override
    public int size() {
        return super.size() + SIZE;
    }

}
