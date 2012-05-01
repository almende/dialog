package com.almende.dialog.proxy.agent;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.sun.jersey.spi.resource.Singleton;
@Singleton
@Path("/kastje/")
public class Kastje {
	private static final String URL="http://char-a-lot.appspot.com/kastje/";
	
	private String getQuestion(String question_no){
		String result = null;
		if (question_no.equals("10")){
			result= "{ requester:\""+URL+"id\",question_text:\""+URL+"questions/"+question_no+"\",type:\"referral\",url:\"http://char-a-lot.appspot.com/muur/\"}";
		} else {
			result= "{ requester:\""+URL+"id\",question_text:\""+URL+"questions/"+question_no+"\",type:\"open\",answers:["+
						"{ answer_text:\"\", callback:\""+URL+"questions/10\" }]}";
		}
		return result;
	}
	
	
	@GET
	@Path("/id/")
	public Response getId(@QueryParam("preferred_language") String preferred_language){
		if (preferred_language != null && preferred_language.startsWith("en")){
			return Response.ok("{ url:\""+URL+"\",nickname:\"Pillar\"}").build();			
		} else {
			return Response.ok("{ url:\""+URL+"\",nickname:\"Kastje\"}").build();
		}
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
				case 0: result="Hi, please ask me a question!"; break;
				case 10: result="Sorry, I haven't got a clue...! Maybe 'Post' knows something about that, let me pass you on!"; break;
				default: result="Eehhh??!?";
			}
		} else{
			switch (questionNo){
				case 0: result="Hoi, stel me maar een vraag!"; break;
				case 10: result="Oei, daar weet ik niets van, maar misschien 'Muur' wel, ik stuur je door!"; break;
				default: result="Eehhh??!?";
			}
		}
		return Response.ok(result).build();
	}
	
}

