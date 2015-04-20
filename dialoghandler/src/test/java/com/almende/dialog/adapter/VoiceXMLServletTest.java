package com.almende.dialog.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.VoiceXMLRESTProxy.Return;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.utils.PhoneNumberUtils;

public class VoiceXMLServletTest extends TestFramework {

    protected static final String COMMENT_QUESTION_ID = "1";
    protected static final String COMMENT_QUESTION_AUDIO = "http://audio.wav";

    protected static final String secondRemoteAddress = "0612345678";

    @Test
    public void renderCommentQuestionTest() throws Exception {
        
        Question question = getCommentQuestion();
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        Session session = createSession(sessionKey);
        
        String result = renderQuestion(question, adapter, sessionKey, remoteAddressVoice, session);
        
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
        assertXMLGeneratedByTwilioLibrary(String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" "
                                                                                        + "xmlns=\"http://www.w3.org/2001/vxml\"><form><block><prompt><audio src=\"%1$s\"/>"
                                                                                        + "</prompt><goto next=\"answer?questionId=1&amp;sessionKey=%2$s\"/>"
                                                                                        + "</block></form></vxml>",
                                                        COMMENT_QUESTION_AUDIO, URLEncoder.encode(sessionKey, "UTF-8")),
                                          result);
    }

    @Test
    public void renderReferralQuestionTest() throws Exception {
        Question question = getReferralQuestion( false, false, false );
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        Session session = createSession(sessionKey);
        
        String result = renderQuestion( question, adapter, sessionKey, remoteAddressVoice, session );
        System.out.println("Res: "+result);
        String expected = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">"
                                        + "<form><transfer name=\"thisCall\" dest=\"tel:%s\" bridge=\"true\" connecttimeout=\"40s\">"
                                        + "<prompt><audio src=\"http://audio.wav\"/></prompt>"
                                        + "<filled><if cond=\"thisCall=='unknown'\">"
                                        + "<goto next=\"answer?questionId=%s&amp;sessionKey=%s&amp;callStatus=completed\"/>"
                                        + "<else/><goto expr=\"'answer?questionId=%s&amp;sessionKey=%s&amp;callStatus=' + thisCall\"/>"
                                        + "</if></filled></transfer></form></vxml>",
                                        PhoneNumberUtils.formatNumber( remoteAddressVoice, null ),
                                        COMMENT_QUESTION_ID, URLEncoder.encode(session.getKey(), "UTF-8"),
                                        COMMENT_QUESTION_ID, URLEncoder.encode(session.getKey(), "UTF-8"));
        assertXMLGeneratedByTwilioLibrary( expected, result );
    }  
    
    @Test
    public void renderMultiReferralQuestionTest() throws Exception {
        Question question = getReferralQuestion( false, false, true );
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        Session session = createSession(sessionKey);
        
        String result = renderQuestion( question, adapter, sessionKey, remoteAddressVoice, session );
        System.out.println("Res: "+result);
        String expected = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">"
                                        + "<form><transfer name=\"thisCall\" dest=\"tel:%s\" bridge=\"true\" connecttimeout=\"40s\">"
                                        + "<prompt><audio src=\"http://audio.wav\"/></prompt>"
                                        + "<filled><if cond=\"thisCall=='unknown'\">"
                                        + "<goto next=\"answer?questionId=%s&amp;sessionKey=%s&amp;callStatus=completed\"/>"
                                        + "<else/><goto expr=\"'answer?questionId=%s&amp;sessionKey=%s&amp;callStatus=' + thisCall\"/>"
                                        + "</if></filled></transfer></form></vxml>",
                                        PhoneNumberUtils.formatNumber( remoteAddressVoice, null ),
                                        COMMENT_QUESTION_ID, URLEncoder.encode(session.getKey(), "UTF-8"),
                                        COMMENT_QUESTION_ID, URLEncoder.encode(session.getKey(), "UTF-8"));
        assertXMLGeneratedByTwilioLibrary( expected, result );
    } 

    @Test
    public void renderClosedQuestionTest() {

    }

    @Test
    public void renderOpenQuestionWithTypeDTMFTest() throws Exception {

        Question question = getOpenDTMFQuestion();
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        Session session = createSession(sessionKey);
        String result = renderQuestion(question, adapter, session.getKey(), remoteAddressVoice, session);
        assertOpenQuestionWithDTMFType( result );
    }

    @Test
    public void renderOpenQuestionWithTypeAudioTest() throws Exception {

        Question question = getOpenAudioQuestion();
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        Session session = createSession(sessionKey);

        String result = renderQuestion(question, adapter, sessionKey, remoteAddressVoice, session);
        TestServlet.logForTest(AdapterType.CALL.toString(), COMMENT_QUESTION_AUDIO);
        String expected = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">"
                                                                        + "<form id=\"ComposeMessage\"><record name=\"file\" beep=\"true\" maxtime=\"15s\" dtmfterm=\"true\"><prompt timeout=\"5s\">"
                                                                        + "<audio src=\"%1$s\"/></prompt><noinput><prompt><audio src=\"%1$s\"/></prompt></noinput>"
                                                                        + "<catch event=\"connection.disconnect.hangup\"><submit next=\"upload?questionId=1&"
                                                                        + "amp;sessionKey=%2$s\" namelist=\"file\" method=\"post\" enctype=\"multipart/form-data\"/>"
                                                                        + "</catch><filled><submit next=\"upload?questionId=1&amp;sessionKey=%2$s\" namelist=\"file\" "
                                                                        + "method=\"post\" enctype=\"multipart/form-data\"/></filled></record></form></vxml>",
                                        COMMENT_QUESTION_AUDIO, URLEncoder.encode(sessionKey, "UTF-8"));
        assertXMLGeneratedByTwilioLibrary(expected, result);
    }
    
    /**
     * Check if the session that is deleted during hte hangup even is not
     * restored in the hangup call
     * @throws Exception 
     */
    @Test
    public void sessionRestoreOnHangupTest() throws Exception {
        
        //create an adapter
        AdapterConfig adapter = createBroadsoftAdapter();
        //create a session
        Session session = createSession(createSessionKey(adapter, remoteAddressVoice));
        VoiceXMLRESTProxy voiceXMLRESTProxy = new VoiceXMLRESTProxy();
        voiceXMLRESTProxy.hangup(session);
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
    
    private Question getReferralQuestion( boolean useExternalCallerId, boolean usePreconnect,boolean simultaneousRing ) {

        Question question = new Question();
        question.setQuestion_id( COMMENT_QUESTION_ID );
        question.setType( "referral" );
        question.setQuestion_text( COMMENT_QUESTION_AUDIO );
        
        if(simultaneousRing) {
            question.setUrl( new ArrayList<String>(Arrays.asList( "tel:" + remoteAddressVoice, "tel:"+secondRemoteAddress)) );
            
        } else {
            question.setUrl( "tel:" + remoteAddressVoice );
        }

        Answer answer1 = new Answer( "http://answer.wav", "/next" );
        question.setAnswers( new ArrayList<Answer>( Arrays.asList( answer1 ) ) );

        MediaProperty mp = new MediaProperty();
        mp.setMedium( MediumType.BROADSOFT );

        if ( useExternalCallerId ) {
            mp.addProperty( MediaPropertyKey.USE_EXTERNAL_CALLERID, "true" );
        }

        if ( usePreconnect ) {
            mp.addProperty( MediaPropertyKey.USE_PRECONNECT, "true" );
        }

        question.addMedia_Properties( mp );

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private String renderQuestion(Question question, AdapterConfig adapter, String sessionKey, String remoteID, Session session) throws Exception {

        VoiceXMLRESTProxy servlet = new VoiceXMLRESTProxy();
        Return res = servlet.formQuestion(question, adapter.getConfigId(), remoteAddressVoice, null, sessionKey);

        if (question.getType().equalsIgnoreCase("comment")) {
            return servlet.renderComment(res.question, res.prompts, sessionKey);
        }
        else if (question.getType().equalsIgnoreCase("referral")) {
            if (question.getUrl() != null && question.getUrl().size() > 0 && question.getUrl().get(0).startsWith("tel:")) {
                return servlet.renderReferralQuestion(question, adapter, remoteID, res, session);
            }
        }
        else if (question.getType().equalsIgnoreCase("open")) {
            return servlet.renderOpenQuestion(res.question, res.prompts, sessionKey);
        }
        else if (question.getType().equalsIgnoreCase("closed")) {

        }

        return null;
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
