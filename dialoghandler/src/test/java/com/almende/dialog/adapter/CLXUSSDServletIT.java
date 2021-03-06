package com.almende.dialog.adapter;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import com.almende.dialog.IntegrationTest;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;

@Category(IntegrationTest.class)
public class CLXUSSDServletIT extends TestFramework{
	
	private static final String message = "How are you doing? today";
	private static final String remoteAdressVoice2 = "31624107792";
	private static final String senderName ="ASk Fast test";
	
    @Test
    public void outBoundMessageSenderNameNotNullTest() throws Exception {

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAdressVoice2, senderName);

        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.USSD.toString(), AdapterProviders.CLX,
            TEST_ACCOUNT_ID, remoteAddressVoice, remoteAddressVoice, "");
        // for test ser real password and username
        adapterConfig.setAccessToken("ASKFastBV_h_ugw0");
        adapterConfig.setAccessTokenSecret("qMA3gBY5");
        adapterConfig.update();

        Session.createSession(adapterConfig, "31648901147");

        //String sessionKey = createSessionKey(adapterConfig, remoteAdressVoice2);
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", message);

        outBoundUSSDCallXMLTest(addressNameMap, adapterConfig, message, QuestionInRequest.SIMPLE_COMMENT, senderName,
                                "outBoundBroadcastCallSenderNameNotNullTest");
    }
	
    @Test
    public void simpleTest() throws Exception {

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("questionType", "comment");
        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.USSD.toString(), null, TEST_ACCOUNT_ID,
                                                          "31624107792", "", "");
        CLXUSSDServlet servlet = new CLXUSSDServlet();
        servlet.sendMessage("hallo", "hey", "me", "vincent", "31624107792", "vincent", map, adapterConfig,
                            adapterConfig.getOwner(), null);
    }
	

    @Test
    public void outboundBroadcastMessageSenderNameNotNullTest() throws Exception {

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAdressVoice2, senderName);
        addressNameMap.put(remoteAdressVoice2, senderName);

        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.USSD.toString(), AdapterProviders.CLX,
                                                          TEST_ACCOUNT_ID, "", localAddressBroadsoft, "");
        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", message);
        outBoundUSSDCallXMLTest(addressNameMap, adapterConfig, message, QuestionInRequest.SIMPLE_COMMENT, senderName,
                                "outBoundBroadcastCallSenderNameNotNullTest");
    }

    @Test
    public void outBoundMenuMessageSenderNameNotNullTest() throws Exception {

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(remoteAdressVoice2, senderName);

        AdapterConfig adapterConfig = createAdapterConfig(AdapterType.USSD.toString(), AdapterProviders.CLX,
                                                          TEST_ACCOUNT_ID, remoteAddressVoice, remoteAddressVoice, "");

        Session.createSession(adapterConfig, "31624107792");

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       QuestionInRequest.SIMPLE_COMMENT.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", message);
        outBoundUSSDCallXMLTest(addressNameMap, adapterConfig, message, QuestionInRequest.SIMPLE_COMMENT, senderName,
                                "outBoundBroadcastCallSenderNameNotNullTest");
    }	

	
    private void outBoundUSSDCallXMLTest(Map<String, String> addressNameMap, AdapterConfig adapterConfig,
        String simpleQuestion, QuestionInRequest questionInRequest, String senderName, String subject) throws Exception {

        String url = ServerUtils.getURLWithQueryParams(TestServlet.TEST_SERVLET_PATH, "questionType",
                                                       questionInRequest.name());
        url = ServerUtils.getURLWithQueryParams(url, "question", URLEncoder.encode(simpleQuestion, "UTF-8"));
        DialogAgent dialogAgent = new DialogAgent();
        if (addressNameMap.size() > 1) {
            dialogAgent.outboundCallWithMap(addressNameMap, null, null, senderName, subject, url, null,
                adapterConfig.getConfigId(), TEST_ACCOUNT_ID, "", adapterConfig.getAccountType());
        }
        else {
            dialogAgent.outboundCall(addressNameMap.keySet().iterator().next(), senderName, subject, url, null,
                adapterConfig.getConfigId(), TEST_ACCOUNT_ID, "", adapterConfig.getAccountType());
        }
    }

}
