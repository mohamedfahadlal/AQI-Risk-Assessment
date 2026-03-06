package com.aqi.utils;

/**
 * Holds the currently logged-in user's session data.
 * Set by the login module after successful authentication.
 */
public class UserSession {

    private static String userId   = null;
    private static String username = null;

    public static void setUserId(String id) {
        userId = id;
    }

    public static String getUserId() {
        return userId;
    }

    public static void setUsername(String name) {
        username = name;
    }

    public static String getUsername() {
        return username;
    }

    public static void clearSession() {
        userId   = null;
        username = null;
    }

    public static boolean isLoggedIn() {
        return userId != null;
    }
}
