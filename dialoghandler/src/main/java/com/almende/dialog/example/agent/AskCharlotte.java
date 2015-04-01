package com.almende.dialog.example.agent;

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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.almende.dialog.model.AnswerPost;
import com.almende.util.jackson.JOM;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("/ask-charlotte/")
public class AskCharlotte {
	private static final String SOUNDURL = "http://ask50.ask-cs.nl/~ask/tokyo_a02/rest/";
	//private static final String SOUNDURL = "http://commondatastorage.googleapis.com/dialogserver-sounds/testSounds/espeakConv_";
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	static final ObjectMapper om =ParallelInit.getObjectMapper();
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
	public Response firstQuestion(@QueryParam("preferred_medium") String preferred_medium, @Context UriInfo ui){
		String result=null;
		String URL=ui.getBaseUri().toString();
		String url=URL;
		boolean audio = false;
		if (preferred_medium != null && preferred_medium.startsWith("audio")){
			audio = true;
			url= SOUNDURL;
		}
		
		ObjectNode node= om.createObjectNode();
		node.put("requester", URL+"id");
		node.put("question_text",url+(audio?"1810.wav":"questions/0"));
		node.put("type", "closed");
		
		ArrayNode answers = node.putArray("answers");
		for (int i = 10; i< 12; i++){
			ObjectNode answerNode = answers.addObject();
			answerNode.put("answer_text",url+(audio?"14.wav":"answers/"+i));
			answerNode.put("callback",URL+"questions/"+i+"?preferred_medium="+(audio?"audio/wav":"text/plain"));
		}
		result=node.toString();
		return Response.ok(result).build();
	}
	
	@Path("/questions/{question_no}")
	@POST
	@Produces("application/json")
	@Consumes("*/*")
	public Response answerQuestion(String answer_json, @PathParam("question_no") String question_no,@QueryParam("preferred_medium") String preferred_medium, @Context UriInfo ui){
		String result=null;
		String URL=ui.getBaseUri().toString();
		boolean audio=false;
		if (preferred_medium != null && preferred_medium.startsWith("audio")){
			audio = true;
		}
		
		String answer_input="";
		try {
			AnswerPost answer = JOM.getInstance().readValue(answer_json, AnswerPost.class);
			answer_input=answer.getAnswer_text();
			log.warning("Received responder: "+answer.getResponder()+" input: "+answer_input);
		} catch (Exception e){
			log.severe(e.toString());
		}
		
		ObjectNode node= om.createObjectNode();
		node.put("type", "referral");		
		switch (Integer.parseInt(question_no)){
		case 10:
			node.put("question_text",URL+"questions/"+question_no);
			if(audio) {
				node.put("url","tel:0647771234");
			} else {
				node.put("url","http://sven.ask-services.appspot.com/agents/knrm/");
			}
			break;
		case 11:
			node.put("question_text",URL+"questions/"+question_no);
			if(audio) {
				node.put("url","tel:0103032402");
			} else {
				node.put("url","http://sven.ask-services.appspot.com/ns_tokyo/agents/tokyo/");
			}			
			break;
		}
		result=node.toString();
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

