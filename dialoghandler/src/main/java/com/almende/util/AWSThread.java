package com.almende.util;

import com.almende.dialog.Settings;
import com.almende.dialog.aws.AWSClient;

public class AWSThread extends Thread {

    @Override
    public void run() {
        ParallelInit.awsClient = new AWSClient();
        ParallelInit.awsClient.init( Settings.BUCKET_NAME, Settings.AWS_ACCESS_KEY, Settings.AWS_ACCESS_KEY_SECRET );
        ParallelInit.awsClientActive = true;
    }
}
