package locking;

import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Locking
{
    // These can be thought of as fake servers
    private static final int QTY = 5;
    // how often to run each server
    private static final int REPETITIONS = QTY * 100;

    private static final String     PATH = "/examples/locks4";

    public static void main(String[] args) throws Exception
    {
        // all of the useful sample code is in ExampleClientThatLocks.java

        // FakeLimitedResource simulates some external resource that can only be access by one process at a time
        final FakeLimitedResource   resource = new FakeLimitedResource();

        ExecutorService             service = Executors.newFixedThreadPool(QTY);
        final TestingServer         server = new TestingServer();
        CuratorFramework tempClient = CuratorFrameworkFactory.newClient("127.0.0.1:2181", new ExponentialBackoffRetry(1000, 3));
        try {
            tempClient.start();
            tempClient.delete().deletingChildrenIfNeeded().forPath("/examples/locks4");
        }
        catch (Exception e) {
            System.out.println("E: " + e.toString());
        }
        try {
            // couldn't figure out how to serialize a bool lol
            tempClient.create().creatingParentsIfNeeded().forPath("/examples/hitRateLimit", "false".getBytes());
        }
        catch (Exception e) {
            System.out.println("E: " + e.toString());
        }
        try {
            tempClient.delete().deletingChildrenIfNeeded().forPath("/examples/locks5");
        }
        catch (Exception e) {
            System.out.println("E: " + e.toString());
        }
        ClientThatLocks      tempClient2 = new ClientThatLocks(tempClient, PATH, resource, "Client cron");
        Cron myThread = new Cron(tempClient2);
        myThread.start();


        try
        {
            for ( int i = 0; i < QTY; ++i )
            {
                final int       index = i;
                Callable<Void>  task = new Callable<Void>()
                {
                    @Override
                    public Void call() throws Exception
                    {
                        System.out.println(server.getConnectString());
                        CuratorFramework client = CuratorFrameworkFactory.newClient("127.0.0.1:2181", new ExponentialBackoffRetry(1000, 3));
                        try
                        {
                            client.start();

                            ClientThatLocks      example = new ClientThatLocks(client, PATH, resource, "Client " + index);
                            for ( int j = 0; j < REPETITIONS; ++j )
                            {



                                example.doWork(50, TimeUnit.SECONDS);
                            }
                        }
                        catch ( InterruptedException e )
                        {
                            Thread.currentThread().interrupt();
                        }
                        catch ( Exception e )
                        {
                            e.printStackTrace();
                            // log or do something
                        }
                        finally
                        {
                            CloseableUtils.closeQuietly(client);
                        }
                        return null;
                    }
                };
                service.submit(task);
            }

            service.shutdown();
            service.awaitTermination(10, TimeUnit.MINUTES);
        }
        finally
        {
            CloseableUtils.closeQuietly(server);
        }
    }
}