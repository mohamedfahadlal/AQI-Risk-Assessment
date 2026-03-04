package com.aqi.utils;

public class UserSession {
    private static int loggedInUserId = -1;

    public static void setUserId(int id) {
        loggedInUserId = id;
    }

    public static int getUserId() {
        return loggedInUserId;
    }

    public static void clearSession() {
        loggedInUserId = -1;
    }
}