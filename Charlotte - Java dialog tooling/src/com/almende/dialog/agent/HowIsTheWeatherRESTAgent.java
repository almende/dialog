package com.almende.dialog.agent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.Settings;
import com.almende.dialog.model.AnswerPost;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("/howIsTheWeather/")
public class HowIsTheWeatherRESTAgent {
	private static final String URL = "http://"+Settings.HOST+"/howIsTheWeather/";
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	static final ObjectMapper om =ParallelInit.getObjectMapper();
	
	@GET
	@Path("/id/")
	public Response getId(){
		return Response.ok("{ url:\""+URL+"\",nickname:\"HowIsTheWeather\"}").build();
	}
	
	@GET
	@Produces("application/json")
	public Response firstQuestion(@QueryParam("preferred_medium") String preferred_medium){
		String result=null;
		String url=URL;
		boolean audio = false;
		if (preferred_medium != null && preferred_medium.startsWith("audio")){
			audio = true;
			url="http://commondatastorage.googleapis.com/dialogserver-sounds/testSounds/espeakConv_";
		}
		result="{requester:\""+URL+"id/\",question_text:\""+url+(audio?"Q0.wav":"questions/0")+"\",type:\"closed\",answers:[";
		for (int i = 10; i< 15; i++){
			result+="{answer_text:\""+url+(audio?"A"+i+".wav":"answers/"+i)+"\",callback:\""+URL+"questions/"+i+"?preferred_medium="+(audio?"audio/wav":"text/plain")+"\"}"+(i<14?",":"");
		}
		result+="]}";
		result=parseQuestion(result);
		return Response.ok(result).build();
	}
	
	@Path("/questions/{question_no}")
	@POST
	@Produces("application/json")
	public Response answerQuestion(String answer_json, @PathParam("question_no") String question_no,@QueryParam("preferred_medium") String preferred_medium){
		String result=null;
		try {
			AnswerPost answer = om.readValue(answer_json,AnswerPost.class);
			log.warning("Received responder: "+answer.getResponder());
		} catch (Exception e){
			log.severe(e.toString());
		}
		
		String url=URL;
		boolean audio = false;
		if (preferred_medium != null && preferred_medium.startsWith("audio")){
			audio = true;
			url="http://commondatastorage.googleapis.com/dialogserver-sounds/testSounds/espeakConv_";
		}
		if (Integer.parseInt(question_no) >= 20) {
			result="{requester:\""+URL+"id/\",question_text:\""+url+(audio?"Q"+question_no+".wav":"questions/"+question_no)+"\",type:\"comment\",answers:[],url:\"tel:0107421230\"}";
		} else {
			result="{requester:\""+URL+"id/\",question_text:\""+url+(audio?"Q"+question_no+".wav":"questions/"+question_no)+"\",type:\"closed\",answers:["+
				   "{answer_text:\""+url+(audio?"A20.wav":"answers/20")+"\",callback:\""+URL+"questions/20?preferred_medium="+(audio?"audio/wav":"text/plain")+"\"},"+
				   "{answer_text:\""+url+(audio?"A21.wav":"answers/21")+"\",callback:\""+URL+"questions/21?preferred_medium="+(audio?"audio/wav":"text/plain")+"\"}"+
				   "]}";
		}
		result=parseQuestion(result);
		return Response.ok(result).build();
	}
	
	@Path("/questions/{question_no}")
	@GET
	@Produces("text/plain")
	public  Response getQuestionText(@PathParam("question_no") String question_no, @QueryParam("preferred_medium") String prefered_mimeType ){
		Integer questionNo = Integer.parseInt(question_no);
		String result = "";
		
		if (prefered_mimeType != null && prefered_mimeType.startsWith("audio")){
			URI location = null;
			try {
				String url="http://commondatastorage.googleapis.com/dialogserver-sounds/testSounds/espeakConv_Q"+question_no+".wav";
				//String url="http://techniek.ask-cs.nl/sounds/espeakConv_Q"+question_no+".wav";
				location = new URI(url);
			} catch (URISyntaxException e) {
				System.out.println("Didn't understand location string....");
				return Response.serverError().build();
			}
			return Response.temporaryRedirect(location).build();
		} else {
			switch (questionNo){
				case 0: result="Hi, how is the weather today?"; break;
				case 10: result="Great to hear! Are you going out, to enjoy it?"; break;
				case 11: result="Nice, do you get a chance be outside today?"; break;
				case 12: result="Ok, still going out today?"; break;
				case 13: result="Mmmh, hope you don't need to go out today?"; break;
				case 14: result="That's not good! And do you really need to go outside?"; break;
				case 20: result="Good luck then!"; break;
				case 21: result="Just your luck..."; break;
				default: result="What was that?";
			}
			return Response.ok(result).build();
		}
	}
	
	@Path("/answers/{answer_no}")
	@GET
	@Produces("text/plain")
	public Response getAnswerText(@PathParam("answer_no") String answer_no, @QueryParam("preferred_medium") String prefered_mimeType){
		Integer answerNo = Integer.parseInt(answer_no);
		String result = "";
		if (prefered_mimeType != null && prefered_mimeType.startsWith("audio")){
			URI location = null;
			try {
				String url="http://commondatastorage.googleapis.com/dialogserver-sounds/testSounds/espeakConv_A"+answer_no+".wav";
				//String url="http://techniek.ask-cs.nl/sounds/espeakConv_A"+answer_no+".wav";
				location = new URI(url);
			} catch (URISyntaxException e) {
				System.out.println("Didn't understand location string....");
				return Response.serverError().build();
			}
			return Response.temporaryRedirect(location).build();
		} else {
			switch (answerNo){
				case 10: result="Great"; break;
				case 11: result="Nice"; break;
				case 12: result="Ok"; break;
				case 13: result="Not too good"; break;
				case 14: result="Really bad"; break;
				case 20: result="Yes"; break;
				case 21: result="No"; break;
				default: result="What was that?";
			}
			return Response.ok(result).build();
		}
	}
	
	private String parseQuestion(String json) {
		ObjectNode resultObj=null;
		try {
			resultObj =om.readValue(json, ObjectNode.class);
			json = resultObj.toString();
		} catch(Exception e){
		}
		return json;
	}
}

