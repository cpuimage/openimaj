package org.openimaj.rdf.owl2java;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.WordUtils;
import org.openrdf.model.URI;
import org.openrdf.query.BindingSet;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.sail.memory.model.MemLiteral;

/**
 *
 *
 *	@author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 *	@author David Dupplaw (dpd@ecs.soton.ac.uk)
 *  @created 29 Oct 2012
 *	@version $Author$, $Revision$, $Date$
 */
public class ClassDef
{
	/** The description of the class from the RDF comment */
	protected String comment;

	/** The URI of the class */
	protected URI uri;

	/** List of the superclasses to this class */
	protected List<URI> superclasses;

	/** List of the properties in this class */
	protected List<PropertyDef> properties;

	/**
	 * 	Outputs the Java class definition for this class def
	 *
	 *	{@inheritDoc}
	 * 	@see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		return "class " + this.uri.getLocalName() + " extends " +
				this.superclasses + " {\n" + "\t" + this.properties + "\n}\n";
	}

	/**
	 *	Loads all the class definitions from the given repository
	 *
	 *	@param conn The repository connection from where to get the classes
	 *	@return
	 *	@throws RepositoryException
	 *	@throws MalformedQueryException
	 *	@throws QueryEvaluationException
	 */
	public static Map<URI,ClassDef> loadClasses( final RepositoryConnection conn )
		throws RepositoryException, MalformedQueryException, QueryEvaluationException
	{
		final HashMap<URI,ClassDef> classes = new HashMap<URI, ClassDef>();

		// This is the types we'll look for
		// We'll look for both OWL and RDF classes
		final String[] clzTypes = {
				"<http://www.w3.org/2002/07/owl#Class>",
				"rdfs:Class"
		};

		// Loop over the namespaces
		for( final String clzType : clzTypes )
		{
			// Create a query to get the classes
			final String query = "SELECT Class, Comment "
					+ "FROM {Class} rdf:type {" + clzType + "}; "
					+ " [ rdfs:comment {Comment} ]";

			// Prepare the query...
			final TupleQuery preparedQuery = conn.prepareTupleQuery(
					QueryLanguage.SERQL, query );

			// Run the query...
			final TupleQueryResult res = preparedQuery.evaluate();

			// Loop over the results
			while( res.hasNext() )
			{
				final BindingSet bindingSet = res.next();

				// If we have a class with a URI...
				if( bindingSet.getValue("Class") instanceof URI )
				{
					// Create a new class definition for it
					final ClassDef clz = new ClassDef();

					// Get the comment, if there is one.
					if( bindingSet.getValue("Comment") != null )
					{
						final MemLiteral lit = (MemLiteral)
								bindingSet.getValue("Comment");
						clz.comment = lit.stringValue();
					}

					clz.uri = (URI) bindingSet.getValue("Class");
					clz.superclasses = ClassDef.getSuperclasses( clz.uri, conn );
					clz.properties   = PropertyDef.loadProperties( clz.uri, conn );

					classes.put( clz.uri, clz );
				}
			}
		}
		return classes;
	}

	/**
	 *	Retrieves the superclass list for the given class URI using the given
	 *	repository
	 *
	 *	@param uri The URI of the class to find the superclasses of
	 *	@param conn The respository
	 *	@return A list of URIs of superclasses
	 *
	 *	@throws RepositoryException
	 *	@throws MalformedQueryException
	 *	@throws QueryEvaluationException
	 */
	private static List<URI> getSuperclasses( final URI uri, final RepositoryConnection conn )
				throws RepositoryException, MalformedQueryException, QueryEvaluationException
	{
		// SPARQL query to get the superclasses
		final String query = "SELECT ?superclass WHERE { "+
				"<" + uri.stringValue() + "> "+
				"<http://www.w3.org/2000/01/rdf-schema#subClassOf> "+
				"?superclass. }";

		final TupleQuery preparedQuery = conn.prepareTupleQuery(QueryLanguage.SPARQL, query);
		final TupleQueryResult res = preparedQuery.evaluate();

		final List<URI> superclasses = new ArrayList<URI>();
		while (res.hasNext()) {
			final BindingSet bindingSet = res.next();

			if (bindingSet.getValue("superclass") instanceof URI) {
				superclasses.add((URI) bindingSet.getValue("superclass"));
			}
		}

		return superclasses;
	}

	/**
	 * 	Generates a Java file in the target directory
	 *
	 *	@param targetDir The target directory
	 *	@param pkgs A map of package mappings to class URIs
	 * 	@param classes A map of class URIs to ClassDefs
	 * 	@param flattenClassStructure Whether to flatten the class structure
	 * 	@param generateAnnotations Whether to generate OpenIMAJ RDF annotations
	 * 		for the properties
	 * @param separateImplementations
	 * @throws FileNotFoundException
	 */
	public void generateClass( final File targetDir, final Map<URI, String> pkgs,
			final Map<URI, ClassDef> classes, final boolean flattenClassStructure,
			final boolean generateAnnotations, final boolean separateImplementations ) throws FileNotFoundException
	{
		// We don't need to generate an implementation file if there are no
		// properties to get/set
		if( this.properties.size() == 0 )
			return;

		// Generate the filename for the output file
		final File path = new File( targetDir.getAbsolutePath() + File.separator +
				pkgs.get(this.uri).replace( ".", File.separator ) +
				(separateImplementations?File.separator+"impl":"") );
		path.mkdirs();
		final PrintStream ps = new PrintStream( new File( path.getAbsolutePath()
				+ File.separator + this.uri.getLocalName() + "Impl.java") );

		// Output the package definition
		ps.println("package " + pkgs.get(this.uri) +
				(separateImplementations?".impl":"")+";");
		ps.println();

		// Output the imports
		if( separateImplementations )
			ps.println( "import "+pkgs.get(this.uri)+".*;" );
		if( generateAnnotations )
			ps.println( "import org.openimaj.rdf.serialize.Predicate;\n");
		this.printImports( ps, pkgs );
		ps.println();

		// Output the comment at the top of the class
		this.printClassComment(ps);

		// Output the class
		ps.print("public class " + this.uri.getLocalName() + "Impl ");
		if (this.superclasses.size() > 0)
		{
			// It will implement the interface that defines it
			ps.print( "implements "+this.uri.getLocalName() );

			// ...and any of the super class interfaces
			for( final URI superclass : this.superclasses )
			{
				ps.print(", ");
				ps.print( superclass.getLocalName() );
			}
		}
		ps.println("\n{");

		// Output the definition of the class
		this.printClassPropertyDefinitions( ps, classes,
				flattenClassStructure, generateAnnotations );

		ps.println("}\n");
	}

	/**
	 * 	Generates a Java interface file in the target directory
	 *
	 *	@param targetDir The target directory
	 *	@param pkgs A list of package mappings to class URIs
	 * 	@param classes
	 * 	@param separateImplementations
	 * 	@throws FileNotFoundException
	 */
	public void generateInterface( final File targetDir, final Map<URI, String> pkgs,
			final Map<URI, ClassDef> classes ) throws FileNotFoundException
	{
		final File path = new File( targetDir.getAbsolutePath() + File.separator +
				pkgs.get(this.uri).replace( ".", File.separator ) );
		path.mkdirs();
		final PrintStream ps = new PrintStream( new File( path.getAbsolutePath()
				+ File.separator + this.uri.getLocalName() + ".java") );

		ps.println("package " + pkgs.get(this.uri) + ";");
		ps.println();

		this.printClassComment(ps);

		ps.print("public interface " + this.uri.getLocalName() + " ");
		ps.println("\n{");
		this.printInterfacePropertyDefinitions( ps );
		ps.println("}\n");
	}

	/**
	 * 	Prints the comment at the top of the file for this class.
	 *
	 *	@param ps The stream to print the comment to.
	 */
	private void printClassComment( final PrintStream ps )
	{
		ps.println("/**");
		if (this.comment == null) {
			ps.println(" * " + this.uri);
		} else {
			final String cmt = WordUtils.wrap(" * " + this.comment.replaceAll("\\r?\\n", " "), 80, "\n * ", false);
			ps.println(" " + cmt);
		}
		ps.println(" */");
	}

	/**
	 * 	Outputs the list of imports necessary for this class.
	 *
	 *	@param ps The stream to print the imports to
	 *	@param pkgs The list of package mappings for all the known classes
	 */
	private void printImports( final PrintStream ps, final Map<URI, String> pkgs )
	{
		final Set<String> imports = new HashSet<String>();

		for( final URI sc : this.superclasses )
			imports.add( pkgs.get(sc) );

		imports.remove( pkgs.get(this.uri) );

		final String[] sortedImports = imports.toArray(new String[imports.size()]);
		Arrays.sort(sortedImports);

		for (final String imp : sortedImports) {
			ps.println("import " + imp + ".*;");
		}
	}

	/**
	 *	Outputs all the properties into the class definition.
	 *
	 *	@param ps The stream to print to.
	 */
	private void printInterfacePropertyDefinitions( final PrintStream ps )
	{
		for( final PropertyDef p : this.properties)
			ps.println( p.toSettersAndGetters( "\t", false, null ) );
	}

	/**
	 *	Outputs all the properties into the class definition.
	 *
	 *	@param ps The stream to print to.
	 * 	@param classes A map of class URIs to ClassDefs
	 * 	@param flattenClassStructure Whether to combine all the properties from
	 * 		all the superclasses into this class (TRUE), or whether to use instance
	 * 		pointers to classes of that type (FALSE)
	 * @param generateAnnotations
	 */
	private void printClassPropertyDefinitions( final PrintStream ps,
			final Map<URI, ClassDef> classes, final boolean flattenClassStructure,
			final boolean generateAnnotations )
	{
		if( flattenClassStructure )
		{
			// Work out all the properties to output
			final List<PropertyDef> pd = new ArrayList<PropertyDef>();
			pd.addAll( this.properties );
			for( final URI superclass : this.superclasses )
				pd.addAll( classes.get( superclass ).properties );

			// Output all the property definitions for this class.
			for( final PropertyDef p : pd )
				ps.println( p.toJavaDefinition("\t",generateAnnotations) );
			ps.println();
			// Output all the getters and setters for this class.
			for( final PropertyDef p : pd )
				ps.println( p.toSettersAndGetters( "\t", true, null ) );
		}
		else
		{
			// Output all the property definitions for this class.
			for( final PropertyDef p : this.properties )
				ps.println( p.toJavaDefinition("\t",generateAnnotations) );
			ps.println();

			// Now we need to output the links to other objects from which
			// this class inherits. While we do that, we'll also remember which
			// properties we need to delegate to the other objects.
			final HashMap<String,List<PropertyDef>> pd = new HashMap<String, List<PropertyDef>>();
			for( final URI superclass : this.superclasses )
			{
				final String instanceName =
						superclass.getLocalName().substring(0,1).toLowerCase()+
						superclass.getLocalName().substring(1);

				ps.println( "\t/** "+superclass.getLocalName()+" instance */" );
				ps.println( "\tprivate "+superclass.getLocalName()+" "+instanceName+";\n" );

				pd.put( instanceName, classes.get(superclass).properties );
			}

			// Output the property getters and setters for this class
			for( final PropertyDef p : this.properties )
				ps.println( p.toSettersAndGetters( "\t", true, null ) );

			// Now output the delegated getters and setters for this class
			for( final String instanceName : pd.keySet() )
				for( final PropertyDef p : pd.get(instanceName) )
					ps.println( p.toSettersAndGetters( "\t", true, instanceName ) );
		}
	}
}
