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

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.yuezhikong.Server.Server;
import org.yuezhikong.Server.user.User;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 服务端 JavaFX 管理界面。
 * 提供服务启停、日志查看、在线用户列表和广播消息功能。
 */
public class ServerUI {

    private final Stage stage;
    private Scene serverScene;

    // 控件
    private TextField portField;
    private Button startBtn;
    private Button stopBtn;
    private Label statusLabel;
    private TextArea logArea;
    private ListView<String> userList;
    private ObservableList<String> userListData;
    private TextField broadcastField;
    private Button broadcastBtn;

    private Server server;
    private Thread serverThread;
    private javafx.animation.Timeline refreshTimeline;

    public ServerUI(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        serverScene = createServerScene();
        stage.setTitle("JavaIM - 服务端");
        stage.setScene(serverScene);
        stage.setResizable(true);
        stage.sizeToScene();
        // 关闭窗口时停止服务器
        stage.setOnCloseRequest(e -> {
            if (server != null) {
                try {
                    stopUserListRefresh();
                    removeLogAppender();
                    server.stop();
                } catch (Exception ex) {
                    // ignore
                }
            }
        });
    }

    private Scene createServerScene() {
        // ===== 顶部控制栏 =====
        Label portLabel = new Label("端口:");
        portField = new TextField("8080");
        portField.setPrefWidth(80);

        startBtn = new Button("启动服务");
        startBtn.setStyle("-fx-background-color: #4caf50; -fx-text-fill: white;");
        startBtn.setOnAction(e -> doStart());

        stopBtn = new Button("停止服务");
        stopBtn.setDisable(true);
        stopBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
        stopBtn.setOnAction(e -> doStop());

        statusLabel = new Label("已停止");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #757575;");

        HBox controlBar = new HBox(12, portLabel, portField, startBtn, stopBtn, new Separator(), statusLabel);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setPadding(new Insets(10, 12, 10, 12));
        controlBar.setStyle("-fx-background-color: #fafafa; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");

        // ===== 中间区域：日志 + 用户列表 =====
        // 日志区域
        Label logTitle = new Label("服务器日志");
        logTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setStyle("-fx-font-family: 'Consolas', 'Microsoft YaHei'; -fx-font-size: 12px;");
        VBox logBox = new VBox(5, logTitle, logArea);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        // 用户列表
        Label userTitle = new Label("在线用户");
        userTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        userListData = FXCollections.observableArrayList();
        userList = new ListView<>(userListData);
        userList.setPrefWidth(160);
        VBox userBox = new VBox(5, userTitle, userList);
        VBox.setVgrow(userList, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(logBox, userBox);
        splitPane.setDividerPositions(0.72);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // ===== 底部广播栏 =====
        Label broadcastLabel = new Label("广播/指令:");
        broadcastField = new TextField();
        broadcastField.setPromptText("输入消息或指令（以/开头），例如 /help, /list...");
        broadcastField.setOnAction(e -> doBroadcastOrCommand());

        broadcastBtn = new Button("发送");
        broadcastBtn.setPrefWidth(70);
        broadcastBtn.setDisable(true);
        broadcastBtn.setOnAction(e -> doBroadcastOrCommand());

        HBox bottomBar = new HBox(10, broadcastLabel, broadcastField, broadcastBtn);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 12, 10, 12));
        bottomBar.setStyle("-fx-background-color: #fafafa; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");
        HBox.setHgrow(broadcastField, Priority.ALWAYS);

        // ===== 根布局 =====
        VBox root = new VBox(controlBar, splitPane, bottomBar);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        return new Scene(root, 700, 520);
    }

    // ==================== 服务启停 ====================

    private void doStart() {
        String portStr = portField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("端口无效", "请输入 1-65535 之间的端口号");
            return;
        }

        // 注册 Log4j2 TextArea Appender
        setupLogAppender();

        // 更新 UI 状态
        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        portField.setDisable(true);
        broadcastBtn.setDisable(false);
        statusLabel.setText("正在启动...");
        statusLabel.setStyle("-fx-text-fill: #1565c0; -fx-font-size: 13px;");
        logArea.clear();
        appendLog("[系统] 正在启动服务器，端口: " + port);

        // 在后台线程启动服务器
        serverThread = new Thread(() -> {
            try {
                server = new Server();
                server.setGuiMode(true);
                server.start(port);
                // start() 中 networkServer.start() 是阻塞的
                // 当服务器停止后才会返回
            } catch (Exception e) {
                Platform.runLater(() -> {
                    appendLog("[错误] 服务器启动失败: " + e.getMessage());
                    resetUI(false);
                });
            }
        }, "ServerThread");
        serverThread.setDaemon(true);
        serverThread.start();

        // 启动用户列表刷新定时器
        startUserListRefresh();

        // 延迟更新状态为运行中
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> {
            if (server != null && Server.getInstance() != null) {
                statusLabel.setText("运行中 (端口 " + port + ")");
                statusLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 13px;");
            }
        });
        pause.play();
    }

    private void doStop() {
        appendLog("[系统] 正在关闭服务器...");
        stopBtn.setDisable(true);

        Thread stopThread = new Thread(() -> {
            try {
                if (server != null) {
                    server.stop();
                    server = null;
                }
            } catch (Exception e) {
                // ignore
            }
            Platform.runLater(() -> {
                stopUserListRefresh();
                removeLogAppender();
                resetUI(true);
                appendLog("[系统] 服务器已关闭");
            });
        }, "ServerStopThread");
        stopThread.setDaemon(true);
        stopThread.start();
    }

    private void resetUI(boolean stopped) {
        startBtn.setDisable(!stopped);
        stopBtn.setDisable(stopped);
        portField.setDisable(!stopped);
        broadcastBtn.setDisable(stopped);
        if (stopped) {
            statusLabel.setText("已停止");
            statusLabel.setStyle("-fx-text-fill: #757575; -fx-font-size: 13px;");
            userListData.clear();
        }
    }

    // ==================== 广播消息或执行指令 ====================

    private void doBroadcastOrCommand() {
        if (server == null) return;
        String input = broadcastField.getText().trim();
        if (input.isEmpty()) return;

        // 检查是否为指令（以 / 开头）
        if (input.startsWith("/")) {
            doCommand(input);
        } else {
            doBroadcast(input);
        }
        
        broadcastField.clear();
    }

    private void doBroadcast(String text) {
        if (server.getServerAPI() == null) return;

        try {
            server.getServerAPI().sendMessageToAllClient(text);
            appendLog("[广播] " + text);
        } catch (Exception e) {
            appendLog("[错误] 广播失败: " + e.getMessage());
        }
    }

    private void doCommand(String input) {
        if (server.getRequest() == null) return;

        try {
            // 去掉开头的 /
            String commandWithSlash = input;
            String command = input.substring(1);

            // 分割命令和参数
            String[] parts = command.split("\\s+");
            String cmd = parts[0];
            String[] args = new String[parts.length - 1];
            if (parts.length > 1) {
                System.arraycopy(parts, 1, args, 0, parts.length - 1);
            }

            appendLog("[指令] 执行: " + commandWithSlash);

            // 创建控制台用户并执行指令
            org.yuezhikong.Server.user.ConsoleUser consoleUser = new org.yuezhikong.Server.user.ConsoleUser();
            server.getRequest().commandRequest(cmd, args, consoleUser);
        } catch (Exception e) {
            appendLog("[错误] 指令执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== 用户列表刷新 ====================

    private void startUserListRefresh() {
        refreshTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(3), e -> refreshUserList())
        );
        refreshTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        refreshTimeline.play();
    }

    private void stopUserListRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            refreshTimeline = null;
        }
    }

    private void refreshUserList() {
        if (server == null || server.getServerAPI() == null) {
            userListData.clear();
            return;
        }
        try {
            List<User> users = server.getServerAPI().getValidUserList(false);
            userListData.clear();
            for (User u : users) {
                String status = u.isUserLogged() ? " [已登录]" : " [未登录]";
                userListData.add(u.getUserName() + status);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // ==================== 日志处理 ====================

    private void setupLogAppender() {
        try {
            TextAreaAppender.setTextArea(logArea);
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();

            // 如果已经存在同名 appender 则先移除
            Appender existing = config.getAppender("TextArea");
            if (existing != null) {
                existing.stop();
                config.getRootLogger().removeAppender("TextArea");
            }

            PatternLayout layout = PatternLayout.newBuilder()
                    .withConfiguration(config)
                    .withCharset(StandardCharsets.UTF_8)
                    .withPattern("[%d{HH:mm:ss}] [%t] [%level]: %msg%n")
                    .build();

            TextAreaAppender appender = new TextAreaAppender(
                    "TextArea", null, layout, true,
                    new org.apache.logging.log4j.core.config.Property[0]
            );
            appender.start();
            config.addAppender(appender);
            config.getRootLogger().addAppender(
                    config.getAppender("TextArea"),
                    org.apache.logging.log4j.Level.ALL,
                    null
            );
            ctx.updateLoggers();
        } catch (Exception e) {
            appendLog("[警告] 无法注册日志输出到界面: " + e.getMessage());
        }
    }

    private void removeLogAppender() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();
            Appender appender = config.getAppender("TextArea");
            if (appender != null) {
                appender.stop();
                config.getRootLogger().removeAppender("TextArea");
                ctx.updateLoggers();
            }
            TextAreaAppender.setTextArea(null);
        } catch (Exception e) {
            // ignore
        }
    }

    private void appendLog(String message) {
        if (logArea == null) return;
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.appendText("[" + time + "] " + message + "\n");
        logArea.positionCaret(logArea.getText().length());
    }

    // ==================== 辅助方法 ====================

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
