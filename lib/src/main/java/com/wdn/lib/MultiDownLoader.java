package com.wdn.lib;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class MultiDownLoader {
    private static final String path               = "http://192.168.1.6:8080/cosbrowser-setup-1.1.6.exe";
    public static final  int    TOTAL_THREAD_COUNT =3;
    public static        int    runningThreadCount =0;
    public static void main(String[] args) {
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
                int blockSize=length/TOTAL_THREAD_COUNT;
                System.out.println("every block size:"+blockSize);
                runningThreadCount=TOTAL_THREAD_COUNT;
                for (int threadId=0;threadId<TOTAL_THREAD_COUNT;threadId++){
                    int startPosition=threadId*blockSize;
                    int endPosition=(threadId+1)*blockSize-1;
                    if (threadId==(TOTAL_THREAD_COUNT-1)){
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

    /**
     * 从网络路径获取文件名
     * @param path
     *      网络路径
     * @return 文件名
     */
    private static String getDownLoadFileName(String path) {
        return path.substring(path.lastIndexOf("/")+1);
    }

    /**
     * 下载文件的线程
     */
    private static class DownLoadThread extends Thread{
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

        public DownLoadThread(int threadId, int startPosition, int endPosition) {
            this.threadId=threadId;
            this.startPosition=startPosition;
            this.endPosition=endPosition;
        }

        @Override
        public void run() {
            System.out.println("threadId"+threadId+"begin working");
            //lest thread download it's self range data
            try {
                File file = new File(TOTAL_THREAD_COUNT + getDownLoadFileName(path) + threadId + ".txt");
                if (file.exists()&&file.length()>0){
                    FileInputStream fileInputStream=new FileInputStream(file);
                    BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(fileInputStream));
                    String lastPosition=bufferedReader.readLine();
                    //this thread 上一次下载的数据
                    int intLastPosition = Integer.parseInt(lastPosition);
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
                        RandomAccessFile inforaf = new RandomAccessFile(TOTAL_THREAD_COUNT + getDownLoadFileName(path) + threadId + ".txt","rwd");
                   //save position of current thread
                        inforaf.write(String.valueOf(startPosition+total).getBytes());
                        inforaf.close();
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
                synchronized (MultiDownLoader.class){
                runningThreadCount--;
                if (runningThreadCount<=0){
                    System.out.println("multi thread download complete.");
                    for (int i=0;i<TOTAL_THREAD_COUNT;i++){
                        File file = new File(TOTAL_THREAD_COUNT + getDownLoadFileName(path) + i + ".txt");
                      /*  System.out.println(file.delete());*/
                    }
                }
            }
            }

        }
    }
}
