package com.almende.dialog.adapter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CM;
import com.almende.dialog.adapter.tools.CMStatus;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.thetransactioncompany.cors.HTTPMethod;

public class CMSmsServlet extends TextServlet {

	private static final long serialVersionUID = 408503132941968804L;
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final String servletPath = "/_ah/sms/";
	private static final String adapterType = "CM";
	
	@Override
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, Map<String, Object> extras, AdapterConfig config) throws Exception {
		
		String[] tokens = config.getAccessToken().split("\\|");
		CM cm = new CM(tokens[0], tokens[1], config.getAccessTokenSecret());
		return cm.sendMessage(message, subject, from, fromName, to, toName, extras, config);
	}
	
    @Override
    protected int broadcastMessage( String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config ) throws Exception
    {
        String[] tokens = config.getAccessToken().split( "\\|" );

        CM cm = new CM( tokens[0], tokens[1], config.getAccessTokenSecret() );
        return cm.broadcastMessage( message, subject, from, senderName, addressNameMap, extras, config );
    }
    
    @Override
    public void service( HttpServletRequest req, HttpServletResponse res ) throws IOException
    {
        if ( req.getRequestURI().endsWith( "/sms/cm/deliveryStatus" ) && req.getMethod().equals( "POST" ) )
        {
            StringBuffer jb = new StringBuffer();
            String line = null;
            try
            {
                BufferedReader reader = req.getReader();
                while ( ( line = reader.readLine() ) != null )
                {
                    jb.append( line );
                }
                handleDeliveryStatusReport(jb.toString());
            }
            catch ( Exception e )
            {
                log.severe( "POST payload retrieval failed. Message: " + e.getLocalizedMessage() );
                return;
            }
        }
        else
        {
            super.service( req, res );
        }
    }

    @Override
    protected void doPost( HttpServletRequest req, HttpServletResponse resp ) throws ServletException, IOException
    {
        super.doPost( req, resp );
    }

    @Override
	protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp)
			throws Exception {
		// TODO: Needs implementation, but service not available at CM
		return null;
	}

	@Override
	protected String getServletPath() {
		return servletPath;
	}

	@Override
	protected String getAdapterType() {
		return adapterType;
	}

	@Override
	protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {}
	
	private CMStatus handleDeliveryStatusReport( String payload )
    {
        try
        {
            DocumentBuilderFactory newInstance = DocumentBuilderFactory.newInstance();
            DocumentBuilder newDocumentBuilder = newInstance.newDocumentBuilder();
            Document parse = newDocumentBuilder.parse( new ByteArrayInputStream(payload.getBytes("UTF-8")) );
            Node sentNode = parse.getElementsByTagName( "MESSAGES" ).item( 0 ).getAttributes().getNamedItem("SENT" );
            Node receivedNode = parse.getElementsByTagName( "MSG" ).item( 0 ).getAttributes().getNamedItem("RECEIVED" );
            Node toNode = parse.getElementsByTagName( "TO" ).item(0 );
            Node referenceNode = parse.getElementsByTagName( "REFERENCE" ).item(0 );
            Node codeNode = parse.getElementsByTagName( "CODE" ).item(0 );
            Node errorCodeNode = parse.getElementsByTagName( "ERRORCODE" ).item(0 );
            Node errorDescNode = parse.getElementsByTagName( "ERRORDESCRIPTION" ).item(0 );
            if ( referenceNode != null )
            {
                CMStatus cmStatus = CMStatus.fetch( referenceNode.getTextContent() );
                if ( cmStatus == null )
                {
                    log.warning( "CM status not found for reference: "+ referenceNode.getTextContent() );
                    cmStatus = new CMStatus();
                    String[] referenceArray = referenceNode.getTextContent().split( "_" );
                    if(referenceArray.length == 5)
                    {
                        cmStatus.setAdapterID( referenceArray[1] );
                        cmStatus.setLocalAddress( referenceArray[2] );
                    }
                }
                if ( sentNode != null )
                {
                    cmStatus.setSentTimeStamp( sentNode.getTextContent() );
                }
                if ( receivedNode != null )
                {
                    cmStatus.setDeliveredTimeStamp( receivedNode.getTextContent() );
                }
                if ( toNode != null )
                {
                    if ( !toNode.getTextContent().equals( cmStatus.getRemoteAddress() ) )
                    {
                        log.warning( "To address dont match between entity and status callback from CM !!" );
                    }
                    cmStatus.setRemoteAddress( toNode.getTextContent() );
                }
                if ( codeNode != null )
                {
                    cmStatus.setCode( codeNode.getTextContent() );
                }
                if ( errorCodeNode != null )
                {
                    cmStatus.setErrorCode( errorCodeNode.getTextContent() );
                }
                if ( errorDescNode != null )
                {
                    cmStatus.setErrorDescription( errorDescNode.getTextContent() );
                }
                if ( cmStatus.getCallback() != null && cmStatus.getCallback().startsWith( "http" ))
                {
                    if ( !ServerUtils.isInUnitTestingEnvironment() )
                    {
                        Client client = ParallelInit.getClient();
                        WebResource webResource = client.resource( cmStatus.getCallback() );
                        try
                        {
                            String callbackPayload = ServerUtils.serialize( cmStatus );
                            webResource.type( "text/plain" ).post( String.class, callbackPayload );
                            dialogLog.debug( cmStatus.getAdapterID(), String.format(
                                "POST request with payload %s sent to: %s", callbackPayload, cmStatus.getCallback() ) );
                        }
                        catch ( Exception ex )
                        {
                            log.severe( "Callback failed. Message: " + ex.getLocalizedMessage() );
                        }
                    }
                    else
                    {
                        TestFramework.fetchResponse( HTTPMethod.POST, cmStatus.getCallback(),
                            ServerUtils.serialize( cmStatus ) );
                    }
                }
                cmStatus.store();
                return cmStatus;
            }
            else
            {
                log.severe( "Reference code cannot be null" );
            }
        }
        catch ( Exception e )
        {
            log.severe( "Document parse failed. \nMessage: "+ e.getLocalizedMessage() );
        }
        return null;
    }
}
