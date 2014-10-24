package com.almende.dialog.adapter;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

import org.junit.Test;
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

public class TwilioAdapterTest extends TestFramework {
	
	Logger log = Logger.getLogger(TwilioAdapterTest.class.getName());
	
	protected static final String COMMENT_QUESTION_ID = "1";
    protected static final String COMMENT_QUESTION_AUDIO = "http://audio";
    protected static final String COMMENT_QUESTION_TEXT = "text://Hello World";
    
    @Test
    public void renderCommentQuestionTest() throws Exception {
        
        Question question = getCommentQuestion(false);
        AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node redirect = play.getNextSibling();
        
        
        assertEquals("Play", play.getNodeName());
        assertEquals("http://audio", play.getTextContent());
        assertEquals("Redirect", redirect.getNodeName());
        assertTrue(redirect.getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        
        assertEquals("GET", redirect.getAttributes().getNamedItem("method").getTextContent());
    }
    
    @Test
    public void renderCommentQuestionTTSTest() throws Exception {
        
        Question question = getCommentQuestion(true);
        AdapterConfig adapter = createBroadsoftAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
                
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node say = response.getFirstChild();
        Node redirect = say.getNextSibling();
        
        
        assertEquals("Say", say.getNodeName());
        assertEquals("Hello World", say.getTextContent());
        assertEquals("nl-NL", say.getAttributes().getNamedItem("language").getTextContent());
        assertEquals("Redirect", redirect.getNodeName());
        assertTrue(redirect.getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        
        assertEquals("GET", redirect.getAttributes().getNamedItem("method").getTextContent());
    }
	
	@Test
	public void renderOpenQuestionTest() throws Exception {
		
		Question question = getOpenQuestion(false, false);
		AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        log.info("Result Open Question: "+result);
        
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node gather = response.getFirstChild();
        Node play = gather.getFirstChild();
        Node redirect = gather.getNextSibling();
        
        assertEquals("Gather", gather.getNodeName());
        assertEquals("GET", gather.getAttributes().getNamedItem("method").getTextContent());
        assertTrue(gather.getAttributes().getNamedItem("action").getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        assertEquals("Play", play.getNodeName());
        assertEquals("http://audio", play.getTextContent());
        assertEquals("Redirect", redirect.getNodeName());
        assertTrue(redirect.getTextContent().endsWith("/dialoghandler/rest/twilio/timeout"));
        
        assertEquals("GET", redirect.getAttributes().getNamedItem("method").getTextContent());
	}
	
	@Test
	public void renderOpenAudioQuestionTest() throws Exception {
		
		Question question = getOpenQuestion(false, true);
		AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        log.info("Result Open Question: "+result);
        
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node play = response.getFirstChild();
        Node record = play.getNextSibling();
        Node redirect = record.getNextSibling();
        
        assertEquals("Play", play.getNodeName());
        assertEquals("http://audio", play.getTextContent());
        
        assertEquals("Record", record.getNodeName());
        assertEquals("GET", record.getAttributes().getNamedItem("method").getTextContent());
        assertTrue(record.getAttributes().getNamedItem("action").getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        
        assertEquals("Redirect", redirect.getNodeName());
        assertTrue(redirect.getTextContent().endsWith("/dialoghandler/rest/twilio/timeout"));
        
        assertEquals("GET", redirect.getAttributes().getNamedItem("method").getTextContent());
	}
	
	@Test
	public void renderClosedQuestionTest() throws Exception {
		
		Question question = getClosedQuestion(false);
		AdapterConfig adapter = createTwilioAdapter();
        String sessionKey = createSessionKey(adapter, remoteAddressVoice);
        
        String result = renderQuestion(question, adapter, sessionKey);
        
        log.info("Result Open Question: "+result);
        
        Document doc = getXMLDocumentBuilder(result);
        Node response = doc.getFirstChild();
        Node gather = response.getFirstChild();
        Node play1 = gather.getFirstChild();
        Node play2 = play1.getNextSibling();
        Node play3 = play2.getNextSibling();
        Node redirect = gather.getNextSibling();
        
        assertEquals("Gather", gather.getNodeName());
        assertEquals("GET", gather.getAttributes().getNamedItem("method").getTextContent());
        assertTrue(gather.getAttributes().getNamedItem("action").getTextContent().endsWith("/dialoghandler/rest/twilio/answer"));
        assertEquals("Play", play1.getNodeName());
        assertEquals("http://audio", play1.getTextContent());
        assertEquals("Play", play2.getNodeName());
        assertEquals("http://answer1.wav", play2.getTextContent());
        assertEquals("Play", play3.getNodeName());
        assertEquals("http://answer2.wav", play3.getTextContent());
        assertEquals("Redirect", redirect.getNodeName());
        assertTrue(redirect.getTextContent().endsWith("/dialoghandler/rest/twilio/timeout"));
        
        assertEquals("GET", redirect.getAttributes().getNamedItem("method").getTextContent());
	}
	
	@Test
	public void renderReferralQuestionTest() {
		
	}
    
    private Question getCommentQuestion(boolean tts) {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType("comment");
        if(tts) {
        	question.setQuestion_text(COMMENT_QUESTION_TEXT);
        } else {
        	question.setQuestion_text(COMMENT_QUESTION_AUDIO);
        }

        Answer answer = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer)));

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private Question getOpenQuestion(boolean tts, boolean audio) {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType("open");
        if(tts) {
        	question.setQuestion_text(COMMENT_QUESTION_TEXT);
        } else {
        	question.setQuestion_text(COMMENT_QUESTION_AUDIO);
        }

        Answer answer = new Answer("http://answer.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer)));
        
        if(audio) {
        	MediaProperty mp = new MediaProperty();
        	mp.setMedium(MediumType.BROADSOFT);
        	mp.addProperty(MediaPropertyKey.TYPE, "audio");
        	question.addMedia_Properties(mp);
        }

        // set the answers in the question
        question.generateIds();
        return question;
    }
    
    private Question getClosedQuestion(boolean tts) {

        Question question = new Question();
        question.setQuestion_id(COMMENT_QUESTION_ID);
        question.setType("closed");
        if(tts) {
        	question.setQuestion_text(COMMENT_QUESTION_TEXT);
        } else {
        	question.setQuestion_text(COMMENT_QUESTION_AUDIO);
        }

        Answer answer1 = new Answer("http://answer1.wav", "/next");
        Answer answer2 = new Answer("http://answer2.wav", "/next");
        question.setAnswers(new ArrayList<Answer>(Arrays.asList(answer1, answer2)));

        // set the answers in the question
        question.generateIds();
        return question;
    }
	
	private String renderQuestion(Question question, AdapterConfig adapter, String sessionKey) throws Exception {

		TwilioAdapter servlet = new TwilioAdapter();
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
        	return servlet.renderClosedQuestion(res.question, res.prompts, sessionKey);
        }

        return null;
    }

}
