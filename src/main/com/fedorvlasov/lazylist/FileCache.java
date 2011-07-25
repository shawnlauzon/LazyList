package com.fedorvlasov.lazylist;

import java.io.File;

import android.content.Context;
import android.os.Environment;

//TODO Refactor saving files here
/**
 * Class to handling saving and retrieving files to and from the External Storage or application
 * storage
 *
 * @author Fedor Vlasov <http://www.fedorvlasov.com>
 * @author slightly modified by Grantland Chew <http://grantland.me>
 */
public class FileCache {

    private File mCacheDir;

    /**
     * Creates cache directory. Uses external storage if mounted, internal cache if not.
     * @param context
     */
    public FileCache(Context context) {
        //Find the dir to save cached images
        if (Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
        	// External Cache Dir: sdcard/Android/data/<packageName>/cache/
        	String dir = Environment.getExternalStorageDirectory().getPath()
                	+ "/Android/data/" + context.getPackageName();
            mCacheDir = new File(dir, "cache");
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