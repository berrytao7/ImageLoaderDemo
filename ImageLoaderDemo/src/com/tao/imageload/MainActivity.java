package com.tao.imageload;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;

public class MainActivity extends Activity {
/*
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    TextView textView = (TextView) findViewById(R.id.path);
    textView.setText(AsyncImageLoader.saveDir);
  }*/
  
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main); //设置layout布居

      // 使用线程异步加载数据，不阻塞界面。
      new Thread(){

          @Override
          public void run() {
              // TODO Auto-generated method stub
              super.run();
              initData();
          }
           
      }.start();
  }

  private final static int MSG_INIT_VIEW = 0xA00;
  private final Handler handler = new Handler() {

      @Override
      public void dispatchMessage(Message msg) {
          switch (msg.what) {
          case MSG_INIT_VIEW:
              initView();
              break;
          default:
              super.dispatchMessage(msg);
          }
           
           
      }
       
  };
   
  private void initData(){
      try {
          Thread.sleep(5000);// 模拟加载数据需要 5秒
      } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
      }
      //数据加载完成，可以更新界面了
      handler.sendEmptyMessage(MSG_INIT_VIEW);
  }
   
  private void initView(){
      //TODO 刷新界面
  }


}
