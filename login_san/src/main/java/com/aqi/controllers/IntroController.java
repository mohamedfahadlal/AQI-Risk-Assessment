package com.aqi.controllers;

import com.aqi.utils.SceneManager;
import javafx.fxml.FXML;

public class IntroController {
    @FXML
    private void goToLogin() {
        SceneManager.switchScene("/fxml/Login.fxml");
    }

    @FXML
    private void goToSignUp() {
        SceneManager.switchScene("/fxml/SignUp.fxml");
    }
}
