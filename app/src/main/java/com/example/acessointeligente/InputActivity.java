package com.example.acessointeligente;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class InputActivity extends AppCompatActivity {

    private EditText nameEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Verifica se o nome já foi salvo nas SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String userName = sharedPreferences.getString("USER_NAME", null);

        // Se o nome já está salvo, redireciona para a MainActivity e fecha a InputActivity
        if (userName != null) {
            Intent intent = new Intent(InputActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return; // Evita continuar a execução da InputActivity
        }

        setContentView(R.layout.activity_input); // Layout da InputActivity

        nameEditText = findViewById(R.id.nameEditText);
        Button startMonitoringButton = findViewById(R.id.startMonitoringButton);

        startMonitoringButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userName = nameEditText.getText().toString().trim();

                // Verifica se o nome não está vazio ou nulo
                if (userName.isEmpty()) {
                    // Exibe um alerta informando que o nome é necessário
                    showAlert("Nome inválido", "Por favor, insira um nome válido.");
                } else {
                    // Salva o nome nas SharedPreferences
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("USER_NAME", userName);
                    editor.apply(); // Salva a preferência

                    // Redireciona para a MainActivity
                    Intent intent = new Intent(InputActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish(); // Fecha a InputActivity
                }
            }
        });
    }

    // Método para exibir um alerta
    private void showAlert(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }
}