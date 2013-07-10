package com.hexa.client.classinfo;

import java.util.List;

public interface Clazz<T>
{
	String getClassName();

	Class<T> getReflectedClass();

	List<Field<T>> getFields();

	Field<T> getField( String fieldName );

	List<Method> getMethods();

	Method getMethod( String methodName );

	T NEW();
}