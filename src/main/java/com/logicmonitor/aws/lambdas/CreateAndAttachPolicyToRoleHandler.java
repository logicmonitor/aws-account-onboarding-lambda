package com.logicmonitor.aws.lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.util.Map;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest;
import software.amazon.awssdk.services.iam.model.CreatePolicyResponse;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.GetPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetPolicyResponse;
import software.amazon.awssdk.services.iam.model.IamException;
import software.amazon.awssdk.services.iam.model.Policy;
import software.amazon.awssdk.services.iam.waiters.IamWaiter;

/**
 * This handler class will create role, policy and attach the policy to role.
 */
public class CreateAndAttachPolicyToRoleHandler implements RequestHandler<Map<String, Object>, String> {

    /**
     *
     * @param event The Lambda Function input contains principalAccountId, externalId, roleName, policyName, policyJson
     * @param context The Lambda execution environment context object.
     * @return role and policy arn
     */
    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        Region region = Region.AWS_GLOBAL;
        IamClient iamClient = IamClient.builder()
            .region(region)
            .build();
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        String principalAccountId = event.get("principalAccountId").toString();
        String policyName = event.get("policyName").toString();
        String policyJson = gson.toJson(event.get("policyJson"));
        String roleName = event.get("roleName").toString();
        String externalId = event.get("externalId").toString();

        String trustedEntityPolicyJson = gson.toJson(
            JsonParser.parseString(getTrustedEntityPolicy(externalId, principalAccountId))
        );
        Policy createdPolicy = createIAMPolicy(iamClient, policyName, policyJson);
        String createdRoleArn = createIAMRole(iamClient, roleName,
            trustedEntityPolicyJson);
        attachPolicyToRole(iamClient, roleName, createdPolicy.arn());
        return "Created and attached policy: " + createdPolicy.arn() + " to role: "
            + createdRoleArn;
    }

    private String getTrustedEntityPolicy(String externalId, String principalAccountId) {
        return "{\n"
            + "    \"Version\": \"2012-10-17\",\n"
            + "    \"Statement\": [\n"
            + "        {\n"
            + "            \"Effect\": \"Allow\",\n"
            + "            \"Action\": \"sts:AssumeRole\",\n"
            + "            \"Principal\": {\n"
            + "                \"AWS\": \"" + principalAccountId + "\"\n"
            + "            },\n"
            + "            \"Condition\": {\n"
            + "                \"StringEquals\": {\n"
            + "                    \"sts:ExternalId\": \"" + externalId + "\"\n"
            + "                }\n"
            + "            }\n"
            + "        }\n"
            + "    ]\n"
            + "}";
    }

    public static Policy createIAMPolicy(IamClient iam, String policyName, String policyDoc) {

        try {
            IamWaiter iamWaiter = iam.waiter();

            CreatePolicyRequest request = CreatePolicyRequest.builder()
                .policyName(policyName)
                .policyDocument(policyDoc)
                .build();

            CreatePolicyResponse response = iam.createPolicy(request);
            // Wait until the policy is created.
            GetPolicyRequest polRequest = GetPolicyRequest.builder()
                .policyArn(response.policy().arn())
                .build();

            WaiterResponse<GetPolicyResponse> waitUntilPolicyExists = iamWaiter.waitUntilPolicyExists(
                polRequest);
            waitUntilPolicyExists.matched().response().ifPresent(System.out::println);
            return response.policy();

        } catch (IamException ex) {
            System.err.println(ex.awsErrorDetails().errorMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        return null;
    }


    public static String createIAMRole(IamClient iam, String roleName,
        String assumeRolePolicyJson) {

        try {
            CreateRoleRequest request = CreateRoleRequest.builder()
                .roleName(roleName)
                .assumeRolePolicyDocument(assumeRolePolicyJson)
                .description("Created using the AWS SDK for Java")
                .build();

            CreateRoleResponse response = iam.createRole(request);
            return response.role().arn();
        } catch (IamException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
        return "";
    }

    private static void attachPolicyToRole(IamClient iam, String roleName, String policyArn) {
        AttachRolePolicyRequest request = AttachRolePolicyRequest.builder()
            .policyArn(policyArn)
            .roleName(roleName)
            .build();

        try {
            iam.attachRolePolicy(request);
        } catch (IamException e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }
    }
}