package org.yuezhikong.UI;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

/**
 * Log4j2 Appender，将日志输出到 JavaFX TextArea
 */
@Plugin(name = "TextArea", category = "Core", elementType = "Appender", printObject = true)
public class TextAreaAppender extends AbstractAppender {

    private static volatile TextArea textArea;

    public static void setTextArea(TextArea area) {
        textArea = area;
    }

    protected TextAreaAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, Property[] properties) {
        super(name, filter, layout, ignoreExceptions, properties);
    }

    @Override
    public void append(LogEvent event) {
        if (textArea == null) return;
        byte[] bytes = getLayout().toByteArray(event);
        String message = new String(bytes, StandardCharsets.UTF_8);
        Platform.runLater(() -> {
            textArea.appendText(message);
            // 自动滚动到底部
            textArea.positionCaret(textArea.getText().length());
        });
    }
}
