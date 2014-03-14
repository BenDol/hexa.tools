package com.hexa.client.tableobserver;

import com.hexa.client.interfaces.IHasIntegerId;

public abstract class SimpleDataPlug<T extends IHasIntegerId> implements XTableListen<T>
{
	@Override
	public final void deleted( int recordId, T oldRecord )
	{
		currentRecord( null );
	}

	@Override
	public final void updated( int recordId, T record )
	{
		currentRecord( record );
	}

	@Override
	public final void updatedField( int recordId, String fieldName, T record )
	{
		currentRecord( record );
	}

	@Override
	public final void wholeTable( Iterable<T> records )
	{
		boolean ok = false;
		for( T r : records )
		{
			ok = true;
			currentRecord( r );
			break;
		}
		if( !ok )
			currentRecord( null );
	}

	@Override
	public final void clearAll()
	{
		currentRecord( null );
	}

	protected abstract void currentRecord( T record );
}