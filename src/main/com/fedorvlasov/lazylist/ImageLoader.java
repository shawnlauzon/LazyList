package com.fedorvlasov.lazylist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Stack;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

/**
 * Class that handles loading images from the web and caching them in memory and in the filesystem
 *
 * @author Fedor Vlasov <http://www.fedorvlasov.com>
 * @author slightly modified by Grantland Chew <http://grantland.me>
 */
public class ImageLoader {

    protected MemoryCache mMemoryCache;
    private FileCache mFileCache;
    protected Map<ImageView, String> mImageViews = Collections.synchronizedMap(new WeakHashMap<ImageView, String>());
    private PhotosLoader mPhotoLoaderThread = new PhotosLoader();
    private PhotosQueue mPhotosQueue = new PhotosQueue();

    public ImageLoader(Context context) {
        //Make the background thead low priority. This way it will not affect the UI performance
        mPhotoLoaderThread.setPriority(Thread.NORM_PRIORITY-1);

        mMemoryCache = new MemoryCache();
        mFileCache = new FileCache(context);
    }

    public void displayImage(String url, ImageView imageView) {
        mImageViews.put(imageView, url);
        Bitmap bitmap = mMemoryCache.get(url);

        if(bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
        	queueImage(new PhotoToLoad(url, imageView, new HttpCallable(url)));
        }
    }

    protected void queueImage(PhotoToLoad photoToLoad) {
        // This ImageView may be used for other images before. So there may be some old tasks in the queue. We need to discard them.
        mPhotosQueue.clean(photoToLoad.imageView);
        synchronized(mPhotosQueue.mPhotosToLoad){
            mPhotosQueue.mPhotosToLoad.push(photoToLoad);
            mPhotosQueue.mPhotosToLoad.notifyAll();
        }

        // Start thread if it's not started yet
        if(mPhotoLoaderThread.getState() == Thread.State.NEW)
            mPhotoLoaderThread.start();
    }

    /** Runs on PhotoLoader Thread */
    private Bitmap getBitmap(PhotoToLoad photoToLoad) {
        File f = mFileCache.getFile(photoToLoad.key);

        // Load from file cache
        Bitmap bitmap = decodeFile(f);
        if (bitmap != null) {
            return bitmap;
        }

        // Load from web
        try {
            InputStream is = photoToLoad.callable.call();

            // Save to file
            OutputStream os = new FileOutputStream(f);
            Utils.copyStream(is, os);
            os.close();

            bitmap = decodeFile(f);
            return bitmap;
        } catch (FileNotFoundException e) {
        	e.printStackTrace();
        	return null;
        } catch (Exception e){
           e.printStackTrace();
           return null;
        }
    }

    /** Decodes image and scales it to reduce memory consumption */
    private Bitmap decodeFile(File f){
        try {
            //decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(new FileInputStream(f),null,o);

            //Find the correct scale value. It should be the power of 2.
            final int REQUIRED_SIZE = 70;
            int width_tmp = o.outWidth,
            	height_tmp = o.outHeight;
            int scale = 1;
            while (true) {
                if(width_tmp/2<REQUIRED_SIZE || height_tmp/2<REQUIRED_SIZE)
                    break;
                width_tmp /= 2;
                height_tmp /= 2;
                scale *= 2;
            }

            //decode with inSampleSize
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            return BitmapFactory.decodeStream(new FileInputStream(f), null, o2);
        } catch (FileNotFoundException e) {
        	//TODO add exception case
        }
        return null;
    }

    public void stopThread() {
        mPhotoLoaderThread.interrupt();
    }

    public void clearCache() {
        mMemoryCache.clear();
        mFileCache.clear();
    }

    /** Task for the queue */
    protected class PhotoToLoad {
        public String key;
        public ImageView imageView;
        public Callable<InputStream> callable;

        public PhotoToLoad(String key, ImageView i, Callable<InputStream> c){
            this.key = key;
            imageView = i;
            callable = c;
        }
    }

    private class HttpCallable implements Callable<InputStream> {
    	private String url;

    	public HttpCallable(String url) {
    		this.url = url;
    	}

		@Override
		public InputStream call() throws Exception {
            URL imageUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection)imageUrl.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            return conn.getInputStream();
		}
    }

    /** Stores list of photos to download */
    class PhotosQueue {
        private Stack<PhotoToLoad> mPhotosToLoad = new Stack<PhotoToLoad>();

        //removes all instances of this ImageView
        public void clean(ImageView image) {
            for(int j = 0; j<mPhotosToLoad.size(); /* nothing */) {
                if(mPhotosToLoad.get(j).imageView == image) {
                    mPhotosToLoad.remove(j);
                } else {
                    ++j;
                }
            }
        }
    }

    /** Thread to load images */
    class PhotosLoader extends Thread {
        public void run() {
            try {
                while(true) {
                    //thread waits until there are any images to load in the queue
                    if(mPhotosQueue.mPhotosToLoad.size() == 0) {
                        synchronized(mPhotosQueue.mPhotosToLoad) {
                            mPhotosQueue.mPhotosToLoad.wait();
                        }
                    }

                    // Load a photo!
                    if(mPhotosQueue.mPhotosToLoad.size() != 0) {
                        PhotoToLoad photoToLoad;
                        synchronized(mPhotosQueue.mPhotosToLoad) {
                            photoToLoad = mPhotosQueue.mPhotosToLoad.pop();
                        }

                        Bitmap bmp = getBitmap(photoToLoad);
                        mMemoryCache.put(photoToLoad.key, bmp);
                        String tag = mImageViews.get(photoToLoad.imageView);

                        if(tag != null && tag.equals(photoToLoad.key)) {
                            BitmapDisplayer bd = new BitmapDisplayer(bmp, photoToLoad.imageView);
                            Activity a = (Activity)photoToLoad.imageView.getContext();
                            a.runOnUiThread(bd);
                        }
                    }

                    if(Thread.interrupted()) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                //allow thread to exit
            }
        }
    }

    /** Runnable to display bitmap in the UI thread */
    class BitmapDisplayer implements Runnable
    {
        Bitmap bitmap;
        ImageView imageView;
        public BitmapDisplayer(Bitmap b, ImageView i) {
        	bitmap = b;
        	imageView = i;
    	}

        public void run() {
            if(bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }
}
