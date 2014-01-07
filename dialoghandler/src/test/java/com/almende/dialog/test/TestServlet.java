package com.almende.dialog.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;

import org.junit.Assert;

import com.almende.dialog.TestFramework;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.util.ServerUtils;

public class TestServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;
    public static final String TEST_SERVLET_PATH = "http://localhost:9000/unitTestServlet";
    public static final String APPOINTMENT_MAIN_QUESTION = "Are you available today?";
    public static final String OPEN_QUESTION_URL_WITH_SPACES = "/URL WITH SPACES";
    public static final String PLAIN_TEXT_QUESTION = "/PLAIN TEXT";
    public static final String APPOINTMENT_YES_ANSWER = "Yup";
    public static final String APPOINTMENT_NO_ANSWER = "Nope";
    public static final String APPOINTMENT_FREE_ANSWER = "Free";
    public static final String APPOINTMENT_SECOND_QUESION = "How long are you available? (in mins)";
    public static final String APPOINTMENT_REJECT_RESPONSE = "Thanks for responding to the invitation!";
    public static final String APPOINTMENT_ACCEPTANCE_RESPONSE = "Thanks for accepting the invitation!";
    
    /**
     * simple enum to generate different questions formats
     * @author Shravan
     */
    public enum QuestionInRequest
    {
        APPOINTMENT, SIMPLE_COMMENT, OPEN_QUESTION, OPEN_QUESION_WITHOUT_ANSWERS, URL_QUESTION_TEXT, PLAIN_TEXT_QUESION;
    }
    
    @Override
    protected void doGet( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException
    {
        String result = "";
        String questionType = req.getParameter( "questionType" );
        if ( questionType != null )
        {
            switch ( QuestionInRequest.valueOf(questionType) )
            {
                case APPOINTMENT:
                    result = getAppointmentQuestion( req.getParameter( "question" ) );
                    break;
                case SIMPLE_COMMENT:
                    result = getJsonSimpleCommentQuestion( req.getParameter( "question" ) );
                    break;
                case OPEN_QUESTION:
                    result = getJsonSimpleOpenQuestion( req.getParameter( "question" ) );
                    break;
                case PLAIN_TEXT_QUESION:
                    result = req.getParameter( "question" );
                    break;
                case OPEN_QUESION_WITHOUT_ANSWERS:
                    result = getJsonSimpleOpenQuestionWithoutAnswers( req.getParameter( "question" ) );
                default:
                    break;
            }
        }
        //store all the questions loaded in the TestFramework
        if(result != null && !result.isEmpty())
        {
            try
            {
                TestFramework.storeResponseQuestionInThread(getResponseQuestionWithOptionsInString(result));
            }
            catch ( Exception e )
            {
                Assert.fail( "Exception is not expected to be thrown. "+ e.getLocalizedMessage() );
            }
        }
        if ( result == null || result.isEmpty()
            && req.getPathInfo().startsWith( OPEN_QUESTION_URL_WITH_SPACES ) )
        {
            String message = req.getPathInfo().substring( OPEN_QUESTION_URL_WITH_SPACES.length() + 1 );
            result = getJsonSimpleOpenQuestion( TEST_SERVLET_PATH + PLAIN_TEXT_QUESTION + "/" + message);
        }
        else if ( result == null || result.isEmpty() && req.getPathInfo().startsWith( PLAIN_TEXT_QUESTION ) )
        {
            result = req.getPathInfo().substring( PLAIN_TEXT_QUESTION.length() + 1 );
        }
        resp.getWriter().write( result );
        resp.setHeader( "Content-Type", MediaType.APPLICATION_JSON );
    }
    
    @Override
    protected void doPost( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException
    {
        String result = "";
        String appointmentTag = req.getParameter( "appointment" );
        if(appointmentTag != null)
        {
            result = getAppointmentQuestion( appointmentTag );
            //store all the questions loaded in the TestFramework
            try
            {
                TestFramework.storeResponseQuestionInThread(getResponseQuestionWithOptionsInString(result));
            }
            catch ( Exception e )
            {
                Assert.fail( "Exception is not expected to be thrown. "+ e.getLocalizedMessage() );
            }
        }
        else if(req.getParameter( "questionType") != null && req.getParameter( "questionType").equals( QuestionInRequest.SIMPLE_COMMENT.name() ))
        {
            result = getJsonSimpleCommentQuestion( req.getParameter( "question" ) );
        }
        else
        {
            StringBuffer jb = new StringBuffer();
            String line = null;
            try
            {
                BufferedReader reader = req.getReader();
                while ( ( line = reader.readLine() ) != null )
                {
                    jb.append( line );
                }
                result = jb.toString();
                TestFramework.log( result );
            }
            catch ( Exception e )
            {
                Assert.fail( "POST payload retrieval failed. Message: " + e.getLocalizedMessage() );
                return;
            }
        }
        resp.getWriter().write( result );
        resp.setHeader( "Content-Type", MediaType.APPLICATION_JSON );
    }

    @Override
    protected void doPut( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException
    {
        super.doPut( req, resp );
    }
    
    public static String getJsonSimpleCommentQuestion(String questionText)
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "comment" );
        try
        {
            question.setQuestion_text( "text://" + URLDecoder.decode( questionText, "UTF-8" ));
        }
        catch ( UnsupportedEncodingException e )
        {
            Assert.fail( e.getLocalizedMessage() );
        }
        return question.toJSON();
    }
    
    private String getJsonSimpleOpenQuestionWithoutAnswers( String questionText )
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "open" );
        if(questionText.startsWith( "http://" ))
        {
            question.setQuestion_text( questionText );
        }
        else
        {
            question.setQuestion_text( "text://" + questionText );
        }
        question.generateIds();
        try
        {
            return ServerUtils.serialize( question );
        }
        catch ( Exception e )
        {
            Assert.fail("exception not expected. "+ e.getLocalizedMessage());
            return null;
        }
    }
    
    private String getJsonSimpleOpenQuestion( String questionText ) throws UnsupportedEncodingException
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "open" );
        if(questionText.startsWith( "http://" ))
        {
            question.setQuestion_text( questionText );
        }
        else
        {
            question.setQuestion_text( "text://" + questionText );
        }
        String callback = ServerUtils.getURLWithQueryParams( TEST_SERVLET_PATH , "questionType", QuestionInRequest.SIMPLE_COMMENT.name() );
        callback = ServerUtils.getURLWithQueryParams( callback, "question", "Simple%20Comment" );
        question.setAnswers( new ArrayList<Answer>( Arrays.asList( new Answer( "Test answer", callback ))));
        question.generateIds();
        try
        {
            return ServerUtils.serialize( question );
        }
        catch ( Exception e )
        {
            Assert.fail("exception not expected. "+ e.getLocalizedMessage());
            return null;
        }
    }
    
    public static String getJsonAppointmentQuestion()
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "closed" );
        question.setQuestion_text( "text://" + APPOINTMENT_MAIN_QUESTION );
        question.addEventCallback( null, "delivered", TEST_SERVLET_PATH );
        Answer yesAnswer = new Answer( "text://" + APPOINTMENT_YES_ANSWER, TEST_SERVLET_PATH + "?appointment=" + APPOINTMENT_YES_ANSWER );
        Answer noAnswer = new Answer( "text://" + APPOINTMENT_NO_ANSWER, TEST_SERVLET_PATH + "?appointment=" + APPOINTMENT_NO_ANSWER );
        
        //set the answers in the question
        question.setAnswers( new ArrayList<Answer>( Arrays.asList( yesAnswer, noAnswer ) ));
        question.generateIds();
        return question.toJSON();
    }
    
    public static String getJsonAppointmentYesQuestion()
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "open" );
        question.setQuestion_text( "text://"+ APPOINTMENT_SECOND_QUESION );
        
        Answer openAnswer = new Answer( "text://", TEST_SERVLET_PATH + "?appointment="+ APPOINTMENT_FREE_ANSWER );
        
        //set the answers in the question
        question.setAnswers( new ArrayList<Answer>(Arrays.asList( openAnswer )));
        question.generateIds();
        return question.toJSON();
    }
    
    public static String getJsonAppointmentNoQuestion()
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "comment" );
        question.setQuestion_text( "text://" + APPOINTMENT_REJECT_RESPONSE );
        question.generateIds();
        return question.toJSON();
    }
    
    public static String getJsonAppointmentFreeQuestion()
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "comment" );
        question.setQuestion_text( "text://"+ APPOINTMENT_ACCEPTANCE_RESPONSE );
        question.generateIds();
        return question.toJSON();
    }
    
    /**
     * @param appointmentTag
     * @return
     */
    private String getAppointmentQuestion( String appointmentTag )
    {
        String result;
        if ( appointmentTag.equals( "start" ) )
        {
            result = getJsonAppointmentQuestion();
        }
        else if ( appointmentTag.equals( APPOINTMENT_YES_ANSWER ) )
        {
            result = getJsonAppointmentYesQuestion();
        }
        else if ( appointmentTag.equals( APPOINTMENT_NO_ANSWER ) )
        {
            result = getJsonAppointmentNoQuestion();
        }
        else if ( appointmentTag.equals( APPOINTMENT_FREE_ANSWER ) )
        {
            result = getJsonAppointmentFreeQuestion();
        }
        else
        {
            result = getJsonAppointmentQuestion();
        }
        return result;
    }

    /**
     * returns a String format of a question. used for testing.
     * E.g. Are you available today?
            [ Yup | Nope  ]
     * @param questionJSON
     * @return
     * @throws Exception 
     */
    public static String getResponseQuestionWithOptionsInString( String questionJSON ) throws Exception
    {
        Question question = ServerUtils.deserialize( questionJSON, false, Question.class );
        if ( question != null )
        {
            String result = question.getQuestion_expandedtext();
            if ( question.getAnswers() != null && question.getType().equals( "closed" ) )
            {
                result += "\n[ ";
                Iterator<Answer> answerIterator = question.getAnswers().iterator();
                while ( answerIterator.hasNext() )
                {
                    result += answerIterator.next().getAnswer_expandedtext();
                    if ( answerIterator.hasNext() )
                    {
                        result += " | ";
                    }
                }
                result += "  ]";
            }
            return result;
        }
        else
        {
            return questionJSON;
        }
    }
}
