package com.example;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
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

        // initialize the Parsely tracker with your site id and the current Context
        ParselyTracker.sharedInstance("example.com", 30, this);

        // Set debugging to true so we don't actually send things to Parse.ly
        ParselyTracker.sharedInstance().setDebug(true);

        final TextView queueView = (TextView) findViewById(R.id.queue_size);
        queueView.setText(String.format("Queued events: %d", ParselyTracker.sharedInstance().queueSize()));

        final TextView storedView = (TextView) findViewById(R.id.stored_size);
        storedView.setText(String.format("Stored events: %d", ParselyTracker.sharedInstance().storedEventsCount()));

        final TextView intervalView = (TextView) findViewById(R.id.interval);
        storedView.setText(String.format("Flush interval: %d", ParselyTracker.sharedInstance().getFlushInterval()));

        updateEngagementStrings();

        final TextView views[] = new TextView[3];
        views[0] = queueView;
        views[1] = storedView;
        views[2] = intervalView;

        final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                TextView[] v = (TextView[]) msg.obj;
                TextView qView = v[0];
                qView.setText(String.format("Queued events: %d", ParselyTracker.sharedInstance().queueSize()));

                TextView sView = v[1];
                sView.setText(String.format("Stored events: %d", ParselyTracker.sharedInstance().storedEventsCount()));

                TextView iView = v[2];
                if (ParselyTracker.sharedInstance().flushTimerIsActive()) {
                    iView.setText(String.format("Flush Interval: %d", ParselyTracker.sharedInstance().getFlushInterval()));
                } else {
                    iView.setText("Flush timer inactive");
                }

                updateEngagementStrings();
            }
        };

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                Message msg = new Message();
                msg.obj = views;
                mHandler.sendMessage(msg);
            }
        }, 500, 500);

    }

    private void updateEngagementStrings() {
        StringBuilder eMsg = new StringBuilder("Engagement is ");
        if (ParselyTracker.sharedInstance().engagementIsActive() == true) {
            eMsg.append("active.");
        } else {
            eMsg.append("inactive.");
        }
        eMsg.append(String.format(" (interval: %.01fms)", ParselyTracker.sharedInstance().getEngagementInterval()));

        TextView eView = findViewById(R.id.et_interval);
        eView.setText(eMsg.toString());

        StringBuilder vMsg = new StringBuilder("Video is ");
        if (ParselyTracker.sharedInstance().videoIsActive() == true) {
            vMsg.append("active.");
        } else {
            vMsg.append("inactive.");
        }
        vMsg.append(String.format(" (interval: %.01fms)", ParselyTracker.sharedInstance().getVideoEngagementInterval()));

        TextView vView = findViewById(R.id.video_interval);
        vView.setText(vMsg.toString());
    }

    public void trackPageview(View view) {
        // NOTE: urlMetadata is only used when "url" has no version accessible outside the app. If
        //       the post has an internet-accessible URL, we will crawl it. urlMetadata is only used
        //       in the case of app-only content that we can't crawl.
        ParselyTracker.sharedInstance().trackPageview(
                "http://example.com/article1.html", "http://example.com/", null, null
        );
    }

    public void startEngagement(View view) {
        final Map<String, Object> extraData = new HashMap<>();
        extraData.put("product-id", "12345");
        ParselyTracker.sharedInstance().startEngagement("http://example.com/article1.html", "http://example.com/", extraData);
        updateEngagementStrings();
    }

    public void stopEngagement(View view) {
        ParselyTracker.sharedInstance().stopEngagement();
        updateEngagementStrings();
    }

    public void trackPlay(View view) {
        ParselyVideoMetadata metadata = new ParselyVideoMetadata(
                new ArrayList<String>(),
                "video-1234",
                "videos",
                new ArrayList<String>(),
                "http://example.com/thumbs/video-1234",
                "Awesome Video #1234",
                Calendar.getInstance(),
                "post",
                90
        );
        // NOTE: For videos embedded in an article, "url" should be the URL for that article.
        ParselyTracker.sharedInstance().trackPlay("http://example.com/app-videos", null, metadata, null);

    }

    public void trackPause(View view) {
        ParselyTracker.sharedInstance().trackPause();
    }

    public void trackReset(View view) {ParselyTracker.sharedInstance().resetVideo(); }
}
