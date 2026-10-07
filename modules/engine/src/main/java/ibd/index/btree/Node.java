package ibd.index.btree;

import ibd.persistent.AbstractExternalizablePage;

public abstract class Node extends AbstractExternalizablePage {

    private InternalNode parent;
    private int parentID = -1;
    
    public void setParentNode(InternalNode n){
        parent = n;
        if (n==null)
            parentID = -1;
        else parentID = n.getPageID();
    }
    
    public InternalNode getParentNode(){
        return parent;
    }
    
    public int getParentID(){
        return parentID;
    }
    
    public void setParentID(int id){
        parentID = id;
    }
    
    
}
