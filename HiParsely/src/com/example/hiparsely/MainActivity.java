package com.example.hiparsely;

import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import com.parsely.parselyandroid.*;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ParselyTracker.sharedInstance("arstechnica.com", 3, this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void trackURL(View view) {
        ParselyTracker.sharedInstance().trackURL("http://arstechnica.com/something-whatever.html");
    }

    public void trackPID(View view) {
        ParselyTracker.sharedInstance().trackPostId("1987263412-341872361023-12783461234");
    }
}
