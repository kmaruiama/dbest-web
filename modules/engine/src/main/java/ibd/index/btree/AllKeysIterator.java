package ibd.index.btree;

import engine.info.Parameters;
import java.util.Iterator;

public class AllKeysIterator implements Iterator<DictionaryPair>{

    DictionaryPair nextValue = null;
    LeafNode curNode;
    DictionaryPair dps[];
    BPlusTreeFile btree;

    int index;

    int endLeafID = -1;
    
    public AllKeysIterator(BPlusTreeFile btree){
        this.btree = btree;
        curNode = (LeafNode) btree.getNode(btree.getFirstLeafID());
        dps = curNode.dictionary;
        index = 0;
    }

    public AllKeysIterator(BPlusTreeFile btree, int firstLeafID, int endLeafID){
        this.btree = btree;
        this.endLeafID = endLeafID;
        if (firstLeafID != endLeafID) {
            curNode = (LeafNode) btree.getNode(firstLeafID);
        }
        if (curNode != null) {
            dps = curNode.dictionary;
        }
        index = 0;
    }

    @Override
    public DictionaryPair next() {

        if (nextValue != null) {
            DictionaryPair next_ = nextValue;
            nextValue = null;
            return next_;
        }

        return findNextTuple();

    }

    @Override
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
                Parameters.RECORDS_READ.increment();
                nextValue = dps[index];
                index++;
                return nextValue;

            }


            if (curNode.rightSiblingID == endLeafID) {
                curNode = null;
            } else {
                curNode = (LeafNode) btree.getNode(curNode.rightSiblingID);
            }
            if (curNode!=null){
                dps = curNode.dictionary;
                index =0;
            }
        }
        return null;

    }
}