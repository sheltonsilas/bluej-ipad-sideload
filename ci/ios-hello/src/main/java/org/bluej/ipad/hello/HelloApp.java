package org.bluej.ipad.hello;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public final class HelloApp extends Application {
    @Override public void start(Stage stage) {
        stage.setTitle("BlueJ iPad feasibility");
        stage.setScene(new Scene(new StackPane(new Label("Hello iPad")), 640, 480));
        stage.show();
    }
    public static void main(String[] args) { launch(args); }
}
