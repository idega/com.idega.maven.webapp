package com.idega.maven.webapp;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import com.idega.util.FileUtil;
import com.idega.util.WebXmlMerger;

/**
 * Build the necessary things up in an idegaweb webapp
 *
 * @author <a href="evenisse@apache.org">Emmanuel Venisse</a>
 * @version $Id: IdegaWebWarMojo.java,v 1.2 2006/06/08 22:49:20 tryggvil Exp $
 * @goal war
 * @phase package
 * @requiresDependencyResolution runtime
 */
public class IdegaWebWarMojo extends WarMojo {


	private static final String WEB_INF = "WEB-INF";
	private boolean extractBundles=false;

	public IdegaWebWarMojo() {
		// TODO Auto-generated constructor stub
	}
	
    public void execute() throws MojoExecutionException{
    	
    		createWebXml();
    	
			super.execute();
		
			exctactResourcesFromJars();
    		
    		compileDependencyList();
    		
    		mergeWebInf();
    		
    		cleanup();
    		
    }
	
	private void cleanup() {
		if(!isExtractBundles()){
			//delete the idegaweb bundle dirs:
			File bundlesDir = getAndCreatePrivateBundlesDir();
			FileUtil.deleteFileAndChildren(bundlesDir);
		}
	}

	private void createWebXml() {
		File webXml = getWebXmlFile();
		if(!webXml.exists()){
			try {
				webXml.createNewFile();
				
				StringBuffer buf = new StringBuffer();
				buf.append("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n<!DOCTYPE web-app\n\tPUBLIC \"-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN\"\n\t\"http://java.sun.com/dtd/web-app_2_3.dtd\">\n");
				buf.append("\n<web-app>\n");
				
				buf.append("\n<!-- MODULE:BEGIN org.apache.myfaces 0.0 -->\n");
				buf.append("\n<!-- MODULE:END org.apache.myfaces 0.0 -->\n");
				
				buf.append("\n<!-- MODULE:BEGIN com.idega.core 0.0 -->\n");
				buf.append("\n<!-- MODULE:END com.idega.core 0.0 -->\n");
				
				buf.append("\n<!-- MODULE:BEGIN com.idega.faces 0.0 -->\n");
				buf.append("\n<!-- MODULE:END com.idega.faces 0.0 -->\n");
				
				buf.append("\n<!-- MODULE:BEGIN org.apache.axis 0.0 -->\n");
				buf.append("\n<!-- MODULE:END org.apache.axis 0.0 -->\n");
				
				buf.append("\n</web-app>\n");
				
		        DataOutputStream output = new DataOutputStream(new FileOutputStream(webXml));
		        output.writeUTF(buf.toString());
		        output.flush();
		        output.close();
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
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
					//if(name.startsWith("properties")||name.startsWith("jsp")||name.startsWith("WEB-INF")||name.startsWith("resources")){
					if(extractResourceFromJar(name)){
						File file = null;
						if(name.startsWith("properties")||name.startsWith("jsp")||name.startsWith("WEB-INF")){
						//if(name.startsWith("WEB-INF")){
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
	
	protected boolean extractResourceFromJar(String name){
		if(isExtractBundles()){
			//if extractBundles is true then extract all these directories
			if(name.startsWith("properties")||name.startsWith("jsp")||name.startsWith("WEB-INF")||name.startsWith("resources")){
				return true;
			}
		}
		else{
			if(name.equals("WEB-INF/")||name.equals("WEB-INF/web.xml")){
				return true;
			}
		}
		return false;
	}
	
    private void compileDependencyList() {
    	
        //File libDirectory = new File( webappDirectory, WEB_INF + "/lib" );
        //File tldDirectory = new File( webappDirectory, WEB_INF + "/tld" );
        //File webappClassesDirectory = new File( webappDirectory, WEB_INF + "/classes" );

    	MavenProject project = getProject();
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
	
	private File getWebInfDirectory() {
		File webInf = new File( getWebappDirectory(), WEB_INF  );
		if(!webInf.exists()){
			webInf.mkdirs();
		}
		return webInf;
	}
	

	private File getWebXmlFile() {
		File file = new File(getWebInfDirectory(),"web.xml");
		/*if(!file.exists()){
			try {
				file.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}*/
		return file;
	}
	
	private File getLibDirectory() {
		File libDirectory = new File(getWebInfDirectory(), "lib" );
		return libDirectory;
	}
	private File getAndCreatePrivateIdegawebDir(){
		File idegawebDir = new File( getWebInfDirectory(), "idegaweb");
		if(!idegawebDir.exists()){
			idegawebDir.mkdir();
		}
		return idegawebDir;
	}
	
	private File getAndCreatePrivateBundlesDir(){
		File bundlesDir = new File( getAndCreatePrivateIdegawebDir(), "bundles");
		if(!bundlesDir.exists()){
			bundlesDir.mkdir();
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
	
	private File getAndCreatePublicBundlesDir(){
		File bundlesDir = new File( getAndCreatePublicIdegawebDir(), "bundles");
		if(!bundlesDir.exists()){
			bundlesDir.mkdir();
		}
		return bundlesDir;
	}
	
	private File getAndCreatePublicIdegawebDir(){
		File idegawebDir = new File( getWebappDirectory(), "idegaweb");
		if(!idegawebDir.exists()){
			idegawebDir.mkdir();
		}
		return idegawebDir;
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

	public boolean isExtractBundles() {
		return extractBundles;
	}

	public void setExtractBundles(boolean extractBundles) {
		this.extractBundles = extractBundles;
	}

}
