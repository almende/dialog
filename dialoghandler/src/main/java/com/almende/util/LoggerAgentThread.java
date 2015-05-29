package com.almende.util;

import java.io.File;
import java.io.FileInputStream;
import com.almende.dialog.agent.LoggerProxyAgent;
import com.askfast.commons.eve.EveUtil;

/**
 * Logger thread for giving a singelton instance of the LoggerAgent
 * 
 * @author shravan
 *
 */
public class LoggerAgentThread extends Thread {

    public static final String HTTP_LOGGER_EVE_PATH = "eve_logger.yaml";
    
    @Override
    public void run() {

        try {
            if (ParallelInit.loggerAgent == null) {
                ParallelInit.loggerAgent = initAgent();
            }
            ParallelInit.loggerAgentActive = true;
        }
        catch (Exception e) {
            e.printStackTrace();
            ParallelInit.loggerAgentActive = false;
        }
    }
    
    private LoggerProxyAgent initAgent() throws Exception {

        String eveLoggerPath = System.getProperty("user.dir") + "/src/main/webapp/WEB-INF/" + HTTP_LOGGER_EVE_PATH;
        FileInputStream fileInputStream = null;
        LoggerProxyAgent agent = null;
        try {
            fileInputStream = new FileInputStream(new File(eveLoggerPath));
            if (fileInputStream != null) {
                agent = (LoggerProxyAgent) EveUtil.loadAgent(fileInputStream);
            }
            return agent;
        }
        finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
        }
    }
}
