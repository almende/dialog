
package com.almende.dialog.adapter;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Logger;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.TwilioAdapter.Return;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.MediaProperty;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;
import com.twilio.sdk.resource.instance.Call;


public class TwilioAdapterTest extends TestFramework{

    Logger log = Logger.getLogger( TwilioAdapterTest.class.getName() );

    protected static final String COMMENT_QUESTION_ID = "1";
    protected static final String COMMENT_QUESTION_AUDIO = "http://audio";
    protected static final String COMMENT_QUESTION_TEXT = "text://Hello World";
    protected static final String REFERRAL_PHONE_NUMBER = "tel:0643002549";
    
    protected static final String secondRemoteAddress = "0612345678";

    @Test
    public void renderCommentQuestionTest() throws Exception {

        Question question = getCommentQuestion( false );
        AdapterConfig adapter = createTwilioAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);

        Document doc = getXMLDocumentBuilder( result );
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node redirect = play.getNextSibling();

        assertEquals( "Play", play.getNodeName() );
        assertEquals( "http://audio", play.getTextContent() );
        assertEquals( "Redirect", redirect.getNodeName() );
        assertTrue( redirect.getTextContent()
            .endsWith( "/dialoghandler/rest/twilio/answer" ) );

        assertEquals( "GET", redirect.getAttributes().getNamedItem( "method" )
            .getTextContent() );
    }

    @Test
    public void renderCommentQuestionTTSTest() throws Exception {

        Question question = getCommentQuestion( true );
        AdapterConfig adapter = createBroadsoftAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion(question, adapter, session);

        Document doc = getXMLDocumentBuilder( result );
        Node response = doc.getFirstChild();
        Node say = response.getFirstChild();
        Node redirect = say.getNextSibling();

        assertEquals( "Say", say.getNodeName() );
        assertEquals( "Hello World", say.getTextContent() );
        assertEquals( "nl-nl", say.getAttributes().getNamedItem( "language" )
            .getTextContent() );
        assertEquals( "Redirect", redirect.getNodeName() );
        assertTrue( redirect.getTextContent()
            .endsWith( "/dialoghandler/rest/twilio/answer" ) );

        assertEquals( "GET", redirect.getAttributes().getNamedItem( "method" )
            .getTextContent() );
    }

    @Test
    public void renderOpenQuestionTest() throws Exception {

        Question question = getOpenQuestion( false, false );
        AdapterConfig adapter = createTwilioAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion(question, adapter, session);

        log.info( "Result Open Question: " + result );

        Document doc = getXMLDocumentBuilder( result );
        Node response = doc.getFirstChild();
        Node gather = response.getFirstChild();
        Node play = gather.getFirstChild();
        Node redirect = gather.getNextSibling();

        assertEquals( "Gather", gather.getNodeName() );
        assertEquals( "GET", gather.getAttributes().getNamedItem( "method" ).getTextContent() );
        assertTrue( gather.getAttributes().getNamedItem( "action" ).getTextContent().endsWith( "/dialoghandler/rest/twilio/answer" ) );
        assertEquals( "Play", play.getNodeName() );
        assertEquals( "http://audio", play.getTextContent() );
        assertEquals( "Redirect", redirect.getNodeName() );
        assertTrue( redirect.getTextContent().endsWith( "/dialoghandler/rest/twilio/timeout" ) );

        assertEquals( "GET", redirect.getAttributes().getNamedItem( "method" ).getTextContent() );
    }

    @Test
    public void renderOpenAudioQuestionTest() throws Exception {

        Question question = getOpenQuestion( false, true );
        AdapterConfig adapter = createTwilioAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);

        log.info( "Result Open Question: " + result );

        Document doc = getXMLDocumentBuilder( result );
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node record = play.getNextSibling();
        Node redirect = record.getNextSibling();

        assertEquals( "Play", play.getNodeName() );
        assertEquals( "http://audio", play.getTextContent() );

        assertEquals( "Record", record.getNodeName() );
        assertEquals( "GET", record.getAttributes().getNamedItem( "method" )
            .getTextContent() );
        assertTrue( record.getAttributes().getNamedItem( "action" )
            .getTextContent().endsWith( "/dialoghandler/rest/twilio/answer" ) );

        assertEquals( "Redirect", redirect.getNodeName() );
        assertTrue( redirect.getTextContent()
            .endsWith( "/dialoghandler/rest/twilio/timeout" ) );

        assertEquals( "GET", redirect.getAttributes().getNamedItem( "method" )
            .getTextContent() );
    }

    @Test
    public void renderClosedQuestionTest() throws Exception {

        Question question = getClosedQuestion( false );
        AdapterConfig adapter = createTwilioAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);

        Document doc = getXMLDocumentBuilder( result );
        Node response = doc.getFirstChild();
        Node gather = response.getFirstChild();
        Node play1 = gather.getFirstChild();
        Node play2 = play1.getNextSibling();
        Node play3 = play2.getNextSibling();
        Node redirect = gather.getNextSibling();

        assertEquals( "Gather", gather.getNodeName() );
        assertEquals( "GET", gather.getAttributes().getNamedItem( "method" )
            .getTextContent() );
        assertTrue( gather.getAttributes().getNamedItem( "action" )
            .getTextContent().endsWith( "/dialoghandler/rest/twilio/answer" ) );
        assertEquals( "Play", play1.getNodeName() );
        assertEquals( "http://audio", play1.getTextContent() );
        assertEquals( "Play", play2.getNodeName() );
        assertEquals( "http://answer1.wav", play2.getTextContent() );
        assertEquals( "Play", play3.getNodeName() );
        assertEquals( "http://answer2.wav", play3.getTextContent() );
        assertEquals( "Redirect", redirect.getNodeName() );
        assertTrue( redirect.getTextContent()
            .endsWith( "/dialoghandler/rest/twilio/timeout" ) );

        assertEquals( "GET", redirect.getAttributes().getNamedItem( "method" )
            .getTextContent() );
    }

    @Test
    public void renderReferralQuestionTest() throws Exception {

        Question question = getReferralQuestion( false, false,false );
        AdapterConfig adapter = createTwilioAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);

        log.info( "Result Referral Question: " + result );

        Document doc = getXMLDocumentBuilder( result );
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node dial = play.getNextSibling();
        Node number = dial.getFirstChild();

        assertEquals( "Play", play.getNodeName() );
        assertEquals( "http://audio", play.getTextContent() );

        assertEquals( "Dial", dial.getNodeName() );
        assertEquals( "GET", dial.getAttributes().getNamedItem( "method" )
            .getTextContent() );
        assertTrue( dial.getAttributes().getNamedItem( "action" )
            .getTextContent().endsWith( "/dialoghandler/rest/twilio/answer" ) );
        assertEquals( localAddressBroadsoft,
                      dial.getAttributes().getNamedItem( "callerId" )
                          .getTextContent() );

        String formattedAddress = PhoneNumberUtils
            .formatNumber( remoteAddressVoice, null );
        assertEquals( "Number", number.getNodeName() );
        assertEquals( formattedAddress, number.getTextContent() );
    }

    @Test
    public void renderReferralQuestionExternalCIDTest() throws Exception {

        Question question = getReferralQuestion( true, false,false );
        AdapterConfig adapter = createTwilioAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);

        log.info( "Result Referral Question: " + result );

        Document doc = getXMLDocumentBuilder( result );
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node dial = play.getNextSibling();
        Node number = dial.getFirstChild();

        assertEquals( "Play", play.getNodeName() );
        assertEquals( "http://audio", play.getTextContent() );

        assertEquals( "Dial", dial.getNodeName() );
        assertEquals( "GET", dial.getAttributes().getNamedItem( "method" )
            .getTextContent() );
        assertTrue( dial.getAttributes().getNamedItem( "action" )
            .getTextContent().endsWith( "/dialoghandler/rest/twilio/answer" ) );
        assertEquals( remoteAddressVoice,
                      dial.getAttributes().getNamedItem( "callerId" )
                          .getTextContent() );

        String formattedAddress = PhoneNumberUtils
            .formatNumber( remoteAddressVoice, null );
        assertEquals( "Number", number.getNodeName() );
        assertEquals( formattedAddress, number.getTextContent() );
    }

    @Test
    public void renderReferralPreconnectTest() throws Exception {

        Question question = getReferralQuestion( true, true, false );
        AdapterConfig adapter = createTwilioAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session );

        log.info( "Result Referral Question: " + result );

        Document doc = getXMLDocumentBuilder( result );
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node dial = play.getNextSibling();
        Node number = dial.getFirstChild();

        assertEquals( "Play", play.getNodeName() );
        assertEquals( "http://audio", play.getTextContent() );

        assertEquals( "Dial", dial.getNodeName() );
        assertEquals( "GET", dial.getAttributes().getNamedItem( "method" )
            .getTextContent() );
        assertTrue( dial.getAttributes().getNamedItem( "action" )
            .getTextContent().endsWith( "/dialoghandler/rest/twilio/answer" ) );
        assertEquals( remoteAddressVoice,
                      dial.getAttributes().getNamedItem( "callerId" )
                          .getTextContent() );

        String formattedAddress = PhoneNumberUtils
            .formatNumber( remoteAddressVoice, null );
        assertEquals( "Number", number.getNodeName() );
        assertEquals( formattedAddress, number.getTextContent() );
        assertTrue( number.getAttributes().getNamedItem( "url" )
            .getTextContent()
            .endsWith( "/dialoghandler/rest/twilio/preconnect" ) );
    }

    @Test
    public void renderMultiReferralQuestionTest() throws Exception {

        Question question = getReferralQuestion( false, false, true );
        AdapterConfig adapter = createTwilioAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);

        log.info( "Result Referral Question: " + result );

        Document doc = getXMLDocumentBuilder( result );
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node dial = play.getNextSibling();
        Node number = dial.getFirstChild();
        Node number2 = number.getNextSibling();

        assertEquals( "Play", play.getNodeName() );
        assertEquals( "http://audio", play.getTextContent() );

        assertEquals( "Dial", dial.getNodeName() );
        assertEquals( "GET", dial.getAttributes().getNamedItem( "method" )
            .getTextContent() );
        assertTrue( dial.getAttributes().getNamedItem( "action" )
            .getTextContent().endsWith( "/dialoghandler/rest/twilio/answer" ) );
        assertEquals( localAddressBroadsoft,
                      dial.getAttributes().getNamedItem( "callerId" )
                          .getTextContent() );

        String formattedAddress = PhoneNumberUtils
            .formatNumber( remoteAddressVoice, null );
        assertEquals( "Number", number.getNodeName() );
        assertEquals( formattedAddress, number.getTextContent() );
        
        String formattedAddress2 = PhoneNumberUtils.formatNumber( secondRemoteAddress, null );
        assertEquals( "Number", number2.getNodeName() );
        assertEquals( formattedAddress2, number2.getTextContent() );
    }
    
    @Test
    public void renderMultiReferralPreconnectTest() throws Exception {

        Question question = getReferralQuestion( true, true, true );
        AdapterConfig adapter = createTwilioAdapter();
        Session session = createSession(adapter, remoteAddressVoice);
        String result = renderQuestion( question, adapter, session);

        log.info( "Result Referral Question: " + result );

        Document doc = getXMLDocumentBuilder( result );
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node dial = play.getNextSibling();
        Node number = dial.getFirstChild();
        Node number2 = number.getNextSibling();

        assertEquals( "Play", play.getNodeName() );
        assertEquals( "http://audio", play.getTextContent() );

        assertEquals( "Dial", dial.getNodeName() );
        assertEquals( "GET", dial.getAttributes().getNamedItem( "method" )
            .getTextContent() );
        assertTrue( dial.getAttributes().getNamedItem( "action" )
            .getTextContent().endsWith( "/dialoghandler/rest/twilio/answer" ) );
        assertEquals( remoteAddressVoice,
                      dial.getAttributes().getNamedItem( "callerId" )
                          .getTextContent() );

        String formattedAddress = PhoneNumberUtils.formatNumber( remoteAddressVoice, null );
        assertEquals( "Number", number.getNodeName() );
        assertEquals( formattedAddress, number.getTextContent() );
        assertTrue( number.getAttributes().getNamedItem( "url" ).getTextContent().endsWith( "/dialoghandler/rest/twilio/preconnect" ) );
        
        String formattedAddress2 = PhoneNumberUtils.formatNumber( secondRemoteAddress, null );
        assertEquals( "Number", number2.getNodeName() );
        assertEquals( formattedAddress2, number2.getTextContent() );
        assertTrue( number2.getAttributes().getNamedItem( "url" ).getTextContent().endsWith( "/dialoghandler/rest/twilio/preconnect" ) );
    }

    /**
     * Tests the
     * {@link TwilioAdapter#updateSessionWithCallTimes(com.almende.dialog.model.Session, com.twilio.sdk.resource.instance.Call)}
     * method if all the session times are updated correctly when
     * {@link Call#getEndTime()} is null
     * 
     * @throws ParseException
     */
    @Test
    public void updateSessionWithCallTimesNullEndTimeTest()
        throws ParseException {

        //mock the Context
        Call call = Mockito.mock( Call.class );
        Mockito.when( call.getProperty( "date_created" ) )
            .thenReturn( "Mon, 15 Aug 2005 15:52:01 +0000" );
        Mockito.when( call.getEndTime() ).thenReturn( null );
        Mockito.when( call.getDuration() ).thenReturn( "30" );
        //Mockito.when( call.getStartTime() )
        //    .thenReturn( "Mon, 15 Aug 2005 15:52:02 +0000" );
        Session session = new Session();
        Session updateSessionWithCallTimes = TwilioAdapter
            .updateSessionWithCallTimes( session, call );
        Assert
            .assertThat( updateSessionWithCallTimes.getStartTimestamp(),
                         Matchers
                             .is( TimeUtils
                                 .getTimeWithFormat( "Mon, 15 Aug 2005 15:52:01 +0000",
                                                     "EEE, dd MMM yyyy HH:mm:ss Z",
                                                     null,
                                                     null ).getMillis() +
                                  "" ) );
        Assert
            .assertThat( updateSessionWithCallTimes.getAnswerTimestamp(),
                         Matchers
                             .is( TimeUtils
                                 .getTimeWithFormat( "Mon, 15 Aug 2005 15:52:02 +0000",
                                                     "EEE, dd MMM yyyy HH:mm:ss Z",
                                                     null,
                                                     null ).getMillis() +
                                  "" ) );
        Assert
            .assertThat( updateSessionWithCallTimes.getReleaseTimestamp(),
                         Matchers
                             .is( ( TimeUtils
                                 .getTimeWithFormat( "Mon, 15 Aug 2005 15:52:02 +0000",
                                                     "EEE, dd MMM yyyy HH:mm:ss Z",
                                                     null,
                                                     null ).getMillis() + 30000 ) +
                                  "" ) );
    }

    /**
     * Tests the
     * {@link TwilioAdapter#updateSessionWithCallTimes(com.almende.dialog.model.Session, com.twilio.sdk.resource.instance.Call)}
     * method if all the session times are updated correctly
     * 
     * @throws ParseException
     */
    @Test
    public void updateSessionWithCallTimesTest() throws ParseException {

        //mock the Context
        Call call = Mockito.mock( Call.class );
        Mockito.when( call.getProperty( "date_created" ) )
            .thenReturn( "Mon, 15 Aug 2005 15:52:01 +0000" );
        //Mockito.when( call.getEndTime() )
        //    .thenReturn( "Mon, 15 Aug 2005 15:52:32 +0000" );
        Mockito.when( call.getDuration() ).thenReturn( "31" );
        //Mockito.when( call.getStartTime() )
        //    .thenReturn( "Mon, 15 Aug 2005 15:52:02 +0000" );
        Session session = new Session();
        Session updateSessionWithCallTimes = TwilioAdapter
            .updateSessionWithCallTimes( session, call );
        Assert
            .assertThat( updateSessionWithCallTimes.getStartTimestamp(),
                         Matchers
                             .is( TimeUtils
                                 .getTimeWithFormat( "Mon, 15 Aug 2005 15:52:01 +0000",
                                                     "EEE, dd MMM yyyy HH:mm:ss Z",
                                                     null,
                                                     null ).getMillis() +
                                  "" ) );
        Assert
            .assertThat( updateSessionWithCallTimes.getAnswerTimestamp(),
                         Matchers
                             .is( TimeUtils
                                 .getTimeWithFormat( "Mon, 15 Aug 2005 15:52:02 +0000",
                                                     "EEE, dd MMM yyyy HH:mm:ss Z",
                                                     null,
                                                     null ).getMillis() +
                                  "" ) );
        Assert
            .assertThat( updateSessionWithCallTimes.getReleaseTimestamp(),
                         Matchers
                             .is( TimeUtils
                                 .getTimeWithFormat( "Mon, 15 Aug 2005 15:52:32 +0000",
                                                     "EEE, dd MMM yyyy HH:mm:ss Z",
                                                     null,
                                                     null ).getMillis() +
                                  "" ) );
    }

    private Question getCommentQuestion( boolean tts ) {

        Question question = new Question();
        question.setQuestion_id( COMMENT_QUESTION_ID );
        question.setType( "comment" );
        if ( tts ) {
            question.setQuestion_text( COMMENT_QUESTION_TEXT );
        }
        else {
            question.setQuestion_text( COMMENT_QUESTION_AUDIO );
        }

        Answer answer = new Answer( "http://answer.wav", "/next" );
        question.setAnswers( new ArrayList<Answer>( Arrays.asList( answer ) ) );

        // set the answers in the question
        question.generateIds();
        return question;
    }

    private Question getOpenQuestion( boolean tts, boolean audio ) {

        Question question = new Question();
        question.setQuestion_id( COMMENT_QUESTION_ID );
        question.setType( "open" );
        if ( tts ) {
            question.setQuestion_text( COMMENT_QUESTION_TEXT );
        }
        else {
            question.setQuestion_text( COMMENT_QUESTION_AUDIO );
        }

        Answer answer = new Answer( "http://answer.wav", "/next" );
        question.setAnswers( new ArrayList<Answer>( Arrays.asList( answer ) ) );

        if ( audio ) {
            MediaProperty mp = new MediaProperty();
            mp.setMedium( MediumType.BROADSOFT );
            mp.addProperty( MediaPropertyKey.TYPE, "audio" );
            question.addMedia_Properties( mp );
        }

        // set the answers in the question
        question.generateIds();
        return question;
    }

    private Question getClosedQuestion( boolean tts ) {

        Question question = new Question();
        question.setQuestion_id( COMMENT_QUESTION_ID );
        question.setType( "closed" );
        if ( tts ) {
            question.setQuestion_text( COMMENT_QUESTION_TEXT );
        }
        else {
            question.setQuestion_text( COMMENT_QUESTION_AUDIO );
        }

        Answer answer1 = new Answer( "http://answer1.wav", "/next" );
        Answer answer2 = new Answer( "http://answer2.wav", "/next" );
        question.setAnswers( new ArrayList<Answer>( Arrays.asList( answer1,
                                                                   answer2 ) ) );

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

    private String renderQuestion(Question question, AdapterConfig adapter, Session session) throws Exception {

        TwilioAdapter servlet = new TwilioAdapter();
        Return res = servlet.formQuestion(question, adapter.getConfigId(), remoteAddressVoice, null, session,
                                          new HashMap<String, String>());
        String sessionKey = session != null ? session.getKey() : null;
        if (question != null && !question.getType().equalsIgnoreCase("comment"))
            question = res.question;

        if (question.getType().equalsIgnoreCase("comment")) {
            return servlet.renderComment(question, res.prompts, sessionKey);
        }
        else if (question.getType().equalsIgnoreCase("referral")) {

            String remoteID = remoteAddressVoice;
            String externalCallerId = question.getMediaPropertyValue(MediumType.BROADSOFT,
                                                                     MediaPropertyKey.USE_EXTERNAL_CALLERID);
            Boolean callerId = false;
            if (externalCallerId != null) {
                callerId = Boolean.parseBoolean(externalCallerId);
            }
            if (!callerId) {
                remoteID = adapter.getMyAddress();
            }

            for (int i = 0; i < question.getUrl().size(); i++) {
                String url = question.getUrl().get(i);
                //for(String url : question.getUrl()) {
                if (url.startsWith("tel:")) {
                    // added for release0.4.2 to store the question in the
                    // session,
                    // for triggering an answered event
                    // Check with remoteID we are going to use for the call

                    log.info(String.format("current session key before referral is: %s and remoteId %s", sessionKey,
                                           remoteID));
                    String redirectedId = PhoneNumberUtils.formatNumber(url.replace("tel:", ""), null);
                    if (redirectedId != null) {

                        question.getUrl().set(i, redirectedId);
                    }
                    else {
                        log.severe(String.format("Redirect address is invalid: %s. Ignoring.. ",
                                                 url.replace("tel:", "")));
                    }
                }
            }
            return servlet.renderReferral(question, res.prompts, sessionKey, remoteID);
        }
        else if (question.getType().equalsIgnoreCase("open")) {
            return servlet.renderOpenQuestion(question, res.prompts, sessionKey);
        }
        else if (question.getType().equalsIgnoreCase("closed")) {
            return servlet.renderClosedQuestion(question, res.prompts, sessionKey);
        }

        return null;
    }
}
