package com.aqi.utils;

public class UserSession {

    private static String  userId   = null;
    private static String  username = null;
    private static boolean isNewUser = false;
    private static boolean guest    = false;
    private static boolean guestReturnToDashboard = false;

    // ── Regular user ─────────────────────────────────────────────
    public static void setUserId(String id)      { userId   = id; }
    public static String getUserId()             { return userId; }

    public static void setUsername(String name)  { username = name; }
    public static String getUsername()           { return username; }

    public static void setNewUser(boolean value) { isNewUser = value; }
    public static boolean isNewUser()            { return isNewUser; }

    public static boolean isLoggedIn()           { return userId != null; }

    // ── Guest mode ───────────────────────────────────────────────
    public static void loginAsGuest() {
        clearSession();
        guest    = true;
        username = "Guest";
    }

    public static boolean isGuest() { return guest; }

    /** Set true when guest hits a restricted action so SignUp shows back button */
    public static void setGuestReturnToDashboard(boolean flag) {
        guestReturnToDashboard = flag;
    }
    public static boolean isGuestReturnToDashboard() { return guestReturnToDashboard; }

    // ── Clear ────────────────────────────────────────────────────
    public static void clearSession() {
        userId                 = null;
        username               = null;
        isNewUser              = false;
        guest                  = false;
        guestReturnToDashboard = false;
    }

    /** Alias kept for compatibility */
    public static void clear() { clearSession(); }
}
