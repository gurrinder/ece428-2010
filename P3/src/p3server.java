import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class p3server
{

	/**
	 * @param args
	 * @throws IOException
	 * @throws NoSuchPaddingException
	 * @throws NoSuchAlgorithmException
	 * @throws InvalidKeyException
	 * @throws BadPaddingException
	 * @throws ShortBufferException
	 * @throws IllegalBlockSizeException
	 * @throws InvalidAlgorithmParameterException
	 * @throws InterruptedException 
	 */
	public static void main(String[] args) throws IOException,
			NoSuchAlgorithmException, NoSuchPaddingException,
			InvalidKeyException, IllegalBlockSizeException,
			ShortBufferException, BadPaddingException,
			InvalidAlgorithmParameterException, InterruptedException
	{
		Decoder decoder = new Decoder(Integer.valueOf(args[1]));
		DatagramSocket welcomeSocket = new DatagramSocket();
		int serverPort = welcomeSocket.getLocalPort();
		
		//System.console().printf("setenv P "+welcomeSocket.getLocalPort()); 
		welcomeSocket.setReceiveBufferSize(64 * 1024 * 1024); // 64MB
		byte[] buf = new byte[1024];
		BufferedWriter file = new BufferedWriter(new FileWriter("out.dat"));
		
		// start client with the specified parameters
		ProcessBuilder builder = new ProcessBuilder("/home/tripunit/p3client", "-s", Integer.toString(serverPort), "-f", args[0], "-n", args[1]);
		Process client = builder.start();
		
		while (true)
		{
			DatagramPacket packet = new DatagramPacket(buf, buf.length);
			welcomeSocket.receive(packet);
			if (new String(buf, 0, packet.getLength()).equals("   KEY CHANGE   "))
			{
				//System.out.println("hopefully KEY CHANGE " + packet.getLength() + " = " + new String(getSubByte(buf, 0, packet.getLength())) + "    =" + Decoder.getHexText(getSubByte(buf, 0, packet.getLength()), packet.getLength()));
				decoder.resetKey();
			} else
			{
				byte[] crypto = getSubByte(buf, 0, packet.getLength());
				byte[] plainText = decoder.decode(crypto);
				file.write(new String(plainText, 1, plainText.length-1));
				file.flush();
			}

		}
	}

	private static byte[] getSubByte(byte[] buf, int s, int length)
	{
		byte[] ret = new byte[length];
		for (int i = 0; i < length; i++)
			ret[i] = buf[s + i];
		return ret;
	}

}

class Decoder
{
	int numZero;
	SecretKeySpec keyFound = null;
	Cipher cipher;
	ArrayList<DecoderWorker> workers = new ArrayList<DecoderWorker>();
	long startTime;
	final byte[] iv = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
	private IvParameterSpec ips = new IvParameterSpec(iv);

	Decoder(int n) throws NoSuchAlgorithmException, NoSuchPaddingException
	{
		this.numZero = n;
		cipher = Cipher.getInstance("AES/CBC/NoPadding");
		System.out.println("numZero=" + this.numZero);
		setupWorkers(4);
	}

	private void setupWorkers(int n) throws NoSuchAlgorithmException, NoSuchPaddingException
	{
		int divSize = (int) (comb(128, this.numZero)/n);
		int start = 0;
		int end = 128;
		for (int i = 0; i < n-1; i++)
		{
			int subSum = 0;
			int lastSubSum = 0;
			while(subSum < divSize)
			{
				lastSubSum = subSum;
				end--;
				subSum += comb(end, this.numZero-1);
			}
			if (divSize - lastSubSum < subSum - divSize)
			{
				subSum = lastSubSum;
				end++;
			}
			assert start <= (128-end-1);
			workers.add(new DecoderWorker(this.numZero, start, 128-end-1));
			System.out.printf("%3d-%3d : Worst Case takes 4s/1,000,000 keys time=%d\n", start, 128-end-1, subSum*4/1000000);
			start = 128-end;
		}
		workers.add(new DecoderWorker(this.numZero, start, 128-this.numZero));
		System.out.printf("%3d-%3d : Worst Case takes 4s/1,000,000 keys time=%d\n", start, 128-this.numZero, (int)(comb(end, this.numZero)*4/1000000));
	}
	
	private double comb(int n, int r)
	{
		if (r == 0)
			return 1;
		double num=1;
		double din=1;
		for (int i = 0; i < r; i++)
		{
			num *= n-i;
			din *= (i+1);
		}
		return num/din;
	}

	public void resetKey()
	{
		System.out.println("Reset Key");
		for (DecoderWorker d : workers)
			d.resetKey();
		keyFound = null;
	}

	public byte[] decode(byte[] msg) throws InvalidKeyException,
			InvalidAlgorithmParameterException, ShortBufferException,
			IllegalBlockSizeException, BadPaddingException, InterruptedException
	{
		byte[] plainText = null;
		if (keyFound != null)
		{
			plainText = new byte[msg.length];
			int ptLength = cipher.update(msg, 0, msg.length, plainText, 0);
			ptLength += cipher.doFinal(plainText, ptLength);
			if (DecoderWorker.checkString(plainText, ptLength))
			{
				System.out.printf("Key Reused\n");
				return plainText;
			}
			
			//keyval = keyFound.getEncoded();
		}
		startTime = new Date().getTime();
		for (DecoderWorker d : workers)
			d.setMsgAndRun(msg);
		
		while(keyFound == null)
		{
			Thread.sleep(1000);
			int deadCount = 0;
			for (DecoderWorker d : workers)
			{
				if (!d.isAlive() && d.keyFound != null)
				{
					plainText = d.plainText;
					this.keyFound = d.keyFound;
					cipher.init(Cipher.DECRYPT_MODE, this.keyFound, ips);
					for (DecoderWorker dd : workers)
						dd.endThread();
					break;
				}
				else if (!d.isAlive())
				{
					deadCount++;
				}
			}
			if (deadCount == workers.size())
				return null;
		}
		
		long endTime = new Date().getTime();
		System.out.println("Time Execution = " + (endTime-startTime));
		return plainText;

	}

	public static String getHexText(byte[] buf, int len)
	{
		StringBuilder hex = new StringBuilder();
		for (int i = 0; i < len; i++)
		{
			int v = 0;
			if (buf[i] < 0)
				v+=128;
			v += buf[i] & DecoderWorker.masks[0][0];
			hex.append(Integer.toHexString(v).toUpperCase());
		}
		return hex.toString();
	}
}

class DecoderWorker implements Runnable
{
	int numZero;
	SecretKeySpec keyFound = null;
	Cipher cipher;
	ArrayList<Integer> lastStop = new ArrayList<Integer>();
	final byte[] iv = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
			0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
	private IvParameterSpec ips = new IvParameterSpec(iv);
	private byte[] msg;
	private int start;
	private int end;
	private boolean keyFoundByAnother;
	private Thread myThread;
	public byte[] plainText;

	DecoderWorker(int n, int start, int end) throws NoSuchAlgorithmException, NoSuchPaddingException
	{
		this.numZero = n;
		this.start = start;
		this.end = end;
		cipher = Cipher.getInstance("AES/CBC/NoPadding");
		//System.out.printf("%3d-%3d : Initialized\n", this.start, this.end);
	}

	public boolean isAlive()
	{
		return this.myThread.isAlive();
	}

	public void resetKey()
	{
		//System.out.printf("%3d-%3d : Key Reset\n", this.start, this.end);
		this.lastStop.clear();
		keyFound = null;
	}
	
	public void setMsgAndRun(byte[] msg)
	{
		this.msg = msg;
		this.keyFoundByAnother = false;
		this.keyFound = null;
		this.myThread = new Thread(this);
		//System.out.printf("%3d-%3d : Starting\n", this.start, this.end);
		this.myThread.start();
	}
	
	public void endThread() throws InterruptedException
	{
		this.keyFoundByAnother = true;
		this.myThread.join();
		//System.out.printf("%3d-%3d : Ended\n", this.start, this.end);
	}
	
	private byte[] computeKeyAndDecode(byte[] msg, int start, int end, int z, byte[] keyval)
			throws ShortBufferException, IllegalBlockSizeException,
			BadPaddingException, InvalidKeyException,
			InvalidAlgorithmParameterException
	{
		byte[] plainText;
		if (z == numZero)
		{

			SecretKeySpec key;
			//System.out.println("Key Attempted : " + getHexText(keyval, keyval.length));
			key = new SecretKeySpec(keyval, "AES");
			cipher.init(Cipher.DECRYPT_MODE, key, ips);
			plainText = new byte[msg.length];
			int ptLength = cipher.update(msg, 0, 16, plainText, 0);
			ptLength += cipher.doFinal(plainText, ptLength);
			if (checkStringPositive(plainText, ptLength))
			{
				ptLength = cipher.update(msg, 0, msg.length, plainText, 0);
				ptLength += cipher.doFinal(plainText, ptLength);
				if (checkString(plainText, ptLength))
				{
					this.keyFound = key;
					System.out.printf("%3d-%3d : Key Found\n", this.start, this.end);
					return plainText;
				}					
			}
			return null;
		}
		if (this.lastStop.size() > 0)
		{
			start = this.lastStop.remove(0);
		}
		byte[] newkeyval = new byte[keyval.length];
		for (int j = 0; j < keyval.length; j++)
			newkeyval[j] = keyval[j];
		for (int i = start; i <= end; i++)
		{
			if (i != start)
				setBit(newkeyval, i-1, 1);
			setBit(newkeyval, i, 0);
			plainText = computeKeyAndDecode(msg, i+1, 128-this.numZero+z+1, z+1, newkeyval);
			if (this.keyFoundByAnother || plainText != null)
			{
				this.lastStop.add(0, i);
				return plainText;
			}
		}
		return null;
	}

	final static byte[][] masks = {{127, -65, -33, -17, -9, -5, -3, -2},
		{-128, 64, 32, 16, 8, 4, 2, 1}};
	private static void setBit(byte[] data, int pos, int val)
	{
		int posByte = pos / 8;
		int posBit = pos % 8;
		if (val == 0)
			data[posByte] = (byte) (data[posByte] & masks[val][posBit]);
		else
			data[posByte] = (byte) (data[posByte] | masks[val][posBit]);
	}

	static boolean checkString(byte[] plainText, int len)
	{
		byte xor = 0;
		for (int i = 1; i < len; i++)
		{
			xor ^= plainText[i];
			if ((plainText[i] & masks[1][0]) == masks[1][0])
				return false;
		}
		return xor == plainText[0];
	}
	private static boolean checkStringPositive(byte[] plainText, int len)
	{
		for (int i = 1; i < len; i++)
		{
			if ((plainText[i] & masks[1][0]) == masks[1][0])
				return false;
		}
		return true;
	}

	@Override
	public void run()
	{
		byte[] keyval = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
				-1, -1, -1 };
		try
		{
			this.plainText = computeKeyAndDecode(msg, this.start, this.end, 0, keyval);
		} catch (InvalidKeyException e)
		{
			e.printStackTrace();
		} catch (ShortBufferException e)
		{
			e.printStackTrace();
		} catch (IllegalBlockSizeException e)
		{
			e.printStackTrace();
		} catch (BadPaddingException e)
		{
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e)
		{
			e.printStackTrace();
		}

	}
}
