package academy.backend.pollbot.telegram.api;

public class BotOperationException extends RuntimeException {

    public BotOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
