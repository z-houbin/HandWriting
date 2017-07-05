package com.example.handwriting;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.example.handwriting.view.HandwritingView;

public class MainActivity extends AppCompatActivity {

    private EditText input;
    private HandwritingView handwriting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.input = (EditText) findViewById(R.id.input);
        this.handwriting = (HandwritingView) findViewById(R.id.handwrite);
    }

    public void setText(View view) {
        String str = input.getText().toString().trim();
        try {
            handwriting.setText(str);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
