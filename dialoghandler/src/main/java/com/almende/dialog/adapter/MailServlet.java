package com.almende.dialog.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
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
import com.almende.dialog.state.StringStore;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.TypeUtil;


public class MailServlet extends TextServlet implements Runnable {

    public static final String GMAIL_SENDING_PORT = "465";
    public static final String GMAIL_SENDING_HOST = "smtp.gmail.com";
    public static final String GMAIL_SENDING_PROTOCOL = "smtps";
    public static final String GMAIL_RECEIVING_HOST = "imap.gmail.com";
    public static final String GMAIL_RECEIVING_PROTOCOL = "imaps";
    public static final String CC_ADDRESS_LIST_KEY = "cc_email";
    public static final String BCC_ADDRESS_LIST_KEY = "bcc_email";
	private static final long serialVersionUID = 6892283600126803780L;
	private static final String servletPath = "/_ah/mail/";
	
	public void doErrorPost(HttpServletRequest req, HttpServletResponse res) {}
	
	private AdapterConfig adapterConfig = null;
	public MailServlet()
	{
	    
	}
	
	public MailServlet( AdapterConfig adapterConfig )
    {
	    this.adapterConfig = adapterConfig;
    }
	
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
        Map<String, Object> extras, AdapterConfig config ) throws Exception
    {
        HashMap<String, String> addressNameMap = new HashMap<>( 1 );
        addressNameMap.put( to, toName );
        return broadcastMessage( message, subject, from, fromName, addressNameMap, extras, config );
    }
	
    @Override
    protected int broadcastMessage( String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config ) throws Exception
    {
        //xsiURL is of the form <email protocol>: <sending host>: <sending port>
        final String sendingConnectionSettings = config.getXsiURL() != null && !config.getXsiURL().isEmpty() ? config
            .getXsiURL().split( "\n" )[0] : GMAIL_SENDING_PROTOCOL + ":" + GMAIL_SENDING_HOST + ":"
            + GMAIL_SENDING_PORT; 
        String[] connectionSettingsArray = sendingConnectionSettings.split( ":" );
        String sendingHost = connectionSettingsArray.length == 3 ? connectionSettingsArray[1] : GMAIL_SENDING_HOST;
        String sendingPort = connectionSettingsArray.length == 3 ? connectionSettingsArray[2] : GMAIL_SENDING_PORT;
        String sendingProtocol = connectionSettingsArray[0] != null ? connectionSettingsArray[0]
                                                                   : GMAIL_SENDING_PROTOCOL; 
        final String username = config.getXsiUser();
        final String password = config.getXsiPasswd();
        Properties props = new Properties();
        props.put( "mail.smtp.host", sendingHost );
        props.put( "mail.smtp.port", sendingPort );
        props.put( "mail.smtp.user", username );
        props.put( "mail.smtp.password", password );
        props.put( "mail.smtp.auth", "true" );
        Session session = Session.getDefaultInstance( props );
        Message simpleMessage = new MimeMessage( session );
        try
        {
            log.info( String.format(
                "sending email from: %s senderName: %s to: %s with params: host: %s port: %s user: %s ", from,
                senderName, ServerUtils.serialize( addressNameMap ), sendingHost, sendingPort, username ) );
            simpleMessage.setFrom( new InternetAddress( from,
                senderName != null && !senderName.isEmpty() ? senderName : config.getAddress() ) );
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
            Transport transport = session.getTransport( sendingProtocol );
            transport.connect( sendingHost, Integer.parseInt( sendingPort ), username, password );
            transport.sendMessage( simpleMessage, simpleMessage.getAllRecipients() );
            transport.close();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            log.warning( "Failed to send message, because encoding: " + e.getLocalizedMessage() );
            throw e;
        }
        return 1;
    }
    
    public void readInboundEmail( AdapterConfig adapterConfig )
    {
        if ( adapterConfig.getInitialAgentURL() != null && !adapterConfig.getInitialAgentURL().isEmpty() )
        {
            final String receivingConnectionSettings = adapterConfig.getXsiURL() != null
                && adapterConfig.getXsiURL().split( "\n" ).length > 1 ? adapterConfig.getXsiURL().split( "\n" )[1]
                                                                     : GMAIL_RECEIVING_PROTOCOL + ":"
                                                                         + GMAIL_RECEIVING_HOST;
            String[] connectionSettingsArray = receivingConnectionSettings.split( ":" );
            String receivingProtocol = connectionSettingsArray[0] != null ? connectionSettingsArray[0]
                                                                         : GMAIL_RECEIVING_PROTOCOL;
            String receivingHost = connectionSettingsArray.length == 3 ? connectionSettingsArray[1]
                                                                      : GMAIL_RECEIVING_HOST;

            final String username = adapterConfig.getXsiUser();
            final String password = adapterConfig.getXsiPasswd();
            Properties props = new Properties();
            props.setProperty( "mail.store.protocol", receivingProtocol );
            Session session = Session.getDefaultInstance(props, null);
            try
            {
                Store store = session.getStore( receivingProtocol );
                store.connect( receivingHost, username, password );
                Folder folder = store.getFolder( "INBOX" );
                folder.open( Folder.READ_ONLY );
                Message message[] = folder.getMessages();
                String lastEmailTimestamp = StringStore.getString( "lastEmailRead_" + adapterConfig.getConfigId() );
                String updatedLastEmailTimestamp = null;
                for ( int i = 0; i < message.length; i++ )
                {
                    InternetAddress fromAddress = ( (InternetAddress) message[i].getFrom()[0] );
                    //skip if the address contains no-reply as its address
                    if ( lastEmailTimestamp == null
                        || Long.parseLong( lastEmailTimestamp ) < message[i].getReceivedDate().getTime()
                        && !fromAddress.toString().contains( "no-reply" )
                        && !fromAddress.toString().contains( "noreply" ) )
                    {
                        try
                        {
                            MimeMessage mimeMessage = new MimeMessage( session, message[i].getInputStream() );
                            mimeMessage.setFrom( fromAddress );
                            mimeMessage.setSubject( message[i].getSubject() );
                            mimeMessage.setContent( message[i].getContent(), message[i].getContentType() );
                            TextMessage receiveMessage = receiveMessage( mimeMessage, adapterConfig.getMyAddress() );
                            processMessage( receiveMessage );
                        }
                        catch ( Exception e )
                        {
                            log.warning( String.format(
                                "Adapter: %s of type: %s threw exception: %s while reading inboundEmail scedule",
                                adapterConfig.getConfigId(), adapterConfig.getAdapterType(), e.getLocalizedMessage() ) );
                        }
                    }
                    updatedLastEmailTimestamp = String.valueOf( message[i].getReceivedDate().getTime() );
                }
                folder.close( true );
                store.close();
                if(updatedLastEmailTimestamp != null && updatedLastEmailTimestamp != lastEmailTimestamp)
                {
                    StringStore.storeString( "lastEmailRead_"+ adapterConfig.getConfigId(), lastEmailTimestamp );
                }
            }
            catch ( Exception e )
            {
                log.warning( String.format(
                    "Adapter: %s of type: %s threw exception: %s while reading inboundEmail scedule",
                    adapterConfig.getConfigId(), adapterConfig.getAdapterType(), e.getLocalizedMessage() ) );
            }
        }
    }
    
	@Override
	protected String getServletPath() {
		return servletPath;
	}
	
	@Override
	protected String getAdapterType() {
		return AdapterAgent.ADAPTER_TYPE_EMAIL;
	}

    @Override
    public void run()
    {
        readInboundEmail( adapterConfig );
    }
}
