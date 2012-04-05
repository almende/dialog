package com.almende.dialog.proxy.agent;

import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.model.AnswerPost;

import flexjson.JSONDeserializer;

@Path("/calendar/")
public class CalendarConversation {
	private static final Logger log = Logger.getLogger(com.almende.dialog.proxy.agent.CalendarConversation.class.getName()); 	
	private static final String URL="http://char-a-lot.appspot.com/calendar/";

	private String createEvent(int duration){
		String reply="";
		//create meetingagent
		return reply;
	}
	
	private String renderEnglishAgenda(){
		String reply="";
		//get events today
		return reply;
	}	
	private String renderDutchAgenda(){
		String reply="";
		//get events today
		return reply;
	}
	
	@GET
	@Produces("application/json")
	public Response firstQuestion(@QueryParam("preferred_medium") String preferred_medium){
		String response = "{question_text:\""+URL+"questions/0\",type:\"closed\",answers:["+
						  "{answer_text:\""+URL+"answers/10\",callback:\""+URL+"questions/10\" },"+
						  "{answer_text:\""+URL+"answers/11\",callback:\""+URL+"questions/11\" }"+
						  "]}";
		return Response.ok(response).build();
	}
	
	@Path("/questions/{question_no}")
	@POST
	@Produces("application/json")
	public Response answerQuestion(String answer_json, 
			@PathParam("question_no") String question_no,
			@QueryParam("preferred_medium") String preferred_medium){
		Integer questionNo = Integer.parseInt(question_no);
		String result = "";
		switch (questionNo){
			case 0:
				return firstQuestion(preferred_medium);
			case 10: 
				result="{question_text:\""+URL+"questions/10\",type:\"open\",answers:["+
				       "{answer_text:\"\", callback:\""+URL+"questions/20\" }]}";
				break;
			case 20:
				try {
					AnswerPost answer = new JSONDeserializer<AnswerPost>().
							use(null, AnswerPost.class).
							deserialize(answer_json);
					int duration=Integer.parseInt(answer.getAnswer_text());
					createEvent(duration);	
				} catch (Exception e){
					log.severe(e.toString());
				}
				result="{question_text:\""+URL+"questions/20\",type:\"comment\",answers:[]}";
				break;
			case 11: 
				result="{question_text:\""+URL+"questions/11\",type:\"comment\",answers:[]}";
				break;
		}
		return Response.ok(result).build();
	}
	
	@Path("/questions/{question_no}")
	@GET
	@Produces("text/plain")
	public  Response getQuestionText(@PathParam("question_no") String question_no, @QueryParam("preferred_language") String preferred_language ){
		Integer questionNo = Integer.parseInt(question_no);
		String result = "";

		if (preferred_language != null && preferred_language.startsWith("en")){
			switch (questionNo){
				case 0: result="[Calendar] What do you want me to do?"; break;
				case 10: result="[Calendar] How long will the event take? (time in minutes)"; break;
				case 11: result="[Calendar] Today you have the following items in your agenda:";
						 result+=renderEnglishAgenda();						 
						 break;
				default: result="[Calendar] Eehhh??!?";
			}
		} else{
			switch (questionNo){
				case 0: result="[Kalender] Wat kan ik voor je doen?"; break;
				case 10: result="[Kalender] Hoe lang moet de afspraak duren? (tijd in minuten)"; break;
				case 11: result="[Calendar] Today you have the following items in your agenda:";
				 		result+=renderDutchAgenda();						 
				 		break;
				default: result="[Kalender] Eehhh??!?";
			}
		}
		return Response.ok(result).build();
	}
	@Path("/answers/{answer_no}")
	@GET
	@Produces("text/plain")
	public  Response getAnswerText(@PathParam("answer_no") String answer_no, @QueryParam("preferred_language") String preferred_language ){
		Integer answerNo = Integer.parseInt(answer_no);
		String result = "";

		if (preferred_language != null && preferred_language.startsWith("en")){
			switch (answerNo){
				case 10: result="PlanEvent"; break;
				case 11: result="GetEvents"; break;
				default: result="Eehhh??!?";
			}
		} else{
			switch (answerNo){
				case 10: result="MaakAfspraak"; break;
				case 11: result="HaalAfspraken"; break;
				default: result="Eehhh??!?";
			}
		}
		return Response.ok(result).build();
	}
	
}

