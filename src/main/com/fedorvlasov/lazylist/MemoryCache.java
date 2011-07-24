package com.fedorvlasov.lazylist;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import android.graphics.Bitmap;

public class MemoryCache {
    private HashMap<String, SoftReference<Bitmap>> mCache = new HashMap<String, SoftReference<Bitmap>>();
    
    public Bitmap get(String id) {
        if(!mCache.containsKey(id)) {
            return null;
        }
        
        SoftReference<Bitmap> ref = mCache.get(id);
        return ref.get();
    }
    
    public void put(String id, Bitmap bitmap) {
        mCache.put(id, new SoftReference<Bitmap>(bitmap));
    }

    public void clear() {
        mCache.clear();
    }
}