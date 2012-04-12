package com.almende.dialog.proxy.agent;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.TimeZone;
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

@Path("/charlotte/")
public class Charlotte {
	private static final String URL = "http://char-a-lot.appspot.com/charlotte/";
	private static final String SOUNDURL = "http://commondatastorage.googleapis.com/dialogserver-sounds/testSounds/espeakConv_";
	private static final Logger log = Logger.getLogger(com.almende.dialog.proxy.agent.Charlotte.class.getName()); 	
	
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
		boolean audio = false;
		if (preferred_medium != null && preferred_medium.startsWith("audio")){
			audio = true;
			url= SOUNDURL;
		}
		result="{question_text:\""+url+(audio?"Q0.wav":"questions/0")+"\",type:\"closed\",answers:[";
		for (int i = 10; i< 15; i++){
			result+="{answer_text:\""+url+(audio?"A"+i+".wav":"answers/"+i)+"\",callback:\""+URL+"questions/"+i+"?preferred_medium="+(audio?"audio/wav":"text/plain")+"\"}"+(i<14?",":"");
		}
		result+="]}";
		return Response.ok(result).build();
	}
	
	@Path("/questions/{question_no}")
	@POST
	@Produces("application/json")
	public Response answerQuestion(String answer_json, @PathParam("question_no") String question_no,@QueryParam("preferred_medium") String preferred_medium){
		String result=null;
		String url=URL;
		String answer_input="";
		try {
			AnswerPost answer = new JSONDeserializer<AnswerPost>().
					use(null, AnswerPost.class).
					deserialize(answer_json);
			answer_input=answer.getAnswer_text();
		} catch (Exception e){
			log.severe(e.toString());
		}
		boolean audio = false;
		if (preferred_medium != null && preferred_medium.startsWith("audio")){
			audio = true;
			url=SOUNDURL;
		}
		switch (Integer.parseInt(question_no)){
		case 10:
			result= "{ question_text:\""+URL+"questions/"+question_no+"\",type:\"referral\",url:\"http://char-a-lot.appspot.com/kastje/\"}";
			break;
		case 11:
			result= "{ question_text:\""+URL+"questions/"+question_no+"\",type:\"referral\",url:\"http://char-a-lot.appspot.com/howIsTheWeather/\"}";
			break;
		case 12:
			result= "{ question_text:\""+URL+"questions/"+question_no+"\",type:\"referral\",url:\"http://char-a-lot.appspot.com/calendar/\"}";
			break;
		case 13:
			result= "{ question_text:\""+URL+"questions/"+question_no+"\",type:\"referral\",url:\"http://char-a-lot.appspot.com/passAlong/\"}";
			break;
		case 14:
			result="{question_text:\""+url+(audio?"Q"+question_no+".wav":"questions/"+question_no)+"\",type:\"open\",answers:["+
					   "{answer_text:\""+url+(audio?"A20.wav":"answers/20")+"\",callback:\""+URL+"questions/20?preferred_medium="+(audio?"audio/wav":"text/plain")+"\"}"+
					   "]}";
			break;
		case 20:
			result= "{ question_text:\""+URL+"questions/"+question_no+"\",type:\"referral\",url:\""+answer_input+"\"}";
			break;			
		}
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
				String url=SOUNDURL+"Q"+question_no+".wav";
				//String url="http://techniek.ask-cs.nl/sounds/espeakConv_Q"+question_no+".wav";
				location = new URI(url);
			} catch (URISyntaxException e) {
				System.out.println("Didn't understand location string....");
				return Response.serverError().build();
			}
			return Response.temporaryRedirect(location).build();
		} else {
			switch (questionNo){
				case 0: result=getGreeting()+", with whom do you want to talk?\n(type '/help' for chat-cmds)"; break;
				case 10: result="Ok, passing you to 'Kastje'!";break;
				case 11: result="Ok, passing you to 'HowIsTheWeatherAgent'!";break;
				case 12: result="Ok, passing you to 'Calendar'!";break;
				case 13: result="Ok, passing you to 'PassAlong'!";break;
				case 14: result="Please enter the URL to the agent you want to talk to:";break;
				case 20: result="Ok, passing you to custom URL";break;
				default: result="Sorry, for some strange reason I don't have that question text available...";
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
				String url=SOUNDURL+"A"+answer_no+".wav";
				//String url="http://techniek.ask-cs.nl/sounds/espeakConv_A"+answer_no+".wav";
				location = new URI(url);
			} catch (URISyntaxException e) {
				System.out.println("Didn't understand location string....");
				return Response.serverError().build();
			}
			return Response.temporaryRedirect(location).build();
		} else {
			switch (answerNo){
				case 10: result="Vraagbaak"; break;
				case 11: result="HowIsTheWeather"; break;
				case 12: result="Calendar"; break;
				case 13: result="PassAlong"; break;
				case 14: result="Custom"; break;
				default: result="Sorry, for some strange reason I don't have that answer text available...";
			}
			return Response.ok(result).build();
		}
	}
	
}

