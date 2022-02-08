package uk.nhs.prm.deduction.e2e.utility;

import uk.nhs.prm.deduction.e2e.nems.NemsEventMessage;
import uk.nhs.prm.deduction.e2e.nhs.NhsIdentityGenerator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class NemsEventFactory {

    public static NemsEventMessage createNemsEventFromTemplate(String nemsEventFilename, String nhsNumber, String nemsMessageId) {
        return createNemsEventFromTemplate(nemsEventFilename, nhsNumber, nemsMessageId, "B85612");
    }

    public static NemsEventMessage createNemsEventFromTemplate(String nemsEventFilename, String nhsNumber, String nemsMessageId, String previousGP) {
        return new NemsEventMessage(Resources.readTestResourceFile(nemsEventFilename)
                .replaceAll("__NHS_NUMBER__", nhsNumber)
                .replaceAll("__NEMS_MESSAGE_ID__", nemsMessageId)
                .replaceAll("__PREVIOUS_GP_ODS_CODE__", previousGP)
        );
    }

    public static Map<String, NemsEventMessage> getDLQNemsEventMessages() throws IOException {
        NemsEventMessage nhsNumberVerification = createNemsEventFromTemplate("nhs-number-verification-fail.xml", NhsIdentityGenerator.randomNhsNumber(), NhsIdentityGenerator.randomNemsMessageId());
        NemsEventMessage nhsNumberFieldNotPresent = createNemsEventFromTemplate("nhs-number-field-not-present.xml", NhsIdentityGenerator.randomNhsNumber(), NhsIdentityGenerator.randomNemsMessageId());
        NemsEventMessage nhsNumberVerificationFieldNotPresent = createNemsEventFromTemplate("nhs-number-verification-field-not-present.xml", NhsIdentityGenerator.randomNhsNumber(), NhsIdentityGenerator.randomNemsMessageId());
        NemsEventMessage episodeOfCareFieldNotPresent = createNemsEventFromTemplate("no-finished-episode-of-care.xml", NhsIdentityGenerator.randomNhsNumber(), NhsIdentityGenerator.randomNemsMessageId());
        NemsEventMessage managingOrganizationFieldNotPresent = createNemsEventFromTemplate("no-managing-organization.xml", NhsIdentityGenerator.randomNhsNumber(), NhsIdentityGenerator.randomNemsMessageId());
        NemsEventMessage odsCodeForFinishedPractiseNotPresent = createNemsEventFromTemplate("no-ods-code-for-finished-practise.xml", NhsIdentityGenerator.randomNhsNumber(), NhsIdentityGenerator.randomNemsMessageId());
        NemsEventMessage odsCodeIdentifierForManagingOrganizationNotPresent = createNemsEventFromTemplate("no-ods-code-identifier-for-managing-organization.xml", NhsIdentityGenerator.randomNhsNumber(), NhsIdentityGenerator.randomNemsMessageId());
        NemsEventMessage referenceForManagingOrganizationNotPresent = createNemsEventFromTemplate("no-reference-for-managing-organization.xml", NhsIdentityGenerator.randomNhsNumber(), NhsIdentityGenerator.randomNemsMessageId());
        NemsEventMessage uriForManagingOrganizationNotPresent = createNemsEventFromTemplate("no-reference-for-uri-for-managing-organization.xml", NhsIdentityGenerator.randomNhsNumber(), NhsIdentityGenerator.randomNemsMessageId());
        Map<String, NemsEventMessage> messages = new HashMap<>();
        messages.put("nhsNumberVerification", nhsNumberVerification);//p
        messages.put("nhsNumberFieldNotPresent", nhsNumberFieldNotPresent);//p
        messages.put("nhsNumberVerificationFieldNotPresent", nhsNumberVerificationFieldNotPresent);
        messages.put("episodeOfCareFieldNotPresent", episodeOfCareFieldNotPresent);//p
        messages.put("managingOrganizationFieldNotPresent", managingOrganizationFieldNotPresent);//p
        messages.put("odsCodeForFinishedPractiseNotPresent", odsCodeForFinishedPractiseNotPresent);
        messages.put("odsCodeIdentifierForManagingOrganizationNotPresent", odsCodeIdentifierForManagingOrganizationNotPresent);//p
        messages.put("referenceForManagingOrganizationNotPresent", referenceForManagingOrganizationNotPresent);
        messages.put("uriForManagingOrganizationNotPresent", uriForManagingOrganizationNotPresent);
        return messages;
    }
}