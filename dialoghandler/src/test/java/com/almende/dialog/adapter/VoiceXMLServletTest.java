package com.almende.dialog.adapter;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.junit.Test;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.VoiceXMLRESTProxy.Return;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.example.agent.TestServlet.QuestionInRequest;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.ServerUtils;

public class VoiceXMLServletTest extends TestFramework {

    protected static final String COMMENT_QUESTION_ID = "1";
    protected static final String COMMENT_QUESTION_AUDIO = "http://audio.wav";

    /**
     * this test is to check the bug which rethrows the same question when an open question doesnt
     * have an answer nor a timeout eventtype
     * @throws Exception
     */
    @Test
    public void inbountPhoneCall_WithOpenQuestion_MissingAnswerTest() throws Exception
    {
        String url = ServerUtils.getURLWithQueryParams( TestServlet.TEST_SERVLET_PATH, "questionType",
            QuestionInRequest.OPEN_QUESION_WITHOUT_ANSWERS.name() );
        url = ServerUtils.getURLWithQueryParams( url, "question", COMMENT_QUESTION_AUDIO );
        //create SMS adapter
        AdapterConfig adapterConfig = createAdapterConfig( AdapterAgent.ADAPTER_TYPE_BROADSOFT, TEST_PUBLIC_KEY, localAddressBroadsoft, url );

        //create session
        Session.getOrCreateSession( adapterConfig, remoteAddressVoice );

        //mock the Context
        UriInfo uriInfo = Mockito.mock( UriInfo.class );
        Mockito.when( uriInfo.getBaseUri() ).thenReturn( new URI( TestServlet.TEST_SERVLET_PATH ) );
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        Response newDialog = voiceXMLRESTProxy.getNewDialog( "inbound", remoteAddressVoice, localAddressBroadsoft,
            uriInfo );
        HashMap<String, String> answerVariables = assertOpenQuestionWithDTMFType( newDialog.getEntity().toString() );

        //answer the dialog
        Question retrivedQuestion = ServerUtils.deserialize( TestFramework.fetchResponse( HttpMethod.GET, url, null ),
            Question.class );
        String mediaPropertyValue = retrivedQuestion.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.RETRY_LIMIT );
        
        Integer retryCount = Question.getRetryCount( answerVariables.get( "sessionKey" ) );
        int i = 0;
        while ( i++ < 10 )
        {
            Response answerResponse = voiceXMLRESTProxy.answer(answerVariables.get("questionId"), null,
                                                               answerVariables.get("answerInput"),
                                                               answerVariables.get("sessionKey"), uriInfo);
            if ( answerResponse.getEntity() != null )
            {
                if (answerResponse.getEntity().toString().equals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                                                "<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">" +
                                                "<form><block><exit/></block></form></vxml>")) {
                    break;
                }
            }
            retryCount++;
        }
        assertTrue( retryCount != null );
        if(mediaPropertyValue != null)
        {
            assertTrue( retryCount < i );
            assertTrue( retryCount == Integer.parseInt( mediaPropertyValue ));
        }
        else
        {
            assertTrue( retryCount <= i );
            assertEquals( new Integer(Question.DEFAULT_MAX_QUESTION_LOAD), retryCount );
        }
    }
    
    private Question getCommentQuestion() {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType("comment");
        question.setQuestion_text(COMMENT_QUESTION_AUDIO);

        Answer answer = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer)));

        // set the answers in the question
        question.generateIds();
        return question;
    }

    private Question getOpenDTMFQuestion() {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType( "open" );
        question.setQuestion_text(COMMENT_QUESTION_AUDIO);

        Answer answer = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer)));

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private Question getOpenAudioQuestion() {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType( "open" );
        question.setQuestion_text(COMMENT_QUESTION_AUDIO);

        Answer answer = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer)));
        
        MediaProperty property = new MediaProperty();
        property.setMedium(MediumType.BROADSOFT);
        property.addProperty(MediaPropertyKey.TYPE, "AudIO");
        
        question.addMedia_Properties(property);

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private String renderQuestion(Question question, AdapterConfig adapter, String sessionKey) throws Exception {

        VoiceXMLRESTProxy servlet = new VoiceXMLRESTProxy();
        Return res = servlet.formQuestion(question, adapter.getConfigId(), remoteAddressVoice, null, sessionKey);

        if (question.getType().equalsIgnoreCase("comment")) {
            return servlet.renderComment(res.question, res.prompts, sessionKey);
        }
        else if (question.getType().equalsIgnoreCase("referral")) {

        }
        else if (question.getType().equalsIgnoreCase("open")) {
            return servlet.renderOpenQuestion(res.question, res.prompts, sessionKey);
        }
        else if (question.getType().equalsIgnoreCase("closed")) {

        }

        return null;
    }

    @Test
    public void renderCommentQuestionTest() throws Exception {
        
        Question question = getCommentQuestion();
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        Document doc = getXMLDocumentBuilder(result);
        Node vxml = doc.getFirstChild();
        Node form = vxml.getFirstChild();

        Node block = form.getFirstChild();
        Node prompt = block.getFirstChild();
        Node _goto = prompt.getNextSibling();

        assertNotNull(doc);
        assertEquals(doc.getChildNodes().getLength(), 1);
        assertEquals(vxml.getNodeName(), "vxml");
        assertEquals(form.getNodeName(), "form");

        assertEquals(block.getChildNodes().getLength(), 2);
        assertEquals(COMMENT_QUESTION_AUDIO, prompt.getFirstChild()
                .getAttributes().getNamedItem("src").getNodeValue());

        assertEquals("answer?questionId=" + COMMENT_QUESTION_ID + "&sessionKey=" + sessionKey,
                     java.net.URLDecoder.decode(_goto.getAttributes().getNamedItem("next").getNodeValue(), "UTF-8"));
    }

    @Test
    public void renderReferralQuestionTest() {

    }

    @Test
    public void renderClosedQuestionTest() {

    }

    @Test
    public void renderOpenQuestionWithTypeDTMFTest() throws Exception {

        Question question = getOpenDTMFQuestion();
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        createSession(sessionKey);
        String result = renderQuestion(question, adapter, sessionKey);
        assertOpenQuestionWithDTMFType( result );
    }

    @Test
    public void renderOpenQuestionWithTypeAudioTest() throws Exception {

        Question question = getOpenAudioQuestion();
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        TestServlet.logForTest( COMMENT_QUESTION_AUDIO );
        
        Document doc = getXMLDocumentBuilder(result);
        Node vxml = doc.getFirstChild();
        Node form = vxml.getFirstChild();
        
        Node record = form.getFirstChild();
        Node subdialog = record.getNextSibling();
        
        assertNotNull(doc);
        assertEquals(doc.getChildNodes().getLength(), 1);
        assertEquals(vxml.getNodeName(), "vxml");
        assertEquals(form.getNodeName(), "form");
        assertThat(form.getChildNodes().getLength(), not(4));
        assertEquals(record.getNodeName(), "record");
        assertEquals(subdialog.getNodeName(), "subdialog");
    }
    
    /**
     * @param result
     * @throws Exception
     */
    protected HashMap<String,String> assertOpenQuestionWithDTMFType( String result ) throws Exception
    {
        HashMap<String, String> variablesForAnswer = new HashMap<String, String>();

        Document doc = getXMLDocumentBuilder( result );
        Node vxml = doc.getFirstChild();
        Node answerInputNode = vxml.getChildNodes().item( 0 );
        Node questionIdNode = vxml.getChildNodes().item( 1 );
        Node sessionKeyNode = vxml.getChildNodes().item( 2 );
        Node form = vxml.getChildNodes().item( 3 );

        Node field = form.getFirstChild();

        assertNotNull( doc );
        assertEquals( doc.getChildNodes().getLength(), 1 );
        assertEquals( vxml.getNodeName(), "vxml" );
        assertEquals( "form", form.getNodeName() );
        assertEquals( "answerInput", answerInputNode.getAttributes().getNamedItem( "name" ).getNodeValue() );
        assertEquals( "questionId", questionIdNode.getAttributes().getNamedItem( "name" ).getNodeValue() );
        assertEquals( "sessionKey", sessionKeyNode.getAttributes().getNamedItem( "name" ).getNodeValue() );
        assertEquals( "property", field.getNodeName() );
        
        field = form.getChildNodes().item( 1 );
        assertEquals( "form", form.getNodeName() );
        assertEquals( "answerInput", answerInputNode.getAttributes().getNamedItem( "name" ).getNodeValue() );
        assertEquals( "questionId", questionIdNode.getAttributes().getNamedItem( "name" ).getNodeValue() );
        assertEquals( "sessionKey", sessionKeyNode.getAttributes().getNamedItem( "name" ).getNodeValue() );
        assertEquals( "field", field.getNodeName() );
        assertEquals( 4, field.getChildNodes().getLength() );

        if(answerInputNode.getAttributes().getNamedItem( "expr" ) != null)
        {
            variablesForAnswer.put( "answerInput", answerInputNode.getAttributes().getNamedItem( "expr" ).getNodeValue()
            .replace( "'", "" ) );
        }
        if(questionIdNode.getAttributes().getNamedItem( "expr" ) != null)
        {
            variablesForAnswer.put( "questionId", questionIdNode.getAttributes().getNamedItem( "expr" ).getNodeValue()
            .replace( "'", "" ) );
        }
        if(sessionKeyNode.getAttributes().getNamedItem( "expr" ) != null)
        {
            variablesForAnswer.put( "sessionKey", sessionKeyNode.getAttributes().getNamedItem( "expr" ).getNodeValue()
                .replace( "'", "" ) );
        }
        return variablesForAnswer;
    }
}
