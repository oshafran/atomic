package locking;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;

import java.io.*;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class ClientThatLocks
{
    public final InterProcessMutex lock;
    private final FakeLimitedResource resource;
    private final String clientName;
    final CuratorFramework client;
    public static int count;


    public ClientThatLocks(CuratorFramework client, String lockPath, FakeLimitedResource resource, String clientName)
    {
        this.resource = resource;
        this.clientName = clientName;
        this.client = client;
        lock = new InterProcessMutex(client, lockPath);
    }

    public void doWork(long time, TimeUnit unit) throws Exception
    {
        if ( !lock.acquire(time, unit) )
        {
            throw new IllegalStateException(clientName + " could not acquire the lock");
        }
        try
        {
            System.out.println(clientName + " has the lock");
            resource.use();
            Thread.sleep(500);


            if(this.readHitRateLimit().equals("true")) {
                System.out.println("LIMIT HAS BEEN REACHED");
                return;
            }


            LinkedList<Log> logs = this.readLogs();
            logs.push(new Log());
            this.writeLogs(logs);



        }
        finally
        {
//            System.out.println(clientName + " releasing the lock");
            lock.release(); // always release the lock in a finally block
        }
    }

    public void writeLogs(LinkedList<Log> logs) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(logs);
            out.flush();
            this.client.create().orSetData().creatingParentsIfNeeded().forPath("/examples/locks5", bos.toByteArray());

        } finally {
            try {
                bos.close();
            } catch (IOException ex) {
                System.out.println("here, error");
                // ignore close exception
            }
        }

    }

    public void writeHitRateLimit(String hit) throws Exception {
        this.client.create().orSetData().creatingParentsIfNeeded().forPath("/examples/hitRateLimit", hit.getBytes());

    }

    public String readHitRateLimit() throws Exception {

        byte[] tempRateLimitHit = this.client.getData().forPath("/examples/hitRateLimit");
       return new String(tempRateLimitHit, StandardCharsets.UTF_8);
    }

    public LinkedList<Log> readLogs() throws Exception {
        LinkedList<Log> logs;
        try {
            byte[] tempLog = this.client.getData().forPath("/examples/locks5");
            ByteArrayInputStream bis = new ByteArrayInputStream(tempLog);
            ObjectInput in = null;
            in = new ObjectInputStream(bis);
            logs = (LinkedList<Log>) in.readObject();

        } catch(Exception e) {
            System.out.println("creating new log object");
            logs = new LinkedList<Log>();
        }
        return logs;
    }
    public static byte[] convertIntToByteArray(int value) {
        return  ByteBuffer.allocate(4).putInt(value).array();
    }

    // method 2, bitwise right shift
    public static byte[] convertIntToByteArray2(int value) {
        return new byte[] {
                (byte)(value >> 24),
                (byte)(value >> 16),
                (byte)(value >> 8),
                (byte)value };
    }

    public static String convertBytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte temp : bytes) {
            result.append(String.format("%02x", temp));
        }
        return result.toString();
    }
    // method 1
    public static int convertByteArrayToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    // method 2, bitwise again, 0xff for sign extension
    public static int convertByteArrayToInt2(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                ((bytes[3] & 0xFF) << 0);
    }


}