package ibd.index.btree;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class InternalNode extends Node  {

    int maxDegree;
    int minDegree;
    int degree;
    
    BPlusTree tree;
    
    InternalNode leftSibling;
    InternalNode rightSibling;

    int leftSiblingID = -1;
    int rightSiblingID = -1;

    Key[] keys;
    Node[] childPointers;
    Integer[] childPointersIDs;

    public InternalNode(BPlusTree tree) {
        this.tree = tree;
    }

    public InternalNode(int m, Key[] keys, BPlusTree tree) {
        this.maxDegree = m;
        setMinDegree();
        this.degree = 0;
        this.keys = keys;
        this.tree = tree;
        this.childPointers = new Node[this.maxDegree + 1];
        this.childPointersIDs = new Integer[this.maxDegree + 1];

    }

    public InternalNode(int m, Key[] keys, Node[] pointers, BPlusTree tree) {
        this.maxDegree = m;
        setMinDegree();
        this.degree = Utils.linearNullSearch(pointers);
        this.keys = keys;
        this.tree = tree;
        this.childPointers = pointers;
        this.childPointersIDs = new Integer[this.maxDegree + 1];
        for (int i = 0; i < pointers.length; i++) {
            Node node = pointers[i];
            if (node == null) {
                break;
            }
            childPointersIDs[i] = node.getPageID();
        }

    }

    public InternalNode(int m, Key[] keys, Integer[] pointers, BPlusTree tree) {
        this.maxDegree = m;
        setMinDegree();
        this.degree = Utils.linearNullSearch(pointers);
        this.keys = keys;
        this.tree = tree;
        this.childPointers = new Node[pointers.length];
        this.childPointersIDs = pointers;


    }

    private void setMinDegree() {
        this.minDegree = (int) Math.ceil(maxDegree / 2.0);
    }

    public void appendChildPointer(Node pointer) {
        this.childPointers[degree] = pointer;
        this.childPointersIDs[degree] = pointer.getPageID();
        pointer.setParentNode(this);
        pointer.setParentID(this.getPageID());
        
        this.degree++;
    }
    
    
    public void appendAllEntries(InternalNode in){
    appendChildPointer(in.childPointers[0]);
            for (int i = 1; i < in.degree; i++) {
                    appendKey(in.keys[i-1]);
                    appendChildPointer(in.childPointers[i]);
            }
    }

    public int findIndexOfPointer(Node pointer) {
        for (int i = 0; i < childPointersIDs.length; i++) {
            if (childPointersIDs[i] == pointer.getPageID()) {
                return i;
            }
        }
        return -1;
    }

    public void insertChildPointer(Node pointer, int index) {
        for (int i = degree - 1; i >= index; i--) {
            childPointers[i + 1] = childPointers[i];
            childPointersIDs[i + 1] = childPointersIDs[i];
        }
        this.childPointers[index] = pointer;
        this.childPointersIDs[index] = pointer.getPageID();
        pointer.setParentNode(this);
        this.degree++;
    }

    public boolean isDeficient() {
        return this.degree < this.minDegree;
    }

    public boolean isLendable() {
        return this.degree > this.minDegree;
    }

    public boolean isMergeable() {
        return this.degree == this.minDegree;
    }

    public boolean isOverfull() {
        return this.degree == maxDegree + 1;
    }

    
    
    public void prependChildPointer(Node pointer) {
        for (int i = degree - 1; i >= 0; i--) {
            childPointers[i + 1] = childPointers[i];
            childPointersIDs[i + 1] = childPointersIDs[i];
        }
        this.childPointers[0] = pointer;
        this.childPointersIDs[0] = pointer.getPageID();
        this.degree++;
        pointer.setParentNode(this);
        pointer.setParentID(this.getPageID());
    }
    
    
    public void prependAllEntries(InternalNode in) {
    
        int nkeys = degree;
        for (int i = nkeys-1; i >= 0; i--) {
            this.keys[i+in.degree-1] = this.keys[i];
        }
        for (int i = degree-1; i >= 0; i--) {
            this.childPointers[i+in.degree] = this.childPointers[i];
            this.childPointersIDs[i+in.degree] = this.childPointersIDs[i];
        }
        
        int i = 0;
        for (i = 0; i <in.degree - 1; i++) {
            this.setPointer(i, in.childPointers[i]);
            this.setKey(i, in.keys[i]);
        }
        this.setPointer(i, in.childPointers[i]);
        this.degree+=in.degree;
        
    }
    
    public void prependAllEntries1(InternalNode in) {
        prependChildPointer(in.childPointers[in.degree-1]);
        for (int i = in.degree - 2; i >= 0; i--) {
            prependChildKey(in.keys[i]);
            prependChildPointer(in.childPointers[i]);
        }
    }

    
    public void prependChildKey(Key key) {
        for (int i = keys.length - 2; i >= 0; i--) {
            keys[i + 1] = keys[i];
        }
        this.keys[0] = key;
    }

    public void removeKey(int index) {
        this.keys[index] = null;
    }
    
    
    public void removeEntry(int index) {
        removeKey(index - 1);
        removePointer(index);
        shiftNullKeysToEnd(index-1);
        shiftNullNodesToEnd(index);
    }
    
    public void removeEntry1(int index) {
        removePointer(index);
        removeKey(index);
        shiftNullKeysToEnd(index);
        shiftNullNodesToEnd(index);
    }

    public void removeLastEntry(){
        removeEntry(degree-1);
    }

    
    public void removePointer(int index) {
        this.childPointers[index] = null;
        this.childPointersIDs[index] = null;
        this.degree--;
    }

    
    

    public void removePointer(Integer pointerID) {
        for (int i = 0; i < degree; i++) {
            if (childPointersIDs[i].equals(pointerID)) {
                this.childPointers[i] = null;
                this.childPointersIDs[i] = null;
            }
        }
        this.degree--;
    }

    public void setKey(int index, Key key) {
        keys[index] = key;
    }

    public void addKey(Key key) {
        insertAndShift(key);
    }
    
    private void insertAndShift(Key key) {
        int index = 0;
        while (index < degree-1 && keys[index].compareTo(key) < 0) {
            index++;
        }

        for (int i = degree-1; i > index; i--) {
            this.keys[i] = keys[i - 1];
        }

        this.keys[index] = key;

    }
    
    

    public void setPointer(int index, Node node) {
        childPointers[index] = node;
        childPointersIDs[index] = node.getPageID();
        node.setParentNode(this);
    }
    
    public void appendKey(Key key) {
        setKey(degree - 1, key);
    }

    public Key getLastKey() {
        return keys[degree - 2];
    }
    
    public Key getFirstKey() {
        return keys[0];
    }

    public Integer[] splitChildPointersID(int split) {

        Integer[] pointersID = childPointersIDs;
        Integer[] halfPointers = new Integer[maxDegree + 1];

        for (int i = split + 1; i < pointersID.length; i++) {
            halfPointers[i - split - 1] = pointersID[i];
            removePointer(i);
        }

        degree = Utils.linearNullSearch(childPointersIDs);

        return halfPointers;
    }

    public Node getFirstPointer() {
        return childPointers[0];
    }
    
    public Integer getFirstPointerID() {
        return childPointersIDs[0];
    }

    public Node getLastPointer() {
        return childPointers[degree - 1];
    }
    
    public Integer getLastPointerID() {
        return childPointersIDs[degree - 1];
    }

    public Node[] splitChildPointers(int split) {

        Node[] pointers = childPointers;
        Node[] halfPointers = new Node[maxDegree + 1];

        for (int i = split + 1; i < pointers.length; i++) {
            halfPointers[i - split - 1] = pointers[i];
            removePointer(i);
        }

        degree = Utils.linearNullSearch(childPointers);

        return halfPointers;
    }

    @Override
    public int getHeaderSizeInBytes(){
        return super.getHeaderSizeInBytes() + 5 * Integer.BYTES;
    }
    
    
    @Override
    public void writeExternal(DataOutput out) throws IOException {
        out.writeInt(maxDegree);
        out.writeInt(degree);
        out.writeInt(getParentID());
        out.writeInt(leftSiblingID);
        out.writeInt(rightSiblingID);
        
        for (int i = 0; i < degree - 1; i++) {
            keys[i].writeExternal(out);
        }
        
        for (int i = 0; i < degree; i++) {
            out.writeInt(childPointersIDs[i]);
        }
    }

    @Override
    public void readExternal(DataInput in) throws IOException {
        this.maxDegree = in.readInt();
        this.degree = in.readInt();
        this.setParentID(in.readInt());
        this.leftSiblingID = in.readInt();
        this.rightSiblingID = in.readInt();

        this.setMinDegree();

        keys = new Key[maxDegree];
        for (int i = 0; i < degree - 1; i++) {
            Key key = tree.createKey();
            key.readExternal(in);
            keys[i] = key;
        }

        childPointers = new Node[maxDegree + 1];
        childPointersIDs = new Integer[maxDegree + 1];
        for (int i = 0; i < degree; i++) {
            childPointersIDs[i] = in.readInt();
        }
    }

    
    
    public int findOutOptimalDegree(long pageSize, long keySize) {
        return (int) Math.floor((pageSize - getHeaderSizeInBytes()- Integer.BYTES) / (keySize+Integer.BYTES));
    }

            
    
    @Override
    public String toString() {
        String aux = "";
        for (Key key : keys) {
            if (key != null) {
                aux += key.toString() + "--";
            }
        }
        return aux;
    }

    
    public void shiftNullNodesToEnd(int index) {
        int nonNullIndex = index;
        for (int currentIndex = index; currentIndex < childPointersIDs.length; currentIndex++) {
            if (childPointersIDs[currentIndex] != null) {
                Node temp = childPointers[currentIndex];
                childPointers[currentIndex] = childPointers[nonNullIndex];
                childPointers[nonNullIndex] = temp;
                
                Integer temp_ = childPointersIDs[currentIndex];
                childPointersIDs[currentIndex] = childPointersIDs[nonNullIndex];
                childPointersIDs[nonNullIndex] = temp_;
                
                nonNullIndex++;
            }
        }
    }

    public void shiftNullKeysToEnd(int index) {
        int nonNullIndex = index;
        for (int currentIndex = index; currentIndex < keys.length; currentIndex++) {
            if (keys[currentIndex] != null) {
                Key temp = keys[currentIndex];
                keys[currentIndex] = keys[nonNullIndex];
                keys[nonNullIndex] = temp;
                nonNullIndex++;
            }
        }
    }

}
