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
public class Application 
{
    public static String MQTT_BROKER = "";
    public static final String MQTT_TOPIC = "device/status";
    public static MqttClient client;
    public static final String deviceCategoryList[] = new String[]{"Sensor","Actuator","Controller","Application"};
    public static final String deviceTypeSensor[] = new String[]{"Temperature","Humidity","Light"};
    public static final String deviceTypeActuator[] = new String[]{"Fan","Pump","Light"};
    static volatile boolean finished = false;
    public static String messageNotify="";
    public static String adrress;
    public static boolean MqttInitialized = false;
    public static String sensorId;
    public static void main(String[] args)  throws InterruptedException
    {
        if(args.length == 2){
            try
            {
                String data = SocketFunctionsApplication.getIndexDevice(deviceCategoryList,args[1]);
                System.out.println(data);
                if(args[1].equals("Sensor")){
                    data = data + "|" + SocketFunctionsApplication.getIndexDevice(deviceTypeSensor,args[2]) + "|" + args[3];
                }
                else if(args[1].equals("Actuator")){
                    data = data + "|" + SocketFunctionsApplication.getIndexDevice(deviceTypeActuator,args[2]) + "|" + args[3];
                }
                else if(args[1].equals("Controller")){
                    data = data + "|2|0";
                }
                else
                    data = data + "|3|0";
                //adrress = SocketFunctionsSensor.getIpAddress();
                adrress = args[0];
                String messageMSearch = 
                    "HOST:"+ adrress +"\n"+
                    "ssdp:msearch\n"+ 
                    "type:Application";
                messageNotify = 
                    "HOST:" + adrress + "\n"+
                    "ssdp:notify\n"+
                    "type:Application\n"+
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
                SocketFunctionsApplication.sendData(messageMSearch,group,port,socket);
                Thread t = new Thread(new
                ReadThreadApplication(socket,group,port));
                t.start();
                SocketFunctionsApplication.sendData(messageNotify,group,port,socket);
                while(true)
                {
                    Thread.sleep(3000);
                    SocketFunctionsApplication.sendData(messageNotify,group,port,socket);
                    if(!Application.MQTT_BROKER.equals("")){
                        MqttHelperApplication.sendMqttData();
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
class ReadThreadApplication implements Runnable
{
    private MulticastSocket socket;
    private InetAddress group;
    private int port;
    public static final int MAX_LEN = 1000;
    ReadThreadApplication(MulticastSocket socket,InetAddress group,int port)
    {
        this.socket = socket;
        this.group = group;
        this.port = port;
    }
      
    @Override
    public void run()
    {
        while(true){

            String message = SocketFunctionsApplication.recvData(group,port,socket);
            String hostIp = message.split("\n")[0].split(":")[1];
            String messageType = message.split("\n")[1].split(":")[1];
            String messageSender = message.split("\n")[2].split(":")[1];
            if(!messageSender.equals("sensor")){
                
                if(messageType.equals("msearch")){
                    System.out.println("msearch received");
                    SocketFunctionsApplication.sendData(Application.messageNotify,group,port,socket);
                    
                }
                else if(messageType.equals("notify") && messageSender.equals("controller")){
                    Application.MQTT_BROKER = "tcp://"+hostIp+":4000";
                    //Sensor.MQTT_BROKER = "tcp://localhost:4000";
                    if(!Application.MQTT_BROKER.equals("") && !Application.MqttInitialized){
                        Application.MqttInitialized = true;
                        MqttHelperApplication.initMqtt();
                        MqttHelperApplication.subscribeToController();
                    }
                }
            }
        } 
    }



}
class SocketFunctionsApplication{
   
    
    public static void sendData(String message, InetAddress group, int port, MulticastSocket socket){
        try{
            byte[] buffer = message.getBytes();
            DatagramPacket datagram = new DatagramPacket(buffer,buffer.length,group,port);
            socket.send(datagram);
        }
        catch(IOException ie)
        {
            System.out.println("Error reading/writing from/to socket");
            ie.printStackTrace();
        
        }
    }
    public static String recvData(InetAddress group, int port, MulticastSocket socket){
            String message = "";
            byte[] buffer = new byte[ReadThreadApplication.MAX_LEN];
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
class MqttHelperApplication{

    public static void initMqtt(){
        try{
            Application.client = new MqttClient(Application.MQTT_BROKER, MqttClient.generateClientId());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            Application.client.connect(options);
      }
        catch (MqttException e) {
            System.out.println("Error publishing message");
            e.printStackTrace();
        }
    }
    public static void sendMqttData(){
        try{
            String message = "25";
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            Application.client.publish("plastenik/biljka/aplikacija/Temperature", mqttMessage); 
            Application.client.publish("plastenik/biljka/aplikacija/Humidity", mqttMessage); 
            Application.client.publish("plastenik/biljka/aplikacija/Light", mqttMessage); 
        }
        catch (MqttException e) {
            System.out.println("Error publishing message");
            e.printStackTrace();
        }
    }
    public static void subscribeToController(){
        try{
            
            Application.client.subscribe("plastenik/biljka/kontroler/Temperature",(topic,controllerMessage) -> {
                String mqttMessage = new String(controllerMessage.getPayload());
                System.out.println("Value received from Controller for Temp :" + mqttMessage);
            }); 
            Application.client.subscribe("plastenik/biljka/kontroler/Humidity",(topic,controllerMessage) -> {
                String mqttMessage = new String(controllerMessage.getPayload());
                System.out.println("Value received from Controller for Humidity :" + mqttMessage);
            }); 
            Application.client.subscribe("plastenik/biljka/kontroler/Light",(topic,controllerMessage) -> {
                String mqttMessage = new String(controllerMessage.getPayload());
                System.out.println("Value received from Controller for Light :" + mqttMessage);
            }); 
        }
        catch(MqttException e){
            e.printStackTrace();
        }
    }
}

