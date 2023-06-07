import java.net.*;
import org.eclipse.paho.client.mqttv3.*;
import java.io.*;
import java.util.*;
import java.util.Arrays;
import java.io.FileWriter;   // Import the FileWriter class
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
public class Sensor 
{
    public static String MQTT_BROKER = "";
    public static final String MQTT_TOPIC = "device/status";
    public static MqttClient client;
    public static final String deviceCategoryList[] = new String[]{"Sensor","Actuator","Controller","Aplication"};
    public static final String deviceTypeSensor[] = new String[]{"Temperature","Humidity","Light"};
    public static final String deviceTypeActuator[] = new String[]{"Fan","Pump","Light"};
    static volatile boolean finished = false;
    public static String messageNotify="";
    public static String adrress;
    public static boolean MqttInitialized = false;
    public static void main(String[] args)  throws InterruptedException
    {
        if(args.length == 3){
            try
            {
                String data = SocketFunctions.getIndexDevice(deviceCategoryList,args[1]);
                if(args[1].equals("Sensor")){
                    data = data + "|" + SocketFunctions.getIndexDevice(deviceTypeSensor,args[2]) + "|0";
                }
                else if(args[1].equals("Actuator")){
                    data = data + "|" + SocketFunctions.getIndexDevice(deviceTypeActuator,args[2]) + "|0";
                }
                else if(args[1].equals("Controller")){
                    data = data + "|2|0";
                }
                else
                    data = data + "|3|0";
                adrress = SocketFunctionsServer.getIpAddress();
                String messageMSearch = 
                    "HOST:"+ adrress +"\n"+
                    "ssdp:msearch\n"+ 
                    "type:sensor";
                messageNotify = 
                    "HOST:" + adrress + "\n"+
                    "ssdp:notify\n"+
                    "type:sensor\n"+
                    "data:" + data;
                String mqttData = "20";
                InetAddress group = InetAddress.getByName("239.255.255.250");
                int port = 1900;
                MulticastSocket socket = new MulticastSocket(port);
                //socket.setLoopbackMode(true);
                // Since we are deploying
                socket.setTimeToLive(1);
                //this on localhost only (For a subnet set it as 1)
                  
                socket.joinGroup(group);
                SocketFunctions.sendData(messageMSearch,group,port,socket);
                Thread t = new Thread(new
                ReadThreadSensor(socket,group,port));
                t.start();
                SocketFunctions.sendData(messageNotify,group,port,socket);
                while(true)
                {
                    Thread.sleep(3000);
                    SocketFunctions.sendData(messageNotify,group,port,socket);
                    if(!Sensor.MQTT_BROKER.equals("")){
                        MqttHelperSensor.sendMqttData(args[2]);
                    }
                }
            }
            catch(SocketException se)
            {
                System.out.println("Error creating socket");
                se.printStackTrace();
            }
            catch(IOException ie)
            {
                System.out.println("Error reading/writing from/to socket");
                ie.printStackTrace();
            }
            // catch (MqttException e) {
            //     System.out.println("Error publishing message");
            //     e.printStackTrace();
            // }
        }
        else
            return ;
    }
    
}
class ReadThreadSensor implements Runnable
{
    private MulticastSocket socket;
    private InetAddress group;
    private int port;
    public static final int MAX_LEN = 1000;
    ReadThreadSensor(MulticastSocket socket,InetAddress group,int port)
    {
        this.socket = socket;
        this.group = group;
        this.port = port;
    }
      
    @Override
    public void run()
    {
        while(true){

            String message = SocketFunctions.recvData(group,port,socket);
            String hostIp = message.split("\n")[0].split(":")[1];
            String messageType = message.split("\n")[1].split(":")[1];
            String messageSender = message.split("\n")[2].split(":")[1];
            if(!messageSender.equals("sensor")){
                
                if(messageType.equals("msearch")){
                    System.out.println("msearch received");
                    SocketFunctions.sendData(Sensor.messageNotify,group,port,socket);
                    
                }
                else if(messageType.equals("notify") && messageSender.equals("controller")){
                    Sensor.MQTT_BROKER = "tcp://"+hostIp+":4000";
                    //Sensor.MQTT_BROKER = "tcp://localhost:4000";
                    if(!Sensor.MQTT_BROKER.equals("") && !Sensor.MqttInitialized){
                        Sensor.MqttInitialized = true;
                        MqttHelperSensor.initMqtt();
                    }
                }
            }
        } 
    }



}
class SocketFunctions{
   
    
    public static void sendData(String message, InetAddress group, int port, MulticastSocket socket){
        try{
            byte[] buffer = message.getBytes();
            DatagramPacket datagram = new DatagramPacket(buffer,buffer.length,group,port);
            socket.send(datagram);
            System.out.println("Sending data...");
        }
        catch(IOException ie)
        {
            System.out.println("Error reading/writing from/to socket");
            ie.printStackTrace();
        
        }
    }
    public static String recvData(InetAddress group, int port, MulticastSocket socket){
            String message = "";
            byte[] buffer = new byte[ReadThread.MAX_LEN];
            DatagramPacket datagram = new
            DatagramPacket(buffer,buffer.length,group,port);
            try
            {
                socket.receive(datagram);
                message = new
                String(buffer,0,datagram.getLength(),"UTF-8");
            }
            catch(IOException e)
            {
                System.out.println("Socket closed!");
            }
            return message;
        
    }
    public static String getIndexDevice(String list[],String name){
        for(int i=0;i<list.length;i++){
            if(list[i].equals(name)){
                return String.valueOf(i);
            }
        }
        return "0";
    }
    public static String getIpAddress(){
        final Pattern pattern = Pattern.compile("192", Pattern.CASE_INSENSITIVE);
        try{ 
            Enumeration en = NetworkInterface.getNetworkInterfaces(); 
            while (en.hasMoreElements()) { 
                NetworkInterface ni = (NetworkInterface) en.nextElement(); 
                Enumeration ee = ni.getInetAddresses(); 
                while (ee.hasMoreElements()) { 
                    InetAddress ia = (InetAddress) ee.nextElement(); 
                    String ip = ia.getHostAddress(); 
                    Matcher matcher = pattern.matcher(ip);
                    boolean matched = matcher.find();
                    if(matched)
                      return ip;
                } 
            } 
        } 
        catch(Exception e){ 
        }
        return "";
  }
}
class MqttHelperSensor{

    public static void initMqtt(){
        try{
            System.out.println(Sensor.MQTT_BROKER);
            Sensor.client = new MqttClient(Sensor.MQTT_BROKER, MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            Sensor.client.connect(options);
            System.out.println(Sensor.client);
      }
        catch (MqttException e) {
            System.out.println("Error publishing message");
            e.printStackTrace();
        }
    }
    public static void sendMqttData(String type){
        int value;
        try{
            if(type.equals("Temperature")){
                Random rand = new Random();
                value = rand.nextInt(30 - 10 + 1) + 10;
                MqttMessage mqttMessage = new MqttMessage(String.valueOf(value).getBytes());
                Sensor.client.publish("plastenik/biljka/" + type,mqttMessage);
                System.out.println(value);
            }
            else if(type.equals("Humidity")){
                Random rand = new Random();
                value = rand.nextInt(101);
                MqttMessage mqttMessage = new MqttMessage(String.valueOf(value).getBytes());
                Sensor.client.publish("plastenik/biljka/" + type,mqttMessage);
                System.out.println(value);
            }
            else{
                Random rand = new Random();
                value = rand.nextInt(1000);
                MqttMessage mqttMessage = new MqttMessage(String.valueOf(value).getBytes());
                Sensor.client.publish("plastenik/biljka/" + type,mqttMessage);
                System.out.println(value);
            }
        }
        catch (MqttException e) {
            System.out.println("Error publishing message");
            e.printStackTrace();
        }
    }
}
