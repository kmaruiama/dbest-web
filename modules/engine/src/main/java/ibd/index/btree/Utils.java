package ibd.index.btree;

import java.util.Arrays;
import java.util.Comparator;

public class Utils {
    
    
    public static int binarySearch(DictionaryPair[] dps, int numPairs, Key t, BPlusTree tree) {
        Comparator<DictionaryPair> c = new Comparator<DictionaryPair>() {
            @Override
            public int compare(DictionaryPair o1, DictionaryPair o2) {
                return o1.compareTo(o2);
            }
        };
        return Arrays.binarySearch(dps, 0, numPairs, new DictionaryPair(t, null, tree), c);
    }
    
    public static int linearNullSearch(DictionaryPair[] dps) {
        for (int i = 0; i < dps.length; i++) {
            if (dps[i] == null) {
                return i;
            }
        }
        return -1;
    }
    
    public static int linearNullSearch(Node[] pointers) {
        for (int i = 0; i < pointers.length; i++) {
            if (pointers[i] == null) {
                return i;
            }
        }
        return -1;
    }
    
    public static int linearNullSearch(Integer[] pointers) {
        for (int i = 0; i < pointers.length; i++) {
            if (pointers[i] == null) {
                return i;
            }
        }
        return -1;
    }
}


