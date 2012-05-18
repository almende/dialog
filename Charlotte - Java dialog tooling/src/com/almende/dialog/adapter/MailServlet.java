package com.almende.dialog.adapter;

import java.io.IOException;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MailServlet extends HttpServlet {
	private static final long serialVersionUID = 2523444841594913679L;
    public void doPost(HttpServletRequest req, 
            HttpServletResponse resp) 
            		throws IOException { 
    	Properties props = new Properties(); 
    	Session session = Session.getDefaultInstance(props, null); 
    	try {
			MimeMessage message = new MimeMessage(session, req.getInputStream());
			System.out.println("Received mail from:"+message.getSender().toString()+" -> "+message.getAllRecipients().toString());
			
		} catch (MessagingException e) {
			System.out.println("Error receiving email.");
			e.printStackTrace();
		}
    
    }
}
