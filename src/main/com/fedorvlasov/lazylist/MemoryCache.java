package com.fedorvlasov.lazylist;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import android.graphics.Bitmap;
/**
 * Class to handling saving and retrieving files to and from memory
 *
 * @author Fedor Vlasov <http://www.fedorvlasov.com>
 * @author slightly modified by Grantland Chew <http://grantland.me>
 */
public class MemoryCache {
    private HashMap<String, SoftReference<Bitmap>> mCache;

    public MemoryCache() {
    	mCache = new HashMap<String, SoftReference<Bitmap>>();
    }

    public Bitmap get(String key) {
        if(!mCache.containsKey(key)) {
            return null;
        }

        SoftReference<Bitmap> ref = mCache.get(key);
        return ref.get();
    }

    public void put(String key, Bitmap bitmap) {
        mCache.put(key, new SoftReference<Bitmap>(bitmap));
    }

    public void clear() {
        mCache.clear();
    }
}