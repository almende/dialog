package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.tools.TextMessage;
import com.google.appengine.api.utils.SystemProperty;


public class MailServlet extends TextServlet {
	
	private static final long serialVersionUID = 6892283600126803780L;
	private static final String servletPath = "/_ah/mail/";
	private static final String adapterType = "MAIL";
	
	public void doErrorPost(HttpServletRequest req, HttpServletResponse res) {}
	
	@Override
	protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		
		Properties props = new Properties();
		javax.mail.Session mailSession = javax.mail.Session.getDefaultInstance(props, null);
		
		MimeMessage message = new MimeMessage(mailSession, req.getInputStream());
		
		String uri = req.getRequestURI();
		String recipient = uri.substring(servletPath.length());
		
		/*Address[] recipients = message.getAllRecipients();
		Address recipient=null;
		if (recipients.length>0){
			recipient = recipients[0];
		}*/ 
		return receiveMessage( message, recipient );
	}

        /**
         * @param msg
         * @param message
         * @param recipient
         * @return
         * @throws MessagingException
         * @throws Exception
         * @throws IOException
         */
        private TextMessage receiveMessage( MimeMessage message, String recipient )
        throws MessagingException, Exception, IOException
        {
            TextMessage msg = new TextMessage();
            msg.setSubject("RE: "+message.getSubject());
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
    		if(senders != null && senders.length>0) {
    			InternetAddress sender = (InternetAddress) senders[0];
    			msg.setAddress(sender.getAddress());
    			msg.setRecipientName(sender.getPersonal());
    		}
    		
                Multipart mp = null;
                if(message.getContent() instanceof Multipart)
                {
                    mp = (Multipart) message.getContent();
                }
                else 
                {
                    mp = new MimeMultipart();
                    mp.addBodyPart( new MimeBodyPart(new InternetHeaders(), message.getContent().toString().getBytes()) ); 
                }
    		if(mp.getCount()>0) {
    			msg.setBody(mp.getBodyPart(0).getContent().toString());
    			log.info("Receive mail: "+msg.getBody());
    		}
    		
    		return msg;
        }

        @Deprecated
        /**
         * @Deprecated use broadcastMessage instead
         */
	@Override
	protected int sendMessage(String message, String subject, String from, String fromName,
			String to, String toName, AdapterConfig config) {
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
            
            log.info("Send reply to mail post: "+(new Date().getTime()));

        } catch (AddressException e) {
            log.warning("Failed to send message, because wrong address: "+e.getLocalizedMessage());
        } catch (MessagingException e) {
        	log.warning("Failed to send message, because message: "+e.getLocalizedMessage());
        } catch (UnsupportedEncodingException e) {
        	log.warning("Failed to send message, because encoding: "+e.getLocalizedMessage());
		}
		
		return 1;		
	}
	
        @Override
        protected int broadcastMessage( String message, String subject, String from, String fromName, String senderName,
            Map<String, String> addressNameMap, AdapterConfig config )
        throws Exception
        {
            javax.mail.Session session = javax.mail.Session.getDefaultInstance( new Properties(), null );
            try
            {
                Message msg = new MimeMessage( session );
                msg.setFrom( new InternetAddress( from, senderName ) );
                for ( String address : addressNameMap.keySet() )
                {
                    String toName = addressNameMap.get( address );
                    msg.addRecipient( Message.RecipientType.TO, new InternetAddress( address, toName ) );
                }
                msg.setSubject( subject );
                msg.setText( message );
                Transport.send( msg );
    
                log.info( "Send reply to mail post: " + ( new Date().getTime() ) );
            }
            catch ( AddressException e )
            {
                log.warning( "Failed to send message, because wrong address: "
                    + e.getLocalizedMessage() );
            }
            catch ( MessagingException e )
            {
                log.warning( "Failed to send message, because message: " + e.getLocalizedMessage() );
            }
            catch ( UnsupportedEncodingException e )
            {
                log.warning( "Failed to send message, because encoding: " + e.getLocalizedMessage() );
            }
            return 1;
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
