package com.mundoglass.worldglass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.AsyncTask;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;

public class BGBroadcastingService extends IntentService {
	
	private SurfaceView mSurfaceView = CameraActivity.mSurfaceView;

	public BGBroadcastingService() {
		super("BGBroadcastingService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		new HttpAsyncTask().execute("http://192.168.1.241:8080/useraccount/login/dologin?username=ran&password=ran");
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
