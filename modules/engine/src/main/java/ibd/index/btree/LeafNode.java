package ibd.index.btree;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

public class LeafNode extends Node {

    int maxNumPairs;
    int minNumPairs;
    int numPairs;

    BPlusTree tree;

    LeafNode leftSibling;
    LeafNode rightSibling;
    int leftSiblingID = -1;
    int rightSiblingID = -1;

    DictionaryPair[] dictionary;

    public LeafNode(BPlusTree tree) {
        this.tree = tree;
    }

    public LeafNode(int m, DictionaryPair dp, BPlusTree tree) {
        this.maxNumPairs = m - 1;
        setMinNumPairs();
        this.dictionary = new DictionaryPair[m];
        this.numPairs = 0;
        this.tree = tree;
        this.insert(dp);
    }

    public LeafNode(int m, DictionaryPair[] dps, InternalNode parent) {
        this.maxNumPairs = m - 1;
        setMinNumPairs();
        this.dictionary = dps;
        this.numPairs = Utils.linearNullSearch(dps);
        this.setParentNode(parent);
    }

    private void setMinNumPairs() {
        this.minNumPairs = (int) (Math.ceil((maxNumPairs + 1) / 2) - 1);
    }

    public void print() {
        StringBuffer buf = new StringBuffer();
        buf.append("maxNumPairs " + maxNumPairs + ","
                + "minNumPairs " + minNumPairs + ","
                + "numPairs " + numPairs + ","
                + "leftSiblingID " + leftSiblingID + ","
                + "rightSiblingID " + rightSiblingID + ","
                + "parentID " + getParentID()
        );
        buf.append(" entries:");
        for (int i = 0; i < numPairs; i++) {
            buf.append(dictionary[i].key + ",");
        }
        System.out.println(buf.toString());
    }

    public DictionaryPair getFirstDictionaryPair() {
        return dictionary[0];
    }

    public DictionaryPair getLastDictionaryPair() {
        return dictionary[numPairs - 1];
    }

    public void deleteFirstDictionaryPair() {
        delete(0);
    }

    public void deleteLastDictionaryPair() {
        delete(numPairs - 1);
    }

    public void delete(int index) {

        this.dictionary[index] = null;

        numPairs--;

        shiftNullsToEnd();
    }

    public boolean insert(DictionaryPair dp) {
        if (this.isFull()) {

            return false;
        } else {

            insertAndShift(dp);
            return true;
        }
    }

    private void insertAndSort(DictionaryPair dp){
        this.dictionary[numPairs] = dp;
        numPairs++;
        Arrays.sort(this.dictionary, 0, numPairs);
    }
    
    private void insertAndShift(DictionaryPair dp) {
        int index = 0;
        while (index < numPairs && dictionary[index].compareTo(dp) < 0) {
            index++;
        }

        for (int i = numPairs; i > index; i--) {
            this.dictionary[i] = dictionary[i - 1];
        }

        this.dictionary[index] = dp;

        numPairs++;
    }

    public void prependPair(DictionaryPair dp) {
        for (int i = numPairs - 1; i >= 0; i--) {
            this.dictionary[i + 1] = this.dictionary[i];
        }
        this.dictionary[0] = dp;
        this.numPairs++;

    }

    public void prependPairs(LeafNode ln) {

        for (int i = numPairs - 1; i >= 0; i--) {
            this.dictionary[i + ln.numPairs] = this.dictionary[i];
        }

        for (int i = 0; i < ln.numPairs; i++) {
            this.dictionary[i] = ln.dictionary[i];
        }

        this.numPairs += ln.numPairs;

    }

    public void appendPairs(LeafNode ln) {

        for (int i = 0; i < ln.numPairs; i++) {
            this.dictionary[numPairs + i] = ln.dictionary[i];
        }

        this.numPairs += ln.numPairs;

    }

    public void appendPair(DictionaryPair dp) {
        this.dictionary[numPairs] = dp;
        this.numPairs++;

    }

    public boolean isDeficient() {
        return numPairs < minNumPairs;
    }

    public boolean isFull() {
        return numPairs == maxNumPairs;
    }

    public boolean isLendable() {
        return numPairs > minNumPairs;
    }

    public boolean isMergeable() {
        return numPairs <= minNumPairs;
    }

    public DictionaryPair[] splitDictionary(int split) {

        DictionaryPair[] halfDict = new DictionaryPair[maxNumPairs + 1];

        for (int i = split; i < dictionary.length; i++) {
            halfDict[i - split] = dictionary[i];
            this.dictionary[i] = null;
            numPairs--;
        }

        shiftNullsToEnd();

        return halfDict;
    }

    public void shiftNullsToEnd() {
        int nonNullIndex = 0;
        for (int currentIndex = 0; currentIndex < dictionary.length; currentIndex++) {
            if (dictionary[currentIndex] != null) {
                DictionaryPair temp = dictionary[currentIndex];
                dictionary[currentIndex] = dictionary[nonNullIndex];
                dictionary[nonNullIndex] = temp;
                nonNullIndex++;
            }
        }
    }

    private void sortDictionary() {
        Arrays.sort(dictionary, new Comparator<DictionaryPair>() {
            @Override
            public int compare(DictionaryPair o1, DictionaryPair o2) {
                if (o1 == null && o2 == null) {
                    return 0;
                }
                if (o1 == null) {
                    return 1;
                }
                if (o2 == null) {
                    return -1;
                }
                return o1.compareTo(o2);
            }
        });
    }

    public void sortDictionary(Key key, Value value) {

        DictionaryPair dp = new DictionaryPair(key, value, tree);
        insertAndSort(dp);
    }

    @Override
    public int getHeaderSizeInBytes() {
        return super.getHeaderSizeInBytes() + 5 * Integer.BYTES;
    }

    @Override
    public void writeExternal(DataOutput out) throws IOException {
        out.writeInt(maxNumPairs);
        out.writeInt(numPairs);
        out.writeInt(getParentID());
        out.writeInt(leftSiblingID);
        out.writeInt(rightSiblingID);
        for (int i = 0; i < numPairs; i++) {
            dictionary[i].writeExternal(out);
        }
    }

    @Override
    public void readExternal(DataInput in) throws IOException {
        this.maxNumPairs = in.readInt();
        this.numPairs = in.readInt();

        this.setParentID(in.readInt());
        this.leftSiblingID = in.readInt();
        this.rightSiblingID = in.readInt();

        setMinNumPairs();

        dictionary = new DictionaryPair[maxNumPairs + 1];
        for (int i = 0; i < numPairs; i++) {
            DictionaryPair dp = new DictionaryPair(tree);
            dp.readExternal(in);
            dictionary[i] = dp;
        }

    }

    public int findOutOptimalDegree(long pageSize, long keySize, long valueSize) {
        return (int) Math.floor((pageSize - getHeaderSizeInBytes()) / (keySize + valueSize));
    }

    @Override
    public String toString() {
        String aux = "";
        for (int i = 0; i < numPairs; i++) {
            aux += dictionary[i].key + "--";
        }

        return aux;
    }

}
