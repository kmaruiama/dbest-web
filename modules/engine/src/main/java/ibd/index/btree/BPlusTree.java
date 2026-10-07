package ibd.index.btree;

import java.util.ArrayList;

public abstract class BPlusTree {

    protected int m;
    protected int leafM;
    
    public abstract Key createKey();
    
    public abstract Value createValue();
    
    public abstract Value delete(Key key);
    
    public abstract boolean insert(Key key, Value value, boolean unique);
    
    public abstract Value search(Key key);
    

    public abstract ArrayList<Value> search(Key lowerBound, Key upperBound);

    public abstract ArrayList<DictionaryPair> searchAll();
    
    public abstract ArrayList<Value> partialSearch(Key key);
    
    public abstract boolean contains(Key key);

    public abstract Value update(Key key, Value value);
    
    public abstract AllKeysIterator searchAllIterator();
    

    
}
