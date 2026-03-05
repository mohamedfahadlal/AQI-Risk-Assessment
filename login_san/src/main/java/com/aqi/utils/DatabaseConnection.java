package com.aqi.utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    // Update these to match your local MySQL setup if needed
    private static final String URL = "jdbc:mysql://localhost:3306/aqi_app";
    private static final String USER = "root"; 
    private static final String PASSWORD = "MaxNino"; // Enter your MySQL password if you have one

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}