package ibd.index.btree;

import ibd.persistent.ExternalizablePage;
import java.util.Arrays;

public abstract class Value implements ExternalizablePage {

    protected Object[] objects;


    public Object get(int index) {
        return objects[index];
    }

    public void set(int index, Object value) {
        objects[index] = value;
    }

    public int size() {
        return objects.length;
    }

    @Override
    public String toString() {
        return Arrays.toString(objects);
    }

    public abstract int getSizeInBytes();

}
