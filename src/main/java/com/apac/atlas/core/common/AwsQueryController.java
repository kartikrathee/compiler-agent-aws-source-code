package com.apac.atlas.core.common;

import com.apac.atlas.services.ec2.Ec2QueryHandler;
import com.apac.atlas.services.cloudwatch.metrics.CloudWatchMetricsQueryHandler;
import com.apac.atlas.services.cognito.CognitoJsonHandler;
import com.apac.atlas.services.iam.IamQueryHandler;
import com.apac.atlas.services.iam.StsQueryHandler;
import com.apac.atlas.services.sns.SnsQueryHandler;
import com.apac.atlas.services.sqs.SqsQueryHandler;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic dispatcher for all AWS services that use the Query Protocol (form-encoded POST, XML response).
 * Routes requests to the appropriate service handler based on the service name extracted from the
 * Authorization header's credential scope, falling back to action-name matching when no auth header
 * is present.
 *
 * @see AwsJsonController
 */
@Path("/")
public class AwsQueryController {

    private static final Logger LOG = Logger.getLogger(AwsQueryController.class);

    // Extracts service name: Credential=AKID/20260227/us-east-1/iam/aws4_request → "iam"
    private static final Pattern SERVICE_PATTERN =
            Pattern.compile("Credential=\\S+/\\d{8}/[^/]+/([^/]+)/");

    private static final Set<String> STS_ACTIONS = Set.of(
            "AssumeRole", "AssumeRoleWithWebIdentity", "AssumeRoleWithSAML",
            "GetCallerIdentity", "GetSessionToken", "GetFederationToken",
            "DecodeAuthorizationMessage"
    );

    private static final Set<String> SNS_ACTIONS = Set.of(
            "CreateTopic", "DeleteTopic", "ListTopics", "GetTopicAttributes", "SetTopicAttributes",
            "Subscribe", "Unsubscribe", "ListSubscriptions", "ListSubscriptionsByTopic",
            "Publish", "PublishBatch", "TagResource", "UntagResource", "ListTagsForResource",
            "CreatePlatformApplication", "DeletePlatformApplication", "GetPlatformApplicationAttributes",
            "SetPlatformApplicationAttributes", "ListPlatformApplications",
            "CreatePlatformEndpoint", "DeleteEndpoint", "GetEndpointAttributes",
            "SetEndpointAttributes", "ListEndpointsByPlatformApplication",
            "CheckIfPhoneNumberIsOptedOut", "ListPhoneNumbersOptedOut",
            "OptInPhoneNumber", "SetSMSAttributes", "GetSMSAttributes",
            "GetSubscriptionAttributes", "SetSubscriptionAttributes", "ConfirmSubscription",
            "CreateSMSSandboxPhoneNumber", "DeleteSMSSandboxPhoneNumber",
            "GetSMSSandboxAccountStatus", "ListSMSSandboxPhoneNumbers",
            "VerifySMSSandboxPhoneNumber", "AddPermission", "RemovePermission"
    );

    private static final Set<String> IAM_ACTIONS = Set.of(
            "CreateUser", "GetUser", "DeleteUser", "ListUsers", "UpdateUser",
            "CreateGroup", "GetGroup", "DeleteGroup", "ListGroups",
            "AddUserToGroup", "RemoveUserFromGroup", "ListGroupsForUser",
            "CreateRole", "GetRole", "DeleteRole", "ListRoles", "UpdateRole",
            "CreatePolicy", "GetPolicy", "DeletePolicy", "ListPolicies",
            "CreatePolicyVersion", "GetPolicyVersion", "DeletePolicyVersion",
            "ListPolicyVersions", "SetDefaultPolicyVersion",
            "AttachUserPolicy", "DetachUserPolicy", "ListAttachedUserPolicies",
            "AttachGroupPolicy", "DetachGroupPolicy", "ListAttachedGroupPolicies",
            "AttachRolePolicy", "DetachRolePolicy", "ListAttachedRolePolicies",
            "PutUserPolicy", "GetUserPolicy", "DeleteUserPolicy", "ListUserPolicies",
            "PutRolePolicy", "GetRolePolicy", "DeleteRolePolicy", "ListRolePolicies",
            "PutGroupPolicy", "GetGroupPolicy", "DeleteGroupPolicy", "ListGroupPolicies",
            "CreateAccessKey", "DeleteAccessKey", "ListAccessKeys", "UpdateAccessKey",
            "CreateInstanceProfile", "GetInstanceProfile", "DeleteInstanceProfile",
            "ListInstanceProfiles", "AddRoleToInstanceProfile",
            "RemoveRoleFromInstanceProfile", "ListInstanceProfilesForRole",
            "TagUser", "UntagUser", "ListUserTags",
            "TagRole", "UntagRole", "ListRoleTags",
            "TagPolicy", "UntagPolicy", "ListPolicyTags",
            "CreateLoginProfile", "GetLoginProfile", "DeleteLoginProfile", "UpdateLoginProfile",
            "GenerateCredentialReport", "GetCredentialReport",
            "GetAccountSummary", "GetAccountAuthorizationDetails"
    );

    private static final Set<String> EC2_ACTIONS = Set.of(
            "RunInstances", "DescribeInstances", "TerminateInstances", "StartInstances", "StopInstances",
            "RebootInstances", "DescribeInstanceStatus", "DescribeInstanceAttribute", "ModifyInstanceAttribute",
            "CreateVpc", "DescribeVpcs", "DeleteVpc", "ModifyVpcAttribute", "DescribeVpcAttribute",
            "DescribeVpcEndpointServices",
            "CreateDefaultVpc", "AssociateVpcCidrBlock", "DisassociateVpcCidrBlock",
            "CreateSubnet", "DescribeSubnets", "DeleteSubnet", "ModifySubnetAttribute",
            "CreateSecurityGroup", "DescribeSecurityGroups", "DeleteSecurityGroup",
            "AuthorizeSecurityGroupIngress", "AuthorizeSecurityGroupEgress",
            "RevokeSecurityGroupIngress", "RevokeSecurityGroupEgress",
            "DescribeSecurityGroupRules", "ModifySecurityGroupRules",
            "UpdateSecurityGroupRuleDescriptionsIngress", "UpdateSecurityGroupRuleDescriptionsEgress",
            "CreateKeyPair", "DescribeKeyPairs", "DeleteKeyPair", "ImportKeyPair",
            "DescribeImages",
            "CreateTags", "DeleteTags", "DescribeTags",
            "CreateInternetGateway", "DescribeInternetGateways", "DeleteInternetGateway",
            "AttachInternetGateway", "DetachInternetGateway",
            "CreateRouteTable", "DescribeRouteTables", "DeleteRouteTable",
            "AssociateRouteTable", "DisassociateRouteTable", "CreateRoute", "DeleteRoute",
            "AllocateAddress", "AssociateAddress", "DisassociateAddress", "ReleaseAddress", "DescribeAddresses",
            "DescribeAvailabilityZones", "DescribeRegions", "DescribeAccountAttributes",
            "DescribeInstanceTypes"
    );

    private static final Set<String> CLOUDWATCH_ACTIONS = Set.of(
            "PutMetricData", "ListMetrics", "GetMetricStatistics", "GetMetricData",
            "PutMetricAlarm", "DescribeAlarms", "DeleteAlarms", "SetAlarmState",
            "ListTagsForResource", "TagResource", "UntagResource"
    );

    private static final Set<String> COGNITO_ACTIONS = Set.of(
            "AdminCreateUser", "AdminGetUser", "AdminDeleteUser", "AdminSetUserPassword",
            "AdminUpdateUserAttributes", "AdminUserGlobalSignOut", "ListUsers",
            "InitiateAuth", "AdminInitiateAuth", "RespondToAuthChallenge", "AdminRespondToAuthChallenge",
            "SignUp", "ConfirmSignUp", "ChangePassword", "ForgotPassword",
            "ConfirmForgotPassword", "GetUser", "UpdateUserAttributes",
            "CreateUserPool", "DescribeUserPool", "ListUserPools", "UpdateUserPool", "DeleteUserPool",
            "TagResource", "UntagResource", "ListTagsForResource",
            "CreateUserPoolClient", "DescribeUserPoolClient", "ListUserPoolClients", "DeleteUserPoolClient",
            "CreateGroup", "GetGroup", "ListGroups", "DeleteGroup",
            "AdminAddUserToGroup", "AdminRemoveUserFromGroup", "AdminListGroupsForUser"
    );

    private final SqsQueryHandler sqsQueryHandler;
    private final SnsQueryHandler snsQueryHandler;
    private final IamQueryHandler iamQueryHandler;
    private final StsQueryHandler stsQueryHandler;
    private final CloudWatchMetricsQueryHandler cloudWatchMetricsQueryHandler;
    private final CognitoJsonHandler cognitoJsonHandler;
    private final Ec2QueryHandler ec2QueryHandler;
    private final ResolvedServiceCatalog catalog;
    private final RegionResolver regionResolver;

    @Inject
    public AwsQueryController(SqsQueryHandler sqsQueryHandler, SnsQueryHandler snsQueryHandler,
                              IamQueryHandler iamQueryHandler, StsQueryHandler stsQueryHandler,
                              CloudWatchMetricsQueryHandler cloudWatchMetricsQueryHandler,
                              CognitoJsonHandler cognitoJsonHandler,
                              Ec2QueryHandler ec2QueryHandler,
                              ResolvedServiceCatalog catalog,
                              RegionResolver regionResolver) {
        this.sqsQueryHandler = sqsQueryHandler;
        this.snsQueryHandler = snsQueryHandler;
        this.iamQueryHandler = iamQueryHandler;
        this.stsQueryHandler = stsQueryHandler;
        this.cloudWatchMetricsQueryHandler = cloudWatchMetricsQueryHandler;
        this.cognitoJsonHandler = cognitoJsonHandler;
        this.ec2QueryHandler = ec2QueryHandler;
        this.catalog = catalog;
        this.regionResolver = regionResolver;
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_XML)
    public Response dispatch(
            @HeaderParam("Authorization") String authorization,
            @Context HttpHeaders httpHeaders,
            MultivaluedMap<String, String> formParams) {

        String action = formParams.getFirst("Action");
        if (action == null) {
            return null;
        }

        String service = resolveService(authorization, action);
        LOG.debugv("Query protocol service={0} action={1}", service, action);

        String region = regionResolver.resolveRegion(httpHeaders);

        return switch (service) {
            case "sqs" -> sqsQueryHandler.handle(action, formParams, region);
            case "sns" -> snsQueryHandler.handle(action, formParams, region);
            case "iam" -> iamQueryHandler.handle(action, formParams);
            case "sts" -> stsQueryHandler.handle(action, formParams);
            case "monitoring" -> cloudWatchMetricsQueryHandler.handle(action, formParams, region);
            case "cognito-idp" -> handleCognitoQuery(action, formParams, region);
            case "ec2" -> ec2QueryHandler.handle(action, formParams, region);
            default -> xmlErrorResponse("UnknownService",
                    "Unknown or unsupported service: " + service, 400);
        };
    }

    private Response handleCognitoQuery(String action, MultivaluedMap<String, String> formParams, String region) {
        // Cognito is primarily JSON 1.1, but we provide a bridge for Query protocol if hit.
        String xml = new XmlBuilder()
                .start("ErrorResponse")
                  .start("Error")
                    .elem("Type", "Sender")
                    .elem("Code", "UnsupportedOperation")
                    .elem("Message", "Operation " + action + " is not supported by Cognito via Query protocol.")
                  .end("Error")
                  .elem("RequestId", UUID.randomUUID().toString())
                .end("ErrorResponse")
                .build();
        return Response.status(400).entity(xml).type(MediaType.APPLICATION_XML).build();
    }

    /**
     * Determines the target AWS service. Prefers the service name embedded in the
     * Authorization header credential scope. Falls back to action-name lookup when
     * the header is absent (e.g. raw HTTP testing without AWS SDK auth).
     */
    private String resolveService(String authorization, String action) {
        if (authorization != null && !authorization.isEmpty()) {
            Matcher m = SERVICE_PATTERN.matcher(authorization);
            if (m.find()) {
                String scope = m.group(1).toLowerCase();
                ServiceDescriptor descriptor = catalog.byCredentialScope(scope).orElse(null);
                if (descriptor != null && descriptor.supportsProtocol(ServiceProtocol.QUERY)) {
                    return descriptor.externalKey();
                }
            }
        }
        return inferServiceFromAction(action);
    }

    private String inferServiceFromAction(String action) {
        if (STS_ACTIONS.contains(action)) {
            return "sts";
        }
        if (IAM_ACTIONS.contains(action)) {
            return "iam";
        }
        if (SNS_ACTIONS.contains(action)) {
            return "sns";
        }
        if (CLOUDWATCH_ACTIONS.contains(action)) {
            return "monitoring";
        }
        if (COGNITO_ACTIONS.contains(action)) {
            return "cognito-idp";
        }
        if (EC2_ACTIONS.contains(action)) {
            return "ec2";
        }
        // SQS actions are numerous and not enumerated — fall back to sqs only for
        // requests that arrived without an Authorization header (raw/test clients)
        return "sqs";
    }

    private Response xmlErrorResponse(String code, String message, int status) {
        String xml = new XmlBuilder()
                .start("ErrorResponse")
                  .start("Error")
                    .elem("Type", "Sender")
                    .elem("Code", code)
                    .elem("Message", message)
                  .end("Error")
                  .elem("RequestId", UUID.randomUUID().toString())
                .end("ErrorResponse")
                .build();
        return Response.status(status).entity(xml).type(MediaType.APPLICATION_XML).build();
    }
}
