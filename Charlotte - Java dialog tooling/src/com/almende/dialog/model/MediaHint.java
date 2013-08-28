package com.almende.dialog.model;


import java.util.HashMap;
import java.util.Map;


public class MediaHint
{
    public enum MediaHintKey
    {
        RedirectTimeOut( "timeout" ), AnswerInput( "answer_input" ), Length( "length" );

        private String name;

        private MediaHintKey( String name )
        {
            this.name = name;
        }
    }

    private MediumType medium;
    private Map<MediaHintKey, String> hints;

    public MediumType getMedium()
    {
        return medium;
    }

    public void setMedium( MediumType medium )
    {
        this.medium = medium;
    }

    public Map<MediaHintKey, String> getHints()
    {
        return hints;
    }

    public void addHint( MediaHintKey key, String value )
    {
        hints = hints != null ? hints : new HashMap<MediaHintKey, String>();
        hints.put( key, value );
    }
}