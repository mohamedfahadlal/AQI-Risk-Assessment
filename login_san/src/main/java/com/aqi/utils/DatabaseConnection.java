package com.aqi.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    // Notice the URL is much cleaner now and includes sslmode=require
    private static final String URL = "jdbc:postgresql://db.ileoodctldnhridhabuj.supabase.co:5432/postgres?sslmode=require";
    private static final String USER = "postgres";
    private static final String PASSWORD = "P9n43OJAoK9N8ujU";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}