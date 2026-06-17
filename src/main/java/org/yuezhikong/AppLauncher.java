package org.yuezhikong;

import javafx.application.Application;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.yuezhikong.UI.JavaFXApp;

import java.security.Security;

/**
 * JavaFX GUI 模式的启动入口。
 * 此类不继承 javafx.application.Application，
 * 因此可以直接用 java -jar 启动。
 */
public class AppLauncher {
    public static void main(String[] args) {
        // 注册 Bouncy Castle 安全提供者
        Security.addProvider(new BouncyCastleProvider());
        // 启动 JavaFX 应用
        Application.launch(JavaFXApp.class, args);
    }
}
