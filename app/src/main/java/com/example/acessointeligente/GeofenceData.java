package com.example.acessointeligente;


public class GeofenceData {
    private String name;
    private double latitude;
    private double longitude;
    private float radius;

    // Construtor
    public GeofenceData(double latitude, double longitude, float radius, String name) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radius = radius;
    }


    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public float getRadius() {
        return radius;
    }
}
