/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.events;

import org.eclipse.lsp4j.Location;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;

/**
 * @author Martin Lippert
 */
public class EventPublisherIndexElement extends AbstractSpringIndexElement {
	
	private final String eventType;
	private final Location location;

	public EventPublisherIndexElement(String eventType, Location location) {
		this.eventType = eventType;
		this.location = location;
	}

	public String getEventType() {
		return eventType;
	}
	
	public Location getLocation() {
		return location;
	}

}
