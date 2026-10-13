package com.generated.app;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class UsbMassStorageSource implements ByteSource {

    private static final String TAG = "UsbMassStorageSource";
    private static final String ACTION_USB_PERMISSION = "com.generated.app.USB_PERMISSION";

    private final Context context;
    private final UsbDevice device;
    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbInterface usbInterface;
    private UsbEndpoint epIn;
    private UsbEndpoint epOut;

    private long sectorSize = 512;
    private long totalSectors = 0;
    private long totalSize = 0;
    private int tagCounter = 1;

    private static final int CBW_SIGNATURE = 0x43425355;
    private static final int CSW_SIGNATURE = 0x53425355;

    public UsbMassStorageSource(Context context, UsbDevice device) throws IOException {
        this.context = context.getApplicationContext();
        this.device = device;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        
        initConnection();
    }

    private void initConnection() throws IOException {
        if (!usbManager.hasPermission(device)) {
            requestPermissionSync();
        }

        if (!usbManager.hasPermission(device)) {
            throw new IOException("USB permission denied");
        }

        // Find Mass Storage Interface (Class 0x08, Subclass 0x06, Protocol 0x50)
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() == 0x08 && intf.getInterfaceSubclass() == 0x06 && intf.getInterfaceProtocol() == 0x50) {
                usbInterface = intf;
                break;
            }
        }

        if (usbInterface == null && device.getInterfaceCount() > 0) {
            // Fallback: take first interface if subclass/protocol differs slightly
            usbInterface = device.getInterface(0);
        }

        if (usbInterface == null) {
            throw new IOException("No suitable USB Mass Storage interface found");
        }

        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint ep = usbInterface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    epIn = ep;
                } else if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    epOut = ep;
                }
            }
        }

        if (epIn == null || epOut == null) {
            throw new IOException("USB bulk endpoints not found");
        }

        connection = usbManager.openDevice(device);
        if (connection == null) {
            throw new IOException("Failed to open USB device connection");
        }

        if (!connection.claimInterface(usbInterface, true)) {
            connection.close();
            throw new IOException("Failed to claim USB interface");
        }

        // Initialize device commands
        inquiry();
        testUnitReady();
        readCapacity();
    }

    private void requestPermissionSync() throws IOException {
        final CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                    synchronized (this) {
                        UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if (d != null && d.equals(device)) {
                            latch.countDown();
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }

        PendingIntent pi = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        usbManager.requestPermission(device, pi);

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception ignored) {}
        }
    }

    private void bulkOnlyReset() {
        try {
            // Class specific request for Mass Storage Reset (request 0xFF, bmRequestType 0x21)
            int bmRequestType = 0x21; // Host to device, class, interface
            int bRequest = 0xFF; // Reset
            int wValue = 0;
            int wIndex = usbInterface.getId();
            byte[] buffer = new byte[0];
            connection.controlTransfer(bmRequestType, bRequest, wValue, wIndex, buffer, 0, 2000);
            
            // Clear endpoint stalls via standard control transfer if needed
            int requestTypeOut = 0x02; // Endpoint clear feature (recipient endpoint = 2)
            int requestClearFeature = 0x01; // CLEAR_FEATURE
            int featureHalt = 0x00; // ENDPOINT_HALT
            
            if (epIn != null) {
                int epAddr = epIn.getAddress();
                connection.controlTransfer(requestTypeOut, requestClearFeature, featureHalt, epAddr, new byte[0], 0, 2000);
            }
            if (epOut != null) {
                int epAddr = epOut.getAddress();
                connection.controlTransfer(requestTypeOut, requestClearFeature, featureHalt, epAddr, new byte[0], 0, 2000);
            }
        } catch (Exception ignored) {}
    }

    private boolean sendCbw(int tag, long dataTransferLength, int flags, byte lun, byte[] cdb) {
        ByteBuffer cbw = ByteBuffer.allocate(31);
        cbw.order(ByteOrder.LITTLE_ENDIAN);
        cbw.putInt(CBW_SIGNATURE);
        cbw.putInt(tag);
        cbw.putInt((int) dataTransferLength);
        cbw.put((byte) flags);
        cbw.put(lun);
        cbw.put((byte) cdb.length);
        cbw.put(cdb);

        // pad remainder to 31 bytes if cdb < 16
        int written = 15 + cdb.length;
        while (written < 31) {
            cbw.put((byte) 0);
            written++;
        }

        int sent = connection.bulkTransfer(epOut, cbw.array(), 31, 3000);
        return sent == 31;
    }

    private boolean receiveCsw(int expectedTag) {
        byte[] cswBuffer = new byte[13];
        int received = connection.bulkTransfer(epIn, cswBuffer, 13, 3000);
        if (received != 13) return false;

        ByteBuffer csw = ByteBuffer.wrap(cswBuffer);
        csw.order(ByteOrder.LITTLE_ENDIAN);
        int sig = csw.getInt();
        int tag = csw.getInt();
        int residue = csw.getInt();
        byte status = csw.get();

        if (sig != CSW_SIGNATURE || tag != expectedTag || status != 0) {
            if (status != 0) {
                requestSense();
            }
            return false;
        }
        return true;
    }

    private void inquiry() throws IOException {
        byte[] cdb = new byte[]{ (byte)0x12, 0, 0, 0, (byte)0x24, 0 };
        int tag = tagCounter++;
        byte[] data = new byte[36];

        synchronized (this) {
            if (!sendCbw(tag, 36, 0x80, (byte)0, cdb)) {
                bulkOnlyReset();
                throw new IOException("INQUIRY CBW failed");
            }
            int read = connection.bulkTransfer(epIn, data, 36, 3000);
            if (!receiveCsw(tag)) {
                bulkOnlyReset();
            }
        }
    }

    private void testUnitReady() {
        byte[] cdb = new byte[]{ 0, 0, 0, 0, 0, 0 };
        int tag = tagCounter++;
        synchronized (this) {
            if (sendCbw(tag, 0, 0x00, (byte)0, cdb)) {
                receiveCsw(tag);
            }
        }
    }

    private void requestSense() {
        byte[] cdb = new byte[]{ 0x03, 0, 0, 0, (byte)0x12, 0 };
        int tag = tagCounter++;
        byte[] data = new byte[18];
        synchronized (this) {
            if (sendCbw(tag, 18, 0x80, (byte)0, cdb)) {
                connection.bulkTransfer(epIn, data, 18, 2000);
                receiveCsw(tag);
            }
        }
    }

    private void readCapacity() throws IOException {
        byte[] cdb = new byte[]{ (byte)0x25, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        int tag = tagCounter++;
        byte[] data = new byte[8];

        synchronized (this) {
            for (int attempt = 0; attempt < 3; attempt++) {
                if (sendCbw(tag, 8, 0x80, (byte)0, cdb)) {
                    int read = connection.bulkTransfer(epIn, data, 8, 3000);
                    if (receiveCsw(tag) && read == 8) {
                        ByteBuffer bb = ByteBuffer.wrap(data);
                        bb.order(ByteOrder.BIG_ENDIAN);
                        long lastLba = bb.getInt() & 0xFFFFFFFFL;
                        long blockLen = bb.getInt() & 0xFFFFFFFFL;
                        if (blockLen > 0) sectorSize = blockLen;
                        totalSectors = lastLba + 1;
                        totalSize = totalSectors * sectorSize;
                        return;
                    }
                }
                bulkOnlyReset();
            }
        }
        throw new IOException("Failed to read capacity from USB storage");
    }

    @Override
    public long getSize() {
        return totalSize;
    }

    @Override
    public int readAt(long offset, byte[] buf, int len) throws IOException {
        if (totalSize <= 0 || offset >= totalSize) return -1;

        long sectorIndex = offset / sectorSize;
        int sectorOffset = (int) (offset % sectorSize);
        int sectorsToRead = (int) ((len + sectorOffset + sectorSize - 1) / sectorSize);
        
        // Cap max transfer per command to 128KB (256 sectors) for stability
        int maxSectorsPerTransfer = 256;
        if (sectorsToRead > maxSectorsPerTransfer) {
            sectorsToRead = maxSectorsPerTransfer;
        }

        long bytesToTransfer = sectorsToRead * sectorSize;
        byte[] sectorBuffer = new byte[(int) bytesToTransfer];

        boolean success = false;
        for (int retry = 0; retry < 3; retry++) {
            synchronized (this) {
                int tag = tagCounter++;
                byte[] cdb;
                
                if (sectorIndex > 0xFFFFFFFFL || sectorsToRead > 0xFFFF) {
                    // READ(16)
                    cdb = new byte[16];
                    cdb[0] = (byte) 0x88; // READ(16)
                    ByteBuffer.wrap(cdb, 2, 8).order(ByteOrder.BIG_ENDIAN).putLong(sectorIndex);
                    ByteBuffer.wrap(cdb, 10, 4).order(ByteOrder.BIG_ENDIAN).putInt(sectorsToRead);
                } else {
                    // READ(10)
                    cdb = new byte[10];
                    cdb[0] = (byte) 0x28; // READ(10)
                    ByteBuffer.wrap(cdb, 2, 4).order(ByteOrder.BIG_ENDIAN).putInt((int) sectorIndex);
                    ByteBuffer.wrap(cdb, 7, 2).order(ByteOrder.BIG_ENDIAN).putShort((short) sectorsToRead);
                }

                if (sendCbw(tag, bytesToTransfer, 0x80, (byte)0, cdb)) {
                    int totalRead = 0;
                    long deadline = System.currentTimeMillis() + 5000;
                    while (totalRead < bytesToTransfer && System.currentTimeMillis() < deadline) {
                        int chunkRead = connection.bulkTransfer(epIn, sectorBuffer, totalRead, (int) (deadline - System.currentTimeMillis()));
                        if (chunkRead <= 0) break;
                        totalRead += chunkRead;
                    }

                    if (receiveCsw(tag)) {
                        success = true;
                        break;
                    }
                }
                bulkOnlyReset();
            }
        }

        if (!success) {
            // Skip unreadable sector block by filling zeros and logging
            Log.w(TAG, "Unreadable sector at offset " + offset + ", skipping sector block.");
            java.util.Arrays.fill(sectorBuffer, (byte) 0);
        }

        int copyLen = Math.min(len, (int) (bytesToTransfer - sectorOffset));
        if (copyLen <= 0) return -1;

        System.arraycopy(sectorBuffer, sectorOffset, buf, 0, copyLen);
        return copyLen;
    }

    @Override
    public void close() {
        try {
            if (connection != null && usbInterface != null) {
                connection.releaseInterface(usbInterface);
                connection.close();
            }
        } catch (Exception ignored) {}
    }
}
