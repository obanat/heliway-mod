package com.obana.heleway;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.fh.lib.Define;
import com.fh.lib.FHSDK;
import com.fh.lib.PlayInfo;
import com.obana.heliway.R;


/* loaded from: classes.dex */
public class MainActivity extends Activity implements View.OnTouchListener, Define.CbDataInterface,CommUtil.CbTcpConnected {

    public static final int GET_STATUS_DELAY_MS = 5000;
    public static final int MOVE_DELAY_MS = 300;
 
    
    private WifiCarController controller = null;
    
    //heliway
    private GLFrameSurface glFrameSurface;
    public GLFrameRenderer mFrameRender;

    
    public static final int CAMERA_MODE_DEFAULT = 0x01;
    public static final int CAMERA_MODE_HELIWAY = 0x02;

    public static int mCameraMode = CAMERA_MODE_HELIWAY;

    ImageView settingsView;

    @Override // android.app.Activity
    public void onCreate(Bundle bundle) {
        LogUtils.i("onCreate");
        super.onCreate(bundle);
        
        FHSDK.handle = FHSDK.createPlayHandle();
        FHSDK.registerNotifyCallBack(FHSDK.handle, this);

        //setContentView(R.layout.main);
        getWindow().addFlags(4096);
        
        this.controller = new WifiCarController(this);


        DisplayMetrics displayMetrics = getApplicationContext().getResources().getDisplayMetrics();
        this.glFrameSurface = new GLFrameSurface(this);
        this.glFrameSurface.setEGLContextClientVersion(2);
        this.mFrameRender = new GLFrameRenderer(this, this.glFrameSurface, displayMetrics);
        this.glFrameSurface.setRenderer(this.mFrameRender);
        addContentView(this.glFrameSurface,new ViewGroup.LayoutParams(-1, -1));
      
        LayoutInflater mInflater = LayoutInflater.from(this);

        View contrilView = mInflater.inflate(R.layout.main, (ViewGroup) null);
        addContentView(contrilView, new ViewGroup.LayoutParams(-1, -1));
        
        View overView = mInflater.inflate(R.layout.controller, (ViewGroup) null);
        addContentView(overView, new ViewGroup.LayoutParams(-1, -1));   
        controller.init(findViewById(R.id.joystickView));
        
        settingsView = (ImageView) findViewById(R.id.setting_button);
        settingsView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                //start settings
                startActivity(new Intent(MainActivity.this, Settings.class));
            }
            
        });
        
        initMediaCodec();
        CommUtil.getInstance().init(this, this);
    }

    @Override // android.app.Activity
    public void onDestroy() {
        super.onDestroy();
    }

    

    @Override // android.app.Activity
    public void onPause() {
        LogUtils.i("onPause");
        super.onPause();
    }

    @Override // android.app.Activity
    public void onResume() {
        super.onResume();
        LogUtils.i("onResume");
    }


    @Override // android.view.View.OnTouchListener
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }
    
    private void initMediaCodec() {
        if (PlayInfo.decodeType == 4) {
            MyMediaCodec.getInstance().init(null);
        } else {
            MyMediaCodec.getInstance().init(this.mFrameRender);
        }
    }


    
    @Override // this means socket connectted
    public void cb_connected() {
        LogUtils.i("cb_connected======> start playinfo:" + PlayInfo.targetIpAddr);
          FHSDK.registerNotifyCallBack(FHSDK.handle,this);
       
        FHSDK.setPlayInfo(FHSDK.handle,new PlayInfo());
    } 
    
    @Override // com.p005fh.lib.Define.CbDataInterface
    public void cb_data(int i, byte[] bArr, int i2) {
        //LogUtils.i("cb_data======> start i1:" + i + " i2:" + i2);
        if (i != 0) {
            if (i != 4) {
                return;
            }
            CommUtil.getInstance().requestIFrame();
            return;
        }
    }
    
    public void onUpdateFrame(byte[] bArr, int i, int i2) {
    }

    public void setOutputVideo(boolean z) {
        if (this.mFrameRender != null) {
            if (z) {
                this.mFrameRender.setOnYuvUpdateListener(new GLFrameRenderer.OnYuvUpdateListener() { // from class: com.vison.baselibrary.base.BaseOpenGlActivity.8
                    @Override // com.vison.baselibrary.opengles.GLFrameRenderer.OnYuvUpdateListener
                    public void onUpdate(byte[] bArr, int i, int i2) {
                        onUpdateFrame(bArr, i, i2);
                    }
                });
            } else {
                this.mFrameRender.setOnYuvUpdateListener(null);
            }
        }
    }
    
    long lastPressTime = 0;
    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        LogUtils.i("onKeyDown keycode=" + keyCode);
        if (keyCode == 4) {//back key
            long pressTime = System.currentTimeMillis();
            if (pressTime - this.lastPressTime <= 1200) {
                this.finish();
                System.gc();
                System.exit(0);
            } else {
                Toast.makeText(this, R.string.click_again_to_exit_the_program, Toast.LENGTH_LONG).show();
            }
            this.lastPressTime = pressTime;
            return true;
        }
        
        return super.onKeyDown(keyCode, event);
    }

}
