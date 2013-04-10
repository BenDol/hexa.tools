package com.hexa.client.ui;

import com.hexa.client.interfaces.IStackPanel;
import com.hexa.client.interfaces.IStackPanelRow;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

public class AbsoluteStackPanel extends Composite implements IStackPanel
{
	FlowPanel m_stack = new FlowPanel();

	public AbsoluteStackPanel()
	{
		initWidget( m_stack );
	}

	private class RowItem implements IStackPanelRow
	{
		AbsolutePanel row;

		RowItem()
		{
			row = new AbsolutePanel();

			row.setWidth( m_stack.getOffsetWidth() + "px" );
		}

		public void setHeight( int height )
		{
			row.setHeight( height + "px" );
		}

		public void addItem( Widget w, int x, int y, int sx, int sy )
		{
			row.add( w, x, y );
			w.setPixelSize( sx, sy );
		}

		public void repositionWidget( Widget w, int x, int y, int sx, int sy )
		{
			row.setWidgetPosition( w, x, y );
			w.setPixelSize( sx, sy );
		}

		public void removeItem( Widget w )
		{
			row.remove( w );
		}

		public void clearAll()
		{
			row.clear();
		}

		public void setVisible( boolean visible )
		{
			row.setVisible( visible );
		}

		@Override
		public IStackPanelRow createSubRow()
		{
			RowItem item = new RowItem();
			m_stack.insert( item.row, m_stack.getWidgetIndex( row ) + 1 );

			return item;
		}
	}

	public IStackPanelRow addRow()
	{
		RowItem item = new RowItem();

		m_stack.add( item.row );

		return item;
	}

	public void clear()
	{
		m_stack.clear();
	}

	// addSubPanel
}
