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
package org.zaproxy.zap.extension.spiderAjax;

import javax.swing.ImageIcon;
import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.model.SiteNode;
import org.zaproxy.zap.extension.spiderAjax.ExtensionAjax;
import org.zaproxy.zap.view.PopupMenuSiteNode;


public class PopupMenuAjaxSite extends PopupMenuSiteNode {

	private static final long serialVersionUID = 1L;
    private ExtensionAjax extension = null;
    private static Logger logger = Logger.getLogger(PopupMenuAjaxSite.class);

    /**
     * @param label
     */
    public PopupMenuAjaxSite(String label, ExtensionAjax extension) {
        super(label);
        this.setIcon(new ImageIcon(getClass().getResource("/resource/icon/16/spiderAjax.png")));
        this.extension=extension;
    }
    
    private ExtensionAjax getExtensionSpider() {
    	if (extension == null) {
    		extension = (ExtensionAjax) Control.getSingleton().getExtensionLoader().getExtension(ExtensionAjax.NAME);
    	}
    	return extension;
    }
	
    @Override
    public boolean isSubMenu() {
    	return true;
    }
    
    @Override
    public String getParentMenuName() {
    	return Constant.messages.getString("attack.site.popup");
    }

    @Override
    public int getParentMenuIndex() {
    	return ATTACK_MENU_INDEX;
    }

	@Override
	public void performAction(SiteNode node) throws Exception {
	    if (node != null) {
	    	extension.spiderSite(node, false);
	    }
	}
	
	@Override
    public boolean isEnabledForSiteNode (SiteNode node) {
	    if (node != null && ! node.isRoot() ) {
	        this.setEnabled(true);
	    } else {
	        this.setEnabled(false);
	    }
        return true;
    }
	@Override
	public boolean isEnableForInvoker(Invoker invoker) {
	    if (getExtensionSpider() == null) {
			return false;
		}
		switch (invoker) {
		case alerts:
		case ascan:
		case bruteforce:
		case fuzz:
			return false;
		case history:
		case sites:
		case search:
		default:
			return true;
		}
	}

}
