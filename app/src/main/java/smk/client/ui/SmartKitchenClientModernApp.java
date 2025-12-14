package smk.client.ui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;   // <- add
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SmartKitchenClientModernApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/ClientTerminal.fxml"));
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/smk.css").toExternalForm());
        stage.setTitle("SmartKitchen — Client");
        stage.setScene(scene);
        stage.show();
    }
    public static void main(String[] args) { launch(args); }
}
