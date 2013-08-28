package com.almende.dialog.model;


import java.util.HashMap;
import java.util.Map;


public class MediaProperty
{
    public enum MediaPropertyKey
    {
        RedirectTimeOut( "timeout" ), AnswerInput( "answer_input" ), Length( "length" );

        @SuppressWarnings("unused")
		private String name;

        private MediaPropertyKey( String name )
        {
            this.name = name;
        }
    }

    private MediumType medium;
    private Map<MediaPropertyKey, String> hints;

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
        return hints;
    }

    public void addProperty( MediaPropertyKey key, String value )
    {
        hints = hints != null ? hints : new HashMap<MediaPropertyKey, String>();
        hints.put( key, value );
    }
}