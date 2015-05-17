package com.konst.bootloader;

import java.io.InputStream;

/**
 * Created with IntelliJ IDEA.
 * User: Kostya
 * Date: 23.12.13
 * Time: 16:56
 * To change this template use File | Settings | File Templates.
 */
public abstract class AVRProgrammer {
    private final HandlerBootloader handler;                        // для сообщений
    private AVRDevice avrDevice;                                    // микроконтроллер
    private HEXFile hexFile;                                        // фаил прошивки
    private long pagesize;                                          // Flash page size.
    private int flashStartAddress;                                  // Limit Flash operations, -1 if not.
    private int flashEndAddress = -1;                               // ...to this address, inclusive, -1 if not.
    private int eepromEndAddress = -1;


    /* Constructor */
    public AVRProgrammer(/*Module module,*/ HandlerBootloader _handler) {
        //this.module = module;
        handler = _handler;
    }

    public abstract void sendByte(byte ch);     // посылаем байт на устройство
    public abstract int getByte();              // принимаем байт с устройства

    /* Methods */
    private void setPagesize(long _pagesize) {
        pagesize = _pagesize;
    }

    private boolean chipErase() throws Exception {
        /* Send command 'e' */
        sendByte((byte) 'e');
        /* Should return CR */
        if (getByte() != '\r') {
            throw new Exception("Chip erase failed! Programmer did not return CR after 'e'-command.");
        }
        return true; // Indicate supported command.
    }

    private void readSignature(Integer... sig) {
        /* Send command 's' */
        sendByte((byte) 's');
	    /* Get actual signature */
        sig[2] = getByte();
        sig[1] = getByte();
        sig[0] = getByte();
    }

    private byte readPartCode() {
	    /* Send command 't' */
        sendByte((byte) 't');
        return (byte) getByte();
    }

    public boolean checkSignature(long sig0, long sig1, long sig2) throws Exception {
        Integer[] sig = new Integer[3];
	    /* Get signature */
        readSignature(sig);
	    /* Compare signature */
        if (sig[0] != sig0 || sig[1] != sig1 || sig[2] != sig2) {
            throw new Exception("Signature does not match selected device! ");
        }
        return true; // Indicate supported command.
    }

    private void writeFlashPage() throws Exception {
        sendByte((byte) 'm');

        if (getByte() != '\r') {
            throw new Exception("Writing Flash page failed! " + "Programmer did not return CR after 'm'-command.");
        }
    }

    private boolean writeFlash(HEXFile data) throws Exception {

	    /* Check that pagesize is set */
        if (pagesize == -1) {
            throw new Exception("Programmer pagesize is not set!");
        }

	    /* Check block write support */
        sendByte((byte) 'b');

        if (getByte() == 'Y') {
            handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "Using block mode...").sendToTarget();
            return writeFlashBlock(data); // Finished writing.
        }

	    /* Get range from HEX file */
        int start = data.getRangeStart();
        int end = data.getRangeEnd(); // Data address range.

	    /* Check autoincrement support */
        sendByte((byte) 'a');

        boolean autoincrement = getByte() == 'Y';                                                                // Bootloader supports address autoincrement?

	    /* Set initial address */
        setAddress(start >> 1); // Flash operations use word addresses.

	    /* Need to write one odd byte first? */
        int address = start;
        if ((address & 1) == 1) {
		    /* Use only high byte */
            writeFlashLowByte((byte) 0xff); // No-write in low byte.
            writeFlashHighByte(data.getData(address));
            address++;

		    /* Need to write page? */
            if (address % pagesize == 0 || address > end) {// Just passed page limit or no more bytes to write?

                setAddress(address - 2 >> 1); // Set to an address inside the page.
                writeFlashPage();
                setAddress(address >> 1);
            }
        }

	    /* Write words */
        while (end - address + 1 >= 2) {// More words left?

		    /* Need to set address again? */
            if (!autoincrement) {
                setAddress(address >> 1);
            }

		    /* Write words */
            writeFlashLowByte(data.getData(address));
            writeFlashHighByte(data.getData(address + 1));
            address += 2;

            /*if( address % MEM_PROGRESS_GRANULARITY == 0 )
                handler.sendMessage(handler.obtainMessage(ActivityBootloader.MSG_LOG,"#"));*/

		    /* Need to write page? */
            if (address % pagesize == 0 || address > end) {// Just passed a page limit or no more bytes to write?

                setAddress(address - 2 >> 1); // Set to an address inside the page.
                writeFlashPage();
                setAddress(address >> 1);
            }
        }

	    /* Need to write one even byte before finished? */
        if (address == end) {
		    /* Use only low byte */
            writeFlashLowByte(data.getData(address));
            writeFlashHighByte((byte) 0xff); // No-write in high byte.
            address += 2;

		    /* Write page */
            setAddress(address - 2 >> 1); // Set to an address inside the page.
            writeFlashPage();
        }

        handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "").sendToTarget();
        return true; // Indicate supported command.
    }

    private void writeFlashHighByte(byte value) throws Exception {
        sendByte((byte) 'C');
        sendByte(value);

        if (getByte() != '\r') {
            throw new Exception("Writing Flash high byte failed! " + "Programmer did not return CR after 'C'-command.");
        }
    }

    private void writeFlashLowByte(byte value) throws Exception {
        sendByte((byte) 'c');
        sendByte(value);

        if (getByte() != '\r') {
            throw new Exception("Writing Flash low byte failed! " + "Programmer did not return CR after 'c'-command.");
        }
    }

    private boolean writeFlashBlock(HEXFile data) throws Exception {

	    /* Get block size, assuming command 'b' just issued and 'Y' has been read */
        int blockSize = getByte() << 8 | getByte(); // Bootloader block size.

	    /* Get range from HEX file */
        int start = data.getRangeStart();
        int end = data.getRangeEnd(); // Data address range.

	    /* Need to write one odd byte first? */
        int address = start;
        if ((address & 1) == 1) {
            setAddress(address >> 1); // Flash operations use word addresses.

		    /* Use only high byte */
            writeFlashLowByte((byte) 0xff); // No-write in low byte.
            writeFlashHighByte(data.getData(address));
            address++;

		    /* Need to write page? */
            if (address % pagesize == 0 || address > end) {// Just passed page limit or no more bytes to write?

                setAddress(address - 2 >> 1); // Set to an address inside the page.
                writeFlashPage();
                setAddress(address >> 1);
            }
        }

	    /* Need to write from middle to end of block first? */
        int byteCount;
        if (address % blockSize > 0) {// In the middle of a block?

            byteCount = blockSize - address % blockSize; // Bytes left in block.

            if (address + byteCount - 1 > end) {// Is that past the write range?

                byteCount = end - address + 1; // Bytes left in write range.
                byteCount &= ~0x01; // Adjust to word count.
            }

            if (byteCount > 0) {
                setAddress(address >> 1); // Flash operations use word addresses.

			    /* Start Flash block write */
                sendByte((byte) 'B');
                sendByte((byte) (byteCount >> 8)); // Size, MSB first.
                sendByte((byte) byteCount);
                sendByte((byte) 'F'); // Flash memory.

                while (byteCount > 0) {

                    sendByte(data.getData(address));
                    address++;
                    byteCount--;
                }

                if (getByte() != '\r') {
                    throw new Exception("Writing Flash block failed! " + "Programmer did not return CR after 'BxxF'-command.");
                }

                //handler.sendMessage(handler.obtainMessage(ActivityBootloader.MSG_LOG,"#")); // Advance progress indicator.
            }
        }

	    /* More complete blocks to write? */
        while (end - address + 1 >= blockSize) {
            byteCount = blockSize;

            setAddress(address >> 1); // Flash operations use word addresses.

		    /* Start Flash block write */
            sendByte((byte) 'B');
            sendByte((byte) (byteCount >> 8)); // Size, MSB first.
            sendByte((byte) byteCount);
            sendByte((byte) 'F'); // Flash memory.

            while (byteCount > 0) {
                sendByte(data.getData(address));
                address++;
                byteCount--;
            }

            if (getByte() != '\r') {
                throw new Exception("Writing Flash block failed! " + "Programmer did not return CR after 'BxxF'-command.");
            }

            handler.obtainMessage(HandlerBootloader.Result.MSG_UPDATE_DIALOG.ordinal(), address, 0).sendToTarget();
        }

	    /* Any bytes left in last block */
        if (end - address + 1 >= 1) {
            byteCount = end - address + 1; // Get bytes left to write.
            if ((byteCount & 1) == 1) {
                byteCount++; // Align to next word boundary.
            }

            setAddress(address >> 1); // Flash operations use word addresses.

		    /* Start Flash block write */
            sendByte((byte) 'B');
            sendByte((byte) (byteCount >> 8)); // Size, MSB first.
            sendByte((byte) byteCount);
            sendByte((byte) 'F'); // Flash memory.

            while (byteCount > 0) {
                if (address > end) {
                    sendByte((byte) 0xff); // Don't write outside write range.
                } else {
                    sendByte(data.getData(address));
                }

                address++;
                byteCount--;
            }

            if (getByte() != '\r') {
                throw new Exception("Writing Flash block failed! " + "Programmer did not return CR after 'BxxF'-command.");
            }

            //handler.sendMessage(handler.obtainMessage(ActivityBootloader.MSG_LOG,"#"));
        }

        //handler.sendMessage(handler.obtainMessage(ActivityBootloader.MSG_LOG,""));
        return true; // Indicate supported command.
    }

    private boolean readFlash(HEXFile data) throws Exception {

        if (pagesize == -1) {
            throw new Exception("Programmer pagesize is not set!");
        }

	    /* Check block read support */
        sendByte((byte) 'b');

        if (getByte() == 'Y') {
            handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "Using block mode...").sendToTarget();
            return readFlashBlock(data); // Finished writing.
        }

	    /* Get range from HEX file */
        long start = data.getRangeStart();
        long end = data.getRangeEnd(); // Data address range.

	    /* Check autoincrement support */
        sendByte((byte) 'a');

        boolean autoincrement = getByte() == 'Y';                                                                // Bootloader supports address autoincrement?

	    /* Set initial address */
        setAddress(start >> 1); // Flash operations use word addresses.

	    /* Need to read one odd byte first? */
        long address = start;
        if ((address & 1) == 1) {
		    /* Read both, but use only high byte */
            sendByte((byte) 'R');

            data.setData(address, (byte) getByte()); // High byte.
            getByte(); // Don t use low byte.
            address++;
        }

	    /* Get words */
        while (end - address + 1 >= 2) {
		    /* Need to set address again? */
            if (!autoincrement) {
                setAddress(address >> 1);
            }

		    /* Get words */
            sendByte((byte) 'R');

            data.setData(address + 1, (byte) getByte()); // High byte.
            data.setData(address, (byte) getByte()); // Low byte.
            address += 2;

            /*if( address % MEM_PROGRESS_GRANULARITY == 0 )
                handler.sendMessage(handler.obtainMessage(ActivityBootloader.MSG_LOG,"#"));// Advance progress indicator.*/


        }

	    /* Need to read one even byte before finished? */
        if (address == end) {
		    /* Read both, but use only low byte */
            sendByte((byte) 'R');

            getByte(); // Don t use high byte.
            data.setData(address, (byte) getByte()); // Low byte.
        }

        //handler.sendMessage(handler.obtainMessage(ActivityBootloader.MSG_LOG,""));
        return true; // Indicate supported command.
    }

    private boolean readFlashBlock(HEXFile data) throws Exception {

	    /* Get block size, assuming command 'b' just issued and 'Y' has been read */
        int blockSize = getByte() << 8 | getByte(); // Bootloader block size.

	    /* Get range from HEX file */
        int start = data.getRangeStart();
        int end = data.getRangeEnd(); // Data address range.

	    /* Need to read one odd byte first? */
        int address = start;
        if ((address & 1) == 1) {
            setAddress(address >> 1); // Flash operations use word addresses.

		    /* Use only high word */
            sendByte((byte) 'R');

            data.setData(address, (byte) getByte()); // High byte.
            getByte(); // Low byte.
            address++;
        }

	    /* Need to read from middle to end of block first? */
        int byteCount;
        if (address % blockSize > 0) { // In the middle of a block?

            byteCount = blockSize - address % blockSize; // Bytes left in block.

            if (address + byteCount - 1 > end) {// Is that past the read range?

                byteCount = end - address + 1; // Bytes left in read range.
                byteCount &= ~0x01; // Adjust to word count.
            }

            if (byteCount > 0) {
                setAddress(address >> 1); // Flash operations use word addresses.

			    /* Start Flash block read */
                sendByte((byte) 'g');
                sendByte((byte) (byteCount >> 8)); // Size, MSB first.
                sendByte((byte) byteCount);
                sendByte((byte) 'F'); // Flash memory.

                while (byteCount > 0) {
                    data.setData(address, (byte) getByte());
                    address++;
                    byteCount--;
                }

                //handler.sendMessage(handler.obtainMessage(ActivityBootloader.MSG_LOG,"#"));// Advance progress indicator.
            }
        }

	    /* More complete blocks to read? */
        while (end - address + 1 >= blockSize) {
            byteCount = blockSize;

            setAddress(address >> 1); // Flash operations use word addresses.

		    /* Start Flash block read */
            sendByte((byte) 'g');
            sendByte((byte) (byteCount >> 8)); // Size, MSB first.
            sendByte((byte) byteCount);
            sendByte((byte) 'F'); // Flash memory.

            while (byteCount > 0) {
                data.setData(address, (byte) getByte());
                address++;
                byteCount--;
            }
            handler.obtainMessage(HandlerBootloader.Result.MSG_UPDATE_DIALOG.ordinal(), address, 0).sendToTarget();
        }

	    /* Any bytes left in last block */
        if (end - address + 1 >= 1) {
            byteCount = end - address + 1; // Get bytes left to read.
            if ((byteCount & 1) == 1) {
                byteCount++; // Align to next word boundary.
            }

            setAddress(address >> 1); // Flash operations use word addresses.

		    /* Start Flash block read */
            sendByte((byte) 'g');
            sendByte((byte) (byteCount >> 8)); // Size, MSB first.
            sendByte((byte) byteCount);
            sendByte((byte) 'F'); // Flash memory.

            while (byteCount > 0) {
                if (address > end) {
                    getByte(); // Don't read outside write range.
                } else {
                    data.setData(address, (byte) getByte());
                }

                address++;
                byteCount--;
            }

            //handler.sendMessage(handler.obtainMessage(ActivityBootloader.MSG_LOG,"#"));
        }

        //handler.sendMessage(handler.obtainMessage(ActivityBootloader.MSG_LOG,"\r\n"));
        return true; // Indicate supported command.
    }

    public String readProgrammerID() {
        /* Send 'S' command to programmer */
        sendByte((byte) 'S');
	    /* Read 7 characters */
        char[] id = new char[7]; // Reserve 7 characters.
        int length = id.length;
        for (int i = 0; i < length; i++) {
            id[i] = (char) getByte();
        }
        return String.valueOf(id);
    }

    public boolean isProgrammerId() { //Является ли программатором
        //String str = AVRProgrammer.readProgrammerID();
        return "AVRBOOT".equals(readProgrammerID());
    }

    private void setAddress(long address) throws Exception {
	    /* Set current address */
        if (address < 0x10000) {
            sendByte((byte) 'A');
            sendByte((byte) (address >> 8));
            sendByte((byte) address);
        } else {
            sendByte((byte) 'H');
            sendByte((byte) (address >> 16));
            sendByte((byte) (address >> 8));
            sendByte((byte) address);
        }

	    /* Should return CR */
        if (getByte() != '\r') {
            throw new Exception("Setting address for programming operations failed! " + "Programmer did not return CR after 'A'-command.");
        }
    }

    public int getDescriptor(){
        Integer[] sig = new Integer[3];
        /* Get signature */
        readSignature(sig);
        /* находим фаил дескриптор */
        return (sig[1] & 0xff) << 8 | sig[2] & 0xff;
    }

    public void doJob(InputStream isDevice, InputStream isHex) throws Exception {

        avrDevice = new AVRDevice(isDevice /*dirDeviceFiles + '/' + deviceFileName, this*/, handler);
        hexFile = new HEXFile(avrDevice.getFlashSize(), (byte) 0xff, handler);
        hexFile.readFile(isHex /*dirBootFiles + '/' + bootFileName*/);
    }

    public void doDeviceDependent(/*AVRProgrammer programmer, AVRDevice avr, HEXFile hex*/) throws Exception {

	    /* Set programmer pagesize */
        pagesize = avrDevice.getPageSize();
    /* Check if specified address limits are within device range */
        if (flashEndAddress == -1) {
            flashStartAddress = 0;
            flashEndAddress = avrDevice.getFlashSize() - 1;
        } else {
            if (flashEndAddress >= avrDevice.getFlashSize()) {
                throw new Exception("Specified Flash address range is outside device address space!");
            }
        }

        if (eepromEndAddress == -1) {
            //int eepromStartAddress = 0;
            eepromEndAddress = avrDevice.getEEPROMSize() - 1;
        } else {
            if (eepromEndAddress >= avrDevice.getEEPROMSize()) {
                throw new Exception("Specified EEPROM address range is outside device address space!");
            }
        }

		    /* Check limits */
        if (hexFile.getRangeStart() > flashEndAddress || hexFile.getRangeEnd() < flashStartAddress) {
            throw new Exception("HEX file defines data outside specified range!");
        }

        if (hexFile.getRangeStart() > flashStartAddress) {
            flashStartAddress = hexFile.getRangeStart();
        }
        if (hexFile.getRangeEnd() < flashEndAddress) {
            flashEndAddress = hexFile.getRangeEnd();
        }
        hexFile.setUsedRange(flashStartAddress, 15 - flashEndAddress % 16 + flashEndAddress);
        /* Erase chip before programming anything? */
        handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "Erasing chip contents...").sendToTarget();
        if (!chipErase()) {
            throw new Exception("Chip erase is not supported by this programmer!");
        }

		/* Program data */
        handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "Programming Flash contents...").sendToTarget();
        handler.obtainMessage(HandlerBootloader.Result.MSG_SHOW_DIALOG.ordinal(), flashEndAddress, 0, "Programming Flash...").sendToTarget();
        if (!writeFlash(hexFile)) {
            handler.obtainMessage(HandlerBootloader.Result.MSG_CLOSE_DIALOG.ordinal()).sendToTarget();
            throw new Exception("Flash programming is not supported by this programmer!");
        }
        handler.obtainMessage(HandlerBootloader.Result.MSG_CLOSE_DIALOG.ordinal()).sendToTarget();

		/* Prepare HEX file for comparision */
        HEXFile hexFileVerifying = new HEXFile(avrDevice.getFlashSize(), (byte) 0xff, /*getApplicationContext(),*/ handler); // Used for verifying memory contents.

		/* Compare to Flash */
        handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "Reading Flash contents...").sendToTarget();
        handler.obtainMessage(HandlerBootloader.Result.MSG_SHOW_DIALOG.ordinal(), flashEndAddress, 0, "Reading Flash...").sendToTarget();
        hexFileVerifying.setUsedRange(hexFile.getRangeStart(), hexFile.getRangeEnd());
        if (!readFlash(hexFileVerifying)) {
            handler.obtainMessage(HandlerBootloader.Result.MSG_CLOSE_DIALOG.ordinal()).sendToTarget();
            throw new Exception("Flash readout is not supported by this programmer!");
        }
        handler.obtainMessage(HandlerBootloader.Result.MSG_CLOSE_DIALOG.ordinal()).sendToTarget();

		/* Compare data */
        handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "Comparing Flash data...").sendToTarget();
        handler.obtainMessage(HandlerBootloader.Result.MSG_SHOW_DIALOG.ordinal(), flashEndAddress, 0, "Comparing Flash data...").sendToTarget();
        int pos; // Used when comparing data.
        for (pos = hexFile.getRangeStart(); pos <= hexFile.getRangeEnd(); pos++) {
            handler.obtainMessage(HandlerBootloader.Result.MSG_UPDATE_DIALOG.ordinal(), pos, 0).sendToTarget();
            if (hexFile.getData(pos) != hexFileVerifying.getData(pos)) {
                handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "Unequal at address 0x" + hexFile.getData(pos) + '!').sendToTarget();
                handler.obtainMessage(HandlerBootloader.Result.MSG_CLOSE_DIALOG.ordinal()).sendToTarget();
                break;
            }
        }
        handler.obtainMessage(HandlerBootloader.Result.MSG_CLOSE_DIALOG.ordinal()).sendToTarget();

        if (pos > hexFile.getRangeEnd()) {// All equal?

            handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "Equal!").sendToTarget();
        }
        sendByte((byte) 'E');   //Exit bootloader
        handler.obtainMessage(HandlerBootloader.Result.MSG_LOG.ordinal(), "Exit bootloader").sendToTarget();
    }

    public AVRDevice getAvrDevice(){
        return avrDevice;
    }

}
