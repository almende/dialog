package com.almende.dialog;

import com.almende.dialog.util.ServerUtils;

public class Settings {
	public static final Object Test = new Object();
	public static final Object Development = new Object();
	public static final Object Production = new Object();
	
	public static Object environment(){
		return Development;
	}
	
//	 public static String KEYSERVER=null;
	// public static String KEYSERVER="http://askanyways.test.rotterdamcs.com/askAnywaysServices";
	public static final String	HOST		= "sandbox.ask-fast.com";
	//public static String		KEYSERVER	= "http://localhost:8080/oauth";
	public static String		KEYSERVER	= "http://localhost:8080/keyserver/token";
	public static String       DIALOG_HANDLER   = "http://localhost:8080/dialoghandler";
        public static String ASK_FAST_MARKETPLACE_REGISTER_URL = "http://askfastmarket.appspot.com";
	
	static {
		if (ServerUtils.isInUnitTestingEnvironment()) {
			KEYSERVER = null;
		}
	}
}
