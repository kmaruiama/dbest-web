package ibd.persistent;

public abstract class PageFile<P extends Page> {
  
    
    public abstract void setPageSerialization(PageSerialization pageSerialization);
    
  public abstract int setPageID(P page);

  

  public abstract P readPage(int pageID);

  public abstract void deletePage(int pageID);

  

  public abstract void clear();
  
  public abstract void reset();
  
  public abstract void flush();

  public abstract int getNextPageID();

  public abstract void setNextPageID(int nextPageID);

  public abstract int getPageSize();

  public abstract boolean initialize(PageHeader header);

  public abstract PageHeader getHeader();

  public final synchronized int writePage(P page) {
    int pageid = setPageID(page);
    writePage(pageid, page);
    return pageid;
  }

  protected abstract void writePage(int pageid, P page);
  
  public abstract void reopen() throws Exception;  
  
  

  public void close() {
    clear();
  }  
  
}
