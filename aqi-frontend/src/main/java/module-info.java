module com.aqi.frontend {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.swing;
    requires javafx.media;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.google.gson;
    requires com.fasterxml.jackson.databind;
    requires java.mail;
    requires java.sql;
    requires java.net.http;
    requires org.postgresql.jdbc;
    requires jbcrypt;
    requires java.prefs;

    // Added for PDF export and Desktop (Browser) support
    requires org.apache.pdfbox;
    requires java.desktop;

    opens com.aqi.app         to javafx.fxml;
    opens com.aqi.controllers to javafx.fxml;
    opens com.aqi.utils       to javafx.fxml;
    opens com.aqi.services    to javafx.fxml;

    // Updated: Open to Jackson as well for API data binding
    opens com.example.aqidashboard to javafx.fxml, com.fasterxml.jackson.databind;
    opens com.example.test2   to javafx.fxml;
    opens images;

    exports com.aqi.app;
    exports com.aqi.controllers;
    exports com.aqi.services;
    exports com.example.aqidashboard;
    exports com.example.test2;
}