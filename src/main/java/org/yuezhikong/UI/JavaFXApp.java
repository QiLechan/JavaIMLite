package org.yuezhikong.UI;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX Application 主入口。
 * 显示启动选择界面：启动客户端 或 启动服务端。
 */
public class JavaFXApp extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("JavaIM Lite");
        primaryStage.setScene(createChoiceScene());
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    private Scene createChoiceScene() {
        Label title = new Label("JavaIM Lite");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");

        Label subtitle = new Label("请选择启动模式");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        Button clientBtn = new Button("启动客户端");
        clientBtn.setPrefWidth(200);
        clientBtn.setPrefHeight(45);
        clientBtn.setStyle("-fx-font-size: 15px;");
        clientBtn.setOnAction(e -> openClientUI());

        Button serverBtn = new Button("启动服务端");
        serverBtn.setPrefWidth(200);
        serverBtn.setPrefHeight(45);
        serverBtn.setStyle("-fx-font-size: 15px;");
        serverBtn.setOnAction(e -> openServerUI());

        VBox layout = new VBox(15, title, subtitle, clientBtn, serverBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(40));
        layout.setStyle("-fx-background-color: #f5f5f5;");

        return new Scene(layout, 360, 300);
    }

    private void openClientUI() {
        ClientUI clientUI = new ClientUI(primaryStage);
        clientUI.show();
    }

    private void openServerUI() {
        ServerUI serverUI = new ServerUI(primaryStage);
        serverUI.show();
    }
}
