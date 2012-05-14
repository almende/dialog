package com.almende.dialog.proxy;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.znerd.xmlenc.XMLOutputter;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.state.StringStore;

@Path("/vxml/")
public class VoiceXMLProxy {
	private static final Logger log = Logger.getLogger(com.almende.dialog.proxy.VoiceXMLProxy.class.getName()); 	
	private static final int LOOP_DETECTION=10;
	private static final String DTMFGRAMMAR="/dtmf2hash.grxml";
	
	private class Return {
		ArrayList<String> prompts;
		Question question;

		public Return(ArrayList<String> prompts, Question question) {
			this.prompts = prompts;
			this.question = question;
		}
	}
	
	public Return formQuestion(Question question,String address) {
		ArrayList<String> prompts = new ArrayList<String>();
		String preferred_language = question.getPreferred_language();
		for (int count = 0; count<=LOOP_DETECTION; count++){
			if (question == null) break;
			question.setPreferred_language(preferred_language);	
			String qText = question.getQuestion_text();
			
			if(qText!=null && !qText.equals("")) prompts.add(qText);

			if (question.getType().equals("closed")) {
				for (Answer ans : question.getAnswers()) {
					String answer = ans.getAnswer_text();
					if (answer != null && !answer.equals("")) prompts.add(answer);
				}
				break; //Jump from forloop
			} else if (question.getType().equals("comment")) {
				question = question.answer(null, null, null);
			} else 	if (question.getType().equals("referral")) {
				question = Question.fromURL(question.getUrl(),address);
			} else {
				break; //Jump from forloop (open questions, etc.)
			}
		}
		return new Return(prompts, question);
	}
	
	private String renderComment(Question question,ArrayList<String> prompts){
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.startTag("form");
					outputter.startTag("block");
						for (String prompt : prompts){
							outputter.startTag("prompt");
								outputter.startTag("audio");
									outputter.attribute("src", prompt);
								outputter.endTag();
							outputter.endTag();
						}
					outputter.endTag();
					if (question != null && question.getType().equals("referral")){
						outputter.startTag("transfer");
							outputter.attribute("dest", question.getUrl());
						outputter.endTag();
					}
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();
		} catch (Exception e) {
			log.severe("Exception in creating question XML: "+ e.toString());
		}
		return sw.toString();	
	}
	private String renderClosedQuestion(Question question,ArrayList<String> prompts){
		ArrayList<Answer> answers=question.getAnswers();
		
		String handleAnswerURL = "/vxml/answer";

		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.startTag("menu");	
					for (String prompt : prompts){
						outputter.startTag("prompt");
							outputter.startTag("audio");
								outputter.attribute("src", prompt);
							outputter.endTag();
						outputter.endTag();
					}
					for(int cnt=0; cnt<answers.size(); cnt++){
						outputter.startTag("choice");
							outputter.attribute("dtmf", new Integer(cnt+1).toString());
							outputter.attribute("next", handleAnswerURL+"?question_id="+question.getQuestion_id()+"&answer_id="+answers.get(cnt).getAnswer_id());
						outputter.endTag();
					}
					outputter.startTag("noinput");
						outputter.startTag("reprompt");
						outputter.endTag();
					outputter.endTag();
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();
		} catch (Exception e) {
			log.severe("Exception in creating question XML: "+ e.toString());
		}
		return sw.toString();
	}
	private String renderOpenQuestion(Question question,ArrayList<String> prompts){
		String handleAnswerURL = "/vxml/answer";

		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.attribute("xml:lang", "nl-NL"); //To prevent "unrecognized input" prompt
				outputter.startTag("var");
					outputter.attribute("name","answer_input");
				outputter.endTag();
				outputter.startTag("var");
					outputter.attribute("name","question_id");
					outputter.attribute("expr", "'"+question.getQuestion_id()+"'");
				outputter.endTag();
				outputter.startTag("form");
					outputter.startTag("field");
						outputter.attribute("name", "answer");
						outputter.startTag("grammar");
							outputter.attribute("mode", "dtmf");
							outputter.attribute("src", DTMFGRAMMAR);
							outputter.attribute("type", "application/srgs+xml");
						outputter.endTag();
						for (String prompt: prompts){
							outputter.startTag("prompt");
								outputter.startTag("audio");
									outputter.attribute("src", prompt);
								outputter.endTag();
							outputter.endTag();
						}
						outputter.startTag("noinput");
							outputter.startTag("reprompt");
							outputter.endTag();
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("filled");
						outputter.startTag("assign");
							outputter.attribute("name", "answer_input");
							outputter.attribute("expr", "answer$.utterance.replace(' ','','g')");
						outputter.endTag();
						outputter.startTag("submit");
							outputter.attribute("next", handleAnswerURL);
							outputter.attribute("namelist","answer_input question_id");
						outputter.endTag();
						outputter.startTag("clear");
							outputter.attribute("namelist", "answer_input answer");
						outputter.endTag();
					outputter.endTag();
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();	
		} catch (Exception e) {
			log.severe("Exception in creating open question XML: "+ e.toString());
		}		
		return sw.toString();
	}
	
	private Response handleQuestion(Question question,String remoteID){
		String result="<vxml><exit/></vxml>";
		Return res = formQuestion(question,remoteID);
		question = res.question;
		
		if (question != null){
			question.generateIds();
			StringStore.storeString(question.getQuestion_id(), question.toJSON());
			StringStore.storeString(question.getQuestion_id()+"-remoteID", remoteID);
		
			if (question.getType().equals("closed")){
				result = renderClosedQuestion(question,res.prompts);
			} else if (question.getType().equals("open")){
				result = renderOpenQuestion(question,res.prompts);
			} else if (question.getType().equals("referral")){
				if (question.getUrl().startsWith("tel:")){
					result = renderComment(question,res.prompts);	
				}
			} else if (res.prompts.size() > 0) {
				result = renderComment(question,res.prompts);
			}
		} else if (res.prompts.size() > 0){
			result = renderComment(null,res.prompts);
		}
		return Response.ok(result).build();
	}
	
	@Path("new")
	@GET
	@Produces("application/voicexml+xml")
	public Response getNewDialog(@QueryParam("url") String url,@QueryParam("remoteID") String remoteID){
		
		remoteID=stripPhonenumber(remoteID);
		Question question = Question.fromURL(url,remoteID);
		return handleQuestion(question,remoteID);
	}
	
	@Path("answer")
	@GET
	@Produces("application/voicexml+xml")
	public Response answer(@QueryParam("question_id") String question_id, @QueryParam("answer_id") String answer_id, @QueryParam("answer_input") String answer_input){
		String reply="<vxml><exit/></vxml>";
		String json = StringStore.getString(question_id);
		if (json != null){
			Question question = Question.fromJSON(json);
			String responder = StringStore.getString(question_id+"-remoteID");
			
			StringStore.dropString(question_id);
			StringStore.dropString(question_id+"-remoteID");

			question = question.answer(responder,answer_id,answer_input);
			return handleQuestion(question,responder);
		}
		return Response.ok(reply).build();
	}
	
	private String stripPhonenumber(String phone) {
		if (phone == null || phone.equals("")) return "";
		String[] sPhone = phone.split("@");
		String result = sPhone[0];
		result = "+31"+result.replaceFirst("^0","").replace("+31", "");
		return result;
	}
}
