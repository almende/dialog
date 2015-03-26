package com.almende.util;

import com.almende.dialog.aws.AWSClient;

public class AWSThread extends Thread {

    @Override
    public void run() {
        ParallelInit.awsClient = new AWSClient();
        ParallelInit.awsClientActive = true;
    }
}
