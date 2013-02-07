package com.almende.dialog.adapter;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
		
		AdapterConfig config = session.getAdapterConfig();
		if(config!=null) {
			Broadsoft bs = new Broadsoft(config);
			bs.endCall(session.getExternalSession());
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
		
		Broadsoft bs = new Broadsoft(config);
		bs.startSubscription();
		
		String extSession = bs.startCall(address);
		
		session.setExternalSession(extSession);
		session.storeSession();
		
		return sessionKey;
	}
	public static ArrayList<String> getActiveCalls(AdapterConfig config) {
		Broadsoft bs = new Broadsoft(config);
		return bs.getActiveCalls();
	}
	
	public static ArrayList<String> getActiveCallsInfo(AdapterConfig config) {
		Broadsoft bs = new Broadsoft(config);
		return bs.getActiveCallsInfo();
	}
	
	public static boolean killActiveCalls(AdapterConfig config) {
		Broadsoft bs = new Broadsoft(config);
		return bs.killActiveCalls();
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
			else
				question=null;
			
			
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
	
	@Path("timeout")
	@GET
	@Produces("application/voicexml+xml")
	public Response timeout(@QueryParam("question_id") String question_id, @QueryParam("sessionKey") String sessionKey){
		String reply="<vxml><exit/></vxml>";
		String json = StringStore.getString(question_id);
		if (json != null){
			Question question = Question.fromJSON(json);
			String responder = StringStore.getString(question_id+"-remoteID");
			Session session = Session.getSession(sessionKey);
			if (session.killed){
				return Response.status(Response.Status.BAD_REQUEST).build();
			}
			DDRWrapper.log(question,session,"Timeout");
			
			StringStore.dropString(question_id);
			StringStore.dropString(question_id+"-remoteID");
			StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());

			question = question.event("timeout");
			
			return handleQuestion(question,responder,sessionKey);
		}
		return Response.ok(reply).build();
	}
	
	@Path("hangup")
	@GET
	@Produces("application/voicexml+xml")
	public Response hangup(@QueryParam("direction") String direction,@QueryParam("remoteID") String remoteID,@QueryParam("localID") String localID){
		log.info("call hangup with:"+direction+":"+remoteID+":"+localID);
		
		String adapterType="broadsoft";
		
		String sessionKey = adapterType+"|"+localID+"|"+remoteID;
		Session session = Session.getSession(sessionKey);
		
		Question question = null;
		String json = StringStore.getString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());
		if(json!=null) {
			question = Question.fromJSON(json);
			String question_id = question.getQuestion_id();
			
			StringStore.dropString(question_id);
			StringStore.dropString(question_id+"-remoteID");
			StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());
		} else {
			question = Question.fromURL(session.getStartUrl(),remoteID,localID);
		}

		question.event("hangup");
		DDRWrapper.log(question,session,"Hangup");
		
		handleQuestion(null,remoteID,sessionKey);
		
		return Response.ok("").build();
	}
	
	@Path("cc")
	@POST
	public Response receiveCCMessage(String xml) {
		
		log.info("Received cc: "+xml);
		
		String reply="";
		
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			
			Document dom = db.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
			Node subscriberId = dom.getElementsByTagName("subscriberId").item(0);
			
			AdapterConfig config = AdapterConfig.findAdapterConfigByUsername(subscriberId.getTextContent());
			
			Node eventData = dom.getElementsByTagName("eventData").item(0);
			// check if incall event
			if(eventData.getChildNodes().getLength()>1) {
				
				
				Node call = eventData.getChildNodes().item(1);
				
				Node personality = null;
				Node callState = null;
				Node remoteParty = null;
				@SuppressWarnings("unused")
				Node extTrackingId = null;
				
				for(int i=0;i<call.getChildNodes().getLength();i++) {
					Node node = call.getChildNodes().item(i);
					if(node.getNodeName().equals("personality")) {
						personality=node;
					} else if(node.getNodeName().equals("callState")) {
						callState=node;
					} else if(node.getNodeName().equals("remoteParty")) {
						remoteParty=node;
					} else if(node.getNodeName().equals("extTrackingId")) {
						extTrackingId=node;
					}
				}				
				
				if(callState!=null && callState.getNodeName().equals("callState")) {

					// Check if call
					if(callState.getTextContent().equals("Released")) {
						
						// Check if a sip or network call
						String type="";
						String address="";
						String user="";
						for(int i=0; i<remoteParty.getChildNodes().getLength();i++) {
							Node rpChild = remoteParty.getChildNodes().item(i);
							if(rpChild.getNodeName().equals("address")) {
								address=rpChild.getTextContent();
							} else if(rpChild.getNodeName().equals("callType")) {
								type=rpChild.getTextContent();
							} else if(rpChild.getNodeName().equals("userId")) {
								user=rpChild.getTextContent();
							}
						}
						
						// Check if session can be matched to call
						if(type.equals("Network") || type.equals("Group")) {
							
							if(type.equals("Group")) {
								address = user.substring(0,user.indexOf("@"));
							} else if(type.equals("Network")) {
								address = address.replace("tel:", "");
							}
							String direction="inbound";
							if(personality.getTextContent().equals("Originator")) {
								address += "@outbound";
								direction="outbound";
							}
							String adapterType="broadsoft";
							String sessionKey = adapterType+"|"+config.getMyAddress()+"|"+address;
							String ses = StringStore.getString(sessionKey);
							
							if(ses!=null) {
								log.info("SESSSION FOUND!! SEND HANGUP!!!");
								this.hangup(direction, address, config.getMyAddress());
							} else {
								
								if(personality.getTextContent().equals("Originator")) {
									log.info("Probably a disconnect of a redirect");
								} else if(personality.getTextContent().equals("Terminator")) {
									log.info("No session for this inbound?????");
								} else {
									log.info("What the hell was this?????");
									log.info("Session already ended?");
								}
							}
							
						} else {
							log.warning("Can't handle hangup of type: "+type+" (yet)");
						}						
					}
				}
			} else {
				Node eventName = dom.getElementsByTagName("eventName").item(0);
				if(eventName!=null && eventName.getTextContent().equals("SubscriptionTerminatedEvent")) {
					
					Broadsoft bs = new Broadsoft(config);
					bs.startSubscription();
					log.info("Start a new dialog");
				}
				
				log.info("Received a subscription update!");
			}
			
		} catch (Exception e) {
			log.severe("Something failed: "+e.getMessage());
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
								outputter.startTag("filled");
									outputter.startTag("exit");
									outputter.endTag();
								outputter.endTag();
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
		String handleTimeoutURL = "/vxml/timeout";

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
						outputter.startTag("goto");
							outputter.attribute("next", handleTimeoutURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
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
		} else {
			log.info("Going to hangup? So clear Session?");
		}
		log.info("Sending xml: "+result);
		return Response.ok(result).build();
	}
}
