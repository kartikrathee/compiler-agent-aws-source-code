package com.apac.atlas.services.eventbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.apac.atlas.services.eventbridge.model.InputTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EventBridgeInvokerTest {

    private EventBridgeInvoker invoker;

    @BeforeEach
    void setUp() {
        invoker = new EventBridgeInvoker(
                mock(com.apac.atlas.services.lambda.LambdaService.class),
                mock(com.apac.atlas.services.sqs.SqsService.class),
                mock(com.apac.atlas.services.sns.SnsService.class),
                new ObjectMapper(),
                mock(com.apac.atlas.config.EmulatorConfig.class)
        );
    }

    @Test
    void extractJsonPath_topLevelField() {
        String event = "{\"source\":\"aws.s3\",\"detail-type\":\"Object Created\"}";
        assertEquals("aws.s3", invoker.extractJsonPath("$.source", event));
    }

    @Test
    void extractJsonPath_nestedField() {
        String event = "{\"detail\":{\"bucket\":{\"name\":\"my-bucket\"},\"object\":{\"key\":\"file.txt\"}}}";
        assertEquals("my-bucket", invoker.extractJsonPath("$.detail.bucket.name", event));
        assertEquals("file.txt", invoker.extractJsonPath("$.detail.object.key", event));
    }

    @Test
    void extractJsonPath_missingField_returnsNull() {
        String event = "{\"source\":\"aws.s3\"}";
        assertNull(invoker.extractJsonPath("$.detail.bucket.name", event));
    }

    @Test
    void extractJsonPath_nonTextualValueReturnsRawJson() {
        String event = "{\"detail\":{\"size\":42}}";
        assertEquals("42", invoker.extractJsonPath("$.detail.size", event));
    }

    @Test
    void applyInputPath_extractsNestedField() {
        String event = "{\"source\":\"aws.s3\",\"detail\":{\"bucket\":\"my-bucket\",\"key\":\"file.txt\"}}";
        String result = invoker.applyInputPath("$.detail", event);
        assertEquals("{\"bucket\":\"my-bucket\",\"key\":\"file.txt\"}", result);
    }

    @Test
    void applyInputPath_dollarSignReturnsFullEvent() {
        String event = "{\"source\":\"aws.s3\"}";
        assertEquals(event, invoker.applyInputPath("$", event));
    }

    @Test
    void applyInputPath_missingField_returnsFullEvent() {
        String event = "{\"source\":\"aws.s3\"}";
        assertEquals(event, invoker.applyInputPath("$.detail", event));
    }

    @Test
    void applyInputPath_scalarField_returnsText() {
        String event = "{\"detail\":{\"name\":\"test\"}}";
        assertEquals("test", invoker.applyInputPath("$.detail.name", event));
    }

    @Test
    void applyInputTransformer_substitutesVariables() {
        String eventJson = "{\"source\":\"aws.s3\",\"detail\":{\"bucket\":{\"name\":\"my-bucket\"},\"object\":{\"key\":\"photos/cat.jpg\"}}}";
        InputTransformer transformer = new InputTransformer(
                Map.of("bucket", "$.detail.bucket.name", "key", "$.detail.object.key"),
                "{\"bucket\": \"<bucket>\", \"key\": \"<key>\"}"
        );
        String result = invoker.applyInputTransformer(transformer, eventJson);
        assertEquals("{\"bucket\": \"my-bucket\", \"key\": \"photos/cat.jpg\"}", result);
    }

    @Test
    void applyInputTransformer_missingPath_substituteEmpty() {
        String eventJson = "{\"source\":\"aws.s3\"}";
        InputTransformer transformer = new InputTransformer(
                Map.of("bucket", "$.detail.bucket.name"),
                "bucket=<bucket>"
        );
        assertEquals("bucket=", invoker.applyInputTransformer(transformer, eventJson));
    }

    @Test
    void applyInputTransformer_nullTemplate_returnsEventJson() {
        String eventJson = "{\"source\":\"aws.s3\"}";
        InputTransformer transformer = new InputTransformer(Map.of(), null);
        assertEquals(eventJson, invoker.applyInputTransformer(transformer, eventJson));
    }
}
