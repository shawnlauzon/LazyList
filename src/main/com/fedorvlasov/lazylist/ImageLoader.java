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
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

public class ImageLoader {

    private MemoryCache mMemoryCache;
    private FileCache mFileCache;
    private Map<ImageView, String> mImageViews = Collections.synchronizedMap(new WeakHashMap<ImageView, String>());
    private PhotosLoader mPhotoLoaderThread = new PhotosLoader();
    private PhotosQueue mPhotosQueue = new PhotosQueue();

    public ImageLoader(Context context) {
        //Make the background thead low priority. This way it will not affect the UI performance
        mPhotoLoaderThread.setPriority(Thread.NORM_PRIORITY-1);

        mMemoryCache = new MemoryCache();
        mFileCache = new FileCache(context);
    }

    final int stub_id = R.drawable.stub;
    public void displayImage(String url, Activity activity, ImageView imageView) {
        mImageViews.put(imageView, url);
        Bitmap bitmap = mMemoryCache.get(url);

        if(bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            queuePhoto(url, activity, imageView);
            imageView.setImageResource(stub_id);
        }
    }

    private void queuePhoto(String url, Activity activity, ImageView imageView) {
        // This ImageView may be used for other images before. So there may be some old tasks in the queue. We need to discard them.
        mPhotosQueue.clean(imageView);
        PhotoToLoad p = new PhotoToLoad(url, imageView);
        synchronized(mPhotosQueue.mPhotosToLoad){
            mPhotosQueue.mPhotosToLoad.push(p);
            mPhotosQueue.mPhotosToLoad.notifyAll();
        }

        // Start thread if it's not started yet
        if(mPhotoLoaderThread.getState() == Thread.State.NEW)
            mPhotoLoaderThread.start();
    }

    /** Gets run on PhotoLoader Thread */
    private Bitmap getBitmap(String url) {
        File f = mFileCache.getFile(url);

        // Load from file cache
        Bitmap bitmap = decodeFile(f);
        if (bitmap != null) {
            return bitmap;
        }

        // Load from web
        try {
            URL imageUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection)imageUrl.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            InputStream is = conn.getInputStream();

            // Save to file
            OutputStream os = new FileOutputStream(f);
            Utils.copyStream(is, os);
            os.close();

            bitmap = decodeFile(f);
            return bitmap;
        } catch (Exception ex){
           ex.printStackTrace();
           return null;
        }
    }

    //decodes image and scales it to reduce memory consumption
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

    //Task for the queue
    private class PhotoToLoad {
        public String url;
        public ImageView imageView;
        public PhotoToLoad(String u, ImageView i){
            url = u;
            imageView = i;
        }
    }

    //stores list of photos to download
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

                        Bitmap bmp = getBitmap(photoToLoad.url);
                        mMemoryCache.put(photoToLoad.url, bmp);
                        String tag = mImageViews.get(photoToLoad.imageView);

                        if(tag!=null && tag.equals(photoToLoad.url)) {
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

    //Used to display bitmap in the UI thread
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
            } else {
                imageView.setImageResource(stub_id);
            }
        }
    }

}
