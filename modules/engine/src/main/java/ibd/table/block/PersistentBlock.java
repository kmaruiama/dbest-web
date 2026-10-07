package ibd.table.block;

import ibd.persistent.AbstractExternalizablePage;

public class PersistentBlock extends AbstractExternalizablePage{
    
    protected int pageSize;
    
    public PersistentBlock(int pageSize){
        this.pageSize = pageSize;
    }
    
    public int getPageSize(){
        return pageSize;
    }
    
    
}
