package com.apac.atlas.services.s3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class S3VirtualHostFilterTest {

    // --- extractBucket with baseHostname ---

    @ParameterizedTest
    @CsvSource({
            // Standard localhost endpoint
            "my-bucket.localhost:4566, localhost, my-bucket",
            "my-bucket.localhost,      localhost, my-bucket",
            // Custom single-label hostname
            "my-bucket.myhost,         myhost,    my-bucket",
            // Multi-label hostname (e.g. Docker compose service name)
            "my-bucket.atlas.internal, atlas.internal, my-bucket",
            // K8s-style service hostname with ATLAS_HOSTNAME set
            "my-bucket.atlas.default.svc.cluster.local, atlas.default.svc.cluster.local, my-bucket",
            "my-bucket.atlas-svc.namespace.svc, atlas-svc.namespace.svc, my-bucket",
            // localhost is always recognized regardless of baseHostname (fixes virtual-host when ATLAS_HOSTNAME=atlas)
            "my-bucket.localhost,      atlas, my-bucket",
            "my-bucket.localhost:4566, atlas, my-bucket",
            // AWS S3 domains (fallback — independent of baseHostname)
            "my-bucket.s3.amazonaws.com,               localhost, my-bucket",
            "my-bucket.s3.amazonaws.com:443,            localhost, my-bucket",
            "my-bucket.s3.us-east-1.amazonaws.com,      localhost, my-bucket",
            "my-bucket.s3.eu-west-1.amazonaws.com:443,  localhost, my-bucket",
            // LocalStack-compatible domains (*.localhost.localstack.cloud resolves to 127.0.0.1 via public DNS)
            "my-bucket.s3.localhost.localstack.cloud,           localhost, my-bucket",
            "my-bucket.s3.localhost.localstack.cloud:4566,      localhost, my-bucket",
            "my-bucket.s3.us-east-1.localhost.localstack.cloud, localhost, my-bucket",
            "my-bucket.localhost.localstack.cloud,              localhost, my-bucket",
            "my-bucket.localhost.localstack.cloud:4566,         localhost, my-bucket",
            // Atlas public wildcard DNS (*.s3.localhost.atlas.local and *.localhost.atlas.local resolve to 127.0.0.1)
            "my-bucket.s3.localhost.atlas.local,       localhost, my-bucket",
            "my-bucket.s3.localhost.atlas.local:4566,  localhost, my-bucket",
            "my-bucket.localhost.atlas.local,          localhost, my-bucket",
            "my-bucket.localhost.atlas.local:4566,     localhost, my-bucket",
            "my-bucket.s3.us-east-1.localhost.atlas.local, localhost, my-bucket",
    })
    void extractsBucketFromVirtualHostedStyle(String host, String baseHostname, String expectedBucket) {
        assertEquals(expectedBucket, S3VirtualHostFilter.extractBucket(host, baseHostname));
    }

    // --- Path-style: service hostname alone — must NOT extract a bucket ---

    @ParameterizedTest
    @CsvSource({
            // Bare hostname — no dot, never virtual-hosted
            "localhost:4566, localhost",
            "localhost,      localhost",
            "plain-host,     plain-host",
            // K8s service hostname used as endpoint (path-style) — must NOT be rewritten
            "atlas.default.svc.cluster.local,           localhost",
            "atlas-service.namespace.svc.cluster.local, localhost",
            "my-svc.default.svc,                        localhost",
            // Remainder doesn't match baseHostname and isn't an AWS S3 domain
            "my-bucket.custom.internal, localhost",
            "my-bucket.emulator.local,  localhost",
    })
    void returnsNullForPathStyleOrMismatchedRemainder(String host, String baseHostname) {
        assertNull(S3VirtualHostFilter.extractBucket(host, baseHostname));
    }

    @ParameterizedTest
    @CsvSource({
            "192.168.1.1,      localhost",
            "192.168.1.1:4566, localhost",
            "127.0.0.1,        localhost",
            "10.0.0.1:9000,    localhost",
    })
    void returnsNullForIpAddresses(String host, String baseHostname) {
        assertNull(S3VirtualHostFilter.extractBucket(host, baseHostname));
    }

    @ParameterizedTest
    @NullSource
    void returnsNullForNullHost(String host) {
        assertNull(S3VirtualHostFilter.extractBucket(host, "localhost"));
    }

    @Test
    void returnsNullForNullBaseHostname() {
        // path-style bare hostname (no subdomain) — must return null
        assertNull(S3VirtualHostFilter.extractBucket("localhost:4566", null));
        // well-known domains match regardless of baseHostname
        assertEquals("my-bucket", S3VirtualHostFilter.extractBucket("my-bucket.localhost", null));
        assertEquals("my-bucket", S3VirtualHostFilter.extractBucket("my-bucket.localhost:4566", null));
        assertEquals("my-bucket", S3VirtualHostFilter.extractBucket("my-bucket.s3.amazonaws.com", null));
        assertEquals("my-bucket", S3VirtualHostFilter.extractBucket("my-bucket.s3.localhost.localstack.cloud", null));
        assertEquals("my-bucket", S3VirtualHostFilter.extractBucket("my-bucket.localhost.localstack.cloud", null));
        assertEquals("my-bucket", S3VirtualHostFilter.extractBucket("my-bucket.s3.localhost.atlas.local", null));
        assertEquals("my-bucket", S3VirtualHostFilter.extractBucket("my-bucket.s3.us-east-1.localhost.atlas.local", null));
        assertEquals("my-bucket", S3VirtualHostFilter.extractBucket("my-bucket.localhost.atlas.local", null));
    }

    // --- Hostname extraction from URL ---

    @ParameterizedTest
    @CsvSource({
            "http://localhost:4566,                             localhost",
            "http://localhost,                                  localhost",
            "http://atlas.default.svc.cluster.local:4566,      atlas.default.svc.cluster.local",
            "http://atlas-service.namespace.svc.cluster.local, atlas-service.namespace.svc.cluster.local",
            "http://my-host:9000,                              my-host",
    })
    void extractsHostnameFromUrl(String url, String expectedHostname) {
        assertEquals(expectedHostname, S3VirtualHostFilter.extractHostnameFromUrl(url));
    }

    @Test
    void extractHostnameFromUrlReturnsNullForNull() {
        assertNull(S3VirtualHostFilter.extractHostnameFromUrl(null));
    }
}
