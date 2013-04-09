package com.example.hiparsely;

import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import com.example.parselyandroid.*;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		ParselyTracker.sharedInstance("arstechnica.com", 2);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	public void trackURL(View view) {
		ParselyTracker.sharedInstance().track("http://arstechnica.com/something-whatever.html", ParselyTracker.kIdType.kUrl);
	}
	
	public void trackPID(View view) {
		ParselyTracker.sharedInstance().track("1987263412-341872361023-12783461234", ParselyTracker.kIdType.kPostId);
	}
	
	public void toggleConnection(View view) {
		Log.i("MainActivity", "toggle connection called");
	}
}
