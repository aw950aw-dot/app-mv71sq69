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
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class UsbMassStorageSource implements ByteSource {

    private static final String TAG = "UsbMassStorageSource";
    private static final String ACTION_USB_PERMISSION = "com.generated.app.USB_PERMISSION";

    private final Context context;
    private final UsbDevice device;
    private final UsbManager usbManager;
    private UsbDeviceConnection connection;
    private UsbInterface usbInterface;
    private UsbEndpoint epIn;
    private UsbEndpoint epOut;

    private long sectorSize = 512;
    private long totalSectors = 0;
    private long totalSize = 0;
    private int tagCounter = 1;
    private byte lun = 0;

    private static final int CBW_SIGNATURE = 0x43425355;
    private static final int CSW_SIGNATURE = 0x53425355;

    private static final List<String> diagnosticLogs = new ArrayList<>();

    public static List<String> getDiagnosticLogs() {
        synchronized (diagnosticLogs) {
            return new ArrayList<>(diagnosticLogs);
        }
    }

    private static void logDiag(String message) {
        String entry = "[" + System.currentTimeMillis() + "] " + message;
        Log.d(TAG, entry);
        synchronized (diagnosticLogs) {
            diagnosticLogs.add(entry);
            if (diagnosticLogs.size() > 100) {
                diagnosticLogs.remove(0);
            }
        }
    }

    public UsbMassStorageSource(Context context, UsbDevice device) throws IOException {
        this.context = context.getApplicationContext();
        this.device = device;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        synchronized (diagnosticLogs) {
            diagnosticLogs.clear();
        }

        logDiag("Initializing UsbMassStorageSource for device: " + device.getDeviceName());
        logDiag("VID: 0x" + Integer.toHexString(device.getVendorId()) + ", PID: 0x" + Integer.toHexString(device.getProductId()));
        logDiag("Interface count: " + device.getInterfaceCount());

        initConnection();
    }

    private void initConnection() throws IOException {
        if (!usbManager.hasPermission(device)) {
            logDiag("Device lacks USB permission. Requesting permission...");
            requestPermissionSync();
        } else {
            logDiag("USB permission already granted.");
        }

        if (!usbManager.hasPermission(device)) {
            logDiag("FATAL: USB permission denied by user or system.");
            throw new IOException("USB permission denied");
        }

        // Find Mass Storage Interface (Class 0x08, Subclass 0x06, Protocol 0x50)
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            int iclass = intf.getInterfaceClass();
            int isub = intf.getInterfaceSubclass();
            int iproto = intf.getInterfaceProtocol();
            logDiag("Interface " + i + ": class=0x" + Integer.toHexString(iclass) +
                    ", subclass=0x" + Integer.toHexString(isub) +
                    ", proto=0x" + Integer.toHexString(iproto) +
                    ", endpoints=" + intf.getEndpointCount());

            if (iclass == 0x08 && isub == 0x06 && iproto == 0x50) {
                usbInterface = intf;
                logDiag("-> Selected standard BOT mass storage interface at index " + i);
                break;
            }
        }

        if (usbInterface == null) {
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface intf = device.getInterface(i);
                if (intf.getInterfaceClass() == 0x08) {
                    usbInterface = intf;
                    logDiag("-> Fallback: Selected mass storage class interface at index " + i);
                    break;
                }
            }
        }

        if (usbInterface == null && device.getInterfaceCount() > 0) {
            usbInterface = device.getInterface(0);
            logDiag("-> Fallback: Selected first available interface index 0");
        }

        if (usbInterface == null) {
            logDiag("FATAL: No suitable USB interface found.");
            throw new IOException("No suitable USB Mass Storage interface found");
        }

        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint ep = usbInterface.getEndpoint(i);
            String dir = (ep.getDirection() == UsbConstants.USB_DIR_IN) ? "IN" : "OUT";
            String type = (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) ? "BULK" : "OTHER";
            logDiag("Endpoint " + i + ": dir=" + dir + ", type=" + type + ", addr=0x" + Integer.toHexString(ep.getAddress()));

            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    epIn = ep;
                } else if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    epOut = ep;
                }
            }
        }

        if (epIn == null || epOut == null) {
            logDiag("FATAL: Bulk IN or OUT endpoints missing. epIn=" + (epIn != null) + ", epOut=" + (epOut != null));
            throw new IOException("USB bulk endpoints not found");
        }

        connection = usbManager.openDevice(device);
        if (connection == null) {
            logDiag("FATAL: usbManager.openDevice(device) returned null!");
            throw new IOException("Failed to open USB device connection");
        }
        logDiag("Device connection opened successfully.");

        boolean claimed = connection.claimInterface(usbInterface, true);
        logDiag("claimInterface result: " + claimed);
        if (!claimed) {
            connection.close();
            throw new IOException("Failed to claim USB interface");
        }

        // Get Max LUN (Class-specific request, bmRequestType = 0xA1, bRequest = 0xFE)
        getMaxLun();

        // Initialize device commands with retries
        initStorageCommandsWithRetry();
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
                            logDiag("Received USB permission broadcast for device.");
                            latch.countDown();
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        Intent permissionIntent = new Intent(ACTION_USB_PERMISSION);
        permissionIntent.setPackage(context.getPackageName());
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, permissionIntent, flags);

        usbManager.requestPermission(device, pi);
        logDiag("Permission request sent. Awaiting response...");

        try {
            boolean received = latch.await(15, TimeUnit.SECONDS);
            logDiag("Permission await completed. Received: " + received);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logDiag("Permission request interrupted.");
        } finally {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception ignored) {}
        }
    }

    private void getMaxLun() {
        try {
            int requestType = 0xA1; // Device-to-host, class, interface
            int request = 0xFE; // GET_MAX_LUN
            int value = 0;
            int index = usbInterface.getId();
            byte[] buffer = new byte[1];
            int result = connection.controlTransfer(requestType, request, value, index, buffer, 1, 3000);
            logDiag("GET_MAX_LUN controlTransfer result: " + result + (result >= 0 ? ", maxLun=" + (buffer[0] & 0xFF) : ""));
            if (result >= 0) {
                lun = (byte) (buffer[0] & 0xFF);
            } else {
                lun = 0;
                logDiag("GET_MAX_LUN failed or unsupported; assuming LUN 0.");
            }
        } catch (Exception e) {
            lun = 0;
            logDiag("GET_MAX_LUN exception: " + e.getMessage() + "; assuming LUN 0.");
        }
    }

    private void bulkOnlyReset() {
        try {
            int bmRequestType = 0x21; // Host to device, class, interface
            int bRequest = 0xFF; // RESET
            int wValue = 0;
            int wIndex = usbInterface.getId();
            byte[] buffer = new byte[0];
            int res = connection.controlTransfer(bmRequestType, bRequest, wValue, wIndex, buffer, 0, 3000);
            logDiag("Bulk Only Reset controlTransfer result: " + res);

            int requestTypeOut = 0x02; // Endpoint clear feature
            int requestClearFeature = 0x01; // CLEAR_FEATURE
            int featureHalt = 0x00; // ENDPOINT_HALT

            if (epIn != null) {
                int resIn = connection.controlTransfer(requestTypeOut, requestClearFeature, featureHalt, epIn.getAddress(), new byte[0], 0, 2000);
                logDiag("Clear stall epIn result: " + resIn);
            }
            if (epOut != null) {
                int resOut = connection.controlTransfer(requestTypeOut, requestClearFeature, featureHalt, epOut.getAddress(), new byte[0], 0, 2000);
                logDiag("Clear stall epOut result: " + resOut);
            }
        } catch (Exception e) {
            logDiag("bulkOnlyReset exception: " + e.getMessage());
        }
    }

    private boolean sendCbw(int tag, long dataTransferLength, int flags, byte lunByte, byte[] cdb) {
        ByteBuffer cbw = ByteBuffer.allocate(31);
        cbw.order(ByteOrder.LITTLE_ENDIAN);
        cbw.putInt(CBW_SIGNATURE);
        cbw.putInt(tag);
        cbw.putInt((int) dataTransferLength);
        cbw.put((byte) flags);
        cbw.put(lunByte);
        cbw.put((byte) cdb.length);
        cbw.put(cdb);

        int written = 15 + cdb.length;
        while (written < 31) {
            cbw.put((byte) 0);
            written++;
        }

        int sent = connection.bulkTransfer(epOut, cbw.array(), 31, 5000);
        return sent == 31;
    }

    private boolean receiveCsw(int expectedTag) {
        byte[] cswBuffer = new byte[13];
        int received = connection.bulkTransfer(epIn, cswBuffer, 13, 5000);
        if (received != 13) {
            logDiag("CSW receive failed, bytes received: " + received);
            return false;
        }

        ByteBuffer csw = ByteBuffer.wrap(cswBuffer);
        csw.order(ByteOrder.LITTLE_ENDIAN);
        int sig = csw.getInt();
        int tag = csw.getInt();
        int residue = csw.getInt();
        byte status = csw.get();

        if (sig != CSW_SIGNATURE || tag != expectedTag || status != 0) {
            logDiag("CSW invalid: sig=0x" + Integer.toHexString(sig) + ", tag=" + tag + ", status=" + status);
            if (status != 0) {
                requestSense();
            }
            return false;
        }
        return true;
    }

    private void initStorageCommandsWithRetry() throws IOException {
        IOException lastEx = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                logDiag("Initialization attempt " + attempt + "/5...");
                inquiry();
                testUnitReady();
                readCapacity();
                logDiag("Storage initialized successfully on attempt " + attempt);
                return;
            } catch (IOException e) {
                lastEx = e;
                logDiag("Attempt " + attempt + " failed: " + e.getMessage());
                bulkOnlyReset();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw lastEx != null ? lastEx : new IOException("Failed to initialize USB storage after 5 attempts");
    }

    private void inquiry() throws IOException {
        byte[] cdb = new byte[]{ (byte)0x12, 0, 0, 0, (byte)0x24, 0 };
        int tag = tagCounter++;
        byte[] data = new byte[36];

        synchronized (this) {
            if (!sendCbw(tag, 36, 0x80, lun, cdb)) {
                throw new IOException("INQUIRY CBW failed");
            }
            int read = connection.bulkTransfer(epIn, data, 36, 5000);
            if (!receiveCsw(tag)) {
                throw new IOException("INQUIRY CSW failed");
            }
            logDiag("INQUIRY succeeded, read " + read + " bytes.");
        }
    }

    private void testUnitReady() throws IOException {
        byte[] cdb = new byte[]{ 0, 0, 0, 0, 0, 0 };
        int tag = tagCounter++;
        synchronized (this) {
            if (!sendCbw(tag, 0, 0x00, lun, cdb) || !receiveCsw(tag)) {
                throw new IOException("TEST UNIT READY failed");
            }
            logDiag("TEST UNIT READY succeeded.");
        }
    }

    private void requestSense() {
        byte[] cdb = new byte[]{ 0x03, 0, 0, 0, (byte)0x12, 0 };
        int tag = tagCounter++;
        byte[] data = new byte[18];
        synchronized (this) {
            if (sendCbw(tag, 18, 0x80, lun, cdb)) {
                connection.bulkTransfer(epIn, data, 18, 3000);
                receiveCsw(tag);
                logDiag("REQUEST SENSE executed.");
            }
        }
    }

    private void readCapacity() throws IOException {
        byte[] cdb = new byte[]{ (byte)0x25, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        int tag = tagCounter++;
        byte[] data = new byte[8];

        synchronized (this) {
            if (!sendCbw(tag, 8, 0x80, lun, cdb)) {
                throw new IOException("READ CAPACITY CBW failed");
            }
            int read = connection.bulkTransfer(epIn, data, 8, 5000);
            if (!receiveCsw(tag) || read != 8) {
                throw new IOException("READ CAPACITY failed or incomplete read");
            }

            ByteBuffer bb = ByteBuffer.wrap(data);
            bb.order(ByteOrder.BIG_ENDIAN);
            long lastLba = bb.getInt() & 0xFFFFFFFFL;
            long blockLen = bb.getInt() & 0xFFFFFFFFL;
            if (blockLen > 0) sectorSize = blockLen;
            totalSectors = lastLba + 1;
            totalSize = totalSectors * sectorSize;
            logDiag("Read Capacity: lastLba=" + lastLba + ", sectorSize=" + sectorSize + ", totalSize=" + totalSize);
        }
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

        int maxSectorsPerTransfer = 256;
        if (sectorsToRead > maxSectorsPerTransfer) {
            sectorsToRead = maxSectorsPerTransfer;
        }

        long bytesToTransfer = sectorsToRead * sectorSize;
        byte[] sectorBuffer = new byte[(int) bytesToTransfer];

        boolean success = false;
        for (int retry = 0; retry < 5; retry++) {
            synchronized (this) {
                int tag = tagCounter++;
                byte[] cdb;

                if (sectorIndex > 0xFFFFFFFFL || sectorsToRead > 0xFFFF) {
                    cdb = new byte[16];
                    cdb[0] = (byte) 0x88;
                    ByteBuffer.wrap(cdb, 2, 8).order(ByteOrder.BIG_ENDIAN).putLong(sectorIndex);
                    ByteBuffer.wrap(cdb, 10, 4).order(ByteOrder.BIG_ENDIAN).putInt(sectorsToRead);
                } else {
                    cdb = new byte[10];
                    cdb[0] = (byte) 0x28;
                    ByteBuffer.wrap(cdb, 2, 4).order(ByteOrder.BIG_ENDIAN).putInt((int) sectorIndex);
                    ByteBuffer.wrap(cdb, 7, 2).order(ByteOrder.BIG_ENDIAN).putShort((short) sectorsToRead);
                }

                if (sendCbw(tag, bytesToTransfer, 0x80, lun, cdb)) {
                    int totalRead = 0;
                    long deadline = System.currentTimeMillis() + 8000;
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
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (!success) {
            logDiag("Unreadable sector block at offset " + offset + ", filling with zeros.");
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
                logDiag("USB connection closed.");
            }
        } catch (Exception ignored) {}
    }
}
