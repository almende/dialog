package com.almende.dialog.adapter;

import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;

import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Question;
import com.google.appengine.api.utils.SystemProperty;


public class MailServlet extends TextServlet {
	
	private static final long serialVersionUID = 6892283600126803780L;
	private static final String servletPath = "/_ah/mail/";
	private static final String adapterType = "MAIL";
	
	@Override
	protected TextMessage receiveMessage(HttpServletRequest req) throws Exception {
		
		TextMessage msg = new TextMessage();
		Properties props = new Properties();
		javax.mail.Session mailSession = javax.mail.Session.getDefaultInstance(props, null);
		MimeMessage message = new MimeMessage(mailSession, req.getInputStream());
		msg.setSubject("RE: "+message.getSubject());
		
		String uri = req.getRequestURI();
		String recipient = uri.substring(servletPath.length());
		
		/*Address[] recipients = message.getAllRecipients();
		Address recipient=null;
		if (recipients.length>0){
			recipient = recipients[0];
		}*/ 
		if (recipient != null && !recipient.equals("")){
			msg.setLocalAddress(recipient.toString());
		} else if(SystemProperty.environment.value() == com.google.appengine.api.utils.SystemProperty.Environment.Value.Development) {
			
			Address[] recipients = message.getAllRecipients();
			if (recipients.length>0){
				InternetAddress recip= (InternetAddress)recipients[0];
				msg.setLocalAddress(recip.getAddress());
			} else
				throw new Exception("MailServlet: Can't determine local address! (Dev)");
			
		} else {
			throw new Exception("MailServlet: Can't determine local address!");
		}
		
		Address[] senders = message.getFrom();
		if(senders.length>0) {
			InternetAddress sender = (InternetAddress) senders[0];
			msg.setAddress(sender.getAddress());
			msg.setRecipientName(sender.getPersonal());
		}
		
		Multipart mp = (Multipart) message.getContent();
		if(mp.getCount()>0) {
			msg.setBody(mp.getBodyPart(0).getContent().toString());
			log.info("Receive mail: "+msg.getBody());
		}
		
		return msg;
	}

	@Override
	public void sendMessage(String message, String subject, String from, String fromName,
			String to, String toName) {
		Properties props = new Properties();
        javax.mail.Session session = javax.mail.Session.getDefaultInstance(props, null);

		try {
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(from, fromName));
            msg.addRecipient(Message.RecipientType.TO,
                             new InternetAddress(to, toName));
            msg.setSubject(subject);
            msg.setText(message);
            Transport.send(msg);
            
            log.warning("Send reply to mail post: "+(new Date().getTime()));

        } catch (AddressException e) {
            log.warning("Failed to send message, because wrong address: "+e.getLocalizedMessage());
        } catch (MessagingException e) {
        	log.warning("Failed to send message, because message: "+e.getLocalizedMessage());
        } catch (UnsupportedEncodingException e) {
        	log.warning("Failed to send message, because encoding: "+e.getLocalizedMessage());
		}
		
	}

	@Override
	public String getNickname(Question question) {
		HashMap<String, String> requester = question.getExpandedRequester();
		return requester.get("nickname");
	}
	
	@Override
	protected String getServletPath() {
		return servletPath;
	}
	
	@Override
	protected String getAdapterType() {
		return adapterType;
	}
}
