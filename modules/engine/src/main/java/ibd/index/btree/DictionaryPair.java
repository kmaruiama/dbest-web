package ibd.index.btree;

import ibd.persistent.ExternalizablePage;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class DictionaryPair implements Comparable<DictionaryPair>, ExternalizablePage {

    Key key;
    Value value;
    BPlusTree tree;

    public DictionaryPair(BPlusTree tree) {
        this.tree = tree;
    }
    
    public Key getKey(){
        return key;
    }
    
    public Value getValue(){
        return value;
    }
    
    
    public DictionaryPair(Key key, Value value, BPlusTree tree) {
        this.key = key;
        this.value = value;
        this.tree = tree;
    }

    
    @Override
    public int compareTo(DictionaryPair o) {
        return key.compareTo(o.key);
    }

    @Override
    public void writeExternal(DataOutput out) throws IOException {
        key.writeExternal(out);
        value.writeExternal(out);
    }

    @Override
    public void readExternal(DataInput in) throws IOException {
        
        key = tree.createKey();
        key.readExternal(in);
        value = tree.createValue();
        
        value.readExternal(in);
        
    }
    
    @Override
    public int getSizeInBytes(){
        return key.getSizeInBytes()+value.getSizeInBytes();
    }
    
    @Override
    public String toString(){
        return key.toString()+","+value.toString();
    }
    
}
