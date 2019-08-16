/*
 * A class to manage the communication between Ibis instances
 */
package rubiks.ipl;

import ibis.ipl.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author alexsharifi
 */
public class Communication implements MessageUpcall {
        public Ibis myIbis;
        public AtomicInteger result = new AtomicInteger(0); 
        public AtomicInteger counter = new AtomicInteger(0); 
        public AtomicInteger subResult= new AtomicInteger(0);
        public int ibisSize;
        public int oldBound, newBound;
        public IbisIdentifier[] joinedIbises;
        public IbisIdentifier server;
        public int index; 
        public ReceivePort receiverBoundPort, resultReceiverPort;
        public SendPort sendResultPort,sendBoundPort;
        public static Thread[] threadList;
        public static ArrayList<Cube> allJobs; 

        public CubeCache cache[];
    /*
      Port for receiving the results in master side 
      or sendind the results in client side    
     */ 
        
    PortType receivePortType = new PortType(PortType.COMMUNICATION_RELIABLE,
            PortType.SERIALIZATION_DATA,
            PortType.RECEIVE_AUTO_UPCALLS,
            PortType.CONNECTION_MANY_TO_ONE);

    /**
     * Port type used for sending or receiving new bound
     */
    PortType sendPortType = new PortType(PortType.COMMUNICATION_RELIABLE,
            PortType.SERIALIZATION_DATA,
            PortType.RECEIVE_EXPLICIT,
            PortType.CONNECTION_ONE_TO_MANY);

    IbisCapabilities ibisCapabilities = new IbisCapabilities(
            IbisCapabilities.ELECTIONS_STRICT,
            IbisCapabilities.MEMBERSHIP_TOTALLY_ORDERED, 
            IbisCapabilities.CLOSED_WORLD);

        
    public Communication(ArrayList<Cube> _allJobs, Cube cube) throws Exception {
        // Create an ibis instance.
        // Notice createIbis uses varargs for its parameters.
        
        allJobs = _allJobs;
        myIbis = IbisFactory.createIbis(ibisCapabilities, null, receivePortType, sendPortType);
        myIbis.registry().waitUntilPoolClosed();
        
        joinedIbises = myIbis.registry().joinedIbises();
        // Elect a server
        server = myIbis.registry().elect("Server");
        ibisSize=myIbis.registry().getPoolSize();
        newBound=3;
        oldBound=3;
        index=0;
        //Elect an uniquie index for each ibis instance
        for (int i=1;i<=ibisSize;i++) {
            if(joinedIbises[i-1].equals(myIbis.identifier())){
                index=i;
                break;
            }   
        }
        resultReceiverPort = myIbis.createReceivePort(receivePortType, "receiveResult", this);
        resultReceiverPort.enableConnections();        
        
        receiverBoundPort = myIbis.createReceivePort(sendPortType, "receiveBound"); 
        receiverBoundPort.enableConnections();
        
        sendResultPort = myIbis.createSendPort(receivePortType);   
        sendResultPort.connect(server, "receiveResult");         
        
        
        cache= new CubeCache[(allJobs.size()/ibisSize)+1];
        threadList= new Thread[(allJobs.size()/ibisSize)+1];        
 
        for (int i=0;i<(allJobs.size()/ibisSize)+1;i++) {
            cache[i] = new CubeCache(cube.getSize());
        }
    }
    
    public void run() throws IOException, InterruptedException{
        // If I am the server, run server, else run client.
        if (server.equals(myIbis.identifier())) {
            server(myIbis);
        } else {      
            client(myIbis, server);
        }  
        receiverBoundPort.close();
        sendResultPort.close();
        resultReceiverPort.close();
        myIbis.end();
    }
    
    
    @Override
    public void upcall(ReadMessage message) throws IOException {

        String s = message.readString();
        message.finish();
        result.addAndGet(Integer.parseInt(s));
        counter.addAndGet(1);
        //Send a new bound to the clients if there is not any results
        // with lower bound.
        if(counter.get()==ibisSize && result.get()==0){
            oldBound++; 
            counter.set(0);

        WriteMessage msg = sendBoundPort.newMessage();
        msg.writeString(Integer.toString(oldBound));
        msg.finish();  
        // Send a negative bound to the clients to determine the end of program
        } else if(counter.get()==ibisSize && result.get()!=0){
            
            newBound=oldBound;
            oldBound=-1;
            WriteMessage msg = sendBoundPort.newMessage();
            msg.writeString(Integer.toString(oldBound));
            msg.finish();
            sendBoundPort.close();

        }
    }
        
        
    public void server (Ibis myIbis) throws IOException, InterruptedException{
        resultReceiverPort.enableMessageUpcalls(); 
        sendBoundPort = myIbis.createSendPort(sendPortType);
        System.out.print("Bound now: 1 2");
        for (IbisIdentifier joinedIbis : joinedIbises) 
           sendBoundPort.connect(joinedIbis, "receiveBound"); 
        client(this.myIbis, this.server);       
        System.out.println();
        System.out.println("Solving cube possible in " + result + " ways of "
                + newBound + " steps");
        }
        
        public void client(Ibis myIbis, IbisIdentifier server) throws IOException, InterruptedException{
            
            int allJobsSize=allJobs.size();
                
                while (oldBound!=-1){
                    int j=0;
                    //create a new thread for each sub job and solve them sequentially, a single thread implementation 
                    for (int i=index;i<=allJobsSize;i+=ibisSize)
                    {
                            threadList[j]= new ClientThread(allJobs.get(i-1), this, oldBound, cache[j]);
                            threadList[j].start();
                            threadList[j].join();    
                            j++;
                            
                    } 

                    if(myIbis.identifier().equals(server))
                        System.out.print(" " + oldBound);
                    WriteMessage msg = sendResultPort.newMessage();
                    msg.writeString(Integer.toString(subResult.get()));
                    msg.finish(); 
                    
                    ReadMessage r = receiverBoundPort.receive();
                    String s = r.readString();
                    r.finish(); 
                    oldBound=Integer.parseInt(s);

                }   
 
        }
}
