package com.wdn.multidownloader;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private EditText et_num;
    private EditText et_path;
    private LinearLayout ll_container;
    private String path               = "http://192.168.1.6:8080/cosbrowser-setup-1.1.6.exe";
    public  int    totalThreadCount =0;
    public  int    runningThreadCount =0;
    private ArrayList<ProgressBar> pbs;
    //上一次下载的数据的大小
    private int lastPositionSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //1.找到控件
        //2.在button点击事件当中下载文件
        //3.下载的时候要在界面显示下载的进程
        et_num = findViewById(R.id.edite_num);
        et_path = findViewById(R.id.edite_path);
        ll_container = findViewById(R.id.ll_container);
    }
public void download(View view){
        path=et_path.getText().toString().trim();
        String str_num=et_num.getText().toString().trim();
        totalThreadCount=Integer.parseInt(str_num);
        ll_container.removeAllViews();
    pbs = new ArrayList<>();
        for (int i=0;i<totalThreadCount;i++){
        ProgressBar pb= (ProgressBar) View.inflate(this,R.layout.pb,null);
        ll_container.addView(pb);
        pbs.add(pb);
       }
 new Thread(){
     @Override
     public void run() {
         try {
             URL url = new URL(path);
             HttpURLConnection urlConnection= (HttpURLConnection) url.openConnection();
             urlConnection.setRequestMethod("GET");
             int code=urlConnection.getResponseCode();
             if (code==200){
                 int length=urlConnection.getContentLength();
                 System.out.println("file length:"+length);
                 RandomAccessFile raf = new RandomAccessFile(getDownLoadFileName(path), "rw");
                 //创建一个空的文件并且设置它的文件长度等于服务器上的文件长度吗
                 raf.setLength(length);
                 raf.close();
                 int blockSize=length/totalThreadCount;
                 System.out.println("every block size:"+blockSize);
                 runningThreadCount=totalThreadCount;
                 for (int threadId=0;threadId<totalThreadCount;threadId++){
                     int startPosition=threadId*blockSize;
                     int endPosition=(threadId+1)*blockSize-1;
                     if (threadId==(totalThreadCount-1)){
                         endPosition=length-1;
                     }
                     System.out.println("threadid"+threadId+"range"+startPosition+"~~"+endPosition);
                     new DownLoadThread(threadId,startPosition,endPosition).start();
                 }
             }
         } catch (Exception e) {
             e.printStackTrace();
         }
     }
 }.start();
}
    private static String getDownLoadFileName(String path) {
        return path.substring(path.lastIndexOf("/")+1);
    }
    /**
     * 下载文件的线程
     */
    private  class DownLoadThread extends Thread{
        /**
         * 线程id
         */
        private int threadId;
        /**
         * 当前线程下载起始位置
         */
        private int startPosition;
        /**
         * 当前线程下载终止位置
         */
        private int endPosition;
        /**
         *当前线程需要下载的总共字节
         */

        private int threadTotal;
        public DownLoadThread(int threadId, int startPosition, int endPosition) {
            this.threadId=threadId;
            this.startPosition=startPosition;
            this.endPosition=endPosition;
            this.threadTotal=endPosition-startPosition;
            pbs.get(threadId).setMax(threadTotal);
          }

        @Override
        public void run() {
            System.out.println("threadId"+threadId+"begin working");
            //lest thread download it's self range data
            try {
                File file = new File(totalThreadCount + getDownLoadFileName(path) + threadId + ".txt");
                if (file.exists()&&file.length()>0){
                    FileInputStream fileInputStream=new FileInputStream(file);
                    BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(fileInputStream));
                    String lastPosition=bufferedReader.readLine();
                    //this thread 上一次下载的数据
                    int intLastPosition = Integer.parseInt(lastPosition);
                    //上一次下载的数据的大小
                    lastPositionSize = intLastPosition-startPosition;
                    startPosition=intLastPosition;
                    fileInputStream.close();
                }
                URL url = new URL(path);
                HttpURLConnection conn= (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                System.out.println("threadid"+threadId+"range"+startPosition+"~~"+endPosition);
                conn.setRequestProperty("Range","bytes"+startPosition+"-"+endPosition);
                //download resource from server
                int code=conn.getResponseCode();
                if (code==206){
                    InputStream is=conn.getInputStream();
                    RandomAccessFile rw = new RandomAccessFile(getDownLoadFileName(path), "rw");
                    rw.seek(startPosition);
                    byte[] bytes = new byte[1024];
                    int len=-1;
                    int total=0;
                    //download data of current thread this times
                    while ((len=is.read(bytes))!=-1){
                        rw.write(bytes,0,len);
                        //记录当前流的文件位置去下载
                        total+=len;
                        RandomAccessFile inforaf = new RandomAccessFile(totalThreadCount + getDownLoadFileName(path) + threadId + ".txt","rwd");
                        //save position of current thread
                        inforaf.write(String.valueOf(startPosition+total).getBytes());
                        inforaf.close();
                        pbs.get(threadId).setProgress(total+lastPositionSize);
                    }
                    is.close();
                    rw.close();
                    System.out.println("thread"+threadId+"download complete!");
                }else {
                    System.out.println("thread"+threadId+"download failed!");
                }
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                synchronized (MainActivity.class){
                    runningThreadCount--;
                    if (runningThreadCount<=0){
                        System.out.println("multi thread download complete.");
                        for (int i=0;i<totalThreadCount;i++){
                            File file = new File(totalThreadCount + getDownLoadFileName(path) + i + ".txt");
                            /*  System.out.println(file.delete());*/
                        }
                    }
                }
            }

        }
    }
}
