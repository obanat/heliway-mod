package com.obana.heleway;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Date;

import com.fh.lib.PlayInfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;


public class CommUtil {
	
    public static final int TCP_CONNECT_SUCCEED = 2701;
    public static final int TCP_ERROR = 2703;
    public static final int TCP_RECEIVE_DATA = 2702;
    public static final int UDP_RECEIVE_DATA = 2704;

    public static final String HELIWAY_LOCAL_TCP_ADDR = "172.16.10.1";
    public static final int HELIWAY_LOCAL_TCP_PORT = 8888;


    public static final int HELIWAY_REMOTE_TCP_PORT = 28001;
    public static final int HELIWAY_REMOTE_UDP_PORT = 28002;
    
    public static final int TCP_CHECK_PORT = 80;//this port is use for check connectivity
    
  
    
    private static final String HELIWAY_FWD_ADDR= "127.0.0.1";
    private static final int HELIWAY_FWD_PORT= 8900;
    
    
    
	private static CommUtil instance;
    private UDPServerTemp mUdpSer = new UDPServerTemp();
    private InetAddress mDevAddr;
    private int sendCheckValidateCount = 0;
    SocketClient socketClient = null;
    private Context context;
    CbTcpConnected callBack;
    
    public static CommUtil getInstance() {
    	if (instance == null ) instance = new CommUtil();
        return instance;
    }

    public CommUtil() {
    
    }
    
    public void init (Context ctx, CbTcpConnected cb) {
    	//just do nothing
    	LogUtils.i("CommUtil init");
    	callBack = cb;
    	context = ctx;
    	this.mHandler.post(this.checkAP);
    }
    
    public void requestIFrame() {
        this.mHandler.post(this.sendRequestCmd);
    }

    private Runnable sendRequestCmd = new Runnable() { // from class: com.vison.baselibrary.base.CommUtil.8
        @Override // java.lang.Runnable
        public void run() {
        	LogUtils.i("CommUitl, requestIFrame");
        	CommUtil.this.writeUDPCmd(new byte[]{39});
        }
    };

    public void writeUDPCmd(byte[] bArr) {
        this.mUdpSer.addCmdP(bArr, this.mDevAddr, HELIWAY_REMOTE_UDP_PORT);
    }

    private Runnable sendCheckWorkCmd = new Runnable() { // from class: com.vison.baselibrary.base.CommUtil.7
        @Override // java.lang.Runnable
        public void run() {
            try {
                //InetAddress byAddress = InetAddress.getByAddress(socketClient.getHost().getBytes());
                if (-1 == PlayInfo.transMode) {
                    if (PlayInfo.udpDevType == 4) {
                        CommUtil.this.writeTCPCmd("remote\r\n");
                    } else {
                        CommUtil.this.writeTCPCmd(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 37, 37});
                    }
                } else {
                    if (-1 != PlayInfo.transMode && PlayInfo.transMode != 0) {
                        if (1 == PlayInfo.transMode) {
                            if (PlayInfo.udpDevType == 4) {
                                CommUtil.this.writeTCPCmd("remote\r\n");
                            } else {
                                //CommUtil.this.writeTCPCmd(new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 37, 37});
                            }
                        }
                    }
                    CommUtil.this.mUdpSer.addCmdP(new byte[]{37}, CommUtil.this.mDevAddr, HELIWAY_REMOTE_UDP_PORT);
                }
            } catch (Exception e) {
            	LogUtils.e("sendCheckWorkCmd exception:" + e.getMessage());
            }
            CommUtil.access$110(CommUtil.this);
            if (CommUtil.this.sendCheckValidateCount > 0) {
                CommUtil.this.mHandler.postDelayed(CommUtil.this.sendCheckWorkCmd, 100L);
            } else {
                CommUtil.this.mHandler.postDelayed(CommUtil.this.sendCheckWorkCmd, 1500L);
            }
        }
    };

    Runnable sendDevTime = new Runnable() { // from class: com.vison.baselibrary.base.BaseApplication.5
        @Override // java.lang.Runnable
        public void run() {
            if (PlayInfo.udpDevType == 2) {
            	CommUtil.this.udpSendTime();
            }
            CommUtil.this.mHandler.postDelayed(CommUtil.this.sendDevTime, 1000L);
        }
    };

    TcpForwarder fr = null;
    Runnable checkAP = new Runnable() { // from class: com.vison.baselibrary.base.BaseApplication.3
        @Override // java.lang.Runnable
        public void run() {
        	
        	 WifiManager wifiManager = (WifiManager) context.getSystemService("wifi");
        	 DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();

        	 int intAddr = dhcpInfo.gateway;
        	 String wifiDhcpAddr = long2ip(intAddr);
        	 int res = 0;
        	 if (intAddr != 0) {
        		 LogUtils.i("checkAP run in local mode, addr:" + wifiDhcpAddr);

        		 res = restartTCP(wifiDhcpAddr);//wifi local mode
        		 
        		 if(MainActivity.mCameraMode == MainActivity.CAMERA_MODE_HELIWAY){
	        		 PlayInfo.targetIpAddr = CommUtil.this.socketClient.getHost();
	                 //PlayInfo.RTSPUrl = CommUtil.this.socketClient.getHost();
	                 PlayInfo.udpPort = CommUtil.this.socketClient.getPort();;
	                 PlayInfo.playType = 1;
        		 }
        	 } else {
        		 res = restartTCP(null);
        		 
        		 if(MainActivity.mCameraMode == MainActivity.CAMERA_MODE_HELIWAY){
	        		 InetSocketAddress from = new InetSocketAddress(HELIWAY_FWD_ADDR, HELIWAY_FWD_PORT);
	        		 InetSocketAddress to = new InetSocketAddress(CommUtil.this.socketClient.getHost(),CommUtil.this.socketClient.getPort());
	        		 fr = new TcpForwarder(from ,to ,"");
	            	 try {
	    				fr.call();
	    			} catch (IOException e) {
	    				LogUtils.e("TcpForwarder error!" + e.getMessage());
	    			}
	        		 
	        		try {
						Thread.sleep(200L);
					} catch (InterruptedException e) {
	
					}
					PlayInfo.targetIpAddr = HELIWAY_FWD_ADDR;//CommUtil.this.socketClient.getHost();
					//PlayInfo.RTSPUrl = CommUtil.this.socketClient.getHost();//LO_ADDR;
					PlayInfo.udpPort = HELIWAY_FWD_PORT;//CommUtil.this.socketClient.getPort();//
					PlayInfo.playType = 1;
        		 }
        	 } 

        	 if(MainActivity.mCameraMode == MainActivity.CAMERA_MODE_HELIWAY){
	             CommUtil.this.mUdpSer.Stop();
				 if (res > 0) {
				    CommUtil.this.mUdpSer.setAccesser(CommUtil.this.mHandler);
				    CommUtil.this.mUdpSer.Start();
				 }
	                
				if (CommUtil.this.callBack != null) {
					CommUtil.this.callBack.cb_connected();
				}
        	 }
        }
    };
   
    
    public interface CbTcpConnected {
        void cb_connected();
    }
    
    
    private String long2ip(int i) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append(String.valueOf(i & 255));
        stringBuffer.append('.');
        stringBuffer.append(String.valueOf((i >> 8) & 255));
        stringBuffer.append('.');
        stringBuffer.append(String.valueOf((i >> 16) & 255));
        stringBuffer.append('.');
        stringBuffer.append(String.valueOf((i >> 24) & 255));
        return stringBuffer.toString();
    }


    public void udpSendTime() {
        Date date = new Date();
        int year = date.getYear() + 1900;
        int month = date.getMonth() + 1;
        int date2 = date.getDate();
        int day = date.getDay();
        int hours = date.getHours();
        int minutes = date.getMinutes();
        int seconds = date.getSeconds();
        writeUDPCmd(new byte[]{38, (byte) (year & 255), (byte) ((year & 0xff00) >> 8), (byte) ((year & 16711680) >> 16), (byte) ((year & 0xff000000) >> 24), (byte) (month & 255), (byte) ((month & 0xff00) >> 8), (byte) ((month & 16711680) >> 16), (byte) ((month & 0xff000000) >> 24), (byte) (date2 & 255), (byte) ((date2 & 0xff00) >> 8), (byte) ((date2 & 16711680) >> 16), (byte) ((date2 & 0xff000000) >> 24), (byte) (day & 255), (byte) ((day & 0xff00) >> 8), (byte) ((day & 16711680) >> 16), (byte) ((day & 0xff000000) >> 24), (byte) (hours & 255), (byte) ((hours & 0xff00) >> 8), (byte) ((hours & 16711680) >> 16), (byte) ((hours & 0xff000000) >> 24), (byte) (minutes & 255), (byte) ((minutes & 0xff00) >> 8), (byte) ((minutes & 16711680) >> 16), (byte) ((minutes & 0xff000000) >> 24), (byte) (seconds & 255), (byte) ((seconds & 0xff00) >> 8), (byte) ((seconds & 16711680) >> 16), (byte) ((seconds & 0xff000000) >> 24)});
    }

    
    public void requestVideoData(boolean z) {
        if (z) {
            this.mHandler.removeCallbacks(this.sendRequestVideo);
            this.mHandler.postDelayed(this.sendRequestVideo, 10L);
            return;
        }
        this.mHandler.removeCallbacks(this.sendRequestVideo);
    }
    private Runnable sendRequestVideo = new Runnable() { // from class: com.vison.baselibrary.base.BaseApplication.6
        @Override // java.lang.Runnable
        public void run() {
            if (PlayInfo.transMode == 0) {
                try {
                    ByteBuffer allocate = ByteBuffer.allocate(5);
                    allocate.put((byte) 8);
                    //allocate.put(CommUtil.long2byte(CommUtil.this.mIPinfo[0]));TODO
                    CommUtil.this.writeUDPCmd(allocate.array());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                CommUtil.this.mHandler.postDelayed(CommUtil.this.sendRequestVideo, 1000L);
            }
        }
    };

    
    public void writeTCPCmd(byte[] bArr) {
    	LogUtils.i("heliway tcp send data len:" + bArr.length);
        if (this.socketClient != null) {
            this.socketClient.sendData(bArr);
        }
    }

    public void writeTCPCmd(String str) {
        writeTCPCmd(str.getBytes());
    }

    static /* synthetic */ int access$110(CommUtil baseApplication) {
        int i = baseApplication.sendCheckValidateCount;
        baseApplication.sendCheckValidateCount = i - 1;
        return i;
    }

    public void onUdpReceiveData(byte[] bArr) {
        String bytesToHexString = ObjectUtils.bytesToHexString(bArr);
       if (bytesToHexString.startsWith("55445037323050") || bytesToHexString.startsWith("3130383050")) {
            if (bytesToHexString.startsWith("55445037323050")) {
                //PlayInfo.deviceId = "720p";
            } else {
                //PlayInfo.deviceId = "1080P";
            }
            PlayInfo.transMode = 1;
            LogUtils.i("onUdpReceiveData, devId:" + PlayInfo.deviceId);
            PlayInfo.udpDevType = 2;
            this.mHandler.post(this.sendDevTime);
            //restartTCP(true);
            //getVersion();
            
        }
    }
 
    public void onTcpReceiveData(byte[] bArr) {
       
    }

    public int restartTCP(String localAddr) {
    	
    	if (this.socketClient == null) {
            this.socketClient = new SocketClient();
            this.socketClient.setCallBackHandler(this.mHandler);
        }
    	
        if (this.socketClient.isConnected()) {
        	LogUtils.i("restartTCP, socket already connected, just return!");
        	return 0;
        }
        
        if (MainActivity.mCameraMode == MainActivity.CAMERA_MODE_HELIWAY) {
	        PlayInfo.udpDevType = 2;//for heliway wifi camera
	        PlayInfo.transMode = 1;
	        PlayInfo.deviceId = PlayInfo.DeviceId._872_720.getId();// _8626_5G_8801_8603_1080P.get"720p";
	        PlayInfo.deviceType = PlayInfo.DeviceType._720P;
        }
        
        
    	if (localAddr != null && localAddr.length() > 8) {
    		LogUtils.i("restartTCP, run in local mode, local addr:" + localAddr);
    		
    		this.socketClient.setHost(localAddr);
        	this.socketClient.setPort(HELIWAY_LOCAL_TCP_PORT);

	        try {
				this.mDevAddr = InetAddress.getByName(localAddr);
			} catch (IOException e) {
				LogUtils.e("failed to set mDevAddr");
			}
    	} else {
    	
    		//ipv6 rc mode
	        String ip = getIpv6HostName();
	        if (ip != null && ip.length() > 4) {
	        	this.socketClient.setHost(ip);  	
	        	this.socketClient.setPort(HELIWAY_REMOTE_TCP_PORT);

	        	LogUtils.i("restartTCP, run in ipv6 mode, ip:" + ip);
	        } else {
	        	this.socketClient.setHost("192.168.10.1");
	        	this.socketClient.setPort(HELIWAY_LOCAL_TCP_PORT);
	        }
	        
	        try {
				this.mDevAddr = InetAddress.getByName(ip);
			} catch (IOException e) {
				LogUtils.e("failed to set mDevAddr");
			}
    	}
    	
        /*LogUtils.i("restartTCP,socket connecting...host:" + socketClient.getHost() + " port:" + socketClient.getPort());
        this.socketClient.connect();
        LogUtils.i("socket connectted!");
        return 1;*/
    	return 0;
    }
    
    public String getIpv6HostName() {
    	String targetHost = null;
    	Socket socket = null;
    	
    	String mac = getSharedPreference("mac");
    	String getUrl = String.format("http://obana.f3322.org:38086/wificar/getClientIp?mac=%s", mac);

    	LogUtils.i("getURLContent: url:" + getUrl);
    	
        String ipaddr = getURLContent(getUrl);
        
        if (ipaddr != null && ipaddr.length() > 8){
        	socket = tryCreateSocket(ipaddr, TCP_CHECK_PORT);

        	if (socket != null && socket.isConnected()){
        		targetHost = ipaddr;

        		LogUtils.i("tryCreateSocket: success! use remote ipv6 control");
        	}
        	try {
        		if (socket != null) socket.close();
        	} catch (Exception e) {
        		LogUtils.i("failed to getURLContent: error:" + e.getMessage());
        	}
        }
        return targetHost;
    }
    
    private static String getURLContent(String url) {
        StringBuffer sb = new StringBuffer();
        LogUtils.i("getURLContent:" +url);
        try {
            URL updateURL = new URL(url);
            URLConnection conn = updateURL.openConnection();
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF8"));
            while (true) {
                String s = rd.readLine();
                if (s == null) {
                    break;
                }
                sb.append(s);
            }
        } catch (Exception e){

        }
        return sb.toString();
    }
    

    
    private static Socket tryCreateSocket(String targetHost, int targetPort)  {
    	Socket socket = null;
    	try {
    		LogUtils.i("tryCreateSocket ------->:" + targetHost + ":" + targetPort);
    		//socket = new Socket(targetHost, targetPort);
	        socket = new Socket();
	        SocketAddress remoteAddr = new InetSocketAddress(targetHost, targetPort);
	        socket.connect(remoteAddr, 3000);

    	} catch (IOException e) {
    		LogUtils.e("socket error:" + e.getMessage());
    		return null;
    	} finally {
    		
    	}
    	LogUtils.i("tryCreateSocket success!------->:" + targetHost);
        return socket;
    }
    
    private Handler mHandler = new Handler() { // from class: com.vison.baselibrary.base.CommUtil.1
        @Override // android.os.Handler
        public void handleMessage(Message message) {
            switch (message.what) {
                case TCP_CONNECT_SUCCEED /* 2701 */:
                	CommUtil.this.sendCheckValidateCount = 10;
                	CommUtil.this.mHandler.removeCallbacks(CommUtil.this.sendCheckWorkCmd);
                	CommUtil.this.mHandler.postDelayed(CommUtil.this.sendCheckWorkCmd, 10L);
                    return;
                case TCP_RECEIVE_DATA /* 2702 */:
                	CommUtil.this.onTcpReceiveData((byte[]) message.obj);
                    return;
                case TCP_ERROR /* 2703 */:
                    //CommUtil.this.socketClient.connect();
                    return;
                case UDP_RECEIVE_DATA:
                	CommUtil.this.onUdpReceiveData((byte[]) message.obj);
                    return;
                default:
                    return;
            }
        }
    };
    
	private String getSharedPreference(String key) {
		//return AndRovio.getSharedPreference(key);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		return sp.getString(key, "");
	}

}
