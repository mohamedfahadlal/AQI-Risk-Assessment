package com.aqi.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    // Correct Java syntax for your exact Supabase connection string
    private static final String URL = "jdbc:postgresql://aws-1-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require";
    private static final String USER = "postgres.ileoodctldnhridhabuj";
    private static final String PASSWORD = "P9n43OJAoK9N8ujU";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}