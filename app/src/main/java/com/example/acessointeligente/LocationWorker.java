package com.example.acessointeligente;


import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class LocationWorker extends Worker {
    public LocationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Inicia o serviço de localização
        Intent serviceIntent = new Intent(getApplicationContext(), LocationForegroundService.class);
        try {
            getApplicationContext().startForegroundService(serviceIntent);
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }
}
