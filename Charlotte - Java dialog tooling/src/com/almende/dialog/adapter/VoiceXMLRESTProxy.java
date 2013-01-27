package com.almende.dialog.adapter;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.znerd.xmlenc.XMLOutputter;

import com.almende.dialog.DDRWrapper;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.state.StringStore;
import com.almende.dialog.util.KeyServerLib;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

@Path("/vxml/")
public class VoiceXMLRESTProxy {
	private static final Logger log = Logger.getLogger(com.almende.dialog.adapter.VoiceXMLRESTProxy.class.getName()); 	
	private static final int LOOP_DETECTION=10;
	private static final String DTMFGRAMMAR="/dtmf2hash.grxml";
	
	public static void killSession(Session session){
		
		if(session.getDirection().equals("outbound")) {
			AdapterConfig config = session.getAdapterConfig();
			if(config!=null) {
				Broadsoft bs = new Broadsoft(config.getXsiUser(), config.getXsiPasswd());
				bs.endCall(session.getExternalSession());
			}
		}
	}
	
	public static String dial(String address, String url, AdapterConfig config){

		address = formatNumber(address).replaceFirst("\\+31", "0")+"@outbound";
		String adapterType="broadsoft";
		String sessionKey = adapterType+"|"+config.getMyAddress()+"|"+address;
		Session session = Session.getSession(sessionKey);
		if (session == null){
			log.severe("VoiceXMLRESTProxy couldn't start new outbound Dialog, adapterConfig not found? "+sessionKey);
			return "";
		}
		session.setStartUrl(url);
		session.setDirection("outbound");
		session.setRemoteAddress(address);
		session.setType(adapterType);
		session.storeSession();
		
		DDRWrapper.log(url,"",session,"Dial",config);
		
		Broadsoft bs = new Broadsoft(config.getXsiUser(), config.getXsiPasswd());
		String extSession = bs.startCall(address);
		
		session.setExternalSession(extSession);
		session.storeSession();
		
		return sessionKey;
	}
	public static String getActiveCalls(AdapterConfig config) {
		Broadsoft bs = new Broadsoft(config.getXsiUser(), config.getXsiPasswd());
		return bs.getActiveCalls();
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
		
		String adapterType="broadsoft";
		AdapterConfig config = AdapterConfig.findAdapterConfig(adapterType, localID);
		
		if(KeyServerLib.checkCredits(config.getPublicKey())) {
			log.info("Call is authorized");
			String sessionKey = adapterType+"|"+localID+"|"+remoteID+(direction.equals("outbound")?"@outbound":"");
			Session session = Session.getSession(sessionKey);
			String url="";
			if (direction.equals("inbound")){
				url = config.getInitialAgentURL();
				session.setStartUrl(url);
				session.setDirection("inbound");
				session.setRemoteAddress(remoteID);
				session.setType(adapterType);
				session.setPubKey(config.getPublicKey());
			} else {
				url=session.getStartUrl();
			}
			Question question = Question.fromURL(url,remoteID,localID);
			DDRWrapper.log(question,session,"Start",config);
			session.storeSession();
						
			return handleQuestion(question,remoteID,sessionKey);
		} else {
			DDRWrapper.log(null,null,"FailInbound",config);
			return Response.status(Status.FORBIDDEN).build();
		}
	}
	
	@Path("continue")
	@GET
	@Produces("application/voicexml+xml")
	public Response getContinueDialog(@QueryParam("direction") String direction,@QueryParam("remoteID") String remoteID,@QueryParam("localID") String localID){
		log.warning("call continue with:"+direction+":"+remoteID+":"+localID);
		
		String adapterType="broadsoft";
		AdapterConfig config = AdapterConfig.findAdapterConfig(adapterType, localID);
		
		if(KeyServerLib.checkCredits(config.getPublicKey())) {
			log.info("Call is authorized");
			String sessionKey = adapterType+"|"+localID+"|"+remoteID+(direction.equals("outbound")?"@outbound":"");
			Session session = Session.getSession(sessionKey);
			if (session.killed){
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			
			String json = StringStore.getString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());
			Question question = Question.fromJSON(json);
			
			// Answer question with, if correct, the only answer.
			List<Answer> answers = question.getAnswers();
			if(answers!=null && answers.size()>0)
				question = question.answer(remoteID, answers.get(0).getAnswer_id(), null);
			
			
			DDRWrapper.log(question,session,"Continue",config);
			return handleQuestion(question,remoteID,sessionKey);
		} else {
			DDRWrapper.log(null,null,"FailInbound",config);
			return Response.status(Status.FORBIDDEN).build();
		}
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
			StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());

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
	

	
	@SuppressWarnings("unused")
	public Return formQuestion(Question question,String address) {
		ArrayList<String> prompts = new ArrayList<String>();
		for (int count = 0; count<=LOOP_DETECTION; count++){
			if (question == null) break;
			String preferred_language = question.getPreferred_language();
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
				break;
			} else 	if (question.getType().equals("referral")) {
				if(!question.getUrl().startsWith("tel:")) {
					//question = Question.fromURL(question.getUrl(),address);
					question = question.answer(null, null, null);
					break;
				} else 
					break;
			} else {
				break; //Jump from forloop (open questions, etc.)
			}
		}
		return new Return(prompts, question);
	}
	
	private String renderComment(Question question,ArrayList<String> prompts, String sessionKey){
		
		String handleAnswerURL = "/vxml/answer";
		
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.startTag("form");
						if (question != null && question.getType().equals("referral")){
							outputter.startTag("transfer");
								outputter.attribute("name", "thisCall");
								outputter.attribute("dest", question.getUrl());
								outputter.attribute("bridge","true");
								
								for (String prompt : prompts){
									outputter.startTag("prompt");
										outputter.startTag("audio");
											outputter.attribute("src", prompt);
										outputter.endTag();
									outputter.endTag();
								}
							outputter.endTag();
						} else {
							outputter.startTag("block");
								for (String prompt : prompts){
									outputter.startTag("prompt");
										outputter.startTag("audio");
											outputter.attribute("src", prompt);
										outputter.endTag();
									outputter.endTag();
								}
								outputter.startTag("goto");
									outputter.attribute("next", handleAnswerURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
								outputter.endTag();
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
		String result="<?xml version=\"1.0\" encoding=\"UTF-8\"?><vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\"><form><block><exit/></block></form></vxml>";
		Return res = formQuestion(question,remoteID);
		if(question !=null && !question.getType().equals("comment"))
			question = res.question;
		
		if (question != null){						
			question.generateIds();
			StringStore.storeString(question.getQuestion_id(), question.toJSON());
			StringStore.storeString(question.getQuestion_id()+"-remoteID", remoteID);
			
			Session session = Session.getSession(sessionKey);
			StringStore.storeString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress(), question.toJSON());
		
			if (question.getType().equals("closed")){
				result = renderClosedQuestion(question,res.prompts,sessionKey);
			} else if (question.getType().equals("open")){
				result = renderOpenQuestion(question,res.prompts,sessionKey);
			} else if (question.getType().equals("referral")){
				if (question.getUrl().startsWith("tel:")){
					result = renderComment(question,res.prompts, sessionKey);	
				}
			} else if (res.prompts.size() > 0) {
				result = renderComment(question,res.prompts, sessionKey);
			}
		} else if (res.prompts.size() > 0){
			result = renderComment(null,res.prompts, sessionKey);
		}
		log.info("Sending xml: "+result);
		return Response.ok(result).build();
	}
}
