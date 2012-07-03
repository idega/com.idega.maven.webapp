package com.idega.maven.webapp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import com.idega.util.CoreConstants;
import com.idega.util.FacesConfigMerger;
import com.idega.util.FileUtil;
import com.idega.util.StringHandler;
import com.idega.util.WebXmlMerger;

/**
 * Build the necessary things up in an idegaweb webapp
 *
 * @author <a href="evenisse@apache.org">Emmanuel Venisse</a>
 * @version $Id: IdegaWebWarMojo.java,v 1.6 2007/04/22 15:19:35 tryggvil Exp $
 * @goal war
 * @phase package
 * @requiresDependencyResolution runtime
 */
public class IdegaWebWarMojo extends WarMojo {

	private static final String WEB_INF = "WEB-INF";
	private boolean extractBundles=false;

	public IdegaWebWarMojo() {}

	public void execute() throws MojoExecutionException{
    	createWebXml();

    	createFacesConfig();

    	super.execute();

		exctactResourcesFromJars();

		compileDependencyList();

    	mergeCustomizedFacesConfigs();

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
		createWebXml(webXml);
	}

	private void createFacesConfig() {
		File facesCfg = getFacesConfigFile();
		createFacesConfig(facesCfg);
	}

	private void createWebXml(File webXml) {
		if(!webXml.exists()){
			try {
				webXml.createNewFile();

				StringBuffer buf = new StringBuffer();
				buf.append("<web-app xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://java.sun.com/xml/ns/javaee\" xmlns:web=\"http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\" xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\" id=\"Idega_ePlatform\" version=\"2.5\">\n");
				buf.append("<display-name>Idega Web application: ePlatform</display-name>\n");

				buf.append("\n<!-- MODULE:BEGIN com.idega.springframework 0.0 -->\n");
				buf.append("\n<!-- MODULE:END com.idega.springframework 0.0 -->\n");

				buf.append("\n<!-- MODULE:BEGIN org.apache.myfaces 0.0 -->\n");
				buf.append("\n<!-- MODULE:END org.apache.myfaces 0.0 -->\n");

				buf.append("\n<!-- MODULE:BEGIN com.idega.core 0.0 -->\n");
				buf.append("\n<!-- MODULE:END com.idega.core 0.0 -->\n");

				buf.append("\n<!-- MODULE:BEGIN com.idega.faces 0.0 -->\n");
				buf.append("\n<!-- MODULE:END com.idega.faces 0.0 -->\n");

				buf.append("\n<!-- MODULE:BEGIN org.apache.axis 0.0 -->\n");
				buf.append("\n<!-- MODULE:END org.apache.axis 0.0 -->\n");

				buf.append("\n</web-app>\n");


				PrintWriter writer = new PrintWriter(webXml, "UTF-8");
				writer.write(buf.toString());
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void createFacesConfig(File facesCfg) {
		if(!facesCfg.exists()){
			try {
				facesCfg.createNewFile();

				String xmlHeader = "<?xml version='1.0' encoding='UTF-8'?>\n\n";

				StringBuffer buf = new StringBuffer(xmlHeader);
				buf.append("<faces-config xmlns=\"http://java.sun.com/xml/ns/javaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-facesconfig_1_2.xsd\" version=\"1.2\">")
				.append("<!-- empty -->")
				.append("\n</faces-config>\n");

				PrintWriter writer = new PrintWriter(facesCfg, "UTF-8");
				writer.write(buf.toString());
				writer.close();
			} catch (IOException e) {
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
					if (extractResourceFromJar(name)) {
						File file = null;
						if (name.startsWith("properties") || name.startsWith("jsp") || name.startsWith("WEB-INF")) {
							file = new File(getAndCreatePrivateBundleDir(fJarFile), name);
						} else if(name.startsWith("resources")) {
							file = new File(getAndCreatePublicBundleDir(fJarFile),name);
						}

						createAndCopyContent(entry, file, jarFile);
					}

					if (name.startsWith("WEB-INF/services/") || name.startsWith("WEB-INF/modules/") || name.equals("WEB-INF/axis2.xml")) {
						File folderToCopyToWebApp = new File(getWebInfDirectory(), StringHandler.replace(name, "WEB-INF/", CoreConstants.EMPTY));
						createAndCopyContent(entry, folderToCopyToWebApp, jarFile);
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	private void createAndCopyContent(JarEntry entry, File file, JarFile jarFile) throws IOException {
		if (entry.isDirectory())
			file.mkdirs();
		else {
			file.createNewFile();
			InputStream inStream = jarFile.getInputStream(entry);
			FileOutputStream outStream = new FileOutputStream(file);
			int bufferlen = 1000;
			byte[] buf = new byte[bufferlen];
			int noRead = inStream.read(buf);
			while (noRead != -1) {
				outStream.write(buf);
				noRead = inStream.read(buf);
			}
			outStream.close();
			inStream.close();
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
			if(name.equals("WEB-INF/")||name.equals("WEB-INF/web.xml") || name.equals("WEB-INF/customized-faces-config.xml")){
				return true;
			}
		}
		return false;
	}

    private void compileDependencyList() {
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
   		WebXmlMerger merger = new WebXmlMerger();
   		merger.setBundlesFolder(getAndCreatePrivateBundlesDir());
   		merger.setOutputFile(getWebXmlFile());
		merger.process();
	}

	private void mergeCustomizedFacesConfigs() {
		FacesConfigMerger merger = new FacesConfigMerger();
		merger.setBundleFilePath("/WEB-INF/customized-faces-config.xml");
    	merger.setBundlesFolder(getAndCreatePrivateBundlesDir());
    	merger.setOutputFile(getFacesConfigFile());
		merger.process();
	}

	private File getWebInfDirectory() {
		File webInf = new File(getWebappDirectory(), WEB_INF);
		if (!webInf.exists()) {
			webInf.mkdirs();
		}
		return webInf;
	}


	private File getWebXmlFile() {
		File file = new File(getWebInfDirectory(), "web.xml");
		return file;
	}

	private File getFacesConfigFile() {
		File file = new File(getWebInfDirectory(), "faces-config.xml");
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
		File bundlesDir = new File(getAndCreatePrivateBundlesDir(), bundleFolderName);
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


	public static void main(String[] args) throws Exception{
		IdegaWebWarMojo mojo = new IdegaWebWarMojo();
		File webXML = new File("/tmp/web.xml");
		mojo.createWebXml(webXML);
	}
}
