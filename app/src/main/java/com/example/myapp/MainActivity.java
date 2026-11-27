package com.example.myapp;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView placeholder = new TextView(this);
        placeholder.setText("新应用将在这里开始");
        placeholder.setTextSize(20);
        placeholder.setGravity(Gravity.CENTER);
        setContentView(placeholder);
    }
}
