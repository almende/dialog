package com.almende.dialog.adapter.tools;

import java.io.Serializable;
import java.util.logging.Logger;

import com.almende.dialog.util.ServerUtils;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.annotations.Id;


public class CMStatus implements Serializable
{
    private static final long serialVersionUID = 3674394844170200281L;
    private static final Logger log = Logger.getLogger( CMStatus.class.getSimpleName() );
    
    @Id
    public String reference;
    private String sms = "";
    private String adapterID = "";
    private String callback = "";
    private String remoteAddress = "";
    private String localAddress = "";
    private String sentTimeStamp = "";
    private String deliveredTimeStamp = "";
    private String code = "";
    private String errorCode = "";
    private String errorDescription = "";
    
    public void store()
    {
        if ( reference != null )
        {
        	TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
            sentTimeStamp = sentTimeStamp == null ? ServerUtils.getServerCurrentTime().toString() : sentTimeStamp;
            datastore.storeOrUpdate( this );
            log.info( "CM status saved: " + ServerUtils.serializeWithoutException( this ) );
        }
    }
    
    public static CMStatus fetch(String reference)
    {
    	TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.load( CMStatus.class, reference );
    }
    
    public String getReference()
    {
        return reference;
    }

    public void setReference( String reference )
    {
        this.reference = reference;
    }

    public String getSentTimeStamp()
    {
        return sentTimeStamp;
    }

    public void setSentTimeStamp( String sentTimeStamp )
    {
        this.sentTimeStamp = sentTimeStamp;
    }

    public String getDeliveredTimeStamp()
    {
        return deliveredTimeStamp;
    }

    public void setDeliveredTimeStamp( String deliveredTimeStamp )
    {
        this.deliveredTimeStamp = deliveredTimeStamp;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode( String code )
    {
        this.code = code;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public void setErrorCode( String errorCode )
    {
        this.errorCode = errorCode;
    }

    public String getErrorDescription()
    {
        return errorDescription;
    }

    public void setErrorDescription( String errorDescription )
    {
        this.errorDescription = errorDescription;
    }

    public String getSms()
    {
        return sms;
    }

    public void setSms( String sms )
    {
        this.sms = sms;
    }

    public String getAdapterID()
    {
        return adapterID;
    }

    public void setAdapterID( String adapterID )
    {
        this.adapterID = adapterID;
    }

    public String getCallback()
    {
        return callback;
    }

    public void setCallback( String callback )
    {
        this.callback = callback;
    }

    public String getRemoteAddress()
    {
        return remoteAddress;
    }

    public void setRemoteAddress( String remoteAddress )
    {
        this.remoteAddress = remoteAddress;
    }

    public String getLocalAddress()
    {
        return localAddress;
    }

    public void setLocalAddress( String localAddress )
    {
        this.localAddress = localAddress;
    }
}
