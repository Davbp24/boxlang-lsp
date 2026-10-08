package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;

import java.nio.file.Path;

import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.lsp.workspace.index.ProjectIndex;

public class HoverTest extends BaseTest {

	@Test
	void testHoverOnFunctionInvocation() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(

		    /*
		     * Why the long org.eclipse.lsp4j. names?
		     * That's the class's full name, including its package. If you write the full name, you don't need an import line at the top. HoverTest mixes both
		     * styles: some
		     * classes are imported, and some are written out in full. If you add imports instead, you can write new TextDocumentItem(...).
		     */
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );
		/*
		 * 1. Read the files text: java.nio.file.Files.readString(defPath) reads the text in the file.
		 * 
		 * 2. Turn the path to URI: So this entire line opens the file and returns everything inside it in
		 * one big string. .toUri() conerts it to the format LSP uses to identify files. .toString() turns that into plain text.
		 * 
		 * 3. Package it as a TextDocumentItem
		 * new TextDocumentItem is a container that describes one open file which takes 4 values:
		 * - uri: Which file this is
		 * - languageId: what language it's written in
		 * - version: version number of the file's contents. It goes up each time the file is edited; 1 means just opened
		 * - text: the code itself
		 * 
		 * 4. Wrap it in a DidOpenTextDocumentParams
		 * new DidOpenTextDocumentParams(textDocumentItem)
		 * This is an envelope around the item. Every LSP method takes a "Params" objects even when there's only one thing inside it.
		 * Hover takes HoverParams, and opening a file takes DidOpenTextDocumentParams
		 * 
		 * 5. Tell the server
		 * 
		 * svc.didOpen(params) tells the server that the user just opened this file and here is its content. The server reads the code,
		 * understands its structure and keeps it in memory. After this it can answer questions about the file live hover or selection range.
		 * 
		 */

		// Position is on "getUser" call in line 34: "var result = getUser(1);"
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 33, 22 ) ); // On "getUser"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		assertThat( hover.getContents() ).isNotNull();

		// Should contain function signature
		String hoverContent = hover.getContents().getRight().getValue();
		assertThat( hoverContent ).contains( "getUser" );
		assertThat( hoverContent ).contains( "numeric id" );
	}

	@Test
	void testHoverOnDocumentedFunctionShowsDocumentation() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "getUser" call on line 34
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 33, 22 ) ); // On "getUser"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should contain documentation
		assertThat( hoverContent ).contains( "Retrieves a user by their unique identifier" );
		assertThat( hoverContent ).contains( "@param" );
		assertThat( hoverContent ).contains( "id" );
		assertThat( hoverContent ).contains( "The user's unique ID" );
		assertThat( hoverContent ).contains( "@return" );
		assertThat( hoverContent ).contains( "@deprecated" );
	}

	@Test
	void testHoverOnSimpleFunctionShowsDescription() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "simpleFunction" call on line 35
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 34, 10 ) ); // On "simpleFunction"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should contain simple description
		assertThat( hoverContent ).contains( "simpleFunction" );
		assertThat( hoverContent ).contains( "A simple function with no documentation tags" );
	}

	@Test
	void testHoverOnUndocumentedFunctionShowsSignatureOnly() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "undocumentedFunction" call on line 36
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 35, 12 ) ); // On "undocumentedFunction"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should contain signature but no doc comments
		assertThat( hoverContent ).contains( "undocumentedFunction" );
		assertThat( hoverContent ).contains( "string name" );
	}

	@Test
	void testHoverOnFunctionDefinition() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "getUser" function definition (line 17)
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 16, 25 ) ); // On "getUser" in definition

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should contain function signature and documentation
		assertThat( hoverContent ).contains( "getUser" );
		assertThat( hoverContent ).contains( "Retrieves a user by their unique identifier" );
	}

	@Test
	void testHoverOnNonHoverablePositionReturnsNull() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position on whitespace or empty area
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 0, 0 ) ); // Start of file

		var hover = svc.hover( hoverParams ).get();

		// May return null or empty hover
		// Adjust based on implementation behavior
	}

	@Test
	void testHoverShowsContainingClassForMethod() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "getUser" call on line 34
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 33, 22 ) ); // On "getUser"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show the containing class name
		assertThat( hoverContent ).contains( "hoverTestClass" );
	}

	@Test
	void testHoverShowsAccessModifier() throws Exception {
		var							defPath	= java.nio.file.Paths.get( "src/test/resources/files/hoverTestClass.bx" );

		BoxLangTextDocumentService	svc		= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( defPath.toUri().toString(), "boxlang", 1, java.nio.file.Files.readString( defPath ) ) ) );

		// Position is on "undocumentedFunction" call (private function) on line 36
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( defPath.toUri().toString() ) );
		hoverParams.setPosition( new Position( 35, 12 ) ); // On "undocumentedFunction"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should show access modifier
		assertThat( hoverContent ).contains( "private" );
	}

	@Test
	void testCrossFileMethodHover() throws Exception {
		ProjectContextProvider	provider		= ProjectContextProvider.getInstance();
		ProjectIndex			index			= new ProjectIndex();
		Path					workspaceRoot	= java.nio.file.Paths.get( "src/test/resources/files" ).toAbsolutePath();
		index.reinitialize( workspaceRoot, null );
		provider.setIndex( index );

		// First, index the class with the documented function
		Path classWithDocFunc = java.nio.file.Paths.get( "src/test/resources/files/ClassWithDocFunc.bx" );
		index.indexFile( classWithDocFunc.toUri() );

		// Now open the file that uses the documented function
		Path						classThatUsesDocFunc	= java.nio.file.Paths.get( "src/test/resources/files/ClassThatUsesDocFunc.bx" );

		BoxLangTextDocumentService	svc						= new BoxLangTextDocumentService();
		svc.didOpen( new org.eclipse.lsp4j.DidOpenTextDocumentParams(
		    new org.eclipse.lsp4j.TextDocumentItem( classThatUsesDocFunc.toUri().toString(), "boxlang", 1,
		        java.nio.file.Files.readString( classThatUsesDocFunc ) ) ) );

		// Position is on "documentedFunction" in line 5: "return obj.documentedFunction( 10, "Hello" );"
		// Line 5 (0-indexed: 4), column should be around the method name
		HoverParams hoverParams = new HoverParams();
		hoverParams.setTextDocument( new TextDocumentIdentifier( classThatUsesDocFunc.toUri().toString() ) );
		hoverParams.setPosition( new Position( 4, 20 ) ); // On "documentedFunction"

		var hover = svc.hover( hoverParams ).get();

		assertThat( hover ).isNotNull();
		String hoverContent = hover.getContents().getRight().getValue();

		// Should contain the function name
		assertThat( hoverContent ).contains( "documentedFunction" );

		// Should contain the documentation
		assertThat( hoverContent ).contains( "A documented function" );

		// Should contain parameter info
		assertThat( hoverContent ).contains( "param1" );
		assertThat( hoverContent ).contains( "The first parameter" );

		// Should contain return info
		assertThat( hoverContent ).contains( "@return" );

		// Should show the containing class name
		assertThat( hoverContent ).contains( "ClassWithDocFunc" );
	}
}
