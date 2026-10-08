package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Paths;
import java.util.List;

//lsp4j is a library used to define LSP messages in Java
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectionRange;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;//Library that runs tests

public class SelectionRangeTest extends BaseTest {
	/*
	 * BaseTest starts the BoxLang runtime, which is used to read Boxlangs code

	 * This name "SelectionRangeTest" groups related tests together, it is named after the feature plus test.
	 * We are adding the expand selection feature so we named it like SelectionRangeTest
	 * Extend means it inherits the setup that starts the Boxlang runtime
	 */

	@Test // tells JUnit to run this
	void onAFunctionCall() throws Exception { //tests expand selection on a function call
		var							defPath		= Paths.get( "src/test/resources/files/hoverTestClass.bx" ); // input file or "fixture"
		BoxLangTextDocumentService	svc			= new BoxLangTextDocumentService(); //creates the server. svc is the part of the server that handles requests about files. This includes hover and soon selection ranges.
		//four details the server needs about the file. 
		String						uri			= defPath.toUri().toString();
		String						languageId	= "boxlang";
		int							version		= 1;
		String						text		= java.nio.file.Files.readString( defPath ); //actual code, read from the file

		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams( new org.eclipse.lsp4j.TextDocumentItem( uri, languageId, version, text ) ) );
		//tells the server that file was just opened and gives the server the code. The server reads the code and builds the tree in memory. 

        SelectionRangeParams selectionRangeParams = new SelectionRangeParams();
		//Creates an empty question form for the selection ranges
        selectionRangeParams.setTextDocument(new TextDocumentIdentifier(uri));
		//Files in which file to ask about. Server matches the question to the this file
        selectionRangeParams.setPositions(List.of(new Position(33, 22)));
		//Files in where the cursor is: line 33, column 22. Sends a list cause VS Code can send serveral cursors at once. 
        List<SelectionRange> ranges = svc.selectionRange(selectionRangeParams).get();
		/*Saves the range where the box starts and ends in the file and the parent, the next bigger box, which is another
		SelectionRange with its own range and parent.

		So the answer is a chain, roughly like this:
		*/

		assertThat(ranges).isNotNull();
		assertThat(ranges).hasSize(1);

		assertThat(ranges.get(0).getRange()).isEqualTo(new Range(new Position(33, 21), new Position(33, 31)));
		


		/* 

	ranges
	└── [0]  range: getUser                        ← smallest box (where the cursor is)
			parent ─┐
					range: getUser(1)
					parent ─┐
							range: result = getUser(1)
							parent ─┐
									range: the whole line
									parent ─┐
											range: the whole caller() function
											parent ─┐
													range: the whole file
													parent: none   ← end of the chain
		*/



	}

	@Test
	void onAnIdentifier() throws Exception { // tests expand selection on a variable name (identifier)
		var							defPath	= Paths.get( "src/test/resources/files/hoverTestClass.bx" );
		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		String						uri		= defPath.toUri().toString();
		String						text	= java.nio.file.Files.readString( defPath );

		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams( new org.eclipse.lsp4j.TextDocumentItem( uri, "boxlang", 1, text ) ) );

		// Line 30 in the editor (29 here): return "Hello, " & name;
		// The cursor is on the "a" in name
		SelectionRangeParams selectionRangeParams = new SelectionRangeParams();
		selectionRangeParams.setTextDocument( new TextDocumentIdentifier( uri ) );
		selectionRangeParams.setPositions( List.of( new Position( 29, 28 ) ) );
		List<SelectionRange> ranges = svc.selectionRange( selectionRangeParams ).get();

		assertThat( ranges ).hasSize( 1 );

		// The smallest box is just the word name: columns 27 to 31
		assertThat( ranges.get( 0 ).getRange() ).isEqualTo( new Range( new Position( 29, 27 ), new Position( 29, 31 ) ) );
	}

}
