package com.almende.dialog;

//import java.util.Date;
import java.util.HashSet;
import java.util.Set;
//import java.util.logging.Logger;

import javax.ws.rs.core.Application;

import com.almende.dialog.proxy.*;
import com.almende.dialog.proxy.agent.*;
import com.almende.tools.ParallelInit;


public class MyApplication extends Application {
//	private static final Logger log = Logger
//			.getLogger("DialogHandler");
	public MyApplication(){
		super();
		ParallelInit.startThreads();
	}
	
	public Set<Class<?>> getClasses() {
		return null;
	}
	public Set<Object> getSingletons(){
//		log.warning("getSingletons() called:"+new Date().getTime());
		Set<Object> result = new HashSet<Object>(8);
		result.add(new CalendarConversation());
		result.add(new Charlotte());
		result.add(new HowIsTheWeatherRESTAgent());
		result.add(new Kastje());
		result.add(new Muur());
		result.add(new PassAlong());
		result.add(new CCXMLProxy());
		result.add(new VoiceXMLProxy());
//		log.warning("getSingletons() returned:"+new Date().getTime());		
		return result;
	}
}
