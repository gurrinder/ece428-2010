import java.net.InetSocketAddress;

// this class represents a 16-bit data structure containing 2 bytes
class Word
{
	public byte byte1 = 0;
	public byte byte2 = 0;
	
	public Word(int value)
	{
		byte2 = (byte)value;
		byte1 = (byte)(value>>8);
	}
	
	public static Word createFromInt(int value)
	{
		return new Word(value);
	}
	
	public int toInt()
	{
		int value = ((byte1 & 0x000000FF) << 8);
		value += ((byte2 & 0x000000FF));
		return value;
	}
}

//this class represents a 32-bit data structure containing 4 bytes (i.e. Double Word)
class DWord
{
	public byte byte1 = 0;
	public byte byte2 = 0;
	public byte byte3 = 0;
	public byte byte4 = 0;
	
	public DWord(long value)
	{
		byte4 = (byte)value;
		byte3 = (byte)(value>>8);
		byte2 = (byte)(value>>16);
		byte1 = (byte)(value>>24);
	}
	
	public int toInt()
	{
		int value = ((byte1 & 0x000000FF) << 24);
		value += ((byte2 & 0x000000FF) << 16);
		value += ((byte3 & 0x000000FF) << 8);
		value += ((byte4 & 0x000000FF));
		return value;
	}
	
	public static DWord createFromInt(int value)
	{
		return new DWord(value);
	}
}

// TCP Header info structure
class TCPHeader
{
	// meta-data size
	static final int FIXED_HEADER_SIZE = 20;
	// actual header + data size
	static final int AGGREGATED_HEADER_SIZE = FIXED_HEADER_SIZE + 528;
	
	// this value is not used in actual transmission
	public InetSocketAddress senderAddr = null;
	// this value is not used
	public long recvTime = 0;
	
	/* <!-----------------------------------------------------------
	/* ---------------------- TCP HEADER --------------------------- 
	/* ------------------------------------------------------------- */
	
	// 32 bit seperator
	public Word sourcePort = Word.createFromInt(0);
	public Word destPort = Word.createFromInt(0);
	
	// 32 bit seperator
	public DWord seqNum = DWord.createFromInt(0);

	// 32 bit seperator
	public DWord ackNum = DWord.createFromInt(0);
	
	// 32 bit seperator
	public Word checksum = Word.createFromInt(0);
	public byte syn = 0;
	public byte fin = 0;

	// 32 bit seperator
	public byte ack = 0;
	public byte dataSize = 0;
	public Word windowSize = Word.createFromInt(0);

	/* -----------------------------------------------------------!> */
	
	// data will be exactly 528 bytes (with or w/o padding)
	public byte[] data;
	
	// TOTAL Length of TCP Header at all time = 5 * 4 + 528 = 548 
	// UDP Header will add an extra 8 bytes of header = 548 + 8 = 556 (the lowest MMS supported)
	
	void setData(byte[] dat)
	{
		data = new byte[528];
		assert(dat.length <= 128);
		dataSize = (byte) dat.length;
		for(int i = 0; i < dat.length; i++)
		{
			data[i] = dat[i];
		}
	}
	
	byte[] toBytes()
	{
		int index = 0;
		byte[] hdr = new byte[TCPHeader.FIXED_HEADER_SIZE + data.length];
		
		// add the tcp header fields
		hdr[index++] = sourcePort.byte1;
		hdr[index++] = sourcePort.byte2;
		hdr[index++] = destPort.byte1;
		hdr[index++] = destPort.byte2;
		hdr[index++] = seqNum.byte1;
		hdr[index++] = seqNum.byte2;
		hdr[index++] = seqNum.byte3;
		hdr[index++] = seqNum.byte4;
		hdr[index++] = ackNum.byte1;
		hdr[index++] = ackNum.byte2;
		hdr[index++] = ackNum.byte3;
		hdr[index++] = ackNum.byte4;
		hdr[index++] = checksum.byte1;
		hdr[index++] = checksum.byte2;
		hdr[index++] = syn;
		hdr[index++] = fin;
		hdr[index++] = ack;
		hdr[index++] = dataSize;
		hdr[index++] = windowSize.byte1;
		hdr[index++] = windowSize.byte2;

		assert((index + data.length - 1) == hdr.length);
		
		for(int i = 0; i < data.length; i++)
		{
			hdr[i + index] = data[i];
		}
		return hdr;
	}
	
	static TCPHeader createFromBytes (byte[] hdrData)
	{
		int index = 0;
		byte[] data = new byte[hdrData.length - FIXED_HEADER_SIZE];
		TCPHeader hdr = new TCPHeader();
		
		assert (hdrData.length == AGGREGATED_HEADER_SIZE);
		
		hdr.sourcePort.byte1 = hdrData[index++];
		hdr.sourcePort.byte2 = hdrData[index++];
		
		hdr.destPort.byte1 = hdrData[index++];
		hdr.destPort.byte2 = hdrData[index++];

		hdr.seqNum.byte1 = hdrData[index++];
		hdr.seqNum.byte2 = hdrData[index++];
		hdr.seqNum.byte3 = hdrData[index++];
		hdr.seqNum.byte4 = hdrData[index++];
		
		hdr.ackNum.byte1 = hdrData[index++];
		hdr.ackNum.byte2 = hdrData[index++];
		hdr.ackNum.byte3 = hdrData[index++];
		hdr.ackNum.byte4 = hdrData[index++];

		hdr.checksum.byte1 = hdrData[index++];
		hdr.checksum.byte2 = hdrData[index++];
		hdr.syn = hdrData[index++];
		hdr.fin = hdrData[index++];
		hdr.ack = hdrData[index++];
		hdr.dataSize = hdrData[index++];
		hdr.windowSize.byte1 = hdrData[index++];
		hdr.windowSize.byte2 = hdrData[index++];
		
		for(int i = 0; i < (hdrData.length - index); i++)
		{
			data[i] = hdrData[index + i];
		}
		hdr.data = data;
		return hdr;
	}
}

class TCPHeaderType
{
	public static final int DEFAULT						= 0x00000000;	
	public static final int SYN							= 0x00000001;
	public static final int ACK							= 0x00000002;
	public static final int FIN							= 0x00000004;
	
	public int type										= DEFAULT;
	public long ackNum									= 0;
	
	public TCPHeaderType(int type, long ackNum)
	{
		this.type = type;
		this.ackNum = ackNum;
	}
}

public class TCPHeaderUtil 
{
	static int calculateCheckSum(TCPHeader hdr)
	{
		int tempsum = 0;
		int checksum = 0;
		int oldchecksum = 0;
		
		// save the current checksum value
		oldchecksum = hdr.checksum.toInt();
		
		// set the current checksum to 0 for calculations
		hdr.checksum = new Word(0);
		byte[] data = hdr.toBytes();
		
		// array length is even
		assert(data.length % 2 == 0);
		
		for(int i = 0; i < data.length; i+= 2)
		{
			tempsum = ((data[i] & 0x000000FF) << 8);
			tempsum += ((data[i+1] & 0x000000FF));
			
			checksum += ~tempsum;
		}
		
		checksum = ~checksum;
		
		// put the old checksum value back
		hdr.checksum = Word.createFromInt(oldchecksum);
		return Word.createFromInt(checksum).toInt();
	}
	
	static void fillCheckSumForHeader(TCPHeader hdr)
	{
		int checksum = calculateCheckSum(hdr);
		hdr.checksum = Word.createFromInt(checksum);
	}
	
	static TCPHeader createTCPHeader(
			int srcPort,
			int destPort,
			int seqNum, 
			int ackNum, 
			boolean syn, 
			boolean fin, 
			boolean ack, 
			int windowSize,
			byte[] data
			)
	{
		TCPHeader hdr = new TCPHeader();
		
		hdr.sourcePort = Word.createFromInt(srcPort);
		hdr.destPort = Word.createFromInt(destPort);
		hdr.seqNum = DWord.createFromInt(seqNum);
		hdr.ackNum = DWord.createFromInt(ackNum);
		hdr.syn = (byte) (syn ? 1 : 0);
		hdr.fin = (byte) (fin ? 1 : 0);
		hdr.ack = (byte) (ack ? 1 : 0);
		hdr.setData(data);
		hdr.windowSize = Word.createFromInt(windowSize);
		
		fillCheckSumForHeader(hdr);
		return hdr;
	}
}
