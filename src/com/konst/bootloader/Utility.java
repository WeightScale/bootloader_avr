package com.konst.bootloader;

/*
 * Created with IntelliJ IDEA.
 * User: Kostya
 * Date: 23.12.13
 * Time: 14:58
 * To change this template use File | Settings | File Templates.
 */
class Utility {

    /* Methods */
    int convertHex(final String txt) throws Exception {

        if (txt.isEmpty()) {
            throw new Exception("Cannot convert zero-length hex-string to number!");
        }

        if (txt.length() > 8) {
            throw new Exception("Hex conversion overflow! Too many hex digits in string.");
        }


        int result = 0;
        for (int i = 0; i < txt.length(); i++) {
            /* Convert hex digit */
            int digit;
            if (txt.charAt(i) >= '0' && txt.charAt(i) <= '9') {
                digit = (int) txt.charAt(i) - (int) '0';
            } else if (txt.charAt(i) >= 'a' && txt.charAt(i) <= 'f') {
                digit = (int) txt.charAt(i) - (int) 'a' + 10;
            } else if (txt.charAt(i) >= 'A' && txt.charAt(i) <= 'F') {
                digit = (int) txt.charAt(i) - (int) 'A' + 10;
            } else {
                throw new Exception("Invalid hex digit found!");
            }
            /* Add digit as least significant 4 bits of result */
            result = result << 4 | digit;
        }

        return result;
    }
}
