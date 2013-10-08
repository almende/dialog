package com.almende.dialog.test;

import com.almende.dialog.TestFramework;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.util.ServerUtils;
import junit.framework.Assert;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class TestServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;
    public static final String TEST_SERVLET_PATH = "http://localhost:9000/unitTestServlet";
    public static final String APPOINTMENT_MAIN_QUESTION = "Are you available today?";
    public static final String APPOINTMENT_YES_ANSWER = "Yup";
    public static final String APPOINTMENT_NO_ANSWER = "Nope";
    public static final String APPOINTMENT_FREE_ANSWER = "Free";
    public static final String APPOINTMENT_SECOND_QUESION = "How long are you available? (in mins)";
    public static final String APPOINTMENT_REJECT_RESPONSE = "Thanks for responding to the invitation!";
    public static final String APPOINTMENT_ACCEPTANCE_RESPONSE = "Thanks for accepting the invitation!";
    
    @Override
    protected void doGet( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException
    {
        String result = "";
        if(req.getParameter( "appointment" ) != null)
        {
            result = getAppointmentQuestion( req.getParameter( "appointment" ) );
        }
        else
        {
            result = getJsonSimpleCommentQuestion( req.getParameter( "simpleComment" ) );
        }
        //store all the questions loaded in the TestFramework
        TestFramework.storeResponseQuestionInThread(getResponseQuestionWithOptionsInString(result));
        resp.getWriter().write( result );
        resp.setHeader( "Content-Type", MediaType.APPLICATION_JSON );
    }
    
    @Override
    protected void doPost( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException
    {
        String result = "";
        String appointmentTag = req.getParameter( "appointment" );
        result = getAppointmentQuestion( appointmentTag );
        //store all the questions loaded in the TestFramework
        TestFramework.storeResponseQuestionInThread(getResponseQuestionWithOptionsInString(result));
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
        question.setQuestion_text( "text://" + questionText );
        return question.toJSON();
    }

    public static String getJsonAppointmentQuestion()
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "closed" );
        question.setQuestion_text( "text://" + APPOINTMENT_MAIN_QUESTION );
        
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
     */
    public static String getResponseQuestionWithOptionsInString( String questionJSON )
    {
        Question question = null;
        try
        {
            question = ServerUtils.deserialize(questionJSON, Question.class);
        }
        catch (Exception e)
        {
            Assert.fail("Serialization should be OK. Exception is not expected to be thrown");
        }
        String result = question.getQuestion_expandedtext();
        if(question.getAnswers() != null && question.getType().equals("closed"))
        {
            result += "\n[ ";
            Iterator<Answer> answerIterator = question.getAnswers().iterator();
            while (answerIterator.hasNext())
            {
                result += answerIterator.next().getAnswer_expandedtext();
                if(answerIterator.hasNext())
                {
                    result += " | ";
                }
            }
            result += "  ]";
        }
        return result;
    }
    
}
