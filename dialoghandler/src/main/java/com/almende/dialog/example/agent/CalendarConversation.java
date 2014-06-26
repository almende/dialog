package com.almende.dialog.example.agent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.Settings;
import com.almende.dialog.agent.tools.Event;
import com.almende.dialog.agent.tools.Result;
import com.almende.dialog.model.AnswerPost;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;

@Path("/calendar/")
public class CalendarConversation {
	private static final Logger log = Logger.getLogger(com.almende.dialog.example.agent.CalendarConversation.class.getName()); 	
	private static final String URL="http://"+Settings.HOST+"/calendar/";
	//private static final String USERAGENT = "https://agentplatform.appspot.com/agents/UserAgent/12d3c692-2138-4b4d-bcb2-f29058f21819";
	private static final String CALENDARAGENT = "https://eveagents.appspot.com/agents/GoogleCalendarAgent/647fe772-918d-44a8-a199-657a6a8f07c6";
	static final ObjectMapper om =ParallelInit.getObjectMapper();
	
	private ArrayList<Event> getEventsToday(){
		Client client = ParallelInit.getClient();
		WebResource wr = client.resource(CALENDARAGENT);
		try {
			String s = wr.type("application/json").post(String.class,"{\"id\":1, \"method\":\"getEventsToday\", \"params\":{}}" );
			Result result = om.readValue(s,Result.class); 
			if (result != null){
				return result.getResult();
			}
		} catch (Exception e){
			log.severe(e.toString());
		}
		return null;
	}
	
	private String createEvent(int duration){
		String reply="";
		//create meetingagent
		return reply;
	}
	
	private String renderAgenda(){
		String reply="\n";
		ArrayList<Event> list = getEventsToday();
		if (list == null) return reply;
		
		for (Event event : list){
			Date starttime = new Date(event.getWhen().get(0).getStart());
			String formatPattern = "HH:mm";
			SimpleDateFormat sdf = new SimpleDateFormat(formatPattern);
			sdf.setTimeZone(TimeZone.getTimeZone("GMT+2"));
			reply+=sdf.format(starttime);
			reply+=" -> '"+event.getTitle()+"'";
			reply+=";\n";
		}
		return reply;
	}	
	
	@GET
	@Path("/id/")
	public Response getId(@QueryParam("preferred_language") String preferred_language){
		if (preferred_language != null && preferred_language.startsWith("en")){
			return Response.ok("{ url:\""+URL+"\",nickname:\"Calendar\"}").build();			
		} else {
			return Response.ok("{ url:\""+URL+"\",nickname:\"Kalender\"}").build();
		}
	}
	
	@GET
	@Produces("application/json")
	public Response firstQuestion(@QueryParam("preferred_medium") String preferred_medium){
		String response = "{requester:\""+URL+"id/\",question_text:\""+URL+"questions/0\",type:\"closed\",answers:["+
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
				result="{requester:\""+URL+"id/\",question_text:\""+URL+"questions/10\",type:\"open\",answers:["+
				       "{answer_text:\"\", callback:\""+URL+"questions/20\" }]}";
				break;
			case 20:
				try {
					AnswerPost answer = om.readValue(answer_json,AnswerPost.class); 
					int duration=Integer.parseInt(answer.getAnswer_text());
					createEvent(duration);	
				} catch (Exception e){
					log.severe(e.toString());
				}
				result="{requester:\""+URL+"id/\",question_text:\""+URL+"questions/20\",type:\"comment\",answers:[]}";
				break;
			case 11: 
				result="{requester:\""+URL+"id/\",question_text:\""+URL+"questions/11\",type:\"comment\",answers:[]}";
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
				case 0: result="What do you want me to do?"; break;
				case 10: result="How long will the event take? (time in minutes)"; break;
				case 11: result="Today you have the following items in your agenda:";
						 result+=renderAgenda();						 
						 break;
				default: result="Eehhh??!?";
			}
		} else{
			switch (questionNo){
				case 0: result="Wat kan ik voor je doen?"; break;
				case 10: result="Hoe lang moet de afspraak duren? (tijd in minuten)"; break;
				case 11: result="Voor vandaag heb je de volgende afspraken in je agenda:";
				 result+=renderAgenda();						 
				 		break;
				default: result="Eehhh??!?";
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

