package com.intellij;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Notes extends Application {

    private static final Logger log = LoggerFactory.getLogger(Notes.class);

    public static void main(String[] args) throws Exception {
        launch(args);
    }

    public void start(Stage stage) throws Exception {

        log.info("Starting Hello JavaFX and Maven demonstration application");

        String fxmlFile = "fxml/main.fxml";
        log.debug("Loading FXML for main view from: {}", fxmlFile);
        FXMLLoader loader = new FXMLLoader();
        Parent rootNode = loader.load(getClass().getClassLoader().getResourceAsStream(fxmlFile));
        final MainController controller = loader.getController();
        controller.setMainStage(stage);

        log.debug("Showing JFX scene");
        Scene scene = new Scene(rootNode, 735, 463);
        scene.getStylesheets().add("/styles/styles.css");

        stage.setTitle("Notes");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }
}
