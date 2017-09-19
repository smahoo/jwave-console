package de.smahoo.jwave.console;

import de.smahoo.jwave.JWaveController;
import de.smahoo.jwave.JWaveControllerMode;
import de.smahoo.jwave.cmd.JWaveCommand;
import de.smahoo.jwave.cmd.JWaveCommandClass;
import de.smahoo.jwave.cmd.JWaveCommandClassSpecification;
import de.smahoo.jwave.cmd.JWaveNodeCommand;
import de.smahoo.jwave.event.JWaveErrorEvent;
import de.smahoo.jwave.event.JWaveEvent;
import de.smahoo.jwave.event.JWaveEventListener;
import de.smahoo.jwave.node.JWaveNode;
import de.smahoo.jwave.specification.JWaveSpecification;
import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.SerialPort;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.StringTokenizer;

/**
 * @author Mathias Runge (mathias.runge@smahoo.de)
 */

public class JWaveConsole implements Runnable {

	private static final String VERSION = "1.0.2";
	
	private static JWaveConsole instance = null;
	private static JWaveController cntrl = null;
	private static String cmdSpecificationPath = null;
	
	private static String currentSerialPort = null;
	private static boolean keepAlive = true;
	private static String configFile;
	private CommPort commPort = null;

	
	
	public void run(){		
		System.out.println("Using JWave v"+JWaveController.getVersion());
		JWaveCommandClassSpecification spec = null;
		String path;
		if (cmdSpecificationPath != null) {
			path = System.getProperty("user.dir") + System.getProperty("file.separator") + cmdSpecificationPath;
			System.out.print("loading Z-Wave Specification ("+cmdSpecificationPath+") ... ");
			try {
				spec = new JWaveCommandClassSpecification(path);
			} catch (Exception exc){
				System.out.println("ERROR");
				exc.printStackTrace();
				return;
			}
		} else {
			System.out.print("loading default Z-Wave Specification from resource ... ");
			try {
				spec = JWaveSpecification.loadDefaultSpecification();
			} catch (Exception exc){
				System.out.println("ERROR");
				exc.printStackTrace();
				return;
			}
		}

		System.out.print("generating controller ...");
			try {
				cntrl = new JWaveController(spec);
				System.out.println("OK");
			}catch (Exception exc){
				System.out.println("ERROR");
				System.out.println(exc.getMessage());
			}

		JWaveController.doLogging(true);
		if (cntrl == null){
			System.out.println("Unable to initialize Controller");
			return;
		}
		if (cntrl.getCommandClassSpecifications() == null){				
			// mist
			System.out.println("Controller was not initialized with zwave specifications. Will exit now");
			return;
		}
		
		cntrl.addCntrlListener(new JWaveEventListener() {
			

			public void onJWaveEvent(JWaveEvent event) {
				handleZWaveEvent(event);
				
			}
		});
				
		System.out.println("Ready! Please type command (type 'help' for command list):");
		//System.out.print("> ");
		while (keepAlive){
			try {
				Thread.sleep(100);
			} catch (InterruptedException exc){
				// do nothing - interrupt seems to be intended
			}
		}		
		try {
			cntrl.dispose();
			if (commPort != null){
				commPort.close();
			}
		} catch (Exception exc){
			exc.printStackTrace();
		}
	}
	
	protected void handleZWaveEvent(JWaveEvent event){
		switch (event.getEventType()){
			case ERROR_IO_CONNECTION:
				if (event instanceof JWaveErrorEvent){
					JWaveErrorEvent errEvnt = (JWaveErrorEvent)event;
					handleIOError(errEvnt.getMessage(),errEvnt.getThrowable());
				}
				break;
			default:
				break;
		}
	}
	
	
	protected void handleIOError(String message, Throwable throwable){
		System.out.println("IO-ERROR - "+message);
		if (commPort != null){			
			System.out.println("Serial Connection will be closed.");
			commPort.close();
		}
		
	}
	
	protected static boolean checkConnection(){
		if (cntrl.getControllerMode() == JWaveControllerMode.CNTRL_MODE_NOT_CONNECTED){
			System.out.println("Controller is not connected. Connect the controller first ('connect <serial port>')");
			return false;
		}
		return true;
	}
	
	protected void connect(String port){		
			
			int baudrate = 115200;
			
			CommPortIdentifier portIdentifier = null;
		    
			try {
		        	portIdentifier = CommPortIdentifier.getPortIdentifier(port);
		    } catch (NoSuchPortException exc){
		        	//exc.printStackTrace();
		    		System.out.println("ERROR: there exists no port with the name '"+port+"'."+"\r\n"+
		    						   "    ==>Type 'print serial' to get a list of available serial ports.");
		    		return;
		    }
		    
			if ( portIdentifier.isCurrentlyOwned() ) {
		            System.out.println("Error: Port '"+port+"' is currently in use");
		    } else   {
		    	try {
		          commPort = portIdentifier.open(this.getClass().getName(),2000);
		            
		            if ( commPort instanceof SerialPort )  {
		                SerialPort serialPort = (SerialPort) commPort;
		                serialPort.setSerialPortParams(baudrate,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);                    
		                serialPort.enableReceiveTimeout(500000);          
		                
		                cntrl.init(serialPort.getInputStream(),serialPort.getOutputStream());
		                currentSerialPort = port;
		              
		               
		            }  else  {
		            	
		         
		            }
		    	} catch (Exception exc){
		    		exc.printStackTrace();
		    	}
		   }  
		}
	
	
	
	
	protected static void printNodes(){
		if (cntrl==null){
			System.out.println("Controller is not initialized. Unable to print nodes.");
			return;
		}
		
		if (cntrl.getNodes().size() <=1){
			System.out.println("No nodes connected to this controller");
			return;
		}
		
		for (JWaveNode node : cntrl.getNodes()){
			System.out.println("");
			printNode(node);
		}
	}
	
	protected static void printNode(int id){
		for (JWaveNode node : cntrl.getNodes()){
			if (node.getNodeId() == id){
				printNode(node);
				return;
			}
		}
		System.out.println("There exists no node with id = "+id);
	}
	
	protected static void printNode(JWaveNode node) {
		System.out.println("===========================================================================");		
		System.out.println("             NODE "+node.getNodeId()+" | 0x"+Integer.toHexString(node.getGenericDeviceType().getKey())+" | "+node.getGenericDeviceType().getName());
		System.out.println("---------------------------------------------------------------------------");
		System.out.println("    Manufacturer 0x"+Integer.toHexString(node.getManufactureId()));
		System.out.println("    Product Type 0x"+Integer.toHexString(node.getProductTypeId()));
		System.out.println("         Product 0x"+Integer.toHexString(node.getProductId()));		
		System.out.println("---------------------------------------------------------------------------");
		System.out.println(" COMMAND CLASSES");
		for (JWaveCommandClass cc : node.getCommandClasses()){
			System.out.println("   0x"+Integer.toHexString(cc.getKey())+" "+cc.getName());
		}		
		System.out.println("---------------------------------------------------------------------------");
	}
	
	
	public static void evaluateParamCmd(String cmd){
		StringTokenizer tok = new StringTokenizer(cmd," ");
		String[] pcmd = new String[tok.countTokens()];
		for (int i = 0; i<pcmd.length; i++){
			pcmd[i] = tok.nextToken();
		}
		
		if ("send".equals(pcmd[0])){
			evalSendCmd(pcmd);
			return;
		}
		if ("connect".equalsIgnoreCase(pcmd[0])){
			evalConnectCmd(pcmd);
			return;
		}		
		if ("print".equalsIgnoreCase(pcmd[0])){
			evalPrintCmd(pcmd);
			return;
		}
			
		if ("set".equalsIgnoreCase(pcmd[0])){
			evalSetCmd(pcmd);
			return;
		}
		if ("load".equalsIgnoreCase(pcmd[0])){
			evalLoadCmd(pcmd);
			return;
		}		
		if ("save".equalsIgnoreCase(pcmd[0])){
			evalSaveCmd(pcmd);
			return;
		}
		System.out.println("Unknown command ("+pcmd[0]+").");
	}
	
	protected static void reset(){		
		if (!checkConnection()){
			return;
		}
		System.out.println("Resetting the controller");
		cntrl.resetController();
		return;
	}
	
	protected static void evalSaveCmd(String[] cmd){
		if (cmd.length < 2){
			System.out.println("invalid save cmd");
		}
		saveSpecification(cmd[1]);;
	}
	
	
	protected static void evalLoadCmd(String[] cmd){
		if (cmd.length < 2){
			System.out.println("invalid load cmd");
		}
		loadSpecification(cmd[1]);;
	}
	
	
	protected static void saveSpecification(String path){	
		System.out.println("Saving nodes configuration");
		try {
			cntrl.saveConfiguration(path);
		} catch (Exception exc){
			System.out.println("Error during saving ("+exc.getMessage()+")");
			return;
		}
		System.out.println("Specification saved");
	}
	
	protected static void loadSpecification(String path){
		if (cntrl.getControllerMode() == JWaveControllerMode.CNTRL_MODE_NOT_CONNECTED){
			System.out.println("Controller is not connected. Connect the controller first ('connect <serial port>')");
			return;
		}
		
		System.out.println("Loading nodes configuration");
		
		File f = new File(path);
		if (!f.exists()){
			System.out.println("file '"+path+"' does not exist.");
			return;
		}
		try {
			cntrl.loadConfiguration(path);
		} catch (Exception exc){
			System.out.println("Error during loading ("+exc.getMessage()+")");
			return;
		}
		System.out.println("Specification loaded");
	}
	
	protected static void evalSetCmd(String[] cmd){
		if (cntrl.getControllerMode() == JWaveControllerMode.CNTRL_MODE_NOT_CONNECTED){
			System.out.println("Controller is not connected. Connect the controller first ('connect <serial port>')");
			return;
		}
		if (cmd.length < 2){
			System.out.println("Invalid set cmd");
		}
		if ("inclusion".equalsIgnoreCase(cmd[1])){
			System.out.println("Setting inclusion mode");
			cntrl.setInclusionMode(true);		
			return;
		}
		
		if ("exclusion".equalsIgnoreCase(cmd[1])){
			System.out.println("Setting exclusion mode");
			cntrl.setExlusionMode();
			return;
		}
		if ("normal".equalsIgnoreCase(cmd[1])){
			System.out.println("Setting controller back to normal mode");
			cntrl.setNormalMode();
			return;
		}
		
		System.out.println("Unknown set command ("+cmd[1]+")");
	}
	
	protected static void evalConnectCmd(String[] cmd){
		if (cmd.length < 2){
			System.out.println("Unvalid connect command");
		}
		System.out.println("Connecting to "+cmd[1]);
		instance.connect(cmd[1]);
	}
	
	public static void evalPrintCmd(String[] cmd){
		if (cmd.length < 2){
			System.out.println("Invalid print command");
			return;
		}
		
		if ("version".equalsIgnoreCase(cmd[1])){
			System.out.println("v"+VERSION);
			return;
		}
		if ("serial".equalsIgnoreCase(cmd[1])){
			printPorts();
			return;
		}
		if ("commands".equalsIgnoreCase(cmd[1])){
			printHelp();
			return;
		}
		if ("nodes".equalsIgnoreCase(cmd[1])){
			printNodes();
			return;
		}
		if ("node".equalsIgnoreCase(cmd[1])){
			if (cmd.length == 3){
				printNode(parseInt(cmd[2]));
			} else {
				System.out.println("Invalid print node command");
			}
			return;
		}
		if ("controller".equalsIgnoreCase(cmd[1])){
			printControllerDetails();
			return;
		}
		System.out.println("Unknown print command ("+cmd[1]+")");
	}
	
	protected static void printControllerDetails(){
		if (cntrl != null){
			if (cntrl.getControllerMode() == JWaveControllerMode.CNTRL_MODE_NOT_CONNECTED){
				System.out.println("Z-Wave Controller is not connected. Connect the controller to a serial port (use cmd \"connect <portname>\")");
				return;
			}
			System.out.println("--------------------------------------------------"+"\r\n"+
							   "                  Controller Details"+"\r\n"+
							   "--------------------------------------------------"+"\r\n"+
							   ""+"\r\n"+
							   "          connected to port = "+currentSerialPort+"\r\n"+
							   "             z-wave home id = "+cntrl.getHomeId()+"\r\n"+
							   "  z-wave controller version = "+cntrl.getControllerVersion()+"\r\n"+
							   "        z-wave chip version = "+cntrl.getZWaveChipVersion()+"\r\n"+
							   "--------------------------------------------------"+"\r\n");					
		}
	}
	
	protected static void printPorts(){
		System.out.println("available serial ports:"+"\r\n"+
	                        "-----------------------------------");
		
		@SuppressWarnings("unchecked")
		Enumeration<CommPortIdentifier> ports = CommPortIdentifier.getPortIdentifiers();
		while (ports.hasMoreElements()){
			System.out.println("   "+ports.nextElement().getName());
		}
		System.out.println("-----------------------------------");
	}
	
	
	public static void evalSendCmd(String[] cmd){
		int version = 1;	
		boolean containsVersionParam = false;
		int nodeId;
		JWaveNode node = null;
		JWaveCommand zwaveCmd = null;
		if (cmd.length < 4){
			System.out.println("Unvalid send command -> send <id> <cmd_class_id> <cmd_id> [[param_value]]");
			return;
		}
		
		try {
			nodeId = Integer.parseInt(cmd[1]);
			
		} catch (Exception exc){
			System.out.println("Unvalid node Id ("+exc.getMessage()+")");
			return;
		}	
		
		node = cntrl.getNode(nodeId);
		if (node == null){
			System.out.println("There exists no node with id "+nodeId);
			return;
		}
	
		
		if (cmd.length > 4){
			if (cmd[4].contains("-v=")){
				containsVersionParam = true;
				try {
					version = Integer.parseInt(cmd[4].replace("-v=",""));
				} catch (Exception exc){
					System.out.println("Unvalid version parameter ("+cmd[4]+")");
				}
			}
		}
		
		zwaveCmd = getNodeCmd(cmd[2], cmd[3],version);
		
		
		if (zwaveCmd == null){
			System.out.println("Unable to find Z-Wave Command "+cmd[2]+" "+cmd[3]+" of version "+version);
			return;
		}		
			
		
		JWaveNodeCommand nodeCmd = new JWaveNodeCommand(zwaveCmd);
		
		
		int paramStart;
		if (containsVersionParam){
			paramStart = 5;
		} else {
			paramStart = 4;
		}
		
		if (paramStart < cmd.length){
			for (int i = paramStart; i< cmd.length; i++){
				try {
					nodeCmd.setParamValue(i-paramStart, parseInt(cmd[i]));
					
				} catch (Exception exc){
					System.out.println("Unable to set param value ("+(i-paramStart)+" "+cmd[i]+")");
					return;
				}
			}
		} 
		
		node.sendData(nodeCmd);
		
		
	}
	
	protected static int parseInt(String value) throws NumberFormatException{
		if (value.contains("0x")){
			return Integer.parseInt(value.replace("0x",""),16);
		}
		return Integer.parseInt(value);
	}
	
	protected static JWaveCommand getNodeCmd(String cl, String cmd, int version){
		int class_key = -1;
		int cmd_key = -1;
		try {			
			class_key = parseInt(cl);	
			
		} catch (Exception exc){
			
		}
		try {			
			cmd_key = parseInt(cl);	
			
		} catch (Exception exc){
			
		}
		
		JWaveCommandClass cmdClass = null;
		
		if (class_key != -1){
			cmdClass = cntrl.getCommandClassSpecifications().getCommandClass(class_key,version);
		} else {
			cmdClass = cntrl.getCommandClassSpecifications().getCommandClass(cl,version);
		}
		
		if (cmdClass == null){
			return null;
		}
		
		JWaveCommand zwaveCmd = null;
		
		if (cmd_key == -1){
			zwaveCmd = cmdClass.getCommand(cmd);
		} else {
			zwaveCmd = cmdClass.getCommand(cmd_key);
		}
		
		return zwaveCmd;
	}
	
	public static void evalCmd(String cmd){
		if (cmd.contains(" ")){
			evaluateParamCmd(cmd);
			return;
		}
		if ("help".equalsIgnoreCase(cmd)){
			printHelp();
			return;
		}
		if ("save".equalsIgnoreCase(cmd)){
			saveSpecification(configFile);
			return;
		}
		if ("load".equalsIgnoreCase(cmd)){			
			loadSpecification(configFile);			
			return;
		}		
		if ("reset".equalsIgnoreCase(cmd)){
			reset();
			return;
		}
		if ("exit".equalsIgnoreCase(cmd)){			
			keepAlive = false;
			return;
		}
		System.out.println("unknown command ("+cmd+"). Type 'print commands' for a list of possible commands.");
	}
	
	
	protected static void printHelp(){
		System.out.println("======================================================================================="+"\r\n"+	
						   "                                        HELP"+"\r\n"+	
						   "======================================================================================="+"\r\n"+	
						   ""+"\r\n"+				
						   "       save = saving nodes configuration"+"\r\n"+
						   "              ==> use: save [filename]"+"\r\n"+	
						   "       load = loading nodes configuration"+"\r\n"+	
						   "              ==> use: load [filename]" + "\r\n" +
						   "\r\n"+	
						   "        set = sets parameter"+"\r\n"+	
						   "              ==> use: set <command>"+"\r\n"+	
						   "              set inclusion    = sets controller to inclusion mode"+"\r\n"+	
						   "              set exclusion    = sets controller to exclusion mode"+"\r\n"+	
						   "              set normal       = sets controller to normal mode"+"\r\n"+					
						   "\r\n"+	
						   "      reset = resets the controller"+"\r\n"+
						   "\r\n"+
						   "    connect = connect with z-wave controller"+"\r\n"+			
						   "              ==> use: connect <portname>"+"\r\n"+			
						   "\r\n"+	
						   "       send = sends a command to a node"+"\r\n"+			
						   "              ==> use: send <id> <cmd_class> <cmd> [-v=<version>] [[param_value]]"+"\r\n"+	
						   "\r\n"+	
						   "      print = prints something on the console"+"\r\n"+			
						   "              ==> use: print <what to print> [[additional params]]"+"\r\n"+	
						   "              print commands   = prints this help"+"\r\n"+	
						   "              print serial     = prints all available serial ports"+"\r\n"+	
						   "              print version    = prints the version of this application"+"\r\n"+	
						   "              print nodes      = prints alle node details"+"\r\n"+	
						   "              print node <id>  = prints the node details of specific node"+"\r\n"+	
						   "              print controller = prints details about the current z-wave controller"+"\r\n"+			
						   "=======================================================================================");
	}
	
	
	public static void main(String[] args){		
		String jwaveConsoleStr = "============================================================================================================"+"\r\n"+
				 "                __ __      __                                                        __"+"\r\n"+          
				 "               |__/  \\    /  \\_____ ___  __ ____     ____  ____   ____   __________ |  |   ____"+"\r\n"+  
				 "               |  \\   \\/\\/   /\\__  \\\\  \\/ // __ \\  _/ ___\\/  _ \\ /    \\ /  ___/  _ \\|  | _/ __ \\"+"\r\n"+ 
				 "               |  |\\        /  / __ \\\\   /\\  ___/  \\  \\__(  <_> )   |  \\\\___ (  <_> )  |_\\  ___/ "+"\r\n"+
				 "           /\\__|  | \\__/\\  /  (____  /\\_/  \\___  >  \\___  >____/|___|  /____  >____/|____/\\___  >"+"\r\n"+
				 "           \\______|      \\/        \\/          \\/       \\/           \\/     \\/                \\/     v"+VERSION+"\r\n"+
				 " -----------------------------------------------------------------------------------------------------------"+"\r\n"+
				 "                                send commands directly to the z-wave controller       "+"\r\n"+
				 "============================================================================================================";

		System.out.println(jwaveConsoleStr);		
		System.out.println("");
		System.out.println("");
		System.out.println("");
		
		configFile = System.getProperty("user.dir")+System.getProperty("file.separator")+"cnf"+System.getProperty("file.separator")+"nodes.xml";

		if (args.length > 0){
			cmdSpecificationPath = args[0];
		} else {
			cmdSpecificationPath = null;
		}

		instance = new JWaveConsole();
		Thread t = null;
	    try {
	         t = new Thread(instance);
	         t.start();
	    } catch (Exception exc){
	    	exc.printStackTrace();
	    }
	    	    
	    BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
		
		String cmd = null;
		
	    while (keepAlive) {
	    	try {	    		
	    		cmd = console.readLine();
	    		if (cmd.length()!= 0){	    						
	    			evalCmd(cmd);
	    		//	System.out.print("> ");
	    		}
	    	} catch (Exception exc){
	    		exc.printStackTrace();
	    	}
	    }
	  
	    
	}
	
}
