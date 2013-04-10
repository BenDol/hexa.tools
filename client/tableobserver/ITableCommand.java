package com.hexa.client.tableobserver;

import com.hexa.client.interfaces.IAsyncCallback;

public interface ITableCommand<T>
{
	void quit();

	void askRefreshTable();

	boolean isLoaded();

	void waitLoaded( IAsyncCallback<Integer> callback );

	boolean isEmpty(); // only works after the first "wholeTable" callback call

	T getRecord( int recordId ); // only works after the first "wholeTable"
									// callback call

	Iterable<T> getRecords(); // only works after the first "wholeTable"
								// callback call
}
