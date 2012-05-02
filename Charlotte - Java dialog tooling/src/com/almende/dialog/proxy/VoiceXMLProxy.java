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
import com.sun.jersey.spi.resource.Singleton;
@Singleton
@Path("/vxml/")
public class VoiceXMLProxy {
	private static final Logger log = Logger.getLogger(com.almende.dialog.proxy.VoiceXMLProxy.class.getName()); 	
	
	private String renderTransfer(Question question){
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.startTag("form");
					outputter.startTag("block");
						outputter.startTag("prompt");
							outputter.startTag("audio");
								outputter.attribute("src", question.getQuestion_text());
							outputter.endTag();
						outputter.endTag();
					outputter.endTag();
					outputter.startTag("transfer");
						outputter.attribute("dest", question.getUrl());
					outputter.endTag();
				outputter.endTag();
			outputter.endTag();
			outputter.endDocument();
		} catch (Exception e) {
			log.severe("Exception in creating question XML: "+ e.toString());
		}
		return sw.toString();	
	}
	private String renderComment(Question question){
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.startTag("form");
					outputter.startTag("block");
						outputter.startTag("prompt");
							outputter.startTag("audio");
								outputter.attribute("src", question.getQuestion_text());
							outputter.endTag();
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
	private String renderQuestion(Question question){
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
					outputter.startTag("prompt");
						outputter.startTag("audio");
							String url=question.getQuestion_text();
							outputter.attribute("src", url);
						outputter.endTag();
						for (Answer answer : answers){
							outputter.startTag("audio");
							    url = answer.getAnswer_text();
								outputter.attribute("src", url);
							outputter.endTag();
						}
					outputter.endTag();
						
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

	@Path("new")
	@GET
	@Produces("application/voicexml+xml")
	public Response getNewDialog(@QueryParam("url") String url,@QueryParam("remoteID") String remoteID){
		Question question = Question.fromURL(url,remoteID);
		question.generateIds();
		
		StringStore.storeString(question.getQuestion_id(), question.toJSON());
		StringStore.storeString(question.getQuestion_id()+"-remoteID", remoteID);
		return Response.ok(renderQuestion(question)).build();
	}
	private Response getNewDialog(@QueryParam("url") String url){
		//TODO use earlier remoteID if available
		return getNewDialog(url,null);
	}
	
	@Path("answer")
	@GET
	@Produces("application/voicexml+xml")
	public Response answer(@QueryParam("question_id") String question_id, @QueryParam("answer_id") String answer_id){
		String reply="<vxml><exit/></vxml>";
		String json = StringStore.getString(question_id);
		if (json != null){
			Question question = Question.fromJSON(json);
			String responder = StringStore.getString(question_id+"-remoteID");
			question = question.answer(responder,answer_id,null);
			question.generateIds();
			
			StringStore.storeString(question.getQuestion_id(), question.toJSON());
			StringStore.storeString(question.getQuestion_id()+"-remoteID",responder);
			StringStore.dropString(question_id);
			StringStore.dropString(question_id+"-remoteID");
			
			if (question.getType().equals("comment")){
				reply=renderComment(question);
			} else if (question.getType().equals("referral")){
				if (question.getUrl().startsWith("tel:")){
					reply=renderTransfer(question);	
				} else {
					return getNewDialog(question.getUrl());
				}
			} else {
				reply=renderQuestion(question);
			}
		}
		return Response.ok(reply).build();
	}
}
