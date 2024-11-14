package com.example.camerx;

import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import android.graphics.Bitmap;
import android.util.Log;

import kotlin.jvm.internal.Ref;

public class ImageDataBuffer {
    private final HashMap<Long, Bitmap> mImgDataHashMap;
    private final Lock lock;
    private final Condition mReleaseCondition;
    private boolean isRelease;
    private Long   lastItemId ;
    private Long totalcount;

    private ImageDataBuffer(){
        mImgDataHashMap = new HashMap<>();
        lock = new ReentrantLock();
        mReleaseCondition = lock.newCondition();
        isRelease = false;
        lastItemId = 0L;
        totalcount =0L;
    }

    private static class ImageDataBufferInstance {
        private final static ImageDataBuffer INSTANCE = new ImageDataBuffer();
    }

    public static ImageDataBuffer getInstance() {
        return ImageDataBufferInstance.INSTANCE;
    }

    public void addItem(Bitmap item,long itemdid) {
        lock.lock();
        if (isRelease) {
            destroy(itemdid);
            lock.unlock();
            return;
        }
        if(lastItemId == itemdid){
            lock.unlock();
            return;
        }
        lastItemId = itemdid;
        if(totalcount > 150){
            Log.i("MainActivity", "add item: hash map size:" + mImgDataHashMap.size());
            mImgDataHashMap.clear();
            totalcount = 0L;
        }

        mImgDataHashMap.put(itemdid, item);
        ++ totalcount;

  //
        lock.unlock();
    }

    private Bitmap getRef() {
        Bitmap imgDataItem;
        if (mImgDataHashMap.size() < 1) {
            return null;
        }
        imgDataItem = mImgDataHashMap.get(lastItemId);
//        if (imgDataItem != null) {
//            imgDataItem.addRef();
//        }
        return imgDataItem;
    }

    public Bitmap getRef(Long oldId, Long lastid) {
        lock.lock();
        if (oldId == lastItemId || isRelease) {
            lock.unlock();
            return null;
        }
        Bitmap imgDataItem = getRef();
        lastid = lastItemId;
        lock.unlock();
        return imgDataItem;
    }

    public void removeRef(Long itemid) {
        lock.lock();
        Bitmap imgDataItem = mImgDataHashMap.get(itemid);
        if(imgDataItem != null){
            destroy(itemid);
            --totalcount ;
        }
        if (isRelease) {
            mReleaseCondition.signalAll();
        }
        lock.unlock();
    }

    private void destroy(long itemid) {
        mImgDataHashMap.remove(itemid);
    }

    public void waittingForRelease() {
        lock.lock();
        isRelease = true;
        try {
            while (true) {
                if (lastItemId == 0) {
                    break;
                }
                if (mImgDataHashMap.size() < 1 ) {
                    break;
                }

                mReleaseCondition.await();
             //   Log.i("GestureOriginBuffer", "waitingForRelease: while await out");
            }
        } catch (Exception e) {
        //    Log.i("GestureOriginBuffer", "waitingForRelease: " + e);
            e.printStackTrace();
        }
     //   Log.i("GestureOriginBuffer", "waitingForRelease: while out");
        destroy(lastItemId);
        lastItemId = 0L;
        lock.unlock();
      //  Log.i("GestureOriginBuffer", "waitingForRelease: released");
    }

    public void reset() {
        isRelease = false;
    }
}
