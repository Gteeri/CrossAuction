package dev.crossauction.service;

/**
 * Thrown by AuctionService when a request is rejected for a known reason.
 * {@code getMessage()} returns a messages.yml key (never shown raw to the
 * player - callers look it up via Messages), and {@code code()} returns a
 * short machine-readable reason used for branching logic (e.g. detecting a
 * WRONG_TYPE buy-now attempt on an auction listing).
 */
public final class ServiceException extends RuntimeException {

    private final String code;

    public ServiceException(String code, String messageKey) {
        super(messageKey);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
