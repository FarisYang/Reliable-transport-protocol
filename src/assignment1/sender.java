//package assignment1;

import java.io.*;
import java.net.*;
import java.util.*;

public class sender extends Thread{

	static List<byte[]> file_set=new ArrayList<byte[]>();
	static String whole_file="";
	static List<String[]> log = new ArrayList<String[]>();
	static List<int[]> send_confirm = new ArrayList<int[]>();
	static int num = file_set.size();
	static int need_send=0;
	static int last_confirm=0;
	static int WS = 0;
	static int next_send_seq= 0;
	static int next_send_ack= 0;
	static long start_time;
	static int timeout=0;
	static int MWS=0;
	static DatagramSocket sendSocket;
	static InetAddress IpAddress;
	static int port;
	static int MSS ;
	static boolean is_end=false;
	static int all_data_byte=0;
	static int num_segment=0;
	static int num_retransmit=0;
	static int num_duplicate=0;
	static int num_drop=0;
	static double pdrop;
	static boolean all_confirm=true;
	public volatile boolean stop_thread=false;
	static int[] hand3=new int[2];
	static String filename;
	
	public static void main(String[] args) throws Exception
	{
		start_time = System.currentTimeMillis();
		if (args.length != 8) {
		       System.out.println("Incorrect arguments");
		       return;
		    }
	    IpAddress = InetAddress.getByName(args[0]);
	    port = Integer.parseInt(args[1]);
	    filename = args[2];
	    MWS = Integer.parseInt(args[3]);
	    MSS = Integer.parseInt(args[4]);
	    timeout= Integer.parseInt(args[5]);
	    pdrop = Float.parseFloat(args[6]);
	    long seed = Long.parseLong(args[7]);
	    
	    sendSocket = new DatagramSocket();
	    byte[] establish2 = new byte[1024];
	    int syn1 = 1;
	    Random random = new Random(seed);
	    int seq = random.nextInt(500);
	    String send1 = "syn"+syn1+" seq"+seq;
	    byte[] establish1 = send1.getBytes();
	    DatagramPacket handshake1 = new DatagramPacket(establish1, establish1.length, IpAddress, port);
	    long handshake1_time = System.currentTimeMillis()-start_time;
	    write_in_log("snd",Long.toString(handshake1_time),"S",Integer.toString(seq),"0","0");
	    sendSocket.send(handshake1);
	    
	    
	    DatagramPacket handshake2 = new DatagramPacket(establish2, establish2.length);
	    sendSocket.receive(handshake2);
	    long handshake2_time = System.currentTimeMillis()-start_time;
	    String receive1 = new String(handshake2.getData());
	    receive1=receive1.trim();
	    String[] receive1_set = receive1.split(" ");
	    int syn2 = Integer.parseInt(receive1_set[0].substring(3));
	    int seq2 = Integer.parseInt(receive1_set[1].substring(3));
	    int ack1 = Integer.parseInt(receive1_set[2].substring(3));
	    write_in_log("rcv",Long.toString(handshake2_time),"SA",Integer.toString(seq2),"0",Integer.toString(ack1));
	    
	    if (ack1==seq+1)
	    {
	    	if(syn2==1)
	    		syn2=0;
	    	seq2+=1;
	    	String send2 = "syn"+syn2+" seq"+ack1+" ack"+seq2;
	    	byte[] establish3 = send2.getBytes();
	    	DatagramPacket handshake3 = new DatagramPacket(establish3, establish3.length, IpAddress, port);
	    	long handshake3_time = System.currentTimeMillis()-start_time;
	    	write_in_log("snd",Long.toString(handshake3_time),"A",Integer.toString(ack1),"0",Integer.toString(seq2));
	    	sendSocket.send(handshake3);
	    	hand3[0]=ack1;
	    	hand3[1]=0;
	    	
	    	
	    	// after 3 handshakes
	    	get_file(filename, MSS);
	    	create_confirm();
	    	next_send_seq= ack1;
	    	next_send_ack= seq2;
	    	sender sender_receive=new sender();
	    	sender_receive.start();
	    	//start send data
	    	while(true)
	    	{
	    		if(WS<=MWS)
	    		{
	    			send_packet(next_send_seq,next_send_ack,need_send,0);
	    			need_send+=1;
	    			if(need_send==file_set.size())
	    				break;
	    		}
	    		else
	    			System.out.println("wait window size");
	    	}
	    }
	    
	}
	
	public static void write_in_log(String action, String time, String type_of_packet, String seq, String length, String ack)
	{
		String[] each_log =new String[6];
		each_log[0]=action;
		each_log[1]=time;
		each_log[2]=type_of_packet;
		each_log[3]=seq;
		each_log[4]=length;
		each_log[5]=ack;
		log.add(each_log);
		System.out.println(action+"  "+time+"  "+type_of_packet+"  "+seq+"  "+length+"  "+ack);
	}
	
	public static void get_file(String filename, int MSS) throws Exception
	{
		File file = new File(filename);
		String line;
	    if (file.isFile() && file.exists())
	    {
	    	//InputStreamReader isr = new InputStreamReader(new FileInputStream(file), "utf-8");
	  	    BufferedReader br = new BufferedReader(new FileReader(file));
	  	    while ((line=br.readLine()) != null) 
  	        {
	  	    	if(line.trim().equals(""))
	  	    		whole_file+="\r\n";
	  	    	else 
	  	    		whole_file+=line+"\r\n";
  	        }
            br.close();   
            //isr.close();
            whole_file=whole_file.substring(0, whole_file.length()-2);
            int add_newline=num_newline();
            if(add_newline!=0)
            {
            	for(int i=0;i<add_newline;i++)
            		whole_file+="\r\n";
            }
	    }
	    else
	    	System.out.println("No such file");
	    byte[] file_in_byte = whole_file.getBytes();
	    all_data_byte=file_in_byte.length;
	    int num=file_in_byte.length/MSS;
	    for (int i=0; i<num; i++)
	    {
	    	byte[] each_set = new byte[MSS];
	    	for (int j=0; j<MSS; j++)
	    	{
	    		each_set[j]=file_in_byte[j+i*MSS];
	    	}
	    	file_set.add(each_set);
	    }
	    byte[] end_set = new byte[file_in_byte.length-num*MSS];
	    if(file_in_byte.length>num*MSS)
	    {
	    	int a=0;
	    	for(int b=num*MSS; b<file_in_byte.length; b++)
	    	{
	    		end_set[a]=file_in_byte[b];
	    		a++;
	    	}
	    	file_set.add(end_set);
	    }
	}
	
	public static int num_newline() throws Exception
	{
		int num_min=1;
		File file = new File(filename);
		RandomAccessFile rf = new RandomAccessFile(file,"r");
		for (num_min=1; num_min<rf.length(); num_min++)
		{
			rf.seek(rf.length()-num_min);
	        String line = rf.readLine();
	        if(line.length()!=0)
	        	break;
		}
        rf.close();
        return (num_min-1)/2-1;
	}
	
	//each_confirm[index, send_seq, send_ack, want_ack, num_confirm]
	public static void create_confirm() throws Exception
	{
		for(int i=0; i<file_set.size(); i++)
		{
			int[] each_confirm = new int[6];
			each_confirm[0]=i;
			each_confirm[1]=0;
			each_confirm[2]=0;
			each_confirm[3]=0;
			each_confirm[4]=0;
			each_confirm[5]=0;
			send_confirm.add(each_confirm);
		}
	}
	
	public static void send_packet(int seq, int ack, int data_index, int is_retrans) throws Exception
	{
		int length_now=0;
		String send;
		Random random = new Random();
		double number=random.nextDouble();
		send_confirm.get(data_index)[5]=0;
		if(number>pdrop)
		{
		length_now=file_set.get(data_index).length;
		if(is_retrans==0)
			send = "seq"+seq+" ack"+ack+" data"+new String(file_set.get(data_index));
		else {
			send = "seq"+seq+" ack"+ack+" data-re"+new String(file_set.get(data_index));
		}
		byte[] send_byte=send.getBytes();
		DatagramPacket send_packet = new DatagramPacket(send_byte, send_byte.length, IpAddress, port);
		long send_time = System.currentTimeMillis()-start_time;
		write_in_log("snd",Long.toString(send_time),"D",Integer.toString(seq),Integer.toString(length_now),Integer.toString(ack));
		
		
		send_confirm.get(data_index)[1]=seq;
		send_confirm.get(data_index)[2]=ack;
		send_confirm.get(data_index)[3]=seq+length_now;
		sendSocket.send(send_packet);
		if(is_retrans==0)
		{
			next_send_seq=seq+length_now;
			WS+=length_now;
		}
		}
		else
		{
			length_now=file_set.get(data_index).length;
			long send_time = System.currentTimeMillis()-start_time;
			write_in_log("drop",Long.toString(send_time),"D",Integer.toString(seq),Integer.toString(length_now),Integer.toString(ack));
			num_drop+=1;
			send_confirm.get(data_index)[1]=seq;
			send_confirm.get(data_index)[2]=ack;
			send_confirm.get(data_index)[3]=seq+length_now;
			if(is_retrans==0)
				next_send_seq=seq+length_now;
		}
		
		Timer timer = new Timer();
		
		TimerTask task= new TimerTask()
		{
			public void run() {
				//start timeout action
				send_confirm.get(data_index)[5]=1;
				if(send_confirm.get(data_index)[4]==0)
				{
					
					while(true)
					{
						if(WS<=MWS)
						{
							try
							{
								num_retransmit+=1;
								send_packet(seq, ack, data_index,1);
								timer.cancel();
								break;
							}
							catch (Exception e) {
								// TODO: handle exception
							}
						}
					}
				}
				else
					timer.cancel();
					
			}
		};
		timer.schedule(task, timeout);
	}
	
	public void run()
	{  
        try {  
        		while(!stop_thread)
        		{
        			byte[] receive_byte = new byte[1024];
        			DatagramPacket receive_packet = new DatagramPacket(receive_byte, receive_byte.length);
        		    sendSocket.receive(receive_packet);
        		    long receive_time = System.currentTimeMillis()-start_time;
        		    String receive_String = new String(receive_packet.getData());
        		    receive_String=receive_String.trim();
        		    String[] receive_split = receive_String.split(" ");
        		    int receive_seq = Integer.parseInt(receive_split[0].substring(3));
        		    int receive_ack = Integer.parseInt(receive_split[1].substring(3));
        		    write_in_log("rcv",Long.toString(receive_time),"A",Integer.toString(receive_seq),"0",Integer.toString(receive_ack));
        		    //next_send_seq=receive_ack;
        		    next_send_ack=receive_seq;
        		    
        		    if(receive_ack==hand3[0])
        		    {
        		    	next_send_ack+=1;
        		    	hand3[1]+=1;
        		    	if(hand3[1]>=2)
        		    		num_duplicate+=1;
        		    	if(hand3[1]==3 && send_confirm.get(0)[5]==0)
        		    	{
        		    		num_retransmit+=1;
		    				 send_packet(send_confirm.get(0)[1], send_confirm.get(0)[2], send_confirm.get(0)[0], 1);
		    				 
        		    	}
        		    }
        		    
        		    for(int a=0; a<send_confirm.size(); a++)
        		    {
        		    	if(receive_ack==send_confirm.get(a)[3])
        		    	{
        		    		send_confirm.get(a)[4]+=1;
        		    		WS=WS-(send_confirm.get(a)[3]-send_confirm.get(a)[1]);
        		    		for(int b=0; b<send_confirm.size(); b++)
        		    		{
        		    			if(send_confirm.get(b)[4]==0)
        		    			{
        		    				all_confirm=false;
        		    				break;
        		    			}
        		    			all_confirm=true;
        		    		}
        		    		if(all_confirm)
        		    			is_end=true;
        		    		if(send_confirm.get(a)[4]>=2)
        		    			num_duplicate+=1;
        		    		if(send_confirm.get(a)[4] !=1 && (send_confirm.get(a)[4]-1)%3==0 && send_confirm.get(a)[5]==0)
        		    		{
        		    			
        		    				 num_retransmit+=1;
        		    				 send_packet(send_confirm.get(a+1)[1], send_confirm.get(a+1)[2], send_confirm.get(a+1)[0], 1);
        		    				 
        		    		}
        		    		break;
        		    	}
        		    }
        		    
        		    if(is_end)
        		    {
        		    	start_fin(send_confirm.get(send_confirm.size()-1)[3],send_confirm.get(send_confirm.size()-1)[2]);
        		    	stop_thread=true;
        		    	sendSocket.close();
    	    			create_log_file();
        		    }
        		}
        } catch (Exception e) {   
        }  
    }  

	
	public static void start_fin(int seq, int ack) throws Exception	
	{
		int fin=1;
		String end_string1 = "fin"+fin+" seq"+seq+" ack"+ack;
		byte[] end_byte1=end_string1.getBytes();
		DatagramPacket end_packet1 = new DatagramPacket(end_byte1, end_byte1.length, IpAddress, port);
		long end_time1 = System.currentTimeMillis()-start_time;
		write_in_log("snd",Long.toString(end_time1),"F",Integer.toString(seq),"0",Integer.toString(ack));
		sendSocket.send(end_packet1);
		
		
		byte[] end_byte2 = new byte[1024];
		DatagramPacket end_packet2 = new DatagramPacket(end_byte2, end_byte2.length);
	    sendSocket.receive(end_packet2);
	    long end_time2 = System.currentTimeMillis()-start_time;
	    String end_string2 = new String(end_packet2.getData());
	    end_string2=end_string2.trim();
	    String[] end_split2 = end_string2.split(" ");
	    fin = Integer.parseInt(end_split2[0].substring(3));
	    int end_seq2 = Integer.parseInt(end_split2[1].substring(3));
	    int end_ack2 = Integer.parseInt(end_split2[2].substring(3));
	    write_in_log("rcv",Long.toString(end_time2),"FA",Integer.toString(end_seq2),"0",Integer.toString(end_ack2));
	    
	    fin=0;
	    String end_string3 = "fin"+fin+" seq"+end_ack2+" ack"+(end_seq2+1);
		byte[] end_byte3=end_string3.getBytes();
		DatagramPacket end_packet3 = new DatagramPacket(end_byte3, end_byte3.length, IpAddress, port);
		long end_time3 = System.currentTimeMillis()-start_time;
		write_in_log("snd",Long.toString(end_time3),"A",Integer.toString(end_ack2),"0",Integer.toString(end_seq2+1));
		sendSocket.send(end_packet3);
		
	}
	
	public static void create_log_file() throws Exception
	{
		String log_file="Sender_log.txt";
		File file = new File(log_file);
		String each_line="";
		String statistic1="Amount of Data Transferred (in bytes): "+all_data_byte+"\n";
		String statistic2="Number of Data Segments Sent (excluding retransmissions): "+num_send_no_retrans()+"\n";
		String statistic3="Number of Packets Dropped: "+num_drop+"\n";
		String statistic4="Number of Retransmitted Segments: "+num_retransmit+"\n";
		String statistic5="Number of Duplicate Acknowledgements received: "+num_duplicate+"\n";
		
		try{
			if(!file.exists()){  
			    file.createNewFile();
			    FileOutputStream write=new FileOutputStream(file);
			    
			    for(int i=0; i<log.size(); i++)
			    {
			    	each_line=log.get(i)[0]+"  "+log.get(i)[1]+"  "+log.get(i)[2]+"  "+log.get(i)[3]+"  "+log.get(i)[4]+"  "+log.get(i)[5]+"\n";
			    	write.write(each_line.getBytes());
			    }
			    write.write(statistic1.getBytes());
			    write.write(statistic2.getBytes());
			    write.write(statistic3.getBytes());
			    write.write(statistic4.getBytes());
			    write.write(statistic5.getBytes());
			    write.close();
			}
			}catch(Exception e){  
			   e.printStackTrace();  
			}  
	}
	
	public static int num_send_no_retrans()
	{
		int num=0;
		for (int i=0;i<log.size();i++)
		{
			if(log.get(i)[0].equals("snd"))
				num+=1;
		}
		num=num-num_retransmit;
		return num;
	}
}
