package tn.esprit.trippocketbackend.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
@Slf4j
public class GoogleTokenVerifierService {

    @Value("${app.google.client-id:}")
    private String googleClientId;

    public GoogleUserInfo verifyToken(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google credential is required.");
        }

        try {
            GoogleIdTokenVerifier.Builder verifierBuilder = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance());

            if (googleClientId != null && !googleClientId.isBlank() && !googleClientId.contains("your-google-client-id")) {
                verifierBuilder.setAudience(Collections.singletonList(googleClientId.trim()));
            }

            GoogleIdTokenVerifier verifier = verifierBuilder.build();
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                log.warn("Google token verification returned null (invalid signature, audience, or expired)");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired Google token.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            Boolean emailVerified = payload.getEmailVerified();
            if (emailVerified == null || !emailVerified) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account email is not verified.");
            }

            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account does not contain a valid email.");
            }

            String subject = payload.getSubject();
            String givenName = (String) payload.get("given_name");
            String familyName = (String) payload.get("family_name");
            String picture = (String) payload.get("picture");

            if (givenName == null || givenName.isBlank()) {
                String name = (String) payload.get("name");
                if (name != null && !name.isBlank()) {
                    String[] parts = name.split(" ", 2);
                    givenName = parts[0];
                    familyName = parts.length > 1 ? parts[1] : "";
                } else {
                    givenName = email.split("@")[0];
                    familyName = "";
                }
            }
            if (familyName == null) {
                familyName = "";
            }

            return new GoogleUserInfo(
                    subject,
                    email.toLowerCase().trim(),
                    true,
                    givenName,
                    familyName,
                    picture
            );

        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            log.error("Error occurred while verifying Google token", e);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to verify Google token: " + e.getMessage());
        }
    }

    public record GoogleUserInfo(
            String subject,
            String email,
            boolean emailVerified,
            String firstName,
            String lastName,
            String picture
    ) {}
}
