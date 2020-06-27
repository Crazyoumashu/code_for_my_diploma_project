package com.huanghw.opencv4android.imgproc;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.huanghw.opencv4android.FaceFeature;
import com.huanghw.opencv4android.R;
import com.huanghw.opencv4android.Utils;
import com.huanghw.opencv4android.natives.DetectionBasedTracker;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.JavaCameraView;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;

import static com.huanghw.opencv4android.FaceNetActivity.RestoreFeature;

public class ImgProcessActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static final int JAVA_DETECTOR = 0;
    private static final int NATIVE_DETECTOR = 1;

    private static final String TAG = "FaceDetectActivity";
    @BindView(R.id.cameraView_face)
    CameraBridgeViewBase mCameraView;
    private Mat mGray;
    private Mat mRgba;
    private int mDetectorType = NATIVE_DETECTOR;
    private int mAbsoluteFaceSize = 0;
    private static int COMPLETED = 1;
    private float mRelativeFaceSize = 0.2f;
    private DetectionBasedTracker mNativeDetector;
    private CascadeClassifier mJavaDetector;
    private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
    TextView factor;
    //性别和年龄识别区域
    private Net mAgeNet;
    private static  final String[] AGES = new String[]{"0-2", "4-6", "8-13", "15-20", "25-32", "38-43", "48-53", "60+"};
    private Net mGenderNet;
    private static final String[] GENDERS = new String[]{"男","女","未知","未知"};
    private Handler handler;


    private List<Map<String,FaceFeature>> List_Map_Facefeature = new ArrayList<>();
    private File mCascadeFile;
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    // OpenCV初始化加载成功，再加载本地so库
                    System.loadLibrary("opencv341");
                    try {
                        // 加载人脸检测模式文件
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);
                        byte[] buffer = new byte[4096];
                        int byteesRead;
                        while ((byteesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, byteesRead);
                        }
                        is.close();
                        os.close();
                        // 使用模型文件初始化人脸检测引擎
                        mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetector.empty()) {
                            Log.e(TAG, "加载cascade classifier失败");
                            mJavaDetector = null;
                        } else {
                            Log.d(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());
                        }
                        mNativeDetector = new DetectionBasedTracker(mCascadeFile.getAbsolutePath(),"", 0);
                        cascadeDir.delete();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // 开启渲染Camera
                    mCameraView.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };
    //摄像头切换
    private int mCameraIndexCount = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_facedetect);
        factor = (TextView)findViewById(R.id.factors);
        handler = new Handler(){
            @Override
                public void handleMessage(Message msg){
                if(msg.what == COMPLETED){
                    String text = (String) msg.obj;
                    factor.setText(text);
                }
            }
        };
        List_Map_Facefeature = RestoreFeature();
        // 绑定View
        ButterKnife.bind(this);
        mCameraView.setCameraIndex(-1);
        mCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        // 注册Camera渲染事件监听器
        mCameraView.setCvCameraViewListener(this);
        findViewById(R.id.btn_swap).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCameraView.disableView();
                int num = Utils.getCameraCount();
                mCameraView.setCameraIndex(++mCameraIndexCount % num);
                mCameraIndexCount = mCameraIndexCount % num;
                mCameraView.enableView();
            }});
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 静态初始化OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "无法加载OpenCV本地库，将使用OpenCV Manager初始化");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "成功加载OpenCV本地库");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 停止渲染Camera
        if (mCameraView != null) {
            mCameraView.disableView();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止渲染Camera
        if (mCameraView != null) {
            mCameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        // 灰度图像
        mGray = new Mat();
        // R、G、B彩色图像
        mRgba = new Mat();

        //加载性别模块
        String proto = getPath("gender_squeeze.prototxt");
        String weights = getPath("gender_squeeze.caffemodel");
//        String proto = getPath("gender_4.prototxt");
//        String weights = getPath("gender_4.caffemodel");
        Log.i(TAG,"onCameraViewStarted| genderProto: "+proto+",genderWeights: "+weights);
        mGenderNet = Dnn.readNetFromCaffe(proto,weights);
        //加载年龄模块
        proto = getPath("squeezenet.prototxt");
        weights = getPath("squeezenet_age_73000.caffemodel");
//        proto = getPath("deploy_age.prototxt");
//        weights = getPath("age1_train.caffemodel");
        Log.i(TAG,"onCameraViewStarted| ageProto: "+proto+",ageWeights: "+weights);
        mAgeNet = Dnn.readNetFromCaffe(proto,weights);



        if (mAgeNet.empty()) {
            Log.i(TAG, "Network loading failed");
        } else {
            Log.i(TAG, "Network loading success");
        }
    }

    @Override
    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        if(this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (mCameraIndexCount != 0) {
                Core.flip(mRgba, mRgba, 1);//flip aroud Y-axis
                Core.flip(mGray, mGray, 1);
            }
        }
        else{
            if (mCameraIndexCount != 0) {
                //Core.rotate(mRgba, mRgba, Core.ROTATE_90_COUNTERCLOCKWISE);
                //Core.rotate(mGray, mGray, Core.ROTATE_90_COUNTERCLOCKWISE);
                Core.flip(mRgba, mRgba, 1);
                Core.flip(mGray, mGray, 1);
            } else {
                //Core.rotate(mRgba, mRgba, Core.ROTATE_90_CLOCKWISE);
                //Core.rotate(mGray, mGray, Core.ROTATE_90_CLOCKWISE);
            }
        }
        // 设置脸部大小
        if (mAbsoluteFaceSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
            mNativeDetector.setMinFaceSize(mAbsoluteFaceSize);
        }
        // 获取检测到的脸部数据
        MatOfRect faces = new MatOfRect();
        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetector != null) {
                mJavaDetector.detectMultiScale(mGray, faces, 1.1, 2, 2,
                        new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
            }
        } else if (mDetectorType == NATIVE_DETECTOR) {
            if (mNativeDetector != null) {
                mNativeDetector.detect(mGray, faces);
            }
        } else {
            Log.e(TAG, "Detection method is not selected!");
        }
        Rect[] facesArray = faces.toArray();
        // 绘制检测框
        for (Rect rect : facesArray) {
            Imgproc.rectangle(mRgba, rect.tl(), rect.br(), FACE_RECT_COLOR, 2);
            long t1 = System.currentTimeMillis();
            String age = analyseAge(mRgba, rect);
            long t2 = System.currentTimeMillis();
            String gender = analyseGender(mRgba, rect);
            long t3 = System.currentTimeMillis();
            Log.e("error", "测算年龄时间"+String.valueOf(t2-t1));
            Log.e("error", "测算性别时间"+String.valueOf(t3-t2));
            Log.e(TAG,"年龄为： "+ age);
            Log.e(TAG,"性别为：" + gender);
            String text = "年龄为： "+ age +"\n" + "性别为：" + gender;
            Message msg = new Message();
            msg.what = COMPLETED;
            msg.obj = text;
            handler.sendMessage(msg);
        }
        return mRgba;
    }
    public String getPath(String file){
        AssetManager assetManager = getApplicationContext().getAssets();
        BufferedInputStream inputStream;

        try{
            //Reading data from app/src/main/assets
            inputStream = new BufferedInputStream(assetManager.open(file));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();

            File outputFile = new File(getApplicationContext().getFilesDir(),file);
            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
            fileOutputStream.write(data);
            fileOutputStream.close();
            return outputFile.getAbsolutePath();
        }catch (IOException e){
            Log.e(TAG,e.toString());
        }
        return "";
    }
    private String analyseAge(Mat mRgba, Rect face){
        try{
            Mat captureFace = new Mat(mRgba, face);
            //Resizing pictures to resolution of Caffe model
            Imgproc.resize(captureFace,captureFace,new Size(227,227));
            //Converting RGBA to BGR
            Imgproc.cvtColor(captureFace,captureFace,Imgproc.COLOR_RGB2BGR);
            //Forwarding picture through Dnn
            Mat inputBlob = Dnn.blobFromImage(captureFace, 1.0f, new Size(227,227),
                    new Scalar(78.4263377603, 87.7689143744, 114.895847746), false,false);
            mAgeNet.setInput(inputBlob,"data");
            Mat probs = mAgeNet.forward("prob").reshape(1,1);
            Core.MinMaxLocResult mm = Core.minMaxLoc(probs); //Getting largest softmax output

            double result = mm.maxLoc.x;//Result of age recognition prediction
            Log.i(TAG, "Result is: "+ result);
            return AGES[(int) result];
        }catch (Exception e){
            Log.e(TAG, "Error processing age", e);
        }
        return null;
    }

    private String analyseGender(Mat mRgba, Rect face){
        try{
            Mat captureFace = new Mat(mRgba, face);
            //Resizing pictures to resolution of Caffe model
            Imgproc.resize(captureFace,captureFace, new Size(227,227));
            //Converting RGBA to BGR
            Imgproc.cvtColor(captureFace,captureFace,Imgproc.COLOR_RGBA2BGR);
            //Forwarding picture through Dnn
            Mat inputBlob = Dnn.blobFromImage(captureFace, 1.0f, new Size(227,227),
                    new Scalar(78.4263377603, 87.7689143744, 114.895847746), false, false);
            mGenderNet.setInput(inputBlob, "data");
            Mat probs = mGenderNet.forward("prob").reshape(1,1);
            Core.MinMaxLocResult mm = Core.minMaxLoc(probs); //Getting largest softmax output

            double result = mm.maxLoc.x;
            Log.i(TAG,"Result is: "+result);
            return GENDERS[(int) result];
        }catch (Exception e){
            Log.e(TAG, "Error processing gender", e);
        }
        return null;
    }
}
