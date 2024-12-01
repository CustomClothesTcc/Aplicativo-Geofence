package com.example.acessointeligente;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

public class LocationForegroundService extends Service {
    private static final String CHANNEL_ID = "location_service_channel";
    private List<GeofenceData> geofenceList;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private String userName;
    private PowerManager.WakeLock wakeLock;
    private final Map<String, Timer> geofenceTimers = new HashMap<>();
    private boolean isHighAccuracyMode = false; // Mantém o estado do modo atual
    private final Map<String, Boolean> geofenceEntryState = new HashMap<>();
    private FirebaseFirestore db;
    private NetworkReceiver networkReceiver = new NetworkReceiver();


    private FirebaseFirestore firestore; // Referência ao Firestore
    private SharedPreferences sharedPreferences; // Para armazenar as geofences localmente


    @Override
    public void onCreate() {
        Log.d("LocationForegroundService", "LocationForegroundService Iniciada");
        super.onCreate();
        sharedPreferences = getSharedPreferences("GeofencesPrefs", MODE_PRIVATE);
        buildNotification();
        getNotification();
        createNotificationChannel();
        db = FirebaseFirestore.getInstance();
        registerNetworkReceiver();
        firestore = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        geofenceList = new ArrayList<>();

        // Carregar geofences da coleção Firestore ou do armazenamento local
        loadGeofencesFromLocal(); //chega até aqui, depois daqui para...
        // Atualiza a lista com dados do Firestore se houver conexão
        startGeofenceUpdateCycle();
        registerNetworkReceiver();
        registerReceiver(new NetworkReceiver(), new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        locationRequest = createLocationRequest(true); // Inicia com alta precisão
        locationCallback = new LocationCallback() {


            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Log.d("LocationForegroundService", "onLocationResult: " + locationResult.getLocations());
                for (Location location : locationResult.getLocations()) {
                    Log.d("LocationUpdate", "Location: " + location.getLatitude() + ", " + location.getLongitude());

                    // Ajusta o intervalo de atualização com base na proximidade das geofences
                    adjustLocationRequestBasedOnProximity(location);

                    // Verifica as geofences
                    checkGeofence(location);
                }
            }
        };
    }
    private Handler syncHandler = new Handler();
    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            if (isNetworkAvailable()) {
                syncOfflineEventsToFirebase(getApplicationContext());
            }
            syncHandler.postDelayed(this, 60000); // Tenta sincronizar a cada 1 minuto
        }
    };

    private final Map<String, Long> geofenceEntryConfirmationTime = new HashMap<>();
    private final Map<String, Long> geofenceExitConfirmationTime = new HashMap<>();
    private final long CONFIRMATION_DELAY_MS = 5 * 60 * 1000; // 5 minutos em milissegundos

    private void checkGeofence(Location location) {
        Log.d("LocationForegroundService", "Checking geofences for location: " + location.getLatitude() + ", " + location.getLongitude());
        for (GeofenceData geofence : geofenceList) {
            float[] results = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                    geofence.getLatitude(), geofence.getLongitude(), results);
            float distance = results[0];

            boolean currentlyInside = distance < geofence.getRadius();
            boolean wasInside = Boolean.TRUE.equals(geofenceEntryState.getOrDefault(geofence.getName(), false));
            Log.d("GeofenceCheck", "Geofence " + geofence.getName() + " - Distance: " + distance + ", Inside: " + currentlyInside);

            long currentTime = System.currentTimeMillis();

            if (currentlyInside) {
                // Caso o usuário esteja dentro da geofence
                if (!wasInside) {
                    // Inicia ou valida o timer de entrada
                    geofenceEntryConfirmationTime.putIfAbsent(geofence.getName(), currentTime);
                    long entryTimer = geofenceEntryConfirmationTime.get(geofence.getName());
                    if (currentTime - entryTimer >= CONFIRMATION_DELAY_MS) {
                        // Confirma entrada após 5 minutos
                        geofenceEntryState.put(geofence.getName(), true);
                        geofenceEntryConfirmationTime.remove(geofence.getName());
                        geofenceExitConfirmationTime.remove(geofence.getName()); // Limpa o timer de saída
                        generateGeofenceEvent(location, geofence, "Entrada Confirmada");
                    }
                } else {
                    // Já confirmado dentro, reseta o timer de entrada para evitar duplicidade
                    geofenceEntryConfirmationTime.remove(geofence.getName());
                }
            } else {
                // Caso o usuário esteja fora da geofence
                if (wasInside) {
                    // Inicia ou valida o timer de saída
                    geofenceExitConfirmationTime.putIfAbsent(geofence.getName(), currentTime);
                    long exitTimer = geofenceExitConfirmationTime.get(geofence.getName());
                    if (currentTime - exitTimer >= CONFIRMATION_DELAY_MS) {
                        // Confirma saída após 5 minutos
                        geofenceEntryState.put(geofence.getName(), false);
                        geofenceExitConfirmationTime.remove(geofence.getName());
                        geofenceEntryConfirmationTime.remove(geofence.getName()); // Limpa o timer de entrada
                        generateGeofenceEvent(location, geofence, "Saída Confirmada");
                    }
                } else {
                    // Já confirmado fora, reseta o timer de saída para evitar duplicidade
                    geofenceExitConfirmationTime.remove(geofence.getName());
                }
            }
        }
    }
    private void registerNetworkReceiver() {
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
    }

    private void generateGeofenceEvent(Location location, GeofenceData geofence, String eventType) {
        Log.d("GeofenceEvent", "Generating event for geofence: " + geofence.getName() + " with event type: " + eventType);

        String geofenceName = geofence.getName();

        GeofenceEvent geofenceEvent = new GeofenceEvent(eventType, location, userName, geofenceName);

        if (isNetworkAvailable()) {
            // Enviar para o Firestore
            Map<String, Object> record = new HashMap<>();
            record.put("timestamp", geofenceEvent.getTimestamp());
            record.put("event_type", geofenceEvent.getEventType());
            record.put("geofence_name", geofence.getName());
            record.put("user_name", geofenceEvent.getUserName());

            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("geofence_records").add(record)
                    .addOnSuccessListener(documentReference -> {
                        Log.d("GeofenceEvent", "Evento registrado no Firestore");
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Firestore", "Erro ao registrar evento", e);
                    });
        } else {
            // Armazenar offline
            storeEventOffline(location, geofence, eventType);

        }
    }


    // Verifica a conectividade
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // API 23 e superior
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false; // Nenhuma rede ativa
        }
        NetworkCapabilities networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork);
        return networkCapabilities != null &&
                (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    // Método para carregar as geofences do Firestore
    private void fetchGeofencesFromFirestore() {
        firestore.collection("cercas")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            geofenceList.clear(); // Limpa a lista antes de adicionar os dados do Firestore
                            for (QueryDocumentSnapshot document : querySnapshot) {
                                Map<String, Object> data = document.getData();
                                // Converte latitude e longitude de string para double
                                double latitude = Double.parseDouble((String) data.get("latitude"));
                                double longitude = Double.parseDouble((String) data.get("longitude"));
                                // Converte raio de string para float
                                float radius = Float.parseFloat(String.valueOf(data.get("raio")));
                                // Nome da geofence
                                String name = (String) data.get("nome");

                                // Adiciona a geofence à lista
                                geofenceList.add(new GeofenceData(latitude, longitude, radius, name));
                            }
                            saveGeofencesToLocal(); // Salva localmente para uso offline
                        }
                    } else {
                        Log.e("Firestore", "Erro ao carregar geofences", task.getException());
                    }
                });
    }
    // Salva as geofences no SharedPreferences para uso offline
    private void saveGeofencesToLocal() {
        Log.d("GeofencesSave", "Saving geofences locally");
        SharedPreferences.Editor editor = sharedPreferences.edit();
        JSONArray geofencesJsonArray = new JSONArray();

        try {
            for (GeofenceData geofence : geofenceList) {
                JSONObject geofenceJson = new JSONObject();
                geofenceJson.put("latitude", geofence.getLatitude());
                geofenceJson.put("longitude", geofence.getLongitude());
                geofenceJson.put("radius", geofence.getRadius());
                geofenceJson.put("name", geofence.getName());
                geofencesJsonArray.put(geofenceJson);
            }
            editor.putString("geofences", geofencesJsonArray.toString());
            editor.apply(); // Salva no SharedPreferences
        } catch (JSONException e) {
            Log.e("GeofencesSave", "Erro ao salvar geofences localmente", e);
        }
    }

    // Carrega as geofences do SharedPreferences para uso offline
    private void loadGeofencesFromLocal() {
        Log.d("GeofencesLoad", "Loading geofences locally");
        String geofencesString = sharedPreferences.getString("geofences", null);
        if (geofencesString != null) {
            try {
                JSONArray geofencesJsonArray = new JSONArray(geofencesString);
                for (int i = 0; i < geofencesJsonArray.length(); i++) {
                    JSONObject geofenceJson = geofencesJsonArray.getJSONObject(i);
                    double latitude = geofenceJson.getDouble("latitude");
                    double longitude = geofenceJson.getDouble("longitude");
                    float radius = (float) geofenceJson.getDouble("radius");
                    String name = geofenceJson.getString("name");
                    geofenceList.add(new GeofenceData(latitude, longitude, radius, name));
                }
            } catch (JSONException e) {
                Log.e("GeofencesLoad", "Erro ao carregar geofences localmente", e);
                Log.d("GeofencesLoad", "Failed to Load Geofences");

            }
        }
    }

    private void storeEventOffline(Location location, GeofenceData geofence, String eventType) {
        // Verificar se a função foi chamada
        Log.d("OfflineEvent", "storeEventOffline chamado.");
        Log.d("OfflineEvent", "Dados recebidos: " +
                "Location: [" + location.getLatitude() + ", " + location.getLongitude() + "], " +
                "Geofence: [" + geofence.getName() + ", Código: " + "], " +
                "Tipo de Evento: " + eventType + ", UserName: " + userName);

        // Guardar evento no cache do Firestore
        Map<String, Object> record = new HashMap<>();
        record.put("timestamp", System.currentTimeMillis());
        record.put("event_type", eventType);
        record.put("geofence_name", geofence.getName());
        record.put("user_name", userName);
        record.put("is_synced", false);  // Marca como não sincronizado

        firestore.collection("geofence_records_offline").add(record)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("OfflineEvent", "Evento salvo offline com sucesso.");
                    } else {
                        Log.e("OfflineEvent", "Erro ao salvar evento offline", task.getException());
                    }
                });
    }

    public class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isNetworkAvailable(context)) {
                Log.d("NetworkReceiver", "Conexão de rede detectada. Iniciando sincronização...");
                syncOfflineEventsToFirebase(context);
            } else {
                Log.d("NetworkReceiver", "Nenhuma conexão de rede disponível.");
            }
        }
        private boolean isNetworkAvailable(Context context) {
            Log.d("NetworkReceiver", "Tem internet ou não? Eis a questão.");
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) return false;
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            return capabilities != null &&
                    (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));

        }

    }
    private void syncOfflineEventsToFirebase(Context context) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        Log.d("SyncEvent", "Iniciando sincronização de eventos offline.");

        firestore.collection("geofence_records_offline")
                .whereEqualTo("is_synced", false)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        Log.d("SyncEvent", "Eventos offline encontrados: " + task.getResult().size());

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            Map<String, Object> record = document.getData();
                            firestore.collection("geofence_records").add(record)
                                    .addOnSuccessListener(docRef -> {
                                        Log.d("SyncEvent", "Evento sincronizado com sucesso: " + docRef.getId());

                                        // Marcar o evento como sincronizado
                                        document.getReference().update("is_synced", true)
                                                .addOnSuccessListener(aVoid ->
                                                        Log.d("SyncEvent", "Evento marcado como sincronizado.")
                                                )
                                                .addOnFailureListener(e ->
                                                        Log.e("SyncError", "Erro ao marcar evento como sincronizado", e)
                                                );
                                    })
                                    .addOnFailureListener(e ->
                                            Log.e("SyncError", "Erro ao sincronizar evento", e)
                                    );
                        }
                    } else if (task.isSuccessful()) {
                        Log.d("SyncEvent", "Nenhum evento offline para sincronizar.");
                    } else {
                        Log.e("SyncError", "Erro ao carregar eventos offline", task.getException());
                    }
                });
    }



    private Handler handler = new Handler();
    private final Runnable updateGeofencesRunnable = new Runnable() {
        @Override
        public void run() {
            // Atualiza as geofences
            fetchGeofencesFromFirestore(); // Atualiza com os dados mais recentes do Firestore
            handler.postDelayed(this, 259200000);
        }
    };

    // Método para iniciar a atualização a cada 1 minuto
    private void startGeofenceUpdateCycle() {
        Log.d("StartUpdate", "Iniciando Update de cercas");
        handler.post(updateGeofencesRunnable);
    }

    // Método para parar a atualização (caso necessário, por exemplo, quando o serviço é interrompido)
    private void stopGeofenceUpdateCycle() {
        handler.removeCallbacks(updateGeofencesRunnable);
    }

    // Ajusta a precisão do LocationRequest dinamicamente
    private void adjustLocationRequestBasedOnProximity(Location location) {
        Log.d("AdjustLocationRequest", "Ajustando Localização...");
        boolean isCloseToAnyGeofence = false;

        for (GeofenceData geofence : geofenceList) {
            float[] results = new float[1];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                    geofence.getLatitude(), geofence.getLongitude(), results);
            float distance = results[0];

            if (distance < geofence.getRadius() + 50) { // Margem de proximidade de 50 metros
                isCloseToAnyGeofence = true;
                break;
            }
        }

        // Verifica se é necessário ajustar o LocationRequest
        if (isCloseToAnyGeofence && !isHighAccuracyMode) {
            isHighAccuracyMode = true;
            LocationRequest locationRequest = createLocationRequest(true);
            restartLocationUpdates(locationRequest);
        } else if (!isCloseToAnyGeofence && isHighAccuracyMode) {
            isHighAccuracyMode = false;
            LocationRequest locationRequest = createLocationRequest(false);
            restartLocationUpdates(locationRequest);
        }
    }

    // Método para reiniciar as atualizações de localização
    private void restartLocationUpdates(LocationRequest locationRequest) {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        } catch (SecurityException e) {
            Log.e("LocationService", "Permissão de localização não concedida.", e);
        }
    }

    // Método para criar o LocationRequest com base na precisão desejada
    private LocationRequest createLocationRequest(boolean highAccuracy) {
        LocationRequest.Builder builder;

        if (highAccuracy) {
            builder = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMinUpdateIntervalMillis(5000) // Atualização mínima de 5 segundos
                    .setMaxUpdateDelayMillis(30000); // Atualização máxima de 30 segundos
        } else {
            builder = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMinUpdateIntervalMillis(2 * 60 * 1000) // Atualização mínima de 2 minutos
                    .setMaxUpdateDelayMillis(5 * 60 * 1000); // Atualização máxima de 5 minutos
        }

        return builder.build();
    }
    private Notification getNotification() {
        Log.d("getNotification", "Notificação criada.");
        return new NotificationCompat.Builder(this, CHANNEL_ID)

                .setContentTitle("Serviço de Localização Ativo")
                .setContentText("O serviço de localização está em execução.")
                .setSmallIcon(R.drawable.ic_logo) // Substitua pelo seu ícone
                .build();
    }
    private Notification buildNotification() {
        Log.d("Building Notification", "build notification chamada");
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Serviço de Localização Ativo")
                .setContentText("Monitorando a localização em segundo plano.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("onStartCommand", "entrou no onStartCommand");
        createNotificationChannel(); // Certifique-se de que o canal de notificação está criado antes de iniciar o serviço em primeiro plano
        startForeground(1, createNotification()); // Exibe a notificação do serviço em primeiro plano
        if (intent != null) {
            userName = intent.getStringExtra("USER_NAME"); // Armazena o nome do usuário
        }
        startLocationUpdates(); // Inicia as atualizações de localização
        syncHandler.post(syncRunnable); // Inicia a sincronização em intervalos
        Log.d("onStartCommand", "Saiu do onStartCommand");
        return START_STICKY; // Permite que o serviço continue rodando após ser encerrado
    }

    private Notification createNotification() {
        Log.d("CreateNotification", "Notificação criada.");
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)

                .setContentTitle("Ponto Enebras")
                .setContentText("Obrigado Por Utilizar nosso serviço.")
                .setSmallIcon(R.drawable.ic_logo) // Substitua pelo seu ícone
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        Log.d("createNotificationChannel", "Notification Channel criado.");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Serviço de Localização",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }


    private void startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        } catch (SecurityException e) {
            Log.e("LocationForegroundService", "Permissão de localização não concedida.", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void releaseWakeLock() {
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Não é um serviço vinculado
    }
}