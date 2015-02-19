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
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;

@Category(IntegrationTest.class)
public class RouteSMSIT extends TestFramework {

    private static final String simpleQuestion = "How are you?";

    @Before
    public void setup() {

        super.setup();
        new DialogAgent().setDefaultProviderSettings(AdapterType.SMS, AdapterProviders.ROUTE_SMS);
    }

    @Test
    public void outBoundSMSCallStatusCheck() throws Exception {

        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.toString(), AdapterProviders.ROUTE_SMS,
                                                          TEST_PUBLIC_KEY, "0612345678", "");
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
                                            adapterConfig.getConfigId(), accountId, "");
        }
        else {
            dialogAgent.outboundCall(addressNameMap.keySet().iterator().next(), senderName, subject, url, null,
                                     adapterConfig.getConfigId(), accountId, "");
        }
    }

}