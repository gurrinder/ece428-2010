import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;


public class StatsServer {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws SocketTimeoutException 
	 */
	public static void main(String[] args) throws SocketTimeoutException, IOException {
		InetSocketAddress serverSocket = new InetSocketAddress("localhost", 12345);
		S_StreamSocket s = new S_StreamSocket(serverSocket);
		s.S_accept();
		
		int[] testSizes = {1, 5, 20, 75, 250};
		
		for(int curTestSize : testSizes) {
			byte[] data = new byte[curTestSize*1024];
			int bytesReceived = s.S_receive(data, data.length);
			System.out.println("Received size: " + bytesReceived + "bytes | Receive timestamp: " + System.currentTimeMillis());
		}
	}

}
