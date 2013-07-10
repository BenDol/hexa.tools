package com.hexa.client.comm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.hexa.client.comm.NetworkPendingRequestInfo.XNetworkPendingRequestInfo;
import com.hexa.client.interfaces.IAsyncCallback;
import com.hexa.client.tools.HexaTools;

public class ServerComm
{
	public static enum ErrorCodes
	{
		ERROR_REQUEST_SEND, ERROR_REQUEST_RESPONSE_STATUS, ERROR_REQUEST_RESPONSE_EMPTY, ERROR_REQUEST_RESPONSE_PARSE, ERROR_REQUEST_GWT;
	}

	public interface ServerCommCb
	{
		// a list of the value of each parameter for the callback
		void onResponse( Object cookie, ResponseJSO response, int msgLevel, String msg );
	}

	public interface ServerCommMessageCb
	{
		void signalResultParseError( String parsedTxt, String trace );

		void signalRequestError( String trace, Throwable exception );

		void signalServerCommMessage( int msgLevel, String msg );

		void hangOut( String title, String description, String name, String type, JavaScriptObject currentData, IAsyncCallback<JavaScriptObject> callback );
	}

	public interface ServerCommStatusCb
	{
		void onServerCommStatusChanged( String status, int nbRqPending, int sentBytes, int receivedBytes );
	}

	private static final int MAX_NB_REQUESTS_IN_NETWORK = 2;
	private static final int MAX_NB_CALLS_BY_REQUEST = 50;

	private ServerCommMessageCb serverCommMessageCb = null;
	private String baseUrl = null;

	private ArrayList<BeforeNetworkRequestHandler> beforeNetworkRequestHandlers;
	private ArrayList<AfterNetworkRequestHandler> afterNetworkRequestHandlers;

	public void Init( String baseUrl, ServerCommMessageCb serverCommMessageCb )
	{
		this.baseUrl = baseUrl;
		this.serverCommMessageCb = serverCommMessageCb;
	}

	public Object addBeforeNetworkRequestHandler( BeforeNetworkRequestHandler handler )
	{
		if( beforeNetworkRequestHandlers == null )
			beforeNetworkRequestHandlers = new ArrayList<BeforeNetworkRequestHandler>();

		beforeNetworkRequestHandlers.add( handler );

		return handler;
	}

	public void removeBeforeNetworkRequestHandler( Object registration )
	{
		beforeNetworkRequestHandlers.remove( (BeforeNetworkRequestHandler) registration );
	}

	public Object addAfterNetworkRequestHandler( AfterNetworkRequestHandler handler )
	{
		if( afterNetworkRequestHandlers == null )
			afterNetworkRequestHandlers = new ArrayList<AfterNetworkRequestHandler>();

		afterNetworkRequestHandlers.add( handler );

		return handler;
	}

	public void removeAfterNetworkRequestHandler( Object registration )
	{
		afterNetworkRequestHandlers.remove( (AfterNetworkRequestHandler) registration );
	}

	// statistics
	private int nbSentBytes = 0;
	private int nbReceivedBytes = 0;

	private ArrayList<ServerCommStatusCb> statusCbs = new ArrayList<ServerCommStatusCb>();

	// state and requests data
	ArrayList<RequestCallInfo> requestsToSend = new ArrayList<RequestCallInfo>();
	Set<NetworkPendingRequestInfo> inNetworkRequests = new HashSet<NetworkPendingRequestInfo>();

	public void registerStatusCallback( ServerCommStatusCb callback )
	{
		statusCbs.add( callback );
	}

	public void sendRequest( RequestDesc request, Object cookie, ServerCommCb callback )
	{
		RequestCallInfo info = new RequestCallInfo( request );
		info.register( callback, cookie );

		requestsToSend.add( info );

		scheduleSend();
	}

	private void statusRefresh()
	{
		String status;
		int nbPending = inNetworkRequests.size();
		if( nbPending == 0 )
			status = "OK";
		else
			status = "Loading (" + nbPending + ")";

		for( ServerCommStatusCb callback : statusCbs )
			callback.onServerCommStatusChanged( status, nbPending, nbSentBytes, nbReceivedBytes );
	}

	// handles the case where an hangOut has been generated by the server
	void hangOut( final RequestCallInfo req, GenericJSO hangOut )
	{
		final int hangOutId = hangOut.getIntByIdx( 0 );

		JsArrayString tmp = hangOut.getGenericJSOByIdx( 1 ).cast();
		String name = tmp.get( 0 );
		String type = tmp.get( 1 );
		String title = tmp.get( 2 );
		String description = tmp.get( 3 );
		JavaScriptObject hangOutCurrentData = hangOut.getGenericJSOByIdx( 2 );

		serverCommMessageCb.hangOut( title, description, name, type, hangOutCurrentData, new IAsyncCallback<JavaScriptObject>()
		{
			public void onSuccess( JavaScriptObject result )
			{
				JSONArray params = new JSONArray();
				params.set( 0, new JSONNumber( hangOutId ) );
				params.set( 1, new JSONObject( result ) );
				req.request = new RequestDesc( req.request.service, req.request.interfaceChecksum, "_hang_out_reply_", params );

				requestsToSend.add( req );
				scheduleSend();
			}
		} );
	}

	private ScheduledCommand sendCommand = new ScheduledCommand()
	{
		public void execute()
		{
			sendTimerEvent();
		}
	};

	private void scheduleSend()
	{
		Scheduler.get().scheduleFinally( sendCommand );
	}

	// called when there is something to be sent over the network
	private void sendTimerEvent()
	{
		// too much gone in network request, will trigger more sending upon receiving
		if( inNetworkRequests.size() >= MAX_NB_REQUESTS_IN_NETWORK )
			return;

		if( requestsToSend.isEmpty() )
			return;

		NetworkPendingRequestInfo networkRqInfo = new NetworkPendingRequestInfo( rqCallback );

		int count = 0;
		while( (!requestsToSend.isEmpty()) && count < MAX_NB_CALLS_BY_REQUEST )
		{
			RequestCallInfo info = requestsToSend.remove( 0 );

			networkRqInfo.toSend( info );

			count++;
		}

		networkRqInfo.send( baseUrl );
	}

	XNetworkPendingRequestInfo rqCallback = new XNetworkPendingRequestInfo()
	{
		public void sent( NetworkPendingRequestInfo request )
		{
			inNetworkRequests.add( request );
			nbSentBytes += request.getNbSentBytes();

			statusRefresh();
		}

		@Override
		public void answerReceived( NetworkPendingRequestInfo request )
		{
			inNetworkRequests.remove( request );

			nbReceivedBytes += request.getNbReceivedBytes();

			statusRefresh();

			if( requestsToSend.size() > 0 )
				scheduleSend();
		}

		public void error( ErrorCodes errorCode, Exception exception, NetworkPendingRequestInfo request )
		{
			inNetworkRequests.remove( request );

			switch( errorCode )
			{
			case ERROR_REQUEST_SEND:
				break;
			case ERROR_REQUEST_RESPONSE_STATUS:
				HexaTools.alert( "Error with server connexion", "The remote server seems so be down.... We are trying to reconnect..." );
				request.resend();
				break;
			case ERROR_REQUEST_RESPONSE_EMPTY:
				// Window.alert(
				// "Empty server response for request, status code: " +
				// request.getResponse().getStatusCode() );
				break;
			case ERROR_REQUEST_RESPONSE_PARSE:
				serverCommMessageCb.signalResultParseError( request.getReceivedText(), request.getTrace() );
				break;
			case ERROR_REQUEST_GWT:
				serverCommMessageCb.signalRequestError( request.getTrace(), exception );
				break;
			}
		}

		public void answer( RequestCallInfo info, int msgLevel, String msg, GenericJSO hangOut, ResponseJSO retValue, NetworkPendingRequestInfo request )
		{
			// signal server message to interested callbacks
			if( msg.length() > 0 )
				serverCommMessageCb.signalServerCommMessage( msgLevel, msg );

			if( hangOut != null )
			{
				// the server tells us a hang out process is needed
				hangOut( info, hangOut );
			}
			else
			{
				// normal case fallback
				info.giveResultToCallbacks( retValue, msgLevel, msg );
			}
		}

		@Override
		public List<BeforeNetworkRequestHandler> getBeforeNetworkRequestHandlers()
		{
			return beforeNetworkRequestHandlers;
		}

		@Override
		public List<AfterNetworkRequestHandler> getAfterNetworkRequestHandlers()
		{
			return afterNetworkRequestHandlers;
		}
	};
}