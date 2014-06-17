package com.almende.dialog.adapter;


import org.junit.Test;

import com.almende.dialog.TestFramework;
import com.almende.dialog.adapter.tools.CLXUSSD;

public class CLXUSSTest extends TestFramework{
	private static final String message = "How are you doing?";

	@Test
	public void outBoundBroadcastCallSenderNameNotNullTest() throws Exception {
		String remoteAdressUSSD = "31624107792";
		String senderName ="AskFastTest";
		String subject = "hallo";
		
		CLXUSSD clxussd = new CLXUSSD();
		clxussd.sendMessage(message, subject, subject, subject, remoteAdressUSSD, senderName,null,null);
	}
	/*
	@Test
	public void outBoundBroadcastCallToMultipleRecipientsSenderNameNotNullTest() throws Exception {
		String remoteAdressUSSD = "31624107792";
		String senderName ="AskFastTest";
		String subject = "hallo";
		
		CLXUSSD clxussd = new CLXUSSD("ASKFastBV_h_ugw0","qMA3gBY5",null);
		Map<String, String> adressMap = new HashMap<String, String>();
		adressMap.put("Vincent", remoteAdressUSSD);
		adressMap.put("vincent1", remoteAdressUSSD);
		clxussd.sendMessage(message, subject, "ask fast test", senderName, adressMap, null, null);
	}*/
}
