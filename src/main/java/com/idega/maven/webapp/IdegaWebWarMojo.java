package com.idega.maven.webapp;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.archiver.war.WarArchiver;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.FileUtils;
import com.idega.util.WebXmlMerger;

/**
 * Build the necessary things up in an idegaweb webapp
 *
 * @author <a href="evenisse@apache.org">Emmanuel Venisse</a>
 * @version $Id: IdegaWebWarMojo.java,v 1.1 2006/06/08 17:33:30 tryggvil Exp $
 * @goal idegawebapp
 * @phase package
 * @requiresDependencyResolution runtime
 */
public class IdegaWebWarMojo
    extends AbstractMojo
{
    public static final String WEB_INF = "WEB-INF";

    /**
     * The mode to use. Possible values are: war (default), inplace
     * and exploded.
     *
     * @parameter expression="${mode}"
     */
    private String mode = "war";

    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The directory containing generated classes.
     *
     * @parameter expression="${project.build.outputDirectory}"
     * @required
     * @readonly
     */
    private File classesDirectory;

    /**
     * The directory for the generated WAR.
     *
     * @parameter expression="${project.build.directory}"
     * @required
     */
    private String outputDirectory;

    /**
     * The directory where the webapp is built.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}"
     * @required
     */
    private File webappDirectory;

    /**
     * Single directory for extra files to include in the WAR.
     *
     * @parameter expression="${basedir}/src/main/webapp"
     * @required
     */
    private File warSourceDirectory;

    /**
     * The comma separated list of tokens to include in the WAR.
     * Default is '**'.
     *
     * @parameter alias="includes"
     */
    private String warSourceIncludes = "**";

    /**
     * The comma separated list of tokens to exclude from the WAR.
     *
     * @parameter alias="excludes"
     */
    private String warSourceExcludes;

    /**
     * The path to the web.xml file to use.
     *
     * @parameter expression="${maven.war.webxml}"
     */
    private String webXml;

    /**
     * The name of the generated war.
     *
     * @parameter expression="${project.build.finalName}"
     * @required
     * @deprecated "Please use the finalName element of build instead"
     */
    private String warName;

    /**
     * The maven archive configuration to use.
     *
     * @parameter
     */
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    private static final String[] EMPTY_STRING_ARRAY = {};


    /**
     * Copies webapp resources from the specified directory.
     * <p/>
     * Note that the <tt>webXml</tt> parameter could be null and may
     * specify a file which is not named <tt>web.xml<tt>. If the file
     * exists, it will be copied to the <tt>META-INF</tt> directory and
     * renamed accordingly.
     *
     * @param sourceDirectory the source directory
     * @param webappDirectory the target directory
     * @param webXml          the path to a custom web.xml
     * @throws IOException if an error occured while copying resources
     */
    public void copyResources( File sourceDirectory, File webappDirectory, String webXml )
        throws IOException
    {
        if ( !sourceDirectory.equals( webappDirectory ) )
        {
            getLog().info( "Copy webapp resources to " + webappDirectory.getAbsolutePath() );

            if ( warSourceDirectory.exists() )
            {
                String[] fileNames = getWarFiles( sourceDirectory );
                for ( int i = 0; i < fileNames.length; i++ )
                {
                    FileUtils.copyFile( new File( sourceDirectory, fileNames[i] ),
                                        new File( webappDirectory, fileNames[i] ) );
                }
            }

            if ( webXml != null && !"".equals( webXml ) )
            {
                //rename to web.xml
                File webinfDir = new File( webappDirectory, WEB_INF );
                FileUtils.copyFile( new File( webXml ), new File( webinfDir, "/web.xml" ) );
            }
        }
    }

    /**
     * Builds the webapp for the specified project.
     * <p/>
     * Classes, libraries and tld files are copied to
     * the <tt>webappDirectory</tt> during this phase.
     *
     * @param project the maven project
     * @throws IOException if an error occured while building the webapp
     */
    public void buildWebapp( MavenProject project )
        throws IOException
    {
        getLog().info( "Assembling webapp " + project.getArtifactId() + " in " + webappDirectory );

        File libDirectory = new File( webappDirectory, WEB_INF + "/lib" );

        File tldDirectory = new File( webappDirectory, WEB_INF + "/tld" );

        File webappClassesDirectory = new File( webappDirectory, WEB_INF + "/classes" );

        if ( classesDirectory.exists() )
        {
            FileUtils.copyDirectoryStructure( classesDirectory, webappClassesDirectory );
        }

        Set artifacts = project.getArtifacts();

        for ( Iterator iter = artifacts.iterator(); iter.hasNext(); )
        {
            Artifact artifact = (Artifact) iter.next();

            // TODO: utilise appropriate methods from project builder
            // TODO: scope handler
            // Include runtime and compile time libraries
            if ( !Artifact.SCOPE_PROVIDED.equals( artifact.getScope() ) &&
                !Artifact.SCOPE_TEST.equals( artifact.getScope() ) )
            {
                String type = artifact.getType();
                if ( "tld".equals( type ) )
                {
                    FileUtils.copyFileToDirectory( artifact.getFile(), tldDirectory );
                }
                else if ( "jar".equals( type ) || "ejb".equals( type ) || "ejb-client".equals( type ) )
                {
                    FileUtils.copyFileToDirectory( artifact.getFile(), libDirectory );
                }
                else
                {
                    getLog().debug( "Skipping artifact of type " + type + " for WEB-INF/lib" );
                }
            }

        }
    }

    /**
     * Generates and exploded webapp.
     * <p/>
     * This mode is invoked when the <tt>mode</tt> parameter has a value
     * of <tt>exploded</tt>.
     *
     * @throws IOException if an error occured while building the webapp
     */
    public void generateExplodedWebapp()
        throws IOException
    {
        webappDirectory.mkdirs();

        File webinfDir = new File( webappDirectory, WEB_INF );

        webinfDir.mkdirs();

        copyResources( warSourceDirectory, webappDirectory, webXml );

        buildWebapp( project );
    }

    /**
     * Generates the webapp in the source directory.
     * <p/>
     * This mode is invoked when the <tt>mode</tt> parameter has a value
     * of <tt>inplace</tt>.
     *
     * @throws IOException if an error occured while building the webapp
     */
    public void generateInPlaceWebapp()
        throws IOException
    {
        webappDirectory = warSourceDirectory;

        generateExplodedWebapp();
    }

    /**
     * Executes the WarMojo on the current project.
     *
     * @throws MojoExecutionException if an error occured while building the webapp
     */
    public void executeOld()
        throws MojoExecutionException
    {
        File warFile = new File( outputDirectory, warName + ".war" );

        try
        {
            performPackaging( warFile );
        }
        catch ( Exception e )
        {
            // TODO: improve error handling
            throw new MojoExecutionException( "Error assembling WAR", e );
        }
    }
    
    
    public void execute()
    throws MojoExecutionException
    {
    		exctactResourcesFromJars();
    		
    		compileDependencyList();
    		
    		mergeWebInf();
    		
    }

    private void compileDependencyList() {
    	
        //File libDirectory = new File( webappDirectory, WEB_INF + "/lib" );
        //File tldDirectory = new File( webappDirectory, WEB_INF + "/tld" );
        //File webappClassesDirectory = new File( webappDirectory, WEB_INF + "/classes" );


        if(project!=null){
        	
        Set artifacts = project.getArtifacts();
        	
        for ( Iterator iter = artifacts.iterator(); iter.hasNext(); )
        {
            Artifact artifact = (Artifact) iter.next();

            // TODO: utilise appropriate methods from project builder
            // TODO: scope handler
            // Include runtime and compile time libraries
            if ( !Artifact.SCOPE_PROVIDED.equals( artifact.getScope() ) &&
                !Artifact.SCOPE_TEST.equals( artifact.getScope() ) )
            {
                String type = artifact.getType();
                if ( "tld".equals( type ) )
                {
                    //FileUtils.copyFileToDirectory( artifact.getFile(), tldDirectory );
                		getLog().debug( "Getting artifact "+artifact.getArtifactId()+" of type " + type + " for WEB-INF/lib" );
                }
                else if ( "jar".equals( type ) || "ejb".equals( type ) || "ejb-client".equals( type ) )
                {
                    //FileUtils.copyFileToDirectory( artifact.getFile(), libDirectory );
                		getLog().debug( "Getting artifact "+artifact.getArtifactId()+" of type " + type + " for WEB-INF/lib" );
                }
                else
                {
                    getLog().debug( "Skipping artifact of type " + type + " for WEB-INF/lib" );
                }
            }

        }
        }
        else{
        		getLog().debug("compileDependencyList() project is null");
        }
	}

	private void mergeWebInf() {
		// TODO Auto-generated method stub
    		WebXmlMerger merger = new WebXmlMerger();
    		merger.setBundlesFolder(getAndCreatePrivateBundlesDir());
    		merger.setOutputFile(getWebXmlFile());
		merger.process();
	}

	private void exctactResourcesFromJars() {
		File libDir = getLibDirectory();
		File[] jarfiles = libDir.listFiles();
		for (int i = 0; i < jarfiles.length; i++) {
			File fJarFile = jarfiles[i];
			try {
				JarFile jarFile = new JarFile(fJarFile);
				Enumeration entries = jarFile.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = (JarEntry) entries.nextElement();
					String name = entry.getName();
					if(name.startsWith("properties")||name.startsWith("jsp")||name.startsWith("WEB-INF")||name.startsWith("resources")){
						File file = null;
						if(name.startsWith("properties")||name.startsWith("jsp")||name.startsWith("WEB-INF")){
							file = new File(getAndCreatePrivateBundleDir(fJarFile),name);
						}
						else if(name.startsWith("resources")){
							file = new File(getAndCreatePublicBundleDir(fJarFile),name);
						}
						if(entry.isDirectory()){
							file.mkdirs();
						}
						else{
							file.createNewFile();
							InputStream inStream = jarFile.getInputStream(entry);
							FileOutputStream outStream = new FileOutputStream(file);
							int bufferlen = 1000;
							byte[] buf = new byte[bufferlen];
							int noRead = inStream.read(buf);
							while(noRead!=-1){
								outStream.write(buf);
								noRead = inStream.read(buf);
							}
							outStream.close();
							inStream.close();
							
						}
					}
				}
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		
	}
    

	private File getWebInfDirectory() {
		File libDirectory = new File( webappDirectory, WEB_INF  );
		return libDirectory;
	}
	

	private File getWebXmlFile() {
		File file = new File(getWebInfDirectory(),"web.xml");
		return file;
	}
	
	private File getLibDirectory() {
		File libDirectory = new File(getWebInfDirectory(), "lib" );
		return libDirectory;
	}
	
	private File getAndCreatePublicIdegawebDir(){
		File idegawebDir = new File( webappDirectory, "idegaweb");
		if(!idegawebDir.exists()){
			idegawebDir.mkdir();
		}
		return idegawebDir;
	}
	
	private File getAndCreatePrivateIdegawebDir(){
		File idegawebDir = new File( getWebInfDirectory(), "idegaweb");
		if(!idegawebDir.exists()){
			idegawebDir.mkdir();
		}
		return idegawebDir;
	}
	
	private File getAndCreatePublicBundlesDir(){
		File bundlesDir = new File( getAndCreatePublicIdegawebDir(), "bundles");
		if(!bundlesDir.exists()){
			bundlesDir.mkdir();
		}
		return bundlesDir;
	}
	
	private File getAndCreatePrivateBundlesDir(){
		File bundlesDir = new File( getAndCreatePrivateIdegawebDir(), "bundles");
		if(!bundlesDir.exists()){
			bundlesDir.mkdir();
		}
		return bundlesDir;
	}
	
	private File getAndCreatePublicBundleDir(File bundleJar){
		String bundleFolderName = getBundleFolderName(bundleJar);
		File bundlesDir = new File( getAndCreatePublicBundlesDir(), bundleFolderName);
		if(!bundlesDir.exists()){
			bundlesDir.mkdir();
			getLog().info("Extracting to bundle folder: "+bundlesDir.toURI());
		}
		return bundlesDir;
	}
	
	private File getAndCreatePrivateBundleDir(File bundleJar){
		String bundleFolderName = getBundleFolderName(bundleJar);
		File bundlesDir = new File( getAndCreatePrivateBundlesDir(), bundleFolderName);
		if(!bundlesDir.exists()){
			bundlesDir.mkdir();
			getLog().info("Extracting to bundle folder: "+bundlesDir.toURI());
		}
		return bundlesDir;
	}
	
	private String getBundleFolderName(File bundleJarFile){
		String jarName = bundleJarFile.getName();
		String bundleIdentifier = jarName.substring(0,jarName.indexOf("-"));
		String bundleFolderName = bundleIdentifier+".bundle";
		return bundleFolderName;
	}


	/**
     * Generates the webapp according to the <tt>mode</tt> attribute.
     *
     * @param warFile the target war file
     * @throws IOException
     * @throws ArchiverException
     * @throws ManifestException
     * @throws DependencyResolutionRequiredException
     */
    private void performPackaging( File warFile )
        throws IOException, ArchiverException, ManifestException, DependencyResolutionRequiredException
    {
        if ( "inplace".equals( mode ) )
        {
            generateInPlaceWebapp();
        }
        else
        {
            generateExplodedWebapp();

            // TODO: make a separate 'exploded' Mojo. For now,
            // disable not making an artifact so the install phase
            // doesn't fail.
            if ( !"exploded".equals( mode ) )
            {
                //generate war file
                getLog().info( "Generating war " + warFile.getAbsolutePath() );

                MavenArchiver archiver = new MavenArchiver();

                WarArchiver warArchiver = new WarArchiver();

                archiver.setArchiver( warArchiver );

                archiver.setOutputFile( warFile );

                warArchiver.addDirectory( webappDirectory, getIncludes(), getExcludes() );

                warArchiver.setWebxml( new File( webappDirectory, "WEB-INF/web.xml" ) );

                // create archive
                archiver.createArchive( project, archive );

                project.getArtifact().setFile( warFile );
            }
            else
            {
                getLog().warn( "Exploded mode will be deprecated in the next release. It is not compatible with install/deploy goals" );
            }
        }
    }

    /**
     * Returns the default exclude tokens.
     *
     * @return a list of <code>String</code> tokens
     * @todo copied again. Next person to touch it puts it in the right place! :)
     */
    public List getDefaultExcludes()
    {
        List defaultExcludes = new ArrayList();
        defaultExcludes.add( "**/*~" );
        defaultExcludes.add( "**/#*#" );
        defaultExcludes.add( "**/.#*" );
        defaultExcludes.add( "**/%*%" );
        defaultExcludes.add( "**/._*" );

        // CVS
        defaultExcludes.add( "**/CVS" );
        defaultExcludes.add( "**/CVS/**" );
        defaultExcludes.add( "**/.cvsignore" );

        // SCCS
        defaultExcludes.add( "**/SCCS" );
        defaultExcludes.add( "**/SCCS/**" );

        // Visual SourceSafe
        defaultExcludes.add( "**/vssver.scc" );

        // Subversion
        defaultExcludes.add( "**/.svn" );
        defaultExcludes.add( "**/.svn/**" );

        // Mac
        defaultExcludes.add( "**/.DS_Store" );

        // Windows Thumbs
        defaultExcludes.add( "**/Thumbs.db" );

        return defaultExcludes;
    }

    /**
     * Returns a list of filenames that should be copied
     * over to the destination directory.
     *
     * @param sourceDir the directory to be scanned
     * @return the array of filenames, relative to the sourceDir
     */
    private String[] getWarFiles( File sourceDir )
    {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir( sourceDir );
        scanner.setExcludes( getExcludes() );
        scanner.addDefaultExcludes();

        scanner.setIncludes( getIncludes() );

        scanner.scan();

        return scanner.getIncludedFiles();
    }

    /**
     * Returns a string array of the excludes to be used
     * when assembling/copying the war.
     *
     * @return an array of tokens to exclude
     */
    private String[] getExcludes()
    {
        List excludeList = getDefaultExcludes();
        if ( warSourceExcludes != null && !"".equals( warSourceExcludes ) )
        {
            excludeList.add( warSourceExcludes );
        }

        // if webXML is specified, omit the one in the source directory
        if ( webXml != null && !"".equals( webXml ) )
        {
            excludeList.add( "**/" + WEB_INF + "/web.xml" );
        }

        return (String[]) excludeList.toArray( EMPTY_STRING_ARRAY );
    }

    /**
     * Returns a string array of the includes to be used
     * when assembling/copying the war.
     *
     * @return an array of tokens to include
     */
    private String[] getIncludes()
    {
        return new String[]{warSourceIncludes};
    }
    
    
    /**
     * Test method
     * @param args
     */
    public static void main(String[] args)throws Exception{
    	
    		IdegaWebWarMojo mojo = new IdegaWebWarMojo();
    		mojo.webappDirectory=new File("/idega/eclipse/content/applications/base/target/base-3.1-SNAPSHOT");
    		mojo.execute();
    		
    }
}
