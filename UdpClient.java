import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;

public class UdpClient {
	private static double averageRTT = 0.00;
	private static String address;

	public static void main(String[] args) {
		try(Socket socket = new Socket("18.221.102.182", 38005)) {
			address = socket.getInetAddress().getHostAddress();
			System.out.println("Connected to server.");

			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			//Create IPv4 Packet 
			byte[] deadBeef = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};	//Data (0xDEADBEEF)
			byte[] handshake = createIPv4(socket, 4, deadBeef);

			//Write "handshake" to Server
			os.write(handshake);

			System.out.print("Handshake response: ");
			printServerResponse(is);

			//2 bytes of data received as port number for packets
			byte portNum1 = (byte) is.read();
			byte portNum2 = (byte) is.read();
			int portNum = ((portNum1<<8 & 0xFF00) | portNum2 & 0xFF);
			System.out.println("Port Number received: " + portNum + "\n");


			/** CREATE UDP PACKET */
			for(int index = 1; index < 13; index++) {
				//Data increases by 2 each time
				int dataLength = (int) Math.pow(2, index);

				/** CREATE IPv4 PSEUDOHEADER */
				//20 - PseudoHeader + UDP header
				int pseudoSize = 20 + dataLength;
				byte[] UDPpseudo = new byte[pseudoSize];

				//Set Source IPv4 Address
				//Values at UDPpseudo[0 to 3] are initialized to 0

				//Set Destination IPv4 Address
				setDestAddress(address, UDPpseudo, 4);

				//Value at UDPpseudo[8] is initialized to 0

				//Set Protocol
				UDPpseudo[9] = (byte) 17;

				//Set UDP Length (UDP Header + UDP Data)
				UDPpseudo[10] = (byte) (((8 + dataLength) >> 8) & 0xFF);
				UDPpseudo[11] = (byte) ((8 + dataLength) & 0xFF);

				//Set Source Port to 0
				//Values at UDPpacket[12 to 13] are initialized to 0

				//Set Destination Port to port number
				UDPpseudo[14] = (byte) ((portNum >> 8) & 0xFF);
				UDPpseudo[15] = (byte) (portNum & 0xFF);

				//Set Length to UDP Header + Data
				UDPpseudo[16] = (byte) (((8 + dataLength) >> 8) & 0xFF);
				UDPpseudo[17] = (byte) ((8 + dataLength) & 0xFF);

				//Set Checksum
				//Values at UDPpseudo[18 to 19] are initialized to 0

				//Set data for packet
				System.out.println("Sending packet with " + dataLength + " bytes of data");

				//Create random data for packet
				byte[] data = new byte[dataLength];
				Random rand = new Random();				
				rand.nextBytes(data);

				//Store randomized data in packet
				for (int i = 0; i < dataLength; i++) {
					UDPpseudo[i+20] = data[i];
				}

				//Send to checksum
				short pseudoChksum = checkSum(UDPpseudo);

				/** CREATE UDP PACKET */ 
				//Header size, 8 bytes
				int UDPsize = 8 + dataLength;
				byte[] UDPpacket = new byte[UDPsize];

				//Set Source Port to 0
				//Value at UDPpacket[0 to 1] are initialized to 0

				//Set Destination Port to port number
				UDPpacket[2] = (byte) (portNum >> 8 & 0xFF);
				UDPpacket[3] = (byte) (portNum & 0xFF);

				//Set Length to UDP Header + Data
				UDPpacket[4] = (byte) ((UDPsize >> 8) & 0xFF);
				UDPpacket[5] = (byte) (UDPsize & 0xFF);

				//Update Checksum calculated from PseudoHeader 
				UDPpacket[6] = (byte) ((pseudoChksum >> 8) & 0xFF); 
				UDPpacket[7] = (byte) (pseudoChksum & 0xFF);

				//Set Data
				for (int i=0; i < dataLength; i++) {
					UDPpacket[i + 8] = UDPpseudo[i + 20];
				}

				/** CREATE IPv4 PACKET */
				byte[] IPv4packet = createIPv4(socket, UDPsize, UDPpacket);

				//Write to Server
				long sentTime = System.currentTimeMillis();
				os.write(IPv4packet);

				//Return value of 0xCAFEBABE indicates a good response
				System.out.print("Response: ");
				printServerResponse(is);
				long receivedTime = System.currentTimeMillis();

				//Calculate Estimated RTT
				long estimatedRTT =  receivedTime - sentTime;
				System.out.println("RTT: " + estimatedRTT + "ms\n");

				//Calculate Average RTT
				averageRTT += estimatedRTT;
			}
			//Print Average RTT
			System.out.printf("Average RTT: %.2f", (averageRTT/12));
			System.out.print("ms");

		} catch(IOException e) {
			e.printStackTrace();
		}

	}

	private static void setDestAddress(String address, byte[] packet, int index) {
		String[] temp = address.split("\\.");
		for(int i = 0; i < temp.length; i++) {
			int val = Integer.valueOf(temp[i]);
			packet[index+i] = (byte) val;
		}
	}

	private static void printServerResponse(InputStream is) throws IOException {
		System.out.print("0x");
		for(int j = 0; j < 4; j++) {
			System.out.printf("%02X", is.read());
		}
		System.out.println();
	}

	public static byte[] createIPv4(Socket socket, int dataLength, byte[] data) {
		//IPv4 Packet 
		//	Header is 20 bytes 
		//	Data is UDP header + UDP data
		byte packet[] = new byte[20 + dataLength];

		//IPv4 Version
		int version = 4;

		//Header Length (in words)
		int HL = 5;

		//Concatenate for 1st byte of Packet
		packet[0] = (byte) ((version << 4 & 0xF0) | (HL & 0xF));

		//TOS (No implementation)
		//Value at packet[1] is initialized to 0

		//Total Length (Header + Data length in bytes)
		int totalLength = (int) (20 + dataLength);
		packet[2] = (byte) ((totalLength >> 8) & 0xFF);
		packet[3] = (byte) (byte) (totalLength & 0xFF);

		//Flag is set to '010'
		//Fragment offset is 0 so adds 5 0's to the end of 010 = 01000000
		packet[6] = (byte) 64;

		//Fragment Offset (No implementation)
		//Value at packet[7] is initialized to 0

		//Time to Live
		packet[8] = (byte) 50;

		//UDP Protocol
		packet[9] = (byte) 17;

		int chkSum = 0;
		packet[10] = (byte) chkSum;
		packet[11] = (byte) chkSum;

		//Source/Sender IP Address
		for(int i = 0; i < 4; i++) {
			packet[12 + i] = 0;
		}

		//Destination/Receiver IP Address, send byte array to checksum
		setDestAddress(address, packet, 16); 
		short checksum = checkSum(packet);

		//Split checkSum short, put into checksum
		packet[10] = (byte) (checksum >> 8 & 0xFF);
		packet[11] = (byte) (checksum & 0xFF);

		for(int i = 0; i < dataLength; i++) {
			packet[(20 + i)] = data[i];
		}

		return packet;
	}

	public static void printIPv4Packet(byte[] packet) {
		System.out.println("0        8        16       24");
		int counter=1;
		for(byte b: packet) {
			System.out.print(Integer.toBinaryString(b & 255 | 256).substring(1) + " ");
			if(counter % 4 ==0) {
				System.out.println();
			}
			counter++;
		}
	}

	public static short checkSum(byte[] b) {
		int sum = 0;
		int i = 0;
		while(i < b.length - 1) {
			byte first = b[i];
			byte second = b[i + 1];
			sum += ((first << 8 & 0xFF00) | (second & 0xFF));

			if((sum & 0xFFFF0000) > 0) {
				sum &= 0xFFFF;
				sum++;
			}
			i += 2;
		}

		if((b.length) % 2 == 1) {
			byte last = b[(b.length - 1)];
			sum += ((last << 8) & 0xFF00);

			if((sum & 0xFFFF0000) > 0) {
				sum &= 0xFFFF;
				sum++;
			}
		}
		return (short) ~(sum & 0xFFFF);
	}
}