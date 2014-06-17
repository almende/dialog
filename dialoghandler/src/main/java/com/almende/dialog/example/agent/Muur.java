package com.almende.dialog.example.agent;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.Settings;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("/muur/")
public class Muur {
	private static final String URL="http://"+Settings.HOST+"/muur/";
	static final ObjectMapper om =ParallelInit.getObjectMapper();
	
	private String getQuestion(String question_no){
		ObjectNode node= om.createObjectNode();
		node.put("requester", URL+"id");
		node.put("question_text",URL+"questions/"+question_no);
		if (question_no.equals("10")){
			node.put("type", "referral");
			node.put("url", "http://"+Settings.HOST+"/kastje/");
		} else {
			node.put("type", "open");
			ArrayNode answers = node.putArray("answers");
			
			ObjectNode answerNode = answers.addObject();
			answerNode.put("answer_text", "");
			answerNode.put("callback", URL+"questions/10");
		}
		return node.toString();
	}
	
	
	@GET
	@Path("/id/")
	public Response getId(@QueryParam("preferred_language") String preferred_language){
		ObjectNode node= om.createObjectNode();
		node.put("url", URL);
		if (preferred_language != null && preferred_language.startsWith("en")){
			node.put("nickname", "Post");			
		} else {
			node.put("nickname", "Muur");
		}
		return Response.ok(node.toString()).build();
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
				case 10: result="Sorry, I haven't got a clue...! Maybe 'pillar' knows something about that, let me pass you on!"; break;
				default: result="Eehhh??!?";
			}
		} else{
			switch (questionNo){
				case 0: result="Hoi, stel me maar een vraag!"; break;
				case 10: result="Oei, daar weet ik niets van, maar misschien 'Kastje' wel, ik stuur je door!"; break;
				default: result="Eehhh??!?";
			}
		}
		return Response.ok(result).build();
	}
	
}

