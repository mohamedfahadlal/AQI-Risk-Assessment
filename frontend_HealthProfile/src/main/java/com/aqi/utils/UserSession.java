package com.aqi.utils;

/**
 * Holds the currently logged-in user's ID for the session.
 * This is set by the login module after successful authentication
 * and read by HealthProfileController to identify the user.
 */
public class UserSession {

    private static String userId = null;

    public static void setUserId(String id) {
        userId = id;
    }

    public static String getUserId() {
        return userId;
    }

    public static void clearSession() {
        userId = null;
    }

    public static boolean isLoggedIn() {
        return userId != null;
    }
}
