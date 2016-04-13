package com.tao.imageload;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

/**
 * 图片加载及处理类
 * 
 * @author berry
 * 
 */
public class AsyncImageLoader{
  
  private String TAG = "AsyncImageLoader";
  public static String saveDir = Environment.getExternalStorageDirectory().getPath()
      + "/pada/images/";
  private static final int MAX_CAPACITY = 20;
  // 一级缓存(强连接)
  private HashMap<String, Bitmap> mFirstLevelCache;
  // 二级缓存 (软连接)
  private ConcurrentHashMap<String, SoftReference<Bitmap>> mSecondLevelCache;

  private static AsyncImageLoader instance;
  private ExecutorService pool;// 后台线程池

  private AsyncImageLoader() {
    pool = Executors.newFixedThreadPool(4);// 默认线程池大小为6
    initCache();
  }

  public static AsyncImageLoader getInstance() {
    if (instance == null) {
      instance = new AsyncImageLoader();
    }
    return instance;
  }

  /**
   * 后台请求(用于第一次缓存图片数据到本地,在需要显示之前)
   * 
   * @param url
   */
  public void loadImageByUrl(final String url) {
    if (TextUtils.isEmpty(url)) {
      return;
    }
    pool.submit(new Runnable() {

      @Override
      public void run() {
        getBitmapFromCache(url);
      }
    });
  }

  /**
   * 请求图片并显示
   * 
   * @param url
   * @param imageView
   */
  public void loadImageAndDisplay(String url, ImageView imageView,Bitmap defaultImage) {
    if (TextUtils.isEmpty(url)) {
      return;
    }
    ImageLoaderTask task = new ImageLoaderTask();
    //task.execute(url, imageView,defaultImage);//串行
    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url, imageView,defaultImage);//并行
  }

  class ImageLoaderTask extends AsyncTask<Object, Void, Bitmap> {
    String url = "";
    ImageView imageView;
    Bitmap defaultImage = null;

    @Override
    protected Bitmap doInBackground(Object... params) {
      url = (String) params[0];
      imageView = (ImageView) params[1];
      defaultImage =  (Bitmap) params[2];
      return getBitmapFromCache(url);
    }

    @Override
    protected void onPostExecute(Bitmap result) {
      super.onPostExecute(result);
      if (result == null) {
        result = defaultImage;// 使用默认图片
      }
      imageView.setImageBitmap(result);
    }

  }

  /**
   * 从缓存中读取图片
   * 
   * @param url
   * @return
   */
  public Bitmap getBitmapFromCache(String url) {
    Bitmap bitmap = null;
    // step1:一级缓存
    bitmap = getFromFirstLevelCache(url);
    if (bitmap != null && !bitmap.isRecycled()) {
      return bitmap;
    }
    // step2:二级缓存
    bitmap = getFromSecondLevelCache(url);
    if (bitmap != null && !bitmap.isRecycled()) {
      return bitmap;
    }
    // step3:sd卡缓存
    bitmap = getFromSDCache(url);
    if (bitmap != null && !bitmap.isRecycled()) {
      return bitmap;
    }
    // step4:从网络下载
    bitmap = getFromWebAndAddToCache(url);
    if (bitmap != null && !bitmap.isRecycled()) {
      return bitmap;
    }
    return bitmap;
  }

  /**
   * 从一级缓存中读取图片
   * 
   * @param url
   * @return
   */
  private Bitmap getFromFirstLevelCache(String url) {
    Bitmap bitmap = null;
    synchronized (mFirstLevelCache) {
      bitmap = mFirstLevelCache.get(url);
      if (bitmap != null) {
        mFirstLevelCache.remove(url);
        mFirstLevelCache.put(url, bitmap);
      }
    }
    return bitmap;
  }

  /**
   * 从二级缓存读取 图片
   * 
   * @param url
   * @return
   */
  private Bitmap getFromSecondLevelCache(String url) {
    Bitmap bitmap = null;
    SoftReference<Bitmap> softReference = mSecondLevelCache.get(url);
    if (softReference != null) {
      bitmap = softReference.get();
      if (bitmap == null) {
        mSecondLevelCache.remove(url);
      }
    }
    return bitmap;
  }

  /**
   * 从sd卡缓存读取 图片
   * 
   * @param url
   * @return
   */
  private Bitmap getFromSDCache(String url) {
    // 本地查找
    String bitmapName = url.substring(url.lastIndexOf("/") + 1);
    File cacheDir = new File(saveDir);
    File[] cacheFiles = cacheDir.listFiles();
    int i = 0;
    if (null != cacheFiles) {
      for (; i < cacheFiles.length; i++) {
        if (bitmapName.equals(cacheFiles[i].getName())) {
          break;
        }
      }

      if (i < cacheFiles.length) {
        try {
          InputStream iStream = new FileInputStream(saveDir + bitmapName);
          return resizeBitmap(iStream, 2);//本地找到
        } catch (FileNotFoundException e) {
          return null;
        }
      }
    }

    return null;
  }

  /**
   * 从网络下载
   * 
   * @param url
   * @return
   */
  private Bitmap getFromWebAndAddToCache(String urlString) {
  /*  if (!isNetworkAvailable()) {
      return null;
    }*/
    try {
      URL url = new URL(urlString);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.connect();
      InputStream iStream = conn.getInputStream();
      Bitmap bitmap = BitmapFactory.decodeStream(iStream);
      // 保存到缓存
      mFirstLevelCache.put(urlString, bitmap);
      // 保存到sd卡
      saveBitmapToFile(urlString, bitmap);
      return bitmap;
    } catch (MalformedURLException e) {
      Log.e(TAG, "从网络下载图片错误:" + e.getMessage());
      return null;
    } catch (IOException e) {
      Log.e(TAG, "从网络下载图片错误:" + e.getMessage());
      return null;
    }
  }

  /**
   * 保存Bitmap到sd卡
   * 
   * @param filePath
   * @param mBitmap
   */
  public void saveBitmapToFile(String url, Bitmap mBitmap) {

    File dirFile = new File(saveDir);
    if (!dirFile.exists()) {
      dirFile.mkdirs();
    }
    String filePath = saveDir + url.substring(url.lastIndexOf("/") + 1);
    File bitmapFile = new File(filePath);
    if (!bitmapFile.exists() || bitmapFile.length() < 1) {
      try {
        bitmapFile.createNewFile();
      } catch (IOException e) {
        Log.e(TAG, "创建本地图片失败，msg=" + e.getMessage());
        e.printStackTrace();
      }
    }
    FileOutputStream fOut = null;
    try {
      fOut = new FileOutputStream(bitmapFile);
      mBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fOut);
      fOut.flush();
      fOut.close();
    } catch (FileNotFoundException e) {
      Log.e(TAG, "保存本地图片失败，msg=" + e.getMessage());
      e.printStackTrace();
    } catch (IOException e) {
      Log.e(TAG, "保存本地图片失败，msg=" + e.getMessage());
      e.printStackTrace();
    }

    Log.i(TAG, "保存网络图片成功" + filePath);
  }

  /**
   * 初始化缓存
   */
  private void initCache() {
    mFirstLevelCache = new LinkedHashMap<String, Bitmap>(MAX_CAPACITY / 2, 0.75f, true) {
      private static final long serialVersionUID = 1L;

      protected boolean removeEldestEntry(Entry<String, Bitmap> eldest) {
        if (size() > MAX_CAPACITY) {
          mSecondLevelCache.put(eldest.getKey(), new SoftReference<Bitmap>(eldest.getValue()));
          return true;
        }
        return false;
      };
    };

    mSecondLevelCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>(MAX_CAPACITY / 2);
  }

  /**
   * 创建默认图
   */
 /* public Bitmap getDefaultBitmap(int resId) {
    Drawable drawable = Launcher.getInstance().getResources().getDrawable(resId);
    BitmapDrawable bd = (BitmapDrawable) drawable;
    return bd.getBitmap();
  }
*/
  /**
   * 判断网络状态
   */
/*  public boolean isNetworkAvailable() {
    ConnectivityManager mConnectivityManager =
        (ConnectivityManager) Launcher.getInstance().getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo mNetworkInfo = mConnectivityManager.getActiveNetworkInfo();
    if (mNetworkInfo != null) {
      return mNetworkInfo.isAvailable();
    }
    return false;
  }*/

  /**
   * 移除指定缓存
   * 
   * @param url
   */
  public void deleteCacheByKey(String url) {
    if (url==null || "".equals(url.trim())) {
      return;
    }
    String filePath = saveDir + url.substring(url.lastIndexOf("/") + 1);;
    File file = new File(filePath);
    if (file.exists()) {
      file.delete();
    }
    mFirstLevelCache.remove(url);
    mSecondLevelCache.remove(url);
  }

  /**
   * 释放资源
   */
  public void recycleImageLoader() {
    new Thread(new Runnable() {
      @Override
      public void run() {
        if (pool != null) {
          pool.shutdownNow();
        }
        if (mFirstLevelCache != null) {
          for (Bitmap bmp : mFirstLevelCache.values()) {
            if (bmp != null) {
              bmp.recycle();
              bmp = null;
            }
          }
          mFirstLevelCache.clear();
          mFirstLevelCache = null;
        }
        if (mSecondLevelCache != null) {
          mSecondLevelCache.clear();
        }
        //清除文件夹下的所有image
        deleteAllImages();
      }
    }).start();
  }

  /**
   * 删除sd所有缓存图片
   */
  public void deleteAllImages(){
    File dirFile = new File(saveDir);
    if (dirFile.exists()==false) {
      return;
    }
    if (dirFile.isFile()) {
      dirFile.delete();
      return;
    }
    if (dirFile.isDirectory()) {
      File[] imageFiles = dirFile.listFiles();
      if (imageFiles==null || imageFiles.length == 0) {
        return;
      }
      for (File file : imageFiles) {
        if (file.isFile()) {
          file.delete();
        }
      }
    }
  }
  /**
   * 根据图片本地存储地址获取
   * 
   * @param imagePath
   * @return
   */
  public Bitmap getFromLocalByPath(String imagePath) {
    if (TextUtils.isEmpty(imagePath)) {
      return null;
    }
    try {
      BufferedInputStream in = new BufferedInputStream(new FileInputStream(new File(imagePath)));
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(in, null, options);
      in.close();
      int i = 0;
      Bitmap bitmap = null;
      while (true) {
        if ((options.outWidth >> i <= 256) && (options.outHeight >> i <= 256)) {
          in = new BufferedInputStream(new FileInputStream(new File(imagePath)));
          options.inSampleSize = (int) Math.pow(2.0D, i);
          options.inJustDecodeBounds = false;
          bitmap = BitmapFactory.decodeStream(in, null, options);
          break;
        }
        i += 1;
      }
      return bitmap;
    } catch (IOException e) {
      Log.d(TAG, "本地图片获取失败");
      return null;
    }
  }
  
  
  /**
   * 根据指定倍率调整图片大小
   * 
   * @param res
   * @param resId
   * @param sampleSize
   * @return
   */
  public Bitmap resizeBitmap(InputStream iStream,int sampleSize) {
    // 首先不加载图片,仅获取图片尺寸
    final BitmapFactory.Options options = new BitmapFactory.Options();
    // 当inJustDecodeBounds设为true时,不会加载图片仅获取图片尺寸信息
    options.inJustDecodeBounds = true;
    // 此时仅会将图片信息会保存至options对象内,decode方法不会返回bitmap对象
    BitmapFactory.decodeStream(iStream);
    // 计算压缩比例,如inSampleSize=4时,图片会压缩成原图的1/4
    options.inSampleSize = sampleSize;
    // 当inJustDecodeBounds设为false时,BitmapFactory.decode...就会返回图片对象了
    options.inJustDecodeBounds = false;
    // 利用计算的比例值获取压缩后的图片对象
    return BitmapFactory.decodeStream(iStream);
  }
  
  public static Bitmap resizeBitmap(Resources res, int resId, int sampleSize) {
    // 首先不加载图片,仅获取图片尺寸
    final BitmapFactory.Options options = new BitmapFactory.Options();
    // 当inJustDecodeBounds设为true时,不会加载图片仅获取图片尺寸信息
    options.inJustDecodeBounds = true;
    // 此时仅会将图片信息会保存至options对象内,decode方法不会返回bitmap对象
    BitmapFactory.decodeResource(res, resId, options);
    // 计算压缩比例,如inSampleSize=4时,图片会压缩成原图的1/4
    options.inSampleSize = sampleSize;
    // 当inJustDecodeBounds设为false时,BitmapFactory.decode...就会返回图片对象了
    options.inJustDecodeBounds = false;
    // 利用计算的比例值获取压缩后的图片对象
    return BitmapFactory.decodeResource(res, resId, options);
  }

  /**
   * 根据需要大小，计算缩放比例
   * 
   * @param reqWidth
   * @param reqHeight
   * @param options
   * @return
   */
  private int getSampleSize(int reqWidth, int reqHeight, BitmapFactory.Options options) {
    // 保存图片原宽高值
    final int height = options.outHeight;
    final int width = options.outWidth;
    // 初始化压缩比例为1
    int inSampleSize = 1;

    // 当图片宽高值任何一个大于所需压缩图片宽高值时,进入循环计算系统
    if (height > reqHeight || width > reqWidth) {

      final int halfHeight = height / 2;
      final int halfWidth = width / 2;

      // 压缩比例值每次循环两倍增加,
      // 直到原图宽高值的一半除以压缩值后都~大于所需宽高值为止
      while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
        inSampleSize *= 2;
      }
    }
    return inSampleSize;
  }
}
