package com.almende.dialog.adapter;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.TypeUtil;


public class MailServlet extends TextServlet {

    public static final String GMAIL_SENDING_PORT = "465";
    public static final String GMAIL_SENDING_HOST = "smtp.gmail.com";
    public static final String GMAIL_SENDING_PROTOCOL = "smtps";
    public static final String CC_ADDRESS_LIST_KEY = "cc_email";
    public static final String BCC_ADDRESS_LIST_KEY = "bcc_email";
	private static final long serialVersionUID = 6892283600126803780L;
	private static final String servletPath = "/_ah/mail/";
	
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

        /** method separated from the original @link{MailServlet#receiveMessage(HttpServletRequest, HttpServletResponse)}
         * so that it can be tested without any data mock-ups.
         * @since  3/09/2013
         */
        private TextMessage receiveMessage( MimeMessage message, String recipient )
        throws Exception
        {
            TextMessage msg = new TextMessage();
            msg.setSubject("RE: "+message.getSubject());
            if (recipient != null && !recipient.equals("")){
    			msg.setLocalAddress(recipient.toString());
    		} else {
    			
    			Address[] recipients = message.getAllRecipients();
    			if (recipients.length>0){
    				InternetAddress recip= (InternetAddress)recipients[0];
    				msg.setLocalAddress(recip.getAddress());
    			} else
    				throw new Exception("MailServlet: Can't determine local address! (Dev)");
    			
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
            if ( mp.getCount() > 0 )
            {
                //trim old messages when a message is revieved via the reply button
//                    String body = trimOldReplies( mp.getBodyPart( 0 ).getContent().toString(), msg.getLocalAddress() );
                msg.setBody( mp.getBodyPart( 0 ).getContent().toString() );
                log.info( "Receive mail: " + msg.getBody() );
            }
    		return msg;
        }

    @Deprecated
    /**
     * @Deprecated use broadcastMessage instead
     */
    @Override
    protected int sendMessage( String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config )
    {
        HashMap<String, String> addressNameMap = new HashMap<>( 1 );
        addressNameMap.put( to, toName );
        return broadcastMessage( message, subject, fromName, fromName, addressNameMap, extras, config );
    }
	
    @Override
    protected int broadcastMessage( String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config )
    {
        //xsiURL is of the form <email protocol>: <sending host>: <sending port> 
        String[] connectionSettingsArray = config.getXsiURL().split( ":" );
        String sendingHost = connectionSettingsArray.length == 3 ? connectionSettingsArray[1] : "smtp.gmail.com";
        String sendingPort = connectionSettingsArray.length == 3 ? connectionSettingsArray[2] : "465";
        Properties props = new Properties();
        props.put( "mail.smtp.host", sendingHost );
        props.put( "mail.smtp.port", sendingPort );
        props.put( "mail.smtp.user", config.getXsiUser() );
        props.put( "mail.smtp.password", config.getXsiPasswd() );
        props.put( "mail.smtp.auth", "true" );
        Session session = Session.getDefaultInstance( props );
        Message simpleMessage = new MimeMessage( session );
        try
        {
            simpleMessage.setFrom( new InternetAddress( from,
                senderName != null && !senderName.isEmpty() ? senderName : config.getMyAddress() ) );
            //add to list
            for ( String address : addressNameMap.keySet() )
            {
                String toName = addressNameMap.get( address ) != null ? addressNameMap.get( address ) : address;
                simpleMessage.addRecipient( Message.RecipientType.TO, new InternetAddress( address, toName ) );
            }
            if ( extras != null )
            {
                //add cc list
                if ( extras.get( CC_ADDRESS_LIST_KEY ) != null )
                {
                    if ( extras.get( CC_ADDRESS_LIST_KEY ) instanceof Map )
                    {
                        TypeUtil<HashMap<String, String>> injector = new TypeUtil<HashMap<String, String>>()
                        {
                        };
                        HashMap<String, String> ccAddressNameMap = injector.inject( extras.get( CC_ADDRESS_LIST_KEY ) );
                        for ( String address : ccAddressNameMap.keySet() )
                        {
                            String toName = ccAddressNameMap.get( address ) != null ? ccAddressNameMap.get( address )
                                                                                   : address;
                            simpleMessage
                                .addRecipient( Message.RecipientType.CC, new InternetAddress( address, toName ) );
                        }
                    }
                    else
                    {
                        log.severe( String.format( "CC list seen but not of Map type: %s",
                            ServerUtils.serializeWithoutException( extras.get( CC_ADDRESS_LIST_KEY ) ) ) );
                    }
                }
                //add bcc list
                if ( extras.get( BCC_ADDRESS_LIST_KEY ) != null )
                {
                    if ( extras.get( BCC_ADDRESS_LIST_KEY ) instanceof Map )
                    {
                        @SuppressWarnings( "unchecked" )
                        Map<String, String> bccAddressNameMap = (Map<String, String>) extras.get( BCC_ADDRESS_LIST_KEY );
                        for ( String address : bccAddressNameMap.keySet() )
                        {
                            String toName = bccAddressNameMap.get( address ) != null ? bccAddressNameMap.get( address )
                                                                                    : address;
                            simpleMessage.addRecipient( Message.RecipientType.BCC,
                                new InternetAddress( address, toName ) );
                        }
                    }
                    else
                    {
                        log.severe( String.format( "BCC list seen but not of Map type: %s",
                            ServerUtils.serializeWithoutException( extras.get( BCC_ADDRESS_LIST_KEY ) ) ) );
                    }
                }
            }
            simpleMessage.setSubject( subject );
            simpleMessage.setText( message );
            //sometimes Transport.send(simpleMessage); is used, but for gmail it's different
            Transport transport = session.getTransport( connectionSettingsArray[0] );
            transport.connect( sendingHost, Integer.parseInt( sendingPort ), config.getXsiUser(), 
                config.getXsiPasswd() );
            transport.sendMessage( simpleMessage, simpleMessage.getAllRecipients() );
            transport.close();
        }
        catch ( MessagingException e )
        {
            e.printStackTrace();
            log.warning( "Failed to send message, because encoding: " + e.getLocalizedMessage() );
        }
        catch ( UnsupportedEncodingException e )
        {
            e.printStackTrace();
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
		return AdapterAgent.ADAPTER_TYPE_EMAIL;
	}
}
