package com.almende.dialog.util;

import java.net.MalformedURLException;
import java.net.URL;
import javax.servlet.http.HttpServletRequest;

public class RequestUtil {

    public static String getHost(HttpServletRequest req) throws MalformedURLException {

        int port = req.getServerPort();
        if (req.getScheme().equals("http") && port == 80) {
            port = -1;
        }
        else if (req.getScheme().equals("https") && port == 443) {
            port = -1;
        }
        URL serverURL = new URL(req.getScheme(), req.getServerName(), port, "");
        return serverURL.toString();
    }
}
