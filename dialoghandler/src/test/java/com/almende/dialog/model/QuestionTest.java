package com.almende.dialog.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Map;
import org.junit.Test;
import com.almende.dialog.TestFramework;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;

public class QuestionTest extends TestFramework {

    @Test
    public void parseEmptyQuestionJSON() {
        Question question;
        
        String json = "";
        question = Question.fromJSON(json, null, null, null);
        assertNull(question);
        
        json = "{}";
        question = Question.fromJSON(json, null, null, null);
        assertNotNull(question);
    }
    
    @Test
    public void parseOpenQuestionJSON() {
        
        Question question;
        
        String json1 = "{\"question_id\":1,\"question_text\":\"http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav\",\"type\":\"open\",\"url\":null,\"requester\":null}";
        
        question = Question.fromJSON(json1, null, null, null);
        assertNotNull(question.getQuestion_text());
        assertEquals(question.getQuestion_text(),"http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav");
        assertNull(question.getAnswers());
        assertNull(question.getEvent_callbacks());
        assertNull(question.getMedia_properties());
        
        String json2 = "{\"question_id\":1,\"question_text\":\"http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav\",\"type\":\"open\",\"url\":null,\"requester\":null,\"answers\":[{\"answer_id\":1,\"answer_text\":null,\"callback\":\"http://ask70.ask-cs.nl/~ask/askfastdemo/audio_open_question.php?function=next\"}],\"event_callbacks\":[],\"media_properties\":[{\"medium\":\"BROADsofT\",\"properties\":{\"tYPe\":\"audio\"}}]}";
        
        question = Question.fromJSON(json2, null, null, null);
        assertNotNull(question.getQuestion_text());
        assertEquals(question.getQuestion_text(), "http://ask70.ask-cs.nl/~ask/askfastdemo/audio/nl/inspreken.wav");
        assertEquals(question.getAnswers().size(),1);
        assertTrue(question.getType().equalsIgnoreCase("Open"));
        
        Map<MediaProperty.MediaPropertyKey,String> properties = question.getMediaPropertyByType(MediumType.BROADSOFT);
        assertEquals(properties.size(),1);
        
        assertTrue(properties.get(MediaPropertyKey.TYPE).equalsIgnoreCase("AuDiO"));
    }
    
    @Test
    public void parseOpenQuestionWithMinMaxDtmfInputMediaPropertiesTest()
    {
        String questionText = "{\"preferred_language\":\"en\",\"question_id\":\"1\",\"question_text\":\"text://How are you doing\","
            + "\"type\":\"open\",\"answers\":[{\"answer_id\":\"6b321a81-6fdf-4f6e-8739-001c8413c883\",\"answer_text\":\"\","
            + "\"callback\":\"http://askfastmarket1.appspot.com/resource/question?url=comment\"}],\"event_callbacks\":[],"
            + "\"media_properties\":[{\"medium\":\"BROADSOFT\",\"properties\":{\"ANSWER_INPUT_MIN_LENGTH\":\"3\","
            + "\"ANSWER_INPUT_MAX_LENGTH\":\"3\"}}]}";
        Question fromJSON = Question.fromJSON( questionText, null, null, null);
        assertEquals( "3",
            fromJSON.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.ANSWER_INPUT_MIN_LENGTH ) );
        assertEquals( "3",
            fromJSON.getMediaPropertyValue( MediumType.BROADSOFT, MediaPropertyKey.ANSWER_INPUT_MAX_LENGTH ) );

        assertEquals( "en", fromJSON.getPreferred_language() );
        assertEquals( 1, fromJSON.getMedia_properties().size() );
        assertEquals( "http://askfastmarket1.appspot.com/resource/question?url=comment", fromJSON.getAnswers()
            .iterator().next().getCallback() );
        
        assertTrue( fromJSON.toJSON().contains( "ANSWER_INPUT_MIN_LENGTH\":\"3\"" ) );
    }
}
