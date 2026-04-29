package ro.prospero.aidir.exception;

public class DrupalException extends RuntimeException {
    public DrupalException() {
    }

    public DrupalException(String message) {
        super(message);
    }
}
