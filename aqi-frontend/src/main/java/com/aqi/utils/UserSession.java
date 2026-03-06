package com.aqi.utils;

/**
 * Singleton session store. Holds the currently logged-in user's data
 * so all controllers (Login, Dashboard, HealthProfile, Prediction, etc.)
 * can share the same state within one JVM process.
 */
public class UserSession {

    private static UserSession instance;

    private String userId;       // UUID string from Supabase
    private String username;     // Full name
    private boolean isNewUser;   // true = just registered, needs health profile

    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    // ---- Getters ----

    public static String getUserId() {
        return getInstance().userId;
    }

    public static String getUsername() {
        return getInstance().username;
    }

    public static boolean isNewUser() {
        return getInstance().isNewUser;
    }

    // ---- Setters ----

    public static void setUserId(String id) {
        getInstance().userId = id;
    }

    /** Backward-compat overload for int-based IDs */
    public static void setUserId(int id) {
        getInstance().userId = String.valueOf(id);
    }

    public static void setUsername(String name) {
        getInstance().username = name;
    }

    public static void setNewUser(boolean value) {
        getInstance().isNewUser = value;
    }

    /** Call on logout to wipe all session data */
    public static void clear() {
        getInstance().userId   = null;
        getInstance().username = null;
        getInstance().isNewUser = false;
    }
}
