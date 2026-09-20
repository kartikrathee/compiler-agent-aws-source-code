package com.apac.atlas.config;

import com.apac.atlas.services.acm.CertificateGenerator;
import com.apac.atlas.services.acm.model.KeyAlgorithm;
import com.apac.atlas.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Integration test verifying TLS works with user-provided certificate and key files.
 *
 * <p>Generates a cert/key pair in the static initializer (before Quarkus starts),
 * then configures the profile to use those paths for both atlas config and
 * Quarkus SSL properties.
 */
@QuarkusTest
@TestProfile(TlsUserCertIntegrationTest.UserCertProfile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TlsUserCertIntegrationTest {

    @ConfigProperty(name = "quarkus.http.test-ssl-port", defaultValue = "0")
    int testSslPort;

    @BeforeAll
    void setupRestAssured() {
        RestAssured.config = RestAssuredConfig.config()
                .sslConfig(SSLConfig.sslConfig().relaxedHTTPSValidation());
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void healthEndpointOverHttpsWithUserCert() {
        given()
            .baseUri("https://localhost:" + testSslPort)
        .when()
            .get("/_atlas/health")
        .then()
            .statusCode(200)
            .body("edition", is("community"));
    }

    @Test
    void ssmPutParameterOverHttps() {
        given()
            .baseUri("https://localhost:" + testSslPort)
            .header("X-Amz-Target", "AmazonSSM.PutParameter")
            .contentType("application/x-amz-json-1.1")
            .body("{\"Name\": \"/tls-user-cert-test\", \"Value\": \"test-value\", \"Type\": \"String\"}")
        .when()
            .post("/")
        .then()
            .statusCode(200);
    }

    public static final class UserCertProfile implements QuarkusTestProfile {

        private static final Path CERT_DIR = Path.of("/tmp/atlas-tls-usercert-test");
        private static final Path CERT_FILE = CERT_DIR.resolve("user-test.crt");
        private static final Path KEY_FILE = CERT_DIR.resolve("user-test.key");

        static {
            generateCertIfNeeded();
        }

        private static void generateCertIfNeeded() {
            if (Files.exists(CERT_FILE) && Files.exists(KEY_FILE)) {
                return;
            }
            try {
                Files.createDirectories(CERT_DIR);
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }
                CertificateGenerator gen = new CertificateGenerator();
                CertificateGenerator.GeneratedCertificate cert = gen.generateCertificate(
                        "localhost", List.of("localhost", "127.0.0.1"), KeyAlgorithm.RSA_2048);
                Files.writeString(CERT_FILE, cert.certificatePem());
                Files.writeString(KEY_FILE, cert.privateKeyPem());
            } catch (IOException e) {
                throw new RuntimeException("Failed to generate test cert", e);
            }
        }

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "atlas.tls.enabled", "true",
                    "atlas.tls.self-signed", "false",
                    "atlas.tls.cert-path", CERT_FILE.toAbsolutePath().toString(),
                    "atlas.tls.key-path", KEY_FILE.toAbsolutePath().toString(),
                    "atlas.storage.persistent-path", "/tmp/atlas-tls-usercert-test-data",
                    "quarkus.http.ssl.certificate.files", CERT_FILE.toAbsolutePath().toString(),
                    "quarkus.http.ssl.certificate.key-files", KEY_FILE.toAbsolutePath().toString(),
                    "quarkus.http.insecure-requests", "enabled"
            );
        }
    }
}
