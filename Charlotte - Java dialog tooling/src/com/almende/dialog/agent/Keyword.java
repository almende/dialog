package com.almende.dialog.agent;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.Settings;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("/keyword/")
public class Keyword {
	private static final String URL="http://"+Settings.HOST+"/keyword/";
	static final ObjectMapper om =ParallelInit.getObjectMapper();
	
	private String getQuestion(String question_no){
		ObjectNode node= om.createObjectNode();
		node.put("requester", URL+"id");
		node.put("type", "comment");
		node.put("question_text",URL+"questions/"+question_no);
		return node.toString();
	}
	
	
	@GET
	@Path("/id/")
	public Response getId(@QueryParam("preferred_language") String preferred_language){
		ObjectNode node= om.createObjectNode();
		node.put("url", URL);
		if (preferred_language != null && preferred_language.startsWith("en")){
			node.put("nickname", "Keyword");			
		} else {
			node.put("nickname", "Keyword");
		}
		return Response.ok(node.toString()).build();
	}

	@GET
	@Produces("application/json")
	public Response firstQuestion(@QueryParam("preferred_medium") String preferred_medium){
		return Response.ok(getQuestion("0")).build();
	}
	
	@Path("/questions/{question_no}")
	@GET
	@Produces("text/plain")
	public  Response getQuestionText(@PathParam("question_no") String question_no, @QueryParam("preferred_language") String preferred_language ){
		Integer questionNo = Integer.parseInt(question_no);
		String result = "";

		if (preferred_language != null && preferred_language.startsWith("en")){
			switch (questionNo){
				case 0: result="You need to give the right keyword on this number."; break;
			}
		} else{
			switch (questionNo){
				case 0: result="U dient het juiste keyword mee te geven op dit nummer."; break;
			}
		}
		return Response.ok(result).build();
	}
	
}

