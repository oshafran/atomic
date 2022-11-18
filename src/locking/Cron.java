package locking;

public class Cron extends Thread{

    private ClientThatLocks client;
    public Cron(ClientThatLocks client) {
        this.client = client;
    }

    public void run(){
        while (true) {
            try {
                Log.CalulateRatePerMin(this.client);
                Thread.sleep(5000);

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                System.out.println(e);
                throw new RuntimeException(e);
            }
        }

    }

}
