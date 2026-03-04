package com.aqi.services;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiService {

    private static final String BASE_URL = "http://localhost:8000";

    public static void updateProfile(String jsonData) {

        try {
            URL url = new URL(BASE_URL + "/update-profile");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonData.getBytes());
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            conn.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
