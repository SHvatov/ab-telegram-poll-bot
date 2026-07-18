package academy.backend.pollbot.telegram;

public class BotOperationException extends RuntimeException {

    public BotOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
