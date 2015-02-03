package com.almende.dialog.adapter;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;

@Category(IntegrationTest.class)
public class RouteSMSIT extends TestFramework {

    private static final String simpleQuestion = "How are you?";

    @Before
    public void setup()
    {
        super.setup();
        DialogAgent.DEFAULT_PROVIDERS.put(AdapterType.SMS, AdapterProviders.ROUTE_SMS);
    }
    
    @Test
    public void outBoundSMSCallSenderNameNotNullTest() throws Exception
    {
        String senderName = "TestUser";
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.SMS.getName(), TEST_PUBLIC_KEY,
                                                          "0612345678", "");
        adapterConfig.setAccessToken(TEST_PUBLIC_KEY);
        adapterConfig.setAccessTokenSecret(TEST_PRIVATE_KEY);
        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
        adapterConfig.update();
        HashMap<String, String> addressMap = new HashMap<String, String>();
        addressMap.put( remoteAddressVoice, null );
        outBoundSMSCallXMLTest( addressMap, adapterConfig, simpleQuestion, QuestionInRequest.SIMPLE_COMMENT, senderName,
            "outBoundSMSCallSenderNameNotNullTest", adapterConfig.getOwner() );
     }
    
    private void outBoundSMSCallXMLTest(Map<String, String> addressNameMap, AdapterConfig adapterConfig, String simpleQuestion,
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