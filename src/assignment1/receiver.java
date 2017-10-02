//package assignment1;

import java.io.*;
import java.net.*;
import java.util.*;

import org.omg.CORBA.StringHolder;

public class receiver {

	static List<String[]> content=new ArrayList<String[]>();
	static int want_seq=0;
	static int last_send_seq=0;
	static int last_send_ack=0;
	static List<String[]> log = new ArrayList<String[]>();
	static long start_time;
	static String filename;
	static int amount_byte=0;
	static int num_duplicate=0;
	static List<Integer> duplicate=new ArrayList<Integer>();
	static InetAddress IPAddress;
	static int send_port;
	static DatagramSocket receiveSocket;
	
	public static void main(String[] args) throws Exception
	{
		start_time = System.currentTimeMillis();
		if (args.length != 2) {
		       System.out.println("Incorrect arguments");
		       return;
		    }
	    int port = Integer.parseInt(args[0]);
	    filename = args[1];
	    
	    receiveSocket = new DatagramSocket(port);
	    byte[] establish1 = new byte[1024];
	    byte[] establish2 = new byte[1024];
	    byte[] establish3 = new byte[1024];
	    DatagramPacket handshake1 = new DatagramPacket(establish1, establish1.length);
	    //receive first handshake
	    receiveSocket.receive(handshake1);
	    long handshake1_time = System.currentTimeMillis()-start_time;
	    String receive1 = new String(handshake1.getData());
	    receive1=receive1.trim();
	    IPAddress = handshake1.getAddress();
	    send_port = handshake1.getPort();
	    String[] receive1_set = receive1.split(" ");
	    int syn1 = Integer.parseInt(receive1_set[0].substring(3));
		int seq1 = Integer.parseInt(receive1_set[1].substring(3));
		int ack1 = seq1+1;
		write_in_log("rcv",Long.toString(handshake1_time),"S",Integer.toString(seq1),"0","0");
		
		Random random = new Random(60);
		int seq2 = random.nextInt(500); 
		String send1="syn"+syn1+" seq"+seq2+" ack"+ack1;
		establish2 = send1.getBytes();
		DatagramPacket handshake2 = new DatagramPacket(establish2, establish2.length, IPAddress, send_port);
		//send second handshake
		long handshake2_time = System.currentTimeMillis()-start_time;
		write_in_log("snd",Long.toString(handshake2_time),"SA",Integer.toString(seq2),"0",Integer.toString(ack1));
		receiveSocket.send(handshake2);
		
		last_send_seq=seq2;
		last_send_ack=ack1;
		
		DatagramPacket handshake3 = new DatagramPacket(establish3, establish3.length);
		//receive third handshake
		receiveSocket.receive(handshake3);
		long handshake3_time = System.currentTimeMillis()-start_time;
	    String receive3 = new String(handshake3.getData());
	    receive3=receive3.trim();
	    String[] receive3_set = receive3.split(" ");
	    int syn3 = Integer.parseInt(receive3_set[0].substring(3));
	    int seq3 = Integer.parseInt(receive3_set[1].substring(3));
	    int ack3 = Integer.parseInt(receive3_set[2].substring(3));
	    //third handshake success
	    if(syn3==0 && seq3==ack1 && ack3==seq2+1)
	    {
	    	write_in_log("rcv",Long.toString(handshake3_time),"A",Integer.toString(seq3),"0",Integer.toString(ack3));
	    	want_seq=ack1;
	    	while(true)
	    	{
	    		boolean is_retrans=false;
	    		byte[] receive_byte = new byte[1024];
	    		DatagramPacket receive_packet = new DatagramPacket(receive_byte, receive_byte.length);
	    		receiveSocket.receive(receive_packet);
	    		long receive_time = System.currentTimeMillis()-start_time;
	    		String receive_string = new String(receive_packet.getData(),0,receive_packet.getLength());
	    		
	    		// start fin
	    		if(receive_string.startsWith("fin"))
	    		{
	    		    String[] end_split1 = receive_string.split(" ");
	    		    int fin = Integer.parseInt(end_split1[0].substring(3));
	    		    int end_seq1 = Integer.parseInt(end_split1[1].substring(3));
	    		    int end_ack1 = Integer.parseInt(end_split1[2].substring(3));
	    		    write_in_log("rcv",Long.toString(receive_time),"F",Integer.toString(end_seq1),"0",Integer.toString(end_ack1));
	    		    
	    		    //second fin
	    		    String end_string2 = "fin"+fin+" seq"+end_ack1+" ack"+(end_seq1+1);
	    			byte[] end_byte2=end_string2.getBytes();
	    			DatagramPacket end_packet2 = new DatagramPacket(end_byte2, end_byte2.length, IPAddress, send_port);
	    			long end_time2 = System.currentTimeMillis()-start_time;
	    			write_in_log("snd",Long.toString(end_time2),"FA",Integer.toString(end_ack1),"0",Integer.toString(end_seq1+1));
	    			receiveSocket.send(end_packet2);
	    			
	    		    
	    			//third fin
	    			byte[] end_byte3 = new byte[1024];
	    			DatagramPacket end_packet3 = new DatagramPacket(end_byte3, end_byte3.length);
	    			receiveSocket.receive(end_packet3);
	    			long end_time3 = System.currentTimeMillis()-start_time;
	    		    String end_string3 = new String(end_packet3.getData());
	    		    end_string3=end_string3.trim();
	    		    String[] end_split3 = end_string3.split(" ");
	    		    fin = Integer.parseInt(end_split3[0].substring(3));
	    		    int end_seq3 = Integer.parseInt(end_split3[1].substring(3));
	    		    int end_ack3 = Integer.parseInt(end_split3[2].substring(3));
	    		    write_in_log("rcv",Long.toString(end_time3),"A",Integer.toString(end_seq3),"0",Integer.toString(end_ack3));
	    		    break;	
	    		}
	    		String[] receive_set = receive_string.split(" ");
	    		int receive_seq=Integer.parseInt(receive_set[0].substring(3));
	    		int receive_ack=Integer.parseInt(receive_set[1].substring(3));
	    		String receive_data=receive_string.substring(receive_string.indexOf("data")+4);
	    		if(receive_data.startsWith("-re"))
	    		{
	    			is_retrans=true;
	    			receive_data=receive_data.substring(3);
	    		}
	    		byte[] data_byte=receive_data.getBytes();
	    		write_in_log("rcv",Long.toString(receive_time),"D",Integer.toString(receive_seq),Integer.toString(data_byte.length),Integer.toString(receive_ack));
	    		if(!is_retrans)
	    			amount_byte+=data_byte.length;
	    		if(!is_have(receive_seq,data_byte.length))
	    		{
	    		if (receive_seq==want_seq)
	    		{
	    			//store data
	    			String[] each_content = new String[4];
	    			each_content[0]=Integer.toString(receive_seq);
	    			each_content[1]=Integer.toString(receive_ack);
	    			each_content[2]=receive_data;
	    			each_content[3]="yes";
	    			content.add(each_content);
	    			//start send ack
	    			for(int i=0; i<content.size(); i++)
	    			{
	    				if (content.get(i)[3].equals("no") || i==content.size()-1)
	    				{
	    					content.get(i)[3]="yes";
	    					byte[] send_byte = new byte[1024];
	    					byte[] get_data=content.get(i)[2].getBytes();
	    					int send_seq = Integer.parseInt(content.get(i)[1]);
	    	    			int send_ack = Integer.parseInt(content.get(i)[0])+get_data.length;
	    	    			String send_string = "seq"+send_seq+" ack"+send_ack;
	    	    			send_byte=send_string.getBytes();
	    	    			DatagramPacket send_packet = new DatagramPacket(send_byte, send_byte.length, IPAddress, send_port);
	    	    			long send_time = System.currentTimeMillis()-start_time;
	    	    			write_in_log("snd",Long.toString(send_time),"A",Integer.toString(send_seq),"0",Integer.toString(send_ack));
	    	    			receiveSocket.send(send_packet);
	    				}
	    			}
	    			int[] result=get_want_seq();
	    			want_seq=result[0];
	    	    	last_send_seq=result[1];
	    	    	last_send_ack=result[2];
	    		}
	    		else 
	    		{
	    			//store data
	    			String[] each_content = new String[4];
	    			each_content[0]=Integer.toString(receive_seq);
	    			each_content[1]=Integer.toString(receive_ack);
	    			each_content[2]=receive_data;
	    			each_content[3]="no";
	    			content.add(each_content);
	    			//send ack
	    			byte[] send_byte = new byte[1024];
	    			String send_string = "seq"+last_send_seq+" ack"+last_send_ack;
	    			send_byte=send_string.getBytes();
	    			DatagramPacket send_packet = new DatagramPacket(send_byte, send_byte.length, IPAddress, send_port);
	    			long send_time = System.currentTimeMillis()-start_time;
	    			write_in_log("snd",Long.toString(send_time),"A",Integer.toString(last_send_seq),"0",Integer.toString(last_send_ack));
	    			receiveSocket.send(send_packet);
	    		}
	    		}
	    	}
	    	create_file();
	    	create_log_file();
	    }
	}
	
	public static boolean is_have(int receive_seq,int length)
	{
		boolean result=false;
		for(int i=0;i<content.size();i++)
		{
			if(Integer.parseInt(content.get(i)[0])==receive_seq && content.get(i)[3].equals("yes"))
			{
				result=true;
				num_duplicate+=1;
				break;
			}
		}
		return result;
	}
	
	public static int[] get_want_seq()
	{
		//firstly let content in order
		for(int a=0; a<content.size()-1; a++)
		{
			for(int b=a+1; b<content.size(); b++)
			{
				if(Integer.parseInt(content.get(a)[0]) > Integer.parseInt(content.get(b)[0]))
				{
					exchange_set(content.get(a),content.get(b));
				}
			}
		}
		
		
		
		int[] result=new int[3];
		for(int a=0; a<content.size(); a++)
		{
			boolean is_find=false;
			byte[] temp_byte=content.get(a)[2].getBytes();
			int want_seq=Integer.parseInt(content.get(a)[0])+temp_byte.length;
			for(int b=0; b<content.size(); b++)
			{
				if(Integer.parseInt(content.get(b)[0])==want_seq)
				{
					is_find=true;
					break;
				}
			}
			if(!is_find)
			{
				result[0]=want_seq;
				result[1]=Integer.parseInt(content.get(a)[1]);
				result[2]=want_seq;
				break;
			}
		}
		return result;
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
	
	public static void create_file() throws Exception
	{
		String whole_file="";
		
		//record duplicate
		for(int c=0; c<content.size()-1; c++)
		{
			if(content.get(c)[0].equals(content.get(c+1)[0]))
			{
				num_duplicate+=1;
				duplicate.add(c+1);
			}
		}
		//delete duplicate
		int move=0;
		for(int e=0; e<duplicate.size(); e++)
		{
			content.remove(duplicate.get(e)-move);
			move+=1;
		}
		
				
		for(int i=0; i<content.size(); i++)
		{
			whole_file+=content.get(i)[2];
		}
		
		File file = new File(filename);
		try{
			if(!file.exists()){  
			    file.createNewFile();
			    FileOutputStream write=new FileOutputStream(file);
			    write.write(whole_file.getBytes());
			    write.close();
			}
			}catch(Exception e){  
			   e.printStackTrace();  
			}  
	}
	
	public static int get_num_receive()
	{
		int num=0;
		for(int i=0;i<log.size(); i++)
		{
			if(log.get(i)[0].equals("rcv"))
				num+=1;
		}
		return num;
	}
	
	public static void create_log_file() throws Exception
	{
		String log_file="Receiver_log.txt";
		File file = new File(log_file);
		String each_line="";
		String statistic1="Amount of Data Received (in bytes): "+amount_byte+"\n";
		String statistic2="Number of Data Segments Received: "+get_num_receive()+"\n";
		String statistic3="Number of duplicate segments received: "+num_duplicate+"\n";
		
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
			    write.close();
			}
			}catch(Exception e){  
			   e.printStackTrace();  
			}  
	}
	
	
	public static void exchange_set(String[] a, String[] b)
	{
		for (int i=0; i<a.length; i++)
		{
			String temp=a[i];
			a[i]=b[i];
			b[i]=temp;
		}
	}
}
