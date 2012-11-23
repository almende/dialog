package com.almende.dialog.agent;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.Settings;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.AnswerPost;
import com.almende.dialog.model.Question;

import flexjson.JSONDeserializer;
@Path("/ask-charlotte/")
public class AskCharlotte {
	private static final String URL = "http://"+Settings.HOST+"/ask-charlotte/";
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	//TODO: Charlotte should have an address book of possible agents, managed through the question interface.
	
	private String getGreeting(){
		//TODO: should be based on Contactlist entry for TimeZone info.
		String result="Good ";
		
		Calendar date = Calendar.getInstance(TimeZone.getTimeZone("GMT+2"));
		int hour = date.get(Calendar.HOUR_OF_DAY);
		if (hour < 5 || hour >= 23) result+="night";
		if (hour >= 5 && hour<12) result+="morning";
		if (hour >= 12 && hour<18) result+="afternoon";
		if (hour >= 18 && hour < 23) result+="evening";
		return result;
	}
	
	@GET
	@Produces("application/json")
	public Response firstQuestion(@QueryParam("preferred_medium") String preferred_medium){
		String result=null;
		String url=URL;
		
		Question question = new Question();
		question.setQuestion_text(url+"questions/0");
		question.setType("closed");
		
		ArrayList<Answer> answers = new ArrayList<Answer>();
		for (int i = 10; i< 12; i++){
			answers.add(new Answer(url+"answers/"+i, url+"questions/"+i+"?preferred_medium=text/plain"));
		}
		question.setAnswers(answers);
		result = question.toJSON();
		
		return Response.ok(result).build();
	}
	
	@Path("/questions/{question_no}")
	@POST
	@Produces("application/json")
	@Consumes("*/*")
	public Response answerQuestion(String answer_json, @PathParam("question_no") String question_no,@QueryParam("preferred_medium") String preferred_medium){
		String result=null;
		
		@SuppressWarnings("unused")
		String answer_input="";
		try {
			AnswerPost answer = new JSONDeserializer<AnswerPost>().
					use(null, AnswerPost.class).
					deserialize(answer_json);
			answer_input=answer.getAnswer_text();
			log.warning("Received responder: "+answer.getResponder());
		} catch (Exception e){
			log.severe(e.toString());
		}
		Question question = new Question();
		question.setType("referral");
		
		switch (Integer.parseInt(question_no)){
		case 10:
			question.setQuestion_text(URL+"questions/"+question_no);
			question.setUrl("http://sven.ask-services.appspot.com/agents/knrm/");
			break;
		case 11:
			question.setQuestion_text(URL+"questions/"+question_no);
			question.setUrl("http://sven.ask-services.appspot.com/ns_tokyo/agents/tokyo/");
			break;
		}
		result=question.toJSON();
		return Response.ok(result).build();
	}
	
	@Path("/questions/{question_no}")
	@GET
	@Produces("text/plain")
	@Consumes("*/*")
	public  Response getQuestionText(@PathParam("question_no") String question_no, @QueryParam("preferred_medium") String prefered_mimeType ){
		Integer questionNo = Integer.parseInt(question_no);
		String result = "";
		
		switch (questionNo){
			case 0: result=getGreeting()+", with whom do you want to talk?\n(type '/help' for chat-cmds)"; break;
			case 10: result="Ok, passing you to 'KNRM'!";break;
			case 11: result="Ok, passing you to 'Tokyo'!";break;
			default: result="Sorry, for some strange reason I don't have that question text available...";
		}
		return Response.ok(result).build();
	}
	
	@Path("/answers/{answer_no}")
	@GET
	@Produces("text/plain")
	@Consumes("*/*")
	public Response getAnswerText(@PathParam("answer_no") String answer_no, @QueryParam("preferred_medium") String prefered_mimeType){
		Integer answerNo = Integer.parseInt(answer_no);
		String result = "";
		
		switch (answerNo){
			case 10: result="KNRM"; break;
			case 11: result="Tokyo"; break;
			default: result="Sorry, for some strange reason I don't have that answer text available...";
		}
		return Response.ok(result).build();		
	}
	
}

