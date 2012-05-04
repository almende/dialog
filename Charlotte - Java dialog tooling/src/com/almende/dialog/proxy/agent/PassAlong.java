package com.almende.dialog.proxy.agent;

import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import com.almende.dialog.Settings;
import com.almende.dialog.XMPPReceiverServlet;
import com.almende.dialog.model.AnswerPost;
import com.almende.dialog.state.StringStore;


import flexjson.JSONDeserializer;
@Path("/passAlong/")
public class PassAlong {

	private static final String URL="http://"+Settings.HOST+"/passAlong/";
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	private String getQuestion(String question_no){
		return getQuestion(question_no,"");
	}	
	
	private String getQuestion(String question_no,String responder){
		String result = null;
		Integer questionNo = Integer.parseInt(question_no);
		switch (questionNo){
			case 2:
			case 4:
				result= "{requester:\""+URL+"id\",question_text:\""+URL+"questions/"+questionNo+"\",type:\"comment\",answers:[]}";
				break;
			case 3:
				result= "{requester:\""+URL+"id\",question_text:\""+URL+"questions/"+questionNo+"/"+responder+"\",type:\"closed\",answers:["+
						"{answer_text:\""+URL+"answers/31\",callback:\""+URL+"questions/31\"},"+
						"{answer_text:\""+URL+"answers/32\",callback:\""+URL+"questions/4\"},"+
						"]}";
				break;
			default:
				result= "{requester:\""+URL+"id\",question_text:\""+URL+"questions/"+questionNo+"\",type:\"open\",answers:["+
						"{answer_text:\"\",callback:\""+URL+"questions/"+(questionNo+1)+"\"}"+
						"]}";
				break;
		}
		return result;
	}

	@GET
	@Path("/id/")
	public Response getId(){
		return Response.ok("{ url:\""+URL+"\",nickname:\"PassAlong\"}").build();
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
		Integer questionNo = Integer.parseInt(question_no);
		String answer_input="";
		String responder="";
		try {
			AnswerPost answer = new JSONDeserializer<AnswerPost>().
					use(null, AnswerPost.class).
					deserialize(answer_json);
			answer_input=answer.getAnswer_text();
			responder = answer.getResponder();
			
		} catch (Exception e){
			log.severe(e.toString());
		}
		if (!responder.equals("")){
			switch (questionNo){
				case 1: //Get address, store somewhere
					StringStore.storeString(responder+"_passAlong_address", answer_input);
					StringStore.storeString(answer_input+"_passAlong_address", responder); //For the return path!:)
					break;
				case 31: //Get address, store somewhere
					question_no="1";
					break;
				case 2: //Get message, schedule outbound call
					StringStore.storeString(responder+"_passAlong_message", answer_input);
					XMPPReceiverServlet.startDialog(StringStore.getString(responder+"_passAlong_address"), getQuestion("3",responder));
					break;
			}
		}
		return Response.ok(getQuestion(question_no,responder)).build();
	}
	
	@Path("/questions/{question_no}/{responder}")
	@GET
	@Produces("text/plain")
	public  Response getQuestionText(@PathParam("question_no") String question_no,
									@QueryParam("preferred_language") String preferred_language, 
									@PathParam("responder") String responder ){
		Integer questionNo = Integer.parseInt(question_no);
		String result = "";
		String message = "";
		if (responder != null && !responder.equals("")){
			message = StringStore.getString(responder+"_passAlong_message");
		}
		if (preferred_language != null && preferred_language.startsWith("en")){
			switch (questionNo){
				case 0: result="Hi, to whom should I pass a message?"; break;
				case 1: result="What message should I deliver?"; break;
				case 2: result="Right, I'm on it!"; break;
				case 3: result="I have a message from '"+responder+"' for you:\n\""+
							   message+"\"\n"+
							   "Do you want to send say something back?"; break;
				case 4: result="Okay, goodbye!"; break;
				default: result="Eehhh??!?";
			}
		} else{
			switch (questionNo){
			case 0: result="Hoi, aan wie kan ik iets doorgeven?"; break;
			case 1: result="Wat moet ik doorgeven?"; break;
			case 2: result="Prima, ik regel het!"; break;
			case 3: result="Ik heb een bericht van '"+responder+"' voor je:\n\""+
							   message+"\"\n"+
						   "Wil je nog iets terugzeggen?"; break;
			case 4: result="Prima, tot horens!"; break;
			default: result="Eehhh??!?";
			}
		}
		return Response.ok(result).build();
	}
	@Path("/questions/{question_no}")
	@GET
	@Produces("text/plain")
	public  Response getQuestionText(@PathParam("question_no") String question_no, @QueryParam("preferred_language") String preferred_language ){
		return getQuestionText(question_no,preferred_language,"");
	}
	
	@Path("/answers/{answer_no}")
	@GET
	@Produces("text/plain")
	public Response getAnswerText(@PathParam("answer_no") String answer_no, @QueryParam("preferred_language") String preferred_language){
		Integer answerNo = Integer.parseInt(answer_no);
		String result = "";
		if (preferred_language != null && preferred_language.startsWith("en")){
			switch (answerNo){
				case 31: result="Yes"; break;
				case 32: result="No"; break;
				default: result="Eehhh??!?";
			}
		} else{
			switch (answerNo){
				case 31: result="Ja"; break;
				case 32: result="Nee"; break;
				default: result="Eehhh??!?";
			}
		}
		return Response.ok(result).build();
	}
}

