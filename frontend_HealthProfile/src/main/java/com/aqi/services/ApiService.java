package com.aqi.services;

<<<<<<< HEAD
import java.io.*;
import java.net.*;

public class ApiService {

    private static final String BASE_URL = "http://localhost:8080";

    /**
     * POST /api/health-profile
     * Saves or updates the user's health profile.
     * Called when the user clicks "Save Profile".
     */
    public static void saveProfile(String jsonData) {
        try {
            URL url = new URL(BASE_URL + "/api/health-profile");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonData.getBytes("UTF-8"));
=======
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
>>>>>>> origin/main
                os.flush();
            }

            int responseCode = conn.getResponseCode();
<<<<<<< HEAD
            System.out.println("Save profile response: " + responseCode);
            conn.disconnect();

        } catch (Exception e) {
            System.err.println("Failed to save profile: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * GET /api/health-profile/{userId}
     * Loads an existing profile to pre-fill the form.
     * Returns the JSON string if found, or null if no profile exists yet.
     */
    public static String loadProfile(String userId) {
        try {
            URL url = new URL(BASE_URL + "/api/health-profile/" + userId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();

            // 204 = No profile saved yet — return null so form stays blank
            if (responseCode == 204) {
                conn.disconnect();
                return null;
            }

            // 200 = Profile found — read and return JSON
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8")
                );
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                conn.disconnect();
                return sb.toString();
            }

            conn.disconnect();
            return null;

        } catch (Exception e) {
            System.err.println("Failed to load profile: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
=======
            System.out.println("Response Code: " + responseCode);

            conn.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
>>>>>>> origin/main
}
