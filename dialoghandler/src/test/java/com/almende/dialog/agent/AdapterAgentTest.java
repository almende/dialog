package com.almende.dialog.agent;

import org.junit.Ignore;
import org.junit.Test;

import com.almende.dialog.TestFramework;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.uuid.UUID;

public class AdapterAgentTest extends TestFramework
{
    /**
     * test if an incoming email is received by the MailServlet 
     * @throws Exception 
     */
    @Test
    @Ignore
    public void simpleReceiveEMAILTest() throws Exception
    {
        String testMessage = "testMessage";
        //create mail adapter
        String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType", QuestionInRequest.SIMPLE_COMMENT.name() );
        url = ServerUtils.getURLWithQueryParams( url, "question", testMessage );
        createEmailAdapter( "askfasttest@gmail.com", "askask2times", null, null, null, null, null, null, null,
            new UUID().toString(), url );
        //fetch and invoke the receieveMessage method
        new AdapterAgent().checkInBoundEmails();
    }
    
    @Test
    @Ignore
    public void simpleReceiveTwitterTest() throws Exception
    {
        String testMessage = "testMessage";
        //create mail adapter
        String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType", QuestionInRequest.SIMPLE_COMMENT.name() );
        url = ServerUtils.getURLWithQueryParams( url, "question", testMessage );
        createEmailAdapter( "askfasttest@gmail.com", "askask2times", null, null, null, null, null, null, null,
            new UUID().toString(), url );
        //fetch and invoke the receieveMessage method
        new AdapterAgent().checkInBoundEmails();
    }
}
