package com.hexa.client.ui.miracle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.google.gwt.user.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Widget;
import com.hexa.client.ui.TreeTable;
import com.hexa.client.ui.TreeTableBase.Item;
import com.hexa.client.ui.TreeTableHandler;
import com.hexa.client.ui.miracle.Edits.Editor;

public class DynArrayInTreeTable<T, H> implements Prints<Iterable<T>>, DynArrayManager<T>, HasColumns<T, H>
{
	TreeTable table;
	RefMng<T> refMng;

	H headerData = null;

	ArrayList<DynArrayInFlexTableColumnMng<T, H>> columns = new ArrayList<DynArrayInFlexTableColumnMng<T, H>>();

	Comparator<T> userComparator = null;

	// edition state
	private class EditionState
	{
		int editedCol = -1;
		T editedObject = null;
		Printer editedPrinter = null;
		Editor editedEditor = null;

		void close()
		{
			if( editedEditor != null )
				editedEditor.close();
			editedEditor = null;
		}
	}

	EditionState edition = null;

	public DynArrayInTreeTable( TreeTable table, RefMng<T> refMng )
	{
		this.table = table;
		this.refMng = refMng;

		table.setHandler( tableHandler );
		table.addDomHandler( onTableKeyUp, KeyDownEvent.getType() );
		table.addDomHandler( onTableMouseDown, MouseDownEvent.getType() );
	}

	public void setHeaderData( H headerData )
	{
		this.headerData = headerData;
	}

	@Override
	public void addColumn( PrintsOn<T> column, Edits<T> editMng, CellClickMng<T> clickMng, PrintsOn<H> hdrPrintsOn, CellClickMng<H> hdrClickMng )
	{
		columns.add( new DynArrayInFlexTableColumnMng<T, H>( column, editMng, clickMng, hdrPrintsOn, hdrClickMng ) );
	}

	class HdrInTreeTablePrinter implements Printer
	{
		TreeTable table;
		int col;

		HdrInTreeTablePrinter( TreeTable table, int col )
		{
			this.table = table;
			this.col = col;
		}

		@Override
		public void setText( String text )
		{
			table.setHeader( col, text );
		}

		@Override
		public void setHTML( String html )
		{
			setText( html );
		}

		@Override
		public void setWidget( Widget widget )
		{
			assert false;
			table.setHeader( col, "WIDGET PLACE" );
		}
	}

	class CellInTreeTablePrinter implements Printer
	{
		Item item;
		int col;

		CellInTreeTablePrinter( Item item, int col )
		{
			this.item = item;
			this.col = col;
		}

		@Override
		public void setText( String text )
		{
			item.setText( col, text );
		}

		@Override
		public void setHTML( String html )
		{
			item.setHTML( col, html );
		}

		@Override
		public void setWidget( Widget widget )
		{
			item.setWidget( col, widget );
		}
	}

	public void printHeaders()
	{
		HdrInTreeTablePrinter printer = new HdrInTreeTablePrinter( table, 0 );
		int nbCols = columns.size();
		for( int i = 0; i < nbCols; i++ )
		{
			printer.col = i;

			DynArrayInFlexTableColumnMng<T, H> c = columns.get( i );
			if( c.hdrPrintsOn.print( headerData, printer ) )
				printer = new HdrInTreeTablePrinter( table, 0 );
		}
	}

	@Override
	public void print( Iterable<T> data )
	{
		killCurrentEdit();
		table.emptyTable();

		if( data == null )
			return;

		// sort if needed
		if( userComparator != null )
		{
			ArrayList<T> tmp = new ArrayList<T>();
			for( T t : data )
			{
				int ins = Collections.binarySearch( tmp, t, userComparator );
				tmp.add( -ins - 1, t );
			}
			data = tmp;
		}

		CellInTreeTablePrinter cp = new CellInTreeTablePrinter( null, 0 );

		// each row
		for( T d : data )
		{
			cp.item = table.addItem( null );
			cp.item.setRef( refMng.getRef( d ) );

			cp = printRow( d, cp );
		}
	}

	public void updateRow( T object )
	{
		// find the row associated with this object
		int objectRef = refMng.getRef( object );
		Item item = getRow( objectRef );

		if( edition != null && refMng.getRef( edition.editedObject ) == objectRef )
			killCurrentEdit();

		Item insPos = userComparator != null ? getInsertPoint( object ) : item;

		// insert or move the row to the right place and create a cell printer
		if( item == null )
		{
			// new
			if( insPos != null )
				insPos = table.addItemBefore( insPos );
			else
				insPos = table.addItem( null );

			insPos.setRef( refMng.getRef( object ) );
		}
		else
		{
			if( insPos != item )
			{
				// GWT.log( "is at row " + row + " but should be at " + insPos
				// );
				item.moveBefore( insPos );
				insPos = item;
			}
		}
		CellInTreeTablePrinter cp = new CellInTreeTablePrinter( insPos, 0 );

		// print the row
		printRow( object, cp );
	}

	private Item getInsertPoint( T object )
	{
		ArrayList<Item> items = table.getItemChilds( null );
		int nbRows = items.size();
		int objectRef = refMng.getRef( object );

		if( userComparator != null )
		{
			for( Item item : items )
			{
				int refAtRow = item.getRef();

				if( userComparator.compare( object, refMng.getObject( refAtRow ) ) <= 0 )
				{
					if( refAtRow == objectRef )
					{
						// keep the same place only if lesser than the nxt item
						// or if there is no next
						if( items.indexOf( item ) == nbRows - 1 )
						{
							return item;
						}
						else
						{
							int refAtNextRow = getRefAtRow( item.getNextSiblingItem() );
							if( userComparator.compare( object, refMng.getObject( refAtNextRow ) ) <= 0 )
								return item;
						}
					}
					else
					{
						return item;
					}
				}
			}
		}

		return null;
	}

	public void deleteRow( int ref )
	{
		Item item = getRow( ref );
		if( item == null )
			return;

		// to reset the edition state, just in case... (an only if we are
		// editing on the row we delete)
		if( edition != null && refMng.getRef( edition.editedObject ) == ref )
			killCurrentEdit();

		item.remove();
	}

	public void setComparator( Comparator<T> comparator )
	{
		userComparator = comparator;
		sortAndPrint( comparator );
	}

	public void sortAndPrint( final Comparator<T> userComparator )
	{
		class It
		{
			Item item;
			T object;

			It( Item item, T object )
			{
				this.object = object;
				this.item = item;
			}
		}
		;

		ArrayList<It> its = new ArrayList<It>();

		// get all objects from the table
		ArrayList<Item> childs = table.getItemChilds( null );
		for( Item item : childs )
		{
			int ref = getRefAtRow( item );

			its.add( new It( item, refMng.getObject( ref ) ) );
		}

		// sort them
		Collections.sort( its, new Comparator<It>()
		{
			public int compare( It o1, It o2 )
			{
				return userComparator.compare( o1.object, o2.object );
			}
		} );

		// now reorder the lines
		for( int j = 0; j < its.size(); j++ )
		{
			// item at position j ?
			Item atJ = table.getItemChilds( null ).get( j );
			// needed item is its[i].item
			Item neededItem = its.get( j ).item;

			if( atJ != neededItem )
			{
				// move its[i].item before item at position i
				neededItem.moveBefore( atJ );
			}
		}
	}

	private int getRefAtRow( Item item )
	{
		return item.getRef();
	}

	// return the row index of the row associated to the object referenced as
	// objectRef
	// returns null if the row is not found
	private Item getRow( int objectRef )
	{
		return table.getItemForRef( objectRef );
	}

	// returns a printer that can be used for the next call. a new one can be
	// created
	// WARNING : assumes the printer item is correctly initialized
	private CellInTreeTablePrinter printRow( T object, CellInTreeTablePrinter printer )
	{
		// to reset the edition state, just in case...
		if( edition != null && refMng.getRef( edition.editedObject ) == refMng.getRef( object ) )
			killCurrentEdit();

		// writes the element reference on the corresponding row element
		// note that this does not create a hard link to the referenced object
		// so
		// no garbage is created here
		printer.item.setRef( refMng.getRef( object ) );

		// each column
		for( int i = 0; i < columns.size(); i++ )
		{
			printer.col = i;

			boolean fStillInUse = columns.get( i ).prints.print( object, printer );

			// recreate a printer if the old one is still in use by our client
			if( fStillInUse )
				printer = new CellInTreeTablePrinter( printer.item, 0 );
		}

		return printer;
	}

	private boolean beginEdit( Item item, int col )
	{
		int ref = getRefAtRow( item );

		if( edition != null && ref == refMng.getRef( edition.editedObject ) )
			return true; // already began !!!

		killCurrentEdit();

		Edits<T> editMng = columns.get( col ).edits;
		if( editMng == null )
			return false;

		// find the T object that has been clicked
		T object = refMng.getObject( ref );
		assert object != null;
		if( object == null )
			return false;

		Element td = item.getTdElement( col );

		edition = new EditionState();

		// initialize and set the current edit
		edition.editedCol = col;
		edition.editedObject = object;

		// create a Printer corresponding to this cell
		edition.editedPrinter = new CellInTreeTablePrinter( item, col );

		// create an editor and init it
		// -2 to remove padding : HACK HACK HACK
		edition.editedEditor = editMng.createEditor( edition.editedObject, edition.editedPrinter, onEdit, td.getOffsetWidth() - 2, td.getClientHeight() - 2 );

		return true;
	}

	private static class CellPos
	{
		Item item;
		int col;

		CellPos( Item item, int col )
		{
			this.item = item;
			this.col = col;
		}
	}

	private MouseDownHandler onTableMouseDown = new MouseDownHandler()
	{
		@Override
		public void onMouseDown( MouseDownEvent event )
		{
			Element source = DOM.eventGetTarget( Event.as( event.getNativeEvent() ) );

			// try to see if it's not on a th element
			int hdr = table.getEventTargetHeaderIdx( source );
			if( hdr < 0 )
				return;

			DragDrop.initiate( source, onDragDrop, hdr, Event.as( event.getNativeEvent() ) );
		}
	};

	DragDrop.Callback<Integer> onDragDrop = new DragDrop.Callback<Integer>()
	{
		@Override
		public String getGhostInnerHTML( Integer cookie, Element source )
		{
			// return
			// "<div style='background-color:rgba(120,120,120,0.4);'>Moving column "+cookie+"</div>";

			class TempPrinter implements Printer
			{
				String html = null;

				public void setWidget( Widget widget )
				{
					assert false;
				}

				public void setText( String text )
				{
					html = text;
				}

				public void setHTML( String html )
				{
					this.html = html;
				}
			}

			TempPrinter printer = new TempPrinter();

			columns.get( cookie ).hdrPrintsOn.print( headerData, printer );

			// Due to asynchronism, it may be possible that the printer did not
			// yet print anything, well... Too late, we use the source element
			// instead !!
			if( printer.html == null )
				return "<div style='background-color:rgba(120,120,120,0.4);'>" + source.getInnerHTML() + "</div>";

			return "<div style='background-color:rgba(120,120,120,0.4);width:20px;height:20px;'>" + printer.html + "</div>";
		}

		@Override
		public void onDragDropFinished( Integer cookie, Element source, Element destination )
		{
			int newPos = table.getEventTargetHeaderIdx( destination );
			if( newPos < 0 )
				return;

			DynArrayInFlexTableColumnMng<T, H> dum = columns.get( cookie );
			columns.set( cookie, columns.get( newPos ) );
			columns.set( newPos, dum );

			// Redraw headers
			printHeaders();

			// Redraw all objects in the table
			CellInTreeTablePrinter printer = new CellInTreeTablePrinter( null, 0 );
			ArrayList<Item> items = table.getItemChilds( null );
			for( Item item : items )
			{
				printer.item = item;
				int ref = item.getRef();
				printer = printRow( refMng.getObject( ref ), printer );
			}
		}
	};

	private Edits.Callback onEdit = new Edits.Callback()
	{
		@Override
		public void cancelEdition()
		{
			killCurrentEdit();
		}

		@Override
		public void validateEdition( boolean fJumpNext )
		{
			// just forget the edition state, but don't redraw
			// that means we let the client continue drawing on its cell
			if( edition != null && fJumpNext )
			{
				CellPos next = getNextPos( getRow( refMng.getRef( edition.editedObject ) ), edition.editedCol );

				edition.close();
				edition = null;

				beginEdit( next.item, next.col );
			}
		}
	};

	// wait a bit to avoid ui jitter...
	// use refMng in case the data change during the time waited
	private void killCurrentEdit()
	{
		if( edition == null )
			return;

		// reprints the cell, with uptodate data
		columns.get( edition.editedCol ).prints.print( refMng.getObject( refMng.getRef( edition.editedObject ) ), edition.editedPrinter );

		edition.close();
		edition = null;
	}

	CellPos getNextPos( Item item, int col )
	{

		// either on the same row
		for( int c = col + 1; c < columns.size(); c++ )
		{
			if( columns.get( c ).edits != null )
				return new CellPos( item, c );
		}

		// or on the next
		item = item.getNextSiblingItem();
		for( int c = 0; c < columns.size(); c++ )
		{
			if( columns.get( c ).edits != null )
				return new CellPos( item, c );
		}

		return null;
	}

	CellPos getPrevPos( Item item, int col )
	{
		// either on the same row
		for( int c = col - 1; c >= 0; c-- )
		{
			if( columns.get( c ).edits != null )
				return new CellPos( item, c );
		}

		// or on the previous
		item = item.getPrevSiblingItem();
		for( int c = columns.size() - 1; c >= 0; c-- )
		{
			if( columns.get( c ).edits != null )
				return new CellPos( item, c );
		}

		return null;
	}

	private KeyDownHandler onTableKeyUp = new KeyDownHandler()
	{
		@Override
		public void onKeyDown( KeyDownEvent event )
		{
			if( event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE )
			{
				if( edition == null )
					return;

				event.stopPropagation();
				event.preventDefault();

				killCurrentEdit();
			}
			else if( event.getNativeKeyCode() == KeyCodes.KEY_TAB )
			{
				if( edition == null )
					return;

				event.stopPropagation();
				event.preventDefault();

				// find next cell to be edited

				Item editionItem = getRow( refMng.getRef( edition.editedObject ) );
				CellPos pos = null;
				if( event.isShiftKeyDown() )
					pos = getPrevPos( editionItem, edition.editedCol );
				else
					pos = getNextPos( editionItem, edition.editedCol );

				if( pos != null )
				{
					beginEdit( pos.item, pos.col );
					return;
				}
			}
		}
	};

	private TreeTableHandler tableHandler = new TreeTableHandler()
	{
		@Override
		public void onTableHeaderClick( int column, ClickEvent event )
		{
			CellClickMng<H> clickMng = columns.get( column ).hdrClickMng;
			if( clickMng == null )
				return;

			// get the printer, and go
			Printer printer = table.getHeaderPrinter( column );
			clickMng.onTableClick( headerData, DOM.eventGetTarget( Event.as( event.getNativeEvent() ) ), printer );
		}

		@Override
		public void onTableCellClick( Item item, int column, ClickEvent event )
		{
			int clickedRef = getRefAtRow( item );

			// if clicking on a cell that is in editing mode, return
			if( edition != null && (refMng.getRef( edition.editedObject ) == clickedRef && edition.editedCol == column) )
				return;

			killCurrentEdit();

			boolean fHandled = false;

			// Tries edition
			fHandled = beginEdit( item, column );

			// If not handled, tries click manager
			if( !fHandled )
			{
				// if there is a click manager
				CellClickMng<T> clickMng = columns.get( column ).clicks;
				if( clickMng != null )
				{
					// find the T object that has been clicked
					T object = refMng.getObject( clickedRef );
					if( object == null )
						return;

					CellInTreeTablePrinter pr = new CellInTreeTablePrinter( item, column );

					fHandled = clickMng.onTableClick( object, DOM.eventGetTarget( Event.as( event.getNativeEvent() ) ), pr );
				}
			}
		}
	};
}