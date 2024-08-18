package basicOperation.util;

import lombok.Data;

@Data
public class Alert {
    private String message;
    private Long timestamp;

    public Alert() {
    }

    public Alert(String message, Long timestamp) {
        this.message = message;
        this.timestamp = timestamp;
    }
}
