package com.almende.dialog.adapter;

import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.znerd.xmlenc.XMLOutputter;

import com.almende.dialog.DDRWrapper;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.state.StringStore;
import com.almende.util.ParallelInit;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

@Path("/vxml/")
public class VoiceXMLRESTProxy {
	private static final Logger log = Logger.getLogger(com.almende.dialog.adapter.VoiceXMLRESTProxy.class.getName()); 	
	private static final int LOOP_DETECTION=10;
	private static final String DTMFGRAMMAR="/dtmf2hash.grxml";
	
	public static void killSession(Session session){
		//TODO: kill outstanding calls , will now die after client comes back to server.
	}
	
	public static String dial(String address, String url, AdapterConfig config){

		address = formatNumber(address).replaceFirst("\\+31", "0")+"@outbound";
		String sessionKey = "broadsoft|"+config.getMyAddress()+"|"+address;
		Session session = Session.getSession(sessionKey);
		if (session == null){
			log.severe("VoiceXMLRESTProxy couldn't start new outbound Dialog, adapterConfig not found? "+sessionKey);
			return "";
		}
		session.setStartUrl(url);
		session.setDirection("outbound");
		session.setRemoteAddress(address);
		session.storeSession();
		
		DDRWrapper.log(url,"",session,"Dial",config);
		
		Client client = ParallelInit.getClient();

		WebResource webResource = client.resource(config.getXsiURL());
		webResource.addFilter(new HTTPBasicAuthFilter(config.getXsiUser(), config.getXsiPasswd()));
		try {
			String result = webResource.queryParam("address", URLEncoder.encode(address, "UTF-8")).type("text/plain").post(String.class);
			log.info("Result from BroadSoft: "+result);
		} catch (Exception e) {
			log.severe("Problems dialing out:"+e.getMessage());
		}
		return sessionKey;
	}
	private static String formatNumber(String phone) {
		PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
		try {
			PhoneNumber numberProto = phoneUtil.parse(phone,"NL");
			return phoneUtil.format(numberProto,PhoneNumberFormat.E164);
		} catch (NumberParseException e) {
		  log.severe("NumberParseException was thrown: " + e.toString());
		}
		return null;	
	}
	
	@Path("new")
	@GET
	@Produces("application/voicexml+xml")
	public Response getNewDialog(@QueryParam("direction") String direction,@QueryParam("remoteID") String remoteID,@QueryParam("localID") String localID){
		log.warning("call started:"+direction+":"+remoteID+":"+localID);
		AdapterConfig config = AdapterConfig.findAdapterConfig("broadsoft", localID);
		String sessionKey = "broadsoft|"+localID+"|"+remoteID+(direction.equals("outbound")?"@outbound":"");
		Session session = Session.getSession(sessionKey);
		String url="";
		if (direction.equals("inbound")){
			url = config.getInitialAgentURL();
			session.setStartUrl(url);
			session.setDirection("inbound");
			session.setRemoteAddress(remoteID);
		} else {
			url=session.getStartUrl();
		}
		Question question = Question.fromURL(url,remoteID,localID);
		DDRWrapper.log(question,session,"Start",config);
		session.storeSession();
		return handleQuestion(question,remoteID,sessionKey);
	}
	
	@Path("answer")
	@GET
	@Produces("application/voicexml+xml")
	public Response answer(@QueryParam("question_id") String question_id, @QueryParam("answer_id") String answer_id, @QueryParam("answer_input") String answer_input, @QueryParam("sessionKey") String sessionKey){
		String reply="<vxml><exit/></vxml>";
		String json = StringStore.getString(question_id);
		if (json != null){
			Question question = Question.fromJSON(json);
			String responder = StringStore.getString(question_id+"-remoteID");
			Session session = Session.getSession(sessionKey);
			if (session.killed){
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			DDRWrapper.log(question,session,"Answer");
			
			StringStore.dropString(question_id);
			StringStore.dropString(question_id+"-remoteID");

			question = question.answer(responder,answer_id,answer_input);
			
			
			return handleQuestion(question,responder,sessionKey);
		}
		return Response.ok(reply).build();
	}
	
	
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
	private String renderClosedQuestion(Question question,ArrayList<String> prompts,String sessionKey){
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
							outputter.attribute("next", handleAnswerURL+"?question_id="+question.getQuestion_id()+"&answer_id="+answers.get(cnt).getAnswer_id()+"&sessionKey="+sessionKey);
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
	private String renderOpenQuestion(Question question,ArrayList<String> prompts,String sessionKey){
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
				outputter.startTag("var");
					outputter.attribute("name","sessionKey");
					outputter.attribute("expr", "'"+sessionKey+"'");
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
							outputter.attribute("namelist","answer_input question_id sessionKey");
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
	
	private Response handleQuestion(Question question,String remoteID,String sessionKey){
		String result="<vxml><exit/></vxml>";
		Return res = formQuestion(question,remoteID);
		question = res.question;
		
		if (question != null){
			question.generateIds();
			StringStore.storeString(question.getQuestion_id(), question.toJSON());
			StringStore.storeString(question.getQuestion_id()+"-remoteID", remoteID);
		
			if (question.getType().equals("closed")){
				result = renderClosedQuestion(question,res.prompts,sessionKey);
			} else if (question.getType().equals("open")){
				result = renderOpenQuestion(question,res.prompts,sessionKey);
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
	

}
