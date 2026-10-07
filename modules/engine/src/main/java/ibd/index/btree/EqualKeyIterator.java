package ibd.index.btree;

import engine.info.Parameters;
import java.util.Iterator;

public class EqualKeyIterator implements Iterator<DictionaryPair>{

    DictionaryPair nextValue = null;
    LeafNode curNode;
    DictionaryPair dps[];
    BPlusTreeFile btree;
    Key key;
    int index;
    
    public EqualKeyIterator(BPlusTreeFile btree,Key key){
        this.btree = btree;
        this.key = key;
        curNode = btree.getFirstPage(key);
        dps = curNode.dictionary;
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
                    nextValue = dps[index];
                    index++;
                    return nextValue;
                } 
                return null;

            }


            curNode = (LeafNode) btree.getNode(curNode.rightSiblingID);
            if (curNode!=null){
                dps = curNode.dictionary;
                index =0;
            }
        }
        return null;

    }
}