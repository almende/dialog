package com.almende.dialog;

import com.almende.dialog.util.ServerUtils;

public class Settings {

    public static final String HOST="char-a-lot.appspot.com";
    //  public static final String KEYSERVER=null;
    public static String KEYSERVER="http://askanyways.test.rotterdamcs.com/askAnywaysServices";
    //	public static final String HOST="ask-charlotte.appspot.com";
    //	public static final String KEYSERVER="http://www.ask-fast.com/askAnywaysServices";
    static
    {
        if(ServerUtils.isInUnitTestingEnvironment())
        {
            KEYSERVER = null;
        }
    }
}
