module org.example.startpage {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.media; // NEW: Required for video playback

    // This is the most important line for your error:
    opens com.aiq.controllers to javafx.fxml;

    // Also open the fxml folder itself if you have trouble loading resources
    opens com.aiq.fxml to javafx.fxml;

    exports com.aiq;
}