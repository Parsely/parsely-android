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
        ParselyTracker.init("example.com", 30, this, true);

        final TextView intervalView = (TextView) findViewById(R.id.interval);

        updateEngagementStrings();

        final TextView views[] = new TextView[1];
        views[0] = intervalView;

        final Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                TextView[] v = (TextView[]) msg.obj;
                TextView iView = v[0];
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
