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

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CM;
import com.almende.dialog.adapter.tools.CMStatus;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

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
        if ( req.getRequestURI().startsWith( "/dialoghandler/sms/cm/deliveryStatus" ) )
        {
            if ( req.getMethod().equals( "POST" ) )
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
                    CMStatus cmStatus = handleDeliveryStatusReport( jb.toString() );
                    res.getWriter().println( ServerUtils.serializeWithoutException( cmStatus ) );
                }
                catch ( Exception e )
                {
                    log.severe( "POST payload retrieval failed. Message: " + e.getLocalizedMessage() );
                    return;
                }
            }
            else if ( req.getMethod().equals( "GET" ) )
            {
                try
                {
                    CMStatus cmStatus = handleDeliveryStatusReport( req.getParameter( "reference" ),
                        req.getParameter( "sent" ), req.getParameter( "received" ), req.getParameter( "to" ),
                        req.getParameter( "statuscode" ), req.getParameter( "errorcode" ),
                        req.getParameter( "errordescription" ) );
                    res.getWriter().println( ServerUtils.serializeWithoutException( cmStatus ) );
                }
                catch ( Exception e )
                {
                    log.severe( "GET query processing failed. Message: " + e.getLocalizedMessage() );
                    return;
                }
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
	
    @Override
    protected void attachIncomingCost( AdapterConfig adapterConfig, String fromAddress ) throws Exception
    {
        // Needs implementation, but service not available at CM
    }

    @Override
    protected void attachOutgoingCost( AdapterConfig adapterConfig, Map<String, String> toAddress, String message ) throws Exception
    {
        //add costs with no.of messages * recipients
        DDRUtils.createDDRRecordOnOutgoingCommunication( adapterConfig, UnitType.PART, toAddress,
            CM.countMessageParts( message ) * toAddress.size() );
    }

	/**
     * handles the status report based on xml payload sent by CM. 
     * used by POST only. See {@link http://docs.cm.nl/http_SR.pdf} for more info.
     * @param payload
     * @return
     */
	private CMStatus handleDeliveryStatusReport( String payload )
    {
        try
        {
            log.info( "payload seen: "+ payload );
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
            
            String reference = referenceNode != null ? referenceNode.getTextContent() : null;
            String sent = sentNode != null ? sentNode.getTextContent() : null;
            String received = receivedNode != null ? receivedNode.getTextContent() : null;
            String to = toNode != null ? toNode.getTextContent() : null;
            String code = codeNode != null ? codeNode.getTextContent() : null;
            String errorCode = errorCodeNode != null ? errorCodeNode.getTextContent() : null;
            String errorDescription = errorDescNode != null ? errorDescNode.getTextContent() : null;
            
            return handleDeliveryStatusReport( reference, sent, received, to, code, errorCode, errorDescription );
        }
        catch ( Exception e )
            {
            log.severe( "Document parse failed. \nMessage: "+ e.getLocalizedMessage() );
        }
        return null;
    }
    
    /**
     * handles the status report based on string values of the parameters sent by CM. 
     * used by both POST and GET method. See {@link http://docs.cm.nl/http_SR.pdf} for more info.
     * @param reference
     * @param sent
     * @param received
     * @param to
     * @param code
     * @param errorCode
     * @param errorDescription
     * @return
     * @throws Exception
     */
    private CMStatus handleDeliveryStatusReport( String reference, String sent, String received, String to,
        String code, String errorCode, String errorDescription ) throws Exception
    {
        log.info( String.format(
            "CM SR: reference: %s, sent: %s, received: %s, to: %s, statusCode: %s errorCode: %s, errorDesc: %s",
            reference, sent, received, to, code, errorCode, errorDescription ) );
        if ( reference != null )
        {
            CMStatus cmStatus = CMStatus.fetch( reference );
            if ( cmStatus == null )
            {
                log.warning( "CM status not found for reference: " + reference );
                cmStatus = new CMStatus();
                cmStatus.setReference( reference );
                String[] referenceArray = reference.split( "_" );
                if ( referenceArray.length == 5 )
                {
                    cmStatus.setAdapterID( referenceArray[1] );
                    cmStatus.setLocalAddress( referenceArray[2] );
                }
            }
            if ( sent != null )
            {
                cmStatus.setSentTimeStamp( sent );
            }
            if ( received != null )
            {
                cmStatus.setDeliveredTimeStamp( received );
            }
            if ( to != null )
            {
                if ( !to.equals( cmStatus.getRemoteAddress() ) )
                {
                    log.warning( "To address dont match between entity and status callback from CM !!" );
                }
                cmStatus.setRemoteAddress( to );
            }
            if ( code != null )
            {
                cmStatus.setCode( code );
            }
            if ( errorCode != null )
            {
                cmStatus.setErrorCode( errorCode );
            }
            if ( errorDescription != null )
            {
                cmStatus.setErrorDescription( errorDescription );
            }
            else if ( errorCode != null && !errorCode.isEmpty() )
            {
                cmStatus.setErrorDescription( erroCodeMapping( Integer.parseInt( errorCode ) ) );
            }
            if ( cmStatus.getCallback() != null && cmStatus.getCallback().startsWith( "http" ) )
            {
                Client client = ParallelInit.getClient();
                WebResource webResource = client.resource( cmStatus.getCallback() );
                try
                {
                    String callbackPayload = ServerUtils.serialize( cmStatus );
                    if(ServerUtils.isInUnitTestingEnvironment())
                    {
                        TestServlet.logForTest( cmStatus );
                    }
                    webResource.type( "text/plain" ).post( String.class, callbackPayload );
                    dialogLog.info(
                        cmStatus.getAdapterID(),
                        String.format( "POST request with payload %s sent to: %s", callbackPayload,
                            cmStatus.getCallback() ) );
                }
                catch ( Exception ex )
                {
                    log.severe( "Callback failed. Message: " + ex.getLocalizedMessage() );
                }
            }
            else
            {
                log.info( "Reference: " + reference + ". No delivered callback found." );
                dialogLog.info( cmStatus.getAdapterID(), "No delivered callback found for reference: " + reference );
            }
            cmStatus.store();
            return cmStatus;
        }
        else
        {
            log.severe( "Reference code cannot be null" );
            return null;
        }
    }
    
    /**
     * gives a mapping of the error code to the error description according to 
     * Section 4. of the http://docs.cm.nl/http_SR.pdf
     * @param errorCode
     * @return
     */
    private String erroCodeMapping( int errorCode )
        {
        String result = null;
        switch ( errorCode )
        {
            case 5:
                result = "The message has been confirmed as undelivered but no detailed information related to the failure is known.";
                break;
            case 7:
                result = "Used to indicate to the client that the message has not yet been delivered due to insufficient subscriber credit but is being retried within the network.";
                break;
            case 8:
                result = "Temporary Used when a message expired (could not be delivered within the life time of the message) within the operator SMSC but is not associated with a reason for failure. ";
                break;
            case 20:
                result = "Used when a message in its current form is undeliverable.";
                break;
            case 21:
                result = "Temporary Only occurs where the operator accepts the message before performing the subscriber credit check. If there is insufficient credit then the operator will retry the message until the subscriber tops up or the message expires. If the message expires and the last failure reason is related to credit then this error code will be used.";
                break;
            case 22:
                result = "Temporary Only occurs where the operator performs the subscriber credit check before accepting the message and rejects messages if there are insufficient funds available.";
                break;
            case 23:
                result = "Used when the message is undeliverable due to an incorrect / invalid / blacklisted / permanently barred MSISDN for this operator. This MSISDN should not be used again for message submissions to this operator.";
                break;
            case 24:
                result = "Used when a message is undeliverable because the subscriber is temporarily absent, e.g. his/her phone is switch off, he/she cannot be located on the network. ";
                break;
            case 25:
                result = "Used when the message has failed due to a temporary condition in the operator network. This could be related to the SS7 layer, SMSC or gateway. ";
                break;
            case 26:
                result = "Used when a message has failed due to a temporary phone related error, e.g. SIM card full, SME busy, memory exceeded etc. This does not mean the phone is unable to receive this type of message/content (refer to error code 27).";
                break;
            case 27:
                result = "Permanent Used when a handset is permanently incompatible or unable to receive this type of message. ";
                break;
            case 28:
                result = "Used if a message fails or is rejected due to suspicion of SPAM on the operator network. This could indicate in some geographies that the operator has no record of the mandatory MO required for an MT. ";
                break;
            case 29:
                result = "Used when this specific content is not permitted on the network / shortcode. ";
                break;
            case 30:
                result = "Used when message fails or is rejected because the subscriber has reached the predetermined spend limit for the current billing period.";
                break;
            case 31:
                result = "Used when the MSISDN is for a valid subscriber on the operator but the message fails or is rejected because the subscriber is unable to be billed, e.g. the subscriber account is suspended (either voluntarily or involuntarily), the subscriber is not enabled for bill-to-phone services, the subscriber is not eligible for bill-to-phone services, etc.";
                break;
            case 33:
                result = "Used when the subscriber cannot receive adult content because of a parental lock. ";
                break;
            case 34:
                result = "Permanent Used when the subscriber cannot receive adult content because they have previously failed the age verification process. ";
                break;
            case 35:
                result = "Temporary Used when the subscriber cannot receive adult content because they have not previously completed age verification. ";
                break;
            case 36:
                result = "Temporary Used when the subscriber cannot receive adult content because a temporary communication error prevents their status being verified on the age verification platform.";
                break;
            case 37:
                result = "The MSISDN is on the national blacklist (currently only for NL: SMS dienstenfilter)";
                break;
            default:
                break;
        }
        return result;
    }
}
