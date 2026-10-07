package ibd.index.btree;

import engine.info.Parameters;
import java.util.Iterator;

public class SmallerKeyIterator implements Iterator<DictionaryPair> {

    DictionaryPair nextValue = null;
    LeafNode curNode;
    DictionaryPair dps[];
    BPlusTreeFile btree;
    Key key;
    int index;
    boolean smallerEqual = false;
    boolean foundEqual = false;

    public SmallerKeyIterator(BPlusTreeFile btree, Key key, boolean smallerEqual) {
        this.btree = btree;
        this.key = key;
        curNode = btree.getFirstPage();
        dps = curNode.dictionary;
        this.smallerEqual = smallerEqual;
        index = 0;
    }

    public DictionaryPair next() {

        if (nextValue != null) {
            DictionaryPair next_ = nextValue;
            nextValue = null;
            return next_;
        }

        return findNextTuple();

    }

    public boolean hasNext() {

        if (nextValue != null) {
            return true;
        }
        nextValue = findNextTuple();

        return (nextValue != null);
    }

    protected DictionaryPair findNextTuple() {

        while (curNode != null) {
            while (index < dps.length) {
                if (dps[index] == null) {
                    break;
                }
                Parameters.RECORDS_READ++;
                if (key.partialMatch(dps[index].key)) {
                    foundEqual = true;
                    if (smallerEqual) {
                        nextValue = dps[index];
                        index++;
                        return nextValue;
                    }
                    else return null;
                } else {
                    if (!foundEqual){
                        nextValue = dps[index];
                        index++;
                        return nextValue;
                    }
                }
                index++;
            }


            curNode = (LeafNode) btree.getNode(curNode.rightSiblingID);
            if (curNode != null) {
                dps = curNode.dictionary;
                index = 0;
            }
        }
        return null;

    }
}
