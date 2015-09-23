package com.almende.dialog.adapter;

import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.SMSDeliveryStatus;
import com.almende.dialog.agent.DDRRecordAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.model.ddr.DDRRecord.CommunicationStatus;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DDRType.DDRTypeCategory;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;

@Category(IntegrationTest.class)
public class RouteSMSIT extends TestFramework {

    private static final String simpleQuestion = "How are you?";

    @Before
    public void setup() throws Exception {

        super.setup();
        new DialogAgent().setDefaultProviderSettings(AdapterType.SMS, AdapterProviders.ROUTE_SMS);
    }

    @Test
    public void outBoundSMSCallStatusCheck() throws Exception {

        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS,
                                                          TEST_ACCOUNT_ID, "0612345678", "0612345678", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();
        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        outBoundSMSCallXMLTest(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, senderName,
                               "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner());
        //fetch the sms delivery status reports
        List<SMSDeliveryStatus> smsStatues = SMSDeliveryStatus.fetchAll();
        Assert.assertThat(smsStatues, Matchers.notNullValue());
        Assert.assertThat(smsStatues.size(), Matchers.is(1));
        Assert.assertThat(smsStatues.iterator().next().getCode(), Matchers.is("1701"));
        Assert.assertThat(smsStatues.iterator().next().getDescription(), Matchers.is("Successfully Sent"));
    }
    
    /**
     * Checks outbound SMS with invalid numbers. Creates sessions for landlines
     * number, but should not create sessions for invalid numbers.
     * 
     * @throws Exception
     */
    @Test
    public void outBoundSMSWithInvalidNumberDDRCheck() throws Exception {

        String senderName = "TestUser";
        String landlineNumber = "0103031111";
        String invalidNumber = "1234";

        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS,
                                                          TEST_ACCOUNT_ID, "0612345678", "0612345678", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();

        //create ddrType for the ddr records to be created.
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.10, "outgoing sms", UnitType.PART,
                           AdapterType.SMS, null);

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);
        addressMap.put(landlineNumber, null);
        addressMap.put(invalidNumber, null);

        outBoundSMSCallXMLTest(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, senderName,
                               "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner());
        //fetch sessions
        List<Session> allSessions = Session.getAllSessions();
        //sessions for the landline and the invalid numbers must be dropped
        Assert.assertThat(allSessions.size(), Matchers.is(1));
        for (Session session : allSessions) {
            Assert.assertThat(session.getRemoteAddress(), Matchers.not(invalidNumber));
            Assert.assertThat(session.getRemoteAddress(), Matchers.not(landlineNumber));
        }

        //fetch the sms ddr records
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(TEST_ACCOUNT_ID, null, null, null, null, null, null, null,
                                                             null, null, null);
        Assert.assertThat(allSessions.size(), Matchers.is(1));
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        Assert.assertThat(ddrRecord.getStatusForAddress(invalidNumber), Matchers.is(CommunicationStatus.ERROR));
        Assert.assertThat(ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(landlineNumber, null)),
                          Matchers.is(CommunicationStatus.ERROR));
        Assert.assertThat(ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
                          Matchers.is(CommunicationStatus.SENT));
    }
    
    /**
     * Checks outbound SMS with invalid senderid. Make sure that the session is
     * deleted. No {@link SMSDeliveryStatus} is created. DDR with invalid
     * senderId is created.
     * 
     * @throws Exception
     */
    @Test
    public void outBoundSMSWithInvalidSenderIdCheck() throws Exception {

        new DDRRecordAgent().generateDefaultDDRTypes();
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.07, "sms charge", UnitType.PART,
                           AdapterType.SMS, "");
        String senderName = "TestUser122342345";

        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS,
                                                          TEST_ACCOUNT_ID, "0612345678", "0612345678", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();

        //create ddrType for the ddr records to be created.
        createTestDDRPrice(DDRTypeCategory.OUTGOING_COMMUNICATION_COST, 0.10, "outgoing sms", UnitType.PART,
                           AdapterType.SMS, null);

        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put(remoteAddressVoice, null);

        boolean isExceptionThrown = false;
        try {

            outBoundSMSCallXMLTest(addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT,
                                   senderName, "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner());
        }
        catch (Exception e) {
            isExceptionThrown = true;
        }
        Assert.assertThat(isExceptionThrown, Matchers.is(true));
        //fetch sessions
        List<Session> allSessions = Session.getAllSessions();
        //no sessions must be found
        Assert.assertThat(allSessions.size(), Matchers.is(0));

        //fetch the sms ddr records
        List<DDRRecord> ddrRecords = DDRRecord.getDDRRecords(TEST_ACCOUNT_ID, null, null, null, null, null, null, null,
                                                             null, null, null);
        Assert.assertThat(ddrRecords.size(), Matchers.is(1));
        DDRRecord ddrRecord = ddrRecords.iterator().next();
        Assert.assertThat(ddrRecord.getStatusForAddress(PhoneNumberUtils.formatNumber(remoteAddressVoice, null)),
                          Matchers.is(CommunicationStatus.ERROR));

        //check the SMSDeliveryStatus
        List<SMSDeliveryStatus> allSMSStatus = SMSDeliveryStatus.fetchAll();
        //no status must be found
        Assert.assertThat(allSMSStatus.size(), Matchers.is(0));
    }
    
    @Test
    public void outBoundSMSCallDeliveryNotificationTest() throws Exception {

        //send an outbound sms
        outBoundSMSCallStatusCheck();
        List<SMSDeliveryStatus> smsStatues = SMSDeliveryStatus.fetchAll();
        
        Collection<Class<?>> argumentList = new ArrayList<Class<?>>();
        argumentList.add(String.class);
        argumentList.add(String.class);
        argumentList.add(String.class);
        argumentList.add(String.class);
        argumentList.add(String.class);
        argumentList.add(String.class);
        Method handleDeliveryStatusReportMethod = fetchMethodByReflection("handleDeliveryStatusReport",
                                                                          RouteSmsServlet.class, argumentList);
        Collection<Object> parameterList = new ArrayList<Object>();
        parameterList.add(smsStatues.iterator().next().getReference());
        parameterList.add(TimeUtils.getStringFormatFromDateTime(TimeUtils.getServerCurrentTimeInMillis() - 10000,
                                                                "yyyy-mm-dd hh:mm:ss"));
        parameterList.add(TimeUtils.getStringFormatFromDateTime(TimeUtils.getServerCurrentTimeInMillis(),
                                                                "yyyy-mm-dd hh:mm:ss"));
        parameterList.add(remoteAddressVoice);
        parameterList.add(TEST_PUBLIC_KEY);
        parameterList.add("DELIVRD");
        invokeMethodByReflection(handleDeliveryStatusReportMethod, new RouteSmsServlet(), parameterList);

        //fetch the dlr again and see if it has a delivered status
        smsStatues = SMSDeliveryStatus.fetchAll();
        Assert.assertThat(smsStatues.size(), Matchers.is(1));
        Assert.assertThat(smsStatues.iterator().next().getDescription(), Matchers.is("DELIVRD"));
        Assert.assertThat(smsStatues.iterator().next().getCode(), Matchers.is("1701"));
    }

    private void
        outBoundSMSCallXMLTest(Map<String, String> addressNameMap, AdapterConfig adapterConfig, String simpleQuestion,
            QuestionInRequest questionInRequest, String senderName, String subject, String accountId) throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       questionInRequest.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", URLEncoder.encode(simpleQuestion, "UTF-8"));
        DialogAgent dialogAgent = new DialogAgent();
        if (addressNameMap.size() > 1) {
            dialogAgent.outboundCallWithMap(addressNameMap, null, null, senderName, subject, url, null,
                                            adapterConfig.getConfigId(), accountId, "", adapterConfig.getAccountType());
        }
        else {
            dialogAgent.outboundCall(addressNameMap.keySet().iterator().next(), senderName, subject, url, null,
                                     adapterConfig.getConfigId(), accountId, "", adapterConfig.getAccountType());
        }
    }

}