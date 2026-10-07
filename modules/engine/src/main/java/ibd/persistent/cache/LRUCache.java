package ibd.persistent.cache;

import ibd.persistent.Page;
import ibd.persistent.PageFile;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<P extends Page> extends Cache<P> {

    private LinkedHashMap<Integer, P> map;

    public LRUCache(int cacheSizeBytes) {
        super(cacheSizeBytes);
    }

    protected P getFromCache(int pageID) {
        return map.get(pageID);
    }

    protected void addToCache(int pageID, P page) {
        map.put(pageID, page);
    }

    protected void deleteFromCache(int pageID) {
        map.remove(pageID);
    }

    @Override
    public void clearCache() {
        map.clear();
    }

    protected void initCache() {
        float hashTableLoadFactor = 0.75f;
        int hashTableCapacity = (int) Math.ceil(cacheSize / hashTableLoadFactor) + 1;

        this.map = new LinkedHashMap<Integer, P>(hashTableCapacity, hashTableLoadFactor, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, P> eldest) {
                if (size() > LRUCache.this.cacheSize) {
                    expirePage(eldest.getValue());
                    return true;
                }
                return false;
            }
        };
    }

    protected Collection<P> getCachePages() {
        return map.values();
    }

}
