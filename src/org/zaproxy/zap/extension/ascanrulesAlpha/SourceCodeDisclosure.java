/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.extension.ascanrulesAlpha;

import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.scanner.AbstractAppParamPlugin;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Category;
import org.parosproxy.paros.network.HttpMessage;
import org.zaproxy.zap.extension.ascanrulesAlpha.AscanUtils;
import org.zaproxy.zap.model.Vulnerabilities;
import org.zaproxy.zap.model.Vulnerability;
import org.apache.commons.httpclient.URI;

/**
 * a scanner that looks for application source code disclosure using path traversal techniques
 * 
 * @author 70pointer
 *
 */
public class SourceCodeDisclosure extends AbstractAppParamPlugin {

	//TODO: replace this with an actual random value, or someone will decide to play with our heads by creating actual files with this name.
    private static final String NON_EXISTANT_FILENAME = "thishouldnotexistandhopefullyitwillnot";
        
    //the prefixes to try for source file inclusion
    private String[] LOCAL_SOURCE_FILE_TARGET_PREFIXES = {
         ""
        ,"/"
        ,"../"
        ,"webapps/"  //in the case of servlet containers like Tomcat, JBoss (etc), sometimes the working directory is the application server folder
    	};
    
    //the prefixes to try for WAR/EAR file inclusion
    private String[] LOCAL_WAR_EAR_FILE_TARGET_PREFIXES = {

        	 "/../"			//for Tomcat, if the current directory is the tomcat/webapps/appname folder, when slashes ARE NOT added by the code (far less common in practice than I would have thought, given some real world vulnerable apps.)
        	,"../"			//for Tomcat, if the current directory is the tomcat/webapps/appname folder, when slashes ARE added by the code (far less common in practice than I would have thought, given some real world vulnerable apps.)
        	
        	,"/../../"		//for Tomcat, if the current directory is the tomcat/webapps/appname/a/ folder, when slashes ARE NOT added by the code
        	,"../../"		//for Tomcat, if the current directory is the tomcat/webapps/appname/a/ folder, when slashes ARE added by the code
        	
    		,"/../../../"	//for Tomcat, if the current directory is the tomcat/webapps/appname/a/b/ folder, when slashes ARE NOT added by the code
        	,"../../../"	//for Tomcat, if the current directory is the tomcat/webapps/appname/a/b/ folder, when slashes ARE added by the code

        	,"/../../../../"	//for Tomcat, if the current directory is the tomcat/webapps/appname/a/b/c/ folder, when slashes ARE NOT added by the code
        	,"../../../../"		//for Tomcat, if the current directory is the tomcat/webapps/appname/a/b/c/ folder, when slashes ARE added by the code
        	
        	,"/webapps/"	//for Tomcat, if the current directory is the tomcat folder, when slashes ARE NOT added by the code
        	,"webapps/"		//for Tomcat, if the current directory is the tomcat folder, when slashes ARE added by the code

        	,"/"			//for Tomcat, if the current directory is the tomcat/webapps folder, when slashes ARE NOT added by the code
        	,""				//for Tomcat, if the current directory is the tomcat/webapps folder, when slashes ARE added by the code
    	
        	,"/../webapps/"	//for Tomcat, if the current directory is the tomcat/temp folder, when slashes ARE NOT added by the code
        	,"../webapps/"	//for Tomcat, if the current directory is the tomcat/temp folder, when slashes ARE added by the code
    	};
    
    
    /**
     * details of the vulnerability which we are attempting to find (in this case 33 = Path Traversal)
     */
    private static Vulnerability vuln = Vulnerabilities.getVulnerability("wasc_33");
    
    /**
     * the logger object
     */
    private static Logger log = Logger.getLogger(SourceCodeDisclosure.class);
    
    /**
     * Hirshberg class for longest common substring calculation.  
     * Damn you John McKenna and your dynamic programming techniques!
     */
    Hirshberg hirshberg = new Hirshberg ();
    
    /**
     * the threshold for whether 2 responses match. depends on the alert threshold set in the GUI. not final or static.
     */
    int thresholdPercentage = 0;
    
    /**
     * patterns expected in the output for common server side file extensions
     * TODO: add support for verification of other file types, once I get some real world test cases.
     */
    private static final Pattern PATTERN_JSP = Pattern.compile("<%.*%>");
    private static final Pattern PATTERN_PHP = Pattern.compile("<?php");

    /**
     * returns the plugin id
     */
    @Override
    public int getId() {
        return 40;
    }

    /**
     * returns the name of the plugin
     */
    @Override
    public String getName() {
    	//this would return "Path Traversal", given WASC 33, but we want "Source Code Disclosure"
        //if (vuln != null) {
        //    return vuln.getAlert();
        //}
        return "Source Code Disclosure"; //boo-ya!
    }

    @Override
    public String[] getDependency() {
        return null;
    }

    @Override
    public String getDescription() {
        if (vuln != null) {
            return vuln.getDescription();
        }
        return "Failed to load vulnerability description from file";
    }

    @Override
    public int getCategory() {
        return Category.SERVER;
    }

    @Override
    public String getSolution() {
        if (vuln != null) {
            return vuln.getSolution();
        }
        return "Failed to load vulnerability solution from file";
    }

    @Override
    public String getReference() {
        if (vuln != null) {
            StringBuilder sb = new StringBuilder();
            for (String ref : vuln.getReferences()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(ref);
            }
            return sb.toString();
        }
        return "Failed to load vulnerability reference from file";
    }

    @Override
    public void init() {
    	//DEBUG only
    	//log.setLevel(org.apache.log4j.Level.DEBUG);
    	
    	//register for internationalisation.  
    	//The prefix used will vary depending on whether the Class is in alpha or beta
    	//interesting that this only needs to be done in alpha, and does not need to be done for beta. 
    	//not sure why that is..
    	AscanUtils.registerI18N();
    	
    	switch (this.getAlertThreshold()) {
    	case HIGH:
    		this.thresholdPercentage = 95;
    		break;
    	case MEDIUM:
    		this.thresholdPercentage = 75;
    		break;
    	case LOW:
    		this.thresholdPercentage = 50;
    		break;
    	case OFF:
    		this.thresholdPercentage = 50;	// == LOW
    		break;
    	case DEFAULT:
    		this.thresholdPercentage = 75; // == medium
    		break;
    	}
    }

    /**
     * scans the given parameter for source code disclosure vulnerabilities, using path traversal vulnerabilities
     */
    @Override
    public void scan(HttpMessage originalmsg, String paramname, String paramvalue) {

        try {
            //DEBUG only
            //this.setAttackStrength(AttackStrength.INSANE);

            if (log.isDebugEnabled()) {
                log.debug("Attacking at Attack Strength: " + this.getAttackStrength());
                log.debug("Checking [" + getBaseMsg().getRequestHeader().getMethod() + "] ["
                        + getBaseMsg().getRequestHeader().getURI() + "], parameter [" + paramname + "], with original value ["+ paramvalue + "] for Source Code Disclosure");
            }
            
            
            //first send a query for a random parameter value
            //then try a query for the file paths and names that we are using to try to get out the source code for the current URL                        
            HttpMessage randomfileattackmsg = getNewMsg();
            setParameter(randomfileattackmsg, paramname, NON_EXISTANT_FILENAME);
            sendAndReceive(randomfileattackmsg);
            
            int originalversusrandommatchpercentage = calcMatchPercentage (originalmsg.getResponseBody().toString(), randomfileattackmsg.getResponseBody().toString());            
            if (originalversusrandommatchpercentage > this.thresholdPercentage) {
            	//the output for the "random" file does not sufficiently differ. bale out.
            	if (log.isDebugEnabled()) {
            		log.debug("The output for a non-existent filename ["+ NON_EXISTANT_FILENAME + "] does not sufficiently differ from that of the original parameter ["+ paramvalue+ "], at "+ originalversusrandommatchpercentage + "%, compared to a threshold of "+ this.thresholdPercentage + "%");
            	}
            	return;
            }
                 
            //at this point, there was a sufficient difference between the random filename and the original parameter
            //so lets try the various path names that might point at the source code for this URL
            URI uri = originalmsg.getRequestHeader().getURI();
            String pathMinusLeadingSlash = uri.getPath().substring(1);
            String pathMinusApplicationContext = uri.getPath().substring( uri.getPath().indexOf("/", 1) + 1);
            
            //in the case of wavsep, should give us "wavsep"
            //use this later to build up "wavsep.war", and "wavsep.ear", for instance :)
            String applicationContext= uri.getPath().substring( 1, uri.getPath().indexOf("/", 1)); 
            
            //all of the sourceFileNames should *not* lead with a slash.
            String [] sourceFileNames = {uri.getName(), pathMinusLeadingSlash, pathMinusApplicationContext};
            
            //and get the file extension (in uppercase), so we can switch on it (if there was an extension, that is)
            String fileExtension = null;
            if(uri.getName().contains(".")) {
            	fileExtension = uri.getName().substring(uri.getName().lastIndexOf(".") + 1);
            	fileExtension = fileExtension.toUpperCase();
            }            

            //for each of the file names in turn, try it with each of the prefixes
            for (String sourcefilename : sourceFileNames) {
            	if (log.isDebugEnabled()) {
            		log.debug("Source file is ["+ sourcefilename + "]");
            	}
                //for the url filename, try each of the prefixes in turn
                for (int h = 0; h < LOCAL_SOURCE_FILE_TARGET_PREFIXES.length; h++) {
                	
                    String prefixedUrlfilename = LOCAL_SOURCE_FILE_TARGET_PREFIXES[h] + sourcefilename;
                    if (log.isDebugEnabled()) {                    	
                    	log.debug("Trying file name ["+ prefixedUrlfilename + "]");
                    }
                    
                    HttpMessage sourceattackmsg = getNewMsg();
                    setParameter(sourceattackmsg, paramname, prefixedUrlfilename);	                    
                    //send the modified message (with the url filename), and see what we get back
                    sendAndReceive(sourceattackmsg);
                    
                    int randomversussourcefilenamematchpercentage = calcMatchPercentage (
                    				randomfileattackmsg.getResponseBody().toString(), 
                    				sourceattackmsg.getResponseBody().toString());
                    if (randomversussourcefilenamematchpercentage  > this.thresholdPercentage) {
                    	//the output for the "source" file does not sufficiently differ from the random file name. bale out.
                    	if (log.isDebugEnabled()) {
                    		log.debug("The output for the source code filename ["+ prefixedUrlfilename + "] does not sufficiently differ from that of the random parameter, at "+ randomversussourcefilenamematchpercentage  + "%, compared to a threshold of "+ this.thresholdPercentage + "%");
                    	}
                    } else {
                    	//has the response been verified as source code
                    	boolean responseIsSource = false;
                    	
                    	//TODO: validate the contents looks like what the file extension says it shoud be.
                    	//for instance, for JSP, it should contain "<%" or "%>"
                    	//				for PHP, it should contain "<?php"
                    	if ( fileExtension != null) {
                    		if (fileExtension.equals ("JSP")) {
                    			if ( PATTERN_JSP.matcher(sourceattackmsg.getResponseBody().toString()).find() ) responseIsSource = true;
                    		} else if (fileExtension.equals ("PHP")) {
                    			if ( PATTERN_PHP.matcher(sourceattackmsg.getResponseBody().toString()).find() ) responseIsSource = true;
                    		} else {
                    			if (log.isDebugEnabled()) {
                    				log.debug ("Unknown file extension "+ fileExtension + ". Accepting this file type without verifying it. Could therefore be a false positive.");
                    			}
                    			responseIsSource = true;
                    		}
                    	} else {
                			//no file extension, therefore no way to verify the source code.. so accept it as it is
                			responseIsSource= true;
                		}
                    	
                    	//if we verified it, or couldn't verify it...
                    	if (responseIsSource) {
                    		log.info("Source code disclosure!  The output for the source code filename ["+ prefixedUrlfilename + "] differs sufficiently from that of the random parameter, at "+ randomversussourcefilenamematchpercentage  + "%, compared to a threshold of "+ this.thresholdPercentage + "%");
                    		
		                    //if we get to here, is is very likely that we have source file inclusion attack. alert it.
		                    bingo(Alert.RISK_HIGH, Alert.WARNING,
		                    		Constant.messages.getString("ascanalpha.sourcecodeinclusion.name"),
		                    		Constant.messages.getString("ascanalpha.sourcecodeinclusion.desc"), 
		                    		getBaseMsg().getRequestHeader().getURI().getURI(),
		                            paramname, 
		                            prefixedUrlfilename,
		                            Constant.messages.getString("ascanalpha.sourcecodeinclusion.extrainfo", prefixedUrlfilename, NON_EXISTANT_FILENAME, randomversussourcefilenamematchpercentage, this.thresholdPercentage),
		                            Constant.messages.getString("ascanalpha.sourcecodeinclusion.soln"),
		                            Constant.messages.getString("ascanalpha.sourcecodeinclusion.evidence"),
		                            sourceattackmsg
		                            );
		                    
		                    // All done. No need to look for vulnerabilities on subsequent parameters on the same request (to reduce performance impact)
		                    return;	
	                    } else {
	                    	if (log.isDebugEnabled()) {
	                    		log.debug("Could not verify that the HTML output is source code of type "+fileExtension + ". Next!");
	                    	}
	                    }
                    }
                }            
            }
            
            //if the above fails, get the entire WAR/EAR
            //but only if in HIGH or INSANE attack strength, since this generates more work and slows Zap down badly if it actually 
            //finds and returns the application WAR file!
            
            if ( this.getAttackStrength() == AttackStrength.INSANE ||
            		this.getAttackStrength() == AttackStrength.HIGH ) {
            		
	            //all of the warearFileNames should *not* lead with a slash.
            	//TODO: should we consider uppercase / lowercase on (real) OSs such as Linux that support such a thing?
            	//Note that each of these file types can contain the Java class files, which can be disassembled into the Java source code.
            	//this in fact is one of my favourite hacking techniques.
	            String [] warearFileNames = {applicationContext + ".war", applicationContext + ".ear", applicationContext + ".rar"};
	            
	            //for each of the EAR / file names in turn, try it with each of the prefixes
	            for (String sourcefilename : warearFileNames) {
	            	if (log.isDebugEnabled()) {
	            		log.debug("WAR/EAR file is ["+ sourcefilename + "]");
	            	}
	                //for the url filename, try each of the prefixes in turn
	                for (int h = 0; h < LOCAL_WAR_EAR_FILE_TARGET_PREFIXES.length; h++) {
	                	
	                    String prefixedUrlfilename = LOCAL_WAR_EAR_FILE_TARGET_PREFIXES[h] + sourcefilename;
	                    if (log.isDebugEnabled()) {
	                    	log.debug("Trying WAR/EAR file name ["+ prefixedUrlfilename + "]");
	                    }
	                    
	                    HttpMessage sourceattackmsg = getNewMsg();
	                    setParameter(sourceattackmsg, paramname, prefixedUrlfilename);	                    
	                    //send the modified message (with the url filename), and see what we get back
	                    sendAndReceive(sourceattackmsg);
	                    if (log.isDebugEnabled()) {
	                    	log.debug("Completed WAR/EAR file name ["+ prefixedUrlfilename + "]");
	                    }
	                    
	                    //since the WAR/EAR file may be large, and since the LCS does not work well with such large files, lets just look at the file size, 
	                    //compared to the original
	                    int randomversussourcefilenamematchpercentage = calcLengthMatchPercentage(sourceattackmsg.getResponseBody().length(), randomfileattackmsg.getResponseBody().length());
	                    if ( randomversussourcefilenamematchpercentage < this.thresholdPercentage ) {
	                    	log.info("Source code disclosure!  The output for the WAR/EAR filename ["+ prefixedUrlfilename + "] differs sufficiently (in length) from that of the random parameter, at "+ randomversussourcefilenamematchpercentage  + "%, compared to a threshold of "+ this.thresholdPercentage + "%");
	                    	
	                    	//Note: no verification of the file contents in this case.
	                    	
		                    //if we get to here, is is very likely that we have source file inclusion attack. alert it.
		                    bingo(Alert.RISK_HIGH, Alert.WARNING,
		                    		Constant.messages.getString("ascanalpha.sourcecodeinclusion.name"),
		                    		Constant.messages.getString("ascanalpha.sourcecodeinclusion.desc"), 
		                    		getBaseMsg().getRequestHeader().getURI().getURI(),
		                            paramname, 
		                            prefixedUrlfilename,
		                            Constant.messages.getString("ascanalpha.sourcecodeinclusion.extrainfo", prefixedUrlfilename, NON_EXISTANT_FILENAME, randomversussourcefilenamematchpercentage, this.thresholdPercentage),
		                            Constant.messages.getString("ascanalpha.sourcecodeinclusion.soln"),
		                            Constant.messages.getString("ascanalpha.sourcecodeinclusion.evidence"),
		                            sourceattackmsg
		                            );
		                    
		                    // All done. No need to look for vulnerabilities on subsequent parameters on the same request (to reduce performance impact)
		                    return;	
	                    } else {
	                    	if (log.isDebugEnabled()) {
	                    		log.debug("The output for the WAR/EAR code filename ["+ prefixedUrlfilename + "] does not sufficiently differ in length from that of the random parameter, at "+ randomversussourcefilenamematchpercentage  + "%, compared to a threshold of "+ this.thresholdPercentage + "%");
	                    	}
	                    }
	                }
	            }
            } else {
            	if (log.isDebugEnabled()) {
            		log.debug("Not checking for EAR/WAR files for this request, since the Attack Strength is not HIGH or INSANE");
            	}
            }
            
            
        } catch (Exception e) {
            log.error("Error scanning parameters for Source Code Disclosure: " + e.getMessage(), e);
        }
    }

    @Override
    public int getRisk() {
        return Alert.RISK_HIGH; //definitely a High. If we get the source, we don't need to hack the app any more, because we can just analyse it off-line! Sweet..
    }

    @Override
    public int getCweId() {
        return 541;  //Information Exposure Through Include Source Code
    }

    @Override
    public int getWascId() {
        return 33;  //Path Traversal 
    }
    
	/**
	 * calculate the percentage length of similarity between 2 strings.
	 * TODO: this method is also in LDAPInjection. consider re-factoring out this class up the hierarchy, or into a helper class.
	 * @param a 
	 * @param b
	 * @return
	 */
	private int calcMatchPercentage (String a, String b) {
		if (log.isDebugEnabled()) {
			log.debug("About to get LCS for [" + a +"] and [ "+ b + "]");
		}
		if ( a == null && b == null )
			return 100;
		if ( a == null || b == null )
			return 0;
		if ( a.length() == 0 && b.length() == 0)
			return 100;
		if ( a.length() == 0 || b.length() == 0)
			return 0;
		String lcs = hirshberg.lcs(a, b);
		if (log.isDebugEnabled()) {
			log.debug("Got LCS: "+ lcs);
		}
		//get the percentage match against the longer of the 2 strings
		return (int) ( ( ((double)lcs.length()) / Math.max (a.length(), b.length())) * 100) ;
		
	}
	/**
	 * calculate the percentage length between the 2 strings.
	 * @param a
	 * @param b
	 * @return
	 */
	private int calcLengthMatchPercentage (int a, int b) {
		if ( a == 0 && b == 0 )
			return 100;
		if ( a == 0 || b == 0 )
			return 0;
		
		return (int) ( ( ((double)Math.min (a, b)) / Math.max (a, b)) * 100) ;
		
	}

}