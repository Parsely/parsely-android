package com.example.hiparsely;

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
        ParselyTracker.sharedInstance("examplesite.com", this);
        
        final TextView queueView = (TextView)findViewById(R.id.queue_size);
        queueView.setText(String.format("Queued events: %d", ParselyTracker.sharedInstance().queueSize()));
        
        final TextView storedView = (TextView)findViewById(R.id.stored_size);
        storedView.setText(String.format("Stored events: %d", ParselyTracker.sharedInstance().storedEventsCount()));
        
        final TextView views[] = new TextView[2];
        views[0] = queueView;
        views[1] = storedView;
        
        final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                TextView[] v = (TextView[])msg.obj;
                TextView qView = v[0];
                qView.setText(String.format("Queued events: %d", ParselyTracker.sharedInstance().queueSize()));
                
                TextView sView = v[1];
                sView.setText(String.format("Stored events: %d", ParselyTracker.sharedInstance().storedEventsCount()));
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

    public void trackURL(View view) {
        ParselyTracker.sharedInstance().trackURL("http://examplesite.com/something-whatever.html");
    }

    public void trackPID(View view) {
        ParselyTracker.sharedInstance().trackPostId("1987263412-341872361023-12783461234");
    }
}
