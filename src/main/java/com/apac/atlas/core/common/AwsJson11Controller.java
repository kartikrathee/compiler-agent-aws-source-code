package com.apac.atlas.core.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.apac.atlas.services.acm.AcmJsonHandler;
import com.apac.atlas.services.ecr.EcrJsonHandler;
import com.apac.atlas.services.ecs.EcsJsonHandler;
import com.apac.atlas.services.apigatewayv2.ApiGatewayV2JsonHandler;
import com.apac.atlas.services.cloudwatch.logs.CloudWatchLogsHandler;
import com.apac.atlas.services.cognito.CognitoJsonHandler;
import com.apac.atlas.services.eventbridge.EventBridgeHandler;
import com.apac.atlas.services.kinesis.KinesisJsonHandler;
import com.apac.atlas.services.kms.KmsJsonHandler;
import com.apac.atlas.services.secretsmanager.SecretsManagerJsonHandler;
import com.apac.atlas.services.ssm.Ec2MessagesJsonHandler;
import com.apac.atlas.services.ssm.SsmJsonHandler;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

/**
 * Generic dispatcher for all AWS services that use the application/x-amz-json-1.1 protocol.
 * Routes requests to the appropriate service handler based on the X-Amz-Target header prefix.
 */
@Path("/")
public class AwsJson11Controller {

    private static final Logger LOG = Logger.getLogger(AwsJson11Controller.class);

    private final ObjectMapper objectMapper;
    private final ResolvedServiceCatalog catalog;
    private final RegionResolver regionResolver;
    private final SsmJsonHandler ssmJsonHandler;
    private final EventBridgeHandler eventBridgeHandler;
    private final CloudWatchLogsHandler cloudWatchLogsHandler;
    private final SecretsManagerJsonHandler secretsManagerJsonHandler;
    private final KinesisJsonHandler kinesisJsonHandler;
    private final ApiGatewayV2JsonHandler apigwV2JsonHandler;
    private final KmsJsonHandler kmsJsonHandler;
    private final CognitoJsonHandler cognitoJsonHandler;
    private final AcmJsonHandler acmJsonHandler;
    private final EcsJsonHandler ecsJsonHandler;
    private final EcrJsonHandler ecrJsonHandler;
    private final Ec2MessagesJsonHandler ec2MessagesJsonHandler;

    @Inject
    public AwsJson11Controller(ObjectMapper objectMapper, ResolvedServiceCatalog catalog,
                               RegionResolver regionResolver,
                               SsmJsonHandler ssmJsonHandler, EventBridgeHandler eventBridgeHandler,
                               CloudWatchLogsHandler cloudWatchLogsHandler,
                               SecretsManagerJsonHandler secretsManagerJsonHandler,
                               KinesisJsonHandler kinesisJsonHandler,
                               ApiGatewayV2JsonHandler apigwV2JsonHandler,
                               KmsJsonHandler kmsJsonHandler, CognitoJsonHandler cognitoJsonHandler,
                               AcmJsonHandler acmJsonHandler, EcsJsonHandler ecsJsonHandler,
                               EcrJsonHandler ecrJsonHandler,
                               Ec2MessagesJsonHandler ec2MessagesJsonHandler) {
        this.objectMapper = objectMapper;
        this.catalog = catalog;
        this.regionResolver = regionResolver;
        this.ssmJsonHandler = ssmJsonHandler;
        this.eventBridgeHandler = eventBridgeHandler;
        this.cloudWatchLogsHandler = cloudWatchLogsHandler;
        this.secretsManagerJsonHandler = secretsManagerJsonHandler;
        this.kinesisJsonHandler = kinesisJsonHandler;
        this.apigwV2JsonHandler = apigwV2JsonHandler;
        this.kmsJsonHandler = kmsJsonHandler;
        this.cognitoJsonHandler = cognitoJsonHandler;
        this.acmJsonHandler = acmJsonHandler;
        this.ecsJsonHandler = ecsJsonHandler;
        this.ecrJsonHandler = ecrJsonHandler;
        this.ec2MessagesJsonHandler = ec2MessagesJsonHandler;
    }

    @POST
    @Consumes("application/x-amz-json-1.1")
    @Produces("application/x-amz-json-1.1")
    public Response handle(
            @HeaderParam("X-Amz-Target") String target,
            @Context HttpHeaders httpHeaders,
            String body) {

        if (target == null) {
            return null;
        }

        ServiceCatalog.TargetMatch targetMatch = catalog.matchTarget(target).orElse(null);
        if (targetMatch == null) {
            return JsonErrorResponseUtils.createUnknownOperationErrorResponse(target);
        }

        String serviceKey = targetMatch.descriptor().externalKey();
        String action = targetMatch.action();
        LOG.infov("AwsJson11Controller {0} action: {1}", serviceKey, action);

        try {
            JsonNode request = objectMapper.readTree(body);
            String region = regionResolver.resolveRegion(httpHeaders);

            Response delegated = switch (serviceKey) {
                case "ssm" -> ssmJsonHandler.handle(action, request, region);
                case "events" -> eventBridgeHandler.handle(action, request, region);
                case "logs" -> cloudWatchLogsHandler.handle(action, request, region);
                case "secretsmanager" -> secretsManagerJsonHandler.handle(action, request, region);
                case "kinesis" -> kinesisJsonHandler.handle(action, request, region);
                case "apigatewayv2" -> apigwV2JsonHandler.handle(action, request, region);
                case "kms" -> kmsJsonHandler.handle(action, request, region);
                case "cognito-idp" -> cognitoJsonHandler.handle(action, request, region);
                case "acm" -> acmJsonHandler.handle(action, request, region);
                case "ecs" -> ecsJsonHandler.handle(action, request, region);
                case "ecr" -> ecrJsonHandler.handle(action, request, region);
                case "ec2messages" -> ec2MessagesJsonHandler.handle(action, request, region);
                default -> null;
            };
            // catalog.matchTarget is protocol-agnostic: a JSON 1.0 target
            // (e.g. DynamoDB_20120810.*) can match here under @Consumes json-1.1.
            // Return the AWS-style unknown-operation error rather than null.
            if (delegated == null) {
                return JsonErrorResponseUtils.createUnknownOperationErrorResponse(target);
            }
            return delegated;
        } catch (AwsException e) {
            return JsonErrorResponseUtils.createErrorResponse(e);
        } catch (Exception e) {
            LOG.errorf(e, "Error processing %s request", serviceKey);
            return JsonErrorResponseUtils.createErrorResponse(e);
        }
    }

}
