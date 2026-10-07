package ibd.index.btree;

import ibd.persistent.AbstractExternalizablePage;
import ibd.persistent.Page;
import ibd.persistent.PageFile;
import ibd.persistent.PageSerialization;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class BPlusTreeFile extends BPlusTree implements PageSerialization {

    protected int rootID = -1;
    protected int firstLeafID = -1;

    private static final int EMPTY_PAGE = 0;
    private static final int INTERNAL_NODE = 1;
    private static final int LEAF_NODE = 2;

    protected PageFile<Node> file = null;

    protected boolean init = false;

    public BPlusTreeFile(PageFile pagefile) throws InstantiationException, IllegalAccessException {
        this.file = pagefile;
        pagefile.setPageSerialization(this);

    }

    protected abstract TreeIndexHeader loadHeader();

    protected abstract void readFromHeader(TreeIndexHeader header);

    public void open() {
        if (!init) {

            TreeIndexHeader header = loadHeader();
            file.initialize(header);
            readFromHeader(header);

            int pageSize = file.getHeader().getPageSize();

            Key key = createKey();
            int keySize = key.getSizeInBytes();

            Value value = createValue();
            int valueSize = value.getSizeInBytes();

            InternalNode in = new InternalNode(this);
            this.m = in.findOutOptimalDegree(pageSize, keySize);

            LeafNode ln = new LeafNode(this);
            this.leafM = ln.findOutOptimalDegree(pageSize, keySize, valueSize);

            init = true;
        }
    }
    
    public void reopen() throws Exception{
        file.reopen();
    }
    
    public int getDirCapacity() {
        return m;
    }

    public int getLeafCapacity() {
        return leafM;
    }

    public int getRootID() {
        return ((TreeIndexHeader) file.getHeader()).getRootID();
    }

    private void setRootID(int id) {
        this.rootID = id;
        ((TreeIndexHeader) file.getHeader()).setRootID(id);
    }

    public int getFirstLeafID() {
        return ((TreeIndexHeader) file.getHeader()).getFirstLeafID();
    }

    private void setFirstLeafID(int id) {
        this.firstLeafID = id;
        ((TreeIndexHeader) file.getHeader()).setFirstLeafID(id);
    }

    private LeafNode findLeafNode(Key key) {

        InternalNode root = (InternalNode) getNode(getRootID());
        return findLeafNode(root, key);
    }

    private LeafNode findLeafNode(InternalNode node, Key key) {

        Key[] keys = node.keys;

        int left = 0, right = node.degree - 2;
int result = -1;

while (left <= right) {
    int mid = left + (right - left) / 2;
    int cmp = key.compareTo(keys[mid]);

    if (cmp <= 0) {  
        if (cmp == 0) {
            result = mid;
        }
        right = mid - 1;
    } else {
        left = mid + 1;
    }
}

int i = (result != -1) ? result : left;


        Node childNode = getNode(node.childPointersIDs[i]);
        if (childNode instanceof LeafNode) {
            return (LeafNode) childNode;
        } else {
            return findLeafNode((InternalNode) childNode, key);
        }
    }

    private int getMidpoint(int m_) {
        return (int) Math.ceil((m_ + 1) / 2.0) - 1;
    }

    public void prependAllEntries(InternalNode targetNode, InternalNode sourceNode) {

        int nkeys = targetNode.degree;
        for (int i = nkeys - 1; i >= 0; i--) {
            targetNode.keys[i + sourceNode.degree - 1] = targetNode.keys[i];
        }
        for (int i = targetNode.degree - 1; i >= 0; i--) {
            targetNode.childPointers[i + sourceNode.degree] = targetNode.childPointers[i];
            targetNode.childPointersIDs[i + sourceNode.degree] = targetNode.childPointersIDs[i];
        }

        int i;
        for (i = 0; i < sourceNode.degree - 1; i++) {
            Node child = getNode(sourceNode.childPointersIDs[i]);
            targetNode.setPointer(i, child);
            writeNode(child);
            targetNode.setKey(i, sourceNode.keys[i]);
        }
        Node child = getNode(sourceNode.childPointersIDs[i]);
        targetNode.setPointer(i, child);
        writeNode(child);
        targetNode.degree += sourceNode.degree;

    }

    private void handleDeficiency(InternalNode in) {

        InternalNode sibling;

        InternalNode parent = (InternalNode) getNode(in.getParentID());

        InternalNode root = (InternalNode) getNode(getRootID());
        if (in.leftSiblingID > 0) {
            in.leftSibling = (InternalNode) getNode(in.leftSiblingID);
        }
        if (in.rightSiblingID > 0) {
            in.rightSibling = (InternalNode) getNode(in.rightSiblingID);
        }

        if (in.leftSibling != null && in.leftSibling.getParentID() == in.getParentID() && in.leftSibling.isLendable()) {
            sibling = in.leftSibling;

            int pointerIndex = parent.findIndexOfPointer(in);
            in.prependChildKey(parent.keys[pointerIndex - 1]);

            Node borrowedPointer = getNode(sibling.getLastPointerID());
            in.prependChildPointer(borrowedPointer);

            parent.setKey(pointerIndex - 1, sibling.getLastKey());

            sibling.removeLastEntry();

            writeNode(in);
            writeNode(borrowedPointer);
            writeNode(parent);
            writeNode(sibling);

        } else if (in.rightSibling != null && in.rightSibling.getParentNode() == in.getParentNode() && in.rightSibling.isLendable()) {
            sibling = in.rightSibling;

            int pointerIndex = parent.findIndexOfPointer(in);
            in.appendKey(parent.keys[pointerIndex]);

            Node pointer = getNode(sibling.getFirstPointerID());
            in.appendChildPointer(pointer);

            parent.setKey(pointerIndex, sibling.getFirstKey());

            sibling.removeEntry1(0);

            writeNode(in);
            writeNode(pointer);
            writeNode(parent);
            writeNode(sibling);

        }
        else if (in.leftSibling != null && in.leftSibling.getParentNode() == in.getParentNode() && in.leftSibling.isMergeable()) {
            sibling = in.leftSibling;

            int pointerIndex = parent.findIndexOfPointer(in);
            sibling.appendKey(parent.keys[pointerIndex - 1]);

            appendAllEntries(sibling, in);

            parent.removeEntry(pointerIndex);

            sibling.rightSiblingID = in.rightSiblingID;
            if (sibling.rightSiblingID != -1) {
                sibling.rightSibling = (InternalNode) getNode(sibling.rightSiblingID);
                sibling.rightSibling.leftSiblingID = sibling.getPageID();
                writeNode(sibling.rightSibling);
            }

            writeNode(parent);
            writeNode(sibling);

        }
        else if (in.rightSibling != null && in.rightSibling.getParentNode() == in.getParentNode() && in.rightSibling.isMergeable()) {
            sibling = in.rightSibling;

            int pointerIndex = parent.findIndexOfPointer(in);
            sibling.prependChildKey(parent.keys[pointerIndex]);

            prependAllEntries(sibling, in);

            parent.removeEntry1(pointerIndex);

            sibling.leftSiblingID = in.leftSiblingID;

            if (sibling.leftSiblingID != -1) {
                sibling.leftSibling = (InternalNode) getNode(sibling.leftSiblingID);
                sibling.leftSibling.rightSiblingID = sibling.getPageID();
                writeNode(sibling.leftSibling);
            }

            writeNode(parent);
            writeNode(sibling);
        }

        if (parent.getPageID() == root.getPageID() && parent.degree == 1) {
            Node n = getNode(parent.getFirstPointerID());
            root = (InternalNode) n;
            rootID = n.getPageID();
            setRootID(rootID);
            root.setParentNode(null);
            root.setParentID(-1);
            writeNode(root);

            return;
        }

        if (parent != null && parent.isDeficient() && parent.getPageID() != root.getPageID()) {
            handleDeficiency(parent);
        }
    }

    private void appendAllEntries(InternalNode targetNode, InternalNode sourceNode) {
        Node child = getNode(sourceNode.childPointersIDs[0]);
        targetNode.appendChildPointer(child);
        writeNode(child);
        for (int i = 1; i < sourceNode.degree; i++) {
            targetNode.appendKey(sourceNode.keys[i - 1]);
            child = getNode(sourceNode.childPointersIDs[i]);
            targetNode.appendChildPointer(child);
            writeNode(child);
        }
    }

    private boolean isEmpty() {
        return getFirstLeafID() == -1;
    }

    private void splitInternalNode(InternalNode in) {

        InternalNode parent = null;
        if (parent == null) {
            parent = (InternalNode) getNode(in.getParentID());
        }

        int midpoint = getMidpoint(getDirCapacity());
        Key newParentKey = in.keys[midpoint];
        Key[] halfKeys = splitKeys(in.keys, midpoint);
        Integer[] halfPointers = in.splitChildPointersID(midpoint);

        InternalNode sibling = new InternalNode(getDirCapacity(), halfKeys, halfPointers, this);
        writeNode(sibling);
        for (Integer pointer : halfPointers) {
            if (pointer != null) {
                Node p = getNode(pointer);
                p.setParentNode(sibling);
                writeNode(p);
            }
        }

        sibling.rightSiblingID = in.rightSiblingID;

        if (sibling.rightSiblingID != -1) {
            sibling.rightSibling = (InternalNode) getNode(sibling.rightSiblingID);
            sibling.rightSibling.leftSibling = sibling;
            sibling.rightSibling.leftSiblingID = sibling.getPageID();
            writeNode(sibling.rightSibling);
        }
        in.rightSibling = sibling;
        in.rightSiblingID = sibling.getPageID();
        sibling.leftSibling = in;
        sibling.leftSiblingID = in.getPageID();

        if (parent == null) {

            Key[] keys = new Key[getDirCapacity()];
            keys[0] = newParentKey;
            InternalNode newRoot = new InternalNode(getDirCapacity(), keys, this);
            newRoot.appendChildPointer(in);
            newRoot.appendChildPointer(sibling);
            writeNode(newRoot);
            in.setParentID(newRoot.getPageID());
            sibling.setParentID(newRoot.getPageID());
            setRootID(newRoot.getPageID());

        } else {

            parent.addKey(newParentKey);

            int pointerIndex = parent.findIndexOfPointer(in) + 1;
            parent.insertChildPointer(sibling, pointerIndex);
            writeNode(parent);
        }
        writeNode(sibling);
        writeNode(in);
    }

    private Key[] splitKeys(Key[] keys, int split) {

        Key[] halfKeys = new Key[getDirCapacity()];

        keys[split] = null;

        for (int i = split + 1; i < keys.length; i++) {
            halfKeys[i - split - 1] = keys[i];
            keys[i] = null;
        }

        return halfKeys;
    }

    @Override
    public Value delete(Key key) {

        if (isEmpty()) {

            return null;

        } else {

            LeafNode firstLeaf = (LeafNode) getNode(getFirstLeafID());
            InternalNode root = (InternalNode) getNode(getRootID());

            LeafNode ln = (root == null) ? firstLeaf : findLeafNode(key);

            int dpIndex = Utils.binarySearch(ln.dictionary, ln.numPairs, key, this);

            if (dpIndex < 0) {

                return null;

            } else {

                Value valueToDelete = ln.dictionary[dpIndex].value;

                ln.delete(dpIndex);

                if (ln.isDeficient()) {

                    InternalNode parent = (InternalNode) getNode(ln.getParentID());

                    if (ln.leftSiblingID != -1) {
                        ln.leftSibling = (LeafNode) getNode(ln.leftSiblingID);
                    }

                    if (ln.rightSiblingID != -1) {
                        ln.rightSibling = (LeafNode) getNode(ln.rightSiblingID);
                    }

                    if (ln.leftSibling != null
                            && ln.leftSibling.getParentID() == ln.getParentID()
                            && ln.leftSibling.isLendable()) {

                        LeafNode sibling = ln.leftSibling;

                        DictionaryPair borrowedDP = sibling.getLastDictionaryPair();
                        ln.prependPair(borrowedDP);

                        sibling.deleteLastDictionaryPair();

                        int pointerIndex = parent.findIndexOfPointer(ln);
                        parent.keys[pointerIndex - 1] = borrowedDP.key;

                        writeNode(ln);
                        writeNode(sibling);
                        writeNode(parent);

                        return valueToDelete;

                    } else if (ln.rightSibling != null
                            && ln.rightSibling.getParentID() == ln.getParentID()
                            && ln.rightSibling.isLendable()) {

                        LeafNode sibling = ln.rightSibling;

                        DictionaryPair borrowedDP = sibling.getFirstDictionaryPair();
                        ln.appendPair(borrowedDP);

                        sibling.deleteFirstDictionaryPair();

                        int pointerIndex = parent.findIndexOfPointer(ln);

                        parent.keys[pointerIndex] = sibling.getFirstDictionaryPair().key;

                        writeNode(ln);
                        writeNode(sibling);
                        writeNode(parent);

                        return valueToDelete;

                    }
                    else if (ln.leftSibling != null
                            && ln.leftSibling.getParentID() == ln.getParentID()
                            && ln.leftSibling.isMergeable()) {

                        LeafNode sibling = ln.leftSibling;

                        int pointerIndex = parent.findIndexOfPointer(ln);
                        parent.removeEntry(pointerIndex);
                        writeNode(parent);

                        sibling.appendPairs(ln);

                        sibling.rightSiblingID = ln.rightSiblingID;
                        if (sibling.rightSiblingID != -1) {
                            sibling.rightSibling = (LeafNode) getNode(sibling.rightSiblingID);
                            sibling.rightSibling.leftSiblingID = sibling.getPageID();
                            writeNode(sibling.rightSibling);
                        }
                        writeNode(sibling);

                        deleteNode(ln);

                        if (parent.isDeficient() && parent.getPageID() != root.getPageID()) {
                            handleDeficiency(parent);
                        }
                        return valueToDelete;

                    } else if (ln.rightSibling != null
                            && ln.rightSibling.getParentID() == ln.getParentID()
                            && ln.rightSibling.isMergeable()) {

                        LeafNode sibling = ln.rightSibling;

                        int pointerIndex = parent.findIndexOfPointer(ln);
                        parent.removeEntry1(pointerIndex);

                        writeNode(parent);
                        sibling.prependPairs(ln);

                        sibling.leftSiblingID = ln.leftSiblingID;
                        if (sibling.leftSiblingID == -1) {
                            setFirstLeafID(sibling.getPageID());
                        } else {
                            sibling.leftSibling = (LeafNode) getNode(sibling.leftSiblingID);
                            sibling.leftSibling.rightSiblingID = sibling.getPageID();
                            writeNode(sibling.leftSibling);
                        }
                        writeNode(sibling);
                        deleteNode(ln);

                        if (parent.isDeficient() && parent.getPageID() != root.getPageID()) {
                            handleDeficiency(parent);
                        }
                        return valueToDelete;

                    } else {
                        writeNode(ln);
                        return valueToDelete;
                    }

                } else if (root == null && firstLeaf.numPairs == 0) {

                    setFirstLeafID(-1);
                    deleteNode(firstLeaf);

                } else {
                    writeNode(ln);

                }
                return valueToDelete;
            }
        }
    }

    @Override
    public boolean insert(Key key, Value value, boolean unique) {
        if (isEmpty()) {

            LeafNode ln = new LeafNode(getLeafCapacity(), new DictionaryPair(key, value, this), this);

            writeNode(ln);
            setFirstLeafID(ln.getPageID());

        } else {

            LeafNode firstLeaf = (LeafNode) getNode(getFirstLeafID());
            LeafNode ln = (getRootID() == -1) ? firstLeaf
                    : findLeafNode(key);

            if (unique) {
                DictionaryPair[] dps = ln.dictionary;
                int index = Utils.binarySearch(dps, ln.numPairs, key, this);

                if (index >= 0) {
                    return false;
                }
            }

            if (!ln.insert(new DictionaryPair(key, value, this))) {

                ln.sortDictionary(key, value);

                int midpoint = getMidpoint(getLeafCapacity());
                DictionaryPair[] halfDict = ln.splitDictionary(midpoint);
                writeNode(ln);

                if (ln.getParentID() >= 0) {
                    ln.setParentNode((InternalNode) getNode(ln.getParentID()));
                }

                if (ln.getParentNode() == null) {

                    Key[] parent_keys = new Key[getDirCapacity()];
                    parent_keys[0] = halfDict[0].key;
                    InternalNode parent = new InternalNode(getDirCapacity(), parent_keys, this);

                    parent.appendChildPointer(ln);
                    writeNode(parent);
                    ln.setParentID(parent.getPageID());
                    writeNode(ln);

                } else {

                    Key newParentKey = halfDict[0].key;
                    ln.getParentNode().addKey(newParentKey);
                }

                LeafNode newLeafNode = new LeafNode(getLeafCapacity(), halfDict, ln.getParentNode());
                writeNode(newLeafNode);

                if (ln.getParentNode() == null) {
                    ln.setParentNode((InternalNode) getNode(ln.getParentID()));
                }

                int pointerIndex = ln.getParentNode().findIndexOfPointer(ln) + 1;
                ln.getParentNode().insertChildPointer(newLeafNode, pointerIndex);
                writeNode(ln.getParentNode());

                newLeafNode.rightSiblingID = ln.rightSiblingID;
                if (newLeafNode.rightSiblingID != -1) {
                    newLeafNode.rightSibling = (LeafNode) getNode(newLeafNode.rightSiblingID);
                    newLeafNode.rightSibling.leftSibling = newLeafNode;
                    newLeafNode.rightSibling.leftSiblingID = newLeafNode.getPageID();
                    writeNode(newLeafNode.rightSibling);
                }
                ln.rightSibling = newLeafNode;
                ln.rightSiblingID = newLeafNode.getPageID();
                newLeafNode.leftSibling = ln;
                newLeafNode.leftSiblingID = ln.getPageID();

                writeNode(newLeafNode);
                writeNode(ln);

                if (getRootID() == -1) {

                    setRootID(ln.getParentID());

                } else {

                    InternalNode in = ln.getParentNode();
                    while (in != null) {
                        if (in.isOverfull()) {
                            splitInternalNode(in);
                        } else {
                            break;
                        }
                        if (in.getParentID() != -1) {
                            in = (InternalNode) getNode(in.getParentID());
                        } else {
                            in = null;
                        }
                    }
                }
            } else {
                writeNode(ln);
            }
        }
        return true;
    }

    @Override
    public Value search(Key key) {

        if (isEmpty()) {
            return null;
        }

        LeafNode ln = (getRootID() == -1) ? (LeafNode) getNode(getFirstLeafID())
                : findLeafNode(key);

        DictionaryPair[] dps = ln.dictionary;
        int index = Utils.binarySearch(dps, ln.numPairs, key, this);

        if (index < 0) {
            return null;
        } else {
            return dps[index].value;
        }
    }

    @Override
    public ArrayList<Value> search(Key lowerBound, Key upperBound) {

        LeafNode firstLeaf = (LeafNode) getNode(getFirstLeafID());

        ArrayList<Value> values = new ArrayList<>();

        LeafNode currNode = firstLeaf;
        while (currNode != null) {
            DictionaryPair dps[] = currNode.dictionary;
            for (DictionaryPair dp : dps) {

                if (dp == null) {
                    break;
                }

                if (lowerBound.compareTo(dp.key) <= 0 && dp.key.compareTo(upperBound) <= 0) {
                    values.add(dp.value);
                }
            }

            currNode = currNode.rightSibling;

        }

        return values;
    }

    @Override
    public ArrayList<DictionaryPair> searchAll() {

        LeafNode firstLeaf = (LeafNode) getNode(getFirstLeafID());

        ArrayList<DictionaryPair> values = new ArrayList<>();

        LeafNode currNode = firstLeaf;
        while (currNode != null) {
            DictionaryPair dps[] = currNode.dictionary;
            for (DictionaryPair dp : dps) {

                if (dp == null) {
                    break;
                }

                values.add(dp);
            }

            currNode = (LeafNode) getNode(currNode.rightSiblingID);

        }

        return values;
    }

    @Override
    public AllKeysIterator searchAllIterator() {
        return new AllKeysIterator(this);
    }

    public EqualKeyIterator equalKeyIterator(Key key) {
        return new EqualKeyIterator(this, key);
    }

    public LargerKeyIterator largerKeyIterator(Key key, boolean largerEqual) {
        return new LargerKeyIterator(this, key, largerEqual);
    }

    public SmallerKeyIterator smallerKeyIterator(Key key, boolean smallerEqual) {
        return new SmallerKeyIterator(this, key, smallerEqual);
    }

    public DifferentKeyIterator differentKeyIterator(Key key) {
        return new DifferentKeyIterator(this, key);
    }

    public LeafNode getFirstPage() {
        return (LeafNode) getNode(getFirstLeafID());
    }

    public LeafNode getFirstPage(Key key) {

        if (isEmpty()) {
            return null;
        }

        LeafNode ln = (getRootID() == -1) ? (LeafNode) getNode(getFirstLeafID()) : findLeafNode(key);
        return ln;
    }

    @Override
    public ArrayList<Value> partialSearch(Key key) {

        ArrayList<Value> values = new ArrayList();

        if (isEmpty()) {
            return values;
        }

        LeafNode ln = (getRootID() == -1) ? (LeafNode) getNode(getFirstLeafID()) : findLeafNode(key);

        DictionaryPair[] dps = ln.dictionary;
        int index = Utils.binarySearch(dps, ln.numPairs, key, this);

        if (index < 0) {
            index = ~index;
        }
        else {
            while (index > 0 && dps[index - 1].key.compareTo(key) == 0) {
                index--;
            }
        }

        for (int i = index; i < dps.length; i++) {
            if (dps[i] != null) {
                if (key.partialMatch(dps[i].key)) {
                    values.add(dps[i].value);
                } else {
                    return values;
                }
            }
        }

        LeafNode currNode = (LeafNode) getNode(ln.rightSiblingID);
        while (currNode != null) {
            DictionaryPair dps_[] = currNode.dictionary;
            for (DictionaryPair dp : dps_) {

                if (dp == null) {
                    break;
                }

                if (key.partialMatch(dp.key)) {
                    values.add(dp.value);
                } else {
                    return values;
                }
            }

            currNode = (LeafNode) getNode(currNode.rightSiblingID);

        }

        return values;
    }

    @Override
    public boolean contains(Key key) {

        if (isEmpty()) {
            return false;
        }

        LeafNode ln = (getRootID() == -1) ? (LeafNode) getNode(getFirstLeafID()) : findLeafNode(key);

        DictionaryPair[] dps = ln.dictionary;
        int index = Utils.binarySearch(dps, ln.numPairs, key, this);

        if (index < 0) {
            index = ~index;
        }
        else {
            while (index > 0 && dps[index - 1].key.compareTo(key) == 0) {
                index--;
            }
        }

        for (int i = index; i < dps.length; i++) {
            if (dps[i] != null) {
                if (key.partialMatch(dps[i].key)) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        LeafNode currNode = (LeafNode) getNode(ln.rightSiblingID);
        while (currNode != null) {
            DictionaryPair dps_[] = currNode.dictionary;
            for (DictionaryPair dp : dps_) {

                if (dp == null) {
                    break;
                }

                if (key.partialMatch(dp.key)) {
                    return true;
                } else {
                    return false;
                }
            }

            currNode = (LeafNode) getNode(currNode.rightSiblingID);

        }
        return false;
    }

    @Override
    public Value update(Key key, Value value) {

        if (isEmpty()) {
            return null;
        }

        LeafNode ln = (getRootID() == -1) ? (LeafNode) getNode(getFirstLeafID()) : findLeafNode(key);

        DictionaryPair[] dps = ln.dictionary;
        int index = Utils.binarySearch(dps, ln.numPairs, key, this);

        if (index < 0) {
            return null;
        } else {
            dps[index].value = value;
            writeNode(ln);
            return dps[index].value;
        }
    }

    public void close() {
        file.close();
    }

    public void printNode(LeafNode node) {
        if (node == null) {
            return;
        }
        String parent = "";
        if (node.getParentNode() != null) {
            parent = node.getParentNode().toString();
        }
        System.out.println("LF:" + node.toString() + " => " + parent);
    }

    public void printNode(InternalNode node) {
        if (node == null) {
            return;
        }
        String parent = "";
        if (node.getParentNode() != null) {
            parent = node.getParentNode().toString();
        }
        System.out.println("IN:" + node.toString() + " => " + parent);
        for (int x = 0; x < node.childPointers.length; x++) {
            if (node.childPointers != null) {
                if (node.childPointers[x] instanceof InternalNode) {
                    printNode((InternalNode) node.childPointers[x]);
                } else {
                    printNode((LeafNode) node.childPointers[x]);
                }
            }
        }

    }

    public Node getNode(int nodeID) {
        if (nodeID < 0) {
            return null;
        }
        Node n = file.readPage(nodeID);
        n.setPageID(nodeID);
        return n;
    }

    protected void writeNode(Node node) {
        file.writePage(node);
    }

    protected void deleteNode(Node node) {
        file.deletePage(node.getPageID());
    }

    @Override
    public void writePage(DataOutputStream oos, Page page) throws IOException {
        if (page == null) {
            oos.writeInt(EMPTY_PAGE);
        } else {
            if (page instanceof LeafNode) {
                oos.writeInt(LEAF_NODE);
            } else if (page instanceof InternalNode) {
                oos.writeInt(INTERNAL_NODE);
            }

            ((AbstractExternalizablePage) page).writeExternal(oos);
        }

    }

    @Override
    public Page readPage(DataInputStream ois) throws IOException {
        final int type = ois.readInt();
        if (type == EMPTY_PAGE) {
            return null;
        }
        try {
            AbstractExternalizablePage page = null;
            if (type == INTERNAL_NODE) {
                page = new InternalNode(this);
            } else if (type == LEAF_NODE) {
                page = new LeafNode(this);
            }
            page.readExternal(ois);
            return page;
        } catch (Exception e) {
            throw new IOException("Error instanciating an index page", e);

        }

    }

    public void flush() {
        file.flush();
    }

}
