package org.yuezhikong.UI;

import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.yuezhikong.Client.ClientMain;
import org.yuezhikong.Server.protocol.ChatProtocol;
import org.yuezhikong.Server.protocol.GeneralProtocol;

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 客户端 JavaFX 界面。
 * 继承 ClientMain，将消息输出重定向到 JavaFX TextArea。
 * 包含登录界面和聊天界面两个视图。
 */
public class ClientUI extends ClientMain {

    private final Stage stage;
    private final Gson gson = new Gson();
    private static final int PROTOCOL_VERSION = 1;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    // 登录界面控件
    private TextField addressField;
    private TextField portField;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField certPathField;
    private Button browseBtn;
    private Button connectBtn;
    private Label statusLabel;

    // 聊天界面控件
    private TextArea chatArea;
    private TextField messageField;
    private Button sendBtn;
    private Button disconnectBtn;
    private Label chatStatusLabel;

    private Scene loginScene;
    private Scene chatScene;
    private volatile boolean loggedIn = false;

    public ClientUI(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        loginScene = createLoginScene();
        chatScene = createChatScene();
        stage.setTitle("JavaIM - 客户端");
        stage.setScene(loginScene);
        stage.setResizable(true);
        stage.sizeToScene();
        // 关闭窗口时断开连接
        stage.setOnCloseRequest(e -> {
            loggedIn = false;
            try {
                if (channel != null && channel.isActive()) {
                    disconnect();
                }
            } catch (Exception ex) {
                // ignore
            }
        });
    }

    // ==================== 登录界面 ====================

    private Scene createLoginScene() {
        Label title = new Label("客户端登录");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label addrLabel = new Label("服务器地址:");
        addressField = new TextField("127.0.0.1");
        addressField.setPrefWidth(220);

        Label portLabel = new Label("服务器端口:");
        portField = new TextField("8080");

        Label userLabel = new Label("用户名:");
        usernameField = new TextField();

        Label passLabel = new Label("密码:");
        passwordField = new PasswordField();

        Label certLabel = new Label("CA 证书路径:");
        certPathField = new TextField();
        certPathField.setPromptText("选择服务器 CA 证书文件...");
        browseBtn = new Button("浏览");
        browseBtn.setOnAction(e -> browseCertFile());

        HBox certBox = new HBox(8, certPathField, browseBtn);
        HBox.setHgrow(certPathField, Priority.ALWAYS);

        connectBtn = new Button("连接");
        connectBtn.setPrefWidth(120);
        connectBtn.setDefaultButton(true);
        connectBtn.setOnAction(e -> doConnect());

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #d32f2f;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));
        grid.add(addrLabel, 0, 0);
        grid.add(addressField, 1, 0);
        grid.add(portLabel, 0, 1);
        grid.add(portField, 1, 1);
        grid.add(userLabel, 0, 2);
        grid.add(usernameField, 1, 2);
        grid.add(passLabel, 0, 3);
        grid.add(passwordField, 1, 3);
        grid.add(certLabel, 0, 4);
        grid.add(certBox, 1, 4);

        VBox layout = new VBox(15, title, grid, connectBtn, statusLabel);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(25));
        layout.setPrefWidth(420);

        return new Scene(layout);
    }

    private void browseCertFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择 CA 证书文件");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("证书文件", "*.crt", "*.pem", "*.cer"));
        File file = fc.showOpenDialog(stage);
        if (file != null) {
            certPathField.setText(file.getAbsolutePath());
        }
    }

    private void doConnect() {
        String address = addressField.getText().trim();
        String portStr = portField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String certPath = certPathField.getText().trim();

        // 输入校验
        if (address.isEmpty() || portStr.isEmpty() || username.isEmpty() || password.isEmpty() || certPath.isEmpty()) {
            statusLabel.setText("请填写所有字段");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            statusLabel.setText("端口格式不正确");
            return;
        }
        File certFile = new File(certPath);
        if (!certFile.exists()) {
            statusLabel.setText("证书文件不存在");
            return;
        }

        // 加载证书
        X509Certificate serverCACert;
        try (FileInputStream stream = new FileInputStream(certFile)) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
            serverCACert = (X509Certificate) factory.generateCertificate(stream);
        } catch (Exception e) {
            statusLabel.setText("证书加载失败: " + e.getMessage());
            return;
        }

        // 禁用连接按钮
        connectBtn.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: #1565c0;");
        statusLabel.setText("正在连接...");

        // 在后台线程启动客户端连接
        Thread clientThread = new Thread(() -> {
            try {
                start(address, port, username, password, serverCACert);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setStyle("-fx-text-fill: #d32f2f;");
                    statusLabel.setText("连接失败: " + e.getMessage());
                    connectBtn.setDisable(false);
                    loggedIn = false;
                });
            }
        }, "ClientNettyThread");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    // ==================== 聊天界面 ====================

    private Scene createChatScene() {
        // 顶部状态栏
        chatStatusLabel = new Label("未连接");
        chatStatusLabel.setStyle("-fx-font-size: 13px;");
        disconnectBtn = new Button("断开连接");
        disconnectBtn.setOnAction(e -> {
            try {
                disconnect();
            } catch (Exception ex) {
                // ignore
            }
        });
        HBox topBar = new HBox(10, chatStatusLabel);
        topBar.setAlignment(Pos.CENTER_LEFT);
        HBox topRight = new HBox(disconnectBtn);
        topRight.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(topBar, Priority.ALWAYS);

        HBox header = new HBox(topBar, topRight);
        header.setPadding(new Insets(8, 12, 8, 12));
        header.setStyle("-fx-background-color: #e3f2fd; -fx-border-color: #bbdefb; -fx-border-width: 0 0 1 0;");

        // 聊天显示区域
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setWrapText(true);
        chatArea.setStyle("-fx-font-family: 'Consolas', 'Microsoft YaHei'; -fx-font-size: 13px;");
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        // 消息输入区域
        messageField = new TextField();
        messageField.setPromptText("输入消息...");
        messageField.setOnAction(e -> doSend());

        sendBtn = new Button("发送");
        sendBtn.setPrefWidth(70);
        sendBtn.setDefaultButton(true);
        sendBtn.setOnAction(e -> doSend());

        HBox inputBar = new HBox(10, messageField, sendBtn);
        inputBar.setPadding(new Insets(10, 12, 10, 12));
        HBox.setHgrow(messageField, Priority.ALWAYS);

        VBox layout = new VBox(header, chatArea, inputBar);
        VBox.setVgrow(chatArea, Priority.ALWAYS);

        return new Scene(layout, 520, 480);
    }

    private void doSend() {
        if (channel == null || !channel.isActive()) return;
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        ChatProtocol chatProtocol = new ChatProtocol();
        chatProtocol.setSourceUserName(UserName);
        chatProtocol.setMessage(text);

        GeneralProtocol generalProtocol = new GeneralProtocol();
        generalProtocol.setProtocolName("ChatProtocol");
        generalProtocol.setProtocolVersion(PROTOCOL_VERSION);
        generalProtocol.setProtocolData(gson.toJson(chatProtocol));

        channel.writeAndFlush(gson.toJson(generalProtocol));
        messageField.clear();
    }

    // ==================== 切换到聊天界面 ====================

    private void switchToChat() {
        loggedIn = true;
        Platform.runLater(() -> {
            stage.setScene(chatScene);
            stage.setTitle("JavaIM - 客户端 [" + UserName + "]");
            chatStatusLabel.setText("已连接 - " + UserName);
            chatStatusLabel.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 13px;");
            chatArea.clear();
            appendChat("[系统] 已连接到服务器，欢迎！");
        });
    }

    private void switchToLogin(String reason) {
        loggedIn = false;
        Platform.runLater(() -> {
            stage.setScene(loginScene);
            stage.setTitle("JavaIM - 客户端");
            stage.sizeToScene();
            connectBtn.setDisable(false);
            statusLabel.setStyle("-fx-text-fill: #d32f2f;");
            statusLabel.setText(reason);
        });
    }

    private void appendChat(String message) {
        String time = LocalTime.now().format(timeFormatter);
        chatArea.appendText("[" + time + "] " + message + "\n");
        chatArea.positionCaret(chatArea.getText().length());
    }

    // ==================== 重写 ClientMain 的输出方法 ====================

    @Override
    protected void normalPrint(String data) {
        // 检测登录成功，切换到聊天界面
        if (!loggedIn && data != null && data.contains("登录成功")) {
            switchToChat();
            return;
        }
        // 检测登录失败
        if (!loggedIn && data != null && data.contains("登录失败")) {
            Platform.runLater(() -> {
                statusLabel.setStyle("-fx-text-fill: #d32f2f;");
                statusLabel.setText(data);
                connectBtn.setDisable(false);
            });
            return;
        }
        Platform.runLater(() -> {
            if (loggedIn && chatArea != null) {
                appendChat("[信息] " + data);
            }
        });
    }

    @Override
    protected void normalPrintf(String data, Object... args) {
        String formatted = String.format(data, args);
        // 检测登录成功
        if (!loggedIn && formatted.contains("登录成功")) {
            switchToChat();
            return;
        }
        Platform.runLater(() -> {
            if (loggedIn && chatArea != null) {
                appendChat("[信息] " + formatted);
            }
        });
    }

    @Override
    protected void displayChatMessage(String sourceUserName, String message) {
        Platform.runLater(() -> {
            if (chatArea != null) {
                appendChat("[" + sourceUserName + "]: " + message);
            }
        });
    }

    @Override
    protected void displayMessage(String message) {
        Platform.runLater(() -> {
            if (chatArea != null) {
                appendChat("[系统] " + message);
            }
        });
    }

    @Override
    protected void errorPrintf(String data, Object... args) {
        String formatted = String.format(data, args);
        Platform.runLater(() -> {
            if (loggedIn && chatArea != null) {
                appendChat("[错误] " + formatted);
            }
        });
    }

    /**
     * 重写 start() 以添加连接断开检测。
     * 当 Netty channel 关闭时，自动切换回登录界面。
     */
    @Override
    public void start(String serverAddress, int serverPort, String userName, String password, X509Certificate ServerCACert) {
        super.start(serverAddress, serverPort, userName, password, ServerCACert);

        // super.start() 在 channel 关闭后返回
        // 如果之前在聊天界面，切换回登录
        if (loggedIn) {
            switchToLogin("连接已断开");
        }
    }
}
