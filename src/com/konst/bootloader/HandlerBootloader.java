package com.konst.bootloader;

import android.os.Handler;

/*
 * Created by Kostya on 15.05.2015.
 */
public class HandlerBootloader extends Handler {

    public enum Result{
        MSG_LOG,
        MSG_CLOSE_DIALOG,
        MSG_UPDATE_DIALOG,
        MSG_SHOW_DIALOG
    }

}
