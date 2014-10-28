package com.almende.dialog.model;


import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;


public class MediaProperty
{
    public enum MediaPropertyKey
    {
        /**
         * defines the timeout associated with the call
         */
        TIMEOUT,
        /**
         * defines if the answer is given via dtmf, text etc
         */
        ANSWER_INPUT,
        /**
         * defines the length of th answer input. Typically dtmf
         */
        ANSWER_INPUT_MIN_LENGTH,
        ANSWER_INPUT_MAX_LENGTH,
        /**
         * defines a subtype for the question type. <br>
         * E.g. open question with type: audio refers to voicemail
         */
        TYPE,
        /**
         * defines the length of the voicemail to be recorded
         */
        VOICE_MESSAGE_LENGTH, 
        /**
         * defines the number of times the question should repeat in case of a wrong answer input.
         * works only for phonecalls so as to end a call with repeated input errors.
         */ 
        RETRY_LIMIT,
        /**
         * boolean flag to indicate if a voice mssage start should be indicated by a beep or not
         */
        VOICE_MESSAGE_BEEP,
        /**
         * boolean flag to indicate if the call should terminate when a dtmf is pressed
         */
        DTMF_TERMINATE,
        /**
         * defines the speed at which the TTS is spoken by the TTS engine
         */
        TSS_SPEED,
        /**
         * defines which caller id should be when redirecting a call
         */
        USE_EXTERNAL_CALLERID,
        /**
         * Pre-connect message. Gives opertunity to play a message before connecting the redirected call
         */
        PRE_CONNECT;
        
        @JsonCreator
        public static MediaPropertyKey fromJson(String name) {
            return valueOf(name.toUpperCase());
        }
    }

    public enum MediumType
    {
        BROADSOFT, GTALK, SKYPE, SMS, TWITTER, CALL;
        
        @JsonCreator
        public static MediumType fromJson(String name) {
            return valueOf(name.toUpperCase());
        }
    }
    
    private MediumType medium;
    private Map<MediaPropertyKey, String> properties;
    
    public MediaProperty() {
        properties = new HashMap<MediaPropertyKey, String>();
    }
    

    public MediumType getMedium()
    {
        return medium;
    }

    public void setMedium( MediumType medium )
    {
        this.medium = medium;
    }

    public Map<MediaPropertyKey, String> getProperties()
    {
        return properties;
    }

    public void addProperty( MediaPropertyKey key, String value )
    {
        properties.put( key, value );
    }
}