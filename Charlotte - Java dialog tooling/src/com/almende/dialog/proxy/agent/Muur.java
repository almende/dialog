package com.almende.dialog.proxy.agent;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

@Path("/muur/")
public class Muur {
	private static final String URL="http://char-a-lot.appspot.com/muur/";
	
	private String getQuestion(String question_no){
		String result = null;
		if (question_no.equals("10")){
			result= "{ question_text:\""+URL+"questions/"+question_no+"\",type:\"referral\",url:\"http://char-a-lot.appspot.com/kastje/\"}";
		} else {
			result= "{ question_text:\""+URL+"questions/"+question_no+"\",type:\"open\",answers:["+
						"{ answer_text:\"\", callback:\""+URL+"questions/10\" }]}";
		}
		return result;
	}
	
	@GET
	@Produces("application/json")
	public Response firstQuestion(@QueryParam("preferred_medium") String preferred_medium){
		return Response.ok(getQuestion("0")).build();
	}
	
	@Path("/questions/{question_no}")
	@POST
	@Produces("application/json")
	public Response answerQuestion(String answer_json, @PathParam("question_no") String question_no){
		return Response.ok(getQuestion(question_no)).build();
	}
	
	@Path("/questions/{question_no}")
	@GET
	@Produces("text/plain")
	public  Response getQuestionText(@PathParam("question_no") String question_no, @QueryParam("preferred_language") String preferred_language ){
		Integer questionNo = Integer.parseInt(question_no);
		String result = "";

		if (preferred_language != null && preferred_language.startsWith("en")){
			switch (questionNo){
				case 0: result="[post] Hi, please ask me a question!"; break;
				case 10: result="[post] Sorry, I haven't got a clue...! Maybe 'pillar' knows something about that, let me pass you on!"; break;
				default: result="[post] Eehhh??!?";
			}
		} else{
			switch (questionNo){
				case 0: result="[muur] Hoi, stel me maar een vraag!"; break;
				case 10: result="[muur] Oei, daar weet ik niets van, maar misschien 'Kastje' wel, ik stuur je door!"; break;
				default: result="[muur] Eehhh??!?";
			}
		}
		return Response.ok(result).build();
	}
	
}

