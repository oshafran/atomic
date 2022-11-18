package locking;

import org.apache.curator.framework.CuratorFramework;

import java.io.ByteArrayInputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class Log implements Serializable {
    public  Date created_at;
    public Log() {
        this.created_at = new Date();
    }

    public static void CalulateRatePerMin(ClientThatLocks client) throws Exception {
        long time = 20;
        TimeUnit unit = TimeUnit.SECONDS;
        if ( !client.lock.acquire(time, unit) )
        {
            throw new IllegalStateException("cron" + " could not acquire the lock");
        }
        LinkedList<Log> logs;
        System.out.println("calculating rate/min");

        try {
            byte[] tempLog = client.client.getData().forPath("/examples/locks5");
            ByteArrayInputStream bis = new ByteArrayInputStream(tempLog);
            ObjectInput in = null;
            in = new ObjectInputStream(bis);
            logs = (LinkedList<Log>) in.readObject();

        } catch(Exception e) {
            System.out.println("creating new log object");
            logs = new LinkedList<Log>();
        }


        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, -60);
        int rateCount = 0;
        LinkedList<Log> newLogs = new LinkedList();
        for (int i = 0; i < logs.size(); i++) {
            Log log = logs.get(i);
            if (cal.getTime().before(log.created_at)) {
                rateCount += 1;
                // will this lead to a memory leak? hopefully not
                newLogs.add(log);
            } else {
                break;
            }
        }
        System.out.println("RATE COUNT " + rateCount);
        float rate = (float) rateCount / (float) 60;
        System.out.println("Rate per min is" + rate);
        if (rate > 1) {
            client.writeHitRateLimit("true");
        } else {
            client.writeHitRateLimit("false");
        }
        client.writeLogs(newLogs);
        client.lock.release();

    }
}
