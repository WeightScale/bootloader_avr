package com.konst.bootloader;


import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;

//import com.kostya.weightcheckadmin.Utility;

/**
 * Created with IntelliJ IDEA.
 * User: Kostya
 * Date: 23.12.13
 * Time: 11:28
 * To change this template use File | Settings | File Templates.
 */
class AVRDevice {
    private final HandlerBootloader handler;
    final InputStream inputStreamFile;
    private int flashSize;                                                              // Size of Flash memory in bytes.
    private int eepromSize;                                                             // Size of EEPROM memory in bytes.
    private int signature0;
    private int signature1;
    private int signature2;                                                             // The three signature bytes, read from XML PartDescriptionFiles.
    private int pageSize;                                                               // Flash page size.

    /* Constructor */
    AVRDevice(InputStream inputStreamFile, HandlerBootloader _handler) throws Exception {
        handler = _handler;
        this.inputStreamFile = inputStreamFile;
        flashSize = eepromSize = 0;
        //signature0 = signature1 = signature2 = 0;
        pageSize = -1;
        readParametersFromAVRStudio();
    }

    /* Methods */
    private void readParametersFromAVRStudio() throws Exception {
        Utility Util = new Utility();
        XmlPullParser xpp = XmlPullParserFactory.newInstance().newPullParser();

        flashSize = Integer.parseInt(getValue(xpp, "PROG_FLASH"));
        eepromSize = Integer.parseInt(getValue(xpp, "EEPROM"));

        if (exists(xpp, "BOOT_CONFIG")) {
            pageSize = Integer.parseInt(getValue(xpp, "PAGESIZE"));
            pageSize <<= 1; // We want pagesize in bytes.
        }

        signature0 = Util.convertHex(new StringBuilder(getValue(xpp, "ADDR000")).deleteCharAt(0).toString());
        signature1 = Util.convertHex(new StringBuilder(getValue(xpp, "ADDR001")).deleteCharAt(0).toString());
        signature2 = Util.convertHex(new StringBuilder(getValue(xpp, "ADDR002")).deleteCharAt(0).toString());

        handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "Saving cached XML parameters...").sendToTarget();
    }

    private String getValue(XmlPullParser xpp, String tag) throws IOException, XmlPullParserException {
        inputStreamFile.reset();
        xpp.setInput(inputStreamFile, null);
        int eventType = xpp.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (xpp.getName().equals(tag)) {
                    return xpp.nextText();
                }
            }
            eventType = xpp.next();
        }
        return "";
    }

    private boolean exists(XmlPullParser xpp, String tag) throws IOException, XmlPullParserException {
        inputStreamFile.reset();
        xpp.setInput(inputStreamFile, null);
        int eventType = xpp.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if (xpp.getName().equals(tag)) {
                    return true;
                }
            }
            eventType = xpp.next();
        }
        return false;
    }

    protected int getFlashSize() {
        return flashSize;
    }

    protected int getEEPROMSize() {
        return eepromSize;
    }

    protected long getPageSize() {
        return pageSize;
    }

    private long getSignature0() {
        return signature0;
    }

    private long getSignature1() {
        return signature1;
    }

    private long getSignature2() {
        return signature2;
    }

}
