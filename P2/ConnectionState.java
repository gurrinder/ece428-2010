// this class represents the state of a typical TCP layer
public class ConnectionState 
{	
	private int curState								= 0xFFFFFFFF;
	

	public static final int LISTEN  					= 0x00000001;
	public static final int SYN_SENT 					= 0x00000002;
	public static final int SYN_RECEIVED 				= 0x00000003;
	public static final int ESTABLISHED  				= 0x00000004;
	public static final int FIN_WAIT_1 					= 0x00000005;
	public static final int FIN_WAIT_2 					= 0x00000006;
	public static final int CLOSE_WAIT 					= 0x00000008;
	public static final int CLOSING 					= 0x00000006;
	public static final int LAST_ACK 					= 0x00000006;
	public static final int TIME_WAIT 					= 0x00000006;
	public static final int CLOSED 						= 0x00000000;
	
	public ConnectionState(int state)
	{
		SetState(state);
	}
	
	public void SetState(int state)
	{
		curState = state;
	}
	
	public int GetState()
	{
		return curState;
	}
}