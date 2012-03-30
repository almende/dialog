package com.almende.dialog;

import java.io.IOException;
//import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.state.StringStore;
import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

public class XMPPReceiverServlet extends HttpServlet {
	private static final long serialVersionUID = 10291032309680299L;
	//private static final Logger log = Logger.getLogger(com.almende.dialog.XMPPReceiverServlet.class.getName()); 	
	//TODO: Add presence info
	
	
	//TODO: Make this dynamic (through some routing protocol?)
	private static final String DEMODIALOG = "http://dialogserver.appspot.com/howIsTheWeather/";
	//TODO
	
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse res)
          throws IOException {

        XMPPService xmpp = XMPPServiceFactory.getXMPPService();

        Message message = xmpp.parseMessage(req);
        JID jid = message.getFromJid();

        String address = jid.getId();
        String body = message.getBody();
        
        String json = StringStore.getString(address);
        Question question=null;
        if (json == null){
        	question=Question.fromURL(DEMODIALOG);
        } else {
        	question=Question.fromJSON(json);
        }
        String reply="I'm sorry, I don't know what to say. Please retry talking with me at a later time.";
        if (question != null){
       		question = question.answer( null, body);
        	if (question != null){
        		reply = question.getQuestion_expandedtext();
        		if (question.getType().equals("closed")){
        			reply+="\n[";
        			for (Answer ans: question.getAnswers()){
        				reply+=" "+ans.getAnswer_expandedtext()+"|";
        			}
        			reply=reply.substring(0, reply.length()-1)+"]";
        		}
            	if (question.getType().equals("referral")){
            		question = Question.fromURL(question.getUrl());
            		reply+="\n"+question.getQuestion_expandedtext();
            	}
        		while (question.getType().equals("comment")){
        			question = question.answer( null, null);
        			if (question == null) break;
        			reply+="\n"+question.getQuestion_expandedtext();
        		}
        	}
        	if (question == null){
        		StringStore.dropString(address);
        	} else {
        		StringStore.storeString(address, question.toJSON());
        	}
        }
        Message msg = new MessageBuilder()
                .withRecipientJids(jid)
                .withBody(reply)
                .build();
        
        xmpp.sendMessage(msg);

    }
}
