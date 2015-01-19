package com.almende.util;

import com.almende.dialog.util.AFHttpClient;

public class AFHttpClientConThread extends Thread implements java.lang.Runnable {

    @Override
    public void run() {
        
        ParallelInit.afhttpClient = new AFHttpClient();
        ParallelInit.afclientActive = true;
    }
}
