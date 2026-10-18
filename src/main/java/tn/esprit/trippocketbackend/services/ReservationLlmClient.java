package tn.esprit.trippocketbackend.services;

/**
 * Small abstraction over the chat-completion API used by the Reservation AI assistant,
 * so the AI service can be unit-tested with a fake client.
 */
public interface ReservationLlmClient {

    /** True when an API key is configured (reservation.ai.api-key). */
    boolean isConfigured();

    /**
     * Sends one system + user message and returns the text of the answer.
     *
     * @throws LlmException when the API cannot be reached, refuses the call or returns nothing
     */
    String complete(String systemPrompt, String userPrompt, int maxTokens);

    class LlmException extends RuntimeException {
        public LlmException(String message) {
            super(message);
        }
    }
}
