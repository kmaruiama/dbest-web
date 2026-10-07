package ibd.persistent;


public abstract class AbstractStoringPageFile<P extends Page> extends PageFile {
  protected IntegerArray emptyPages;

  protected int nextPageID;

  protected int pageSize;

  PageSerialization pageSerialization;
  
    protected PageHeader header;
  
  
  protected AbstractStoringPageFile(int pageSize) {
    this.emptyPages = new IntegerArray();
    this.nextPageID = 0;
    this.pageSize = pageSize;
  }

  
    
    public void setPageSerialization(PageSerialization pageSerialization){
      this.pageSerialization = pageSerialization;
  }
    
    @Override
    public PageHeader getHeader() {
        return header;
    }
  
  @Override
  public void reset(){
    this.emptyPages = new IntegerArray();
    this.nextPageID = 0;
  }
  
  
  @Override
  public int setPageID(Page page) {
    int pageID = page.getPageID();
    if(pageID == -1) {
      pageID = getNextEmptyPageID();
      if(pageID == -1) {
        pageID = nextPageID++;
      }
      page.setPageID(pageID);
    }
    else {
      if(pageID >= nextPageID) {
        for(int i = nextPageID; i < pageID; i++) {
          emptyPages.add(i);
        }
        nextPageID = pageID + 1;
      }
    }
    return pageID;
  }

  @Override
  public void deletePage(int pageID) {
    emptyPages.add(pageID);
  }

  private int getNextEmptyPageID() {
    return emptyPages.isEmpty() ? -1 : emptyPages.get(--emptyPages.size);
  }

  @Override
  public int getNextPageID() {
    return nextPageID;
  }

  @Override
  public void setNextPageID(int nextPageID) {
    this.nextPageID = nextPageID;
  }

  @Override
  public int getPageSize() {
    return pageSize;
  }

}
