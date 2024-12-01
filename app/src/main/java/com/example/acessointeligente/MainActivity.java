package com.example.acessointeligente;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 2; // Código para notificações
    private static final int BATTERY_OPTIMIZATION_REQUEST_CODE = 3; // Código para ignorar otimização de bateria
    private TextView statusTextView;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicialização do Firebase
        FirebaseApp.initializeApp(this);
        statusTextView = findViewById(R.id.statusTextView);
        db = FirebaseFirestore.getInstance();

        // Verifica se o nome do usuário já está salvo nas SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String userName = sharedPreferences.getString("USER_NAME", null);

        if (userName == null) {
            // Se o nome não estiver salvo, redireciona para a tela de entrada
            Intent intent = new Intent(MainActivity.this, InputActivity.class);
            startActivity(intent);
            finish(); // Fecha a MainActivity
            return;
        }

        // Exibe a mensagem de boas-vindas
        statusTextView.setText("Bem-vindo, " + userName);

        // Verifica as permissões de localização
        checkLocationPermissions(userName);
    }

    private void checkLocationPermissions(String userName) {
        boolean locationPermissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocationPermissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean foregroundServiceLocationPermissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!locationPermissionGranted || !coarseLocationPermissionGranted || !foregroundServiceLocationPermissionGranted) {
            // Solicita permissões de localização e de serviço em primeiro plano
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION
            }, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Se as permissões de localização já foram concedidas, verifica a permissão de notificações
            checkNotificationPermission(userName);
        }
    }

    private void checkNotificationPermission(String userName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Se o Android for 13 ou superior, verifica a permissão de notificações
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            } else {
                // Se a permissão de notificações for concedida, verifica a otimização de bateria
                checkBatteryOptimization(userName);
            }
        } else {
            // Para versões anteriores, não é necessário verificar a permissão de notificações
            checkBatteryOptimization(userName);
        }
    }

    private void checkBatteryOptimization(String userName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE);
            } else {
                // Inicia o serviço de localização se já estiver ignorando a otimização
                // Inicia o serviço de localização se já estiver ignorando a otimização
                startLocationService(userName);
            }
        } else {
            // Para versões anteriores do Android, inicia o serviço de localização diretamente
            startLocationService(userName);
        }
    }

    private void startLocationService(String userName) {
        // Inicia o serviço de localização passando o nome do usuário como extra
        Intent serviceIntent = new Intent(this, LocationForegroundService.class);
        serviceIntent.putExtra("USER_NAME", userName);
        // Use ContextCompat para iniciar o serviço em primeiro plano
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // Verifica se todas as permissões de localização foram concedidas
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                // As permissões de localização foram concedidas, verifica a permissão de notificações
                SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                String userName = sharedPreferences.getString("USER_NAME", null);
                checkNotificationPermission(userName);
            } else {
                // Se as permissões de localização foram negadas
                statusTextView.setText("Permissão de localização negada");
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // A permissão de notificações foi concedida
                SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                String userName = sharedPreferences.getString("USER_NAME", null);
                checkBatteryOptimization(userName); // Verifica a otimização de bateria
            } else {
                // Se a permissão de notificações foi negada
                statusTextView.setText("Permissão de notificações negada");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BATTERY_OPTIMIZATION_REQUEST_CODE) {
            // Verifica se a otimização de bateria foi ignorada após a solicitação
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                String packageName = getPackageName();
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm.isIgnoringBatteryOptimizations(packageName)) {
                    // A otimização de bateria foi ignorada, inicia o serviço de localização
                    SharedPreferences sharedPreferences = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
                    String userName = sharedPreferences.getString("USER_NAME", null);
                    startLocationService(userName);
                } else {
                    // A otimização de bateria ainda está ativada
                    statusTextView.setText("A otimização de bateria ainda está ativada. Por favor, desative-a.");
                }
            }
        }
    }
}