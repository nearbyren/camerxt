package com.example.camerx;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;
import android.util.TypedValue;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

//////////////////////////////////////////////////////////////
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageAnalysis;
//import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
//import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Size;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.camerx.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
///////////////////////////////////////////////////////////////

public class MainActivity extends AppCompatActivity {

    private  String TAG = "MainActivity";
    // 动态获取权限
    private int REQUEST_CODE_PERMISSIONS=101;
    private final   String[]REQUIRED_PERMISSIONS=new    String[]{"android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE","android.permission.READ_EXTERNAL_STORAGE"};
    private ImageView imageView,imageView1;
    private Button btn_select_image;
    private static final int SELECT_IMAGE = 1;
    private SeetaFace seetaFace=new SeetaFace(); //初始化一个SeetaFace
    private static List<String> lst = new ArrayList<String>();  //初始化模型列表
    private TextView  text;
    private Button toggleCamera;
    private boolean isBackCamera =false;
    static {
        lst.add("age_predictor.csta");
        lst.add("eye_state.csta");
        lst.add("face_detector.csta");
        lst.add("face_landmarker_mask_pts5.csta");
        lst.add("face_landmarker_pts5.csta");
        lst.add("face_landmarker_pts68.csta");
        lst.add("fas_first.csta");
        lst.add("fas_second.csta");
        lst.add("gender_predictor.csta");
        lst.add("mask_detector.csta");
        lst.add("post_estimation.csta");
    }
    private ExecutorService cameraExecutor;
    private ActivityMainBinding mViewBinding;
   private PreviewView viewFinder;
  //  public   ImageView imageView ;
    private  long  lasttimestamp =  0L;
    private  long curIndex =  0;
    ExecutorService msingle = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreen();
        mViewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mViewBinding.getRoot());
       // setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.imageView);
        text = findViewById(R.id.text);
        toggleCamera= findViewById(R.id.toggle_camera);
        toggleCamera.setText("toggleCamera");
        toggleCamera.setTextSize(TypedValue.COMPLEX_UNIT_PX,16);
//        imageView1= findViewById(R.id.imageView1);
//        btn_select_image = findViewById(R.id.btn_select_image);
   //     cameraExecutor = Executors.newSingleThreadExecutor();
        cameraExecutor = Executors.newFixedThreadPool(2);
    //    viewFinder = (PreviewView)findViewById(R.id.viewFinder);
        toggleCamera.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                isBackCamera = !isBackCamera;
                startCamera();
            }
        });
        // 检查权限并启动摄像头
        if (allPermissionsGranted()) {
          //  startCamera(); // 打开摄像头方法
            loadSeetaFaceModule();
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

//        btn_select_image.setOnClickListener(new View.OnClickListener(){
//            @Override
//            public void onClick(View v) {
//                Intent i = new Intent(Intent.ACTION_PICK);   //
//                i.setType("image/*"); //设置图片类型
//                startActivityForResult(i, SELECT_IMAGE);  //跳转界面,执行新的操作, SELECT_IMAGE代码在下面
//            }
//        });

       // setupcamera();
    }

    //全屏
    private void fullscreen(){
        if (getSupportActionBar() != null){   //继承的是 AppCompatActivity
            getSupportActionBar().hide();  //////这行代码必须写在setContentView()方法的后面
        }else{
            this.requestWindowFeature(Window.FEATURE_NO_TITLE);  //继承  Activity   //这行代码必须写在setContentView()方法的前面
        }
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void setupcamera(){
        // 检查权限并启动摄像头
        if (allPermissionsGranted()) {
            startCamera(); // 打开摄像头方法
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    // 检查权限方法
    private boolean allPermissionsGranted(){
        //check if req permissions have been granted
        for(String permission : REQUIRED_PERMISSIONS){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
       // super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {

            if (allPermissionsGranted()) {
                loadSeetaFaceModule();
                startCamera();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // 打开摄像头方法
    @SuppressLint("UnsafeOptInUsageError")
    private void startCamera() {
        // 将Camera的生命周期和Activity绑定在一起（设定生命周期所有者），这样就不用手动控制相机的启动和关闭。
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // 将你的相机和当前生命周期的所有者绑定所需的对象
                ProcessCameraProvider processCameraProvider = cameraProviderFuture.get();

                // 创建一个Preview 实例，并设置该实例的 surface 提供者（provider）。
//                PreviewView viewFinder = (PreviewView)findViewById(R.id.viewFinder);
//                Preview preview = new Preview.Builder()
//                        .build();
//                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // 选择前置摄像头作为默认摄像头
                CameraSelector cameraSelector = isBackCamera?CameraSelector.DEFAULT_BACK_CAMERA:CameraSelector.DEFAULT_FRONT_CAMERA;

                // 创建拍照所需的实例
             //   imageCapture = new ImageCapture.Builder().build();

                // 设置预览帧分析
//                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
//                        .build();
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                   .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .setTargetResolution(new Size(720, 1280)) // 图片的建议尺寸
                        .setOutputImageRotationEnabled(true) // 是否旋转分析器中得到的图片
                        .setTargetRotation(Surface.ROTATION_0) // 允许旋转后 得到图片的旋转设置
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setImageQueueDepth(1)
                        .build();
              //  imageAnalysis.setAnalyzer(cameraExecutor, new MyAnalyzer());
                imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    // 下面处理数据
                    // runOnUiThread(() -> Toast.makeText(getApplicationContext(), "截取一帧", Toast.LENGTH_SHORT).show());
                    Bitmap bitmap = toBitmap(imageProxy.getImage()); //toBitmap  //yuv420ToBitmap
//                    long cur = System.currentTimeMillis();
//                    if(cur > lasttimestamp +50L) { //1秒 最多33 frame
//                        lasttimestamp = cur;
//                        curIndex++;
//                      //  dealPicture(bitmap);
//                    }
                 //   Bitmap  bitmapOut = seetaFace_detectEx(bitmap);

                    int nResult = seetaFace_detectEx(bitmap);
                    String str = "sex:" + (nResult &1) + " age="+ (nResult/2) ;
                    runOnUiThread(() ->{imageView.setImageBitmap(bitmap);text.setText(str); });  //text.setText(str);

                    imageProxy.close(); // 最后要关闭这个
                });

                // 重新绑定用例前先解绑
                processCameraProvider.unbindAll();

                // 绑定用例至相机
                processCameraProvider.bindToLifecycle(MainActivity.this, cameraSelector,
                        imageAnalysis);
//                processCameraProvider.bindToLifecycle(MainActivity.this, cameraSelector,
//                        preview,
//                        imageCapture,
//                        imageAnalysis);

            } catch (Exception e) {
                Log.e(TAG, "用例绑定失败！" + e);
            }
        }, ContextCompat.getMainExecutor(this));

    }


//    private ImageAnalysis setImageAnalysis() {
//
//    }

//    public static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
//        ByteBuffer byteBuffer = imageProxy.getPlanes()[0].getBuffer();
//        byte[] bytes = new byte[byteBuffer.remaining()];
//        byteBuffer.get(bytes);
//        return BitmapFactory.decodeByteArray(bytes,0,bytes.length);
//    }



    //TODO 把assert文件写入系统中
    private boolean copyAssetAndWrite(String fileName){
        try {
            File cacheDir=getCacheDir();
            if (!cacheDir.exists()){
                cacheDir.mkdirs();
            }
            File outFile =new File(cacheDir,fileName);
            if (!outFile.exists()){
                boolean res=outFile.createNewFile();
                if (!res){
                    return false;
                }
            }else {
                if (outFile.length()>10){//表示已经写入一次
                    return true;
                }
            }
            InputStream is=getAssets().open(fileName);
            FileOutputStream fos = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int byteCount;
            while ((byteCount = is.read(buffer)) != -1) {
                fos.write(buffer, 0, byteCount);
            }
            fos.flush();
            is.close();
            fos.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    //加载模型
    private void loadSeetaFaceModule(){
        for(int i=0;i<lst.size();i++){
            boolean mkdir_model = copyAssetAndWrite(lst.get(i));  //放在了缓存区
        }
        String dataPath = getCacheDir().getPath();
        Log.e("文件目录地址为:", dataPath);
        String[] functions={"landmark5","landmark68","live","age","bright","clarity","eyeState","faceMask","mask","gender","resolution","pose","integrity"};
        boolean ret_init = seetaFace.loadModel(dataPath,functions);
        if (ret_init){
            Log.i("加载模型:", "成功");   //+""即把bool转为字符串
           // seetaFace.InitLiveThreshold(0.4f,0.88f);
        }
        else
            Log.e("加载模型:", "失败");   //+""即把bool转为字符串
    }

    //分析
//    private static class MyAnalyzer implements ImageAnalysis.Analyzer{
//        private static  String TAG = "MyAnalyzer";
//        @SuppressLint("UnsafeOptInUsageError")
//        @Override
//        public void analyze(@NonNull ImageProxy image) {
//          //  image.getImageInfo().getRotationDegrees()
//            Log.d(TAG, "Image's stamp is " + Objects.requireNonNull(image.getImage()).getTimestamp()+" rotationDegress="+image.getImageInfo().getRotationDegrees());
//          //  viewFinder.
//            Bitmap bm = toBitmapRGBA2(image.getImage());
//            imageView.setImageBitmap(bm);
//            image.close();
//        }
//    }


    public static Bitmap toBitmapRGBA2(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        buffer.rewind();
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        Bitmap bitmap = Bitmap.createBitmap(image.getWidth()+rowPadding/pixelStride,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    //
    private Bitmap yuv420ToBitmap(Image image) {
        RenderScript rs = RenderScript.create(MainActivity.this);
        ScriptIntrinsicYuvToRGB script = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));

        // Refer the logic in a section below on how to convert a YUV_420_888 image
        // to single channel flat 1D array. For sake of this example I'll abstract it
        // as a method.
        byte[] yuvByteArray = image2byteArray(image);

        Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs)).setX(yuvByteArray.length);
        Allocation in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

        Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(image.getWidth())
                .setY(image.getHeight());
        Allocation out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);

        // The allocations above "should" be cached if you are going to perform
        // repeated conversion of YUV_420_888 to Bitmap.
        in.copyFrom(yuvByteArray);
        script.setInput(in);
        script.forEach(out);

        Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
        out.copyTo(bitmap);
        return bitmap;
    }

    private byte[] image2byteArray(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        // Full size Y channel and quarter size U+V channels.
        int numPixels = (int) (width * height * 1.5f);
        byte[] nv21 = new byte[numPixels];
        int index = 0;

        // Copy Y channel.
        int yRowStride = yPlane.getRowStride();
        int yPixelStride = yPlane.getPixelStride();
        for(int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                nv21[index++] = yBuffer.get(y * yRowStride + x * yPixelStride);
            }
        }

        // Copy VU data; NV21 format is expected to have YYYYVU packaging.
        // The U/V planes are guaranteed to have the same row stride and pixel stride.
        int uvRowStride = uPlane.getRowStride();
        int uvPixelStride = uPlane.getPixelStride();
        int uvWidth = width / 2;
        int uvHeight = height / 2;

        for(int y = 0; y < uvHeight; ++y) {
            for (int x = 0; x < uvWidth; ++x) {
                int bufferIndex = (y * uvRowStride) + (x * uvPixelStride);
                // V channel.
                nv21[index++] = vBuffer.get(bufferIndex);
                // U channel.
                nv21[index++] = uBuffer.get(bufferIndex);
            }
        }
        return nv21;
    }
    //seetaface
//    private  Bitmap seetaFace_detect(Bitmap bm){
//        int [] ret_box = seetaFace.detectFace(bm);//m.cols(),m.rows(),m.channels(),m.dataAddr()
//        if (ret_box == null) {
//            //Toast.makeText(getApplicationContext(), "未检测到人脸", Toast.LENGTH_SHORT).show();   //3.5s显示text
//            // Log.i("setImageAnalysis", "analyze: format:" + format);
//            return bm;
//        }
//
//        seetaFace.landmark(5); //特征点检测
//      //  seetaFace.landmark(68); //特征点检测
//     //   Bitmap bitmapOut = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), Bitmap.Config.ARGB_8888); //空白图像
//        seetaFace.detectDraw(true,true,false,bm);  //人脸框画图
//       // return  bitmapOut;
//        return  bm;
//    }

    private  int  seetaFace_detectEx(Bitmap bm){
        Log.i("seetaFace_detectEx", "我来了呀 seetaFace_detectEx java:");
        int nResult =  seetaFace.detectFaceEx(bm,27);// 1 detect  2 point5 4 point68 8 sex  16 age
        if(nResult < 0) {
            Log.i("seetaFace_detectEx", "seetaFace_detectEx:" + nResult);
        }else{
           // Log.i("seetaFace_detectEx", "sex:" + (nResult &1) + " age="+ (nResult/2));

        }
        return  nResult;
    }

    private  void dealPicture(Bitmap bm){
        msingle.execute(new Runnable() {
            @Override
            public void run() {
                long cur = System.currentTimeMillis();
                int nResult =  seetaFace.detectFaceEx(bm,1);//27
                long cur2 = System.currentTimeMillis();
                String str = "NULL" ;
                if(nResult > 0 ){
                    str = "sex:" + (nResult & 1) + " age=" + (nResult / 2);
                }
                String finalStr = str;

                Log.d(TAG,"cur="+cur+str+" diff="+(cur2-cur));
                runOnUiThread(() -> {
                    imageView.setImageBitmap(bm);
                    text.setText(finalStr);
                });

            }
        });
    }
}