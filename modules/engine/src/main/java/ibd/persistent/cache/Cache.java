package ibd.persistent.cache;

import ibd.persistent.Page;
import ibd.persistent.PageFile;
import ibd.persistent.PageHeader;
import ibd.persistent.PageSerialization;
import java.util.Collection;

public abstract class Cache<P extends Page> extends PageFile<P> {

    protected int cacheSizeBytes;

    protected int cacheSize;

    protected PageFile<P> file;

    public Cache(int cacheSizeBytes) {
        this.cacheSizeBytes = cacheSizeBytes;
    }

    protected abstract P getFromCache(int pageID);

    protected abstract void addToCache(int pageID, P page);

    protected abstract void deleteFromCache(int pageID);

    protected abstract void initCache();

    protected abstract void clearCache();

    protected abstract Collection<P> getCachePages();
    
    public void setPageFile(PageFile<P> file){
        this.file = file;
    }
    

    @Override
    public synchronized P readPage(int pageID) {
        engine.info.Parameters.BLOCKS_ACCESSED++;
        P page = getFromCache(pageID);
        if (page == null) {
            page = file.readPage(pageID);
            addToCache(pageID, page);
        }
        return page;
    }

    @Override
    public synchronized void writePage(int pageID, P page) {
        page.setDirty(true);
        addToCache(pageID, page);

    }

    @Override
    public void deletePage(int pageID) {
        deleteFromCache(pageID);
        file.deletePage(pageID);
    }

    protected void expirePage(P page) {
        if (page.isDirty()) {
            file.writePage(page);
        }
    }

    @Override
    public int setPageID(P page) {
        int pageID = file.setPageID(page);
        return pageID;
    }

    @Override
    public int getNextPageID() {
        return file.getNextPageID();
    }

    @Override
    public void setNextPageID(int nextPageID) {
        file.setNextPageID(nextPageID);
    }

    @Override
    public int getPageSize() {
        return file.getPageSize();
    }

    @Override
    public boolean initialize(PageHeader header) {
        boolean created = file.initialize(header);
        this.cacheSize = cacheSizeBytes / file.getHeader().getPageSize();

        if (this.cacheSize <= 0) {
        }

        initCache();

        return created;
    }

    @Override
    public void reopen() throws Exception{
        file.reopen();
    }
    
    @Override
    public void close() {
        file.close();
    }

    @Override
    public void clear() {
        file.clear();
    }

    @Override
    public void flush() {
        Collection<P> pages = getCachePages();
        for (P object : pages) {
            expirePage(object);
        }
        clearCache();
        file.flush();
    }

    @Override
    public PageHeader getHeader() {
        return file.getHeader();
    }

    @Override
    public void setPageSerialization(PageSerialization pageSerialization) {
        file.setPageSerialization(pageSerialization);
    }

    @Override
    public void reset() {
        file.reset();
    }

}
