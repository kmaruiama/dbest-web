package ibd.index.btree;

import ibd.persistent.ExternalizablePage;
import java.util.Arrays;

public abstract class Key implements Comparable<Key>, ExternalizablePage {

    protected Comparable[] keys;


    public void setKeys(Comparable[] keys) {
        this.keys = keys;
    }

    public Comparable get(int index) {
        return keys[index];
    }

    public String getPartialKey(int parts) {
        String result = "";
        for (int i = 0; i < parts; i++) {
            result += keys[i] + ",";
        }

        return result;

    }

    @Override
    public boolean equals(Object o) {
        Key k1 = (Key) o;

        int res = this.compareTo(k1);
        return (res == 0);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + Arrays.deepHashCode(this.keys);
        return hash;
    }

    
    @Override
    public int compareTo(Key other) {
        Key key1 = (Key) other;
        
        int minLength = Math.min(keys.length, key1.keys.length);

        for (int i = 0; i < minLength; i++) {
            int res = (keys[i].compareTo(key1.keys[i]));
            if (res != 0) {
                return res;
            }
        }

        if (keys.length > key1.keys.length) {
            return 1;
        } else if (keys.length < key1.keys.length) {
            return -1;
        } else {
            return 0;
        }
        
    }
    
    public boolean partialMatch(Key otherKey) {
        int minLength = Math.min(keys.length, otherKey.keys.length);

        for (int i = 0; i < minLength; i++) {
            int res = keys[i].compareTo(otherKey.keys[i]);
            if (res != 0) {
                return false;
            }
        }
        
        
        return true;
    }

    
    @Override
    public String toString() {
        return getPartialKey(keys.length);
    }

    @Override
    public int getSizeInBytes() {
        return 0;
    }

}
