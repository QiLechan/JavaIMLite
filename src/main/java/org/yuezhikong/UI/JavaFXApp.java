/*
 * Simplified Chinese (简体中文)
 *
 * 版权所有 (C) 2023 QiLechan <qilechan@outlook.com> 和本程序的贡献者
 *
 * 本程序是自由软件：你可以再分发之和/或依照由自由软件基金会发布的 GNU 通用公共许可证修改之，无论是版本 3 许可证，还是 3 任何以后版都可以。
 * 发布该程序是希望它能有用，但是并无保障;甚至连可销售和符合某个特定的目的都不保证。请参看 GNU 通用公共许可证，了解详情。
 * 你应该随程序获得一份 GNU 通用公共许可证的副本。如果没有，请看 <https://www.gnu.org/licenses/>。
 * English (英语)
 *
 * Copyright (C) 2023 QiLechan <qilechan@outlook.com> and contributors to this program
 *
 *  This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or 3 any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
