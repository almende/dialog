package com.almende.dialog.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;

import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;

public class TestServlet extends HttpServlet
{
    private static final long serialVersionUID = 1L;
    public static final String TEXT_SERVLET_PATH = "http://localhost:9000/unitTestServlet";

    @Override
    protected void doGet( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException
    {
        String result = "";
        String appointmentTag = req.getParameter( "appointment" );
        result = getAppointmentQuestion( appointmentTag );
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
        resp.getWriter().write( result );
        resp.setHeader( "Content-Type", MediaType.APPLICATION_JSON );
    }

    @Override
    protected void doPut( HttpServletRequest req, HttpServletResponse resp )
    throws ServletException, IOException
    {
        super.doPut( req, resp );
    }

    public static String getJsonAppointmentQuestion()
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "closed" );
        question.setQuestion_text( "text://Are you available today?" );
        
        Answer yesAnswer = new Answer( "text://Yup", "http://localhost:9000/unitTestServlet?appointment=Yes" );
        Answer noAnswer = new Answer( "text://Nope", "http://localhost:9000/unitTestServlet?appointment=Nope" );
        
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
        question.setQuestion_text( "text://How long are you available? (in mins)" );
        
        Answer openAnswer = new Answer( "text://", "http://localhost:9000/unitTestServlet?appointment=free" );
        
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
        question.setQuestion_text( "text://Thanks for responding to the invitation!" );
        question.generateIds();
        return question.toJSON();
    }
    
    public static String getJsonAppointmentFreeQuestion()
    {
        Question question = new Question();
        question.setQuestion_id( "1" );
        question.setType( "comment" );
        question.setQuestion_text( "text://Thanks for accepting the invitation!" );
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
        else if ( appointmentTag.equals( "Yes" ) )
        {
            result = getJsonAppointmentYesQuestion();
        }
        else if ( appointmentTag.equals( "Nope" ) )
        {
            result = getJsonAppointmentNoQuestion();
        }
        else if ( appointmentTag.equals( "free" ) )
        {
            result = getJsonAppointmentFreeQuestion();
        }
        else
        {
            result = getJsonAppointmentQuestion();
        }
        return result;
    }
    
    
}
