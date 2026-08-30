package com.example.appsheetvexor;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        EditText etApp = findViewById(R.id.etAppsheet);
        Button btn = findViewById(R.id.btnGuardar);

        SharedPreferences pref = getSharedPreferences("MI_CONFIG", MODE_PRIVATE);
        etApp.setText(pref.getString("appsheet_url",""));

        btn.setOnClickListener(v -> {
            String url = etApp.getText().toString().trim();
            if(url.isEmpty()){
                Toast.makeText(this, "Pega tu link de AppSheet", Toast.LENGTH_SHORT).show();
                return;
            }
            pref.edit().putString("appsheet_url", url).apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}