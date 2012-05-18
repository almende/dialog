package com.almende.dialog;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;
import com.sun.speech.freetts.audio.NullAudioPlayer;
import com.sun.speech.freetts.util.Utilities;

import java.io.IOException;

public class HelloWorldTTSServlet extends HttpServlet {
	private static final long serialVersionUID = 4900824842342385665L;
	private Voice voice16k;
	private String voice16kName = Utilities.getProperty
		("voice16kName", "kevin16");
    
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		try {
            VoiceManager voiceManager = VoiceManager.getInstance();
    	    voice16k = voiceManager.getVoice(voice16kName);
    	    voice16k.allocate();
   
    	    voice16k.setAudioPlayer(new NullAudioPlayer());
    	    voice16k.speak("Hello World");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
