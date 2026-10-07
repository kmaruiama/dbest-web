package ibd.index.btree;

import engine.info.Parameters;
import java.util.Iterator;

public class DifferentKeyIterator implements Iterator<DictionaryPair> {

    DictionaryPair nextValue = null;
    LeafNode curNode;
    DictionaryPair dps[];
    BPlusTreeFile btree;
    Key key;
    int index;

    public DifferentKeyIterator(BPlusTreeFile btree, Key key) {
        this.btree = btree;
        this.key = key;
        curNode = btree.getFirstPage();
        dps = curNode.dictionary;
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
                if (!(key.partialMatch(dps[index].key))) {
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
}
