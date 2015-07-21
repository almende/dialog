package com.almende.dialog.adapter;

import java.io.StringWriter;
import java.util.Collection;
import javax.ws.rs.Path;
import org.znerd.xmlenc.XMLOutputter;
import com.almende.dialog.model.Question;

@Path("/vvxml/")
public class VoxeoVoiceXMLRESTProxy extends VoiceXMLRESTProxy {
	
    @Override
    protected String renderComment(Question question, Collection<String> prompts, String sessionKey) {
		
		String localID = sessionKey.split("\\|")[1];
		/*String handleTimeoutURL = "/vxml/timeout";
		String handleExceptionURL = "/vxml/exception";*/
		
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("vxml");
				outputter.attribute("version", "2.1");
				outputter.attribute("xmlns", "http://www.w3.org/2001/vxml");
				outputter.startTag("form");
						if (question != null && question.getType().equalsIgnoreCase("referral")){
							outputter.startTag("transfer");
								outputter.attribute("name", "thisCall");
								outputter.attribute("dest", question.getUrl()+";ani="+localID);
								outputter.attribute("bridge","true");
								
								for (String prompt : prompts){
									outputter.startTag("prompt");
										outputter.startTag("audio");
											outputter.attribute("src", prompt);
										outputter.endTag();
									outputter.endTag();
								}
								outputter.startTag("filled");
									/*outputter.startTag("if");
										outputter.attribute("cond", "thisCall=='noanswer'");
										outputter.startTag("goto");
											outputter.attribute("next", handleTimeoutURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
										outputter.endTag();
									outputter.startTag("elseif");
										outputter.attribute("cond", "thisCall=='busy' || thisCall=='network_busy'");
									outputter.endTag();
										outputter.startTag("goto");
											outputter.attribute("next", handleExceptionURL+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
										outputter.endTag();										
									outputter.endTag();*/
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
									outputter.attribute("next", getAnswerUrl()+"?question_id="+question.getQuestion_id()+"&sessionKey="+sessionKey);
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
	
	@Override
	protected String getAnswerUrl() {
		return "/vvxml/answer";
	}
}
