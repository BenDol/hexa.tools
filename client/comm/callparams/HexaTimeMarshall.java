package com.hexa.client.comm.callparams;

import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.hexa.client.common.HexaTime;

public class HexaTimeMarshall implements ICallParamMarshall<HexaTime>
{
	public JSONValue marshall( HexaTime value )
	{
		if( value == null )
			return null;
		return new JSONString( value.getString() );
	}

}
