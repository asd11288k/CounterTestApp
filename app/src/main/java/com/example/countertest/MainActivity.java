package com.example.countertest;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private int count = 0;
    private TextView counterText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 32, 32, 32);

        TextView title = new TextView(this);
        title.setText("极简计数器");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        counterText = new TextView(this);
        counterText.setTextSize(64);
        counterText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        counterText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        root.addView(counterText, numberParams);

        Button addButton = new Button(this);
        addButton.setText("+1");
        addButton.setTextSize(20);
        addButton.setOnClickListener(v -> {
            count++;
            updateCounter();
        });
        root.addView(addButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button resetButton = new Button(this);
        resetButton.setText("重置");
        resetButton.setTextSize(18);
        resetButton.setOnClickListener(v -> {
            count = 0;
            updateCounter();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        resetParams.topMargin = 16;
        root.addView(resetButton, resetParams);

        setContentView(root);
        updateCounter();
    }

    private void updateCounter() {
        counterText.setText(String.valueOf(count));
    }
}
