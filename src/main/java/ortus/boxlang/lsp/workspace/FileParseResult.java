package ortus.boxlang.lsp.workspace;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.Issue;
import ortus.boxlang.compiler.ast.statement.BoxFunctionDeclaration;
import ortus.boxlang.compiler.parser.Parser;
import ortus.boxlang.compiler.parser.ParsingResult;
import ortus.boxlang.lsp.App;
import ortus.boxlang.lsp.DocumentSymbolBoxNodeVisitor;
import ortus.boxlang.lsp.SourceCodeVisitor;
import ortus.boxlang.lsp.SourceCodeVisitorService;
import ortus.boxlang.lsp.lint.DiagnosticRuleRegistry;
import ortus.boxlang.lsp.lint.rules.PossibleTypoRule;
import ortus.boxlang.lsp.workspace.types.ParsedProperty;
import ortus.boxlang.lsp.workspace.visitors.FunctionReturnDiagnosticVisitor;
import ortus.boxlang.lsp.workspace.visitors.PropertyVisitor;

public class FileParseResult {

	public static record ProfilingSnapshot( long fullParses, long parseSourceMillis, long generateDiagnosticsMillis ) {

		// Creates an empty measurement used when profiling starts or is reset.
		public static ProfilingSnapshot empty() {
			return new ProfilingSnapshot( 0L, 0L, 0L );
		}
	}

	private static final LongAdder							FULL_PARSE_COUNT			= new LongAdder();
	private static final LongAdder							PARSE_SOURCE_NANOS			= new LongAdder();
	private static final LongAdder							GENERATE_DIAGNOSTICS_NANOS	= new LongAdder();

	private URI												uri;
	private boolean											isOpen						= false;
	private String											source						= null;
	private WeakReference<ParsingResult>					parseResultRef				= new WeakReference<ParsingResult>( null );
	private List<Issue>										issues						= new ArrayList<Issue>();
	private List<Diagnostic>								diagnostics					= new ArrayList<Diagnostic>();
	private List<CodeAction>								codeActions					= new ArrayList<CodeAction>();
	private List<Either<SymbolInformation, DocumentSymbol>>	outline						= new ArrayList<Either<SymbolInformation, DocumentSymbol>>();
	private List<ParsedProperty>							properties					= new ArrayList<ParsedProperty>();
	private List<SourceCodeVisitor>							visitors					= new ArrayList<SourceCodeVisitor>();
	private List<FunctionDefinition>						functionDefinitions			= new ArrayList<FunctionDefinition>();

	// Creates a result backed by a file on disk and immediately performs a full parse
	// for diagnostics, symbols, properties, and function definitions.
	public static FileParseResult fromFileSystem( URI uri ) {
		FileParseResult fpr = new FileParseResult();
		fpr.uri = uri;

		fpr.fullyParse();

		return fpr;
	}

	/** Parse only the AST for a cold reference search, without generating diagnostics or metadata. */
	// Parses only the AST for a cold reference search, avoiding unnecessary metadata
	// and diagnostic generation.
	public static Optional<BoxNode> astFromFileSystem( URI uri ) {
		FileParseResult result = new FileParseResult();
		result.uri = uri;
		return result.findAstRoot();
	}

	// Creates a result backed by open-editor text so language features use unsaved
	// changes instead of stale file-system contents.
	public static FileParseResult fromSourceString( URI uri, String source ) {
		FileParseResult fpr = new FileParseResult();
		fpr.uri		= uri;
		fpr.source	= source;
		fpr.isOpen	= true;

		fpr.fullyParse();

		return fpr;
	}

	// Returns the URI identifying the source represented by this parse result.
	public URI getURI() {
		return uri;
	}

	// Checks whether this open result already contains the supplied editor text,
	// preventing unnecessary reparsing.
	public boolean hasSource( String content ) {
		return this.isOpen && Objects.equals( this.source, content );
	}

	// Returns properties collected from the parsed AST for workspace features.
	public List<ParsedProperty> properties() {
		return properties;
	}

	// Returns the document outline generated from the parsed AST.
	public List<Either<SymbolInformation, DocumentSymbol>> getOutline() {
		return outline;
	}

	// Returns parser and visitor diagnostics converted to LSP diagnostics.
	public List<Diagnostic> getDiagnostics() {
		return diagnostics;
	}

	// Returns code actions generated while diagnostic visitors inspected the AST.
	public List<CodeAction> getCodeActions() {
		return codeActions;
	}

	// Returns the raw compiler issues captured during parsing.
	public List<Issue> getIssues() {
		return issues;
	}

	// Reads a zero-based source line from open text or disk for source-aware features.
	public String readLine( int lineNumber ) {
		Stream<String> lineStream = Stream.ofNullable( null );

		if ( this.isOpen ) {
			lineStream = List.of( this.source.split( "\n" ) )
			    .stream();
		} else {
			try {
				lineStream = Files.lines( Path.of( this.uri ) );
			} catch ( IOException e ) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return "";
			}
		}

		if ( lineNumber < 0 ) {
			return "";
		}

		return lineStream.skip( lineNumber ).findFirst().orElse( "" );
	}

	// Reads all source lines while normalizing carriage returns for suppression checks
	// and other diagnostics that inspect source text.
	List<String> readAllLines() {
		if ( this.isOpen ) {
			List<String> lines = new ArrayList<>();
			for ( String line : this.source.split( "\n", -1 ) ) {
				lines.add( line.replace( "\r", "" ) );
			}
			return lines;
		}

		try ( Stream<String> lineStream = Files.lines( Path.of( this.uri ) ) ) {
			return lineStream.map( line -> line.replace( "\r", "" ) ).toList();
		} catch ( IOException e ) {
			e.printStackTrace();
			return List.of();
		}
	}

	// Identifies BoxLang template files by their extension.
	public boolean isTemplate() {
		return uri.toString().endsWith( ".bxm" );
	}

	// Identifies class-style BoxLang files by their extension.
	public boolean isClass() {
		return uri.toString().endsWith( ".bx" );
	}

	// Identifies ColdFusion component and template files by their extensions.
	public boolean isCF() {
		return uri.toString().endsWith( ".cfc" ) || uri.toString().endsWith( ".cfm" ) || uri.toString().endsWith( ".cfml" );
	}

	// Locates the main function in the AST for consumers that need the executable
	// entry point without traversing the tree themselves.
	public Optional<BoxFunctionDeclaration> getMainFunction() {
		return findAstRoot()
		    .map( root -> {
			    var funcs = root.getDescendantsOfType( BoxFunctionDeclaration.class, ( n ) -> n.getName().equalsIgnoreCase( "main" ) );

			    return funcs.size() == 0 ? null : funcs.getLast();
		    } );
	}

	// Returns the lazily retained parsing result, reparsing if it was reclaimed.
	public Optional<ParsingResult> getParsingResult() {
		return findParsingResult();
	}

	// Returns the AST root shared by navigation, diagnostics, symbols, and visitors.
	public Optional<BoxNode> findAstRoot() {
		return findParsingResult()
		    .map( ParsingResult::getRoot );
	}

	// Clears global profiling counters so a new measurement interval can begin.
	public static void resetProfiling() {
		FULL_PARSE_COUNT.reset();
		PARSE_SOURCE_NANOS.reset();
		GENERATE_DIAGNOSTICS_NANOS.reset();
	}

	// Returns accumulated parse and diagnostic timing for performance analysis.
	public static ProfilingSnapshot getProfilingSnapshot() {
		return new ProfilingSnapshot(
		    FULL_PARSE_COUNT.sum(),
		    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis( PARSE_SOURCE_NANOS.sum() ),
		    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis( GENERATE_DIAGNOSTICS_NANOS.sum() )
		);
	}

	// Retrieves the cached parse or creates one when the weak reference was reclaimed.
	// Synchronization prevents concurrent callers from duplicating the parse.
	private synchronized Optional<ParsingResult> findParsingResult() {
		ParsingResult result = parseResultRef.get();
		if ( result == null ) {
			result			= parseSource();
			parseResultRef	= new WeakReference<>( result );
		}
		return Optional.ofNullable( result );
	}

	// Parses open in-memory text or a file-system source and records compiler issues.
	// This is the central parsing operation used by full and lazy AST access.
	private ParsingResult parseSource() {
		long	startNanos	= System.nanoTime();
		Parser	parser		= new Parser();

		try {
			ParsingResult result;
			if ( this.isOpen ) {
				String extension = Parser.getFileExtension( this.uri.toString() ).orElse( "bxs" );
				result = parser.parse(
				    this.source,
				    Parser.detectFile( new File( this.uri ) ),
				    shouldParseAsClassLikeSource( extension ),
				    false );
			} else {
				result = parser.parse( Paths.get( this.uri ).toFile(), false );
			}

			this.issues = result == null ? new ArrayList<>() : result.getIssues();
			return result;
		} catch ( Exception e ) {
			this.issues = new ArrayList<>();
			App.logger.error( "Unable to parse " + this.uri, e );
			return null;
		} finally {
			PARSE_SOURCE_NANOS.add( System.nanoTime() - startNanos );
		}
	}

	// Determines whether source needs class-like parsing by combining its extension
	// with leading component or interface declarations.
	private boolean shouldParseAsClassLikeSource( String extension ) {
		if ( extension.matches( "cfc|bx" ) ) {
			return true;
		}

		if ( source == null ) {
			return false;
		}

		String remaining = source.stripLeading();
		while ( remaining.startsWith( "<!---" ) || remaining.startsWith( "<!--" ) ) {
			String	closingDelimiter	= remaining.startsWith( "<!---" ) ? "--->" : "-->";
			int		closingIndex		= remaining.indexOf( closingDelimiter );
			if ( closingIndex < 0 ) {
				return false;
			}
			remaining = remaining.substring( closingIndex + closingDelimiter.length() ).stripLeading();
		}

		String normalized = remaining.toLowerCase( Locale.ROOT );
		return normalized.startsWith( "component" )
		    || normalized.startsWith( "interface" )
		    || normalized.startsWith( "<cfcomponent" )
		    || normalized.startsWith( "<cfinterface" )
		    || normalized.startsWith( "<bx:component" )
		    || normalized.startsWith( "<bx:interface" );
	}

	// Converts parser issues and visitor findings into LSP diagnostics and code actions,
	// then applies source suppression rules before exposing the results.
	private List<Diagnostic> generateDiagnostics( BoxNode astRoot ) {
		long				startNanos		= System.nanoTime();

		List<Diagnostic>	fileDiagnostics	= new ArrayList<>();
		List<CodeAction>	fileCodeActions	= new ArrayList<>();

		fileDiagnostics.addAll( issues.stream().map( ( issue ) -> {
			Diagnostic diagnostic = new Diagnostic();

			diagnostic.setSeverity( DiagnosticSeverity.Error );
			diagnostic.setMessage( issue.getMessage() );

			diagnostic.setRange( BLASTTools.positionToRange( issue.getPosition() ) );

			diagnostic.setMessage( issue.getMessage() );

			return diagnostic;
		} ).toList() );

		if ( astRoot != null ) {
			fileDiagnostics.addAll( generateMalformedFunctionDiagnostics( astRoot ) );

			FunctionReturnDiagnosticVisitor returnDiagnosticVisitor = new FunctionReturnDiagnosticVisitor();
			astRoot.accept( returnDiagnosticVisitor );
			fileDiagnostics.addAll( returnDiagnosticVisitor.getDiagnostics() );

			List<SourceCodeVisitor> visitors = SourceCodeVisitorService.getInstance().forceVisit( this.uri.toString(),
			    astRoot );

			for ( SourceCodeVisitor visitor : visitors ) {
				if ( !visitor.canVisit( this ) ) {
					continue;
				}

				fileDiagnostics.addAll( visitor.getDiagnostics() );
				fileCodeActions.addAll( visitor.getCodeActions() );
			}

			DiagnosticSuppressionFilter suppressionFilter = DiagnosticSuppressionFilter.fromAst( astRoot, readAllLines() );
			fileDiagnostics	= new ArrayList<>( suppressionFilter.filterDiagnostics( fileDiagnostics ) );
			fileCodeActions	= new ArrayList<>( suppressionFilter.filterCodeActions( fileCodeActions ) );
		}

		this.codeActions = fileCodeActions;

		try {
			return fileDiagnostics;
		} finally {
			GENERATE_DIAGNOSTICS_NANOS.add( System.nanoTime() - startNanos );
		}
	}

	// Reports likely misspelled function declarations when malformed syntax prevents
	// the normal parser path from producing a function declaration.
	private List<Diagnostic> generateMalformedFunctionDiagnostics( BoxNode astRoot ) {
		if ( !DiagnosticRuleRegistry.getInstance().isEnabled( PossibleTypoRule.ID, true ) || ! ( astRoot instanceof BoxClass boxClass ) ) {
			return List.of();
		}

		List<Diagnostic> diagnostics = new ArrayList<>();
		for ( PossibleTypoDetector.Match match : PossibleTypoDetector.findFunctionKeywordTypos( boxClass ) ) {
			Diagnostic diagnostic = new Diagnostic();
			diagnostic.setSeverity( DiagnosticSeverity.Error );
			diagnostic.setMessage( "Invalid function declaration: expected 'function'" );
			diagnostic.setRange( BLASTTools.positionToRange( match.statement().getPosition() ) );
			diagnostics.add( diagnostic );
		}
		return diagnostics;
	}

	// Runs the complete parse pipeline and refreshes properties, outline, functions,
	// diagnostics, and code actions used by the language server.
	private synchronized void fullyParse() {
		FULL_PARSE_COUNT.increment();
		ParsingResult result = parseSource();
		parseResultRef = new WeakReference<>( result );
		BoxNode root = result == null ? null : result.getRoot();
		try {
			properties			= root == null ? List.of() : parseProperties( root );
			outline				= root == null ? List.of() : generateOutline( this.uri, root );
			functionDefinitions	= root == null ? List.of() : generateFunctionDefinitions( this.uri, root );
			diagnostics			= generateDiagnostics( root );
		} finally {
			Reference.reachabilityFence( result );
		}
	}

	// Traverses the AST to collect property declarations for workspace features.
	private List<ParsedProperty> parseProperties( BoxNode root ) {
		PropertyVisitor visitor = new PropertyVisitor();

		root.accept( visitor );

		return visitor.getProperties();
	}

	// Traverses the AST to build the editor outline for the source document.
	private List<Either<SymbolInformation, DocumentSymbol>> generateOutline( URI textDocument, BoxNode root ) {
		DocumentSymbolBoxNodeVisitor visitor = new DocumentSymbolBoxNodeVisitor();

		visitor.setFilePath( Paths.get( textDocument ) );
		root.accept( visitor );

		return visitor.getDocumentSymbols();
	}

	// Traverses the AST to collect function declarations used by navigation and the
	// project index.
	private List<FunctionDefinition> generateFunctionDefinitions( URI textDocument, BoxNode root ) {
		FunctionDefinitionVisitor visitor = new FunctionDefinitionVisitor();

		visitor.setFileURI( textDocument );
		root.accept( visitor );

		this.functionDefinitions = this.functionDefinitions.stream().filter( ( fnDef ) -> {
			return !fnDef.getFileURI().equals( textDocument );
		} )
		    .collect( Collectors.toList() );

		return visitor.getFunctionDefinitions();
	}

	// Returns function definitions collected during the full parse.
	public List<FunctionDefinition> getFunctionDefinitions() {
		return functionDefinitions;
	}

	/** Force a full reparse (used when lint configuration changes). */
	// Forces the full pipeline to run again when source or lint configuration changes.
	public void reparse() {
		fullyParse();
	}
}
