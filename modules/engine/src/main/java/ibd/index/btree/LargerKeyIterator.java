package ibd.index.btree;

import engine.info.Parameters;
import java.util.Iterator;

public class LargerKeyIterator implements Iterator<DictionaryPair> {

    DictionaryPair nextValue = null;
    LeafNode curNode;
    DictionaryPair dps[];
    BPlusTreeFile btree;
    Key key;
    int index;
    boolean largerEqual = false;
    boolean foundEqual = false;
    boolean foundGreater = false;

    public LargerKeyIterator(BPlusTreeFile btree, Key key, boolean largerEqual) {
        this.btree = btree;
        this.key = key;
        curNode = btree.getFirstPage(key);
        dps = curNode.dictionary;
        this.largerEqual = largerEqual;
        index = Utils.binarySearch(dps, curNode.numPairs, key, btree);

        if (index < 0) {
            index = ~index;
        }
        else {
            while (index > 0 && dps[index - 1].key.compareTo(key) == 0) {
                index--;
            }
        }
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
                    if (largerEqual) {
                        nextValue = dps[index];
                        index++;
                        return nextValue;
                    }
                } else {
                    nextValue = dps[index];
                    index++;
                    return nextValue;
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

    protected DictionaryPair findNextTuple2() {

        while (curNode != null) {
            while (index < dps.length) {
                if (dps[index] == null) {
                    break;
                }
                Parameters.RECORDS_READ++;
                if (foundGreater) {
                    nextValue = dps[index];
                    index++;
                    return nextValue;
                } else if (foundEqual) {
                    if (largerEqual) {
                        nextValue = dps[index];
                        index++;
                        return nextValue;
                    }
                } else if (key.partialMatch(dps[index].key)) {
                    foundEqual = true;
                    if (largerEqual) {
                        nextValue = dps[index];
                        index++;
                        return nextValue;
                    }
                } else {
                    if (foundEqual) {
                        foundGreater = true;
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
