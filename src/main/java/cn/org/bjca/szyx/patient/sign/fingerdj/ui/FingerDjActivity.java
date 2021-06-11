package cn.org.bjca.szyx.patient.sign.fingerdj.ui;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.seuic.fingerprint.FingerprintService;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;

import cn.org.bjca.finger.ImageUtils;
import cn.org.bjca.finger.model.SignSubmitModel;
import cn.org.bjca.finger.value.ConstanceValue;
import cn.org.bjca.finger.value.Constant;
import cn.org.bjca.szyx.patient.sign.fingerdj.R;
import cn.org.bjca.szyx.patient.sign.fingerdj.bean.FingerprintBean;
import cn.org.bjca.szyx.patient.sign.fingerdj.finger.FingerManage;

public class FingerDjActivity extends AppCompatActivity {

    private static final String TAG = "FingerDjActivity";

    private LinearLayout linInitFinger;
    private LinearLayout linInitBottomFinger;
    private LinearLayout linRestartBottomFinger;
    private RelativeLayout rlErrorFinger;
    private ImageView mImgView;

    private Context mContext = null;
    private String fingerprintResultCode = null;
    private String handwrittenSignFile;
    private SignSubmitModel model;

    private static final int SUCCESS_MSG = 0; // 成功显示图像
    private static final int FAILED_MSG = 1; // 操作失败
    private static final int SHOW_IMG_INIT_MSG = 6; // 初始化显示图像
    private static final int RESTART_INIT_MSG = 8; // 重新初始化指纹
    // 特征
    private static final int TZ_SIZE = 512;
    private static final byte[] m_bFingerTz = new byte[TZ_SIZE];
    private static final byte[] m_bFingerMb = new byte[TZ_SIZE];
    FingerprintService fingerprintService;
    USBReceiver mUsbReceiver;
    // 图像
    int iTimeout = 15 * 1000; // 等待按手指的超时时间，单位：毫秒
    int iImageX = 256;
    int iImageY = 360;
    int iImageSize = iImageX * iImageY;
    byte[] bFingerImage = new byte[iImageSize];

    Resources m_res;
    Bitmap m_bitmap = null;
    private GetImageThread m_getImageThread = null;


    @SuppressLint("HandlerLeak")
    private Handler LinkDetectedHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SUCCESS_MSG:
                    if (m_bitmap != null) {
                        if (!m_bitmap.isRecycled()) {
                            m_bitmap.recycle();
                        }
                    }
                    m_bitmap = fingerprintService.raw2Bimap(bFingerImage, iImageX, iImageY, 1);
                    if (m_bitmap != null) {
                        m_bitmap = ImageUtils.changePng(m_bitmap);
                        mImgView.setImageBitmap(m_bitmap);
                        linInitBottomFinger.setVisibility(View.GONE);
                        linRestartBottomFinger.setVisibility(View.VISIBLE);
                    }
                    break;
                case FAILED_MSG:
                    linInitFinger.setVisibility(View.GONE);
                    rlErrorFinger.setVisibility(View.VISIBLE);
                    break;
                case SHOW_IMG_INIT_MSG:
                    if (m_bitmap != null) {
                        if (!m_bitmap.isRecycled()) {
                            m_bitmap.recycle();
                        }
                    }
                    m_bitmap = BitmapFactory.decodeResource(m_res,
                            R.mipmap.finger_image);
                    if (m_bitmap != null) {
                        mImgView.setImageBitmap(m_bitmap);
                    }
                    break;
                case RESTART_INIT_MSG:
                    mImgView.setImageResource(R.mipmap.finger_image);
                    linInitFinger.setVisibility(View.VISIBLE);
                    linInitBottomFinger.setVisibility(View.VISIBLE);
                    linRestartBottomFinger.setVisibility(View.GONE);
                    rlErrorFinger.setVisibility(View.GONE);
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE); // 设置无标题
        setContentView(R.layout.activity_finger_dj);

        initView();
        initFinger();
    }

    public void initFinger() {

        fingerprintService = FingerManage.fingerprintService;
        if (null == fingerprintService) {
            fingerprintService = FingerprintService.getInstance();
            fingerprintService.open();
        }

//        Log.e("test", "打开设备");

        Arrays.fill(bFingerImage, (byte) 0);
        SendMsg(SHOW_IMG_INIT_MSG, "");

        startFingerThread();
    }

    public void startFingerThread() {
        if (m_getImageThread != null) {
//            Log.e("test", "是否有线程删除线程");
            m_getImageThread.interrupt();
            m_getImageThread = null;
        }
//        Log.e("test", "启动线程");
        m_getImageThread = new GetImageThread();
        m_getImageThread.start();

    }

    public void initView() {
        linInitFinger = findViewById(R.id.lin_init_finger);
        linInitBottomFinger = findViewById(R.id.lin_init_bottom_finger);
        linRestartBottomFinger = findViewById(R.id.lin_restart_bottom_finger);
        rlErrorFinger = findViewById(R.id.rl_error_finger);
        mImgView = findViewById(R.id.imageView);


        mContext = this.getApplicationContext();

        model = new SignSubmitModel();

        Intent intent = getIntent();
        handwrittenSignFile = intent.getStringExtra(Constant.HANDWRITTEN_SIGN_FILE);

        //点击dialog外部不消失
        this.setFinishOnTouchOutside(false);
    }

    public void pageClick(View view) {
        int id = view.getId();
        if (id == R.id.image_close) {//关闭页面
            fingerprintResultCode = ConstanceValue.FINGERPRINT_CANCEL_CODE;

            finish();
        } else if (id == R.id.tv_close || id == R.id.tv_ignore) {//跳过
            fingerprintResultCode = ConstanceValue.FINGERPRINT_NO_CODE;

            model.setHandwrittenSignFile(handwrittenSignFile);
            model.setSignFile(handwrittenSignFile);
            finish();
        } else if (id == R.id.tv_restart_finger || id == R.id.tv_restart) {//重试
            SendMsg(RESTART_INIT_MSG, "");
            startFingerThread();
        } else if (id == R.id.tv_submit) {//提交
            fingerprintResultCode = ConstanceValue.FINGERPRINT_SUCCESS_CODE;
            getImageSuccess();
        }
    }

    public void getImageSuccess() {
        //手绘签名bitmap
        Bitmap signBitmap = BitmapFactory.decodeFile(handwrittenSignFile);
        //指纹签名bitmap，把指纹图片指定大小
        Bitmap fingerBitmap = ImageUtils.getNewImage(m_bitmap, 85, 120);

        //拼接指纹和手绘图片
        Bitmap compoundBitmap = ImageUtils.combineImage(signBitmap, fingerBitmap);

        //手绘指纹图片转成file格式
        File signFiles = ImageUtils.compressImage(compoundBitmap, mContext);

        //压缩指纹图片，并转为file格式
        File fingerprintSignFile = ImageUtils.compressImage(fingerBitmap, mContext);

        model.setHandwrittenSignFile(handwrittenSignFile);
        model.setFingerprintSignFile(fingerprintSignFile.getAbsolutePath());
        model.setSignFile(signFiles.getAbsolutePath());

        finish();
    }

    private class GetImageThread extends Thread {
        public void run() {
            try {
                GetImage();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public void GetImage() {
//        Log.e("test", "开始录用指纹");
        Calendar time1 = Calendar.getInstance();
        boolean ret = false;
        Arrays.fill(bFingerImage, (byte) 0);
        ret = fingerprintService.capture(bFingerImage, iTimeout, 0);
        Calendar time2 = Calendar.getInstance();
        long bt_time = time2.getTimeInMillis() - time1.getTimeInMillis();
        if (ret) {
//            Log.e("test", "结束指纹成功" + ret + "______" + bt_time);
            SendMsg(SUCCESS_MSG, "成功");
        } else {
//            Log.e("test", "结束指纹失败" + "______" + bt_time);

            fingerprintService.cancelCapture();
            if (bt_time > 500) {
                SendMsg(FAILED_MSG, "失败");
            } else {
                SendMsg(SHOW_IMG_INIT_MSG, "");
            }
        }
//        Log.e("test", "结束指纹" + ret + "______" + bt_time);
        Log.d(TAG, "GetImage ret = " + ret);
    }

    private void SendMsg(int what, String obj) {
        Message message = new Message();
        message.what = what;
        message.obj = obj;
        message.arg1 = 0;
        LinkDetectedHandler.sendMessage(message);
    }

    private class USBReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case UsbManager.ACTION_USB_DEVICE_ATTACHED: // 插入USB设备
                    boolean ret = fingerprintService.usbCheck();
                    Log.d(TAG, "onReceive: usbCheck =" + ret);
                    Log.e("test", "usb改变");
                    startFingerThread();
                    break;
                default:
                    break;
            }
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver();
    }

    @Override
    public void finish() {
        fingerprintService.cancelCapture();//取消指纹录用

        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constant.SUBMIT_DATA, model);
        intent.putExtras(bundle);

        switch (fingerprintResultCode) {
            case ConstanceValue.FINGERPRINT_SUCCESS_CODE:
                setResult(ConstanceValue.FINGERPRINT_RESULT_CODE, intent);
                //成功
                break;
            case ConstanceValue.FINGERPRINT_CANCEL_CODE:
                //取消指纹采集
                setResult(ConstanceValue.FINGERPRINT_RESULT_CANCEL_CODE, intent);
                break;
            case ConstanceValue.FINGERPRINT_NO_CODE:
                //跳过采集
                setResult(ConstanceValue.FINGERPRINT_RESULT_IGNORE_CODE, intent);
                break;
        }

        super.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (m_getImageThread != null) {
            m_getImageThread.interrupt();
            m_getImageThread = null;
//            Log.e("test", "删除线程");
        }
    }

    @Override
    public void onBackPressed() {
        fingerprintResultCode = ConstanceValue.FINGERPRINT_CANCEL_CODE;

        finish();
    }

    public void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        mUsbReceiver = new USBReceiver();
        registerReceiver(mUsbReceiver, filter);
    }

    public void unregisterReceiver() {
        unregisterReceiver(mUsbReceiver);
    }
}