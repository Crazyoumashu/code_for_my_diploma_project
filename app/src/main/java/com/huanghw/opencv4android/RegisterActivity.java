package com.huanghw.opencv4android;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import static android.hardware.Camera.open;
import static com.huanghw.opencv4android.FaceNetActivity.getImagePathFromSD;
import static com.huanghw.opencv4android.ImgUtils.saveImageToGallery;

import static com.huanghw.opencv4android.FaceNetActivity.RestoreFeature;

public class RegisterActivity  extends AppCompatActivity {
    private SurfaceView sfv_preview;
    private Button btn_take;
    private ImageButton btn_swap;
    private Camera camera = null;
    public Bitmap bitmap1;
    public ImageView imageView4;
    public EditText editText;
    public MTCNN mtcnn;
    public Facenet facenet;
    public static String Photo_Path = Environment.getExternalStorageDirectory().getAbsolutePath()+ File.separator +"FACE";
    AutoFocusCallback autoFocusCallback;
    private static final int MSG_AUTOFUCS = 1001;
    private Handler handler;
    //用于储存保存的对比照
    public static String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator +"FACE";
    //用于切换摄像头
    private static final int FRONT = 1;//前置摄像头标记
    private static final int BACK = 2;//后置摄像头标记
    private int currentCameraType = 2;//当前打开的摄像头标记

    private List<Map<String,FaceFeature>> List_Map_Facefeature = new ArrayList<>();

    @Override
    protected void onCreate(Bundle saveInstanceState){
        super.onCreate(saveInstanceState);
        setContentView(R.layout.register_view);
        imageView4 = findViewById(R.id.imageView4);
        bindViews();
        autoFocusCallback = new AutoFocusCallback();
        autoFocusCallback.setHandler(handler, MSG_AUTOFUCS);
        List_Map_Facefeature = RestoreFeature();
    }

    private void bindViews(){
        mtcnn = new MTCNN(getAssets());
        facenet=new Facenet(getAssets());
        sfv_preview = (SurfaceView) findViewById(R.id.sfv_preview);
        btn_take = (Button) findViewById(R.id.btn_take);
        sfv_preview.getHolder().addCallback(cpHolderCallback);
        btn_swap = (ImageButton) findViewById(R.id.btn_swap);

        btn_take.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                camera.takePicture(null,null, new Camera.PictureCallback(){
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera){
                        String path = "";
                        if((path = saveFile(data))!=null){
                            imageView4.setImageURI(Uri.fromFile(new File(path)));
                            bitmap1 = ((BitmapDrawable) imageView4.getDrawable()).getBitmap();
                            bitmap1 = Getface(bitmap1);
                            imageView4.setImageBitmap(bitmap1);
                            if(bitmap1==null)
                            {
                                Toast.makeText(RegisterActivity.this,"未检测到人脸", Toast.LENGTH_SHORT).show();
                            }else{
                                String file_name = saveImageToGallery(RegisterActivity.this,bitmap1);
                                Toast.makeText(RegisterActivity.this,"保存照片成功", Toast.LENGTH_SHORT).show();
                                try{
                                    StoreFeature(bitmap1,file_name);
                                }catch (IOException e){
                                    e.printStackTrace();
                                }

                            }
                        }else{
                            Toast.makeText(RegisterActivity.this, "保存照片失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
        btn_swap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    changeCamera();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public Bitmap Getface(Bitmap face){
        Vector<Box> boxes = mtcnn.detectFaces(face,40);
        if(boxes.size()==0) return null;
        Rect rect1 = boxes.get(0).transform2Rect();
        int margin = 20;
        Utils.rectExtend(bitmap1,rect1,margin);
        Bitmap bm = Utils.crop(bitmap1,rect1);
        if(currentCameraType == BACK )
        return Utils.adjustPhotoRotation(bm,90);
        else return Utils.convert(bm);

    }

    public void StoreFeature(Bitmap bitmap1, String file_name) throws IOException{
        Vector<Box> boxes = mtcnn.detectFaces(bitmap1,40);
        Map<String, FaceFeature> map1 = new HashMap<>();
        if(boxes.size()==0){
            Log.e("error","未检测到人脸");
        }else{
            Log.e("error","检测到人脸");
            Rect rect1 = boxes.get(0).transform2Rect();
            Utils.rectExtend(bitmap1,rect1,20);
            Bitmap face_ = Utils.crop(bitmap1, rect1);
            FaceFeature face = facenet.recognizeImage(face_);
            map1.put(Photo_Path+File.separator+file_name,face);
            List_Map_Facefeature.add(map1);
            try{
                File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+File.separator+"FACE"+File.separator+"storefeature.txt");
                ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file));
                for(Map<String, FaceFeature>map : List_Map_Facefeature){
                    os.writeObject(map);
                }
                os.writeObject(null);
                os.close();
            }catch (FileNotFoundException e){
                e.printStackTrace();
            }catch (IOException e){
                e.printStackTrace();
            }
            Log.e("error", "总共特征数为："+ String.valueOf(List_Map_Facefeature.size()));
            Log.e("error","-----------------保存结束-----------------");
            map1.clear();
        }
    }
    public void GetFeature(){
        List<String> getImagePath = getImagePathFromSD(filePath);
        List<Bitmap> bitmap_set = new ArrayList<>();
        Map<String,FaceFeature>map1 = new HashMap<>();
        List<Map<String, FaceFeature>> List_map = new ArrayList<>();
        for(String data:getImagePath){
            try{
                FileInputStream fis = new FileInputStream(data);
                Bitmap bitmap = BitmapFactory.decodeStream(fis);
                bitmap_set.add(bitmap);
            }catch (FileNotFoundException e){
                e.printStackTrace();
            }
            Log.e("error",data);
        }
        int i = 0;
        for(Bitmap bm:bitmap_set){

            Log.e("error","正在提取第"+ String.valueOf(i)+"个特征……");
            Vector<Box> boxes = mtcnn.detectFaces(bm,40);
            if(boxes.size()==0){
                Log.e("error","未检测到人脸");
            }
            else{
                Rect rect1 = boxes.get(0).transform2Rect();
                Utils.rectExtend(bm,rect1,20);
                Bitmap face_ = Utils.crop(bm,rect1);
                FaceFeature face = facenet.recognizeImage(face_);
                map1.put(getImagePath.get(i),face);
                List_map.add(map1);
                i++;
            }
        }
        Log.e("error", "提取特征结束，开始保存……");
        try{
            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+File.separator+"FACE"+File.separator+"storefeature.txt");
            ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file));
            for(Map<String, FaceFeature>map : List_map){
                os.writeObject(map);
            }
            os.writeObject(null);
            os.close();
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }catch (IOException e){
            e.printStackTrace();
        }

        Log.e("error", "总共特征数为："+ String.valueOf(List_map.size()));
        Log.e("error","-----------------保存结束-----------------");
        Toast.makeText(RegisterActivity.this,"总共特征数为："+ String.valueOf(List_map.size())+"\n"+"-----------------保存结束-----------------",
                Toast.LENGTH_SHORT).show();
        map1.clear();
        bitmap_set.clear();
        List_map.clear();
    }
    //保存临时文件的方法
    private String saveFile(byte[] bytes){
        try{
            File file = File.createTempFile("img","");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.flush();
            fos.close();
            return file.getAbsolutePath();
        }catch (IOException e){
            e.printStackTrace();
        }
        return "";
    }
    //开始预览
    private void startPreview(){
        camera = open();
        try{
            camera.setPreviewDisplay(sfv_preview.getHolder());
            camera.setDisplayOrientation(90); //让相机旋转90度
            camera.startPreview();
            camera.autoFocus(autoFocusCallback);
        }catch (IOException e){
            e.printStackTrace();
        }
    }
    //停止预览
    private void stopPreview(){
        camera.stopPreview();
        camera.release();
        camera = null;
    }
    private SurfaceHolder.Callback cpHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            startPreview();
        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            stopPreview();
        }
    };

    private void changeCamera() throws IOException{
        camera.stopPreview();
        camera.release();
        if(currentCameraType == FRONT){
            camera = openCamera(BACK);
        }else if(currentCameraType == BACK){
            camera = openCamera(FRONT);
        }
        camera.setPreviewDisplay(sfv_preview.getHolder());
        camera.setDisplayOrientation(90);
        camera.startPreview();
    }
    @SuppressLint("NewApi")
    private Camera openCamera(int type){
        int frontIndex =-1;
        int backIndex = -1;
        int cameraCount = Camera.getNumberOfCameras();
        Camera.CameraInfo info = new Camera.CameraInfo();
        for(int cameraIndex = 0; cameraIndex<cameraCount; cameraIndex++){
            Camera.getCameraInfo(cameraIndex, info);
            if(info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT){
                frontIndex = cameraIndex;
            }else if(info.facing == Camera.CameraInfo.CAMERA_FACING_BACK){
                backIndex = cameraIndex;
            }
        }

        currentCameraType = type;
        if(type == FRONT && frontIndex != -1){
            return Camera.open(frontIndex);
        }else if(type == BACK && backIndex != -1){
            return Camera.open(backIndex);
        }
        return null;
    }

    /**
     * 重写对象输出流,不让它写头文件
     *
     * @author Huangbin
     *
     */
    static class MyObjectWrite extends ObjectOutputStream {

        private static File f;
        public MyObjectWrite(OutputStream out, File f) throws IOException {
            super(out);// 会调用writeStreamHeader()
        }

        public static MyObjectWrite newInstance(File file, OutputStream out) throws IOException {
            f = file;// 本方法最重要的地方：构建文件对象，两个引用指向同一个文件对象
            return new MyObjectWrite(out, f);
        }
        @Override
        protected void writeStreamHeader() throws IOException {
            // 文件不存在或文件为空,此时是第一次写入文件，所以要把头部信息写入。
            if (!f.exists() || (f.exists() && f.length() == 0)) {
                super.writeStreamHeader();
            } else {
                // 不需要做任何事情
            }

        }
    }
}
