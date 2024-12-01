package com.example.acessointeligente;

import android.location.Location;
import android.os.Build;
import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GeofenceEvent {
    private String eventType; // Ex.: "Entrada Confirmada"
    private double latitude;
    private double longitude;
    private Timestamp timestamp; // Usando Timestamp do Firebase
    private String userName; // Nome do usuário ou modelo do dispositivo
    private String geofenceName; // Nome da geofence
    private boolean isSynced; // Indica se o evento foi sincronizado com o Firebase

    // Construtor
    public GeofenceEvent(String eventType, Location location, String userName, String geofenceCode) {
        this.eventType = eventType;
        this.latitude = location.getLatitude();
        this.longitude = location.getLongitude();

        // Obter o timestamp local do dispositivo
        this.timestamp = new Timestamp(new Date());

        // Configurar o nome do usuário ou usar o modelo do dispositivo como fallback
        this.userName = (userName == null || userName.trim().isEmpty()) ? Build.MODEL : userName;

        // Informações adicionais da geofence
        this.geofenceName = geofenceName;

        // Inicialmente, assume que o evento ainda não foi sincronizado
        this.isSynced = false;
    }

    // Getters
    public String getEventType() {
        return eventType;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public String getUserName() {
        return userName;
    }

    public String getGeofenceName() {
        return geofenceName;
    }

    public boolean isSynced() {
        return isSynced;
    }

    // Setters
    public void setSynced(boolean synced) {
        isSynced = synced;
    }

    // Formatar timestamp em uma string no formato brasileiro
    public String getFormattedTimestamp() {
        Date date = timestamp.toDate();
        SimpleDateFormat sdf = new SimpleDateFormat("dd 'de' MMMM 'de' yyyy 'às' HH:mm:ss z", new Locale("pt", "BR"));
        return sdf.format(date);
    }
}
