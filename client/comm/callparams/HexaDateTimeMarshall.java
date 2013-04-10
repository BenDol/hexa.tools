package com.hexa.client.comm.callparams;

import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.hexa.client.common.HexaDateTime;

public class HexaDateTimeMarshall implements ICallParamMarshall<HexaDateTime>
{
	public JSONValue marshall( HexaDateTime value )
	{
		if( value == null )
			return null;
		return new JSONString( value.getString() );
	}

}