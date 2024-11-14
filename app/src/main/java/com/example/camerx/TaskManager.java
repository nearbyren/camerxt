package com.example.camerx;

import android.graphics.Bitmap;
import android.os.Handler;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class TaskManager {
   private ExecutorService fixedThreadPool ;
   private int  mTask ;
   private final Lock lock ;  // = new ReentrantLock();
    private  TreeMap<Long,Bitmap> tmap ;// = new TreeMap() ;
    private  SeetaFace seetaFace;
    private Handler hHandle ;
    private final Lock lockQueue ;
    private Queue<Integer> queue = new LinkedList() ;
   private TaskManager(){
        mTask = 0 ;
        fixedThreadPool = null;// Executors.newFixedThreadPool(2);
        lock = new ReentrantLock();
        tmap = new TreeMap() ;
       lockQueue = new ReentrantLock();
    }

    public boolean InitTask(int nTask,SeetaFace sf,Handler h){
       if(sf == null) return  false;
        nTask = nTask >1?(nTask<8?nTask:8):1; //1-8
        mTask = nTask;
        fixedThreadPool =  Executors.newFixedThreadPool(mTask);
        seetaFace = sf ;
        hHandle = h;
        for(int i= 0;i<nTask;i++)
            queue.add(i);
        return  true;
    }

    private int  getIndex(){
        int i = -1;
        lockQueue.lock();
        if(!queue.isEmpty()){
            i = queue.poll() ;
        }
        lockQueue.unlock();
        return  i;
    }
    private  void putIndex(int i){
        lockQueue.lock();
        queue.add(i);
        lockQueue.unlock();
    }

    public void Executor(Bitmap bm,Long  lastid){
        fixedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                int nIndex = getIndex();
                if(nIndex  >= 0 && nIndex < mTask) {
                    int nResult =  seetaFace.detectFaceEx(bm,27);// 1 detect  2 point5 4 point68 8 sex  16 age; //去掉 25帧 //加上6,7帧
                    putIndex(nIndex);
                    // Long e = System.currentTimeMillis();
                    if(nResult < 0){
                        lock.lock();
                        tmap.put(lastid<<8,bm);
                        lock.unlock();
                    }else{
                        lock.lock();
                        tmap.put((lastid<<8)+(nResult&0xFF),bm);
                        lock.unlock();
                    }
                    hHandle.sendEmptyMessage(1);
                }

                long tid = Thread.currentThread().getId();
            }
        });
    }
    public  void StopThread(){
       if(fixedThreadPool != null){
           fixedThreadPool.shutdown();
       }
    }

    public  Bitmap getRef(Long nResult){
        Map.Entry<Long,Bitmap>kv ;
        boolean b = false;
        lock.lock();
        kv = tmap.firstEntry();
        if(kv != null){
            tmap.remove(kv.getKey());
            b = true;
        }
        lock.unlock();
        if(!b) return  null ;
        nResult = kv.getKey();
        return  kv.getValue() ;
    }

    private static class TaskManagerInstance {
        private final static TaskManager INSTANCE = new TaskManager();
    }

    public static TaskManager getInstance() {
        return TaskManagerInstance.INSTANCE;
    }
}
