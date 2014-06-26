package com.almende.dialog.adapter;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.ServerUtils;

public class NotificareServeletTest extends TestFramework {

	private static final String message = "How are you doing? today";
	private static final String remoteAdressVoice2 = "31624107792";
	private static final String senderName ="ASk Fast test";
	
	@Test
	public void outBoundMessageSenderNameNotNullTest() throws Exception {

		System.out.println("test has begon");
		
		HashMap<String,String> addressNameMap = new HashMap<String,String>();
		addressNameMap.put(remoteAdressVoice2, senderName);
		
		AdapterConfig adapterConfig = createAdapterConfig("push",TEST_PUBLIC_KEY,"","");
		//set tokens beore testing
		adapterConfig.setAccessToken("");
		adapterConfig.setAccessTokenSecret("");
		adapterConfig.update();
		
		Session.getOrCreateSession(adapterConfig, "31648901147");
		
		
		String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType",
	            QuestionInRequest.SIMPLE_COMMENT.name() );
	        url = ServerUtils.getURLWithQueryParams( url, "question", message );
		
        NotificareServlet not = new NotificareServlet();
        not.sendMessage(message, "hallo", "me", "me", "me", "me", null, adapterConfig);
	        
//		outBoundPushTest( addressNameMap, adapterConfig, message, QuestionInRequest.SIMPLE_COMMENT,
//		           senderName, "outBoundBroadcastCallSenderNameNotNullTest" );
		
	
	}
	
	private void outBoundPushTest( Map<String, String> addressNameMap, AdapterConfig adapterConfig,
	        String simpleQuestion, QuestionInRequest questionInRequest, String senderName, String subject )
	    throws Exception
	    {
		
			String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType",
	            questionInRequest.name() );
	        url = ServerUtils.getURLWithQueryParams( url, "question", URLEncoder.encode( simpleQuestion, "UTF-8" ));
	        DialogAgent dialogAgent = new DialogAgent();
	        if ( addressNameMap.size() > 1 )
	        {
	            dialogAgent.outboundCallWithMap( addressNameMap, null, null, senderName, subject, url, null,
	                adapterConfig.getConfigId(), TEST_PUBLIC_KEY, "" );
	        }
	        else
	        {
	            dialogAgent.outboundCall( addressNameMap.keySet().iterator().next(), senderName, subject, url, null,
	                adapterConfig.getConfigId(), TEST_PUBLIC_KEY, "" );
	        }
	        
	    }
	
}
