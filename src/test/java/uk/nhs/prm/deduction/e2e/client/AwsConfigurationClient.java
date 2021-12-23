package uk.nhs.prm.deduction.e2e.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

@Component
@Slf4j
public class AwsConfigurationClient {
    private SsmClient ssmClient;

    public AwsConfigurationClient() {
        Region region =Region.EU_WEST_2;
        ssmClient = SsmClient.builder()
                .region(region)
                .build();
    }

    public String getParamValue(String paramName) {

        try {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(paramName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            return parameterResponse.parameter().value();

        } catch (SsmException e) {
            System.err.println(String.format("Error for ssm parameter %s and error is %s",paramName, e.getMessage()));
            System.exit(1);
        }
        return paramName;
    }
}
