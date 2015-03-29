package com.mundoglass.worldglass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.rtsp.RtspClient;
import net.majorkernelpanic.streaming.video.VideoQuality;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

/**
 * @author ander.martinez@mundoglass.es based on https://github.com/fyhertz/libstreaming
 * @see www.mundoglass.es
 */
public class CameraActivity extends Activity {

	public final static String TAG = "CameraActivity";
	

	private final static VideoQuality QUALITY_GLASS = new VideoQuality(352, 288, 60, 384000); //wifi
//	private final static VideoQuality QUALITY_GLASS = new VideoQuality(352, 288, 60, 768000); //movil
	String user = "gf";
	String password = "gf";
	String url = "rtsp://192.168.1.241:1935/glassfactory/myStream";

	
	private VideoQuality mQuality = QUALITY_GLASS;			
	private GestureDetector mGestureDetector;
	
	private RelativeLayout mRelativeLayout; 
	protected static SurfaceView mSurfaceView;
	private Session mSession;
	private PowerManager.WakeLock mWakeLock;
	private RtspClient mClient;
	private Boolean recording = false;
	
	private Intent intent;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_camera);
		
		// Getting layout
		mRelativeLayout = (RelativeLayout) findViewById(R.id.camera_activity);
		// Create gesture detector
		mGestureDetector = createGestureDetector(this);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		mSurfaceView = (SurfaceView) findViewById(R.id.surface);		

		// Configures the SessionBuilder
		SessionBuilder sBuilder = SessionBuilder.getInstance()
		.setContext(getApplicationContext())
		.setSurfaceHolder(mSurfaceView.getHolder())
		.setContext(getApplicationContext())
		.setVideoQuality(QUALITY_GLASS)
		.setAudioEncoder(SessionBuilder.AUDIO_AAC)
		.setVideoEncoder(SessionBuilder.VIDEO_H264);	

		// Configures the RTSP client
		mClient = new RtspClient();

		// Creates the Session
		try {
			mSession = sBuilder.build();
			mClient.setSession(mSession);
		} catch (Exception e) {
			logError(e.getMessage());
			e.printStackTrace();
		}

		// Prevents the phone from going to sleep mode
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK,"net.majorkernelpanic.example3.wakelock");
		
		Drawable d = getResources().getDrawable( R.drawable.ginobili );
		mSurfaceView.setBackground(d);
//		new HttpAsyncTask().execute("http://192.168.1.241:8080/useraccount/login/dologin?username=ran&password=ran");
		mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {

			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				if (mSession != null) {
					try {
						if (mSession.getVideoTrack() != null) {
							mSession.getVideoTrack().setVideoQuality(mQuality);
							
							// Start streaming
							new ToggleStreamAsyncTask().execute();
							intent = new Intent(getBaseContext(), BGBroadcastingService.class);
							startService(intent);

						}
					} catch (RuntimeException e) {
						logError(e.getMessage());
						e.printStackTrace();
					}
				}
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format, int width,int height) {
				Log.i(TAG, "surfaceChanged()");
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
				Log.i(TAG, "surfaceDestroyed()");
			}

		});		
		
	}


	@Override
	public void onStart() {
		super.onStart();
		// Lock screen
		mWakeLock.acquire();		
	}
	
	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	@Override
	public void onStop() {
		Log.i(TAG, "onStop()");		
		// Unlock screen
		if (mWakeLock.isHeld()) mWakeLock.release();
			// Setting recording state to disabled
			recording = false;
			mSession.flush();
			
			// Stops the stream and disconnects from the RTSP server
			mClient.stopStream();			
		
			setResult(MainActivity.RESULT_OK);
			super.onStop();
	}	
	
	@Override
	protected void onPause() {

		//Stops the stream and disconnects from the RTSP server
		mClient.stopStream();
		
		// Unlock screen
		if (mWakeLock.isHeld()) mWakeLock.release();
		// Setting recording state to disabled
		recording = false;

		mSession.flush();

		setResult(MainActivity.RESULT_OK);
		super.onPause();
	}


	// Connects/disconnects to the RTSP server and starts/stops the stream
	private class ToggleStreamAsyncTask extends AsyncTask<Void,Void,Integer> {

		private final int START_SUCCEEDED = 0x00;
		private final int START_FAILED = 0x01;
		private final int STOP = 0x02;

		@Override
		protected Integer doInBackground(Void... params) {
			if (!mClient.isStreaming()) {
				String ip,port,path;
				try {
					// We parse the URI written in the Editext
					Pattern uri = Pattern.compile("rtsp://(.+):(\\d+)/(.+)");
					Matcher m = uri.matcher(url); m.find();

					ip = m.group(1);
					port = m.group(2);
					path = m.group(3);
					
					// Connection to the RTSP server
					if (mSession.getVideoTrack() != null) {
						mSession.getVideoTrack().setVideoQuality(mQuality);
					}
					mClient.setCredentials(user, password);
					mClient.setServerAddress(ip, Integer.parseInt(port));
					mClient.setStreamPath("/"+path);
					mClient.startStream(1);
					
					// Init recording flag
					recording = true;
					
					return START_SUCCEEDED;
				} catch (Exception e) {
					Log.e(TAG, "Error starting streaiming.", e);
					Toast.makeText(getApplicationContext(), e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
					return START_FAILED;
				}
			} else {
				// Stops the stream and disconnects from the RTSP server
				mClient.stopStream();				
				// Setting recording state to disabled
				recording = false;
				Log.i(TAG, "*** Recording stopStream()");
				finish();
			}
			return STOP;
		}

	}
	
	// Disconnects from the RTSP server and stops the stream
	private class StopStreamAsyncTask extends AsyncTask<Void,Void,Void> {
		@Override
		protected Void doInBackground(Void... params) {
				mClient.stopStream();
				return null;
		}
	}
	


	private void logError(String msg) {
		final String error = (msg == null) ? "Error unknown" : msg; 
		Log.e(TAG,error);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(CameraActivity.this, error, Toast.LENGTH_SHORT).show();	
			}
		});
	}

	
	private GestureDetector createGestureDetector(Context context) {
	    GestureDetector gestureDetector = new GestureDetector(context);
	        //Create a base listener for generic gestures
	        gestureDetector.setBaseListener( new GestureDetector.BaseListener() {
	            @Override
	            public boolean onGesture(Gesture gesture) {
		                if (gesture == Gesture.TAP) {		                	
		                    Log.i(TAG, "onGesture TAP");
		                    mRelativeLayout.playSoundEffect(SoundEffectConstants.CLICK);		                    
			       			if (recording == false)  {
				    			 new ToggleStreamAsyncTask().execute();
				    			 //Setting recording state to enabled
				    			 recording = true;
				    			 Log.i(TAG, "*** onSingleTapUp onClick .start");
				    		} else {
				    			 new ToggleStreamAsyncTask().execute();
				    			 //Setting recording state to disable
				    			 recording = false;
				    			 Log.i(TAG, "*** onSingleTapUp onClick .stop");
				    			 
				    			 finish();
				    		}  			        			 
			       			return true;
	                } else if (gesture == Gesture.TWO_TAP) {
	                    Log.i(TAG, "onGesture TWO TAP");
	                	return true;
	                } else if (gesture == Gesture.SWIPE_RIGHT) {
	                	Log.i(TAG, "onGesture SWIPE RIGHT");
	                    return true;
	                } else if (gesture == Gesture.SWIPE_LEFT) {
	                	Log.i(TAG, "onGesture SWIPE LEFT");
	                	return true;
	                } else if (gesture == Gesture.SWIPE_DOWN) {
	                	Log.i(TAG, "onGesture SWIPE DOWN ");
	                	return true;	
	                }
	                
	                return false;
	            }
	        });
	        gestureDetector.setFingerListener(new GestureDetector.FingerListener() {
	            @Override
	            public void onFingerCountChanged(int previousCount, int currentCount) {
	              // do something on finger count changes
	            }
	        });
	        gestureDetector.setScrollListener(new GestureDetector.ScrollListener() {
	            @Override
	            public boolean onScroll(float displacement, float delta, float velocity) {
	            	Log.i(TAG, "onScroll");
	            	return true;
	            }
	        });
	        return gestureDetector;
	    }

	    /*
	     * Send generic motion events to the gesture detector
	     */
	    @Override
	    public boolean onGenericMotionEvent(MotionEvent event) {
	        if (mGestureDetector != null) {
	            return mGestureDetector.onMotionEvent(event);
	        }
	        return false;
	    }
	    
	    private class HttpAsyncTask extends AsyncTask<String, Void, String> {

			@Override
			protected String doInBackground(String... urls) {
				return GET(urls[0]);
			}
	    	
			@Override
			protected void onPostExecute(String result) {
				Canvas canvas = null;
				Toast.makeText(getBaseContext(), "Received!", Toast.LENGTH_LONG).show();
				try{
					canvas = mSurfaceView.getHolder().lockCanvas();
					synchronized (mSurfaceView.getHolder()){
						canvas.drawColor(Color.BLACK);
						Paint paint = new Paint();
				        paint.setColor(Color.WHITE);
				        paint.setTextSize(25);
						canvas.drawText(result, 10, 10, paint);
					}
				}catch (Exception e){
					e.printStackTrace();
				}finally {
					if (canvas != null){
						mSurfaceView.getHolder().unlockCanvasAndPost(canvas);
					}
				}
			}
	    }
	    
	    public static String GET(String url){
			InputStream inputStream = null;
			String result = "";
			try {
				
				// create HttpClient
				HttpClient httpclient = new DefaultHttpClient();
				
				// make GET request to the given URL
				HttpResponse httpResponse = httpclient.execute(new HttpGet(url));
				
				// receive response as inputStream
				inputStream = httpResponse.getEntity().getContent();
				
				// convert inputstream to string
				if(inputStream != null)
					result = convertInputStreamToString(inputStream);
				else
					result = "Did not work!";
			
			} catch (Exception e) {
				Log.d("InputStream", e.getLocalizedMessage());
			}
			
			return result;
		}
	    
	    private static String convertInputStreamToString(InputStream inputStream) throws IOException{
	        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
	        String line = "";
	        String result = "";
	        while((line = bufferedReader.readLine()) != null)
	            result += line;
	        
	        inputStream.close();
	        return result;
	        
	    }
	
}
