package com.example;

import java.util.Timer;
import java.util.TimerTask;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import com.parsely.parselyandroid.*;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // initialize the Parsely tracker with your API key and the current Context
        ParselyTracker.sharedInstance("examplesite.com", 15, this);

        // Set debugging to true so we don't actually send things to Parse.ly
        ParselyTracker.sharedInstance().setDebug(true);
        
        final TextView queueView = (TextView)findViewById(R.id.queue_size);
        queueView.setText(String.format("Queued events: %d", ParselyTracker.sharedInstance().queueSize()));
        
        final TextView storedView = (TextView)findViewById(R.id.stored_size);
        storedView.setText(String.format("Stored events: %d", ParselyTracker.sharedInstance().storedEventsCount()));
        
        final TextView intervalView = (TextView)findViewById(R.id.interval);
        storedView.setText(String.format("Flush interval: %d", ParselyTracker.sharedInstance().flushInterval));
        
        final TextView views[] = new TextView[3];
        views[0] = queueView;
        views[1] = storedView;
        views[2] = intervalView;
        
        final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                TextView[] v = (TextView[])msg.obj;
                TextView qView = v[0];
                qView.setText(String.format("Queued events: %d", ParselyTracker.sharedInstance().queueSize()));
                
                TextView sView = v[1];
                sView.setText(String.format("Stored events: %d", ParselyTracker.sharedInstance().storedEventsCount()));
                
                TextView iView = v[2];
                if(ParselyTracker.sharedInstance().flushTimerIsActive()){
                    iView.setText(String.format("Flush Interval: %d", ParselyTracker.sharedInstance().flushInterval));
                } else {
                    iView.setText("Flush timer inactive");
                }
            }
        };
        
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask(){
            public void run(){
                Message msg = new Message();
                msg.obj = views;
                mHandler.sendMessage(msg);
            }
        }, 500, 500);
        
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    @Override
    protected void onDestroy() {
        ParselyTracker.sharedInstance().flush();
        super.onDestroy();
    }

    public void trackURL(View view) {
        ParselyTracker.sharedInstance().trackURL("http://example.com/article1.html");
    }

    public void trackPID(View view) {
        ParselyTracker.sharedInstance().trackPostId("1987263412-341872361023-12783461234");
    }

    public void startEngagement(View view) {
        ParselyTracker.sharedInstance().startEngagement("http://example.com/article1.html");
    }

    public void stopEngagement(View view) {
        ParselyTracker.sharedInstance().stopEngagement();
    }

    public void trackPlay(View view) {
        ParselyTracker.sharedInstance().trackPlay("http://example.com/article1", "video1");
    }

    public void trackPause(View view) {
        ParselyTracker.sharedInstance().trackPause();
    }
}
