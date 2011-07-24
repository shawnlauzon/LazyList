package com.fedorvlasov.lazylist;

import java.io.File;

import android.content.Context;

//TODO Move file cache to /Android/data/<package_name>/files/
//TODO Refactor saving files here
/**
 * Class to handling saving and retrieving files to and from the External Storage or application storage
 * 
 * @author Fedor Vlasov <http://www.fedorvlasov.com>
 * @author slightly modified by Grantland Chew <http://grantland.me>
 */
public class FileCache {
    
    private File mCacheDir;
    
    public FileCache(Context context) {
        //Find the dir to save cached images
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            mCacheDir = new File(android.os.Environment.getExternalStorageDirectory(),"LazyList");
        } else {
            mCacheDir = context.getCacheDir();
        }
        
        if (!mCacheDir.exists()) {
            mCacheDir.mkdirs();
        }
    }
    
    public File getFile(String url){
        //I identify images by hashcode. Not a perfect solution, good for the demo.
        String filename = String.valueOf(url.hashCode());
        File f = new File(mCacheDir, filename);
        return f;   
    }
    
    public void clear(){
        File[] files = mCacheDir.listFiles();
        for(File f:files) {
            f.delete();
        }
    }
}